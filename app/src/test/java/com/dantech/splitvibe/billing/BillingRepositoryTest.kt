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
import java.util.concurrent.TimeUnit
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
        server.enqueue(json(200, """{"purchaser":"$PURCHASER","usage":$PRO_MONTH,"aiEnabled":true}"""))

        val change = repository.buy(activity, FakeBilling.MONTHLY)

        assertEquals(listOf(FakeBilling.MONTHLY to null), billing.purchases)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/plan", request.requestUrl?.encodedPath)
        assertEquals("{}", request.body.readUtf8())
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
        assertEquals(Plan.PRO, change.plan)
        assertTrue(change.membership?.active == true)
        val calls = proxy.account.account.value.callsLeft
        assertEquals(BigDecimal(300), calls?.limit)
        assertEquals(Plan.PRO, calls?.plan)
    }

    @Test
    fun `a purchase that went through stays bought when the proxy cannot be asked just then`() = runBlocking {
        signIn()
        server.enqueue(json(502, """{"error":"subscription_unavailable"}"""))

        val change = repository.buy(activity, FakeBilling.YEARLY)

        assertNull("the proxy has not counted it yet", change.plan)
        assertTrue(change.membership?.active == true)
        assertEquals(1, billing.purchases.size)
    }

    @Test
    fun `a switch of plans names the subscription without its base plan, and never prorates, in both directions`() = runBlocking {
        signIn()
        repeat(2) { server.enqueue(json(200, """{"purchaser":"$PURCHASER","usage":$PRO_MONTH,"aiEnabled":true}""")) }

        repository.switchTo(activity, FakeBilling.YEARLY, from = FakeBilling.active(productId = "pro", basePlanId = "monthly"))
        // RevenueCat may name the product with its base plan; only the subscription goes.
        repository.switchTo(activity, FakeBilling.MONTHLY, from = FakeBilling.active(productId = "pro:annual", basePlanId = "annual"))

        val change = PlanChange(oldProductId = "pro", replacementMode = StoreReplacementMode.WITHOUT_PRORATION)
        assertEquals(listOf(FakeBilling.YEARLY to change, FakeBilling.MONTHLY to change), billing.purchases)
        assertEquals("each switch has the proxy ask again", 2, server.requestCount)
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

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        val SESSION = Session("signed-session", expiresAt = 4_102_444_800_000L)
        val OTHER_SESSION = Session("other-session", expiresAt = 4_102_444_800_000L)
        val PURCHASER = "a".repeat(64)
        val OTHER_PURCHASER = "b".repeat(64)
        const val PRO_MONTH = """{"limit":300,"used":12,"remaining":288,"period":"2026-11","resetsAt":1796083200000,"plan":"pro"}"""
        const val FREE_MONTH = """{"limit":10,"used":3,"remaining":7,"period":"2026-11","resetsAt":1796083200000,"plan":"free"}"""
    }
}
