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
    fun `signed out, nothing goes to RevenueCat and nothing can be bought until the person signs in`() {
        val viewModel = viewModel()

        assertTrue(viewModel.state.value.sold)
        assertFalse(viewModel.state.value.signedIn)
        viewModel.buy(activity)
        assertTrue(billing.purchases.isEmpty())
        assertTrue(billing.starts.isEmpty())
        assertEquals(0, server.requestCount)

        viewModel.signIn(activity)
        awaitOnMain { viewModel.state.value.let { it.working == null && !it.loading && it.plans.isNotEmpty() } }

        assertTrue(viewModel.state.value.signedIn)
        assertEquals(listOf(FakeBilling.MONTHLY, FakeBilling.YEARLY), viewModel.state.value.plans)
        assertEquals("the store runs as the purchaser the sign-in brought", PURCHASER, billing.user)
        assertEquals(emptyList<String>(), billing.startsWithoutTheAccount)
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
        assertEquals(ProMessage("Welcome to Pro: you now have 300 AI calls a month.", isError = false), state.message)
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
        assertEquals("the proxy is not asked", asked, server.requestCount)
    }

    @Test
    fun `a payment that is pending says that Pro starts once Google Play confirms it`() {
        val viewModel = signedInViewModel()
        val asked = server.requestCount
        billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.PaymentPendingError)

        viewModel.buy(activity)
        awaitOnMain { viewModel.state.value.message != null }

        val pending = ProMessage("The payment is pending. Pro starts once Google Play confirms it.", isError = false)
        assertEquals(pending, viewModel.state.value.message)
        assertFalse(viewModel.state.value.member)
        assertEquals(asked, server.requestCount)
    }

    @Test
    fun `a subscription of this Google Play account that another Split Vibe account bought says to sign in with that one`() {
        val viewModel = signedInViewModel()
        for (code in listOf(PurchasesErrorCode.ReceiptAlreadyInUseError, PurchasesErrorCode.ProductAlreadyPurchasedError)) {
            billing.failure = FakeBilling.purchaseFailed(code)
            viewModel.dismissMessage()

            viewModel.buy(activity)
            awaitOnMain { viewModel.state.value.message != null }

            assertEquals(code.name, ProMessage(ANOTHER_ACCOUNT, isError = true), viewModel.state.value.message)
            assertFalse(viewModel.state.value.member)
        }
    }

    @Test
    fun `restoring brings Pro back, and a restore on another account says whose the subscription is`() {
        val viewModel = signedInViewModel()
        billing.membership = FakeBilling.active()
        answers["/v1/plan"] = { json(200, """{"purchaser":"$PURCHASER","usage":$PRO_MONTH,"aiEnabled":true}""") }

        viewModel.restore()
        awaitOnMain { viewModel.state.value.message != null }

        assertEquals("Your Pro subscription is back on this account.", viewModel.state.value.message?.text)
        assertTrue(viewModel.state.value.member)
        assertEquals(1, billing.restores)

        billing.failure = FakeBilling.refused(PurchasesErrorCode.ReceiptAlreadyInUseError)
        viewModel.dismissMessage()
        viewModel.restore()
        awaitOnMain { viewModel.state.value.message != null }

        assertEquals(ProMessage(ANOTHER_ACCOUNT, isError = true), viewModel.state.value.message)
    }

    @Test
    fun `a restore that finds nothing says so`() {
        val viewModel = signedInViewModel()

        viewModel.restore()
        awaitOnMain { viewModel.state.value.message != null }

        val nothing = ProMessage("No Pro subscription was found for this Google Play account.", isError = false)
        assertEquals(nothing, viewModel.state.value.message)
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
        assertEquals("You are on the yearly plan now. Its price is charged from your next renewal.", viewModel.state.value.message?.text)
    }

    @Test
    fun `the plans show although the proxy could not check the plan, which says so plainly`() {
        val cases = mapOf(
            json(429, """{"error":"rate_limited"}""").setHeader("Retry-After", "1200") to
                "Your plan was checked many times in the last hour. Try again later.",
            json(503, """{"error":"not_configured"}""") to "Split Vibe's server cannot check subscriptions yet.",
            json(502, """{"error":"subscription_unavailable"}""") to
                "Split Vibe's server could not check your subscription just now. Try again in a moment.",
        )
        for ((refusal, said) in cases) {
            answers["/v1/plan"] = { refusal }
            proxy.account.saveSession(SESSION, purchaser = PURCHASER)
            proxy.account.onCallsLeft(KNOWN)

            val viewModel = viewModel()
            awaitOnMain { viewModel.state.value.let { !it.loading && it.plans.isNotEmpty() } }

            assertEquals(said, viewModel.state.value.message?.text)
            assertEquals("the last known calls stay", KNOWN, viewModel.state.value.callsLeft)
        }
    }

    @Test
    fun `without a purchaser yet and without the proxy, the plans cannot load, which says why`() {
        // Signed in before the app kept a purchaser, and the proxy cannot be reached now.
        proxy.account.saveSession(SESSION)
        server.shutdown()

        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.let { !it.loading && it.loadError != null } }

        assertEquals("There is no connection. Check it and try again.", viewModel.state.value.loadError)
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
            FakeBilling.purchaseFailed(PurchasesErrorCode.PurchaseNotAllowedError) to STORE_PROBLEM,
            FakeBilling.purchaseFailed(PurchasesErrorCode.PurchaseCancelledError, cancelled = true) to null,
        )
        for ((failure, said) in cases) assertEquals(failure.code.name, said, proMessageOf(failure)?.text)
    }

    private fun viewModel(repository: BillingRepository = BillingRepository(billing, proxy.api, proxy.account, canSignIn = true)) =
        ProViewModel(
            billing = repository,
            accounts = proxy.accountRepository(FakeGoogleAccounts(), ProxyCache(storage.database.proxyCache())),
            account = proxy.account.account,
        )

    /** The screen of an account signed in with its purchaser, once the plans are on it. */
    private fun signedInViewModel(): ProViewModel {
        proxy.account.saveSession(SESSION, signedInAs = "minh.tran@example.com", purchaser = PURCHASER)
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.let { !it.loading && it.plans.isNotEmpty() } }
        return viewModel
    }

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        const val FAR_FUTURE = 4_102_444_800_000L
        val SESSION = Session("signed-session", expiresAt = FAR_FUTURE)
        val PURCHASER = "c".repeat(64)
        const val FREE_MONTH = """{"limit":10,"used":3,"remaining":7,"period":"2026-11","resetsAt":1796083200000,"plan":"free"}"""
        const val PRO_MONTH = """{"limit":300,"used":3,"remaining":297,"period":"2026-11","resetsAt":1796083200000,"plan":"pro"}"""
        val KNOWN = CallsLeft(BigDecimal(10), BigDecimal(4), BigDecimal(6), period = "2026-11", resetsAt = 1_796_083_200_000)
        const val STORE_PROBLEM = "Google Play has a problem right now. Try again later."
        const val ANOTHER_ACCOUNT =
            "The subscription on this Google Play account belongs to another Split Vibe account. Sign in with that account to use it."
    }
}
