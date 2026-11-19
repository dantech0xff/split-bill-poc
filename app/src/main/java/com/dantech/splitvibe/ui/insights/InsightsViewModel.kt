package com.dantech.splitvibe.ui.insights

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.dayOf
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.InsightBill
import com.dantech.splitvibe.domain.InsightGroup
import com.dantech.splitvibe.domain.InsightShare
import com.dantech.splitvibe.domain.Insights
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.NameMatcher
import com.dantech.splitvibe.domain.PersonSpending
import com.dantech.splitvibe.export.ExportContent
import com.dantech.splitvibe.export.ExportFile
import com.dantech.splitvibe.export.ExportFormat
import com.dantech.splitvibe.export.Exporter
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.text
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which finished bills the insights add up, by the date on each bill. */
enum class InsightsPeriod(@param:StringRes val label: Int) {
    THIS_MONTH(R.string.insights_this_month),
    LAST_12_MONTHS(R.string.insights_12_months),
    ALL_TIME(R.string.insights_all_time),
    ;

    /** The first and the last day of the period [today] is in; null for all time. */
    fun days(today: LocalDate): ClosedRange<LocalDate>? {
        val month = YearMonth.from(today)
        return when (this) {
            THIS_MONTH -> month.atDay(1)..month.atEndOfMonth()
            LAST_12_MONTHS -> month.minusMonths(MONTHS_BEFORE).atDay(1)..month.atEndOfMonth()
            ALL_TIME -> null
        }
    }

    private companion object {
        /** The months before this one that the last 12 months take in. */
        const val MONTHS_BEFORE = 11L
    }
}

/** What the insights screen shows: the finished bills of a period, added up in the user's local currency. */
data class InsightsState(
    val period: InsightsPeriod,
    /** What the period is called, such as "November 2026". */
    val periodName: UiText,
    /** A short form of [periodName] for file names, such as "2026-11". */
    val periodKey: String,
    val insights: Insights,
    /** The finished bills of the period, by the day each counts on, the latest first. */
    val bills: List<SavedBill>,
    /** What the totals leave out and why. */
    val notes: List<UiText>,
    /** Whether there is a finished bill at all, in this period or another. */
    val anyFinished: Boolean,
)

/** A message about the last export: that it was saved, or why it was not. */
data class ExportMessage(val text: UiText, val isError: Boolean)

/** Where exporting is: files being written, a share sheet to hand over, or the outcome of the last export. */
data class ExportStatus(val working: Boolean = false, val share: Intent? = null, val message: ExportMessage? = null)

/**
 * The user's finished bills added up by month, person, group and category for the period they
 * choose, which comes back after the system ended the app, and the export of that period's bills.
 * [clock] tells the day, in the zone the phone is in.
 */
