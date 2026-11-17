package com.dantech.splitvibe.billing

import android.app.Activity
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyException
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.models.StoreReplacementMode
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** Pro through a stand-in for RevenueCat, and the proxy's plan through a local server that answers as the proxy does. */
@RunWith(AndroidJUnit4::class)
class BillingRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var proxy: TestProxy
    private lateinit var billing: FakeBilling
    private lateinit var repository: BillingRepository

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        billing = FakeBilling(proxy.account.account)
        repository = BillingRepository(billing, proxy.api, proxy.account, canSignIn = true)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    @Test
    fun `the store runs only as the purchaser of the account signed in now, and stops without one`() {
        scope.launch { repository.follow() }
        idle()
        assertTrue("nothing is started for a phone where nobody signed in", billing.starts.isEmpty())

        // A sign-in from before the app kept a purchaser: nothing until the proxy names it.
        proxy.account.saveSession(SESSION, signedInAs = "minh.tran@example.com")
        idle()
        assertTrue(billing.starts.isEmpty())

        proxy.account.onPurchaser(PURCHASER, SESSION)
        idle()
        assertEquals(PURCHASER, billing.user)

        proxy.account.expireSession()
        idle()
        assertNull("a sign-in that ran out stops it", billing.user)

        proxy.account.saveSession(OTHER_SESSION, signedInAs = "lan@example.com", purchaser = OTHER_PURCHASER)
        idle()
        assertEquals(OTHER_PURCHASER, billing.user)

        proxy.account.clearSession()
        idle()
        assertNull("signing out stops it", billing.user)
        assertEquals(listOf(PURCHASER, OTHER_PURCHASER), billing.starts)
        assertEquals(emptyList<String>(), billing.startsWithoutTheAccount)
    }

    @Test
    fun `nothing is bought, switched or restored while nobody is signed in`() = runBlocking {
        for (attempt in listOf<suspend () -> Unit>(
            { repository.offer() },
            { repository.buy(activity, FakeBilling.MONTHLY) },
            { repository.switchTo(activity, FakeBilling.YEARLY, from = FakeBilling.active()) },
            { repository.restore() },
        )) {
            try {
                attempt()
                fail("Only a signed-in account buys")
            } catch (e: SignInNeededException) {
                // Signing in comes first.
            }
        }
        // Signed in, but the proxy has not said who the purchaser is yet.
        proxy.account.saveSession(SESSION)
        try {
            repository.buy(activity, FakeBilling.MONTHLY)
            fail("A purchase goes only as the purchaser the proxy gave")
        } catch (e: SignInNeededException) {
            // The Pro screen asks the proxy for it first.
        }

        assertTrue(billing.starts.isEmpty())
        assertTrue(billing.purchases.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a purchase has the proxy ask RevenueCat at once, and the account's Pro calls are kept`() = runBlocking {
        signIn()
        // First whether Pro is on sale, then what the purchase changed.
        server.enqueue(planAnswer(FREE_MONTH))
        server.enqueue(planAnswer(PRO_MONTH))

        val change = repository.buy(activity, FakeBilling.MONTHLY)

        assertEquals(listOf(FakeBilling.MONTHLY to null), billing.purchases)
        repeat(2) {
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/plan", request.requestUrl?.encodedPath)
            assertEquals("{}", request.body.readUtf8())
            assertEquals("Bearer signed-session", request.getHeader("Authorization"))
        }
        assertEquals(Plan.PRO, change.plan)
        assertTrue(change.membership?.active == true)
        val calls = proxy.account.account.value.callsLeft
        assertEquals(BigDecimal(300), calls?.limit)
        assertEquals(Plan.PRO, calls?.plan)
    }

    @Test
    fun `a purchase that went through stays bought when the proxy cannot be asked just then`() = runBlocking {
        signIn()
        repeat(2) { server.enqueue(json(502, """{"error":"subscription_unavailable"}""")) }

        val change = repository.buy(activity, FakeBilling.YEARLY)

        assertNull("the proxy has not counted it yet", change.plan)
        assertTrue(change.membership?.active == true)
        assertEquals(1, billing.purchases.size)
    }

    @Test
    fun `a switch of plans names the subscription without its base plan, and never prorates, in both directions`() = runBlocking {
        signIn()
        repeat(4) { server.enqueue(planAnswer(PRO_MONTH)) }

        repository.switchTo(activity, FakeBilling.YEARLY, from = FakeBilling.active(productId = "pro", basePlanId = "monthly"))
        // RevenueCat may name the product with its base plan; only the subscription goes.
        repository.switchTo(activity, FakeBilling.MONTHLY, from = FakeBilling.active(productId = "pro:annual", basePlanId = "annual"))

        val change = PlanChange(oldProductId = "pro", replacementMode = StoreReplacementMode.WITHOUT_PRORATION)
        assertEquals(listOf(FakeBilling.YEARLY to change, FakeBilling.MONTHLY to change), billing.purchases)
        assertEquals("the proxy is asked before and after each switch", 4, server.requestCount)
    }

    @Test
    fun `a restore has the proxy ask again, and one refused for another account moves nothing`() = runBlocking {
        signIn()
        billing.membership = FakeBilling.active()
        server.enqueue(json(200, """{"purchaser":"$PURCHASER","usage":$PRO_MONTH,"aiEnabled":true}"""))

        val restored = repository.restore()

        assertEquals(Plan.PRO, restored.plan)
        assertEquals("/v1/plan", server.takeRequest().requestUrl?.encodedPath)

        billing.failure = FakeBilling.refused(PurchasesErrorCode.ReceiptAlreadyInUseError)
        try {
            repository.restore()
            fail("RevenueCat keeps the subscription with the account that bought it")
        } catch (e: PurchasesException) {
            assertEquals(PurchasesErrorCode.ReceiptAlreadyInUseError, e.code)
        }
        assertEquals("nothing is asked of the proxy after a refusal", 1, server.requestCount)
    }

    @Test
    fun `the proxy's plan is kept with its purchaser, and a refused session turns into a sign-in to renew`() = runBlocking {
        // Signed in before the app kept a purchaser.
        proxy.account.saveSession(SESSION, signedInAs = "minh.tran@example.com")
        server.enqueue(json(200, """{"purchaser":"$PURCHASER","usage":$FREE_MONTH,"aiEnabled":true}"""))
        server.enqueue(json(401, """{"error":"session"}"""))

        val calls = repository.refreshPlan()

        assertEquals(Plan.FREE, calls.plan)
        assertEquals(PURCHASER, proxy.account.account.value.purchaser)
        assertEquals(BigDecimal(7), proxy.account.account.value.callsLeft?.remaining)
        try {
            repository.refreshPlan()
            fail("A refused session is no plan")
        } catch (e: ProxyException) {
            assertEquals("session", e.code)
        }
        assertTrue(proxy.account.account.value.signInExpired)
        assertNull(proxy.account.account.value.purchaser)
    }

    @Test
    fun `a check of the plan answered without the purchaser keeps the one stored for the session`() = runBlocking {
        signIn()
        server.enqueue(json(200, """{"usage":$PRO_MONTH,"aiEnabled":true}"""))

        val calls = repository.refreshPlan()

        assertEquals(Plan.PRO, calls.plan)
        assertEquals(PURCHASER, proxy.account.account.value.purchaser)
        assertEquals(Plan.PRO, proxy.account.account.value.callsLeft?.plan)
    }

    @Test
    fun `an answer that arrives after signing out is nobody's`() = runBlocking {
        signIn()
        val late = json(200, """{"purchaser":"$OTHER_PURCHASER","usage":$PRO_MONTH,"aiEnabled":true}""")
        server.enqueue(late.setHeadersDelay(500, TimeUnit.MILLISECONDS))
        val asked = scope.launch { repository.refreshPlan() }
        awaitOnMain { server.requestCount == 1 }

        proxy.account.clearSession()
        awaitOnMain { !asked.isActive }

        assertNull(proxy.account.account.value.callsLeft)
        assertNull(proxy.account.account.value.purchaser)
    }

    @Test
    fun `while the proxy cannot check subscriptions nothing is bought or switched, until it checks again`() = runBlocking {
        signIn()
        // The screen opened while the proxy could still check them.
        server.enqueue(planAnswer(FREE_MONTH))
        repository.refreshPlan()
        assertTrue(repository.onSale.value)

        repeat(2) { server.enqueue(notConfigured()) }
        for (attempt in listOf<suspend () -> Unit>(
            { repository.buy(activity, FakeBilling.MONTHLY) },
            { repository.switchTo(activity, FakeBilling.YEARLY, from = FakeBilling.active()) },
        )) {
            try {
                attempt()
                fail("Nothing is sold that the proxy would not count")
            } catch (e: NotOnSaleException) {
                // The screen says that Pro cannot be bought right now.
            }
        }
        assertFalse(repository.onSale.value)
        assertTrue(billing.purchases.isEmpty())

        server.enqueue(notConfigured())
        billing.membership = FakeBilling.active()
        val restored = repository.restore()
        assertEquals("a restore stays possible", 1, billing.restores)
        assertNull(restored.plan)

        server.enqueue(planAnswer(FREE_MONTH))
        server.enqueue(planAnswer(PRO_MONTH))
        assertEquals(Plan.PRO, repository.buy(activity, FakeBilling.MONTHLY).plan)
        assertTrue(repository.onSale.value)
    }

    @Test
    fun `a check before a purchase refused for a limit or a server problem does not stop the purchase`() = runBlocking {
        signIn()
        val refusals = listOf(
            json(429, """{"error":"rate_limited"}""").setHeader("Retry-After", "1200"),
            json(502, """{"error":"subscription_unavailable"}"""),
            json(500, """{"error":"internal"}"""),
        )
        for (refusal in refusals) {
            server.enqueue(refusal)
            server.enqueue(planAnswer(PRO_MONTH))

            repository.buy(activity, FakeBilling.MONTHLY)
        }

        assertEquals(3, billing.purchases.size)
        assertTrue(repository.onSale.value)
    }

    @Test
    fun `without the proxy just then, its last answer decides whether Pro is on sale`() = runBlocking {
        signIn()
        // Pro was not on sale when the proxy was last asked.
        server.enqueue(notConfigured())
        try {
            repository.refreshPlan()
            fail("The proxy cannot check subscriptions")
        } catch (e: ProxyException) {
            assertEquals("not_configured", e.code)
        }
        server.shutdown()

        try {
            repository.buy(activity, FakeBilling.MONTHLY)
            fail("Nothing is sold that the proxy would not count")
        } catch (e: NotOnSaleException) {
            // As the proxy said last.
        }
        assertTrue(billing.purchases.isEmpty())

        // When it said nothing of the kind, the purchase goes ahead, and the proxy counts it at its next check.
        val change = BillingRepository(billing, proxy.api, proxy.account, canSignIn = true).buy(activity, FakeBilling.MONTHLY)
        assertEquals(1, billing.purchases.size)
        assertNull(change.plan)
    }

    @Test
    fun `a sign-out while Google Play's purchase or a restore is open waits until it is done`() {
        scope.launch { repository.follow() }
        val steps = listOf<suspend () -> Unit>({ repository.buy(activity, FakeBilling.MONTHLY) }, { repository.restore() })
        for (step in steps) {
            signIn()
            idle()
            // The check of the plan before a purchase; a restore asks nothing first.
            server.enqueue(planAnswer(FREE_MONTH))
            val open = CompletableDeferred<Unit>()
            billing.gate = open
            val opened = billing.purchases.size + billing.restores
            val done = scope.launch { step() }
            awaitOnMain { billing.purchases.size + billing.restores > opened }

            proxy.account.clearSession()
            idle()
            assertEquals("the store still runs as the buyer while Google Play is open", PURCHASER, billing.user)

            open.complete(Unit)
            awaitOnMain { done.isCompleted }
            assertNull("the sign-out stops it once Google Play is done", billing.user)
            assertEquals("stop", billing.log.last())
        }
    }

    @Test
    fun `a purchase left unsent is sent as its buyer before the store stops`() {
        val unsent = mapOf<String, () -> Unit>(
            "its sending failed" to { billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.NetworkError) },
            "its payment is pending" to { billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.PaymentPendingError) },
            "Pro was worked out on the phone" to { billing.nextPurchaseOffline = true },
        )
        scope.launch { repository.follow() }
        for ((case, leaveUnsent) in unsent) {
            signIn()
            idle()
            leaveUnsent()
            buyAllowingFailure()

            proxy.account.clearSession()
            idle()

            assertEquals(case, listOf("sync as $PURCHASER", "stop"), billing.log.takeLast(2))
        }
    }

    @Test
    fun `a purchase left unsent is sent as its buyer before another account's purchaser takes the store`() {
        scope.launch { repository.follow() }
        signIn()
        idle()
        billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.PaymentPendingError)
        buyAllowingFailure()

        proxy.account.saveSession(OTHER_SESSION, signedInAs = "lan@example.com", purchaser = OTHER_PURCHASER)
        idle()

        assertEquals(listOf("sync as $PURCHASER", "start $OTHER_PURCHASER"), billing.log.takeLast(2))
        assertEquals(OTHER_PURCHASER, billing.user)
    }

    @Test
    fun `nothing is sent before the store stops when no purchase was left unsent`() {
        val sent = mapOf<String, () -> Unit>(
            "RevenueCat took it" to {},
            "the person cancelled it" to {
                billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.PurchaseCancelledError, cancelled = true)
            },
            "RevenueCat keeps it with another account" to {
                billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.ReceiptAlreadyInUseError)
            },
        )
        scope.launch { repository.follow() }
        for ((case, outcome) in sent) {
            signIn()
            idle()
            outcome()
            buyAllowingFailure()

            proxy.account.clearSession()
            idle()

            assertEquals(case, "stop", billing.log.last())
        }
        // Signed in and out with no purchase at all.
        signIn()
        idle()
        proxy.account.clearSession()
        idle()
        assertTrue(billing.log.none { it.startsWith("sync") })
    }

    @Test
    fun `the store stops all the same when a purchase left unsent cannot be sent within a few seconds`() {
        scope.launch { repository.follow() }
        signIn()
        idle()
        billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.NetworkError)
        buyAllowingFailure()
        billing.syncHangs = true

        proxy.account.clearSession()
        idle()
        assertEquals("sync as $PURCHASER", billing.log.last())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals("still trying after 4 seconds", PURCHASER, billing.user)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals("stop", billing.log.last())
        assertNull(billing.user)

        // A sync that fails stops the store at once.
        signIn()
        idle()
        billing.failure = FakeBilling.purchaseFailed(PurchasesErrorCode.NetworkError)
        buyAllowingFailure()
        billing.syncHangs = false
        billing.syncFailure = FakeBilling.refused(PurchasesErrorCode.NetworkError)

        proxy.account.clearSession()
        idle()
        assertEquals(listOf("sync as $PURCHASER", "stop"), billing.log.takeLast(2))
    }

    @Test
    fun `a build without RevenueCat's key, or one that cannot sign in, sells no Pro and never starts a store`() {
        val withoutKey = BillingRepository(billing = null, proxy.api, proxy.account, canSignIn = true)
        val withoutSignIn = BillingRepository(billing, proxy.api, proxy.account, canSignIn = false)
        proxy.account.saveSession(SESSION, purchaser = PURCHASER)

        scope.launch { withoutKey.follow() }
        scope.launch { withoutSignIn.follow() }
        idle()

        assertFalse(withoutKey.sellsPro)
        assertFalse(withoutSignIn.sellsPro)
        assertTrue(repository.sellsPro)
        assertTrue(billing.starts.isEmpty())
    }

    private fun signIn() = proxy.account.saveSession(SESSION, signedInAs = "minh.tran@example.com", purchaser = PURCHASER)

    /** Buys the monthly plan, whatever comes of it; the proxy is asked before, and after a purchase that goes through. */
    private fun buyAllowingFailure() = runBlocking {
        repeat(2) { server.enqueue(planAnswer(PRO_MONTH)) }
        try {
            repository.buy(activity, FakeBilling.MONTHLY)
        } catch (e: PurchasesException) {
            // What the test is about comes after.
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    /** The proxy's answer to `POST /v1/plan` with [usage], for the account signed in with [PURCHASER]. */
    private fun planAnswer(usage: String): MockResponse = json(200, """{"purchaser":"$PURCHASER","usage":$usage,"aiEnabled":true}""")

    /** The proxy's refusal to check a plan while it has no key for RevenueCat. */
    private fun notConfigured(): MockResponse = json(503, """{"error":"not_configured"}""")

    private companion object {
        val SESSION = Session("signed-session", expiresAt = 4_102_444_800_000L)
        val OTHER_SESSION = Session("other-session", expiresAt = 4_102_444_800_000L)
        val PURCHASER = "a".repeat(64)
        val OTHER_PURCHASER = "b".repeat(64)
        const val PRO_MONTH = """{"limit":300,"used":12,"remaining":288,"period":"2026-11","resetsAt":1796083200000,"plan":"pro"}"""
        const val FREE_MONTH = """{"limit":10,"used":3,"remaining":7,"period":"2026-11","resetsAt":1796083200000,"plan":"free"}"""
    }
}
