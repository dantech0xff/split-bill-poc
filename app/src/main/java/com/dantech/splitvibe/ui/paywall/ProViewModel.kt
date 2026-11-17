package com.dantech.splitvibe.ui.paywall

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.billing.BillingRepository
import com.dantech.splitvibe.billing.NotOnSaleException
import com.dantech.splitvibe.billing.ProChange
import com.dantech.splitvibe.billing.ProMembership
import com.dantech.splitvibe.billing.ProPeriod
import com.dantech.splitvibe.billing.ProPlan
import com.dantech.splitvibe.billing.SignInNeededException
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.llm.waitHint
import com.dantech.splitvibe.ui.settings.messageOf
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Pro screen is waiting for. */
enum class ProWork { SIGNING_IN, BUYING, SWITCHING, RESTORING }

/** Something to tell the person about what they just did, which with [isError] went wrong. */
data class ProMessage(val text: String, val isError: Boolean)

/** What the Pro screen shows. */
data class ProUiState(
    /** This build sells Pro; without it the screen only says that Pro is not available. */
    val sold: Boolean,
    val signedIn: Boolean = false,
    /** Someone signed in, and the sign-in has since run out. */
    val signInExpired: Boolean = false,
    val callsLeft: CallsLeft? = null,
    /** The plans and the person's Pro are being asked for. */
    val loading: Boolean = false,
    /** Why the plans could not be shown, which trying again may fix. */
    val loadError: String? = null,
    /** The plans Google Play sells, monthly before yearly. */
    val plans: List<ProPlan> = emptyList(),
    /** The plan the person picked to buy. */
    val selected: ProPeriod = ProPeriod.MONTHLY,
    /** Pro as RevenueCat reports it, once asked. */
    val membership: ProMembership? = null,
    val working: ProWork? = null,
    val message: ProMessage? = null,
    /**
     * Pro can be bought, and a plan switched, now: not while Split Vibe's server says it cannot
     * check subscriptions yet, as it would count none of them.
     */
    val onSale: Boolean = true,
) {
    /**
     * The person has Pro: RevenueCat reports it active, or the proxy counts the calls as Pro's. The
     * proxy keeps Pro through Google Play's delay in renewing, which RevenueCat does not.
     */
    val member: Boolean get() = membership?.active == true || callsLeft?.plan == Plan.PRO

    /**
     * The screen shows the person's Pro rather than the plans to buy: they have it, or a Google Play
     * subscription to it that Google Play is still renewing or waits for a payment for.
     */
    val subscribed: Boolean get() = member || membership?.awaitingRenewal == true

    /** The plan of the person's Pro, among those on sale; none for Pro given another way than Google Play. */
    val currentPlan: ProPlan? get() = membership?.takeIf { subscribed }?.let { pro -> plans.firstOrNull(pro::isOn) }

    /** The other plan, which a Pro member can switch to while RevenueCat reports their Pro active. */
    val switchTarget: ProPlan?
        get() = currentPlan?.takeIf { membership?.active == true }?.let { current -> plans.firstOrNull { it.period != current.period } }

    /** The plan the main button buys. */
    val chosen: ProPlan? get() = plans.firstOrNull { it.period == selected } ?: plans.firstOrNull()

    /** Where the person manages their subscription in Google Play. */
    val manageUrl: String get() = manageUrlOf(membership)
}

/**
 * The Pro screen: asks the proxy for the account's plan and RevenueCat for the plans and the
 * person's Pro when it opens, and signs in, buys, switches between the plans and restores, one at
 * a time. Signing in is offered first, as only a signed-in account buys.
 */