class InsightsViewModel(
    bills: BillRepository,
    settings: StateFlow<AppSettings>,
    private val exporter: Exporter,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    /** Null until the bills have been read. */
    val state: StateFlow<InsightsState?> = combine(
        bills.finished(),
        settings.map { it.localCurrency }.distinctUntilChanged(),
        savedState.getStateFlow(KEY_PERIOD, InsightsPeriod.THIS_MONTH.name),
    ) { finished, localCurrency, period ->
        val chosen = InsightsPeriod.entries.firstOrNull { it.name == period } ?: InsightsPeriod.THIS_MONTH
        insightsOf(finished, localCurrency, chosen, clock)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    private val exportStatus = MutableStateFlow(ExportStatus())
    val export: StateFlow<ExportStatus> = exportStatus.asStateFlow()

    fun choose(period: InsightsPeriod) {
        savedState[KEY_PERIOD] = period.name
    }

    /** Gets the files of [format] ready for the share sheet, which [export] then carries. */
    fun share(format: ExportFormat) = exportAs(format, failure = SHARE_FAILED) { files ->
        val sheet = exporter.shareIntent(files, title = R.string.insights_send_title)
        exportStatus.update { it.copy(share = sheet) }
    }

    /** Writes the three CSV files into [folder], a folder the user picked. */
    fun saveToFolder(folder: Uri) = exportAs(ExportFormat.CSV, failure = SAVE_FAILED) { files ->
        exporter.saveToFolder(folder, files)
        tell(text(R.string.insights_saved, andList(files.map { UiText.Raw(it.name) })))
    }

    /** Writes the PDF summary to [document], a file the user made. */
    fun saveTo(document: Uri) = exportAs(ExportFormat.PDF, failure = SAVE_FAILED) { files ->
        exporter.saveTo(document, files.single())
        tell(text(R.string.insights_saved_summary))
    }

    /** The share sheet was handed over. */
    fun shared() = exportStatus.update { it.copy(share = null) }

    /** The phone has no file picker to save with. */
    fun pickerMissing() = tell(text(R.string.insights_no_picker), isError = true)

    fun dismissMessage() = exportStatus.update { it.copy(message = null) }

    /**
     * Writes the files of [format] for the period on screen and hands them to [deliver]; says
     * [failure] when that fails, and says so when the period has no bills. A place the user picked
     * is never dropped: after the system ended the app behind the file picker, the answer comes
     * before the bills are read again, and the export waits for them.
     */
    private fun exportAs(format: ExportFormat, failure: UiText, deliver: suspend (List<ExportFile>) -> Unit) {
        if (exportStatus.value.working) return
        exportStatus.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            try {
                val shown = state.filterNotNull().first()
                if (shown.bills.isEmpty()) {
                    tell(NOTHING_TO_EXPORT, isError = true)
                } else {
                    deliver(exporter.files(contentOf(shown, exporter::text), format))
                }
            } catch (e: IOException) {
                tell(failure, isError = true)
            } finally {
                exportStatus.update { it.copy(working = false) }
            }
        }
    }

    private fun tell(said: UiText, isError: Boolean = false) = exportStatus.update { it.copy(message = ExportMessage(said, isError)) }

    private companion object {
        const val KEY_PERIOD = "period"
        const val STOP_AFTER_MS = 5_000L
        val SHARE_FAILED = text(R.string.insights_share_failed)
        val SAVE_FAILED = text(R.string.insights_save_failed)
        val NOTHING_TO_EXPORT = text(R.string.insights_nothing_to_export)
    }
}

/**
 * What the files of [state] hold: its bills by the day each counts on, the latest first, and what
 * they add up to, with its words as [read] gives them, such as [Exporter.text].
 */
internal fun contentOf(state: InsightsState, read: (UiText) -> String) = ExportContent(
    period = read(state.periodName),
    periodKey = state.periodKey,
    bills = state.bills,
    insights = state.insights,
    notes = state.notes.map(read),
)

/**
 * The insights of the [finished] bills, the latest saved first, for [period] as it is on the day
 * [clock] tells, in the currency [localCurrency] names. A bill counts on its own date, or on the day
 * it was saved when it has none that can be read, in the zone [clock] is in.
 */
internal fun insightsOf(finished: List<SavedBill>, localCurrency: String, period: InsightsPeriod, clock: Clock): InsightsState {
    val local = BillCurrency.resolve(localCurrency, null, emptyList())
    val today = LocalDate.now(clock)
    val days = period.days(today)
    // The sort keeps the latest saved first among bills of the same day.
    val inPeriod = finished.map { it to insightBillOf(it, local.code, clock.zone) }
        .filter { (_, counted) -> days == null || counted.day?.let { it in days } == true }
        .sortedByDescending { (_, counted) -> counted.day }
    val insights = Insights.of(inPeriod.map { (_, counted) -> counted }, local)
    val month = YearMonth.from(today)
    return InsightsState(
        period = period,
        periodName = when (period) {
            InsightsPeriod.THIS_MONTH -> UiText.Raw(Exporter.monthName(month))

            InsightsPeriod.LAST_12_MONTHS -> {
                text(R.string.insights_months_to, Exporter.monthName(month.minusMonths(11)), Exporter.monthName(month))
            }

            InsightsPeriod.ALL_TIME -> text(R.string.insights_all_time)
        },
        periodKey = when (period) {
            InsightsPeriod.THIS_MONTH -> "$month"
            InsightsPeriod.LAST_12_MONTHS -> "${month.minusMonths(11)}-to-$month"
            InsightsPeriod.ALL_TIME -> "all-time"
        },
        insights = insights,
        bills = inPeriod.map { (saved, _) -> saved },
        notes = notesOf(insights, bySavedDay = inPeriod.count { (saved, _) -> dayOf(saved.bill.date) == null }),
        anyFinished = finished.isNotEmpty(),
    )
}

