package com.dantech.splitvibe.data.share

import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.Participant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillProjectionTest {

    @Test
    fun `the page shows the bill's lines and people, with the user under their own name`() {
        val projection = BillProjection.of(ShareFixtures.saved, myName = "Dana")

        assertEquals(Json.parseToJsonElement(ShareFixtures.BILL), Json.encodeToJsonElement(BillProjection.serializer(), projection))
    }

    @Test
    fun `a user without a name is left for the page to call the bill's owner`() {
        val projection = BillProjection.of(ShareFixtures.saved, myName = " ")

        assertEquals("", projection.participants.first().name)
    }

    @Test
    fun `a date that is not a day and a blank merchant are left out`() {
        val bill = ShareFixtures.saved.bill.copy(merchant = "  ", date = "14/11/2026")

        val projection = BillProjection.of(ShareFixtures.saved.copy(bill = bill), myName = "Dana")

        assertNull(projection.merchant)
        assertNull(projection.date)
    }

    @Test
    fun `charges past what a page shows are added up into one line, so the lines keep their total`() {
        val charges = (1..25).map { BillCharge("c$it", "Charge $it", ChargeKind.OTHER, 100L * it) }
        val saved = ShareFixtures.saved.copy(bill = ShareFixtures.saved.bill.copy(charges = charges))

        val projection = BillProjection.of(saved, myName = "Dana")

        assertEquals(20, projection.charges.size)
        assertEquals("Other charges", projection.charges.last().label)
        assertEquals(charges.sumOf { it.amountMinor }, projection.charges.sumOf { it.amountMinor })
        assertEquals((20..25).sumOf { 100L * it }, projection.charges.last().amountMinor)
    }

    @Test
    fun `only a bill with lines, and not more lines or people than a page holds, goes on a link`() {
        assertTrue(BillProjection.canShare(ShareFixtures.saved))
        assertFalse(BillProjection.canShare(ShareFixtures.saved.copy(bill = ShareFixtures.saved.bill.copy(items = emptyList()))))
        val crowd = ShareFixtures.saved.plan.copy(participants = (0..30).map { Participant("p$it", "Person $it") })
        assertFalse(BillProjection.canShare(ShareFixtures.saved.copy(plan = crowd)))
    }
}
