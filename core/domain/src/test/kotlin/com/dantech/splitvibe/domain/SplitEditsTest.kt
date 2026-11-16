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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitEditsTest {

    private val vnd = BillCurrency("VND", 0)
    private val beer = "i1"
    private val pho = "i2"
    private val minh = "p1"
    private val lan = "p2"
    private val khoa = "p3"

    /** Three beers at 30,000 and four bowls of pho at 50,000, with a service charge of 14,500 on 290,000 of items. */
    private val bill = Bill(
        merchant = "Pho 24",
        date = "2026-11-16",
        currency = vnd,
        items = listOf(
            BillItem(beer, "Beer", BigDecimal(3), 30_000, 90_000),
            BillItem(pho, "Pho", BigDecimal(4), 50_000, 200_000),
        ),
        charges = listOf(BillCharge("c1", "Service charge", ChargeKind.SERVICE_CHARGE, 14_500)),
        printedTotalMinor = 304_500,
        lastItemNumber = 2,
        lastChargeNumber = 1,
    )

    /** The user (Dana), Minh, Lan and Khoa; everyone but Khoa had beer, and the user paid. */
    private val plan = SplitPlan()
        .withParticipantAdded("Minh")
        .withParticipantAdded("Lan")
        .withParticipantAdded("Khoa")
        .withMode(SplitMode.BY_ITEM, bill)
        .withItemSharers(beer, setOf(ME_ID, minh, lan))
        .withBill(bill)

    private fun shares(bill: Bill, plan: SplitPlan): Map<String, Long> = plan.result(bill).shares.associate { it.personId to it.totalMinor }

    private fun apply(vararg edits: SplitEdit, bill: Bill = this.bill, plan: SplitPlan = this.plan) =
        SplitEdits.apply(bill, plan, edits.toList())

    private fun reasonOf(edit: SplitEdit, bill: Bill = this.bill, plan: SplitPlan = this.plan): String {
        val result = apply(edit, bill = bill, plan = plan)
        assertEquals("$edit is rejected", emptyList<SplitEdit>(), result.applied)
        assertEquals("a rejected edit changes nothing", bill, result.bill)
        assertEquals("a rejected edit changes nothing", plan, result.plan)
        return result.rejected.single().reason
    }

    @Test
    fun `Lan no longer drinking beer takes her off it, and the bill and plan from before still give the old shares`() {
        // Before: the beer is 30,000 each for the user, Minh and Lan, the pho 50,000 each for all four:
        // items 80,000, 80,000, 80,000 and 50,000 of 290,000. The service charge follows them:
        // 14,500 × 80/290 = 4,000 for each of the three and 14,500 × 50/290 = 2,500 for Khoa.
        val before = mapOf(ME_ID to 84_000L, minh to 84_000L, lan to 84_000L, khoa to 52_500L)
        assertEquals(before, shares(bill, plan))

        val result = apply(ItemSharers(beer, listOf(ME_ID, minh)))

        // After: the beer is 45,000 each for the user and Minh, so the items are 95,000, 95,000, 50,000
        // and 50,000, and the service charge is 14,500 × 95/290 = 4,750 for each of the two beer
        // drinkers and 2,500 for each of the others.
        val after = mapOf(ME_ID to 99_750L, minh to 99_750L, lan to 52_500L, khoa to 52_500L)
        assertEquals(1, result.applied.size)
        assertTrue(result.rejected.isEmpty())
        assertEquals(Division.Equally(among = setOf(ME_ID, minh)), result.plan.setup.items[beer])
        assertEquals(after, shares(result.bill, result.plan))
        val calculated = SplitCalculator.split(result.bill, result.plan.participantIds, result.plan.setup).shares
        assertEquals(calculated.associate { it.personId to it.totalMinor }, after)
        assertEquals(bill.totalMinor, after.values.sum())
        // Undo keeps the bill and plan from before, which still give exactly the old shares.
        assertEquals(before, shares(bill, plan))
    }

    @Test
    fun `an item edit on a bill split as a whole splits it item by item first`() {
        val equal = plan.withMode(SplitMode.EQUAL, bill)

        val sharers = apply(ItemSharers(beer, listOf(minh)), plan = equal)
        val inShares = apply(ItemShares(pho, mapOf(ME_ID to 2L, khoa to 1L)), plan = equal)

        assertEquals(SplitMode.BY_ITEM, sharers.plan.mode)
        assertEquals(Division.Equally(among = setOf(minh)), sharers.plan.setup.items[beer])
        assertEquals(SplitMode.BY_ITEM, inShares.plan.mode)
        assertEquals(Division.ByShares(mapOf(ME_ID to 2L, khoa to 1L)), inShares.plan.setup.items[pho])
        assertTrue(inShares.plan.problems(bill).isEmpty())
        // Naming everyone who shares the bill shares the item with everyone again.
        assertEquals(null, apply(ItemSharers(beer, listOf(ME_ID, minh, lan, khoa))).plan.setup.items[beer])
    }

    @Test
    fun `a percentage charge is of the items, rounded half to even, and moves the printed total and the payer`() {
        val usd = Bill(
            merchant = null,
            date = null,
            currency = BillCurrency("USD", 2),
            items = listOf(BillItem("i1", "Pasta", BigDecimal.ONE, 1_225, 1_225)),
            charges = emptyList(),
            printedTotalMinor = 1_225,
        )
        val single = SplitPlan().withParticipantAdded("Minh").withBill(usd)

        // 10% of 12.25 is 1.225: half a cent, which goes to the even cent, 1.22.
        val tipped = apply(AddCharge(ChargeKind.TIP, "Tip", percentBasisPoints = 1_000), bill = usd, plan = single)
        // 10% of 13.75 is 1.375, which goes to 1.38.
        val pricier = BillEditor.withItemAmount(usd, "i1", 1_375).copy(printedTotalMinor = 1_375)
        val tippedMore = apply(AddCharge(ChargeKind.TIP, "", percentBasisPoints = 1_000), bill = pricier, plan = single.withBill(pricier))

        assertEquals(BillCharge("c1", "Tip", ChargeKind.TIP, 122), tipped.bill.charges.single())
        assertEquals(1_347L, tipped.bill.printedTotalMinor)
        assertEquals(listOf(Payment(ME_ID, 1_347)), tipped.plan.payments)
        assertTrue(tipped.plan.problems(tipped.bill).isEmpty())
        assertEquals(138L, tippedMore.bill.charges.single().amountMinor)
        assertEquals(1_513L, tippedMore.bill.totalMinor)
    }

    @Test
    fun `a discount lowers the bill, and a bill without a printed total is what its lines add up to`() {
        val typed = bill.copy(printedTotalMinor = null)

        val result = apply(
            AddCharge(ChargeKind.DISCOUNT, "", amountMinor = 20_000),
            AddCharge(ChargeKind.TAX, "VAT", percentBasisPoints = 800),
            bill = typed,
            plan = plan.withBill(typed),
        )

        // The VAT is 8% of the items, 290,000, whatever the discount: 23,200.
        assertEquals(
            listOf(
                BillCharge("c2", "Discount", ChargeKind.DISCOUNT, -20_000),
                BillCharge("c3", "VAT", ChargeKind.TAX, 23_200),
            ),
            result.bill.charges.drop(1),
        )
        assertEquals(null, result.bill.printedTotalMinor)
        assertEquals(304_500L - 20_000 + 23_200, result.bill.totalMinor)
        assertEquals(listOf(Payment(ME_ID, result.bill.totalMinor)), result.plan.payments)
    }

    @Test
    fun `several payers keep what they paid when a charge changes the total, and one payer pays all of it`() {
        val twoPayers = plan.withPayments(listOf(Payment(ME_ID, 200_000), Payment(minh, 104_500)))

        val result = apply(AddCharge(ChargeKind.TIP, "Tip", amountMinor = 10_000), plan = twoPayers)
        val settled = apply(AddCharge(ChargeKind.TIP, "Tip", amountMinor = 10_000), SetPayer(minh), plan = twoPayers)

        assertEquals(twoPayers.payments, result.plan.payments)
        assertTrue(result.plan.problems(result.bill).single().startsWith("Payments add up to"))
        assertEquals(listOf(Payment(minh, 314_500)), settled.plan.payments)
        assertTrue(settled.plan.problems(settled.bill).isEmpty())
    }

    @Test
    fun `a charge that is not a positive amount or a percentage of at most a hundred is rejected`() {
        assertEquals(
            "A charge needs an amount or a percentage, and only one of them.",
            reasonOf(AddCharge(ChargeKind.TIP, "Tip", amountMinor = 1_000, percentBasisPoints = 1_000)),
        )
        assertEquals("A charge needs an amount or a percentage, and only one of them.", reasonOf(AddCharge(ChargeKind.TIP, "Tip")))
        assertEquals("A charge has to be more than zero.", reasonOf(AddCharge(ChargeKind.TIP, "Tip", amountMinor = 0)))
        assertEquals("That is more than a bill can hold.", reasonOf(AddCharge(ChargeKind.TIP, "Tip", amountMinor = Long.MAX_VALUE)))
        assertEquals(
            "A percentage has to be more than 0% and at most 100%.",
            reasonOf(AddCharge(ChargeKind.TIP, "Tip", percentBasisPoints = 10_001)),
        )
        assertEquals(
            "A percentage has to be more than 0% and at most 100%.",
            reasonOf(AddCharge(ChargeKind.TIP, "Tip", percentBasisPoints = 0)),
        )
        val noItems = Bill.empty(vnd).copy(printedTotalMinor = 50_000)
        assertEquals(
            "10% of the items comes to nothing.",
            reasonOf(AddCharge(ChargeKind.TIP, "Tip", percentBasisPoints = 1_000), bill = noItems, plan = SplitPlan().withBill(noItems)),
        )
        assertEquals(
            "That would take the bill total below zero.",
            reasonOf(AddCharge(ChargeKind.DISCOUNT, "Voucher", amountMinor = 400_000)),
        )
        // A whole discount is still a bill, of nothing at all.
        assertEquals(0L, apply(AddCharge(ChargeKind.DISCOUNT, "Voucher", amountMinor = 304_500)).bill.totalMinor)
    }

    @Test
    fun `new people are named by their ref in the edits after them`() {
        val result = apply(
            AddPerson("new1", "Zoe"),
            ItemSharers(beer, listOf(ME_ID, "new1")),
            SetPayer("new1"),
        )

        val zoe = result.plan.participants.last()
        assertEquals(Participant("p4", "Zoe"), zoe)
        assertEquals(3, result.applied.size)
        assertEquals(Division.Equally(among = setOf(ME_ID, zoe.id)), result.plan.setup.items[beer])
        assertEquals(listOf(Payment(zoe.id, 304_500)), result.plan.payments)
        assertTrue(result.plan.problems(result.bill).isEmpty())
    }

    @Test
    fun `items, people and refs that are not on the bill are rejected and reported`() {
        val result = apply(
            ItemSharers("i9", listOf(ME_ID)),
            SetPayer("p9"),
            ItemSharers(beer, listOf(ME_ID, "new2")),
            AddPerson("new1", "Zoe"),
            AddPerson("new1", "Zoe again"),
            AddPerson("friend", "Ann"),
            AddPerson("new3", " "),
            SetSharing("new3", false),
        )

        assertEquals(listOf(AddPerson("new1", "Zoe")), result.applied)
        assertEquals(
            listOf(
                RejectedEdit(0, ItemSharers("i9", listOf(ME_ID)), "That item is not on the bill."),
                RejectedEdit(1, SetPayer("p9"), "That person is not on the bill."),
                RejectedEdit(2, ItemSharers(beer, listOf(ME_ID, "new2")), "That person is not on the bill."),
                RejectedEdit(4, AddPerson("new1", "Zoe again"), "This person was already added."),
                RejectedEdit(5, AddPerson("friend", "Ann"), "This person could not be added."),
                RejectedEdit(6, AddPerson("new3", " "), "A new person needs a name."),
                RejectedEdit(7, SetSharing("new3", false), "That person is not on the bill."),
            ),
            result.rejected,
        )
    }

    @Test
    fun `the user stays on the bill, and someone taken off it can no longer be named by their ref`() {
        val result = apply(
            RemovePerson(ME_ID),
            AddPerson("new1", "Zoe"),
            RemovePerson("new1"),
            AddPerson("new2", "Ann"),
            SetPayer("new1"),
            RemovePerson(lan),
        )

        assertEquals("You stay on the bill. You can stop sharing it instead.", result.rejected[0].reason)
        // Zoe's id is free again and goes to Ann, whom Zoe's ref must not reach.
        assertEquals(listOf(ME_ID, minh, khoa, "p4"), result.plan.participantIds)
        assertEquals("Ann", result.plan.participant("p4")?.name)
        assertEquals(RejectedEdit(4, SetPayer("new1"), "That person is not on the bill."), result.rejected[1])
        assertEquals(Division.Equally(among = setOf(ME_ID, minh)), result.plan.setup.items[beer])
        assertEquals(2, result.rejected.size)
    }

    @Test
    fun `someone who only takes part shares nothing and cannot be given a part`() {
        val result = apply(SetSharing(khoa, false), SetPayer(khoa))

        assertEquals(setOf(khoa), result.plan.setup.notSharing)
        assertEquals(0L, shares(result.bill, result.plan)[khoa])
        assertEquals(listOf(Payment(khoa, 304_500)), result.plan.payments)
        assertTrue(result.plan.problems(result.bill).isEmpty())
        val notSharing = result.plan
        assertEquals("Khoa does not share the bill.", reasonOf(ItemSharers(pho, listOf(khoa, minh)), plan = notSharing))
        assertEquals("Khoa does not share the bill.", reasonOf(WholeShares(mapOf(ME_ID to 1L, khoa to 1L)), plan = notSharing))
        val userNotSharing = notSharing.withSharing(ME_ID, false)
        assertEquals("You do not share the bill.", reasonOf(ItemShares(pho, mapOf(ME_ID to 1L)), plan = userNotSharing))
        // A part of nothing for them is fine.
        assertEquals(1, apply(WholeShares(mapOf(ME_ID to 1L, khoa to 0L)), plan = notSharing).applied.size)
        assertEquals(emptySet<String>(), apply(SetSharing(khoa, true), plan = notSharing).plan.setup.notSharing)
    }

    @Test
    fun `a split of the whole bill is taken only when its parts add up`() {
        val percent = apply(WholePercent(mapOf(ME_ID to 4_000L, minh to 3_000L, lan to 2_000L, khoa to 1_000L)))
        val amounts = apply(WholeAmounts(mapOf(ME_ID to 104_500L, minh to 100_000L, lan to 100_000L)))
        val shares = apply(WholeShares(mapOf(ME_ID to 2L, minh to 1L)))

        assertEquals(SplitMode.BY_PERCENT, percent.plan.mode)
        assertEquals(mapOf(ME_ID to 121_800L, minh to 91_350L, lan to 60_900L, khoa to 30_450L), shares(percent.bill, percent.plan))
        assertEquals(mapOf(ME_ID to 104_500L, minh to 100_000L, lan to 100_000L, khoa to 0L), shares(amounts.bill, amounts.plan))
        assertEquals(mapOf(ME_ID to 203_000L, minh to 101_500L, lan to 0L, khoa to 0L), shares(shares.bill, shares.plan))
        assertEquals(
            "Percentages add up to 90%. They need to add up to 100%.",
            reasonOf(WholePercent(mapOf(ME_ID to 6_000L, minh to 3_000L))),
        )
        assertEquals("Give at least one person a share.", reasonOf(WholeShares(mapOf(ME_ID to 0L))))
        // Zoe is added as p4, so her ref and her id are the same person.
        val twice = apply(AddPerson("new1", "Zoe"), WholeShares(mapOf("new1" to 1L, "p4" to 2L)))
        assertEquals("The same person is named twice.", twice.rejected.single().reason)
    }

    @Test
    fun `a negative part, a percentage over a hundred or amounts that do not add up leave the split as it was`() {
        val byAmount = SplitEdits.apply(bill, plan, listOf(SetMode(SplitMode.BY_AMOUNT))).plan
        val hostile = listOf(
            WholeAmounts(mapOf(ME_ID to -100_000L, minh to 404_500L)),
            WholeAmounts(mapOf(ME_ID to 304_500L, minh to -1L, lan to 1L)),
            WholeAmounts(mapOf(ME_ID to 100_000L, minh to 100_000L)),
            WholeAmounts(mapOf(ME_ID to Long.MAX_VALUE, minh to Long.MAX_VALUE, lan to 304_502L)),
            WholePercent(mapOf(ME_ID to 15_000L, minh to -5_000L)),
            WholePercent(mapOf(ME_ID to 12_000L)),
            WholePercent(mapOf(ME_ID to Long.MAX_VALUE, minh to Long.MAX_VALUE, lan to 10_002L)),
            ItemShares(beer, mapOf(ME_ID to -1L, minh to 2L)),
            WholeShares(mapOf(ME_ID to -1L, minh to 2L)),
            ItemAmount(pho, -50_000),
            ItemQuantity(beer, BigDecimal(-2)),
        )

        val result = SplitEdits.apply(bill, byAmount, hostile)

        assertEquals(emptyList<SplitEdit>(), result.applied)
        assertEquals(hostile.indices.toList(), result.rejected.map { it.index })
        assertEquals(bill, result.bill)
        assertEquals(byAmount, result.plan)
        assertTrue(result.plan.problems(result.bill).isEmpty())
        assertEquals(
            listOf(
                "An amount cannot be less than zero.",
                "An amount cannot be less than zero.",
                "Amounts add up to ${bill.format(200_000)}, ${bill.format(104_500)} short of ${bill.format(304_500)}.",
                "No one can pay more than the bill total of ${bill.format(304_500)}.",
                "A percentage cannot be less than zero.",
                "A percentage cannot be more than 100%.",
                "A percentage cannot be more than 100%.",
                "Each share of an item has to be a whole number from 1.",
                "A share cannot be less than zero.",
                "An amount cannot be less than zero.",
                "A quantity has to be a number more than zero.",
            ),
            result.rejected.map { it.reason },
        )
    }

    @Test
    fun `edits are applied in order, each to the bill the ones before it left`() {
        // With a 10% tip of 29,000 the bill comes to 333,500, which exact amounts then have to add up to.
        val amounts = WholeAmounts(mapOf(ME_ID to 133_500L, minh to 100_000L, lan to 100_000L))
        val tip = AddCharge(ChargeKind.TIP, "Tip", percentBasisPoints = 1_000)

        val tipFirst = apply(tip, amounts)
        val amountsFirst = apply(amounts, tip)

        assertEquals(2, tipFirst.applied.size)
        assertEquals(333_500L, tipFirst.bill.totalMinor)
        assertTrue(tipFirst.plan.problems(tipFirst.bill).isEmpty())
        assertEquals(listOf<SplitEdit>(tip), amountsFirst.applied)
        assertEquals(0, amountsFirst.rejected.single().index)
    }

    @Test
    fun `item amounts and quantities change as the editor changes them, keeping the printed total`() {
        val result = apply(ItemAmount(beer, 96_000), ItemQuantity(pho, BigDecimal("3")))

        assertEquals(BillItem(beer, "Beer", BigDecimal(3), 32_000, 96_000), result.bill.items[0])
        assertEquals(BillItem(pho, "Pho", BigDecimal("3"), 50_000, 150_000), result.bill.items[1])
        assertEquals(304_500L, result.bill.totalMinor)
        // The lines now come to 260,500, and the 44,000 left of the printed total is shared in proportion.
        assertEquals(44_000L, result.bill.differenceMinor)
        assertEquals(304_500L, shares(result.bill, result.plan).values.sum())
        assertEquals("A quantity has to be a number more than zero.", reasonOf(ItemQuantity(beer, BigDecimal.ZERO)))
        assertEquals("A quantity has to be a number more than zero.", reasonOf(ItemQuantity(beer, BigDecimal("1E-999999999"))))
        assertEquals("A quantity has to be a number more than zero.", reasonOf(ItemQuantity(beer, BigDecimal("1E+999999999"))))
        assertEquals("That is more than a bill can hold.", reasonOf(ItemAmount(beer, BillCurrency.MAX_AMOUNT_MINOR + 1)))
        // A unit price only half of the most a bill holds, for an item bought twice: three of them do not fit.
        val most = BillCurrency.MAX_AMOUNT_MINOR
        val pricey = bill.copy(items = listOf(BillItem(beer, "Beer", BigDecimal(2), most / 2, most)))
        assertEquals("That is more than a bill can hold.", reasonOf(ItemQuantity(beer, BigDecimal(3)), bill = pricey))
    }

    @Test
    fun `a change of the way to split starts from an even split`() {
        val result = apply(SetMode(SplitMode.BY_PERCENT))

        assertEquals(Division.ByPercent(mapOf(ME_ID to 2_500L, minh to 2_500L, lan to 2_500L, khoa to 2_500L)), result.plan.setup.whole)
        assertEquals(SplitMode.BY_ITEM, apply(SetMode(SplitMode.BY_ITEM)).plan.mode)
    }

    @Test
    fun `each edit is described with the names of the people and items it names`() {
        fun describe(edit: SplitEdit, meName: String = "Dana") = SplitEdits.describe(edit, bill, plan, meName, Locale.US)

        assertEquals("Beer: shared by Dana and Minh", describe(ItemSharers(beer, listOf(ME_ID, minh))))
        assertEquals("Beer: shared by you, Minh and Lan", describe(ItemSharers(beer, listOf(ME_ID, minh, lan)), meName = " "))
        assertEquals("Pho: only Khoa", describe(ItemSharers(pho, listOf(khoa, khoa))))
        assertEquals("Pho: in shares, Dana 2 and Khoa 1", describe(ItemShares(pho, mapOf(ME_ID to 2L, khoa to 1L))))
        assertEquals("Split the whole bill equally", describe(SetMode(SplitMode.EQUAL)))
        assertEquals("Split the bill item by item", describe(SetMode(SplitMode.BY_ITEM)))
        assertEquals("Whole bill in shares: Dana 2 and Minh 1", describe(WholeShares(mapOf(ME_ID to 2L, minh to 1L))))
        assertEquals(
            "Whole bill by percentage: Dana 66.67% and Minh 33.33%",
            describe(WholePercent(mapOf(ME_ID to 6_667L, minh to 3_333L))),
        )
        assertEquals(
            "Whole bill by amount: Lan ₫4,500 and Khoa ₫300,000",
            describe(WholeAmounts(mapOf(lan to 4_500L, khoa to 300_000L))),
        )
        assertEquals("Add Zoe", describe(AddPerson("new1", " Zoe ")))
        assertEquals("Take Lan off the bill", describe(RemovePerson(lan)))
        assertEquals("Khoa does not share the bill", describe(SetSharing(khoa, false)))
        assertEquals("You share the bill", describe(SetSharing(ME_ID, true), meName = ""))
        assertEquals("Add a 10% tip (₫29,000)", describe(AddCharge(ChargeKind.TIP, "Tip", percentBasisPoints = 1_000)))
        assertEquals("Add an 8% VAT (₫23,200)", describe(AddCharge(ChargeKind.TAX, "VAT", percentBasisPoints = 800)))
        assertEquals("Add a 5% discount (-₫14,500)", describe(AddCharge(ChargeKind.DISCOUNT, "discount", percentBasisPoints = 500)))
        assertEquals("Add an ₫18,000 service charge", describe(AddCharge(ChargeKind.SERVICE_CHARGE, "", amountMinor = 18_000)))
        assertEquals("Add a ₫5,000 charge", describe(AddCharge(ChargeKind.OTHER, "Other", amountMinor = 5_000)))
        assertEquals("Minh paid the whole bill", describe(SetPayer(minh)))
        assertEquals("Beer comes to ₫96,000", describe(ItemAmount(beer, 96_000)))
        assertEquals("Beer: quantity 4 (₫120,000)", describe(ItemQuantity(beer, BigDecimal("4.00"))))
        assertEquals("An item that is not on the bill comes to ₫1,000", describe(ItemAmount("i9", 1_000)))
        assertEquals("Someone who is not on the bill paid the whole bill", describe(SetPayer("p9")))
    }

    @Test
    fun `a list of edits is described as they are applied, so refs have names and charges their amounts`() {
        val edits = listOf(
            ItemAmount(beer, 110_000),
            AddCharge(ChargeKind.TIP, "Tip", percentBasisPoints = 1_000),
            AddPerson("new1", "Zoe"),
            ItemSharers(beer, listOf(ME_ID, "new1")),
            AddPerson("new2", ""),
            SetPayer("new2"),
            SetPayer("new3"),
        )

        assertEquals(
            listOf(
                "Beer comes to ₫110,000",
                // 10% of the items after the beer's new amount: 310,000.
                "Add a 10% tip (₫31,000)",
                "Add Zoe",
                "Beer: shared by Dana and Zoe",
                "Add someone new",
                "Someone who is not on the bill paid the whole bill",
                "Someone who is not on the bill paid the whole bill",
            ),
            SplitEdits.describe(edits, bill, plan, "Dana", Locale.US),
        )
    }
}