/**
 * [saved] as the statistics count it: the day it counts on, its category, group, currency and total,
 * each participant's part under a key that is the same for the same person on every bill, the
 * user's own part, and its rate when that converts into [localCurrency]. Its day is its date, or, when
 * that is no day that can be read, the day it was saved, in [zone].
 *
 * Everyone but the user is told apart by name, without case, accents or punctuation, as the phone
 * tells the people it remembers apart. So is someone forgotten and added again, who is remembered
 * under a new id, and someone of the same name who joined a bill from its link. The user, and
 * someone without a name, have none here: the screen names them in its language ([personName]).
 */
internal fun insightBillOf(saved: SavedBill, localCurrency: String, zone: ZoneId): InsightBill {
    val bill = saved.bill
    return InsightBill(
        day = dayOf(bill.date) ?: Instant.ofEpochMilli(saved.createdAt).atZone(zone).toLocalDate(),
        category = bill.category,
        group = saved.group?.let { InsightGroup(it.groupId, it.groupName) },
        currency = bill.currency,
        totalMinor = bill.totalMinor,
        shares = saved.shares.map { share ->
            val name = saved.plan.participant(share.personId)?.name?.trim().orEmpty()
            val key = NameMatcher.normalize(name).takeIf { it.isNotEmpty() }?.let { "name:$it" }
            if (share.personId == ME_ID) {
                InsightShare(ME_ID, "", share.totalMinor)
            } else {
                // Someone without a name is someone of this bill alone.
                InsightShare(key ?: "${saved.id}/${share.personId}", name, share.totalMinor)
            }
        },
        myShareMinor = saved.myShareMinor,
        rate = saved.exchangeRate?.takeIf { it.converts(bill, localCurrency) },
    )
}

/** What the totals by person call [person]: "You" for the user, "Someone" for someone without a name, or their name. */
internal fun personName(person: PersonSpending): UiText = when {
    person.personKey == ME_ID -> text(R.string.common_you)
    person.name.isBlank() -> text(R.string.common_someone)
    else -> UiText.Raw(person.name)
}

/**
 * What the totals of [insights] leave out and how to count it: bills without a rate, which a rate
 * typed on the bill counts. And how [bySavedDay] bills without a date that can be read are counted.
 */
private fun notesOf(insights: Insights, bySavedDay: Int): List<UiText> = buildList {
    val withoutRate = insights.withoutRate
    val count = withoutRate.values.sum()
    if (count > 0) {
        val local = insights.currency.code
        add(
            if (withoutRate.size > 1) {
                val parts = withoutRate.map { (code, bills) -> plural(R.plurals.insights_bills_in, bills, bills, code) }
                plural(R.plurals.insights_without_rates, count, count, local, andList(parts))
            } else {
                plural(R.plurals.insights_without_rate, count, count, withoutRate.keys.single(), local)
            },
        )
    }
    if (bySavedDay > 0) add(plural(R.plurals.insights_by_saved_day, bySavedDay))
}

/** [items] as a list in a sentence, such as "A, B and C". */
private fun andList(items: List<UiText>): UiText = if (items.size < 2) {
    items.singleOrNull() ?: UiText.Raw("")
} else {
    text(R.string.insights_and_list, UiText.Joined(items.dropLast(1), ", "), items.last())
}
