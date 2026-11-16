package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/** One saved bill as the statistics count it. The app builds it from a saved bill and the shares worked out for it. */
data class InsightBill(
    /** The day of the bill as yyyy-mm-dd; null, or anything else, when it is not known. */
    val date: String?,
    val category: BillCategory,
    /** The group the bill is in; null for a bill in no group. */
    val group: InsightGroup?,
    val currency: BillCurrency,
    val totalMinor: Long,
    /** Each participant's part of the bill, in minor units of [currency]. */
    val shares: List<InsightShare>,
    /** The user's own part of the bill, in minor units of [currency]. */
    val myShareMinor: Long,
    /** What one unit of [currency] buys in the local currency; null when it is not known. A bill in the local currency needs none. */
    val rate: ExchangeRate?,
)

/** One participant's part of a bill. [personKey] is the same for the same person on every bill; [name] is what to call them. */
data class InsightShare(val personKey: String, val name: String, val amountMinor: Long)

/** A group a bill is in: [id] tells it apart from every other group, also one of the same name, and [name] is what to call it. */
data class InsightGroup(val id: String, val name: String)

/** What some bills add up to in the local currency: how many there are, their totals, and the user's own part of them. */
data class Spending(val bills: Int, val totalMinor: Long, val myShareMinor: Long)

/**
 * What the bills one person is on add up to in the local currency: how many there are, their
 * totals, the user's part of them and the person's own part of them.
 */
data class PersonSpending(
    val personKey: String,
    val name: String,
    val bills: Int,
    val totalMinor: Long,
    val myShareMinor: Long,
    val shareMinor: Long,
)

/**
 * What the user's bills add up to, by month, by person, by group and by category, in the user's
 * local [currency].
 *
 * Each bill is converted on its own, at its own rate: its total, the user's part and each person's
 * part are each rounded to the nearest minor unit of the local currency, half up as everywhere else
 * the app converts an amount, and only then added up. So a bill's total here is the one the bill
 * shows in the local currency, and its converted parts can differ from it by a unit or two. A bill
 * in another currency without a usable rate is left out of every sum and only counted, by currency,
 * in [withoutRate]. A bill without a usable date is in every sum except those by month, and
 * [undated] adds those bills up on their own.
 */
