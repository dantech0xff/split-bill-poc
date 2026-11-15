package com.dantech.splitvibe.data.share

import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.groups.CurrencyDto
import com.dantech.splitvibe.data.payments.PaymentPayload
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable

// The shapes of the proxy's bill links, as `docs/proxy.md` describes them under "Bill links and the web pages".

/** What the web page shows of a bill: no photos, no text read from them, nothing but the lines and the people. */
@Serializable
data class BillProjection(
    val merchant: String?,
    val date: String?,
    val currency: CurrencyDto,
    val items: List<ProjectedItem>,
    val charges: List<ProjectedCharge>,
    val totalMinor: Long,
    val participants: List<ProjectedPerson>,
) {
    companion object {
        private const val MAX_TEXT = 80
        private const val MAX_NAME = 60
        private const val MAX_CHARGES = 20
        private const val OTHER_CHARGES = "other"

        /** The bill's lines that a link can show; a bill with none cannot be shared. */
        const val MAX_ITEMS = 200
        const val MAX_PEOPLE = 30

        /**
         * [saved] as the page shows it, with the user named [myName]. Charges past what a page shows
         * are added up into one line, so that the lines still come to the same total.
         */
        fun of(saved: SavedBill, myName: String): BillProjection {
            val bill = saved.bill
            val charges = bill.charges.map { ProjectedCharge(it.id, clean(it.label, MAX_TEXT).ifEmpty { "Charge" }, it.amountMinor) }
            return BillProjection(
                merchant = bill.merchant?.let { clean(it, MAX_TEXT) }?.takeIf { it.isNotEmpty() },
                date = bill.date?.takeIf(::isDay),
                currency = CurrencyDto.of(bill.currency),
                items = bill.items.map { item ->
                    ProjectedItem(
                        id = item.id,
                        name = clean(item.name, MAX_TEXT).ifEmpty { "Item" },
                        quantity = item.quantity.stripTrailingZeros().toPlainString(),
                        amountMinor = item.amountMinor,
                    )
                },
                charges = if (charges.size <= MAX_CHARGES) {
                    charges
                } else {
                    charges.take(MAX_CHARGES - 1) +
                        ProjectedCharge(OTHER_CHARGES, "Other charges", charges.drop(MAX_CHARGES - 1).sumOf { it.amountMinor })
                },
                totalMinor = bill.totalMinor,
                participants = saved.plan.participants.map { participant ->
                    val name = if (participant.isMe) myName else participant.name
                    ProjectedPerson(participant.id, clean(name, MAX_NAME).ifEmpty { if (participant.isMe) "" else "Someone" })
                },
            )
        }

        /** Whether [saved] fits on a link: it has lines, and not more lines or people than a page holds. */
        fun canShare(saved: SavedBill): Boolean = saved.bill.items.size in 1..MAX_ITEMS && saved.plan.participants.size <= MAX_PEOPLE

        /** [text] on one line without control characters, which the proxy refuses, and at most [max] long. */
        private fun clean(text: String, max: Int): String =
            text.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex(" {2,}"), " ").trim().take(max).trim()

        private fun isDay(text: String): Boolean = try {
            LocalDate.parse(text)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }
}

@Serializable
data class ProjectedItem(val id: String, val name: String, val quantity: String?, val amountMinor: Long)

@Serializable
data class ProjectedCharge(val id: String, val label: String, val amountMinor: Long)

@Serializable
data class ProjectedPerson(val id: String, val name: String)

/** The items one browser took, under a name its user typed. */
@Serializable
data class ShareClaim(val id: String, val name: String, val itemIds: List<String>, val updatedAt: Long? = null)

/** What one participant owes once the split is settled, and how to pay it. */
@Serializable
data class ShareAmount(val amountMinor: Long, val payment: PaymentPayload?)

/** A bill link as its owner sees it; the public view of the same link has fewer fields. */
@Serializable
data class ShareView(
    val id: String? = null,
    val billId: String? = null,
    val billRevision: Int? = null,
    val status: String,
    val revision: Long,
    val createdAt: Long? = null,
    val expiresAt: Long,
    val bill: BillProjection,
    val claims: List<ShareClaim> = emptyList(),
    val amounts: Map<String, ShareAmount>? = null,
    /** The participant who paid the whole bill, once the split is settled and one person did. */
    val paidBy: String? = null,
) {
    val isOpen: Boolean get() = status == OPEN
    val isLocked: Boolean get() = status == LOCKED

    companion object {
        const val OPEN = "open"
        const val LOCKING = "locking"
        const val LOCKED = "locked"
    }
}

/** A link and the address people open it at. */
data class OwnedShare(val share: ShareView, val url: String)

/** What the live connection of a link says. */
sealed interface LiveShare {
    /** The connection is open: changes come as they happen. */
    data object Connected : LiveShare

    /** The connection dropped and is being opened again; changes wait until it is. */
    data object Lost : LiveShare

    /** The link as it is now. */
    data class Changed(val share: ShareView) : LiveShare

    /** The link was revoked or is gone, and says nothing more. */
    data object Gone : LiveShare
}
