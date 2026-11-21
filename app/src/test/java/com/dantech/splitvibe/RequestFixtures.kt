package com.dantech.splitvibe

import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestView

/**
 * The payment requests of the approved design of the Requests screen, as the proxy lists them: the
 * user is to pay Dana ₫150,000 for Pho 24; Minh says he paid ₫400,680 for Quán Ốc Ba Miền, Lan has
 * not paid her ₫250,991 for it yet, and Minh's ₫150,000 for Pho 24 is paid and confirmed. Friends
 * owe the user ₫651,671, and the user owes ₫150,000. Each request carries Dana's VietQR account.
 */
object RequestFixtures {

    /** The one request the user is to pay, which Dana sent to the user, who goes by Dana too, as on the design. */
    val toPay: String = request("r1", RequestView.DEBTOR, 150_000, payer = "Dana", title = "Pho 24")

    /** The same request to pay, by a link that Dana pasted in place of her bank account. */
    val toPayByLink: RequestView = view(
        request(
            "r1",
            RequestView.DEBTOR,
            150_000,
            payer = "Dana",
            title = "Pho 24",
            method = """{"type":"link","url":"https://revolut.me/splitvibedemo"}""",
        ),
    )

    /** The requests the user made, the paid one last. */
    val asked: List<String> = listOf(
        request("r2", RequestView.CREDITOR, 400_680, payer = "Minh", title = QUAN_OC, reportedPaidAt = REPORTED_AT),
        request("r3", RequestView.CREDITOR, 250_991, payer = "Lan", title = QUAN_OC),
        request(
            "r4",
            RequestView.CREDITOR,
            150_000,
            payer = "Minh",
            title = "Pho 24",
            reportedPaidAt = REPORTED_AT,
            confirmedAt = CONFIRMED_AT,
        ),
    )

    /** Every request of the design, as the proxy answers them, newest first. */
    val boardJson: List<String> = listOf(toPay) + asked

    /** The same requests as the app reads them. */
    val board: List<RequestView> = boardJson.map(::view)

    /** The status of the one request that is paid and confirmed, the last in the list. */
    const val PAID = "Paid and confirmed"

    /** [json], a request as the proxy answers it, as the app reads it. */
    fun view(json: String): RequestView = ProxyCache.json.decodeFromString(RequestView.serializer(), json)

    /**
     * A request [id] of [amountMinor] dong, or of [currency], as the proxy answers it: one the user
     * made, for [payer] to pay, or one sent to the user by Dana, for [title].
     */
    fun request(
        id: String,
        role: String,
        amountMinor: Long,
        payer: String,
        title: String,
        reportedPaidAt: Long? = null,
        confirmedAt: Long? = null,
        currency: String = """{"code":"VND","fractionDigits":0}""",
        method: String = GroupFixtures.VIETQR,
    ): String = GroupFixtures.request(id = id, role = role, reportedPaidAt = reportedPaidAt, confirmedPaidAt = confirmedAt, method = method)
        .replace("\"amountMinor\":150000", "\"amountMinor\":$amountMinor")
        .replace("\"currency\":{\"code\":\"VND\",\"fractionDigits\":0}", "\"currency\":$currency")
        .replace("\"payerName\":\"Minh\"", "\"payerName\":\"$payer\"")
        .replace("\"title\":\"Pho 24\"", "\"title\":\"$title\"")

    private const val QUAN_OC = "Quán Ốc Ba Miền"
    private const val REPORTED_AT = 1_763_200_000_000
    private const val CONFIRMED_AT = 1_763_210_000_000
}
