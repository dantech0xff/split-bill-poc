package com.dantech.splitvibe.data.bills

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Payment
import com.dantech.splitvibe.domain.SplitPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** How bills are kept on the phone: whole, one revision after another, found again by what is on them. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)

    /** A clock that moves on by one at every save, so the newest bill is always the one saved last. */
    private var now = 0L
    private val bills = BillRepository(storage.database, storage.people, storage.pages) { ++now }

    @After
    fun tearDown() = storage.close()

    @Test
    fun `each save is the next revision, with the shares worked out from what was saved`() = runBlocking {
        val first = bills.save(draft("pho-1", "Phở Hà Nội", 120_000))
        val second = bills.save(first.copy(bill = BillEditor.withItemAmount(first.bill, "i1", 150_000)))

        assertEquals(1, first.revision)
        assertEquals(2, second.revision)
        val stored = checkNotNull(bills.get("pho-1"))
        assertEquals(second, stored)
        assertEquals(150_000L, stored.shares.sumOf { it.totalMinor })
    }

    @Test
    fun `a copy read before another save is refused, so it cannot overwrite that save`() = runBlocking {
        val read = bills.save(draft("pho-1", "Phở Hà Nội", 120_000))
        bills.save(read.copy(bill = BillEditor.withMerchant(read.bill, "Phở Thìn")))

        try {
            bills.save(read.copy(bill = BillEditor.withMerchant(read.bill, "Phở Bát Đàn")))
            fail("A save from the older copy must be refused")
        } catch (e: BillConflictException) {
            assertEquals("pho-1", e.billId)
        }
        assertEquals("Phở Thìn", bills.get("pho-1")?.bill?.merchant)
    }

    @Test
    fun `bills are found by place, item and person, newest first`() = runBlocking {
        val pho = bills.save(draft("pho-1", "Phở Hà Nội", 120_000))
        bills.save(pho.copy(plan = pho.plan.withParticipantAdded("Minh Trần")))
        bills.save(draft("bbq-1", "Gogi House", 900_000, item = "Pork belly"))

        assertEquals(listOf("bbq-1", "pho-1"), bills.listings().first().map { it.id })
        assertEquals(listOf("pho-1"), bills.listings("pho").first().map { it.id })
        // Accents and case do not matter, and the people on a bill are found too.
        assertEquals(listOf("pho-1"), bills.listings("minh tran").first().map { it.id })
        assertEquals(listOf("pho-1"), bills.listings("noodles").first().map { it.id })
        assertTrue(bills.listings("sushi").first().isEmpty())
    }

    @Test
    fun `the people on a finished split are remembered for next time, not those on a draft`() = runBlocking {
        val bill = draft("pho-1", "Phở Hà Nội", 120_000)
        val draft = bills.save(bill.copy(plan = bill.plan.withParticipantAdded("Lna")))
        assertTrue("a name on a draft may still be a typo", storage.people.people.first().isEmpty())

        val saved = bills.save(draft.copy(status = BillStatus.FINAL, plan = draft.plan.withParticipantRenamed("p1", "Lan")))

        val lan = storage.people.people.first().single()
        assertEquals("Lan", lan.name)
        assertEquals(lan.id, saved.plan.participants.last().personId)
    }

    @Test
    fun `people who joined from a bill's link are not remembered for next time`() = runBlocking {
        val bill = draft("pho-2", "Phở Hà Nội", 120_000)
        val plan = bill.plan.withParticipantAdded("Lan").withParticipantAdded("Stranger")
        val stranger = plan.participants.last().id
        val link = ShareLink("share-1", "https://proxy.example/s/#TOKEN", added = mapOf("claim-x" to stranger))

        bills.save(bill.copy(status = BillStatus.FINAL, plan = plan, share = link))

        assertEquals(listOf("Lan"), storage.people.people.first().map { it.name })
    }

    @Test
    fun `a finished split changed into one that no longer adds up is saved as a draft`() = runBlocking {
        val bill = draft("pho-1", "Phở Hà Nội", 120_000)
        val finished = bills.save(bill.copy(status = BillStatus.FINAL))
        assertEquals(BillStatus.FINAL, finished.status)

        val broken = bills.save(finished.copy(plan = finished.plan.withPayments(listOf(Payment(ME_ID, 100_000)))))

        assertEquals(BillStatus.DRAFT, broken.status)
        assertEquals(BillStatus.DRAFT, bills.listings().first().single().status.let(BillStatus::valueOf))
    }

    @Test
    fun `deleting a bill deletes the photos of its pages, and a new reading drops the old photos`() = runBlocking {
        val page = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val old = storage.pages.save(page)
        val new = storage.pages.save(page)
        val saved = bills.save(draft("pho-1", "Phở Hà Nội", 120_000).copy(pages = listOf(old)))
        assertNotNull(storage.pages.load(old))

        bills.save(saved.copy(pages = listOf(new)))
        assertNull("the photo of the earlier reading goes", storage.pages.load(old))

        bills.delete("pho-1")
        assertNull(bills.get("pho-1"))
        assertNull(storage.pages.load(new))
    }

    @Test
    fun `a bill made only if absent is made once, by the first of two writers, and the list shows its category`() = runBlocking {
        val rent = draft("rent-dec", "Rent", 9_000_000).let { it.copy(bill = it.bill.copy(category = BillCategory.RENT)) }

        val made = bills.createIfAbsent(rent)
        val again = bills.createIfAbsent(rent.copy(bill = rent.bill.copy(merchant = "Rent again")))

        assertEquals(1, made?.revision)
        assertNull(again)
        assertEquals("Rent", bills.get("rent-dec")?.bill?.merchant)
        assertEquals(listOf("rent"), bills.listings().first().map { it.category })
    }

    @Test
    fun `the finished bills are read whole, the latest saved first, without the drafts`() = runBlocking {
        val pho = bills.save(draft("pho-1", "Phở Hà Nội", 120_000).copy(status = BillStatus.FINAL))
        bills.save(draft("bbq-1", "Gogi House", 900_000))
        val tea = bills.save(draft("tea-1", "Phúc Long", 60_000).copy(status = BillStatus.FINAL))

        assertEquals(listOf(tea, pho), bills.finished().first())
    }

    private fun draft(id: String, merchant: String, amount: Long, item: String = "Noodles"): SavedBill {
        val bill = BillEditor.withItemAdded(
            BillEditor.withMerchant(Bill.empty(BillCurrency("VND", 0)), merchant),
            name = item,
            amountMinor = amount,
        )
        return SavedBill(id = id, createdAt = 0, updatedAt = 0, bill = bill, plan = SplitPlan().withBill(bill))
    }
}