class ProViewModel(
    private val billing: BillingRepository,
    private val accounts: AccountRepository,
    private val account: StateFlow<Account>,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ProUiState> = combine(local, account, billing.onSale, ::uiOf)
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiOf(local.value, account.value, billing.onSale.value))

    private var loading: Job? = null

    init {
        load()
    }

    /**
     * Has the proxy ask RevenueCat for the account's plan, which shows its calls left even when that
     * fails, then asks RevenueCat for the plans and the person's Pro. Only while signed in: nothing
     * goes to RevenueCat for a phone where nobody is.
     */
    fun load() {
        if (!billing.sellsPro || account.value.session == null) return
        loading?.cancel()
        loading = viewModelScope.launch {
            local.update { it.copy(loading = true, loadError = null) }
            val refreshFailure = try {
                billing.refreshPlan()
                null
            } catch (e: ProxyException) {
                e
            } catch (e: IOException) {
                e
            }
            // A refused session turned into a sign-in to renew, which the screen offers by itself.
            if (account.value.session == null) {
                local.update { it.copy(loading = false) }
                return@launch
            }
            try {
                val offer = billing.offer()
                local.update {
                    it.copy(
                        loading = false,
                        plans = offer.plans,
                        membership = offer.membership,
                        message = refreshFailure?.let { failure -> proMessageOf(failure) },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Without a purchaser the proxy's own answer says best what went wrong.
                val failure = if (e is SignInNeededException) refreshFailure ?: e else e
                local.update { it.copy(loading = false, loadError = proMessageOf(failure)?.text) }
            }
        }
    }

    fun select(period: ProPeriod) = local.update { it.copy(selected = period) }

    /** [activity] shows Google's account picker; it is used for this call only. */
    fun signIn(activity: Context) = act(ProWork.SIGNING_IN, failure = { e -> messageOf(e)?.let { ProMessage(it, isError = true) } }) {
        accounts.signIn(activity)
        load()
        null
    }

    /** Buys the plan picked, over [activity], which Google Play's purchase is shown over. */
    fun buy(activity: Activity) {
        val plan = state.value.takeIf { it.onSale }?.chosen ?: return
        act(ProWork.BUYING) {
            val change = purchased { billing.buy(activity, plan) } ?: return@act restored()
            ProMessage(if (change.plan == Plan.PRO) WELCOME else NOT_COUNTED_YET, isError = false)
        }
    }

    /** Switches the person's Pro to [plan], over [activity]. */
    fun switchTo(activity: Activity, plan: ProPlan) {
        val from = state.value.takeIf { it.onSale }?.membership?.takeIf { it.active } ?: return
        act(ProWork.SWITCHING) {
            purchased { billing.switchTo(activity, plan, from) } ?: return@act restored()
            val name = nameOf(plan.period).lowercase()
            ProMessage("You are on the $name plan now. Its price is charged from your next renewal.", isError = false)
        }
    }

    fun restore() = act(ProWork.RESTORING) { restored() }

    /**
     * What [purchase] changed, shown; or null when Google Play says that its account has the
     * subscription already, which only a restore can tell apart: this Split Vibe account's, or
     * another's, which RevenueCat refuses with `ReceiptAlreadyInUseError`.
     */
    private suspend fun purchased(purchase: suspend () -> ProChange): ProChange? {
        val change = try {
            purchase()
        } catch (e: PurchasesException) {
            if (e.code == PurchasesErrorCode.ProductAlreadyPurchasedError) return null
            throw e
        }
        local.update { it.copy(membership = change.membership) }
        return change
    }

    /** Restores the purchases of the phone's Google Play account, then has the proxy check them, and says what was found. */
    private suspend fun restored(): ProMessage {
        local.update { it.copy(working = ProWork.RESTORING) }
        val change = billing.restore()
        local.update { it.copy(membership = change.membership) }
        return ProMessage(restoredText(change), isError = false)
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    /** Runs one action at a time; what it says when it is done, or when it fails, goes on screen. */
    private fun act(work: ProWork, failure: (Exception) -> ProMessage? = ::proMessageOf, block: suspend () -> ProMessage?) {
        if (local.value.working != null) return
        local.update { it.copy(working = work, message = null) }
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure(e)
            }
            local.update { it.copy(working = null, message = message) }
        }
    }

    private fun uiOf(local: Local, account: Account, onSale: Boolean) = ProUiState(
        sold = billing.sellsPro,
        signedIn = account.session != null,
        signInExpired = account.signInExpired,
        callsLeft = account.callsLeft,
        loading = local.loading,
        loadError = local.loadError,
        plans = local.plans,
        selected = local.selected,
        // Pro reported for one account says nothing about the next.
        membership = local.membership.takeIf { account.session != null },
        working = local.working,
        message = local.message,
        onSale = onSale,
    )

    /** What the screen keeps between the person's steps. */
    private data class Local(
        val loading: Boolean = false,
        val loadError: String? = null,
        val plans: List<ProPlan> = emptyList(),
        val selected: ProPeriod = ProPeriod.MONTHLY,
        val membership: ProMembership? = null,
        val working: ProWork? = null,
        val message: ProMessage? = null,
    )

    private companion object {
        const val WELCOME = "Welcome to Pro: you now have ${CallsLeft.PRO_MONTHLY} AI calls a month."
        const val NOT_COUNTED_YET = "Your purchase went through. Your calls turn into Pro's ${CallsLeft.PRO_MONTHLY} a month as soon as " +
            "Split Vibe's server has checked it, usually within a few minutes."

        const val FOUND_NOT_COUNTED_YET = "Your Pro subscription was found. Your calls turn into Pro's ${CallsLeft.PRO_MONTHLY} a " +
            "month as soon as Split Vibe's server has checked it."

        fun restoredText(change: ProChange): String {
            val pro = change.membership
            return when {
                pro == null || !(pro.active || pro.awaitingRenewal) -> "No Pro subscription was found for this Google Play account."
                change.plan == Plan.PRO -> "Your Pro subscription is back on this account."
                pro.active -> FOUND_NOT_COUNTED_YET
                else -> "Your Pro subscription was found. Pro comes back once Google Play has renewed it."
            }
        }
    }
}

