package com.dantech.splitvibe.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.HomeFixtures
import com.dantech.splitvibe.R
import com.dantech.splitvibe.TestClock
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.assistant.SplitAssistant
import com.dantech.splitvibe.assistant.TestVoice
import com.dantech.splitvibe.assistant.VoiceInput
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import com.dantech.splitvibe.ui.history.HistoryScreen
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.home.HomeSummary
import com.dantech.splitvibe.ui.home.MonthSpending
import com.dantech.splitvibe.ui.insights.InsightsPeriod
import com.dantech.splitvibe.ui.insights.insightsOf
import com.dantech.splitvibe.ui.recurring.RecurringActions
import com.dantech.splitvibe.ui.requests.RequestMoneyViewModel
import com.dantech.splitvibe.ui.requests.RequestTotals
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.AccountUiState
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.dantech.splitvibe.ui.split.Progress
import com.dantech.splitvibe.ui.split.SplitAssistantViewModel
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.dantech.splitvibe.ui.split.SplitScreen
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws every screen of the flow and compares it with the reference image in `src/test/screenshots`.
 *
 * The screens are the real ones, rendered by the Android framework on the build machine, so a
 * visual change shows up as a failing test instead of going unnoticed. The data is the same as in
 * [com.dantech.splitvibe.ui.split.SplitFlowTest]: the recorded ML Kit output and Claude answer for
 * the bundled sample bill.
 *
 * After an intended visual change, record new reference images and review them like code:
 *
 *     ./gradlew :app:testProdDebugUnitTest --tests '*ScreenGalleryTest*' -Pscreenshots=record
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class ScreenGalleryTest {

    /**
     * What the screens use from outside them, closed only once the screens are gone: the compose rule
     * inside this one removes the screens first. Closed any earlier, a list of bills still on screen
     * would query the closed database when a save lands late. The file provider starts and ends each
     * test without the folders of another test.
     */
    @get:Rule(order = 0)
    val outsideTheScreens = object : ExternalResource() {
        override fun before() = forgetSharedFolders()

        override fun after() {
            server.shutdown()
            storage.close()
            forgetSharedFolders()
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()
    private val settings = AppSettings(localCurrency = "VND")

    /** A state of home: the requests the navigation bar and the main block read, and whether a group is kept. */
    private enum class HomeState(val shot: String, val requests: RequestTotals, val keepsGroup: Boolean) {
        OWED("owed", HomeFixtures.owed, keepsGroup = true),
        OWING("owing", HomeFixtures.owing, keepsGroup = true),
        SPENDING("spending", RequestTotals.of(signedIn = true, requests = emptyList()), keepsGroup = true),

        // Signing out empties what the phone kept of the account, its groups too.
        SIGNED_OUT("signed-out", RequestTotals.SignedOut, keepsGroup = false),

        // As right after a sign-in, which empties what the phone kept of the account before.
        LOADING("loading", RequestTotals.Unknown, keepsGroup = false),
    }

    private enum class Screen {
        HOME,
        SPLIT,
        SPLIT_WITHOUT_AI,
        SETTINGS,
        SETTINGS_SIGNED_IN,
        SETTINGS_SIGN_IN_EXPIRED,
        SETTINGS_SPEECH_SERVICE,
        HISTORY,
    }

    private var screen by mutableStateOf(Screen.HOME)

    /** This month's spending, which [Screen.HOME] shows once a bill is kept, as the home's view model adds it up. */
    private var homeMonth: MonthSpending? by mutableStateOf(null)

    /** Added to every file name of a walk, so the dark and large-text images sit beside the light ones. */
    private var suffix = ""

    private val recordedMlKitOutput = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    /** The assistant of the result screen, asking the same stand-in for the proxy as the bill read. */
    private lateinit var assistant: SplitAssistantViewModel

    @Before
    fun setUp() {
        server.start()
        // A phone that turns speech into text by itself, as most new phones do, so the result screen offers the mic.
        TestVoice.recognizeOnDevice()
    }

    @Test
    fun `every screen of the split flow`() = everyScreen()

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `every screen of the split flow in the dark theme`() {
        suffix = "-dark"
        everyScreen()
    }

    @Test
    @Config(sdk = [34], qualifiers = TALL)
    fun `whole result page`() = wholeResultPage()

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `whole result page in the dark theme`() {
        suffix = "-dark"
        wholeResultPage()
    }

    /** Changing the split by a sentence: the field on the result screen, then what the AI proposes, before and after. */
    @Test
    fun `the assistant on the result screen`() = assistantScreens()

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `the assistant on the result screen in the dark theme`() {
        suffix = "-dark"
        assistantScreens()
    }

    /** Settings once the user let the phone's speech service hear them, which they can take back there. */
    @Test
    fun `settings once the phone's speech service was allowed`() = speechServiceSettings()

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `settings once the phone's speech service was allowed, in the dark theme`() {
        suffix = "-dark"
        speechServiceSettings()
    }

    /** Text at twice its size must still fit: nothing may be cut off on the four main screens. */
    @Test
    fun `the four main screens with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        suffix = "-large-text"
        val viewModel = start(answerDelaySeconds = 0)
        shot("01-home")

        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        shot("03-photo-and-instruction")

        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        shot("05-result-top")

        compose.onNode(hasText("Request", substring = true) and hasClickAction()).performClick()
        shot("10-requests-sent")
    }

    /**
     * The narrow fields of the flow with text at twice its size: a person's percentage beside their name,
     * the amounts of who paid, and the quantity, price and amount of an item, none of them cut off.
     */
    @Test
    fun `the parts and the fields of the bill with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        suffix = "-large-text"
        val viewModel = start(answerDelaySeconds = 0)
        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        // The shop's name keeps to one line of the top bar, however large the text.
        val shop = checkNotNull(checkNotNull(viewModel.state.value.result).bill.merchant)
        val title = compose.onNode(hasText(shop) and isHeading(), useUnmergedTree = true)
        assertEquals("The shop's name takes more than one line", 1, layoutOf(title).lineCount)

        val list = compose.onNode(hasScrollToIndexAction())
        splitByPercent(list)
        // The first person's row, with the field below the name, up under the top bar.
        val percent = hasSetTextAction() and hasText("Percent for You")
        list.performScrollToNode(percent)
        val field = compose.onNode(percent).fetchSemanticsNode().boundsInRoot.top
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, field - PART_TOP_PX) }
        shot("18-split-by-percent")
        assertSpansItsRow(compose.onNode(percent).fetchSemanticsNode())

        list.performScrollToNode(hasText("You paid", substring = true))
        compose.onNode(hasText("You paid", substring = true) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Several people").performClick()
        shot("19-who-paid")
        // The user paid the whole bill, a number too wide to stand beside the name at this size. The others'
        // 0 fits there, and so stays beside their names, as a part that fits does.
        assertSpansItsRow(compose.onNode(hasSetTextAction() and hasText("Amount for You")).fetchSemanticsNode())
        compose.onNodeWithText("One person").performClick()
        compose.onNodeWithText("Done").performClick()

        editTheFirstItem()
        // The open item up under the top bar, with its three fields of numbers below its name.
        val editor = compose.onNode(hasScrollToIndexAction())
        editor.performScrollToNode(hasSetTextAction() and hasText("Item"))
        val item = compose.onNode(hasSetTextAction() and hasText("Item")).fetchSemanticsNode().boundsInRoot.top
        editor.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, item - PART_TOP_PX) }
        shot("20-edit-bill")
    }

    /**
     * The four main screens on a phone set to Vietnam, whose amounts are written "902.664 ₫" and days
     * "13 Nov": the texts stay English. Home comes last, once the split bill is kept, so that it shows
     * an amount and a day too. Before any picture is taken, the test checks that the phone is set to
     * Vietnam and that the app writes the sample bill's total there as "902.664 ₫"; before each picture
     * that shows the total, that the screen shows it so and not in the app's form for the United States,
     * so that a picture of the wrong region is never recorded as a reference.
     */
    @Test
    @Config(sdk = [34], qualifiers = PHONE_IN_VIETNAM)
    fun `the four main screens on a phone set to Vietnam`() {
        suffix = "-vi-vn"
        // The region and the sample bill's total as the screens write it, before any of them is drawn,
        // the photo and the instruction among them, which show no amount.
        assertEquals("the phone's region", Locale.forLanguageTag("vi-VN"), Locale.getDefault(Locale.Category.FORMAT))
        assertEquals(VIETNAMESE_TOTAL, BillCurrency("VND", 0).format(902_664))
        val viewModel = start(answerDelaySeconds = 0)

        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        shot("03-photo-and-instruction")

        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        val result = checkNotNull(viewModel.state.value.result)
        // The total of the bill, and the line below the shares that they add up to it.
        assertTheTotalWrittenTheVietnameseWay()
        shot("05-result-top")

        // The ticket of the whole split carries the total.
        compose.onNode(hasText("Request", substring = true) and hasClickAction()).performClick()
        assertTheTotalWrittenTheVietnameseWay()
        shot("10-requests-sent")

        // Home once the split is kept: this month's spending, and the bill with its amount and its day.
        awaitState { runBlocking { storage.bills.get(result.saved.id) }?.status == BillStatus.FINAL }
        val kept = checkNotNull(runBlocking { storage.bills.get(result.saved.id) })
        val month = insightsOf(listOf(kept), settings.localCurrency, InsightsPeriod.THIS_MONTH, TestClock(HomeFixtures.today))
        homeMonth = MonthSpending.of(YearMonth.from(HomeFixtures.today), month.insights)
        show(Screen.HOME)
        val shop = checkNotNull(result.bill.merchant)
        awaitState { compose.onAllNodes(hasText(shop, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(VIETNAMESE_BILL_DAY, substring = true)).assertExists()
        assertTheTotalWrittenTheVietnameseWay()
        shot("01-home")
    }

    /**
     * Home in each state it can be in beside the first one: what friends owe the user, only what the
     * user owes, this month's spending, signed out, and signed in with the requests still on their
     * way, which shows as no request. The data is that of the approved design of the home.
     */
    @Test
    fun `home in each of its states`() = homeStates(HomeState.entries)

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `home with what friends owe, in the dark theme`() {
        suffix = "-dark"
        homeStates(listOf(HomeState.OWED))
    }

    @Test
    fun `home with what friends owe, with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        suffix = "-large-text"
        homeStates(listOf(HomeState.OWED))
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE_IN_VIETNAM)
    fun `home with what friends owe, on a phone set to Vietnam`() {
        suffix = "-vi-vn"
        homeStates(listOf(HomeState.OWED)) {
            // The sample bill is the first of the recent bills.
            assertTheTotalWrittenTheVietnameseWay()
            // What friends owe, and the currency on the side a phone set to Vietnam puts it.
            compose.onNodeWithContentDescription("651.671 ₫").assertExists()
        }
    }

    /** The sheet of someone on the bill, which a tap on their row opens, to rename them or take them off the bill. */
    @Test
    fun `the sheet of someone on the bill`() {
        readTheSampleBill()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(rowOf("Minh"))
        compose.onNode(rowOf("Minh")).performClick()
        shot("07-person-sheet")
    }

    /**
     * A bill in dong for someone whose money is in dollars: the sheet of the rate, with the rate their
     * card was charged at typed in, then what the app says when the day's rate cannot be fetched, as
     * when the phone has no connection. A build without the proxy's address, as here, has no rates.
     */
    @Test
    fun `the rate of a bill in another currency, and a day's rate that cannot be fetched`() {
        val viewModel = readTheSampleBill(localCurrency = "USD")
        compose.onNodeWithText("Add a rate").performClick()
        compose.onNode(hasSetTextAction() and hasText("1 VND in USD")).performTextInput(CARD_RATE)
        shot("05-exchange-rate")

        compose.onNodeWithText("Use the day's rate").performClick()
        awaitState { viewModel.state.value.notice != null }
        shot("05-rate-not-fetched")
    }

    /** On a phone that cannot turn speech into text by itself, the mic first asks whether the phone's speech service may hear the change. */
    @Test
    fun `the question before the phone's speech service hears a change`() {
        TestVoice.recognizeOnDevice(available = false)
        TestVoice.installSpeechService(context)
        readTheSampleBill()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Change the split") and hasSetTextAction())
        compose.onNodeWithContentDescription("Say the change").performClick()
        compose.onNodeWithText(context.getString(R.string.assistant_speech_service_title)).assertExists()
        shot("30-assistant-speech-service")
    }

    /**
     * The step of the requests for a signed-in user with two bank accounts: the sheet that picks the one
     * the requests are paid back on, then what the app says when a request could not be kept on the
     * proxy, which answers every call with an error, as a server that is down does.
     */
    @Test
    fun `the way to be paid back, and a request that could not be kept`() {
        readTheSampleBill()
        signInForTheRequests()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(SERVER_ERROR)
        }
        compose.onNode(hasText("Request", substring = true) and hasClickAction()).performClick()
        val change = hasText("Change") and hasClickAction()
        awaitState { compose.onAllNodes(change).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(change).performClick()
        shot("10-requests-method-sheet")

        compose.onNode(hasText("Cancel") and hasClickAction()).performClick()
        // The people come below the name on the requests, further down than the screen reaches.
        val send = hasText("Send") and hasClickAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(send)
        compose.onAllNodes(send).onFirst().performClick()
        // The request still goes to the share sheet, without its link, and its row then offers to send it again.
        val untracked = context.getString(R.string.requests_untracked)
        awaitState {
            compose.onAllNodesWithText(untracked).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText("Again") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        shot("10-requests-untracked")
    }

    private fun everyScreen() {
        // The answer is held back so the "working" state stays on screen long enough to be drawn.
        val viewModel = start(answerDelaySeconds = 4)

        // A phone that keeps no bill yet, signed out: the invitation to split a first one.
        shot("01-home")

        show(Screen.SETTINGS)
        shot("11-settings")

        show(Screen.SETTINGS_SIGNED_IN)
        shot("16-settings-signed-in")

        show(Screen.SETTINGS_SIGN_IN_EXPIRED)
        shot("17-settings-sign-in-expired")

        show(Screen.SPLIT_WITHOUT_AI)
        shot("12-new-split-without-ai")

        show(Screen.SPLIT)
        shot("02-new-split")

        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)
        compose.onNodeWithText(context.getString(R.string.split_default_instruction)).performTextReplacement(INSTRUCTION)
        shot("03-photo-and-instruction")

        // The working state shows an endless animation. Its clock is stopped and then moved on by a
        // fixed amount, so the picture catches the same moment of the animation on every run.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.progress == Progress.ASKING_CLAUDE }
        compose.mainClock.advanceTimeBy(WORKING_ANIMATION_MS)
        shot("04-working")
        compose.mainClock.autoAdvance = true

        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        shot("05-result-top")

        // The lazy list only: the row of the ways to split scrolls sideways and would match a plain scroll matcher.
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Adds up to the bill total"))
        // The shares and their total clear of the bottom bar.
        val total = compose.onNodeWithText("Adds up to the bill total").fetchSemanticsNode().boundsInRoot.bottom
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, total - SHARES_TOTAL_BOTTOM_PX) }
        shot("06-result-shares")

        // Someone split with before is suggested as their name is typed.
        list.performScrollToNode(hasText("Add someone"))
        compose.onNodeWithText("Add someone").performClick()
        compose.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Bao")
        shot("07-add-person")
        compose.onNode(hasText("Add") and hasClickAction() and hasAnyAncestor(hasText("Bao Tran"))).performClick()
        awaitState { viewModel.state.value.result?.plan?.participants?.size == 4 }

        // Bring the beers into view with a few rows above them, as the picture shows them, then tap the row.
        list.performScrollToNode(hasText(BEERS))
        val beers = compose.onNodeWithText(BEERS).fetchSemanticsNode().boundsInRoot.top
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, beers - BEERS_TOP_PX) }
        compose.onNodeWithText(BEERS).performClick()
        shot("08-item-split")
        compose.onNodeWithText("Done").performClick()

        // Divided by percent, each person's part can be typed in, beside their name.
        splitByPercent(list)
        shot("18-split-by-percent")

        // Opened through its click action, which does not depend on where the row lies on the screen.
        list.performScrollToNode(hasText("You paid", substring = true))
        compose.onNode(hasText("You paid", substring = true) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Several people").performClick()
        shot("19-who-paid")
        // Back to me paying it all, so the requests can go out.
        compose.onNodeWithText("One person").performClick()
        compose.onNodeWithText("Done").performClick()

        editTheFirstItem()
        shot("20-edit-bill")
        compose.onNode(hasText("Done") and hasClickAction()).performClick()
        // Back on the split before the clock is stopped below.
        compose.onNode(hasText("Request", substring = true) and hasClickAction()).assertExists()

        // The request button turns into the mark that confirms the requests. The picture catches
        // that change halfway, with the clock stopped as for the working state.
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Request", substring = true) and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(HALFWAY_TO_SENT_MS)
        shot("15-sending")
        compose.mainClock.autoAdvance = true
        shot("10-requests-sent")
        // The list of the requests ends above the bottom bar, so none of its rows passes under the bar.
        // Padding at the end of the list alone would only lift its last row above the bar.
        val bar = compose.onNodeWithText("Done").onParent().getBoundsInRoot()
        val requests = compose.onNode(hasScrollToIndexAction()).getBoundsInRoot()
        assertTrue("The requests run on to ${requests.bottom}, under the top of the bottom bar at ${bar.top}", requests.bottom <= bar.top)

        // The picture that goes to the group chat, as the app writes it to share: the whole ticket,
        // its notches and corners on the colour of the page.
        compose.onNodeWithText("Share the split as a picture").performClick()
        // The share sheet opens once the picture is written whole.
        awaitState { lastStarted()?.action == Intent.ACTION_CHOOSER }
        val sent = checkNotNull(IntentCompat.getParcelableExtra(checkNotNull(lastStarted()), Intent.EXTRA_INTENT, Intent::class.java))
        val shared = checkNotNull(IntentCompat.getParcelableExtra(sent, Intent.EXTRA_STREAM, Uri::class.java)) {
            "The split went to the share sheet without its picture"
        }
        // The provider's folder of shared pictures is the folder of that name in the cache.
        val file = File(context.cacheDir, shared.pathSegments.joinToString("/"))
        val picture = checkNotNull(BitmapFactory.decodeFile(file.path)) { "The shared picture could not be read" }
        // The ticket's top corner is round: the picture shows the page there, not a hole that a chat fills with black.
        assertEquals("A round corner of the ticket is a hole in the picture", 255, Color.alpha(picture.getPixel(0, 0)))
        picture.captureRoboImage(File(SCREENSHOTS, "33-shared-picture$suffix.png"))

        // The finished split is the first bill in the list.
        awaitState { runBlocking { storage.bills.get(checkNotNull(viewModel.state.value.result).saved.id) }?.status == BillStatus.FINAL }
        show(Screen.HISTORY)
        // The sample bill's date, of the year of the day the list is drawn on, written short.
        compose.onNode(hasText(SAMPLE_BILL_DAY, substring = true)).assertExists()
        shot("21-history")
    }

    private fun assistantScreens() {
        val viewModel = start(answerDelaySeconds = 0)
        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText(context.getString(R.string.split_default_instruction)).performTextReplacement(INSTRUCTION)
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)

        // The field below what each person pays, up under the top bar.
        val field = hasText("Change the split") and hasSetTextAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(field)
        compose.onNode(field).performTextInput(ASSISTANT_INSTRUCTION)
        shot("30-assistant")

        server.enqueue(revisionAnswer())
        compose.onNodeWithContentDescription("Ask the AI").performClick()
        awaitState { assistant.state.value.let { it.proposal != null || it.error != null } }
        assertNull(assistant.state.value.error)
        // The user keeps two of the three changes.
        val paid = hasContentDescription("Minh paid the whole bill") and isToggleable()
        compose.onNode(paid).performSemanticsAction(SemanticsActions.OnClick)
        // The top of the page, as the approved design draws it: the total, then the proposed changes below it.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        shot("31-assistant-proposal")
    }

    /**
     * Fails unless the field [node] reaches across its row, as a part too wide for its place beside a
     * name does, rather than standing beside the name at the width of a part. On each side lie the
     * screen's margin and, in a list of rows, the row's inset and that of the part, so that is how far
     * from the edges of the screen it may start and end.
     */
    private fun assertSpansItsRow(node: SemanticsNode) {
        val label = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        // A sheet is a window of its own, as wide as the screen.
        val screenRight = context.resources.configuration.screenWidthDp.dp
        val inset = SvSpacing.screen + SvSpacing.sm * 2
        val field =
            with(compose.density) { node.boundsInRoot.let { DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) } }
        assertTrue("\"$label\" starts at ${field.left}, not across its row", field.left <= inset + SPAN_SLACK)
        assertTrue("\"$label\" ends at ${field.right}, not across its row", field.right >= screenRight - inset - SPAN_SLACK)
    }

    /** How the text of [node] is laid out: its lines as drawn. */
    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    /** Divides the bill on screen by percent, from the row of the ways to split, which scrolls sideways. */
    private fun splitByPercent(list: SemanticsNodeInteraction) {
        list.performScrollToNode(hasText("By percent"))
        // Through its action: the chip may lie partly beyond the edge of its row.
        compose.onNodeWithText("By percent").performSemanticsAction(SemanticsActions.OnClick)
        list.performScrollToNode(hasText("100% of 100%"))
    }

    /** Opens the editor of the bill on screen, with the row of its first item open, as the approved design shows it. */
    private fun editTheFirstItem() {
        compose.onNodeWithContentDescription("Edit bill").performClick()
        val firstItem = hasText(FIRST_ITEM) and hasClickAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(firstItem)
        compose.onNode(firstItem).performClick()
        compose.onNode(hasSetTextAction() and hasText("Item")).assertExists()
    }

    /** What the app last asked Android to open, such as the share sheet, or null before it asked. */
    private fun lastStarted(): Intent? = shadowOf(ApplicationProvider.getApplicationContext<Application>()).peekNextStartedActivity()

    /**
     * Makes the file provider look up its folders again. It keeps them in a static field for as long as
     * the process runs, while Robolectric gives every test folders of its own: after one test shared a
     * file, the files of the next test to share one would lie outside every folder the provider knows.
     */
    private fun forgetSharedFolders() {
        val folders = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<*, *>
        synchronized(folders) { folders.clear() }
    }

    private fun speechServiceSettings() {
        start(answerDelaySeconds = 0)
        show(Screen.SETTINGS_SPEECH_SERVICE)
        shot("32-settings-speech-service")
    }

    /**
     * Draws home in each of [states], named after it, with the navigation bar counting the requests of
     * that state, and runs [beforeEachShot] on each before its picture is taken.
     */
    private fun homeStates(states: List<HomeState>, beforeEachShot: () -> Unit = {}) {
        var state by mutableStateOf(states.first())
        compose.setContent {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                SplitVibeTheme {
                    val requests = state.requests
                    HomeScreen(
                        summary = HomeSummary.of(requests, HomeFixtures.bills, HomeFixtures.month, localCurrency = "VND"),
                        group = HomeFixtures.group.takeIf { state.keepsGroup },
                        bills = HomeFixtures.bills,
                        onOpenBill = {},
                        onSeeAllBills = {},
                        onOpenSettings = {},
                        onOpenRequests = {},
                        onOpenInsights = {},
                        onOpenGroup = {},
                        onSplitBill = {},
                        myName = HomeFixtures.MY_NAME,
                        today = HomeFixtures.today,
                        bottomBar = { AppNavBar(selected = HomeKey, requests = requests, onSelect = {}, onSplitBill = {}) },
                    )
                }
            }
        }
        for (each in states) {
            state = each
            compose.waitForIdle()
            beforeEachShot()
            shot("01-home-${each.shot}")
        }
        // The list of home ends above the navigation bar, so none of its rows ever passes under the bar.
        val bar = compose.onNodeWithContentDescription("Split a bill").onParent().getBoundsInRoot()
        val list = compose.onNode(hasScrollToIndexAction()).getBoundsInRoot()
        assertTrue("Home's list runs on to ${list.bottom}, under the top of the navigation bar at ${bar.top}", list.bottom <= bar.top)
    }

    /**
     * Fails unless a node of the screen shows or says the sample bill's total as the app writes it on a
     * phone set to Vietnam, "902.664 ₫", and none in the app's form for a phone set to the United States,
     * "₫902,664". An amount in large type shows in parts and is said whole, so both what nodes show and
     * what they say are read. The AI's own words, such as "902,664 VND" in its account of the bill, are
     * neither form and are left as they are.
     */
    private fun assertTheTotalWrittenTheVietnameseWay() {
        val vietnamese = compose.onAllNodes(writes(VIETNAMESE_TOTAL), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("The screen does not write the total as $VIETNAMESE_TOTAL", vietnamese.isNotEmpty())
        val american = compose.onAllNodes(writes(AMERICAN_TOTAL), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("The screen writes the total as $AMERICAN_TOTAL, as a phone in the United States does", american.isEmpty())
    }

    /** A node that shows or says [text], also within more. */
    private fun writes(text: String) = hasText(text, substring = true) or hasContentDescription(text, substring = true)

    private fun wholeResultPage() {
        val viewModel = start(answerDelaySeconds = 0)
        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText(context.getString(R.string.split_default_instruction)).performTextReplacement(INSTRUCTION)
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        shot("13-result-whole-page")
        // The picture is as tall as the window, so a page grown longer than the window would lose its
        // end without a word: its list must have nothing left to scroll to.
        val list = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertFalse("The result page runs on below the bottom of the picture", list.value() < list.maxValue())
    }

    /** Starts the flow on home; the split counts in [localCurrency], the currency the user's money is in. */
    private fun start(answerDelaySeconds: Long, localCurrency: String = settings.localCurrency): SplitBillViewModel {
        // People split with before, whose names are suggested again.
        storage.rememberTestPeople()
        server.enqueue(Fixtures.proxyAnswer().setHeadersDelay(answerDelaySeconds, TimeUnit.SECONDS))
        val proxy = TestProxy(server, context)
        val viewModel = testSplitViewModel(
            context,
            storage,
            account = proxy.account.account,
            recognizer = recordedMlKitOutput,
            analyzer = proxy.analyzer,
            localCurrency = localCurrency,
        )
        assistant = SplitAssistantViewModel(
            host = viewModel,
            assistant = SplitAssistant(proxy.api, proxy.account, canSignIn = true, sellsPro = false),
            voice = VoiceInput(context, SettingsStore(context)),
            // The person agreed to the AI before; the consent screen has its own gallery.
            settings = MutableStateFlow(settings.copy(aiAllowed = true)),
            allowAi = {},
            account = proxy.account.account,
        )
        compose.setContent {
            val bills by storage.bills.listings().collectAsState(emptyList())
            // No touch ripples: they are drawn by the platform on real time, so a picture taken
            // shortly after a tap would differ by a few pixels from run to run.
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                SplitVibeTheme {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            summary = HomeSummary.of(RequestTotals.SignedOut, bills, homeMonth, settings.localCurrency),
                            group = null,
                            bills = bills,
                            onOpenBill = {},
                            onSeeAllBills = {},
                            onOpenSettings = {},
                            onOpenRequests = {},
                            onOpenInsights = {},
                            onOpenGroup = {},
                            onSplitBill = {},
                            today = HomeFixtures.today,
                            bottomBar = {
                                AppNavBar(selected = HomeKey, requests = RequestTotals.SignedOut, onSelect = {}, onSplitBill = {})
                            },
                        )

                        // The day the dates of the bills are written as of, so the picture is the same in every year.
                        Screen.HISTORY -> HistoryScreen(
                            listings = bills,
                            query = "",
                            onQueryChanged = {},
                            onOpen = {},
                            onDelete = {},
                            onBack = {},
                            recurring = emptyList(),
                            recurringActions = NO_RECURRING_ACTIONS,
                            today = HomeFixtures.today,
                        )

                        Screen.SPLIT -> SplitScreen(viewModel = viewModel, aiStatus = AiStatus.AVAILABLE, newCameraTarget = {
                            Uri.EMPTY
                        }, scanIntent = { null }, onExit = {}, onOpenSettings = {}, onOpenPro = {}, assistant = assistant)

                        // What a build without the AI proxy's address shows.
                        Screen.SPLIT_WITHOUT_AI -> SplitScreen(viewModel = viewModel, aiStatus = AiStatus.NOT_SET_UP, newCameraTarget = {
                            Uri.EMPTY
                        }, scanIntent = { null }, onExit = {}, onOpenSettings = {}, onOpenPro = {}, assistant = assistant)

                        // Before signing in, with the count the proxy reported for this install.
                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            account = SIGNED_OUT,
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = { _, _ -> },
                            onOpenPaymentMethods = {},
                            onBack = {},
                        )

                        Screen.SETTINGS_SIGNED_IN -> SettingsScreen(
                            settings = settings,
                            account = SIGNED_IN,
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = { _, _ -> },
                            onOpenPaymentMethods = {},
                            onBack = {},
                        )

                        // The user let the phone's speech service hear them when they first said a change.
                        Screen.SETTINGS_SPEECH_SERVICE -> SettingsScreen(
                            settings = settings.copy(speechServiceAllowed = true),
                            account = SIGNED_OUT,
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = { _, _ -> },
                            onOpenPaymentMethods = {},
                            onBack = {},
                            onAllowSpeechService = {},
                        )

                        // The session ran out: sign in again, or sign out to use this phone's free calls.
                        Screen.SETTINGS_SIGN_IN_EXPIRED -> SettingsScreen(
                            settings = settings,
                            account = SIGNED_IN.copy(session = null, callsLeft = null, signInExpired = true),
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = { _, _ -> },
                            onOpenPaymentMethods = {},
                            onBack = {},
                        )
                    }
                }
            }
        }
        return viewModel
    }

    /** Reads the sample bill as the user does, without an instruction, and waits for its split; the split counts in [localCurrency]. */
    private fun readTheSampleBill(localCurrency: String = settings.localCurrency): SplitBillViewModel {
        val viewModel = start(answerDelaySeconds = 0, localCurrency = localCurrency)
        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        return viewModel
    }

    /**
     * Gives the step of the requests what a signed-in user has there: two bank accounts to be paid back
     * on, the first of them the main one, and their name for the requests. Only that step is signed in.
     */
    private fun signInForTheRequests() {
        val signedIn = TestProxy(server, context).apply { account.saveSession(Session("gallery-session", expiresAt = Long.MAX_VALUE)) }
        val methods = PaymentMethodRepository(storage.database)
        runBlocking {
            methods.add(PaymentMethod.VietQr("970416", "257678859", "DANA NGUYEN"))
            methods.add(PaymentMethod.VietQr("970436", "0071000123456", "DANA NGUYEN"))
        }
        loadKoinModules(
            module {
                viewModel {
                    RequestMoneyViewModel(
                        paymentMethods = methods,
                        requests = RequestRepository(signedIn.api, ProxyCache(storage.database.proxyCache()), signedIn.account),
                        account = signedIn.account.account,
                        settings = MutableStateFlow(settings.copy(myName = "Dana")),
                        saveName = {},
                    )
                }
            },
        )
    }

    /** The row of [name] on the split, which a tap opens the sheet of. */
    private fun rowOf(name: String) =
        SemanticsMatcher("taps to change $name") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Change $name" }

    private fun show(target: Screen) {
        screen = target
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File(SCREENSHOTS, "$name$suffix.png"))
    }

    /**
     * Waits for work that finishes on a background thread. Under Robolectric the test itself runs on
     * the main thread, so results posted back to it are only delivered when the main looper is run.
     */
    private fun awaitState(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}

