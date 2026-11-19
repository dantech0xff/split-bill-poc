package com.dantech.splitvibe.ui.split

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.NeverAsked
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.fx.FxRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What keeps a saved bill safe: nothing overwrites it unasked, and no change or photo is lost on the way. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h1700dp-xhdpi")
class BillSafetyTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()

    private val recordedMlKitOutput = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `typing a bill in by hand after going back from a read bill keeps the read bill`() {
        val viewModel = readSampleBill()
        val read = savedOnScreen(viewModel)

        viewModel.backToInput()
        viewModel.startManualEntry()
        viewModel.editBill { BillEditor.withMerchant(it, "C") }

        val typed = savedOnScreen(viewModel)
        assertNotEquals(read.id, typed.id)
        awaitOnMain { countBills() == 2 }
        assertEquals(read.bill, runBlocking { storage.bills.get(read.id) }?.bill)
    }

    @Test
    fun `reading the same photos again replaces their bill, and other photos make a bill of their own`() {
        val viewModel = readSampleBill()
        val first = savedOnScreen(viewModel)

        viewModel.backToInput()
        readAgain(viewModel)
        assertEquals("the same photos are the same bill", first.id, savedOnScreen(viewModel).id)

        viewModel.backToInput()
        viewModel.onSamplePicked()
        awaitOnMain { viewModel.state.value.let { it.photo != null && !it.isBusy } }
        readAgain(viewModel)
        assertNotEquals(first.id, savedOnScreen(viewModel).id)
        awaitOnMain { countBills() == 2 }
    }

    @Test
    fun `two lines of the same name with the same problem do not break the split`() {
        val viewModel = manualViewModel()
        viewModel.startManualEntry()
        repeat(2) { viewModel.editBill { bill -> BillEditor.withItemAdded(bill, "Bia", amountMinor = 50_000) } }
        viewModel.addPerson("Minh")
        viewModel.addPerson("Lan")
        viewModel.setMode(SplitMode.BY_ITEM)
        val half = Division.ByPercent(mapOf("p1" to 5_000L, "p2" to 5_000L))
        viewModel.setItemDivision("i1", half)
        viewModel.setItemDivision("i2", half)
        viewModel.removePerson("p2")
        viewModel.doneEditing()
        val problems = checkNotNull(viewModel.state.value.result).problems
        assertEquals(listOf(problems.first(), problems.first()), problems)

        compose.setContent {
            SplitVibeTheme {
                SplitScreen(viewModel, AiStatus.AVAILABLE, newCameraTarget = {
                    Uri.EMPTY
                }, scanIntent = { null }, onExit = {}, onOpenSettings = {}, onOpenPro = {})
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Paid by"))

        compose.onAllNodesWithText(problems.first()).assertCountEquals(2)
    }

    @Test
    fun `a finished split that a change leaves unbalanced is a draft again`() {
        val viewModel = readSampleBill()
        viewModel.finish()
        val id = savedOnScreen(viewModel).id
        awaitOnMain { runBlocking { storage.bills.get(id) }?.status == BillStatus.FINAL }

        viewModel.setMode(SplitMode.BY_AMOUNT)
        viewModel.setWholeValue(ME_ID, 1)

        assertEquals(BillStatus.DRAFT, savedOnScreen(viewModel).status)
        awaitOnMain { runBlocking { storage.bills.get(id) }?.status == BillStatus.DRAFT }
    }

    @Test
    fun `a change waiting to be saved is saved with its photo when the next split starts`() {
        val viewModel = readSampleBill(saveDelayMs = 60_000)
        val read = savedOnScreen(viewModel)
        assertNull("still waiting", runBlocking { storage.bills.get(read.id) })

        viewModel.startNewSplit()

        awaitOnMain { runBlocking { storage.bills.get(read.id) } != null }
        val page = checkNotNull(runBlocking { storage.bills.get(read.id) }).pages.single()
        assertNotNull("the photo stays with its bill", runBlocking { storage.pages.load(page) })
    }

    @Test
    fun `after a copy saved elsewhere wins, the next change is saved on top of it`() {
        val viewModel = readSampleBill()
        val id = savedOnScreen(viewModel).id
        awaitOnMain { runBlocking { storage.bills.get(id) } != null }
        val stored = checkNotNull(runBlocking { storage.bills.get(id) })
        runBlocking { storage.bills.save(stored.copy(bill = BillEditor.withMerchant(stored.bill, "Changed elsewhere"))) }

        viewModel.editBill { BillEditor.withMerchant(it, "Changed here") }
        awaitOnMain { viewModel.state.value.notice != null }
        assertEquals("Changed elsewhere", runBlocking { storage.bills.get(id) }?.bill?.merchant)

        viewModel.editBill { BillEditor.withDate(it, "2026-11-15") }
        awaitOnMain { runBlocking { storage.bills.get(id) }?.bill?.date == "2026-11-15" }
        val latest = checkNotNull(runBlocking { storage.bills.get(id) })
        assertEquals("Changed elsewhere", latest.bill.merchant)
        assertEquals(stored.revision + 2, latest.revision)
    }

    @Test
    fun `a bill open while the currency is changed in Settings is shown in the new one`() {
        server.enqueue(
            MockResponse().setBody(
                """{"base":"EUR","source":"Frankfurter","fetchedAt":1,"rates":{"EUR":{"rate":1,"date":"2026-11-14"},""" +
                    """"USD":{"rate":1.25,"date":"2026-11-14"},"VND":{"rate":31250,"date":"2026-11-14"}}}""",
            ),
        )
        val settings = MutableStateFlow(AppSettings(localCurrency = "VND"))
        val viewModel =
            manualViewModel(FxRepository(TestProxy(server, context).api, File(context.filesDir, "fx-rates-safety.json")), settings)
        viewModel.startManualEntry()
        viewModel.editBill { BillEditor.withCurrency(it, BillCurrency("USD", 2)) }
        viewModel.editBill { BillEditor.withItemAdded(it, "Burger", amountMinor = 1_000) }
        awaitOnMain { viewModel.state.value.result?.exchangeRate != null }
        assertEquals(BillCurrency("VND", 0).format(250_000), viewModel.state.value.result?.converted(1_000))

        settings.value = AppSettings(localCurrency = "EUR")

        awaitOnMain { viewModel.state.value.result?.exchangeRate?.to == "EUR" }
        assertEquals(BillCurrency("EUR", 2).format(800), viewModel.state.value.result?.converted(1_000))
    }

    @Test
    fun `the photo step comes back with the photos picked on it after the system ends the app`() {
        val handle = SavedStateHandle()
        val viewModel = readSampleBill(handle = handle)
        val read = savedOnScreen(viewModel)
        awaitOnMain { runBlocking { storage.bills.get(read.id) } != null }
        viewModel.backToInput()
        viewModel.onSamplePicked()
        awaitOnMain { viewModel.state.value.let { it.photo != null && !it.isBusy } }

        val kept = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val restored = testSplitViewModel(
            context,
            storage,
            TestProxy(server, context).account.account,
            recordedMlKitOutput,
            NeverAsked,
            savedState = kept,
        )
        awaitOnMain { restored.state.value.photo != null }

        assertNull("the photo step, not the bill left before", restored.state.value.result)
        assertNotEquals(read.pages, handle.get<List<String>>("pages"))
    }

    /** The sample bill read with the recorded answer, split between me, Minh and Lan. */
    private fun readSampleBill(handle: SavedStateHandle = SavedStateHandle(), saveDelayMs: Long = 0): SplitBillViewModel {
        val proxy = TestProxy(server, context)
        val viewModel = testSplitViewModel(
            context,
            storage,
            proxy.account.account,
            recordedMlKitOutput,
            proxy.analyzer,
            savedState = handle,
            saveDelayMs = saveDelayMs,
        )
        viewModel.onSamplePicked()
        awaitOnMain { viewModel.state.value.let { it.photo != null && !it.isBusy } }
        viewModel.onInstructionChanged("Split with Minh and Lan. Minh had the beers.")
        readAgain(viewModel)
        return viewModel
    }

    private fun readAgain(viewModel: SplitBillViewModel) {
        server.enqueue(Fixtures.proxyAnswer())
        viewModel.analyze(defaultInstruction = "Split this bill")
        awaitOnMain { viewModel.state.value.let { (it.result != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)
    }

    private fun savedOnScreen(viewModel: SplitBillViewModel) = checkNotNull(viewModel.state.value.result).saved

    private fun countBills(): Int = runBlocking { storage.bills.listings().first().size }

    private fun manualViewModel(
        fx: FxRepository = storage.noRates,
        settings: MutableStateFlow<AppSettings> = MutableStateFlow(AppSettings("VND")),
    ) = testSplitViewModel(
        context,
        storage,
        TestProxy(server, context).account.account,
        recordedMlKitOutput,
        NeverAsked,
        fx = fx,
        settings = settings,
    )
}
