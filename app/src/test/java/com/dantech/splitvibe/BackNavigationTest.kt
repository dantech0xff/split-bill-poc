package com.dantech.splitvibe

import android.Manifest
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.motion.SvSharedSurfaceName
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.ui.AppLink
import com.dantech.splitvibe.ui.AppNavHost
import com.dantech.splitvibe.ui.SPLIT_SURFACE
import com.dantech.splitvibe.ui.split.Progress
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the back button and the back swipe do in the real activity, with the navigation bar of the
 * main screens, the links that open the app, and an account that signs out.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class BackNavigationTest {

    private lateinit var splitViewModel: SplitBillViewModel

    private val neverFinishes = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = awaitCancellation()
    }

    /** The payment requests of whoever signs in, as the proxy lists them: none, unless a test says otherwise. */
    @Volatile
    private var requestsOnTheProxy = "[]"

    /** Stands in for the AI proxy: it lists [requestsOnTheProxy], and has nothing else. */
    private val proxy = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = if (request.method == "GET" && request.path == "/v1/requests") {
                MockResponse().setHeader("Content-Type", "application/json").setBody("""{"requests":$requestsOnTheProxy}""")
            } else {
                MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}""")
            }
        }
    }

    /**
     * Replaces some of the app's objects before the activity starts. With a recognizer that never
     * finishes, the test can press back while the bill is being read. The account is kept in plain
     * text, as local tests have no Keystore, and it signs in and out against [proxy], without
     * Google's account picker. Work runs at once, as a recurring bill schedules some.
     */
    @get:Rule(order = 0)
    val standIns = object : ExternalResource() {
        override fun before() {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                ApplicationProvider.getApplicationContext(),
                Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
            )
            val address = proxy.url("/").toString()
            loadKoinModules(
                module {
                    viewModel {
                        SplitBillViewModel(
                            settings = MutableStateFlow(AppSettings(localCurrency = "VND", aiAllowed = true)),
                            allowAi = {},
                            account = get<AccountStore>().account,
                            canSignIn = true,
                            sellsPro = false,
                            imageLoader = get(),
                            recognizer = neverFinishes,
                            analyzer = NeverAsked,
                            bills = get(),
                            peopleRepository = get(),
                            pageStore = get(),
                            fx = get(),
                            contactNames = get(),
                            savedState = get(),
                        ).also { splitViewModel = it }
                    }
                    single { AccountStore(androidContext(), proxySetUp = true, cipher = PlainCipher) }
                    single { ProxyApi(address, account = get(), appVersion = TestProxy.APP_VERSION) }
                    single {
                        AccountRepository(
                            api = get(),
                            store = get(),
                            google = null,
                            cache = get(),
                            devices = get(),
                            phone = get(),
                            deletions = get(),
                            app = androidContext(),
                        )
                    }
                },
            )
        }

        override fun after() = proxy.shutdown()
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `a back swipe previews the previous screen and can be cancelled`() {
        compose.onNodeWithContentDescription(SPLIT_A_BILL).performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()

        swipeBackHalfway()
        // While the finger is down both screens are drawn: the one being left and the one behind it.
        compose.onNodeWithText("New split").assertExists()
        compose.onNodeWithText("Recent bills").assertExists()
        captureScreenRoboImage(File("src/test/screenshots/14-back-swipe-preview.png"))

        onBackDispatcher { dispatchOnBackCancelled() }
        compose.onNodeWithText("New split").assertIsDisplayed()
        compose.onNodeWithText("Recent bills").assertDoesNotExist()

        swipeBackHalfway()
        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        compose.onNodeWithText("New split").assertDoesNotExist()
    }

    @Test
    fun `back stops running work before it leaves the split screen`() {
        compose.onNodeWithContentDescription(SPLIT_A_BILL).performClick()
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { splitViewModel.state.value.let { it.photo != null && !it.isBusy } }
        compose.runOnUiThread { splitViewModel.analyze(defaultInstruction = "Split this bill") }
        awaitState { splitViewModel.state.value.progress == Progress.READING_TEXT }

        onBackDispatcher { onBackPressed() }
        assertFalse(splitViewModel.state.value.isBusy)
        compose.onNodeWithText("New split").assertIsDisplayed()

        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
    }

    @Test
    fun `back on a tab goes home, and back on home is left to the system, which leaves the app`() {
        for ((name, title) in TAB_TITLES) {
            compose.onNode(tab(name)).performClick()
            compose.onNodeWithText(title).assertIsDisplayed()
            assertTrue("Back on $title is not the app's", backIsTheApps())

            onBackDispatcher { onBackPressed() }

            compose.onNodeWithText("Recent bills").assertIsDisplayed()
            compose.onNodeWithText(title).assertDoesNotExist()
            compose.onNode(tab("Home")).assertIsSelected()
        }
        assertFalse("Back on home stays in the app", backIsTheApps())
    }

    @Test
    fun `a back swipe on a tab previews home, can be cancelled, and goes home once let go`() {
        compose.onNode(tab("Groups")).performClick()
        compose.onNodeWithText("Groups").assertIsDisplayed()

        swipeBackHalfway()
        compose.onNodeWithText("Groups").assertExists()
        compose.onNodeWithText("Recent bills").assertExists()

        onBackDispatcher { dispatchOnBackCancelled() }
        compose.onNodeWithText("Groups").assertIsDisplayed()
        compose.onNodeWithText("Recent bills").assertDoesNotExist()
        compose.onNode(tab("Groups")).assertIsSelected()

        swipeBackHalfway()
        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        compose.onNodeWithText("Groups").assertDoesNotExist()
        compose.onNode(tab("Home")).assertIsSelected()
    }

    @Test
    fun `while a split opens, the button it grows from and the split screen both carry the surface that grows`() {
        val surface = SemanticsMatcher.expectValue(SvSharedSurfaceName, SPLIT_SURFACE)
        compose.onAllNodes(surface).assertCountEquals(1)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(SPLIT_A_BILL).performClick()
        // Frame by frame, so what a frame starts on the main thread runs before the next one, halfway into the fade.
        repeat(HALFWAY_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        // One on the screen being left, the other on the one coming: so the button grows into the screen.
        compose.onAllNodes(surface).assertCountEquals(2)
        compose.onNodeWithText("Recent bills").assertExists()
        compose.onNodeWithText("New split").assertExists()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onAllNodes(surface).assertCountEquals(1)
        compose.onNodeWithText("New split").assertIsDisplayed()
    }

    @Test
    fun `the button in the middle starts a split from every main screen, and back returns to that screen`() {
        for ((name, title) in listOf("Home" to "Recent bills") + TAB_TITLES) {
            compose.onNode(tab(name)).performClick()
            compose.onNodeWithText(title).assertIsDisplayed()

            compose.onNodeWithContentDescription(SPLIT_A_BILL).performClick()
            compose.onNodeWithText("New split").assertIsDisplayed()
            assertNoNavigationBar("New split")

            onBackDispatcher { onBackPressed() }
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNode(tab(name)).assertIsSelected()
        }
    }

    @Test
    fun `the navigation bar shows on the four main screens and on no other`() {
        keepTheRent()
        assertNavigationBarOn("Home")
        for ((name, title) in TAB_TITLES) {
            compose.onNode(tab(name)).performClick()
            compose.onNodeWithText(title).assertIsDisplayed()
            assertNavigationBarOn(name)
        }
        compose.onNode(tab("Home")).performClick()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Local currency").assertIsDisplayed()
        assertNoNavigationBar("Settings")
        compose.onNode(hasText("Get Pro") and hasClickAction()).performClick()
        compose.onNodeWithText("Split Vibe Pro").assertIsDisplayed()
        assertNoNavigationBar("Pro")
        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Ways to get paid").performScrollTo().performClick()
        compose.onNodeWithText("Ways to get paid").assertIsDisplayed()
        assertNoNavigationBar("the ways to get paid")
        onBackDispatcher { onBackPressed() }
        onBackDispatcher { onBackPressed() }

        compose.onNodeWithText("See all").performClick()
        compose.onNodeWithText("All bills").assertIsDisplayed()
        assertNoNavigationBar("the list of bills")
        onBackDispatcher { onBackPressed() }

        // The bill opens on the split screen, where everyone at the table can pick what they had.
        compose.onNodeWithText("Rent").performClick()
        awaitShown("Bill total")
        assertNoNavigationBar("the split")
        compose.onNodeWithContentDescription("More ways to finish").performClick()
        compose.onNodeWithText("Let everyone pick their items").performClick()
        compose.onNodeWithText("Everyone picks their items").assertIsDisplayed()
        assertNoNavigationBar("the bill's link")
        onBackDispatcher { onBackPressed() }
        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        assertNavigationBarOn("Home")
    }

    @Test
    fun `a tab chosen while a back swipe is held leaves no screen blank`() {
        compose.onNode(tab("Groups")).performClick()
        compose.onNodeWithText("Groups").assertIsDisplayed()

        swipeBackHalfway()
        // Both screens are drawn, each with its bar, and a tab of either does the same.
        compose.onAllNodes(tab("Requests")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        onBackDispatcher { dispatchOnBackCancelled() }

        assertDrawn("Requests")
        compose.onNode(tab("Requests")).assertIsSelected()
        compose.onNodeWithText("Groups").assertDoesNotExist()

        onBackDispatcher { onBackPressed() }
        assertDrawn("Recent bills")
        assertFalse("Back on home stays in the app", backIsTheApps())
    }

    @Test
    fun `a tab chosen again on the bar of home while back from that tab is still fading shows that tab`() {
        compose.onNode(tab("Groups")).performClick()
        compose.onNodeWithText("Groups").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        onBackDispatcher { onBackPressed() }
        // The bar of home, which is coming, stands on Home; the bar of Groups, which is going, on Groups.
        val groupsOnTheBarOfHome = tab("Groups") and isNotSelected()
        advanceFramesUntil("home is drawn") { exists(groupsOnTheBarOfHome) }
        // Groups goes back on the stack before its way out is over.
        assertTrue("Groups is no longer on its way out", exists(hasText("Groups")))
        compose.onNode(groupsOnTheBarOfHome).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertDrawn("Groups")
        compose.onNode(tab("Groups")).assertIsSelected()
        compose.onNodeWithText("Recent bills").assertDoesNotExist()
    }

    @Test
    fun `the button in the middle tapped while back from the split is still fading opens the split again`() {
        compose.onNodeWithContentDescription(SPLIT_A_BILL).performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        onBackDispatcher { onBackPressed() }
        advanceFramesUntil("home is drawn") { exists(hasContentDescription(SPLIT_A_BILL)) }
        // The split goes back on the stack before its way out is over.
        assertTrue("The split is no longer on its way out", exists(hasText("New split")))
        compose.onNodeWithContentDescription(SPLIT_A_BILL).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertDrawn("New split")
        assertNoNavigationBar("the split")
        onBackDispatcher { onBackPressed() }
        assertDrawn("Recent bills")
        compose.onNode(tab("Home")).assertIsSelected()
    }

    @Test
    fun `a tab chosen while a back swipe is held, with the swipe then let go, goes home`() {
        compose.onNode(tab("Groups")).performClick()
        compose.onNodeWithText("Groups").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        swipeBackHalfway()
        advanceFramesUntil("home is drawn behind the swipe") { exists(hasText("Recent bills")) }
        assertTrue("Groups is no longer under the swipe", exists(hasText("Groups")))
        compose.onAllNodes(tab("Requests")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        advanceFrames(FEW_FRAMES)
        // Let go: back now goes from Requests, the tab that was chosen.
        onBackDispatcher { onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertDrawn("Recent bills")
        compose.onNode(tab("Home")).assertIsSelected()
        compose.onNodeWithText("Requests").assertDoesNotExist()
        assertFalse("Back on home stays in the app", backIsTheApps())
    }

    @Test
    fun `home chosen while a back swipe is held, with the swipe then let go, stays on home`() {
        compose.onNode(tab("Groups")).performClick()
        compose.onNodeWithText("Groups").assertIsDisplayed()

        swipeBackHalfway()
        // Home alone is left on the stack while the swipe is still held.
        compose.onAllNodes(tab("Home")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        onBackDispatcher { onBackPressed() }

        assertDrawn("Recent bills")
        compose.onNode(tab("Home")).assertIsSelected()
        assertFalse("Back on home stays in the app", backIsTheApps())
    }

    @Test
    fun `Repeat on the All bills tab shows the bill on the Recurring tab of the same screen, and one back leaves the screen`() {
        // A recurring bill asks to show notices; given already, nothing covers the screen.
        shadowOf(ApplicationProvider.getApplicationContext<SplitVibeApp>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        keepTheRent()
        compose.onNodeWithText("See all").performClick()
        compose.onNodeWithText("All bills").assertIsSelected()

        compose.onNodeWithContentDescription("More for Rent").performClick()
        compose.onNodeWithText("Repeat this bill").performClick()
        compose.onNode(hasText("Repeat") and hasClickAction()).performClick()

        compose.onNodeWithText("Recurring").assertIsSelected()
        compose.onNodeWithText("All bills").assertIsNotSelected()
        awaitState { compose.onAllNodesWithText(RECURRING_INTRO).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Rent").assertIsDisplayed()
        compose.onNodeWithText("Bills").assertIsDisplayed()

        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        compose.onNodeWithText("Bills").assertDoesNotExist()
    }

    @Test
    fun `each kind of link opens its screen right above home, and back goes home`() {
        keepTheRent()
        val links = listOf(
            AppLink.JoinGroup(TOKEN) to "Join a group",
            AppLink.OpenRequest(TOKEN) to "Payment request",
            AppLink.Group(GroupFixtures.GROUP_ID) to "Group",
            AppLink.Requests to "Requests",
            AppLink.OpenBill("rent") to "Bill total",
        )
        for ((link, title) in links) {
            openWith(link)
            awaitShown(title)
            if (link == AppLink.Requests) assertNavigationBarOn("Requests") else assertNoNavigationBar(title)

            onBackDispatcher { onBackPressed() }

            compose.onNodeWithText("Recent bills").assertIsDisplayed()
            compose.onNodeWithText(title).assertDoesNotExist()
            assertFalse("Back after $link stays in the app", backIsTheApps())
        }
    }

    @Test
    fun `once the account signs out, only home and the settings it signed out in stay open`() {
        compose.onNode(tab("Groups")).performClick()
        compose.onNodeWithText("Sign in in Settings").performClick()
        compose.onNodeWithText("Local currency").assertIsDisplayed()
        signIn()
        awaitState { exists(hasText("Sign out")) }

        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        awaitState { accountStore().account.value.session == null }
        compose.waitForIdle()

        // The person stays where they signed out; behind Settings there is only home.
        compose.onNodeWithText("Local currency").assertIsDisplayed()
        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        compose.onNodeWithText("Groups").assertDoesNotExist()
        assertFalse("Back on home stays in the app", backIsTheApps())
    }

    @Test
    fun `an account that signed out while the activity was away still closes its screens once the activity is made again`() {
        compose.onNode(tab("Groups")).performClick()
        compose.onNodeWithText("Sign in in Settings").performClick()
        compose.onNodeWithText("Local currency").assertIsDisplayed()
        signIn()
        awaitState { exists(hasText("Sign out")) }

        // In the background the activity no longer follows the account, which signs out meanwhile.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        signOut()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        compose.onNodeWithText("Local currency").assertIsDisplayed()
        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        compose.onNodeWithText("Groups").assertDoesNotExist()
    }

    @Test
    fun `the Requests tab counts the requests that wait for the account signed in, and none once it signed out`() {
        // One request to the user, about which nobody said anything yet.
        requestsOnTheProxy = "[${GroupFixtures.request(role = "debtor")}]"
        signIn()

        awaitState { exists(tab("Requests, 1")) }
        assertNavigationBarOn("Home")

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        awaitState { accountStore().account.value.session == null }
        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        compose.onNode(tab("Requests")).assertExists()
        compose.onAllNodes(tab("Requests, 1")).assertCountEquals(0)
    }

    /**
     * A sign-in, as Google's sign-in, which cannot run in a test, leaves it: what was kept for anyone
     * before goes, and the session is kept.
     */
    private fun signIn() {
        clearTheCache()
        accountStore().saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE), signedInAs = "lan@example.com")
    }

    /** A sign-out, as Settings makes it: the session goes, and what was kept for the account. */
    private fun signOut() {
        accountStore().clearSession()
        clearTheCache()
    }

    /**
     * Empties what the phone kept of the account, off the main thread, which goes on running while the
     * test waits. A fetch of the requests can hold the cache while it waits to go on on the main thread,
     * so blocking the main thread until the cache is empty would wait for ever. A clear that failed
     * fails the test, as the cache then still holds what it kept.
     */
    private fun clearTheCache() {
        val clearing = CoroutineScope(Dispatchers.Default).async { GlobalContext.get().get<ProxyCache>().clear() }
        awaitState { clearing.isCompleted }
        // It has ended, so this only hands on how: it throws what the clear threw.
        runBlocking { clearing.await() }
    }

    private fun accountStore(): AccountStore = GlobalContext.get().get()

    private fun exists(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /**
     * With the clock stopped, moves it on frame by frame, and lets what each frame starts on the main
     * thread run before the next, until [condition] holds, which [what] describes. Fails once a fade
     * between screens would be over.
     */
    private fun advanceFramesUntil(what: String, condition: () -> Boolean) {
        repeat(FADE_FRAMES) {
            if (condition()) return
            advanceFrames(1)
        }
        throw AssertionError("Not yet so after $FADE_FRAMES frames: $what")
    }

    private fun advanceFrames(frames: Int) = repeat(frames) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun swipeBackHalfway() = onBackDispatcher {
        dispatchOnBackStarted(BackEventCompat(touchX = 0f, touchY = 900f, progress = 0f, swipeEdge = BackEventCompat.EDGE_LEFT))
        dispatchOnBackProgressed(BackEventCompat(touchX = 300f, touchY = 900f, progress = 0.5f, swipeEdge = BackEventCompat.EDGE_LEFT))
    }

    private fun onBackDispatcher(action: OnBackPressedDispatcher.() -> Unit) {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.action() }
        compose.waitForIdle()
    }

    /** Whether the app takes the back button where it is now; where it does not, the system does and leaves the app. */
    private fun backIsTheApps(): Boolean {
        var taken = false
        compose.activityRule.scenario.onActivity { taken = it.onBackPressedDispatcher.hasEnabledCallbacks() }
        return taken
    }

    /**
     * The app as [link] opens it: the activity shows what it shows when a link or a tapped notice
     * started it. The activity reads its link only when it starts, so the content is set again here.
     */
    private fun openWith(link: AppLink) {
        compose.activityRule.scenario.onActivity { it.setContent { SplitVibeTheme { AppNavHost(link = link) } } }
        compose.waitForIdle()
    }

    /** The rent, a finished bill split with Minh, kept on the phone as the only bill. */
    private fun keepTheRent() {
        val rent = SpendingFixtures.finished("rent", "2026-11-16", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh"))
        runBlocking { GlobalContext.get().get<BillRepository>().save(rent) }
        awaitState { compose.onAllNodesWithText("Rent").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Fails unless the navigation bar is on the screen, with its four tabs and its button, standing on the tab [selected]. */
    private fun assertNavigationBarOn(selected: String) {
        compose.onAllNodes(hasRole(Role.Tab)).assertCountEquals(TAB_COUNT)
        compose.onNode(tab(selected)).assertIsSelected()
        compose.onNodeWithContentDescription(SPLIT_A_BILL).assertIsDisplayed()
    }

    /** Fails if the navigation bar, or its button in the middle, is anywhere on [screen]. */
    private fun assertNoNavigationBar(screen: String) {
        // A screen may have tabs of its own, such as the list of bills; those of the bar are named after the main screens.
        val barTabs = NAVIGATION_TABS.flatMap { name -> compose.onAllNodes(tab(name, substring = true)).fetchSemanticsNodes() }
        assertTrue("$screen shows tabs of the navigation bar", barTabs.isEmpty())
        val splitButtons = compose.onAllNodesWithContentDescription(SPLIT_A_BILL).fetchSemanticsNodes()
        assertTrue("$screen shows the button that starts a split", splitButtons.isEmpty())
    }

    /**
     * Fails unless [text] is on the screen and drawn there: the place it takes holds more than one
     * colour. A screen left blank by a change that broke off would hold the colour of the page only.
     */
    private fun assertDrawn(text: String) {
        val bounds = compose.onNodeWithText(text).assertIsDisplayed().getBoundsInRoot()
        val picture = compose.onRoot().captureToImage().toPixelMap()
        val colours = with(compose.density) {
            val xs = bounds.left.roundToPx().coerceAtLeast(0) until bounds.right.roundToPx().coerceAtMost(picture.width)
            val ys = bounds.top.roundToPx().coerceAtLeast(0) until bounds.bottom.roundToPx().coerceAtMost(picture.height)
            xs.flatMap { x -> ys.map { y -> picture[x, y] } }.toSet()
        }
        assertTrue("$text is on the screen but not drawn", colours.size > 1)
    }

    /**
     * Waits until [text] is on the screen and the change of screens that brought it there is over, as
     * when a bill opens once it is read from the database, away from the main thread.
     */
    private fun awaitShown(text: String) {
        awaitState { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    /** Waits for work that finishes on a background thread and reports back on the main looper. */
    private fun awaitState(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}

/** The tabs of the navigation bar other than Home, each with the title of the screen it opens. */
private val TAB_TITLES = listOf("Groups" to "Groups", "Requests" to "Requests", "Insights" to "Insights")
private const val TAB_COUNT = 4

/** Every tab of the navigation bar, by the name a screen reader reads, which may end in a count. */
private val NAVIGATION_TABS = listOf("Home") + TAB_TITLES.map { (name, _) -> name }

/** What the round button in the middle of the navigation bar says to a screen reader; it shows no text. */
private const val SPLIT_A_BILL = "Split a bill"

/** What the Recurring tab says above its recurring bills, once it has some. */
private const val RECURRING_INTRO = "On each day a recurring bill comes back, a draft of it is made for you to check and finish."

/** The token of an invite or request link, of the shape the app takes. */
private const val TOKEN = "AbCdEfGhIjKlMnOpQrStUv"

private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

/**
 * The tab of the navigation bar called [name], or with [substring] one whose name holds it, such as
 * "Requests, 2", as opposed to the title of the screen it opens.
 */
private fun tab(name: String, substring: Boolean = false) = hasContentDescription(name, substring = substring) and hasRole(Role.Tab)

/** Frames halfway through the fade from one screen to the next, which takes 400ms at 16ms a frame. */
private const val HALFWAY_FRAMES = 12

/** Frames of the whole fade from one screen to the next. */
private const val FADE_FRAMES = 25

/** A few frames, enough for a change of screens to have started. */
private const val FEW_FRAMES = 3
private const val TIMEOUT_MS = 60_000L