/** Reference images, relative to the module directory the tests run in. */
private const val SCREENSHOTS = "src/test/screenshots"
private const val PHONE = "w412dp-h915dp-xhdpi"
private const val PHONE_AT_NIGHT = "w412dp-h915dp-night-xhdpi"

/** The same phone set to Vietnamese in Vietnam, the region its amounts and dates follow. */
private const val PHONE_IN_VIETNAM = "vi-rVN-w412dp-h915dp-xhdpi"

/** The day of the sample bill as a list of bills writes it in the year it is of, on a phone set to the United States. */
private const val SAMPLE_BILL_DAY = "Nov 13 · "

/** The sample bill's total as a phone set to Vietnam writes it, with a space that does not break before the symbol. */
private const val VIETNAMESE_TOTAL = "902.664\u00A0₫"

/** The sample bill's total in the app's form for a phone set to the United States, which no picture of a phone set to Vietnam may show. */
private const val AMERICAN_TOTAL = "₫902,664"

/** The day of the sample bill as a list of bills writes it in the year it is of, on a phone set to Vietnam. */
private const val VIETNAMESE_BILL_DAY = "13 Nov · "

/** Tall enough to show the whole result list at once, with a little room above the bottom bar. */
private const val TALL = "w412dp-h2080dp-xhdpi"
private const val TALL_AT_NIGHT = "w412dp-h2080dp-night-xhdpi"
private const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
private const val ASSISTANT_INSTRUCTION = "Lan didn't have any water. Add a 5% tip, and Minh paid"
private const val BEERS = "Bia Saigon Special"
private const val FIRST_ITEM = "Ốc hương rang muối"

