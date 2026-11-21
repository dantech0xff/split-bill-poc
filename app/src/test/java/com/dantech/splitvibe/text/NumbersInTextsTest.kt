package com.dantech.splitvibe.text

import android.content.Context
import android.content.res.Resources
import android.icu.text.PluralRules
import android.util.TypedValue
import android.view.View
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
import com.dantech.splitvibe.HomeFixtures
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
import com.dantech.splitvibe.domain.TEXT_LOCALE
import com.dantech.splitvibe.ui.history.HistoryScreen
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.home.HomeSummary
import com.dantech.splitvibe.ui.paywall.ProActions
import com.dantech.splitvibe.ui.paywall.ProScreen
import com.dantech.splitvibe.ui.paywall.ProUiState
import com.dantech.splitvibe.ui.recurring.RecurringActions
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.AccountUiState
import com.dantech.splitvibe.ui.settings.SettingsScreen
import java.io.File
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
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
 * The English texts write their counts one way whatever the phone is set to, as the counts, the
 * plurals and the ordinals of the texts always were. Amounts and dates follow the phone's region, in
 * its marks and in its order, but with the digits 0 to 9 everywhere: phones set to Persian or Arabic
 * write numbers with digits of their own, which no count, amount or date may take there.
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
    fun `counts stay in English, and money and dates take the region's marks and order with the digits 0 to 9, in Persian`() =
        countsInEnglishMoneyAndDatesOfTheRegion(writtenDay = "19 Nov 2026")

    @Test
    @Config(sdk = [34], qualifiers = "ar")
    fun `counts stay in English, and money and dates take the region's marks and order with the digits 0 to 9, in Arabic`() =
        countsInEnglishMoneyAndDatesOfTheRegion(writtenDay = "19 Nov 2026")

    @Test
    @Config(sdk = [34], qualifiers = "fa")
    fun `the Pro screen, Settings, home and the bills on a phone set to Persian show no other digits`() = screensInEnglish()

    @Test
    @Config(sdk = [34], qualifiers = "ar")
    fun `the Pro screen, Settings, home and the bills on a phone set to Arabic show no other digits`() = screensInEnglish()

    /** Vietnamese has one form for every count, which in the English texts would read "1 bills". */
    @Test
    @Config(sdk = [34], qualifiers = "vi-rVN")
    fun `on a phone set to Vietnamese a count of one takes the singular and two the plural, as in English`() {
        val resources = context.resources

        assertEquals("Your share of 1 bill", plural(R.plurals.home_your_share_of, 1).resolve(resources))
        assertEquals("Your share of 2 bills", plural(R.plurals.home_your_share_of, 2).resolve(resources))
        // Only the form follows English: an amount in the text stays as a phone in Vietnam writes it.
        val amount = BillCurrency("VND", 0).format(651_671)
        assertEquals("651.671 ₫", amount)
        assertEquals("Request $amount from 1 person", plural(R.plurals.split_request_from, 1, amount, 1).resolve(resources))
        assertEquals("Request $amount from 2 people", plural(R.plurals.split_request_from, 2, amount, 2).resolve(resources))
    }

    /** French gives the singular to 0 as well as to 1, which in the English texts would read "0 bill". */
    @Test
    @Config(sdk = [34], qualifiers = "fr")
    fun `on a phone set to French a count of zero takes the plural, as in English`() = assertZeroTakesThePlural()

    /** Persian gives the singular to 0 as well as to 1, which in the English texts would read "0 bill". */
    @Test
    @Config(sdk = [34], qualifiers = "fa")
    fun `on a phone set to Persian a count of zero takes the plural, as in English`() = assertZeroTakesThePlural()

    /** The pseudolocale ar-XB of a debug build is the English texts in another dress, and its plurals keep that dress. */
    @Test
    @Config(sdk = [34], qualifiers = "ar-rXB")
    fun `on a phone set to the pseudolocale ar-XB the plurals stay in its dress`() {
        val resources = context.resources
        for ((count, english) in listOf(1 to "Your share of 1 bill", 2 to "Your share of 2 bills")) {
            val dressed = String.format(TEXT_LOCALE, resources.getQuantityString(R.plurals.home_your_share_of, count), count)
            assertNotEquals("the pseudolocale's own form for $count", english, dressed)
            assertEquals(dressed, plural(R.plurals.home_your_share_of, count).resolve(resources))
        }
    }

    /**
     * Reading a plural leaves the phone's own resources as they were: the words of Android and of the
     * libraries the app uses stay in the phone's language, such as Copy in the menu over a selected text
     * and the "selected" a screen reader says of a tab.
     */
    @Test
    @Config(sdk = [34], qualifiers = "vi-rVN")
    fun `a plural read on a phone set to Vietnamese leaves the phone's own words in Vietnamese`() =
        assertAPluralLeavesThePhoneAsItWas(rightToLeft = false)

    /** As on a phone set to Vietnamese, and the phone's resources go on reading the files they have for right to left. */
    @Test
    @Config(sdk = [34], qualifiers = "ar")
    fun `a plural read on a phone set to Arabic leaves the phone's own words in Arabic, right to left`() =
        assertAPluralLeavesThePhoneAsItWas(rightToLeft = true)

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

    /** [writtenDay] is how the phone's region writes 19 November 2026 with English month names. */
    private fun countsInEnglishMoneyAndDatesOfTheRegion(writtenDay: String) {
        val phone = likeThePhone()
        val resources = context.resources

        assertEquals("300 AI calls a month", plural(R.plurals.pro_calls_a_month, CallsLeft.PRO_MONTHLY).resolve(resources))
        assertEquals(
            "Pro gives you 300 AI calls a month instead of 10, for reading bills and changing splits by a sentence.",
            plural(R.plurals.pro_benefit, CallsLeft.PRO_MONTHLY, CallsLeft.PRO_MONTHLY, CallsLeft.FREE_MONTHLY).resolve(resources),
        )
        assertEquals("Try again in about 40 minutes.", plural(R.plurals.wait_minutes, 40).resolve(resources))
        assertTrue(text(R.string.pro_not_counted_yet, CallsLeft.PRO_MONTHLY).resolve(resources).contains("Pro's 300 a month"))

        // What the phone's region writes, told from the phone's own locale with the digits 0 to 9.
        val marks = DecimalFormatSymbols.getInstance(Locale.Builder().setLocale(phone).setUnicodeLocaleKeyword("nu", "latn").build())
        val dong = BillCurrency("VND", 0).format(250_993)
        val dollars = BillCurrency("USD", 2).format(1_250)
        val day = dateText(LocalDate.of(2026, 11, 19))
        for (written in listOf(dong, dollars, day)) {
            assertTrue("only the digits 0 to 9: $written", written.all { !it.isDigit() || it in '0'..'9' })
        }
        assertTrue(dong, "₫" in dong && "250${marks.groupingSeparator}993" in dong)
        assertTrue(dollars, "$" in dollars && "12${marks.decimalSeparator}50" in dollars)
        assertEquals(writtenDay, day)
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
            {
                // With every amount and count home shows: what friends owe and the user owes, the month and a group.
                HomeScreen(
                    summary = HomeSummary.of(HomeFixtures.owed, bills, HomeFixtures.month, localCurrency = "VND"),
                    group = HomeFixtures.group,
                    bills = bills,
                    onOpenBill = {},
                    onSeeAllBills = {},
                    onOpenSettings = {},
                    onOpenRequests = {},
                    onOpenInsights = {},
                    onOpenGroup = {},
                    onSplitBill = {},
                    today = HomeFixtures.today,
                )
            },
            {
                HistoryScreen(
                    listings = bills,
                    query = "",
                    onQueryChanged = {},
                    onOpen = {},
                    onDelete = {},
                    onBack = {},
                    recurring = emptyList(),
                    recurringActions = RecurringActions(onPause = {}, onResume = {}, onDelete = {}),
                    today = HomeFixtures.today,
                )
            },
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
        assertTrue(shown.any { "2 requests" in it } && shown.any { "Your share of 6 bills" in it })
        val foreign = shown.filter { text -> text.any { it.isDigit() && it !in '0'..'9' } }
        assertEquals("Texts with the phone's digits", emptyList<String>(), foreign)
    }

    private fun assertZeroTakesThePlural() {
        val resources = context.resources
        // The phone's own rules give the singular to 0: the form a plural read straight from its resources would take.
        assertEquals("one", PluralRules.forLocale(resources.configuration.locales[0]).select(0.0))

        assertEquals("Your share of 0 bills", plural(R.plurals.home_your_share_of, 0).resolve(resources))
        assertEquals("Your share of 1 bill", plural(R.plurals.home_your_share_of, 1).resolve(resources))
        assertEquals("Your share of 2 bills", plural(R.plurals.home_your_share_of, 2).resolve(resources))
    }

    /**
     * Reads a plural through UiText, as the screens do, and fails unless the phone's resources then read
     * as they did before: Android's and Compose's words in the phone's language, the layout direction
     * of their configuration, and Android's own file for [rightToLeft] where it has one.
     */
    private fun assertAPluralLeavesThePhoneAsItWas(rightToLeft: Boolean) {
        val resources = context.resources
        val before = PhoneResources.of(resources)
        // In the phone's language and direction to begin with, so that English or left to right afterwards tells.
        assertTrue("words in English before any plural: ${before.words}", before.words.none { (id, word) -> word == PHONE_WORDS[id] })
        assertEquals(if (rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR, before.direction)
        assertEquals("the file of the animation read: ${before.closingAnimation}", rightToLeft, "-ldrtl" in before.closingAnimation)

        assertEquals("Your share of 2 bills", plural(R.plurals.home_your_share_of, 2).resolve(resources))

        assertEquals(before, PhoneResources.of(resources))
    }

    /**
     * What a phone's resources read: Android's and Compose's [words], the layout [direction] of their
     * configuration, and the file of Android's animation for a screen that closes, which is another file
     * for right to left, so the file says which direction the resources look their values up in.
     */
    private data class PhoneResources(val words: Map<Int, String>, val direction: Int, val closingAnimation: String) {
        companion object {
            fun of(resources: Resources): PhoneResources {
                val animation = Resources.getSystem().getIdentifier("activity_close_enter", "anim", "android")
                assertNotEquals("Android's animation of a closing screen", 0, animation)
                return PhoneResources(
                    words = PHONE_WORDS.keys.associateWith(resources::getString),
                    direction = resources.configuration.layoutDirection,
                    closingAnimation = TypedValue().also { resources.getValue(animation, it, true) }.string.toString(),
                )
            }
        }
    }

    /** The phone's language as Android sets it up, also as the default locale, whose region money and dates follow. */
    private fun likeThePhone(): Locale {
        val phone = context.resources.configuration.locales[0]
        Locale.setDefault(phone)
        assertNotEquals("the phone's own digits, which the texts must not take", "300", String.format(phone, "%d", 300))
        return phone
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

        /** Words that only the phone's own resources hold, Android's and Compose's, with what they say in English. */
        val PHONE_WORDS = mapOf(android.R.string.copy to "Copy", androidx.compose.ui.R.string.selected to "Selected")
    }
}
