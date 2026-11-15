package com.dantech.splitvibe.ui.split

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A bill's life on the phone: typed in or read, corrected, saved as it changes, brought back after
 * the system ended the app, and shown in the user's own currency.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h1700dp-xhdpi")
class BillLifecycleTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()
    private val vnd = BillCurrency("VND", 0)

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
    fun `a bill of three items typed in by hand goes through the whole flow`() {
        val viewModel = testSplitViewModel(context, storage, TestProxy(server, context).account.account, recordedMlKitOutput, NeverAsked)
        compose.setContent {
            SplitVibeTheme {
                SplitScreen(viewModel, AiStatus.AVAILABLE, newCameraTarget = {
                    Uri.EMPTY
                }, scanIntent = { null }, onExit = {}, onOpenSettings = {})
            }
        }

        compose.onNodeWithText("Type it in by hand").performClick()
        compose.onNode(hasText("Where") and hasSetTextAction()).performTextInput("Bún chả Hương Liên")
        repeat(3) { compose.onNodeWithText("Add an item").performClick() }
        val items = listOf("Bún chả" to "60000", "Nem cua bể" to "40000", "Trà đá" to "20000")
        items.forEachIndexed { index, (name, amount) ->
            compose.onAllNodes(hasText("Item") and hasSetTextAction())[index].performTextInput(name)
            compose.onAllNodes(hasText("Amount") and hasSetTextAction())[index].performTextInput(amount)
        }
        compose.onNodeWithText("Left empty, the total is what the lines add up to: ${vnd.format(120_000)}.").assertIsDisplayed()
        compose.onNode(hasText("Done") and hasClickAction()).performClick()

        compose.onAllNodesWithText(vnd.format(120_000))[0].assertIsDisplayed()
        compose.onNodeWithText("Add someone").performClick()
        compose.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Minh")
        compose.onNode(hasText("Add") and hasClickAction()).performClick()
        compose.onNodeWithText("Request ${vnd.format(60_000)} from 1 person").performClick()
        compose.onNodeWithText("Requests sent").assertIsDisplayed()

        val id = checkNotNull(viewModel.state.value.result).saved.id
        awaitOnMain { runBlocking { storage.bills.get(id) }?.status == BillStatus.FINAL }
        val saved = checkNotNull(runBlocking { storage.bills.get(id) })
        assertEquals("Bún chả Hương Liên", saved.bill.merchant)
        assertEquals(items.map { it.first }, saved.bill.items.map { it.name })
        assertEquals(listOf(60_000L, 60_000L), saved.shares.map { it.totalMinor })
        assertTrue("no photo is needed", saved.pages.isEmpty())
    }

    @Test
    fun `changing an item of the read bill changes the total and the shares of those who had it`() {
        val viewModel = readSampleBill()
        val before = checkNotNull(viewModel.state.value.result)
        assertEquals(902_664L, before.bill.totalMinor)

        // The beers, which only Minh had, cost 18.000 more than they were read as.
        viewModel.editBill { BillEditor.withItemAmount(it, "i6", 150_000) }
        assertEquals(
            "the printed total stands until the user settles the difference",
            18_000L,
            -viewModel.state.value.result!!.bill.differenceMinor,
        )
        viewModel.editBill(BillEditor::withLinesAsTotal)

        val after = checkNotNull(viewModel.state.value.result)
        assertEquals(920_664L, after.bill.totalMinor)
        assertEquals(920_664L, after.split.totalMinor)
        assertEquals(listOf(221_334L, 371_333L, 221_333L), after.split.shares.map { it.itemsMinor })
        assertTrue(after.problems.isEmpty())
        awaitOnMain { runBlocking { storage.bills.get(after.saved.id) }?.bill?.totalMinor == 920_664L }
    }

    @Test
    fun `a split in progress comes back after the system ends the app`() {
        val handle = SavedStateHandle()
        val viewModel = readSampleBill(handle)
        viewModel.setMode(SplitMode.BY_PERCENT)
        viewModel.setWholeValue(ME_ID, 5_000)
        viewModel.setWholeValue("p1", 2_500)
        viewModel.setWholeValue("p2", 2_500)
        val id = checkNotNull(viewModel.state.value.result).saved.id
        awaitOnMain { runBlocking { storage.bills.get(id) }?.plan?.setup?.whole?.valueOf(ME_ID) == 5_000L }

        // A new process starts from what the system kept for the screen and what the phone saved.
        val kept = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val restored =
            testSplitViewModel(
                context,
                storage,
                TestProxy(server, context).account.account,
                recordedMlKitOutput,
                NeverAsked,
                savedState = kept,
            )
        awaitOnMain { restored.state.value.result != null }

        val result = checkNotNull(restored.state.value.result)
        assertEquals(SplitMode.BY_PERCENT, result.plan.mode)
        assertEquals(listOf(451_332L, 225_666L, 225_666L), result.split.shares.map { it.totalMinor })
        assertNotNull("the photo comes back too", restored.state.value.photo)
    }

    @Test
    fun `a bill typed in by hand is kept only once something is on it`() {
        val viewModel = testSplitViewModel(context, storage, TestProxy(server, context).account.account, recordedMlKitOutput, NeverAsked)

        viewModel.startManualEntry()
        viewModel.doneEditing()
        assertNull("an empty bill goes back to the photo step", viewModel.state.value.result)

        viewModel.startManualEntry()
        viewModel.editBill { BillEditor.withItemAdded(it, "Cà phê", amountMinor = 35_000) }
        val id = checkNotNull(viewModel.state.value.result).saved.id
        awaitOnMain { runBlocking { storage.bills.get(id) } != null }
        assertEquals(1, countBills())
    }

    @Test
    fun `a change made to an older copy shows the newer copy instead of overwriting it`() {
        val viewModel = readSampleBill()
        val id = checkNotNull(viewModel.state.value.result).saved.id
        awaitOnMain { runBlocking { storage.bills.get(id) } != null }
        val stored = checkNotNull(runBlocking { storage.bills.get(id) })
        runBlocking { storage.bills.save(stored.copy(bill = BillEditor.withMerchant(stored.bill, "Changed elsewhere"))) }

        viewModel.editBill { BillEditor.withMerchant(it, "Changed here") }

        awaitOnMain { viewModel.state.value.notice != null }
        assertEquals("This bill was changed elsewhere, so it now shows the latest version.", viewModel.state.value.notice)
        assertEquals("Changed elsewhere", viewModel.state.value.result?.bill?.merchant)
    }

    @Test
    fun `a bill in dollars is also shown in dong at the day's rate, and its shares stay in dollars`() {
        server.enqueue(
            MockResponse().setBody(
                """{"base":"EUR","source":"Frankfurter","fetchedAt":1,"rates":{"EUR":{"rate":1,"date":"2026-11-14"},""" +
                    """"USD":{"rate":1.08,"date":"2026-11-14"},"VND":{"rate":27378,"date":"2026-11-13"}}}""",
            ),
        )
        val proxy = TestProxy(server, context)
        val fx = FxRepository(proxy.api, File(context.filesDir, "fx-rates-lifecycle.json"))
        val viewModel = testSplitViewModel(context, storage, proxy.account.account, recordedMlKitOutput, NeverAsked, fx = fx)

        viewModel.startManualEntry()
        viewModel.editBill { BillEditor.withCurrency(it, BillCurrency("USD", 2)) }
        viewModel.editBill { BillEditor.withItemAdded(it, "Burger", amountMinor = 1_000) }
        awaitOnMain { viewModel.state.value.result?.exchangeRate != null }

        val result = checkNotNull(viewModel.state.value.result)
        assertEquals(vnd.format(253_500), result.converted(1_000))
        assertEquals("2026-11-13", result.exchangeRate?.date)
        awaitOnMain { runBlocking { storage.bills.get(result.saved.id) }?.exchangeRate != null }
        val saved = checkNotNull(runBlocking { storage.bills.get(result.saved.id) })
        assertEquals(listOf(1_000L), saved.shares.map { it.totalMinor })
        assertEquals("Frankfurter", saved.exchangeRate?.source)

        // The rate the user's card was charged at replaces the day's rate.
        viewModel.setExchangeRate(BigDecimal("26000"))
        assertEquals(vnd.format(260_000), viewModel.state.value.result?.converted(1_000))
        assertTrue(viewModel.state.value.result?.exchangeRate?.isByHand == true)
    }

    @Test
    fun `a bill opened from the list goes back to the list, and its photo step is not shown`() {
        val viewModel = readSampleBill()
        val id = checkNotNull(viewModel.state.value.result).saved.id
        awaitOnMain { runBlocking { storage.bills.get(id) } != null }
        var exited = false
        viewModel.startNewSplit()
        compose.setContent {
            SplitVibeTheme {
                SplitScreen(viewModel, AiStatus.AVAILABLE, newCameraTarget = {
                    Uri.EMPTY
                }, scanIntent = { null }, onExit = { exited = true }, onOpenSettings = {})
            }
        }

        viewModel.open(id)
        awaitOnMain { viewModel.state.value.result != null }
        compose.onAllNodesWithText("Bill total")[0].assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        assertTrue(exited)
    }

    /** The sample bill read with the recorded answer, split between me, Minh and Lan. */
    private fun readSampleBill(handle: SavedStateHandle = SavedStateHandle()): SplitBillViewModel {
        server.enqueue(Fixtures.proxyAnswer())
        val proxy = TestProxy(server, context)
        val viewModel =
            testSplitViewModel(context, storage, proxy.account.account, recordedMlKitOutput, proxy.analyzer, savedState = handle)
        viewModel.onSamplePicked()
        awaitOnMain { viewModel.state.value.let { it.photo != null && !it.isBusy } }
        viewModel.onInstructionChanged("Split with Minh and Lan. Minh had the beers.")
        viewModel.analyze()
        awaitOnMain { viewModel.state.value.let { (it.result != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)
        return viewModel
    }

    private fun countBills(): Int = runBlocking {
        storage.database.query("SELECT COUNT(*) FROM bills", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
    }
}
