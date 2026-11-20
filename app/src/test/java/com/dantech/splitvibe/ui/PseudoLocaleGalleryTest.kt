package com.dantech.splitvibe.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.HomeFixtures
import com.dantech.splitvibe.R
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.billing.FakeBilling
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import com.dantech.splitvibe.ui.common.LegalLinks
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.home.HomeSummary
import com.dantech.splitvibe.ui.paywall.ProActions
import com.dantech.splitvibe.ui.paywall.ProScreen
import com.dantech.splitvibe.ui.paywall.ProUiState
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.AccountUiState
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.dantech.splitvibe.ui.split.SplitScreen
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.math.BigDecimal
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws four main screens in the pseudolocale en-XA, which debug builds carry: every text of the
 * resources longer and accented, in brackets. Text that is cut off, overlaps or is not in the
 * resources shows in these pictures, which are compared with the reference images like the others.
 * The split's result is the sample bill as the recorded answer splits it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PSEUDO_PHONE)
class PseudoLocaleGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `the resources are read in the pseudolocale`() {
        assertNotEquals("Settings", context.getString(R.string.settings_title))
    }

    /** With what friends owe, what the user owes, the tiles of the month and of a group, and a draft among the bills. */
    @Test
    fun `home with bills in en-XA`() = shot("50-home-en-xa") {
        HomeScreen(
            summary = HomeSummary.of(HomeFixtures.owed, BILLS, HomeFixtures.month, localCurrency = "VND"),
            group = HomeFixtures.group,
            bills = BILLS,
            onOpenBill = {},
            onSeeAllBills = {},
            onOpenSettings = {},
            onOpenRequests = {},
            onOpenInsights = {},
            onOpenGroup = {},
            onSplitBill = {},
            myName = HomeFixtures.MY_NAME,
            today = HomeFixtures.today,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PSEUDO_TALL)
    fun `the result of a split in en-XA`() {
        server.start()
        server.enqueue(Fixtures.proxyAnswer())
        val proxy = TestProxy(server, context)
        val viewModel = testSplitViewModel(context, storage, proxy.account.account, recognizer = RECORDED, analyzer = proxy.analyzer)
        viewModel.onSamplePicked()
        await { viewModel.state.value.let { it.photo != null && !it.isBusy } }
        viewModel.analyze(defaultInstruction = INSTRUCTION)
        await { viewModel.state.value.let { (it.result != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)

        shot("51-result-en-xa") {
            SplitScreen(
                viewModel = viewModel,
                aiStatus = AiStatus.AVAILABLE,
                newCameraTarget = { Uri.EMPTY },
                scanIntent = { null },
                onExit = {},
                onOpenSettings = {},
                onOpenPro = {},
            )
        }
    }

    /** The narrowest fields of the app: an item's quantity, price each and amount side by side, whose labels end in "…" when long. */
    @Test
    @Config(sdk = [34], qualifiers = PSEUDO_TALL)
    fun `the bill editor in en-XA`() {
        server.start()
        server.enqueue(Fixtures.proxyAnswer())
        val proxy = TestProxy(server, context)
        val viewModel = testSplitViewModel(context, storage, proxy.account.account, recognizer = RECORDED, analyzer = proxy.analyzer)
        viewModel.onSamplePicked()
        await { viewModel.state.value.let { it.photo != null && !it.isBusy } }
        viewModel.analyze(defaultInstruction = INSTRUCTION)
        await { viewModel.state.value.let { (it.result != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)
        viewModel.startEditing()

        shot("54-edit-bill-en-xa") {
            SplitScreen(
                viewModel = viewModel,
                aiStatus = AiStatus.AVAILABLE,
                newCameraTarget = { Uri.EMPTY },
                scanIntent = { null },
                onExit = {},
                onOpenSettings = {},
                onOpenPro = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = PSEUDO_TALL)
    fun `Settings of a signed-in account in en-XA`() = shot("52-settings-en-xa") {
        SettingsScreen(
            settings = AppSettings(localCurrency = "VND", myName = "Minh"),
            account = Account(
                installId = "gallery-install",
                session = Session("gallery-session", expiresAt = Long.MAX_VALUE),
                callsLeft = MONTH,
                ai = AiStatus.AVAILABLE,
                signedInAs = "minh.tran@example.com",
            ),
            accountUi = AccountUiState(),
            canSignIn = true,
            actions = AccountActions(onSignIn = {}, onSignOut = {}, onDeleteAccount = {}, onOpenPro = {}),
            onSave = { _, _ -> },
            onOpenPaymentMethods = {},
            onBack = {},
            legalLinks = LEGAL_LINKS,
            onAllowSpeechService = {},
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PSEUDO_TALL)
    fun `the Pro screen in en-XA`() = shot("53-pro-en-xa") {
        ProScreen(
            state = ProUiState(sold = true, signedIn = true, callsLeft = MONTH, plans = listOf(FakeBilling.MONTHLY, FakeBilling.YEARLY)),
            actions = ProActions(
                onSignIn = {},
                onSelect = {},
                onBuy = {},
                onSwitch = {},
                onRestore = {},
                onRetry = {},
                onDismissMessage = {},
                onBack = {},
            ),
            legalLinks = LEGAL_LINKS,
        )
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SplitVibeTheme {
                // Rippling is left out, so the pictures do not depend on where a test last touched.
                CompositionLocalProvider(LocalRippleConfiguration provides null) { content() }
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }

    /** Waits for work that finishes on another thread, whose result reaches the main thread only when its looper runs. */
    private fun await(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private companion object {
        const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
        const val TIMEOUT_MS = 60_000L

        val LEGAL_LINKS = LegalLinks.of("https://split-vibe-proxy.example.workers.dev")

        /** 1 December 2026, in UTC, when the month's calls come back. */
        val MONTH = CallsLeft(BigDecimal(10), BigDecimal(3), BigDecimal(7), "2026-11", resetsAt = 1_796_083_200_000, plan = Plan.FREE)

        val RECORDED = object : BillTextRecognizer {
            override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
        }

        /** The latest bills of the year the insights draw, and a draft. */
        val BILLS = SpendingFixtures.year.take(2).map { saved ->
            BillListing(
                id = saved.id,
                status = saved.status.name,
                updatedAt = 0,
                merchant = saved.bill.merchant,
                date = saved.bill.date,
                currency = saved.bill.currency.code,
                fractionDigits = saved.bill.currency.fractionDigits,
                totalMinor = saved.bill.totalMinor,
                people = (listOf("You") + saved.plan.participants.drop(1).map { it.name }).joinToString(", "),
                category = saved.bill.category.wireValue,
            )
        } + BillListing("d1", BillStatus.DRAFT.name, 0, "Bia Hơi", "2026-11-14", "VND", 0, 240_000, "You, Lan", "food_and_drink")
    }
}

/** A phone, in the pseudolocale with longer text. */
private const val PSEUDO_PHONE = "en-rXA-w412dp-h915dp-xhdpi"

/** Tall enough to show a whole screen at once. */
private const val PSEUDO_TALL = "en-rXA-w412dp-h1600dp-xhdpi"
