package com.dantech.splitvibe.domain

import com.dantech.splitvibe.domain.SplitEdit.AddCharge
import com.dantech.splitvibe.domain.SplitEdit.AddPerson
import com.dantech.splitvibe.domain.SplitEdit.ItemAmount
import com.dantech.splitvibe.domain.SplitEdit.ItemQuantity
import com.dantech.splitvibe.domain.SplitEdit.ItemSharers
import com.dantech.splitvibe.domain.SplitEdit.ItemShares
import com.dantech.splitvibe.domain.SplitEdit.RemovePerson
import com.dantech.splitvibe.domain.SplitEdit.SetMode
import com.dantech.splitvibe.domain.SplitEdit.SetPayer
import com.dantech.splitvibe.domain.SplitEdit.SetSharing
import com.dantech.splitvibe.domain.SplitEdit.WholeAmounts
import com.dantech.splitvibe.domain.SplitEdit.WholePercent
import com.dantech.splitvibe.domain.SplitEdit.WholeShares
import java.math.BigDecimal
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edit rules hold for any bill, plan and list of edits the AI could propose, not just the
 * examples: 1,500 random bills split in random ways, each given up to 20 random edits, some that can
 * be applied and some that cannot. After every edit, a split without problems adds up to exactly the
 * bill total with no share below zero, and so do the payments; a rejected edit changes nothing; and
 * an edit that names an item, a person or a ref that is not on the bill is rejected with a reason.
 */
class SplitEditPropertyTest {

    private val random = Random(20261116)

    @Test
    fun `any list of edits keeps the shares adding up, and a rejected edit changes nothing`() = repeat(1_500) {
        val bill = randomBill()
        val plan = randomPlan(bill)
        val edits = List(random.nextInt(1, 21)) { randomEdit(bill, plan) }
        val case = "$edits on $bill with $plan"

        val result = SplitEdits.apply(bill, plan, edits)

        assertEquals(case, edits.size, result.applied.size + result.rejected.size)
        assertTrue(case, result.rejected.all { it.reason.isNotBlank() && edits[it.index] == it.edit })
        var before = SplitEditResult(bill, plan, emptyList(), emptyList())
        edits.indices.forEach { index ->
            val after = SplitEdits.apply(bill, plan, edits.take(index + 1))
            val step = "$case, after edit $index"
            if (result.rejected.any { it.index == index }) {
                assertEquals(step, before.bill, after.bill)
                assertEquals(step, before.plan, after.plan)
            }
            assertHoldsTogether(step, after.bill, after.plan)
            before = after
        }
        assertEquals(case, before.bill, result.bill)
        assertEquals(case, before.plan, result.plan)
        val sentences = SplitEdits.describe(edits, bill, plan, "Dana", Locale.US)
        assertEquals(case, edits.size, sentences.size)
        assertTrue(case, sentences.all { it.isNotBlank() && it.first().isUpperCase() })
    }

    @Test
    fun `an edit that names an item, a person or a ref that is not on the bill is rejected and reported`() = repeat(1_500) {
        val bill = randomBill()
        val plan = randomPlan(bill)
        val edits = List(random.nextInt(1, 21)) { randomEdit(bill, plan) }

        val result = SplitEdits.apply(bill, plan, edits)

        edits.forEachIndexed { index, edit ->
            // A ref names someone only once an add_person before the edit has added them.
            val added = edits.take(index).filterIsInstance<AddPerson>().map { it.ref }.toSet()
            val unknown = (namedItems(edit) - bill.items.map { it.id }.toSet()).isNotEmpty() ||
                namedPeople(edit).any { it in NEVER_ON_THE_BILL || (it.startsWith("new") && it !in added) }
            if (unknown) {
                val rejected = result.rejected.firstOrNull { it.index == index }
                assertTrue("$edit at $index of $edits on $bill with $plan", rejected != null && rejected.reason.isNotBlank())
            }
        }
    }

