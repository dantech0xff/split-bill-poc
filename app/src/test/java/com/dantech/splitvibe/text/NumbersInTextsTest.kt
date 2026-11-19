package com.dantech.splitvibe.text

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.R
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.billing.FakeBilling
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.ui.history.HistoryScreen
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.paywall.ProActions
import com.dantech.splitvibe.ui.paywall.ProScreen
import com.dantech.splitvibe.ui.paywall.ProUiState
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.AccountUiState
import com.dantech.splitvibe.ui.settings.SettingsScreen
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The English texts write their numbers one way whatever the phone is set to: with the digits 0 to
 * 9 and "." before decimals, as the counts, the calls left and the ordinals of the texts always
 * were. Phones set to Persian or Arabic write numbers with digits of their own, which a count, an
 * amount or a date must not take there.
 */
@RunWith(AndroidJUnit4::class)
class NumbersInTextsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val before = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(before)

    @Test
    @Config(sdk = [34], qualifiers = "fa")
    fun `counts, money and dates are written in English on a phone set to Persian`() = everyNumberInEnglish()

    @Test
    @Config(sdk = [34], qualifiers = "ar")
    fun `counts, money and dates are written in English on a phone set to Arabic`() = everyNumberInEnglish()

    @Test
    @Config(sdk = [34], qualifiers = "fa")
    fun `the Pro screen, Settings, home and the bills on a phone set to Persian show no other digits`() = screensInEnglish()

    @Test
    @Config(sdk = [34], qualifiers = "ar")
    fun `the Pro screen, Settings, home and the bills on a phone set to Arabic show no other digits`() = screensInEnglish()

    @Test
    fun `a text with a count is read only through UiText, which writes it in English`() {
        val counted = countedResources()
        // A wrong working directory, or a pattern that no longer matches, would pass without looking at anything.
        assertTrue("only ${counted.size} texts with a count found", counted.size > 30)
        val files = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("only ${files.size} source files found", files.size > 20)

        val elsewhere = files.filter { it.name != "UiText.kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val readsCount = PLURALS_READ.containsMatchIn(line) ||
                    STRING_READ.findAll(line).any { it.groupValues[1] in counted }
                if (readsCount) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }

        assertEquals("Read these through UiText:\n${elsewhere.joinToString("\n")}", emptyList<String>(), elsewhere)
    }

    private fun everyNumberInEnglish() {
        likeThePhone()
        val resources = context.resources

        assertEquals("300 AI calls a month", plural(R.plurals.pro_calls_a_month, CallsLeft.PRO_MONTHLY).resolve(resources))
        assertEquals(
            "Pro gives you 300 AI calls a month instead of 10, for reading bills and changing splits by a sentence.",
            plural(R.plurals.pro_benefit, CallsLeft.PRO_MONTHLY, CallsLeft.PRO_MONTHLY, CallsLeft.FREE_MONTHLY).resolve(resources),
        )
        assertEquals("Try again in about 40 minutes.", plural(R.plurals.wait_minutes, 40).resolve(resources))
        assertTrue(text(R.string.pro_not_counted_yet, CallsLeft.PRO_MONTHLY).resolve(resources).contains("Pro's 300 a month"))
        assertEquals("₫250,993", BillCurrency("VND", 0).format(250_993))
        assertEquals("$12.50", BillCurrency("USD", 2).format(1_250))
        assertEquals("Nov 19, 2026", dateText(LocalDate.of(2026, 11, 19)))
    }

    private fun screensInEnglish() {
        likeThePhone()
        val bills = SpendingFixtures.year.take(2).map { saved ->
            BillListing(
                id = saved.id,
                status = saved.status.name,
                updatedAt = 0,
                merchant = saved.bill.merchant,
                date = saved.bill.date,
                currency = saved.bill.currency.code,
                fractionDigits = saved.bill.currency.fractionDigits,
                totalMinor = saved.bill.totalMinor,
                people = "You, Lan",
                category = saved.bill.category.wireValue,
            )
        }
        val month = CallsLeft(BigDecimal(10), BigDecimal(3), BigDecimal(7), "2026-11", resetsAt = 1_796_083_200_000, plan = Plan.FREE)
        val screens = listOf<@Composable () -> Unit>(
            {
                ProScreen(
                    state = ProUiState(sold = true, signedIn = true, callsLeft = month, plans = listOf(FakeBilling.MONTHLY)),
                    actions = ProActions({}, {}, {}, {}, {}, {}, {}, {}),
                )
            },
            {
                SettingsScreen(
                    settings = AppSettings(localCurrency = "VND"),
                    account = Account("install", Session("session", Long.MAX_VALUE), month, AiStatus.AVAILABLE, "minh@example.com"),
                    accountUi = AccountUiState(),
                    canSignIn = true,
                    actions = AccountActions(onSignIn = {}, onSignOut = {}, onDeleteAccount = {}, onOpenPro = {}),
                    onSave = { _, _ -> },
                    onOpenPaymentMethods = {},
                    onBack = {},
                    onAllowSpeechService = {},
                )
            },
            { HomeScreen(bills = bills, onSplitBill = {}, onOpenBill = {}, onSeeAllBills = {}, onOpenSettings = {}) },
            { HistoryScreen(listings = bills, query = "", onQueryChanged = {}, onOpen = {}, onDelete = {}, onBack = {}) },
        )
        var screen by mutableIntStateOf(0)
        compose.setContent { SplitVibeTheme { screens[screen]() } }

        val shown = screens.indices.flatMap { index ->
            screen = index
            compose.waitForIdle()
            textsIn(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        }

        assertTrue("the counts are there to look at: $shown", shown.any { "300 AI calls a month" in it })
        assertTrue(shown.any { "7 of 10" in it })
        val foreign = shown.filter { text -> text.any { it.isDigit() && it !in '0'..'9' } }
        assertEquals("Texts with the phone's digits", emptyList<String>(), foreign)
    }

    /** The phone's language as Android sets it up, also as the default locale, which money and dates used to follow. */
    private fun likeThePhone() {
        val phone = context.resources.configuration.locales[0]
        Locale.setDefault(phone)
        assertNotEquals("the phone's own digits, which the texts must not take", "300", String.format(phone, "%d", 300))
    }

    /** Every text of [node] and the nodes below it. */
    private fun textsIn(node: SemanticsNode): List<String> = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        listOfNotNull(node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")) +
        node.children.flatMap(::textsIn)

    /** The names of the strings and plurals in the app's English resources that hold a count. */
    private fun countedResources(): Set<String> {
        val xml = File("src/main/res/values/strings.xml").readText()
        return RESOURCE.findAll(xml).filter { COUNT.containsMatchIn(it.groupValues[3]) }.map { it.groupValues[2] }.toSet()
    }

    private companion object {
        val RESOURCE = Regex("""<(string|plurals) name="([^"]+)"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
        val COUNT = Regex("""%(\d+\$)?d""")

        /** A plural read straight from the resources, such as with pluralStringResource or getQuantityString. */
        val PLURALS_READ = Regex("""pluralStringResource\(|getQuantityString\(""")

        /** A string read straight from the resources with its arguments. */
        val STRING_READ = Regex("""(?:stringResource|getString)\(\s*R\.string\.(\w+)""")
    }
}
