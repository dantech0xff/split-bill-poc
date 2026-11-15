package com.dantech.splitvibe.data.share

import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.BillReconciler
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.Payments
import com.dantech.splitvibe.domain.SplitPlan
import java.math.BigDecimal

/** A bill at Pho 24 shared on a link, and the proxy's answers about that link, as `docs/proxy.md` shows them. */
object ShareFixtures {
    const val SHARE_ID = "5c1f8a3e-0000-4000-8000-000000000001"
    const val TOKEN = "AAAAAAAAAAAAAAAAAAAAAA"
    const val URL = "https://proxy.example/s/#$TOKEN"

    /** Pho 60,000, bun cha 50,000 and tea 30,000, with a service charge of 14,000, for the user, Minh and Lan; the user paid. */
    val saved = SavedBill(
        id = "bill-1",
        revision = 3,
        createdAt = 1,
        updatedAt = 1,
        bill = BillReconciler.reconcile(
            merchant = "Pho 24",
            date = "2026-11-14",
            currency = BillCurrency("VND", 0),
            items = listOf(
                BillItem("i1", "Pho bo", BigDecimal.ONE, 60_000, 60_000),
                BillItem("i2", "Bun cha", BigDecimal.ONE, 50_000, 50_000),
                BillItem("i3", "Tea", BigDecimal.ONE, 30_000, 30_000),
            ),
            charges = listOf(BillCharge("c1", "Service charge", ChargeKind.SERVICE_CHARGE, 14_000)),
            printedTotalMinor = 154_000,
            warnings = emptyList(),
        ),
        // The user paid the whole bill, as a bill read from a photo starts.
        plan = SplitPlan(
            participants = listOf(Participant.ME, Participant("p1", "Minh"), Participant("p2", "Lan")),
            payments = Payments.single(ME_ID, 154_000),
        ),
    )

    /** [saved] as the page shows it, for a user named Dana. */
    const val BILL = """{"merchant":"Pho 24","date":"2026-11-14","currency":{"code":"VND","fractionDigits":0},""" +
        """"items":[{"id":"i1","name":"Pho bo","quantity":"1","amountMinor":60000},""" +
        """{"id":"i2","name":"Bun cha","quantity":"1","amountMinor":50000},""" +
        """{"id":"i3","name":"Tea","quantity":"1","amountMinor":30000}],""" +
        """"charges":[{"id":"c1","label":"Service charge","amountMinor":14000}],"totalMinor":154000,""" +
        """"participants":[{"id":"me","name":"Dana"},{"id":"p1","name":"Minh"},{"id":"p2","name":"Lan"}]}"""

    /** Minh picked the pho, and a stranger the bun cha. */
    const val CLAIMS = """[{"id":"claim-minh","name":"minh","itemIds":["i1"],"updatedAt":2},""" +
        """{"id":"claim-x","name":"Stranger","itemIds":["i2"],"updatedAt":3}]"""

    fun view(status: String = "open", revision: Long = 1, claims: String = "[]", amounts: String = "null"): String =
        """{"id":"$SHARE_ID","billId":"bill-1","billRevision":3,"status":"$status","revision":$revision,""" +
            """"createdAt":1763190000000,"expiresAt":4102444800000,"bill":$BILL,"claims":$claims,"amounts":$amounts}"""

    fun owned(view: String = view()): String = """{"share":$view,"url":"$URL"}"""

    fun share(view: String): String = """{"share":$view}"""
}