data class Insights(
    val currency: BillCurrency,
    val overall: Spending,
    /** By calendar month of the bill's date, earliest first. */
    val byMonth: Map<YearMonth, Spending>,
    /** Everyone on the bills, the user too, the largest own part first. */
    val byPerson: List<PersonSpending>,
    /**
     * By group, null for bills in no group, the largest total first and bills in no group last of
     * equals. Groups are told apart by their id, so two groups of the same name are two lines, and
     * one renamed between two bills is one, named as the first of the bills in it names it.
     */
    val byGroup: Map<InsightGroup?, Spending>,
    /** The categories that have bills, the largest total first. */
    val byCategory: Map<BillCategory, Spending>,
    val undated: Spending,
    /** How many bills were left out for want of a usable rate, by the code of their currency. */
    val withoutRate: Map<String, Int>,
) {
    companion object {

        /**
         * The statistics of [bills] in [localCurrency]. A person or a group is named as the first of
         * [bills] that has them names them, so newest first shows the name now in use.
         *
         * @throws ArithmeticException when the amounts are too large to add up, which only amounts
         *   far beyond any real bills can cause.
         */
        fun of(bills: List<InsightBill>, localCurrency: BillCurrency): Insights {
            val counted = mutableListOf<Counted>()
            val withoutRate = LinkedHashMap<String, Int>()
            for (bill in bills) {
                val inLocal = inLocal(bill, localCurrency)
                if (inLocal == null) withoutRate.merge(bill.currency.code, 1, Int::plus) else counted += inLocal
            }
            val dated = counted.mapNotNull { bill -> bill.month?.let { it to bill } }
            // Each group as the first bill in it names it.
            val groups = counted.mapNotNull { it.bill.group }.distinctBy { it.id }.associateBy { it.id }
            return Insights(
                currency = localCurrency,
                overall = spending(counted),
                byMonth = dated.groupBy({ it.first }, { it.second }).toSortedMap().mapValues { spending(it.value) },
                byPerson = people(counted),
                byGroup = sums(counted, nullsLast(compareBy<InsightGroup> { it.name }.thenBy { it.id })) { bill ->
                    bill.bill.group?.let { groups.getValue(it.id) }
                },
                byCategory = sums(counted, naturalOrder()) { it.bill.category },
                undated = spending(counted.filter { it.month == null }),
                withoutRate = withoutRate,
            )
        }

        /** [bills] added up by [key], the largest total first, and equal totals in the order of [ties]. */
        private fun <K> sums(bills: List<Counted>, ties: Comparator<K>, key: (Counted) -> K): Map<K, Spending> =
            bills.groupBy(key).mapValues { spending(it.value) }.entries
                .sortedWith(compareByDescending<Map.Entry<K, Spending>> { it.value.totalMinor }.thenBy(ties) { it.key })
                .associate { it.key to it.value }

        /** [bill] with its amounts in [local]; null without a usable rate to it, or when a mistyped rate puts them out of range. */
        private fun inLocal(bill: InsightBill, local: BillCurrency): Counted? {
            val rate = when {
                bill.currency == local -> null

                // The same currency kept to another number of decimals is the same money.
                bill.currency.code == local.code -> ExchangeRate(local.code, local.code, BigDecimal.ONE)

                else -> bill.rate?.takeIf { it.from == bill.currency.code && it.to == local.code && it.rate.signum() > 0 } ?: return null
            }

            fun convert(minor: Long): Long? {
                if (rate == null) return minor
                val converted = rate.convert(minor, bill.currency, local) ?: return null
                return converted.takeIf { Math.abs(it) <= BillCurrency.MAX_AMOUNT_MINOR }
            }
            return Counted(
                bill = bill,
                month = monthOf(bill.date),
                totalMinor = convert(bill.totalMinor) ?: return null,
                myShareMinor = convert(bill.myShareMinor) ?: return null,
                shares = bill.shares.map { share -> share to (convert(share.amountMinor) ?: return null) },
            )
        }

        private fun monthOf(date: String?): YearMonth? {
            if (date == null) return null
            return try {
                YearMonth.from(LocalDate.parse(date.trim()))
            } catch (e: DateTimeParseException) {
                null
            }
        }

        private fun spending(bills: List<Counted>) = Spending(
            bills = bills.size,
            totalMinor = bills.fold(0L) { sum, bill -> Math.addExact(sum, bill.totalMinor) },
            myShareMinor = bills.fold(0L) { sum, bill -> Math.addExact(sum, bill.myShareMinor) },
        )

        private fun people(bills: List<Counted>): List<PersonSpending> {
            val people = LinkedHashMap<String, PersonSpending>()
            for (bill in bills) {
                // Two participants of one bill who are the same person count it once, with both parts.
                bill.shares.groupBy { (share, _) -> share.personKey }.forEach { (key, parts) ->
                    val before = people[key] ?: PersonSpending(key, parts.first().first.name, 0, 0, 0, 0)
                    people[key] = before.copy(
                        bills = before.bills + 1,
                        totalMinor = Math.addExact(before.totalMinor, bill.totalMinor),
                        myShareMinor = Math.addExact(before.myShareMinor, bill.myShareMinor),
                        shareMinor = parts.fold(before.shareMinor) { sum, (_, amount) -> Math.addExact(sum, amount) },
                    )
                }
            }
            return people.values.sortedWith(compareByDescending<PersonSpending> { it.shareMinor }.thenBy { it.name })
        }
    }

    /** A bill with its amounts in the local currency, and the calendar month of its date, if it has a usable one. */
    private class Counted(
        val bill: InsightBill,
        val month: YearMonth?,
        val totalMinor: Long,
        val myShareMinor: Long,
        val shares: List<Pair<InsightShare, Long>>,
    )
}