/**
 * What to tell the person when something on the Pro screen did not work, or null when there is
 * nothing to say, as when they closed Google Play's purchase themselves.
 */
internal fun proMessageOf(error: Exception): ProMessage? {
    val text = when (error) {
        is PurchasesTransactionException -> if (error.userCancelled) null else storeText(error.code)
        is PurchasesException -> storeText(error.code)
        is ProxyException -> proxyText(error)
        is NotOnSaleException -> CANNOT_CHECK
        is SignInNeededException -> "Sign in with Google to buy Pro."
        is IOException -> NO_CONNECTION
        else -> "Something went wrong: ${error.message ?: error.javaClass.simpleName}"
    } ?: return null
    // A payment that is waiting is no failure: Pro follows once Google Play has it.
    val pending = (error as? PurchasesException)?.code == PurchasesErrorCode.PaymentPendingError
    return ProMessage(text, isError = !pending)
}

/** What a refusal of Google Play or RevenueCat means for the person; null for a purchase they cancelled. */
private fun storeText(code: PurchasesErrorCode): String? = when (code) {
    PurchasesErrorCode.PurchaseCancelledError -> null
    PurchasesErrorCode.PaymentPendingError -> "The payment is pending. Pro starts once Google Play confirms it."
    PurchasesErrorCode.ReceiptAlreadyInUseError -> ANOTHER_ACCOUNT
    PurchasesErrorCode.NetworkError -> NO_CONNECTION
    PurchasesErrorCode.StoreProblemError, PurchasesErrorCode.PurchaseNotAllowedError -> STORE_PROBLEM
    else -> "That did not work. Please try again."
}

/** What a refusal of the proxy's check of the plan means for the person. */
private fun proxyText(refusal: ProxyException): String = when (refusal.code) {
    "rate_limited" -> "Your plan was checked many times in the last hour. ${waitHint(refusal.retryAfterSeconds)}"
    ProxyApi.NOT_CONFIGURED -> CANNOT_CHECK
    "subscription_unavailable" -> "Split Vibe's server could not check your subscription just now. Try again in a moment."
    ProxyApi.SESSION_REFUSED -> "Your sign-in has run out. Sign in again to buy or manage Pro."
    else -> "Split Vibe's server had a problem. Try again in a moment."
}

private const val NO_CONNECTION = "There is no connection. Check it and try again."
private const val STORE_PROBLEM = "Google Play has a problem right now. Try again later."

/** Why Pro is not on sale: Split Vibe's server would count none of what is bought, as it has no key for RevenueCat yet. */
private const val CANNOT_CHECK = "Split Vibe's server cannot check subscriptions yet."

/** A subscription bought by another Split Vibe account stays with it, and RevenueCat refuses it to this one. */
private const val ANOTHER_ACCOUNT =
    "The subscription on this Google Play account belongs to another Split Vibe account. Sign in with that account to use it."