    /** What every bill and plan the edits leave keeps true, problems or not. */
    private fun assertHoldsTogether(case: String, bill: Bill, plan: SplitPlan) {
        val people = plan.participantIds.toSet()
        assertTrue(case, bill.totalMinor in 0..BillCurrency.MAX_AMOUNT_MINOR)
        assertTrue(case, bill.items.all { it.amountMinor >= 0 })
        assertEquals(case, ME_ID, plan.participantIds.first())
        assertEquals(case, plan.participantIds.size, people.size)
        assertTrue(case, plan.payments.all { it.participantId in people })
        assertTrue(case, plan.setup.notSharing.all { it in people })
        assertTrue(case, plan.setup.items.keys.all { id -> bill.items.any { it.id == id } })
        val divisions = listOfNotNull(plan.setup.whole) + plan.setup.items.values
        assertTrue(case, divisions.all { people.containsAll(namedIn(it)) })

        val result = plan.result(bill)
        if (plan.problems(bill).isEmpty()) {
            assertEquals(case, plan.participantIds, result.shares.map { it.personId })
            assertEquals(case, bill.totalMinor, result.totalMinor)
            assertTrue(case, result.shares.all { it.totalMinor >= 0 })
            assertEquals(case, bill.totalMinor, plan.payments.sumOf { it.amountMinor })
        } else {
            assertTrue(case, result.shares.isEmpty() || result.totalMinor == bill.totalMinor)
        }
    }

    private fun namedIn(division: Division): Set<String> = when (division) {
        is Division.Equally -> division.among + division.except
        is Division.ByShares -> division.shares.keys
        is Division.ByPercent -> division.basisPoints.keys
        is Division.ByAmount -> division.amounts.keys
    }

    private fun namedItems(edit: SplitEdit): Set<String> = when (edit) {
        is ItemSharers -> setOf(edit.itemId)
        is ItemShares -> setOf(edit.itemId)
        is ItemAmount -> setOf(edit.itemId)
        is ItemQuantity -> setOf(edit.itemId)
        else -> emptySet()
    }

    private fun namedPeople(edit: SplitEdit): Set<String> = when (edit) {
        is ItemSharers -> edit.participantIds.toSet()
        is ItemShares -> edit.shares.keys
        is WholeShares -> edit.shares.keys
        is WholePercent -> edit.basisPoints.keys
        is WholeAmounts -> edit.amounts.keys
        is RemovePerson -> setOf(edit.participantId)
        is SetSharing -> setOf(edit.participantId)
        is SetPayer -> setOf(edit.participantId)
        else -> emptySet()
    }

    private fun randomBill(): Bill {
        val currency = if (random.nextBoolean()) BillCurrency("USD", 2) else BillCurrency("VND", 0)
        val items = List(random.nextInt(0, 8)) { i ->
            val quantity = BigDecimal(random.nextInt(1, 5))
            val price = if (random.nextBoolean()) random.nextLong(0, 1_000_000) else null
            val amount = price?.let { it * quantity.toLong() } ?: random.nextLong(0, 3_000_000)
            BillItem("i${i + 1}", "Item ${i + 1}", quantity, price, amount)
        }
        val charges = List(random.nextInt(0, 3)) { i ->
            BillCharge("c${i + 1}", "Charge ${i + 1}", ChargeKind.entries.random(random), random.nextLong(-200_000, 400_000))
        }
        val lines = items.sumOf { it.amountMinor } + charges.sumOf { it.amountMinor }
        val printed = when (random.nextInt(4)) {
            0 -> null
            1 -> lines + random.nextLong(-50_000, 50_000)
            else -> lines
        }
        val bill = Bill(null, null, currency, items, charges, printed, lastItemNumber = items.size, lastChargeNumber = charges.size)
        // Bills that come to less than nothing are refunds, not splits.
        return if (bill.totalMinor >= 0) bill else bill.copy(printedTotalMinor = 0)
    }

    /** A plan for [bill] split one of the ways the screens offer, with people who may not share it and one or several payers. */
    private fun randomPlan(bill: Bill): SplitPlan {
        val start = (1..random.nextInt(0, 6)).fold(SplitPlan()) { plan, n -> plan.withParticipantAdded(if (n == 3) "" else "Friend $n") }
        val people = start.participantIds
        val notSharing = people.filter { random.nextInt(5) == 0 }.toSet().takeIf { it.size < people.size } ?: emptySet()
        val sharing = people.filter { it !in notSharing }
        val whole: Division? = when (random.nextInt(5)) {
            0 -> Division.Equally()
            1 -> Division.ByShares(sharing.associateWith { random.nextLong(1, 4) })
            2 -> Division.ByPercent(partition(Division.ByPercent.FULL, sharing))
            3 -> Division.ByAmount(partition(bill.totalMinor, sharing))
            else -> null
        }
        val items = bill.items.filter { random.nextBoolean() }.associate { item ->
            item.id to when (random.nextInt(3)) {
                0 -> Division.Equally(among = sharing.filter { random.nextBoolean() }.toSet())
                1 -> Division.ByShares(sharing.associateWith { random.nextLong(1, 4) })
                else -> Division.ByAmount(partition(item.amountMinor, sharing))
            }
        }
        val payers = people.filter { random.nextInt(3) == 0 }.ifEmpty { listOf(ME_ID) }
        val payments = partition(bill.totalMinor, payers).map { (id, amount) -> Payment(id, amount) }
        return start.copy(setup = SplitSetup(whole = whole, items = items, notSharing = notSharing), payments = payments)
    }

