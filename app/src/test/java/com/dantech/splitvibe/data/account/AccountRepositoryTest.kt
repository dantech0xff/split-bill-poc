package com.dantech.splitvibe.data.account

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.FakeGoogleAccounts
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.contentWords
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.domain.payments.PaymentMethod
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val google = FakeGoogleAccounts()
    private val storage = TestStorage(context)
    private val cache = ProxyCache(storage.database.proxyCache())
    private val settings = SettingsStore(context)
    private val recurring = RecurringBillRepository(storage.database, SpendingFixtures.clock, contentWords())
    private val methods = PaymentMethodRepository(storage.database)
    private lateinit var proxy: TestProxy
    private lateinit var repository: AccountRepository

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        repository = proxy.accountRepository(google, cache, storage.phoneData(context, settings))
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `signing in trades Google's ID token for the proxy's session and keeps the account's address`() = runBlocking {
        server.enqueue(json(200, """{"session":"signed-session","expiresAt":${FAR_FUTURE},"usage":$MONTH}"""))

        repository.signIn(context)

        assertEquals("""{"idToken":"google-id-token"}""", server.takeRequest().body.readUtf8())
        val account = proxy.account.account.value
        assertEquals(Session("signed-session", FAR_FUTURE), account.session)
        assertEquals("minh.tran@example.com", account.signedInAs)
        assertEquals(BigDecimal(10), account.callsLeft?.remaining)
    }

    @Test
    fun `signing in keeps the purchaser the proxy gave, and signing out and deleting the account forget it`() = runBlocking {
        server.enqueue(json(200, """{"session":"signed-session","expiresAt":${FAR_FUTURE},"usage":$MONTH,"purchaser":"$PURCHASER"}"""))
        server.enqueue(json(200, """{"session":"signed-session","expiresAt":${FAR_FUTURE},"usage":$MONTH,"purchaser":"$PURCHASER"}"""))
        server.enqueue(MockResponse().setResponseCode(204))

        repository.signIn(context)
        assertEquals(PURCHASER, proxy.account.account.value.purchaser)
        repository.signOut(context)
        assertNull(proxy.account.account.value.purchaser)

        repository.signIn(context)
        repository.deleteAccount(context, withPhoneData = false)
        assertNull(proxy.account.account.value.purchaser)
    }

    @Test
    fun `the calls left bring the purchaser of a sign-in from before the app kept one, and never an install's`() = runBlocking {
        val trial = """{"limit":3,"used":1,"remaining":2,"period":"trial","resetsAt":null}"""
        server.enqueue(json(200, """{"usage":$trial,"purchaser":"$PURCHASER"}"""))
        repository.refresh()
        assertNull("nobody is signed in", proxy.account.account.value.purchaser)

        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        server.enqueue(json(200, """{"usage":$MONTH,"aiEnabled":true,"purchaser":"$PURCHASER"}"""))
        repository.refresh()
        assertEquals(PURCHASER, proxy.account.account.value.purchaser)
    }

    @Test
    fun `a sign-in answered without the purchaser works, the next count brings it, and a count without it keeps it`() = runBlocking {
        // For a few minutes after a deploy, a part of the proxy that still runs the code before answers without it.
        server.enqueue(json(200, """{"session":"signed-session","expiresAt":${FAR_FUTURE},"usage":$MONTH}"""))
        server.enqueue(json(200, """{"usage":$MONTH,"aiEnabled":true,"purchaser":"$PURCHASER"}"""))
        server.enqueue(json(200, """{"usage":$MONTH,"aiEnabled":true}"""))

        repository.signIn(context)
        assertEquals("signed-session", proxy.account.account.value.session?.token)
        assertNull(proxy.account.account.value.purchaser)

        repository.refresh()
        assertEquals(PURCHASER, proxy.account.account.value.purchaser)
        repository.refresh()
        assertEquals("an answer without it keeps the one stored for the session", PURCHASER, proxy.account.account.value.purchaser)
    }

    @Test
    fun `a deletion the proxy limits deletes nothing and keeps the sign-in, with when to try again`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com", purchaser = PURCHASER)
        server.enqueue(json(429, """{"error":"rate_limited"}""").setHeader("Retry-After", "2400"))

        try {
            repository.deleteAccount(context, withPhoneData = false)
            fail("A limited deletion deletes nothing")
        } catch (e: ProxyException) {
            assertEquals("rate_limited", e.code)
            assertEquals(2400L, e.retryAfterSeconds)
        }

        val account = proxy.account.account.value
        assertEquals("signed-session", account.session?.token)
        assertFalse(account.signInExpired)
        assertEquals(PURCHASER, account.purchaser)
        assertFalse(google.forgotten)
    }

    @Test
    fun `nothing changes when the person closes Google's picker`() = runBlocking {
        google.failure = GoogleSignInException(GoogleSignInException.Reason.CANCELLED)

        try {
            repository.signIn(context)
            fail("A closed picker must not sign anyone in")
        } catch (e: GoogleSignInException) {
            assertEquals(GoogleSignInException.Reason.CANCELLED, e.reason)
        }

        assertNull(proxy.account.account.value.session)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a sign-in the proxy refuses keeps the phone signed out`() = runBlocking {
        server.enqueue(json(401, """{"error":"id_token"}"""))

        try {
            repository.signIn(context)
            fail("A refused token must not sign anyone in")
        } catch (e: ProxyException) {
            assertEquals("id_token", e.code)
        }

        assertNull(proxy.account.account.value.session)
    }

    @Test
    fun `signing out forgets the session here and on the phone's account picker`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")

        repository.signOut(context)

        assertNull(proxy.account.account.value.session)
        assertNull(proxy.account.account.value.signedInAs)
        assertTrue(google.forgotten)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `deleting the account asks the proxy first and forgets the session once it is done`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        server.enqueue(MockResponse().setResponseCode(204))

        repository.deleteAccount(context, withPhoneData = false)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
        assertNull(proxy.account.account.value.session)
        assertTrue(google.forgotten)
    }

    @Test
    fun `an account the proxy could not delete stays signed in, unless its session ran out`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        server.enqueue(json(500, """{"error":"internal"}"""))
        server.enqueue(json(401, """{"error":"session"}"""))

        runCatching { repository.deleteAccount(context, withPhoneData = false) }
        assertEquals("signed-session", proxy.account.account.value.session?.token)
        assertFalse(google.forgotten)

        runCatching { repository.deleteAccount(context, withPhoneData = false) }
        assertNull(proxy.account.account.value.session)
        assertTrue("signing in again can delete it", proxy.account.account.value.signInExpired)
        assertEquals("minh.tran@example.com", proxy.account.account.value.signedInAs)
    }

    @Test
    fun `a sign-in that has run out is not asked about as the install, and signing in again ends it`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        server.enqueue(json(401, """{"error":"session"}"""))
        server.enqueue(json(200, """{"session":"new-session","expiresAt":${FAR_FUTURE},"usage":$MONTH}"""))

        repository.refresh()
        assertTrue(proxy.account.account.value.signInExpired)
        repository.refresh()
        assertEquals("the second count is not asked for at all", 1, server.requestCount)

        repository.signIn(context)

        assertEquals("Bearer signed-session", server.takeRequest().getHeader("Authorization"))
        val signIn = server.takeRequest()
        assertEquals("/v1/session", signIn.requestUrl?.encodedPath)
        assertNull("signing in carries Google's token, not the old session", signIn.getHeader("Authorization"))
        assertFalse(proxy.account.account.value.signInExpired)
        assertEquals("new-session", proxy.account.account.value.session?.token)
    }

    @Test
    fun `signing out after the sign-in ran out goes back to this phone's free calls`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE))
        proxy.account.expireSession()
        server.enqueue(json(200, """{"usage":{"limit":3,"used":1,"remaining":2,"period":"trial","resetsAt":null},"aiEnabled":true}"""))

        repository.signOut(context)
        repository.refresh()

        assertFalse(proxy.account.account.value.signInExpired)
        assertNull("the count is the install's", server.takeRequest().getHeader("Authorization"))
        assertEquals(BigDecimal(2), proxy.account.account.value.callsLeft?.remaining)
    }

    @Test
    fun `asks the proxy for the calls left and whether AI is on`() = runBlocking {
        server.enqueue(json(200, """{"usage":{"limit":3,"used":1,"remaining":2,"period":"trial","resetsAt":null},"aiEnabled":false}"""))

        repository.refresh()

        assertEquals(BigDecimal(2), proxy.account.account.value.callsLeft?.remaining)
        assertEquals(AiStatus.OFF, proxy.account.account.value.ai)
    }

    @Test
    fun `cannot sign in without the proxy's address or Google's client ID`() {
        val phone = storage.phoneData(context, settings)
        val withoutProxy = proxy.accountRepository(google, cache, phone, proxy = ProxyApi("", proxy.account, appVersion = 1))
        val withoutClientId = proxy.accountRepository(google = null, cache = cache, phone = phone)

        assertTrue(repository.canSignIn)
        assertFalse(withoutProxy.canSignIn)
        assertFalse(withoutClientId.canSignIn)
    }

    @Test
    fun `groups and requests kept for one account are gone after signing in, signing out and deleting the account`() = runBlocking {
        suspend fun keepSomething() = cache.put("groups", ListSerializer(String.serializer()), listOf("kept"), cache.generation)
        suspend fun kept() = cache.get("groups", ListSerializer(String.serializer()))

        keepSomething()
        server.enqueue(json(200, """{"session":"signed-session","expiresAt":${FAR_FUTURE},"usage":$MONTH}"""))
        repository.signIn(context)
        assertNull("signing in", kept())

        keepSomething()
        repository.signOut(context)
        assertNull("signing out", kept())

        server.enqueue(json(200, """{"session":"signed-session","expiresAt":${FAR_FUTURE},"usage":$MONTH}"""))
        repository.signIn(context)
        keepSomething()
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deleteAccount(context, withPhoneData = false)
        assertNull("deleting the account", kept())
    }

    @Test
    fun `deleting the account with the phone's data deletes what only the phone keeps, after the proxy deleted it`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        val page = keepOnThePhone()
        server.enqueue(MockResponse().setResponseCode(204))

        repository.deleteAccount(context, withPhoneData = true)

        assertEquals("DELETE", server.takeRequest().method)
        val nothing = OnThePhone(bills = 0, recurring = 0, people = 0, methods = 0, groups = false, sharedFiles = 0, name = "")
        assertEquals(nothing, onThePhone())
        assertNull("the photo of its page", storage.pages.load(page))
        assertEquals("the currency stays", "EUR", settings.settings.value.localCurrency)
        assertTrue("the consent to the speech service stays", settings.settings.value.speechServiceAllowed)
        assertNull(proxy.account.account.value.session)
    }

    @Test
    fun `deleting the account without the phone's data keeps it, but not the groups and requests of the account`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        val page = keepOnThePhone()
        server.enqueue(MockResponse().setResponseCode(204))

        repository.deleteAccount(context, withPhoneData = false)

        val kept = OnThePhone(bills = 2, recurring = 1, people = 1, methods = 1, groups = false, sharedFiles = 3, name = "Minh")
        assertEquals(kept, onThePhone())
        assertNotNull(storage.pages.load(page))
        assertNull(proxy.account.account.value.session)
    }

    @Test
    fun `an account the proxy did not delete deletes nothing on the phone, also when the person asked to`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        val page = keepOnThePhone()
        val refusals = mapOf(
            "internal" to json(500, """{"error":"internal"}"""),
            "rate_limited" to json(429, """{"error":"rate_limited"}""").setHeader("Retry-After", "2400"),
            "subscription_unavailable" to json(502, """{"error":"subscription_unavailable"}"""),
        )

        for ((code, refusal) in refusals) {
            server.enqueue(refusal)
            try {
                repository.deleteAccount(context, withPhoneData = true)
                fail("A refused deletion deletes nothing")
            } catch (e: ProxyException) {
                assertEquals(code, e.code)
            }

            val kept = OnThePhone(bills = 2, recurring = 1, people = 1, methods = 1, groups = true, sharedFiles = 3, name = "Minh")
            assertEquals(code, kept, onThePhone())
            assertNotNull(storage.pages.load(page))
            assertEquals("signed-session", proxy.account.account.value.session?.token)
        }
    }

    /** How much of what [keepOnThePhone] keeps is still there. */
    private data class OnThePhone(
        val bills: Int,
        val recurring: Int,
        val people: Int,
        val methods: Int,
        val groups: Boolean,
        val sharedFiles: Int,
        val name: String,
    )

    private fun onThePhone() = runBlocking {
        OnThePhone(
            bills = storage.bills.listings().first().size,
            recurring = recurring.all.first().size,
            people = storage.people.people.first().size,
            methods = methods.all.first().size,
            groups = cache.get("groups", ListSerializer(String.serializer())) != null,
            sharedFiles = SHARED_FILES.count { File(context.cacheDir, it).exists() },
            name = settings.settings.value.myName,
        )
    }

    /**
     * Keeps what a person who used the app has on the phone: a finished bill with the photo of its
     * page and the person split with, a draft, a recurring bill, a way to get paid, the account's
     * groups, copies shared to other apps, their name, the currency and the consent to the speech
     * service. Returns the name of the page's photo.
     */
    private fun keepOnThePhone(): String = runBlocking {
        val page = storage.pages.save(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        val lunch = SpendingFixtures.finished("lunch", "2026-11-14", "Pho 24", BillCategory.FOOD_AND_DRINK, 120_000, listOf("Lan"))
        storage.bills.save(lunch.copy(pages = listOf(page)))
        storage.bills.save(lunch.copy(id = "draft", status = BillStatus.DRAFT))
        recurring.create(lunch, "Lunch", RecurrenceSchedule(Frequency.MONTHLY, LocalDate.of(2026, 11, 14)), LocalDate.of(2026, 12, 14))
        methods.add(PaymentMethod.VietQr("970416", "257678859", "DANA NGUYEN"))
        cache.put("groups", ListSerializer(String.serializer()), listOf("kept"), cache.generation)
        SHARED_FILES.forEach { path -> File(context.cacheDir, path).apply { parentFile?.mkdirs() }.writeText("shared") }
        settings.saveName("Minh")
        settings.save("EUR")
        settings.allowSpeechService(true)
        page
    }

    @Test
    fun `an answer that arrives after signing out is not kept for whoever signs in next`() = runBlocking {
        val asked = cache.generation

        repository.signOut(context)
        cache.put("groups", ListSerializer(String.serializer()), listOf("the earlier account's group"), asked)

        assertNull(cache.get("groups", ListSerializer(String.serializer())))
    }

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        const val FAR_FUTURE = 4_102_444_800_000L
        const val MONTH = """{"limit":10,"used":0,"remaining":10,"period":"2026-11","resetsAt":1796083200000}"""
        val PURCHASER = "9".repeat(64)

        /** A photo from the camera, a shared card and an export, in the cache. */
        val SHARED_FILES = listOf("bill_photos/photo.jpg", "shared/card.png", "exports/1/spending-2026-11.pdf")
    }
}
