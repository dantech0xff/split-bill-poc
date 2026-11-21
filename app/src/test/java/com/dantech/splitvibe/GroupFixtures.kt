package com.dantech.splitvibe

/**
 * A group as the proxy answers for it: a trip of four kept in baht, whose bills and payment back
 * come to the balances the domain's own fixture works out by hand (You +35.43, Minh +1,506.28,
 * Lan −1,262.50, An −279.21). An has no app yet and is a place kept under her name.
 */
object GroupFixtures {
    const val GROUP_ID = "8f9a2c1e-0000-4000-8000-000000000001"
    const val ME = "m-you"
    const val MINH = "m-minh"
    const val LAN = "m-lan"
    const val AN = "m-an"
    const val FAR_FUTURE = 4_102_444_800_000L

    /** Dana's VietQR account, the way to pay that requests carry unless a test gives another. */
    const val VIETQR = """{"type":"vietqr","bankBin":"970416","account":"257678859","accountName":"DANA NGUYEN"}"""

    fun groupState(
        myMemberId: String = ME,
        ownerMemberId: String = ME,
        claims: String = "[]",
        extraBills: String = "",
        settlements: String = """[{"id":"s1","fromMemberId":"$AN","toMemberId":"$ME","amountMinor":10000,""" +
            """"recordedByMemberId":"$ME","requestId":null,"createdAt":1763190000000}]""",
    ): String = """
        {"group":{"id":"$GROUP_ID","name":"Chiang Mai trip","currency":{"code":"THB","fractionDigits":2},
          "ownerMemberId":"$ownerMemberId","revision":9,"createdAt":1763100000000},
         "myMemberId":"$myMemberId",
         "members":[
           {"id":"$ME","name":"Dana","hasAccount":true,"isOwner":${ownerMemberId == ME},"joinedAt":1763100000000},
           {"id":"$MINH","name":"Minh","hasAccount":true,"isOwner":${ownerMemberId == MINH},"joinedAt":1763100100000},
           {"id":"$LAN","name":"Lan","hasAccount":true,"isOwner":false,"joinedAt":1763100200000},
           {"id":"$AN","name":"An","hasAccount":false,"isOwner":false,"joinedAt":1763100300000}],
         "claims":$claims,
         "bills":[
           ${bill("b1", ME, "Khao Soi Mae Sai", 120000, """{"$ME":30000,"$MINH":30000,"$LAN":30000,"$AN":30000}""", """{"$ME":120000}""")},
           ${bill("b2", ME, "Night market", 319500, """{"$ME":106500,"$MINH":106500,"$LAN":106500}""", """{"$MINH":319500}""")},
           ${bill(
        "b3",
        MINH,
        "Hostel",
        89050,
        """{"$ME":27400,"$MINH":20550,"$LAN":20550,"$AN":20550}""",
        """{"$LAN":54800,"$AN":34250}""",
    )},
           ${bill("b4", MINH, "Taxi", 35500, """{"$ME":11857,"$MINH":11822,"$AN":11821}""", """{"$ME":21300,"$AN":14200}""")},
           ${bill("b5", ME, "Elephant park", 48000, """{"$LAN":24000,"$AN":24000}""", """{"$ME":48000}""")}
           $extraBills],
         "settlements":$settlements,
         "log":[{"at":1763190000000,"kind":"settlement_recorded","memberId":"$ME","subjectId":"s1"},
                {"at":1763180000000,"kind":"bill_added","memberId":"$MINH","subjectId":"b4"}]}
    """.trimIndent()

    fun bill(id: String, owner: String, merchant: String, total: Long, shares: String, paid: String): String =
        """{"id":"$id","ownerMemberId":"$owner","revision":1,"updatedAt":1763150000000,""" +
            """"document":{"merchant":"$merchant"},"conversion":null,"totalMinor":$total,"shares":$shares,"paid":$paid}"""

    fun groupList(status: String = "member"): String =
        """{"groups":[{"id":"$GROUP_ID","name":"Chiang Mai trip","currency":{"code":"THB","fractionDigits":2},""" +
            """"revision":9,"myMemberId":${if (status == "member") "\"$ME\"" else "null"},"status":"$status"}]}"""

    fun request(
        id: String = "r1",
        role: String = "creditor",
        reportedPaidAt: Long? = null,
        confirmedPaidAt: Long? = null,
        method: String = VIETQR,
    ): String = """{"id":"$id","role":"$role","amountMinor":150000,"currency":{"code":"VND","fractionDigits":0},""" +
        """"payerName":"Minh","creditorName":"Dana","title":"Pho 24","method":$method,"groupId":null,"toMemberId":null,""" +
        """"billId":"bill-1","createdAt":1763190000000,"reportedPaidAt":${reportedPaidAt ?: "null"},""" +
        """"confirmedPaidAt":${confirmedPaidAt ?: "null"},"remindEveryDays":0,"debtorBound":true}"""
}
