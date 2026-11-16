package com.dantech.splitvibe.ui.insights

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.InsightBill
import com.dantech.splitvibe.domain.InsightShare
import com.dantech.splitvibe.domain.Insights
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.NameMatcher
import com.dantech.splitvibe.export.ExportContent
import com.dantech.splitvibe.export.ExportFile
import com.dantech.splitvibe.export.ExportFormat
import com.dantech.splitvibe.export.Exporter
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which finished bills the insights add up, by the date on each bill. */
enum class InsightsPeriod(val label: String) {
    THIS_MONTH("This month"),
    LAST_12_MONTHS("12 months"),
    ALL_TIME("All time"),
    ;

    /** The first and the last day of the period [today] is in; null for all time, which also counts bills without a date. */
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
    val periodName: String,
    /** A short form of [periodName] for file names, such as "2026-11". */
    val periodKey: String,
    val insights: Insights,
    /** The finished bills of the period, the latest saved first. */
    val bills: List<SavedBill>,
    /** What the totals leave out and why. */
    val notes: List<String>,
    /** Whether there is a finished bill at all, in this period or another. */
    val anyFinished: Boolean,
)

/** A message about the last export: that it was saved, or why it was not. */
data class ExportMessage(val text: String, val isError: Boolean)

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
        insightsOf(finished, localCurrency, chosen, LocalDate.now(clock))
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
        val sheet = exporter.shareIntent(files, title = "Send the bills")
        exportStatus.update { it.copy(share = sheet) }
    }

    /** Writes the three CSV files into [folder], a folder the user picked. */
    fun saveToFolder(folder: Uri) = exportAs(ExportFormat.CSV, failure = SAVE_FAILED) { files ->
        exporter.saveToFolder(folder, files)
        tell("Saved ${andList(files.map { it.name })}.")
    }

    /** Writes the PDF summary to [document], a file the user made. */
    fun saveTo(document: Uri) = exportAs(ExportFormat.PDF, failure = SAVE_FAILED) { files ->
        exporter.saveTo(document, files.single())
        tell("Saved the summary.")
    }

    /** The share sheet was handed over. */
    fun shared() = exportStatus.update { it.copy(share = null) }

    /** The phone has no file picker to save with. */
    fun pickerMissing() = tell("This phone has no app to save files with. Share them instead.", isError = true)

    fun dismissMessage() = exportStatus.update { it.copy(message = null) }

    /** Writes the files of [format] for the period on screen and hands them to [deliver]; says [failure] when that fails. */
    private fun exportAs(format: ExportFormat, failure: String, deliver: suspend (List<ExportFile>) -> Unit) {
        val shown = state.value ?: return
        if (shown.bills.isEmpty() || exportStatus.value.working) return
        exportStatus.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            try {
                deliver(exporter.files(contentOf(shown), format))
            } catch (e: IOException) {
                tell(failure, isError = true)
            } finally {
                exportStatus.update { it.copy(working = false) }
            }
        }
    }

    private fun tell(text: String, isError: Boolean = false) = exportStatus.update { it.copy(message = ExportMessage(text, isError)) }

    private companion object {
        const val KEY_PERIOD = "period"
        const val STOP_AFTER_MS = 5_000L
        const val SHARE_FAILED = "The files could not be written. Check the space left on the phone."
        const val SAVE_FAILED = "The files could not be saved there. Pick another place, or share them instead."
    }
}

/** What the files of [state] hold: its bills by the date on each, the latest first, and what they add up to. */
internal fun contentOf(state: InsightsState) = ExportContent(
    period = state.periodName,
    periodKey = state.periodKey,
    // Undated bills last; the sort keeps the latest saved first among bills of the same day.
    bills = state.bills.sortedByDescending { dayOf(it.bill.date) },
    insights = state.insights,
    notes = state.notes,
)

/**
 * The insights of the [finished] bills, the latest saved first, for [period] as it is on [today], in
 * the currency [localCurrency] names. A bill without a usable date is in no period but all time.
 */