    /**
     * An edit the AI might propose for [bill] and [plan], right or wrong. Most name items and people
     * on the bill, so that most lists get applied in part; some name what is not on it.
     */
    private fun randomEdit(bill: Bill, plan: SplitPlan): SplitEdit {
        fun item() = if (bill.items.isEmpty() || random.nextInt(8) == 0) UNKNOWN_ITEM else bill.items.random(random).id
        fun person() = when (random.nextInt(10)) {
            0 -> NEVER_ON_THE_BILL.random(random)
            1, 2 -> REFS.random(random)
            else -> plan.participantIds.random(random)
        }
        fun some() = List(random.nextInt(1, 4)) { person() }.distinct()
        fun share(least: Long) = if (random.nextInt(8) == 0) random.nextLong(-1, least) else random.nextLong(least, 4)
        fun amount() = when (random.nextInt(8)) {
            0 -> -random.nextLong(1, 100_000)
            1 -> 0L
            2 -> Long.MAX_VALUE
            3 -> BillCurrency.MAX_AMOUNT_MINOR + 1
            else -> random.nextLong(1, 2_000_000)
        }

        /** [total] cut into parts for [among], as often as not a little wrong. */
        fun parts(total: Long, among: List<String>): Map<String, Long> {
            val parts = partition(total, among).toMutableMap()
            when (random.nextInt(6)) {
                0 -> parts[among.random(random)] = random.nextLong(-5, 5)
                1 -> parts[among.random(random)] = Long.MAX_VALUE
                2 -> parts.remove(among.random(random))
                else -> Unit
            }
            return parts
        }

        return when (random.nextInt(13)) {
            0 -> ItemSharers(item(), if (random.nextInt(10) == 0) emptyList() else some())
            1 -> ItemShares(item(), some().associateWith { share(least = 1) })
            2 -> SetMode(SplitMode.entries.random(random))
            3 -> WholeShares(some().associateWith { share(least = 0) })
            4 -> WholePercent(parts(Division.ByPercent.FULL, some()))
            5 -> WholeAmounts(parts(bill.totalMinor, some()))
            6 -> AddPerson((REFS + "friend").random(random), listOf("Zoe", "Ann", "", " ").random(random))
            7 -> RemovePerson(person())
            8 -> SetSharing(person(), random.nextBoolean())
            9 -> randomCharge(amount())
            10 -> SetPayer(person())
            11 -> ItemAmount(item(), amount())
            else -> ItemQuantity(item(), listOf("0", "-1", "0.5", "3", "2.25", "1E+9", "1E-12", "999999").random(random).let(::BigDecimal))
        }
    }

    /** A charge of [amount] or of a percentage, either of them possibly wrong, or both at once. */
    private fun randomCharge(amount: Long): AddCharge = when (random.nextInt(4)) {
        0 -> AddCharge(ChargeKind.entries.random(random), "", amountMinor = amount)
        1 -> AddCharge(ChargeKind.entries.random(random), "Tip", percentBasisPoints = random.nextLong(-100, 12_000))
        2 -> AddCharge(ChargeKind.TIP, "Tip", amountMinor = amount, percentBasisPoints = 1_000)
        else -> AddCharge(ChargeKind.DISCOUNT, "Voucher", percentBasisPoints = random.nextLong(1, 10_001))
    }

    /** [amount] cut at random into parts of zero or more for [people], adding up to it exactly. */
    private fun partition(amount: Long, people: List<String>): Map<String, Long> {
        if (people.isEmpty()) return emptyMap()
        val cuts = (List(people.size - 1) { random.nextLong(0, amount + 1) } + listOf(0L, amount)).sorted()
        return people.indices.associate { i -> people[i] to cuts[i + 1] - cuts[i] }
    }

    private companion object {
        const val UNKNOWN_ITEM = "i404"
        val REFS = listOf("new1", "new2", "new3")

        /** A participant id no bill here has, and a ref no add_person here uses. */
        val NEVER_ON_THE_BILL = setOf("p404", "new9")
    }
}
