package com.dantech.splitvibe.domain

import com.dantech.splitvibe.data.contacts.Contact
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPlannerTest {

    private val minh = Contact("c1", "Trần Quang Minh", "Minh", "minh.tran")
    private val lan = Contact("c2", "Lê Thị Lan", "Lan", "lan.le")
    private val emmaW = Contact("c3", "Emma Wilson", "Emma", "emma.w")
    private val emmaS = Contact("c4", "Emma Schneider", "Emma", "emma.schneider")
    private val contacts = listOf(minh, lan, emmaW, emmaS)

    private val beer = 0
    private val hotpot = 1
    private val greens = 2
    private val bill = BillReconciler.reconcile(
        merchant = null,
        date = null,
        currency = BillCurrency("VND", 0),
        items = listOf(
            BillItem("Bia Saigon", BigDecimal(2), 25_000, 50_000),
            BillItem("Lau thai", BigDecimal.ONE, 350_000, 350_000),
            BillItem("Rau muong", BigDecimal.ONE, 45_000, 45_000),
        ),
        charges = emptyList(),
        totalMinor = 445_000,
        warnings = emptyList(),
    )

    private fun only(item: Int, vararg people: String, me: Boolean = false) = ItemAssignmentHint(item, ShareRule.ONLY, people.toList(), me)
    private fun allExcept(item: Int, vararg people: String, me: Boolean = false) =
        ItemAssignmentHint(item, ShareRule.ALL_EXCEPT, people.toList(), me)

    @Test
    fun `plain split request starts with only the user`() {
        val plan = SplitPlanner.fromHint(SplitHint(), bill, contacts)

        assertEquals(SplitPlan(includeMe = true), plan)
        assertEquals(listOf(ME_ID), plan.participantIds)
        assertTrue(plan.openNotices().isEmpty())
    }

    @Test
    fun `named people are selected and their items assigned`() {
        val hint = SplitHint(people = listOf("Minh", "lan"), itemAssignments = listOf(only(beer, "Minh")))

        val plan = SplitPlanner.fromHint(hint, bill, contacts)

        assertEquals(listOf(ME_ID, "c1", "c2"), plan.participantIds)
        assertEquals(mapOf(beer to setOf("c1")), plan.sharersByItem())
        assertTrue(plan.openNotices().isEmpty())
    }

    @Test
    fun `person mentioned only for an item still joins the split`() {
        val plan = SplitPlanner.fromHint(SplitHint(itemAssignments = listOf(only(greens, "Lan", me = true))), bill, contacts)

        assertEquals(listOf("c2"), plan.selectedContactIds)
        assertEquals(mapOf(greens to setOf(ME_ID, "c2")), plan.sharersByItem())
    }

    @Test
    fun `item the user did not have is shared by everyone else, including people picked later`() {
        // "We are 3, I didn't drink": nobody is named, so the others are not known yet.
        val plan = SplitPlanner.fromHint(SplitHint(peopleCount = 3, itemAssignments = listOf(allExcept(beer, me = true))), bill, contacts)

        assertTrue("alone, the user still carries the item", plan.sharersByItem().isEmpty())
        assertEquals(listOf("Your instruction mentions 3 people. Pick 2 more people below."), plan.openNotices())

        val withFriends = plan.withContactToggled("c1").withContactToggled("c2")

        assertEquals(mapOf(beer to setOf("c1", "c2")), withFriends.sharersByItem())
        assertTrue(withFriends.openNotices().isEmpty())
        val split = SplitCalculator.split(bill, withFriends.participantIds, withFriends.sharersByItem())
        assertEquals(listOf(131_667L, 156_667L, 156_666L), split.shares.map { it.totalMinor })
    }

    @Test
    fun `named person who did not have an item is left out of it`() {
        val hint = SplitHint(people = listOf("Minh", "Lan"), itemAssignments = listOf(allExcept(hotpot, "Lan")))

        val plan = SplitPlanner.fromHint(hint, bill, contacts)

        assertEquals(mapOf(hotpot to setOf(ME_ID, "c1")), plan.sharersByItem())
    }

    @Test
    fun `ambiguous and unknown names are left to the user instead of guessed`() {
        val hint = SplitHint(people = listOf("Emma", "Zoe", "Minh"), itemAssignments = listOf(only(beer, "Minh", "Zoe")))

        val plan = SplitPlanner.fromHint(hint, bill, contacts)

        assertEquals(listOf("c1"), plan.selectedContactIds)
        assertTrue("an item shared with an unknown person must not be assigned", plan.sharersByItem().isEmpty())
        assertEquals(
            listOf(
                "\"Emma\" could be Emma Wilson or Emma Schneider. Pick the right person below.",
                "\"Zoe\" is not in your contacts. Pick them below.",
                "\"Bia Saigon\" was not assigned automatically. Tap it to choose who shares it.",
            ),
            plan.openNotices(),
        )
    }

    @Test
    fun `notices disappear once the user has dealt with them`() {
        val hint = SplitHint(people = listOf("Emma", "Minh"), itemAssignments = listOf(only(beer, "Minh", "Emma")))
        val plan = SplitPlanner.fromHint(hint, bill, contacts)
        assertEquals(2, plan.openNotices().size)

        val emmaPicked = plan.withContactToggled("c4")
        assertEquals(listOf("\"Bia Saigon\" was not assigned automatically. Tap it to choose who shares it."), emmaPicked.openNotices())

        val beerAssigned = emmaPicked.withItemSharers(beer, setOf("c1", "c4"))
        assertTrue(beerAssigned.openNotices().isEmpty())
    }

    @Test
    fun `user who is not taking part is left out everywhere`() {
        val hint = SplitHint(people = listOf("Minh", "Lan"), userIncluded = false, itemAssignments = listOf(only(hotpot, "Lan", me = true)))

        val plan = SplitPlanner.fromHint(hint, bill, contacts)

        assertEquals(listOf("c1", "c2"), plan.participantIds)
        assertEquals(mapOf(hotpot to setOf("c2")), plan.sharersByItem())
    }

    @Test
    fun `assignment for an item that does not exist is ignored`() {
        val plan = SplitPlanner.fromHint(SplitHint(itemAssignments = listOf(only(9, "Minh"))), bill, contacts)

        assertEquals(SplitPlan(includeMe = true), plan)
    }

    @Test
    fun `choosing everyone or nobody for an item puts it back to shared by all`() {
        val plan =
            SplitPlan(
                selectedContactIds = listOf("c1", "c2"),
                assignments = mapOf(beer to setOf("c1")),
                exclusions = mapOf(greens to setOf(ME_ID)),
            )

        assertEquals(emptyMap<Int, Set<String>>(), plan.withItemSharers(beer, setOf(ME_ID, "c1", "c2")).assignments)
        assertEquals(emptyMap<Int, Set<String>>(), plan.withItemSharers(beer, emptySet()).assignments)
        assertEquals(mapOf(beer to setOf("c2")), plan.withItemSharers(beer, setOf("c2", "stranger")).assignments)
        // A choice made by hand replaces what the instruction said about that item.
        assertEquals(mapOf(beer to setOf("c1")), plan.withItemSharers(greens, setOf(ME_ID, "c1", "c2")).sharersByItem())
    }

    @Test
    fun `removing a person takes them off every item`() {
        val plan =
            SplitPlan(selectedContactIds = listOf("c1", "c2"), assignments = mapOf(beer to setOf("c1"), hotpot to setOf(ME_ID, "c2")))

        val withoutMinh = plan.withContactToggled("c1")
        assertEquals(mapOf(hotpot to setOf(ME_ID, "c2")), withoutMinh.assignments)

        val withoutMe = plan.withMeToggled()
        assertEquals(listOf("c1", "c2"), withoutMe.participantIds)
        assertEquals(mapOf(beer to setOf("c1"), hotpot to setOf("c2")), withoutMe.assignments)
    }
}
