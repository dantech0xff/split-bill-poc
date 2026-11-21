package com.dantech.splitvibe.data.account

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.FakeGoogleAccounts
import com.dantech.splitvibe.R
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
import java.util.concurrent.TimeUnit
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
import org.robolectric.Shadows.shadowOf

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
        repository.deleteAccount(withPhoneData = false).join()
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

        repository.deleteAccount(withPhoneData = false).join()

        val refusal = refusal()
        assertEquals("rate_limited", refusal.code)
        assertEquals(2400L, refusal.retryAfterSeconds)
        assertNull("nothing is left to finish", DeletionStore(context).pending)
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

        repository.deleteAccount(withPhoneData = false).join()

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
        assertNull(proxy.account.account.value.session)
        assertTrue(google.forgotten)
        assertEquals(Deletion.Ended(DeletionOutcome.DELETED), repository.deletion.value)
    }

    @Test
    fun `an account the proxy could not delete stays signed in, unless its session ran out`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        server.enqueue(json(500, """{"error":"internal"}"""))
        server.enqueue(json(401, """{"error":"session"}"""))

        repository.deleteAccount(withPhoneData = false).join()
        assertEquals("signed-session", proxy.account.account.value.session?.token)
        assertFalse(google.forgotten)

        repository.deleteAccount(withPhoneData = false).join()
        assertEquals(ProxyApi.SESSION_REFUSED, refusal().code)
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
        repository.deleteAccount(withPhoneData = false).join()
        assertNull("deleting the account", kept())
    }

    @Test
    fun `deleting the account with the phone's data deletes what only the phone keeps, after the proxy deleted it`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        val page = keepOnThePhone()
        server.enqueue(MockResponse().setResponseCode(204))

        repository.deleteAccount(withPhoneData = true).join()

        assertEquals("DELETE", server.takeRequest().method)
        val nothing = OnThePhone(bills = 0, recurring = 0, people = 0, methods = 0, groups = false, sharedFiles = 0, name = "")
        assertEquals(nothing, onThePhone())
        assertNull("the photo of its page", storage.pages.load(page))
        assertEquals("the currency stays", "EUR", settings.settings.value.localCurrency)
        assertTrue("the consent to the speech service stays", settings.settings.value.speechServiceAllowed)
        assertTrue("so does the consent to the AI", settings.settings.value.aiAllowed)
        assertNull(proxy.account.account.value.session)
        assertEquals(Deletion.Ended(DeletionOutcome.DELETED_WITH_PHONE_DATA), repository.deletion.value)
    }

    @Test
    fun `deleting the account without the phone's data keeps it, but not the groups and requests of the account`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        val page = keepOnThePhone()
        server.enqueue(MockResponse().setResponseCode(204))

        repository.deleteAccount(withPhoneData = false).join()

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
            repository.deleteAccount(withPhoneData = true).join()
            assertEquals(code, refusal().code)

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
     * groups, copies shared to other apps, their name, the currency and the consents to the speech
     * service and to the AI. Returns the name of the page's photo.
     */
    private fun keepOnThePhone(): String = runBlocking {
        val page = storage.pages.save(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        val lunch = SpendingFixtures.finished("lunch", "2026-11-14", "Pho 24", BillCategory.FOOD_AND_DRINK, 120_000, listOf("Lan"))
        storage.bills.save(lunch.copy(pages = listOf(page)))
        storage.bills.save(lunch.copy(id = "draft", status = BillStatus.DRAFT))
        recurring.create(lunch, "Lunch", RecurrenceSchedule(Frequency.MONTHLY, LocalDate.of(2026, 11, 14)), LocalDate.of(2026, 12, 14))
        methods.add(PaymentMethod.VietQr("970416", "000000000", "DANA NGUYEN"))
        cache.put("groups", ListSerializer(String.serializer()), listOf("kept"), cache.generation)
        SHARED_FILES.forEach { path -> File(context.cacheDir, path).apply { parentFile?.mkdirs() }.writeText("shared") }
        settings.saveName("Minh")
        settings.save("EUR")
        settings.allowSpeechService(true)
        settings.allowAi(true)
        page
    }

    @Test
    fun `an answer that arrives after signing out is not kept for whoever signs in next`() = runBlocking {
        val asked = cache.generation

        repository.signOut(context)
        cache.put("groups", ListSerializer(String.serializer()), listOf("the earlier account's group"), asked)

        assertNull(cache.get("groups", ListSerializer(String.serializer())))
    }

    @Test
    fun `the person's choice is on disk before the deletion goes out, and how it ended stays until it is said`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        keepOnThePhone()
        server.enqueue(MockResponse().setResponseCode(204).setHeadersDelay(500, TimeUnit.MILLISECONDS))

        val deletion = repository.deleteAccount(withPhoneData = true)

        assertEquals(Deletion.Running, repository.deletion.value)
        assertEquals(PendingDeletion(withPhoneData = true, proxyDeleted = false), DeletionStore(context).pending)
        val written = File(context.dataDir, "shared_prefs/account_deletion.xml").readText()
        assertTrue("written to the file before the answer: $written", written.contains("""name="with_phone_data" value="true""""))

        deletion.join()
        assertNull(DeletionStore(context).pending)
        assertEquals(OnThePhone(0, 0, 0, 0, groups = false, sharedFiles = 0, name = ""), onThePhone())
        // As the app starts again before Settings said it.
        val later = proxy.accountRepository(google, cache, storage.phoneData(context, settings))
        assertEquals(Deletion.Ended(DeletionOutcome.DELETED_WITH_PHONE_DATA), later.deletion.value)
        later.deletionShown()
        assertNull(later.deletion.value)
        assertNull(proxy.accountRepository(google, cache, storage.phoneData(context, settings)).deletion.value)
    }

    @Test
    fun `a deletion the app was ended in before the proxy answered is asked again at the next start, with the phone's data`() =
        runBlocking {
            proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
            val page = keepOnThePhone()
            DeletionStore(context).begin(withPhoneData = true)
            server.enqueue(MockResponse().setResponseCode(204))

            checkNotNull(repository.finishInterruptedDeletion()).join()

            assertEquals("DELETE", server.takeRequest().method)
            assertEquals(OnThePhone(0, 0, 0, 0, groups = false, sharedFiles = 0, name = ""), onThePhone())
            assertNull(storage.pages.load(page))
            assertNull(proxy.account.account.value.session)
            assertTrue(google.forgotten)
            assertEquals(Deletion.Ended(DeletionOutcome.DELETED_WITH_PHONE_DATA), repository.deletion.value)
            assertNull(DeletionStore(context).pending)
        }

    @Test
    fun `a deletion the app was ended in after the proxy deleted the account is finished on the phone without asking again`() =
        runBlocking {
            proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
            keepOnThePhone()
            DeletionStore(context).apply {
                begin(withPhoneData = true)
                proxyDeleted()
            }

            val finishing = checkNotNull(repository.finishInterruptedDeletion())
            assertNull("the deleted account's session is gone at once", proxy.account.account.value.session)
            finishing.join()

            assertEquals(0, server.requestCount)
            assertEquals(OnThePhone(0, 0, 0, 0, groups = false, sharedFiles = 0, name = ""), onThePhone())
            assertEquals(Deletion.Ended(DeletionOutcome.DELETED_WITH_PHONE_DATA), repository.deletion.value)
        }

    @Test
    fun `a deletion cut off whose session the proxy no longer takes deletes nothing on the phone and says so`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        keepOnThePhone()
        DeletionStore(context).begin(withPhoneData = true)
        server.enqueue(json(401, """{"error":"session"}"""))

        checkNotNull(repository.finishInterruptedDeletion()).join()

        assertEquals(Deletion.Ended(DeletionOutcome.NOT_CONFIRMED), repository.deletion.value)
        assertEquals(OnThePhone(2, 1, 1, 1, groups = true, sharedFiles = 3, name = "Minh"), onThePhone())
        assertTrue("signing in again can delete it", proxy.account.account.value.signInExpired)
        assertNull(DeletionStore(context).pending)
    }

    @Test
    fun `a deletion cut off waits for the next start while the proxy cannot be reached`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        DeletionStore(context).begin(withPhoneData = false)
        server.shutdown()

        checkNotNull(repository.finishInterruptedDeletion()).join()

        assertNull("there is nothing to say yet", repository.deletion.value)
        assertEquals(PendingDeletion(withPhoneData = false, proxyDeleted = false), DeletionStore(context).pending)
        assertEquals("signed-session", proxy.account.account.value.session?.token)

        DeletionStore(context).end(null)
        assertNull("a phone with nothing to finish starts nothing", repository.finishInterruptedDeletion())
    }

    @Test
    fun `a photo of a page that cannot be deleted is said, and the rest of the phone's data still goes`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        keepOnThePhone()
        // A page the phone cannot delete, as a folder that is not empty is.
        File(context.filesDir, "bill_pages/${"0".repeat(36)}.jpg/stuck").apply { parentFile?.mkdirs() }.writeText("stuck")
        server.enqueue(MockResponse().setResponseCode(204))

        repository.deleteAccount(withPhoneData = true).join()

        assertEquals(Deletion.Ended(DeletionOutcome.PHONE_DATA_NOT_DELETED), repository.deletion.value)
        assertEquals(OnThePhone(0, 0, 0, 0, groups = false, sharedFiles = 0, name = ""), onThePhone())
        assertNull(proxy.account.account.value.session)
        assertTrue(google.forgotten)
    }

    @Test
    fun `groups and requests that cannot be deleted after the proxy deleted the account are said, not taken as a failed deletion`() =
        runBlocking {
            proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
            keepOnThePhone()
            val broken = TestStorage(context)
            broken.database.openHelper.writableDatabase.execSQL("DROP TABLE proxy_cache")
            val withBrokenCache = proxy.accountRepository(
                google,
                ProxyCache(broken.database.proxyCache()),
                storage.phoneData(context, settings),
            )
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))

            withBrokenCache.deleteAccount(withPhoneData = false).join()
            assertEquals(Deletion.Ended(DeletionOutcome.ACCOUNT_DATA_NOT_DELETED), withBrokenCache.deletion.value)
            assertNull(proxy.account.account.value.session)
            assertTrue(google.forgotten)

            // Asked to, the phone's data still goes.
            proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
            withBrokenCache.deleteAccount(withPhoneData = true).join()
            assertEquals(Deletion.Ended(DeletionOutcome.PHONE_DATA_NOT_DELETED), withBrokenCache.deletion.value)
            assertEquals(0, onThePhone().bills)
            broken.close()
        }

    @Test
    fun `the document scanner's pages in the cache and the app's notices go with the phone's data`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        val scanned = File(context.cacheDir, "mlkit_docscan_ui_client/1/page_1.jpg").apply { parentFile?.mkdirs() }
        scanned.writeText("a page of a bill")
        val notices = context.getSystemService(NotificationManager::class.java)
        notices.createNotificationChannel(NotificationChannel("drafts", "Drafts", NotificationManager.IMPORTANCE_DEFAULT))
        notices.notify(
            1,
            NotificationCompat.Builder(context, "drafts").setSmallIcon(R.drawable.ic_notification).setContentTitle("Rent").build(),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        repository.deleteAccount(withPhoneData = true).join()

        assertFalse(scanned.exists())
        assertEquals(emptyList<File>(), context.cacheDir.listFiles().orEmpty().toList())
        assertEquals(0, shadowOf(notices).allNotifications.size)
    }

    /** Why the last deletion was refused. */
    private fun refusal(): ProxyException = (repository.deletion.value as Deletion.Refused).error as ProxyException

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
