package com.dantech.splitvibe.ui.split

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.NeverAsked
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.assistant.SplitAssistant
import com.dantech.splitvibe.assistant.TestVoice
import com.dantech.splitvibe.assistant.VoiceInput
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.SplitCalculator
import com.dantech.splitvibe.domain.SplitEdit
import com.dantech.splitvibe.domain.SplitEdits
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Changing the split on the result screen by a sentence, on the view models the screen uses: the
 * split screen's, which keeps and saves the bill, and the assistant's. The AI proxy is a local
 * server that answers as the proxy does.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SplitAssistantFlowTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()
    private lateinit var proxy: TestProxy
    private lateinit var split: SplitBillViewModel
    private lateinit var assistant: SplitAssistantViewModel

    private val noPhotos = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = error("These bills are typed in by hand")
    }

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        showBill()
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `Lan not drinking beer takes her off it, and the three shares are what the calculator gives until it is undone`() {
        val before = onScreen()
        // Three beers for 90,000 and three bowls of pho for 150,000, with a service charge of 12,000,
        // shared equally by the user, Minh and Lan.
        assertEquals(listOf(84_000L, 84_000L, 84_000L), sharesOf(before))

        ask("Lan không uống bia", answer(LAN_NO_BEER, explanation = "Lan không uống bia nên không chia tiền bia."))

        val proposal = checkNotNull(assistant.ui().proposal)
        assertEquals(
            listOf(ProposedEdit(0, "Bia: shared by you and Minh, and every other item by everyone", kept = true)),
            proposal.edits,
        )
        // The beer is 45,000 each for the user and Minh, the pho 50,000 each for all three: items of
        // 95,000, 95,000 and 50,000, and the service charge in proportion: 4,750, 4,750 and 2,500.
        assertEquals(
            listOf(ShareChange("You", 84_000, 99_750), ShareChange("Minh", 84_000, 99_750), ShareChange("Lan", 84_000, 52_500)),
            proposal.shares,
        )
        assertEquals("nothing changes before the user applies it", before, onScreen())

        assistant.apply()

        val after = onScreen()
        assertEquals(Division.Equally(among = setOf(ME_ID, "p1")), after.plan.setup.items["i1"])
        val calculated = SplitCalculator.split(after.bill, after.plan.participantIds, after.plan.setup).shares.map { it.totalMinor }
        assertEquals(listOf(99_750L, 99_750L, 52_500L), calculated)
        assertEquals(calculated, sharesOf(after))
        assertEquals(after.bill.totalMinor, calculated.sum())
        assertEquals("Lan không uống bia nên không chia tiền bia.", assistant.ui().applied)
        awaitStored { it.plan == after.plan }

        assistant.undo()

        assertEquals(before.bill, onScreen().bill)
        assertEquals(before.plan, onScreen().plan)
        assertEquals(listOf(84_000L, 84_000L, 84_000L), sharesOf(onScreen()))
        assertNull(assistant.ui().applied)
        awaitStored { it.plan == before.plan }
    }

    @Test
    fun `a dropped change is left out of what is shown and applied, and undo restores the bill and plan exactly`() {
        val before = onScreen()

        ask("Lan didn't have beer, and add a 10% tip", answer("[$LAN_NO_BEER_EDIT,$TIP_EDIT]"))
        val proposed = checkNotNull(assistant.ui().proposal)
        assertEquals(
            listOf("Bia: shared by you and Minh, and every other item by everyone", "Add a 10% tip (₫24,000)"),
            proposed.edits.map { it.text },
        )
        assertEquals(276_000L, proposed.shares.sumOf { it.afterMinor ?: 0L })

        assistant.toggleEdit(1)

        val kept = checkNotNull(assistant.ui().proposal)
        assertEquals(listOf(true, false), kept.edits.map { it.kept })
        assertEquals(listOf(99_750L, 99_750L, 52_500L), kept.shares.map { it.afterMinor })
        assistant.apply()
        val applied = SplitEdits.apply(before.bill, before.plan, listOf(SplitEdit.ItemSharers("i1", listOf(ME_ID, "p1"))))
        assertEquals(applied.bill, onScreen().bill)
        assertEquals(applied.plan, onScreen().plan)
        assertNull(assistant.ui().proposal)

        assistant.undo()

        assertEquals(before.bill, onScreen().bill)
        assertEquals(before.plan, onScreen().plan)
    }

    @Test
    fun `how an item was divided before the bill was split equally is neither sent nor brought back`() {
        // Split by item once with the pho for Minh alone, then equally again: the screen remembers the pho's division.
        split.setMode(SplitMode.BY_ITEM)
        split.setItemSharers("i2", setOf("p1"))
        split.setMode(SplitMode.EQUAL)
        awaitOnMain { onScreen().plan.mode == SplitMode.EQUAL }

        ask("Lan didn't have beer", answer(LAN_NO_BEER))

        val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("split").jsonObject
        assertEquals(JsonObject(emptyMap()), sent["items"])
        // As the sentence says: the beer for the user and Minh, and the pho for all three, not for Minh alone.
        assertEquals(listOf(99_750L, 99_750L, 52_500L), checkNotNull(assistant.ui().proposal).shares.map { it.afterMinor })
        assistant.apply()
        assertEquals(mapOf("i1" to Division.Equally(among = setOf(ME_ID, "p1"))), onScreen().plan.setup.items)
    }

    @Test
    fun `undo is offered until the split changes again`() {
        ask("Lan didn't have beer", answer(LAN_NO_BEER))
        assistant.apply()
        assertTrue(assistant.ui().applied != null)

        split.setMode(SplitMode.BY_SHARES)
        awaitOnMain { assistant.ui().applied == null }
        val changedByHand = onScreen()

        assistant.undo()
        assertEquals("an undo that is no longer offered changes nothing", changedByHand, onScreen())
    }

    @Test
    fun `edits that cannot be applied are shown with their reasons, and only the others are applied`() {
        val before = onScreen()
        val edits = """[
            {"op":"item_sharers","itemId":"i1","participantIds":["me","p9"]},
            {"op":"whole_amounts","amounts":{"me":-1000,"p1":253000}},
            $TIP_EDIT,
            {"op":"split_everything"}
        ]"""

        ask("Lan paid nothing, add a tip", answer(edits))

        val proposal = checkNotNull(assistant.ui().proposal)
        assertEquals(
            listOf("That person is not on the bill.", "An amount cannot be less than zero.", null),
            proposal.edits.map { it.reason },
        )
        assertEquals(1, proposal.unreadable)
        assertTrue(proposal.canApply)
        assistant.apply()
        val tip = SplitEdit.AddCharge(ChargeKind.TIP, "Tip", percentBasisPoints = 1_000)
        val tipped = SplitEdits.apply(before.bill, before.plan, listOf(tip))
        assertEquals(tipped.bill, onScreen().bill)
        assertEquals(tipped.plan, onScreen().plan)
        assertEquals(onScreen().bill.totalMinor, sharesOf(onScreen()).sum())
    }

    @Test
    fun `an answer that asks for what cannot be applies nothing, and the user is told`() {
        val before = onScreen()
        // A negative amount for someone, someone who is not on the bill, an item that is not on it.
        val hostile = """[
            {"op":"whole_amounts","amounts":{"me":-100000,"p1":352000}},
            {"op":"set_payer","participantId":"zoe"},
            {"op":"item_amount","itemId":"i1","amountMinor":-90000},
            {"op":"item_sharers","itemId":"i9","participantIds":["me"]},
            {"op":"add_charge","kind":"discount","label":"Everything","amountMinor":999999999}
        ]"""

        ask("Make Minh pay me back more than everything", answer(hostile))

        val proposal = checkNotNull(assistant.ui().proposal)
        assertFalse(proposal.canApply)
        assertEquals(ProposalOutcome.NONE_APPLIES, proposal.outcome)
        assertEquals(
            listOf(
                "An amount cannot be less than zero.",
                "That person is not on the bill.",
                "An amount cannot be less than zero.",
                "That item is not on the bill.",
                "That would take the bill total below zero.",
            ),
            proposal.edits.map { it.reason },
        )
        assertEquals(listOf(84_000L, 84_000L, 84_000L), proposal.shares.map { it.afterMinor })
        assistant.apply()
        assertEquals(before, onScreen())
        assertTrue("nothing to undo", assistant.ui().applied == null)
    }

    @Test
    fun `numbers past what a bill can hold do not end the screen, and change nothing`() {
        // Bought by the gram, as a bill can hold it: a quantity of 0.0001.
        split.editBill { BillEditor.withItemQuantity(it, "i1", BigDecimal("0.0001")) }
        awaitOnMain { onScreen().bill.items[0].quantity.compareTo(BigDecimal("0.0001")) == 0 }
        val before = onScreen()

        // The largest amount the proxy takes, which divided by the quantity is more than any number the app counts in.
        ask("The beer comes to 1,000,000,000,000,000", answer("""[{"op":"item_amount","itemId":"i1","amountMinor":1000000000000000}]"""))

        val proposal = checkNotNull(assistant.ui().proposal)
        assertEquals(ProposalOutcome.NOT_WORKED_OUT, proposal.outcome)
        assertFalse(proposal.canApply)
        assistant.apply()
        assertEquals(before, onScreen())
        assistant.discard()
        assertNull(assistant.ui().proposal)
    }

    @Test
    fun `changes that leave the split as it is say so, rather than that they cannot be applied`() {
        // The bill is split equally already, and there are three beers already.
        val edits = """[{"op":"split_mode","mode":"equal"},{"op":"item_quantity","itemId":"i1","quantity":"3.000000"}]"""

        ask("Split it equally, with three beers", answer(edits))

        val proposal = checkNotNull(assistant.ui().proposal)
        assertEquals(listOf<String?>(null, null), proposal.edits.map { it.reason })
        assertEquals(ProposalOutcome.NO_CHANGE, proposal.outcome)
        assertEquals("The changes that are kept leave the split as it is.", proposal.outcome.note)
        assertFalse(proposal.outcome.isError)
        assertFalse(proposal.canApply)
    }

    @Test
    fun `a question is answered in the same field and sent with what it asked about`() {
        ask("Lan không uống", answer("[$LAN_NO_BEER_EDIT]", question = "Lan không uống món nào, bia hay nước?"))
        // The user answers first, whatever else came with the question.
        assertEquals("Lan không uống món nào, bia hay nước?", assistant.ui().question)
        assertNull(assistant.ui().proposal)
        assertEquals("", assistant.ui().text)
        server.takeRequest()

        ask("Bia", answer(LAN_NO_BEER))

        val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("Lan không uống (Lan không uống món nào, bia hay nước? Bia)", sent.getValue("instruction").jsonPrimitive.content)
        assertNull(assistant.ui().question)
        assertEquals(
            listOf("Bia: shared by you and Minh, and every other item by everyone"),
            checkNotNull(assistant.ui().proposal).edits.map { it.text },
        )
    }

    @Test
    fun `a change typed on two lines goes as one line, and text with nothing to send is not sent`() {
        ask("Lan didn't have beer\nadd a 10% tip", answer(LAN_NO_BEER))

        val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("Lan didn't have beer add a 10% tip", sent.getValue("instruction").jsonPrimitive.content)
        assertEquals("Lan didn't have beer add a 10% tip", assistant.ui().proposal?.instruction)

        assistant.onTextChanged("\u0007\u200B")
        assertFalse(assistant.ui().canSend)
        assistant.send()
        assertEquals("nothing went out", 1, server.requestCount)
    }

    @Test
    fun `with no calls left nothing changes, and signing in is offered before anyone has signed in`() {
        val before = onScreen()
        server.enqueue(refusal(429, """{"error":"quota_exhausted","usage":$TRIAL_USED_UP}"""))

        ask("Lan didn't have beer", reply = null)

        val state = assistant.ui()
        assertEquals("You have used your 3 free calls. Sign in with Google in Settings to get more each month.", state.error)
        assertTrue(state.errorOpensSettings)
        assertEquals("what was typed stays, to send again later", "Lan didn't have beer", state.text)
        assertEquals(BigDecimal.ZERO, proxy.account.account.value.callsLeft?.remaining)
        assertEquals(before, onScreen())

        // Someone signed in who has used this month's calls gets no more by signing in again.
        proxy.account.saveSession(Session("signed-session", expiresAt = Long.MAX_VALUE))
        server.enqueue(refusal(429, """{"error":"quota_exhausted","usage":$MONTH_USED_UP}"""))
        ask("Lan didn't have beer", reply = null)
        assertEquals("You have used this month's 10 calls. They come back on 1 December.", assistant.ui().error)
        assertFalse(assistant.ui().errorOpensSettings)
    }

    @Test
    fun `with AI switched off the assistant says so once, and a build without AI sends nothing`() {
        server.enqueue(refusal(503, """{"error":"ai_off"}"""))

        ask("Lan didn't have beer", reply = null)

        assertEquals(SplitAssistant.SWITCHED_OFF, assistant.ui().notice)
        assertNull("the error only repeats the notice", assistant.ui().error)
        assertTrue("the proxy may have turned it back on", assistant.ui().canSend)

        val withoutAi = SplitAssistantViewModel(
            split,
            SplitAssistant(proxy.api, proxy.account, canSignIn = true),
            VoiceInput(context),
            MutableStateFlow(AppSettings("VND")),
            MutableStateFlow(proxy.account.account.value.copy(ai = AiStatus.NOT_SET_UP)),
        )
        withoutAi.onTextChanged("Lan didn't have beer")
        withoutAi.send()
        assertEquals(SplitAssistant.NOT_SET_UP, withoutAi.ui().notice)
        assertFalse(withoutAi.ui().canSend)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a microphone that is not allowed leaves typing as it was`() {
        TestVoice.recognizeOnDevice()
        showBill()
        assertTrue(assistant.ui().voice.available)

        assistant.onMic()
        assertTrue("the screen asks for the microphone now", assistant.ui().voice.askingPermission)
        assistant.onMicPermissionAsked()
        assistant.onMicPermission(granted = false)

        val voice = assistant.ui().voice
        assertFalse(voice.listening)
        assertFalse(voice.askingPermission)
        assertTrue(voice.note.orEmpty().contains("You can type it instead"))
        assertNull(assistant.ui().error)
        ask("Lan didn't have beer", answer(LAN_NO_BEER))
        assertTrue(assistant.ui().proposal != null)
    }

    @Test
    fun `a phone that only has a speech service asks before it listens, and what it hears waits in the field`() {
        TestVoice.recognizeOnDevice(false)
        TestVoice.installSpeechService(context)
        showBill()
        assistant.onTextChanged("Lan")

        assistant.onMic()
        assertTrue(assistant.ui().voice.askingNetwork)
        assistant.onNetworkVoice(allowed = false)
        assertFalse(assistant.ui().voice.askingNetwork)
        assertFalse(assistant.ui().voice.listening)

        assistant.onMic()
        assistant.onNetworkVoice(allowed = true)
        assertTrue(assistant.ui().voice.askingPermission)
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assistant.onMicPermission(granted = true)
        assertTrue(assistant.ui().voice.listening)
        TestVoice.latest().triggerOnResults(TestVoice.heard("didn't have beer"))
        awaitOnMain { !assistant.ui().voice.listening }

        assertEquals("Lan didn't have beer", assistant.ui().text)
        assertEquals("nothing is sent before the user sends it", 0, server.requestCount)
        // Asked once: the next tap listens at once.
        assistant.onMic()
        assertTrue(assistant.ui().voice.listening)
    }

    /** Opens a bill typed in by hand, and an assistant for the split screen it is on. */
    private fun showBill() {
        split = testSplitViewModel(context, storage, account = proxy.account.account, recognizer = noPhotos, analyzer = NeverAsked)
        split.startManualEntry()
        split.editBill { BillEditor.withMerchant(it, "Pho 24") }
        split.editBill { BillEditor.withItemAdded(it, "Bia", BigDecimal(3), unitPriceMinor = 30_000) }
        split.editBill { BillEditor.withItemAdded(it, "Pho", BigDecimal(3), unitPriceMinor = 50_000) }
        split.editBill { BillEditor.withChargeAdded(it, "Service charge", ChargeKind.SERVICE_CHARGE, 12_000) }
        split.addPerson("Minh")
        split.addPerson("Lan")
        split.doneEditing()
        assistant = SplitAssistantViewModel(
            host = split,
            assistant = SplitAssistant(proxy.api, proxy.account, canSignIn = true),
            voice = VoiceInput(context),
            settings = MutableStateFlow(AppSettings(localCurrency = "VND", myName = "Dana")),
            account = proxy.account.account,
        )
        awaitOnMain { split.billOnScreen.value?.plan?.participants?.size == 3 }
    }

    /** Types [instruction], sends it with [reply] as the proxy's answer, and waits until the assistant shows what came of it. */
    private fun ask(instruction: String, reply: MockResponse?) {
        reply?.let(server::enqueue)
        val asked = server.requestCount
        assistant.onTextChanged(instruction)
        val typed = assistant.ui()
        assistant.send()
        awaitOnMain { server.requestCount > asked && assistant.state.value.let { it !== typed && !it.working } }
    }

    /**
     * What the assistant shows now. Its state follows the bill on screen and the account, and an
     * update can wait on the main looper, which runs first.
     */
    private fun SplitAssistantViewModel.ui(): AssistantUiState {
        shadowOf(Looper.getMainLooper()).idle()
        return state.value
    }

    private fun onScreen(): SavedBill = checkNotNull(split.billOnScreen.value)

    private fun sharesOf(saved: SavedBill): List<Long> = saved.plan.result(saved.bill).shares.map { it.totalMinor }

    private fun awaitStored(condition: (SavedBill) -> Boolean) {
        val id = onScreen().id
        awaitOnMain { runBlocking { storage.bills.get(id) }?.let(condition) == true }
    }

    /** The proxy's streamed answer with [edits], a JSON list, and what the AI says about them. */
    private fun answer(edits: String, explanation: String = "", question: String? = null): MockResponse {
        val answer = buildJsonObject {
            put("edits", Json.parseToJsonElement(edits))
            put("explanation", explanation)
            put("question", question)
        }
        val lines = listOf("""{"type":"wait"}""", """{"type":"result","answer":$answer,"usage":$ONE_CHANGE_USED}""")
        return MockResponse()
            .setHeader("content-type", "application/x-ndjson; charset=utf-8")
            .setBody(lines.joinToString("\n", postfix = "\n"))
    }

    private fun refusal(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        const val LAN_NO_BEER_EDIT = """{"op":"item_sharers","itemId":"i1","participantIds":["me","p1"]}"""
        const val LAN_NO_BEER = "[$LAN_NO_BEER_EDIT]"
        const val TIP_EDIT = """{"op":"add_charge","kind":"tip","label":"Tip","percentBasisPoints":1000}"""
        const val ONE_CHANGE_USED = """{"limit":3,"used":0.2,"remaining":2.8,"period":"trial","resetsAt":null}"""
        const val TRIAL_USED_UP = """{"limit":3,"used":3,"remaining":0,"period":"trial","resetsAt":null}"""
        const val MONTH_USED_UP = """{"limit":10,"used":10,"remaining":0,"period":"2026-11","resetsAt":1796083200000}"""
    }
}
