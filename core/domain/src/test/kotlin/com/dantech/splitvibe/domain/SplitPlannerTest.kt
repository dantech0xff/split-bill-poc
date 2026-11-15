package com.dantech.splitvibe.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPlannerTest {

    private val minh = Person("person-1", "Minh")
    private val lan = Person("person-2", "Lan")
    private val people = listOf(minh, lan)

    private val beer = "i1"
    private val hotpot = "i2"
    private val greens = "i3"
    private val bill = BillReconciler.reconcile(
        merchant = null,
        date = null,
        currency = BillCurrency("VND", 0),
        items = listOf(
            BillItem(beer, "Bia Saigon", BigDecimal(2), 25_000, 50_000),
            BillItem(hotpot, "Lau thai", BigDecimal.ONE, 350_000, 350_000),
            BillItem(greens, "Rau muong", BigDecimal.ONE, 45_000, 45_000),
        ),
        charges = emptyList(),
        printedTotalMinor = 445_000,
        warnings = emptyList(),
    )

    private fun only(item: String, vararg people: String, me: Boolean = false) =
        ItemAssignmentHint(item, ShareRule.ONLY, people.toList(), me)
    private fun allExcept(item: String, vararg people: String, me: Boolean = false) =
        ItemAssignmentHint(item, ShareRule.ALL_EXCEPT, people.toList(), me)

    private fun SplitPlan.names() = participants.map { it.name }

    private fun SplitPlan.totals() = result(bill).shares.map { it.totalMinor }

    @Test
    fun `plain split request starts with only the user, who paid the whole bill`() {
        val plan = SplitPlanner.fromHint(SplitHint(), bill, people)

        assertEquals(listOf(ME_ID), plan.participantIds)
        assertEquals(SplitMode.EQUAL, plan.mode)
        assertEquals(listOf(Payment(ME_ID, 445_000)), plan.payments)
        assertTrue(plan.openNotices().isEmpty())
        assertTrue(plan.problems(bill).isEmpty())
    }

    @Test
    fun `every name becomes a participant, someone known again or someone new`() {
        val hint = SplitHint(people = listOf("minh", "Zoe", "Minh"))

        val plan = SplitPlanner.fromHint(hint, bill, people)

        assertEquals(listOf("", "Minh", "Zoe"), plan.names())
        assertEquals(listOf(null, minh.id, null), plan.participants.map { it.personId })
        assertEquals(listOf(ME_ID, "p1", "p2"), plan.participantIds)
    }

    @Test
    fun `named people share only their items, the rest is shared by everyone`() {
        val hint = SplitHint(people = listOf("Minh", "Lan"), itemAssignments = listOf(only(beer, "Minh")))

        val plan = SplitPlanner.fromHint(hint, bill, people)

        assertEquals(SplitMode.BY_ITEM, plan.mode)
        assertEquals(mapOf(beer to Division.Equally(among = setOf("p1"))), plan.setup.items)
        assertEquals(445_000L, plan.totals().sum())
        assertEquals(listOf(131_667L, 181_667L, 131_666L), plan.totals())
    }

    @Test
    fun `person mentioned only for an item still joins the split`() {
        val plan = SplitPlanner.fromHint(SplitHint(itemAssignments = listOf(only(greens, "Lan", me = true))), bill, people)

        assertEquals(listOf("", "Lan"), plan.names())
        assertEquals(mapOf(greens to Division.Equally(among = setOf(ME_ID, "p1"))), plan.setup.items)
    }

    @Test
    fun `item the user did not have is shared by everyone else, including people added later`() {
        // "We are 3, I didn't drink": nobody is named, so the others are not known yet.
        val plan = SplitPlanner.fromHint(SplitHint(peopleCount = 3, itemAssignments = listOf(allExcept(beer, me = true))), bill, people)

        assertEquals(listOf("Your instruction mentions 3 people. Add 2 more people below."), plan.openNotices())

        val withFriends = plan.withParticipantAdded("Minh", minh.id).withParticipantAdded("Lan", lan.id)

        assertTrue(withFriends.openNotices().isEmpty())
        assertEquals(listOf(131_667L, 156_667L, 156_666L), withFriends.totals())
    }

    @Test
    fun `named person who did not have an item is left out of it`() {
        val hint = SplitHint(people = listOf("Minh", "Lan"), itemAssignments = listOf(allExcept(hotpot, "Lan")))

        val plan = SplitPlanner.fromHint(hint, bill, people)

        assertEquals(mapOf(hotpot to Division.Equally(except = setOf("p2"))), plan.setup.items)
        // The hotpot is the user's and Minh's; the beer and the greens are everyone's.
        assertEquals(listOf(206_667L, 206_667L, 31_666L), plan.totals())
    }

    @Test
    fun `user who is not taking part shares nothing but still paid`() {
        val hint = SplitHint(people = listOf("Minh", "Lan"), userIncluded = false, itemAssignments = listOf(only(hotpot, "Lan", me = true)))

        val plan = SplitPlanner.fromHint(hint, bill, people)

        assertEquals(setOf(ME_ID), plan.setup.notSharing)
        assertEquals(listOf("p1", "p2"), plan.sharingIds)
        assertEquals(mapOf(hotpot to Division.Equally(among = setOf("p2"))), plan.setup.items)
        assertEquals(0L, plan.result(bill).shareOf(ME_ID)!!.totalMinor)
        assertEquals(listOf(Payment(ME_ID, 445_000)), plan.payments)
    }

    @Test
    fun `assignment for an item that is not on the bill is ignored`() {
        val plan = SplitPlanner.fromHint(SplitHint(itemAssignments = listOf(only("i9", "Minh"))), bill, people)

        assertEquals(SplitMode.EQUAL, plan.mode)
        assertEquals(listOf(ME_ID), plan.participantIds)
    }

    @Test
    fun `choosing everyone or nobody for an item puts it back to shared by all`() {
        val plan = SplitPlan().withParticipantAdded("Minh").withParticipantAdded("Lan").withMode(SplitMode.BY_ITEM, bill)
            .withItemSharers(beer, setOf("p1"))

        assertEquals(emptyMap<String, Division>(), plan.withItemSharers(beer, setOf(ME_ID, "p1", "p2")).setup.items)
        assertEquals(emptyMap<String, Division>(), plan.withItemSharers(beer, emptySet()).setup.items)
        assertEquals(mapOf(beer to Division.Equally(among = setOf("p2"))), plan.withItemSharers(beer, setOf("p2", "stranger")).setup.items)
    }

    @Test
    fun `removing a person takes them off every item and every payment`() {
        val plan = SplitPlan().withParticipantAdded("Minh").withParticipantAdded("Lan").withMode(SplitMode.BY_ITEM, bill)
            .withItemSharers(beer, setOf("p1"))
            .withItemSharers(hotpot, setOf(ME_ID, "p2"))
            .withPayments(listOf(Payment(ME_ID, 400_000), Payment("p1", 45_000)))

        val withoutMinh = plan.withParticipantRemoved("p1", bill)

        assertEquals(listOf(ME_ID, "p2"), withoutMinh.participantIds)
        assertEquals(mapOf(hotpot to Division.Equally(among = setOf(ME_ID, "p2"))), withoutMinh.setup.items)
        // The one payer left paid the whole bill, as a single payer always does.
        assertEquals(listOf(Payment(ME_ID, bill.totalMinor)), withoutMinh.payments)
        assertEquals(plan, plan.withParticipantRemoved(ME_ID, bill))
    }

    @Test
    fun `a bill moved to another currency keeps its exact amounts and payments as written`() {
        val usd = BillCurrency("USD", 2)
        val vnd = BillCurrency("VND", 0)
        val plan = SplitPlan().withParticipantAdded("Minh")
            .withMode(SplitMode.BY_AMOUNT, bill)
            .withWholeValue(ME_ID, 3_000)
            .withWholeValue("p1", 1_550)
            .withPayments(listOf(Payment(ME_ID, 2_000), Payment("p1", 2_550)))

        val moved = plan.withCurrency(usd, vnd)

        assertEquals(Division.ByAmount(mapOf(ME_ID to 30L, "p1" to 16L)), moved.setup.whole)
        assertEquals(listOf(Payment(ME_ID, 20), Payment("p1", 26)), moved.payments)
        assertEquals(plan, plan.withCurrency(usd, usd))
    }

    @Test
    fun `the user can stop sharing the bill and start again`() {
        val plan = SplitPlan().withParticipantAdded("Minh").withBill(bill)

        val paidOnly = plan.withMeSharingToggled()

        assertEquals(listOf("p1"), paidOnly.sharingIds)
        assertEquals(listOf(0L, 445_000L), paidOnly.totals())
        assertEquals(plan, paidOnly.withMeSharingToggled())
    }

    @Test
    fun `a new way of dividing starts from an even division`() {
        val plan = SplitPlan().withParticipantAdded("Minh").withParticipantAdded("Lan").withBill(bill)

        assertEquals(Division.ByShares(mapOf(ME_ID to 1L, "p1" to 1L, "p2" to 1L)), plan.withMode(SplitMode.BY_SHARES, bill).setup.whole)
        assertEquals(
            Division.ByPercent(mapOf(ME_ID to 3_334L, "p1" to 3_333L, "p2" to 3_333L)),
            plan.withMode(SplitMode.BY_PERCENT, bill).setup.whole,
        )
        assertEquals(
            Division.ByAmount(mapOf(ME_ID to 148_334L, "p1" to 148_333L, "p2" to 148_333L)),
            plan.withMode(SplitMode.BY_AMOUNT, bill).setup.whole,
        )
        listOf(SplitMode.EQUAL, SplitMode.BY_ITEM, SplitMode.BY_SHARES, SplitMode.BY_PERCENT, SplitMode.BY_AMOUNT).forEach { mode ->
            assertTrue("$mode starts complete", plan.withMode(mode, bill).problems(bill).isEmpty())
        }
    }

    @Test
    fun `someone added to exact amounts counts for nothing until given a part`() {
        val plan = SplitPlan().withParticipantAdded("Minh").withBill(bill).withMode(SplitMode.BY_AMOUNT, bill)

        val withLan = plan.withParticipantAdded("Lan")

        assertTrue(withLan.problems(bill).isEmpty())
        assertEquals(0L, withLan.result(bill).shareOf("p2")!!.totalMinor)
        assertTrue(withLan.withWholeValue("p2", 10_000).problems(bill).single().startsWith("Amounts add up to"))
    }

    @Test
    fun `a single payer follows the bill total, several payers keep their amounts`() {
        val edited = BillEditor.withPrintedTotal(bill, 450_000)
        val single = SplitPlan().withBill(bill)
        val several = single.withParticipantAdded("Minh").withPayments(listOf(Payment(ME_ID, 400_000), Payment("p1", 45_000)))

        assertEquals(listOf(Payment(ME_ID, 450_000)), single.withBill(edited).payments)
        assertEquals(several.payments, several.withBill(edited).payments)
        assertTrue(several.withBill(edited).problems(edited).single().startsWith("Payments add up to"))
    }

    @Test
    fun `an item that was removed from the bill loses its division`() {
        val plan = SplitPlan().withParticipantAdded("Minh").withMode(SplitMode.BY_ITEM, bill).withItemSharers(beer, setOf("p1"))

        val withoutBeer = plan.withBill(BillEditor.withoutItem(bill, beer))

        assertTrue(withoutBeer.setup.items.isEmpty())
    }
}