internal fun insightsOf(finished: List<SavedBill>, localCurrency: String, period: InsightsPeriod, today: LocalDate): InsightsState {
    val local = BillCurrency.resolve(localCurrency, null, emptyList())
    val days = period.days(today)
    val inPeriod = if (days == null) finished else finished.filter { saved -> dayOf(saved.bill.date)?.let { it in days } == true }
    val undated = if (days == null) 0 else finished.count { dayOf(it.bill.date) == null }
    val insights = Insights.of(inPeriod.map { insightBillOf(it, local.code) }, local)
    val month = YearMonth.from(today)
    return InsightsState(
        period = period,
        periodName = when (period) {
            InsightsPeriod.THIS_MONTH -> Exporter.monthName(month)
            InsightsPeriod.LAST_12_MONTHS -> "${Exporter.monthName(month.minusMonths(11))} to ${Exporter.monthName(month)}"
            InsightsPeriod.ALL_TIME -> "All time"
        },
        periodKey = when (period) {
            InsightsPeriod.THIS_MONTH -> "$month"
            InsightsPeriod.LAST_12_MONTHS -> "${month.minusMonths(11)}-to-$month"
            InsightsPeriod.ALL_TIME -> "all-time"
        },
        insights = insights,
        bills = inPeriod,
        notes = notesOf(insights, undatedLeftOut = undated),
        anyFinished = finished.isNotEmpty(),
    )
}

/**
 * [saved] as the statistics count it: its date, category, group, currency and total, each
 * participant's part under a key that is the same for the same person on every bill, the user's
 * own part, and its rate when that converts into [localCurrency].
 */
internal fun insightBillOf(saved: SavedBill, localCurrency: String): InsightBill {
    val bill = saved.bill
    return InsightBill(
        date = bill.date,
        category = bill.category,
        group = saved.group?.groupName,
        currency = bill.currency,
        totalMinor = bill.totalMinor,
        shares = saved.shares.map { share ->
            val participant = saved.plan.participant(share.personId)
            val name = participant?.name?.trim().orEmpty()
            // Someone the phone does not remember, such as a person added from a bill's link, is told
            // apart by name, as the app does, without case, accents or punctuation.
            val byName = NameMatcher.normalize(name).takeIf { it.isNotEmpty() }?.let { "name:$it" }
            if (share.personId == ME_ID) {
                InsightShare(ME_ID, YOU, share.totalMinor)
            } else {
                InsightShare(participant?.personId ?: byName ?: "${saved.id}/${share.personId}", name.ifEmpty { SOMEONE }, share.totalMinor)
            }
        },
        myShareMinor = saved.shares.firstOrNull { it.personId == ME_ID }?.totalMinor ?: 0,
        rate = saved.exchangeRate?.takeIf { it.converts(bill, localCurrency) },
    )
}

/** What the totals of [insights] leave out and why: bills without a rate, and bills without a date. */
private fun notesOf(insights: Insights, undatedLeftOut: Int): List<String> = buildList {
    val withoutRate = insights.withoutRate
    val count = withoutRate.values.sum()
    if (count > 0) {
        val local = insights.currency.code
        add(
            if (withoutRate.size == 1) {
                val code = withoutRate.keys.single()
                if (count == 1) {
                    "1 bill in $code is left out of the totals, as no rate to $local is saved with it."
                } else {
                    "$count bills in $code are left out of the totals, as no rate to $local is saved with them."
                }
            } else {
                "$count bills are left out of the totals, as no rate to $local is saved with them: " +
                    andList(withoutRate.map { (code, bills) -> "$bills in $code" }) + "."
            },
        )
    }
    val undated = insights.undated.bills
    when {
        undatedLeftOut == 1 -> add("1 bill has no date that can be read, so it is left out of this period. All time counts it.")

        undatedLeftOut > 1 ->
            add("$undatedLeftOut bills have no date that can be read, so they are left out of this period. All time counts them.")

        undated == 1 -> add("1 bill has no date that can be read, so it is in the totals but in no month.")

        undated > 1 -> add("$undated bills have no date that can be read, so they are in the totals but in no month.")
    }
}

/** [items] as a list in a sentence, such as "A, B and C". */
private fun andList(items: List<String>): String =
    if (items.size < 2) items.joinToString() else items.dropLast(1).joinToString() + " and " + items.last()

/** The day [date] names, as yyyy-mm-dd; null when it names none, as the statistics read it. */
private fun dayOf(date: String?): LocalDate? = date?.let {
    try {
        LocalDate.parse(it.trim())
    } catch (e: DateTimeParseException) {
        null
    }
}

private const val YOU = "You"
private const val SOMEONE = "Someone"
