package com.dantech.splitvibe

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.PhoneData
import com.dantech.splitvibe.data.bills.BillPages
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.data.fx.FxRepository
import com.dantech.splitvibe.data.people.ContactNames
import com.dantech.splitvibe.data.people.PeopleRepository
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.data.share.ShareRepository
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/**
 * The bills, the people and the photos of a phone that has none yet, kept in memory for one test.
 * Close it when the test ends.
 */
class TestStorage(context: Context) : AutoCloseable {
    val database: SplitVibeDatabase = Room.inMemoryDatabaseBuilder(context, SplitVibeDatabase::class.java)
        // A test reads what was saved from its own thread, which under Robolectric is the main one.
        .allowMainThreadQueries()
        .build()
    val pages = BillPages(context)
    val people = PeopleRepository(database)
    val bills = BillRepository(database, people, pages, contentWords())
    val contactNames = ContactNames(context.contentResolver)

    /** Exchange rates as a build without a proxy address has them: none. */
    val noRates = FxRepository(
        api = ProxyApi("", AccountStore(context, proxySetUp = false, cipher = PlainCipher), appVersion = TestProxy.APP_VERSION),
        file = File(context.cacheDir, "fx-rates.json"),
    )

    /** What only the phone keeps, as account deletion deletes it, with [settings] for the user's name. */
    fun phoneData(context: Context, settings: SettingsStore = SettingsStore(context)) = PhoneData(database, pages, settings, context)

    /** Remembers [TestPeople] as people the user split with before. */
    fun rememberTestPeople() = runBlocking {
        people.remember(TestPeople.names.mapIndexed { index, name -> Participant("p${index + 1}", name) })
    }

    override fun close() = database.close()
}

/**
 * The split flow as the tests drive it: real storage in [storage], and every change saved at once
 * rather than a moment later, unless a test gives [saveDelayMs].
 */
fun testSplitViewModel(
    context: Context,
    storage: TestStorage,
    account: StateFlow<Account>,
    recognizer: BillTextRecognizer,
    analyzer: BillAnalyzer,
    localCurrency: String = "VND",
    canSignIn: Boolean = true,
    sellsPro: Boolean = false,
    fx: FxRepository = storage.noRates,
    savedState: SavedStateHandle = SavedStateHandle(),
    settings: MutableStateFlow<AppSettings> = MutableStateFlow(AppSettings(localCurrency = localCurrency)),
    saveDelayMs: Long = 0,
    shares: ShareRepository? = null,
): SplitBillViewModel = SplitBillViewModel(
    settings = settings,
    account = account,
    canSignIn = canSignIn,
    sellsPro = sellsPro,
    imageLoader = BillImageLoader(context),
    recognizer = recognizer,
    analyzer = analyzer,
    bills = storage.bills,
    peopleRepository = storage.people,
    pageStore = storage.pages,
    fx = fx,
    contactNames = storage.contactNames,
    savedState = savedState,
    saveDelayMs = saveDelayMs,
    shares = shares,
)