/** What one dong was worth in dollars at the rate a card was charged, about 26,300 dong to the dollar. */
private const val CARD_RATE = "0.000038"
private const val SERVER_ERROR = 500

// Places on the screen at xhdpi (2 px per dp).

/** Where the row of the beers ends up: halfway down the screen, with the items above it in view. */
private const val BEERS_TOP_PX = 1000f

/** Where a person's field for their part ends up with large text: below the top bar, with the person's name above it in view. */
private const val PART_TOP_PX = 520f

/** How far a field's edge may be from where its row ends, for the rounding of positions to pixels. */
private val SPAN_SLACK = 1.dp

/** Where the line under the shares ends up: well above the bottom bar. */
private const val SHARES_TOTAL_BOTTOM_PX = 1500f
private const val TIMEOUT_MS = 60_000L
private const val WORKING_ANIMATION_MS = 500L
private const val HALFWAY_TO_SENT_MS = 200L

private val SIGNED_OUT = Account(
    installId = "gallery-install",
    session = null,
    callsLeft = CallsLeft(BigDecimal(3), used = BigDecimal("1.2"), remaining = BigDecimal("1.8"), period = "trial", resetsAt = null),
    ai = AiStatus.AVAILABLE,
)

private val SIGNED_IN = SIGNED_OUT.copy(
    session = Session("gallery-session", expiresAt = Long.MAX_VALUE),
    signedInAs = "minh.tran@example.com",
    // 1 December 2026, in UTC.
    callsLeft = CallsLeft(BigDecimal(10), BigDecimal(1), BigDecimal(9), period = "2026-11", resetsAt = 1_796_083_200_000),
)

private val NO_ACTIONS = AccountActions(onSignIn = {}, onSignOut = {}, onDeleteAccount = {}, onOpenPro = {})
private val NO_RECURRING_ACTIONS = RecurringActions(onPause = {}, onResume = {}, onDelete = {})

/** What the proxy answers to [ASSISTANT_INSTRUCTION] on the sample bill: the water, a tip and who paid, as three edits. */
private fun revisionAnswer(): MockResponse {
    val edits = """[{"op":"item_sharers","itemId":"i7","participantIds":["me","p1"]},""" +
        """{"op":"add_charge","kind":"tip","label":"Tip","percentBasisPoints":500},{"op":"set_payer","participantId":"p1"}]"""
    val answer = """{"edits":$edits,"explanation":"Lan no longer shares the water, a 5% tip is added, and Minh paid.","question":null}"""
    val usage = """{"limit":3,"used":1.2,"remaining":1.8,"period":"trial","resetsAt":null}"""
    return MockResponse()
        .setHeader("content-type", "application/x-ndjson; charset=utf-8")
        .setBody("{\"type\":\"wait\"}\n{\"type\":\"result\",\"answer\":$answer,\"usage\":$usage}\n")
}
