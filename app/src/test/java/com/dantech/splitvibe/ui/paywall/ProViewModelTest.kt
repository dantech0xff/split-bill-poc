package com.dantech.splitvibe.ui.paywall

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.FakeGoogleAccounts
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.billing.BillingRepository
import com.dantech.splitvibe.billing.FakeBilling
import com.dantech.splitvibe.billing.PlanChange
import com.dantech.splitvibe.billing.ProPeriod
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.english
import com.dantech.splitvibe.inEnglish
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.models.StoreReplacementMode
import java.math.BigDecimal
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** The Pro screen's steps, with a stand-in for RevenueCat and a local server that answers as the proxy does. */
@RunWith(AndroidJUnit4::class)
class ProViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private lateinit var proxy: TestProxy
    private lateinit var billing: FakeBilling

    /** What the proxy answers on each path; the plan starts as a signed-in account's free one. */
    private val answers = mutableMapOf(
        "/v1/plan" to { json(200, """{"purchaser":"$PURCHASER","usage":$FREE_MONTH,"aiEnabled":true}""") },
        "/v1/session" to {
            json(200, """{"session":"signed-session","expiresAt":$FAR_FUTURE,"usage":$FREE_MONTH,"purchaser":"$PURCHASER"}""")
        },
    )

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                answers[request.requestUrl?.encodedPath]?.invoke() ?: MockResponse().setResponseCode(404)
        }
        server.start()
        proxy = TestProxy(server, context)
        billing = FakeBilling(proxy.account.account)
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `signed out, nothing goes to RevenueCat, and the plans left on screen buy nothing until the person signs in again`() {
        val viewModel = viewModel()

        assertTrue(viewModel.state.value.sold)
        assertFalse(viewModel.state.value.signedIn)
        assertTrue(billing.starts.isEmpty())
        assertEquals(0, server.requestCount)

        viewModel.signIn(activity)
        awaitOnMain { viewModel.state.value.let { it.working == null && !it.loading && it.plans.isNotEmpty() } }

        assertTrue(viewModel.state.value.signedIn)
        assertEquals(listOf(FakeBilling.MONTHLY, FakeBilling.YEARLY), viewModel.state.value.plans)
        assertEquals("the store runs as the purchaser the sign-in brought", PURCHASER, billing.user)
        assertEquals(emptyList<String>(), billing.startsWithoutTheAccount)

        // The sign-in runs out with the plans still on the screen, so only the sign-in check stops the purchase.
        proxy.account.expireSession()
        awaitOnMain { !viewModel.state.value.signedIn }
        assertEquals(FakeBilling.MONTHLY, viewModel.state.value.chosen)
        val asked = server.requestCount

        viewModel.buy(activity)
        awaitOnMain { viewModel.state.value.let { it.working == null && it.message != null } }

        assertEquals("Sign in with Google to buy Pro." to true, viewModel.state.value.message.shown())
        assertTrue(billing.purchases.isEmpty())
        assertEquals("nor is the proxy asked", asked, server.requestCount)
    }

    @Test
    fun `another sign-in on the open screen shows nothing of the account before, also when its plans cannot load`() {
        billing.membership = FakeBilling.active(billingIssue = true)
        val viewModel = signedInViewModel()
        viewModel.restore()
        awaitOnMain { viewModel.state.value.let { it.working == null && it.message != null } }
        assertTrue(viewModel.state.value.member)

        // The sign-in runs out, and someone else signs in before the proxy has said who they buy as.
        billing.membership = null
        proxy.account.expireSession()
        proxy.account.saveSession(OTHER_SESSION, signedInAs = "lan@example.com")
        awaitOnMain { viewModel.state.value.message == null }

        assertNull(viewModel.state.value.membership)
        assertFalse(viewModel.state.value.member)

        answers["/v1/plan"] = { json(500, """{"error":"internal"}""") }
        viewModel.load()
        awaitOnMain { viewModel.state.value.let { !it.loading && it.loadError != null } }

        assertNull(viewModel.state.value.membership)
        assertFalse(viewModel.state.value.member)
    }

    @Test
    fun `a purchase has the proxy ask RevenueCat, and the screen then shows Pro's 300 calls`() {
        val viewModel = signedInViewModel()
        answers["/v1/plan"] = { json(200, """{"purchaser":"$PURCHASER","usage":$PRO_MONTH,"aiEnabled":true}""") }
        val asked = server.requestCount

        viewModel.select(ProPeriod.YEARLY)
        viewModel.buy(activity)
        awaitOnMain { viewModel.state.value.let { it.working == null && it.message != null } }

        assertEquals(listOf(FakeBilling.YEARLY to null), billing.purchases)
        repeat(asked) { server.takeRequest() }
        assertEquals("the purchase has the proxy ask again", "/v1/plan", server.takeRequest().requestUrl?.encodedPath)
        val state = viewModel.state.value
        assertTrue(state.member)
        assertEquals(BigDecimal(300), state.callsLeft?.limit)
        assertEquals(Plan.PRO, state.callsLeft?.plan)
        assertEquals("Welcome to Pro: you now have 300 AI calls a month." to false, state.message.shown())
    }

    @Test
    fun `a purchase the person cancels changes nothing and says nothing`() {
        val viewModel = signedInViewModel()
        val before = viewModel.state.value
        val asked = server.requestCount
        billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.PurchaseCancelledError, cancelled = true)

        viewModel.buy(activity)
        awaitOnMain { billing.purchases.isNotEmpty() && viewModel.state.value.working == null }

        assertEquals(before, viewModel.state.value)
        assertNull(viewModel.state.value.message)
        assertEquals("the proxy is asked only whether Pro is on sale", asked + 1, server.requestCount)
    }

    @Test
    fun `a payment that is pending says that Pro starts once Google Play confirms it`() {
        val viewModel = signedInViewModel()
        val asked = server.requestCount
        billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.PaymentPendingError)

        viewModel.buy(activity)
        awaitOnMain { viewModel.state.value.message != null }

        val pending = "The payment is pending. Pro starts once Google Play confirms it." to false
        assertEquals(pending, viewModel.state.value.message.shown())
        assertFalse(viewModel.state.value.member)
        assertEquals("the proxy is asked only whether Pro is on sale", asked + 1, server.requestCount)
    }

    @Test
    fun `a subscription of this Google Play account that another Split Vibe account bought says to sign in with that one`() {
        val viewModel = signedInViewModel()
        // RevenueCat refuses the purchase itself, or Google Play says its account has the subscription
        // already and the restore that follows is refused.
        val refusals = listOf<() -> Unit>(
            { billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.ReceiptAlreadyInUseError) },
            {
                billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.ProductAlreadyPurchasedError)
                billing.restoreFailure = FakeBilling.refused(PurchasesErrorCode.ReceiptAlreadyInUseError)
            },
        )
        for ((index, refuse) in refusals.withIndex()) {
            refuse()
            viewModel.dismissMessage()

            viewModel.buy(activity)
            awaitOnMain { viewModel.state.value.let { it.working == null && it.message != null } }

            assertEquals("$index", ANOTHER_ACCOUNT to true, viewModel.state.value.message.shown())
            assertFalse(viewModel.state.value.member)
        }
        assertEquals("only Google Play's refusal is restored first", 1, billing.restores)
    }

    @Test
    fun `when Google Play says its account has the subscription already, a restore finds this account's Pro and shows it`() {
        val viewModel = signedInViewModel()
        billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.ProductAlreadyPurchasedError)
        billing.membership = FakeBilling.active()
        answers["/v1/plan"] = { json(200, """{"purchaser":"$PURCHASER","usage":$PRO_MONTH,"aiEnabled":true}""") }
        val asked = server.requestCount

        viewModel.buy(activity)
        awaitOnMain { viewModel.state.value.let { it.working == null && it.message != null } }

        assertEquals(1, billing.restores)
        val state = viewModel.state.value
        assertEquals("Your Pro subscription is back on this account." to false, state.message.shown())
        assertTrue(state.member)
        assertEquals(Plan.PRO, state.callsLeft?.plan)
        repeat(asked) { server.takeRequest() }
        val paths = List(2) { server.takeRequest().requestUrl?.encodedPath }
        assertEquals("the proxy checks the plan before the purchase and after the restore", listOf("/v1/plan", "/v1/plan"), paths)
    }

    @Test
    fun `the proxy's Pro counts as membership although RevenueCat reports the subscription ended`() {
        // As in the hours Google Play can take to renew, which the proxy covers and RevenueCat does not.
        billing.membership = FakeBilling.active(willRenew = false).copy(active = false)
        answers["/v1/plan"] = { json(200, """{"purchaser":"$PURCHASER","usage":$PRO_MONTH,"aiEnabled":true}""") }

        val state = signedInViewModel().state.value

        assertTrue(state.member)
        assertTrue(state.subscribed)
        assertNull("no switch until RevenueCat reports it active again", state.switchTarget)
    }

    @Test
    fun `a subscription Google Play is still renewing or waits for a payment for shows as the person's, not as one to buy`() {
        val waiting = listOf(
            FakeBilling.active(willRenew = true).copy(active = false),
            FakeBilling.active(willRenew = false, billingIssue = true).copy(active = false),
        )
        for (membership in waiting) {
            billing.membership = membership

            val state = signedInViewModel().state.value

            assertFalse("the proxy counts free calls", state.member)
            assertTrue(state.subscribed)
            assertEquals(FakeBilling.MONTHLY, state.currentPlan)
            assertNull(state.switchTarget)
        }
    }

    @Test
    fun `opening the screen asks RevenueCat's server for the person's Pro, not the copy its SDK keeps`() {
        signedInViewModel()

        assertEquals(listOf(CacheFetchPolicy.FETCH_CURRENT), billing.policies)
    }

    @Test
    fun `restoring brings Pro back, and a restore on another account says whose the subscription is`() {
        val viewModel = signedInViewModel()
        billing.membership = FakeBilling.active()
        answers["/v1/plan"] = { json(200, """{"purchaser":"$PURCHASER","usage":$PRO_MONTH,"aiEnabled":true}""") }

        viewModel.restore()
        awaitOnMain { viewModel.state.value.message != null }

        assertEquals("Your Pro subscription is back on this account.", viewModel.state.value.message?.text.inEnglish())
        assertTrue(viewModel.state.value.member)
        assertEquals(1, billing.restores)

        billing.failure = FakeBilling.refused(PurchasesErrorCode.ReceiptAlreadyInUseError)
        viewModel.dismissMessage()
        viewModel.restore()
        awaitOnMain { viewModel.state.value.message != null }

        assertEquals(ANOTHER_ACCOUNT to true, viewModel.state.value.message.shown())
    }

    @Test
    fun `a restore that finds nothing says so`() {
        val viewModel = signedInViewModel()

        viewModel.restore()
        awaitOnMain { viewModel.state.value.message != null }

        val nothing = "No Pro subscription was found for this Google Play account." to false
        assertEquals(nothing, viewModel.state.value.message.shown())
    }

    @Test
    fun `a Pro member switches to the other plan at once, without proration`() {
        billing.membership = FakeBilling.active(basePlanId = "monthly")
        val viewModel = signedInViewModel()
        val state = viewModel.state.value
        assertEquals(FakeBilling.MONTHLY, state.currentPlan)
        val target = checkNotNull(state.switchTarget)

        viewModel.switchTo(activity, target)
        awaitOnMain { viewModel.state.value.message != null }

        assertEquals(listOf(FakeBilling.YEARLY to PlanChange("pro", StoreReplacementMode.WITHOUT_PRORATION)), billing.purchases)
        assertEquals(FakeBilling.YEARLY, viewModel.state.value.currentPlan)
        val switched = "You are on the yearly plan now. Its price is charged from your next renewal."
        assertEquals(switched, viewModel.state.value.message?.text.inEnglish())
    }

    @Test
    fun `the plans show although the proxy could not check the plan just then, which says so plainly`() {
        val cases = mapOf(
            json(429, """{"error":"rate_limited"}""").setHeader("Retry-After", "1200") to
                "Your plan was checked many times in the last hour. Try again in about 20 minutes.",
            json(502, """{"error":"subscription_unavailable"}""") to
                "Split Vibe's server could not check your subscription just now. Try again in a moment.",
            // Just after a deploy, a part of the proxy that still runs the code before can fail the check.
            json(500, """{"error":"internal"}""") to "Split Vibe's server had a problem. Try again in a moment.",
        )
        for ((refusal, said) in cases) {
            answers["/v1/plan"] = { refusal }
            proxy.account.saveSession(SESSION, purchaser = PURCHASER)
            proxy.account.onCallsLeft(KNOWN)

            val viewModel = viewModel()
            awaitOnMain { viewModel.state.value.let { !it.loading && it.plans.isNotEmpty() } }

            assertEquals(said, viewModel.state.value.message?.text.inEnglish())
            assertEquals("the last known calls stay", KNOWN, viewModel.state.value.callsLeft)
            assertTrue("Pro stays on sale", viewModel.state.value.onSale)
        }
    }

    @Test
    fun `while Split Vibe's server cannot check subscriptions, no purchase and no switch can be made, and a restore can`() {
        answers["/v1/plan"] = { json(503, """{"error":"not_configured"}""") }
        billing.membership = FakeBilling.active(basePlanId = "monthly")
        proxy.account.saveSession(SESSION, purchaser = PURCHASER)

        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.let { !it.loading && it.plans.isNotEmpty() } }

        val state = viewModel.state.value
        assertFalse(state.onSale)
        assertEquals("Split Vibe's server cannot check subscriptions yet." to true, state.message.shown())
        viewModel.buy(activity)
        viewModel.switchTo(activity, FakeBilling.YEARLY)
        assertNull("nothing started", viewModel.state.value.working)
        assertTrue(billing.purchases.isEmpty())

        viewModel.restore()
        awaitOnMain { viewModel.state.value.working == null }
        assertEquals(1, billing.restores)
        assertTrue(billing.purchases.isEmpty())
    }

    @Test
    fun `a screen opened while Pro was on sale cannot buy once the server says it cannot check subscriptions`() {
        val viewModel = signedInViewModel()
        assertTrue(viewModel.state.value.onSale)
        answers["/v1/plan"] = { json(503, """{"error":"not_configured"}""") }

        viewModel.buy(activity)
        awaitOnMain { viewModel.state.value.let { it.working == null && it.message != null } }

        assertTrue(billing.purchases.isEmpty())
        assertFalse("the screen shows that Pro cannot be bought now", viewModel.state.value.onSale)
        assertEquals("Split Vibe's server cannot check subscriptions yet." to true, viewModel.state.value.message.shown())
    }

    @Test
    fun `without a purchaser yet and without the proxy, the plans cannot load, which says why`() {
        // Signed in before the app kept a purchaser, and the proxy cannot be reached now.
        proxy.account.saveSession(SESSION)
        server.shutdown()

        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.let { !it.loading && it.loadError != null } }

        assertEquals("There is no connection. Check it and try again.", viewModel.state.value.loadError.inEnglish())
        assertTrue(billing.starts.isEmpty())
    }

    @Test
    fun `a build without RevenueCat's key says Pro is not available and asks nothing`() {
        proxy.account.saveSession(SESSION, purchaser = PURCHASER)

        val viewModel = viewModel(BillingRepository(billing = null, proxy.api, proxy.account, canSignIn = true))

        assertFalse(viewModel.state.value.sold)
        viewModel.load()
        viewModel.buy(activity)
        assertEquals(0, server.requestCount)
        assertTrue(billing.starts.isEmpty())
    }

    @Test
    fun `each failure of the store or the proxy has a plain message`() {
        val cases = mapOf(
            FakeBilling.refused(PurchasesErrorCode.NetworkError) to "There is no connection. Check it and try again.",
            FakeBilling.refused(PurchasesErrorCode.StoreProblemError) to STORE_PROBLEM,
            FakeBilling.purchaseFailed(PurchasesErrorCode.PurchaseNotAllowedError) to
                "This phone or this Google Play account cannot buy subscriptions.",
            FakeBilling.refused(PurchasesErrorCode.ConfigurationError) to NOTHING_ON_SALE.english(),
            FakeBilling.purchaseFailed(PurchasesErrorCode.ProductNotAvailableForPurchaseError) to NOTHING_ON_SALE.english(),
            FakeBilling.purchaseFailed(PurchasesErrorCode.PurchaseCancelledError, cancelled = true) to null,
        )
        for ((failure, said) in cases) assertEquals(failure.code.name, said, proMessageOf(failure)?.text.inEnglish())
    }

    @Test
    fun `a failure of the app's own says only that it did not work, never what the exception says`() {
        val failure = IllegalStateException("RevenueCat is used only once a purchaser is signed in")

        assertEquals("That did not work. Please try again." to true, proMessageOf(failure).shown())
    }

    private fun viewModel(repository: BillingRepository = BillingRepository(billing, proxy.api, proxy.account, canSignIn = true)) =
        ProViewModel(
            billing = repository,
            accounts = proxy.accountRepository(FakeGoogleAccounts(), ProxyCache(storage.database.proxyCache()), storage.phoneData(context)),
            account = proxy.account.account,
        )

    /** The screen of an account signed in with its purchaser, once the plans are on it. */
    private fun signedInViewModel(): ProViewModel {
        proxy.account.saveSession(SESSION, signedInAs = "minh.tran@example.com", purchaser = PURCHASER)
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.let { !it.loading && it.plans.isNotEmpty() } }
        return viewModel
    }

    /** What [this] says in English, and whether it is an error. */
    private fun ProMessage?.shown(): Pair<String, Boolean>? = this?.let { it.text.english() to it.isError }

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        const val FAR_FUTURE = 4_102_444_800_000L
        val SESSION = Session("signed-session", expiresAt = FAR_FUTURE)
        val OTHER_SESSION = Session("other-session", expiresAt = FAR_FUTURE)
        val PURCHASER = "c".repeat(64)
        const val FREE_MONTH = """{"limit":10,"used":3,"remaining":7,"period":"2026-11","resetsAt":1796083200000,"plan":"free"}"""
        const val PRO_MONTH = """{"limit":300,"used":3,"remaining":297,"period":"2026-11","resetsAt":1796083200000,"plan":"pro"}"""
        val KNOWN = CallsLeft(BigDecimal(10), BigDecimal(4), BigDecimal(6), period = "2026-11", resetsAt = 1_796_083_200_000)
        const val STORE_PROBLEM = "Google Play has a problem right now. Try again later."
        const val ANOTHER_ACCOUNT =
            "The subscription on this Google Play account belongs to another Split Vibe account. Sign in with that account to use it."
    }
}
