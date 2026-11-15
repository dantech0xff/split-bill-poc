package com.dantech.splitvibe.domain.payments

/** A bank or wallet that takes transfers through NAPAS, by the code VietQR identifies it with. */
data class VietQrBank(val bin: String, val shortName: String)

/**
 * The banks and wallets a VietQR code can send money to. NAPAS does not publish the list; it is
 * taken from the open-source vietnam-qr-pay library (MIT licence, github.com/xuannghia/vietnam-qr-pay),
 * leaving out what it marks as unsupported or replaced.
 */
object VietQrBanks {

    val ALL: List<VietQrBank> = listOf(
        VietQrBank("970425", "AB Bank"),
        VietQrBank("970416", "ACB"),
        VietQrBank("970405", "Agribank"),
        VietQrBank("970409", "BacA Bank"),
        VietQrBank("970438", "BaoViet Bank"),
        VietQrBank("970418", "BIDV"),
        VietQrBank("970454", "BVBank"),
        VietQrBank("546034", "CAKE by VPBank"),
        VietQrBank("970444", "CB Bank"),
        VietQrBank("422589", "CIMB Bank"),
        VietQrBank("970446", "Co-op Bank"),
        VietQrBank("796500", "DBS Bank"),
        VietQrBank("970431", "Eximbank"),
        VietQrBank("970408", "GPBank"),
        VietQrBank("970437", "HDBank"),
        VietQrBank("970442", "HongLeong Bank"),
        VietQrBank("458761", "HSBC Vietnam"),
        VietQrBank("970456", "IBK HCM"),
        VietQrBank("970455", "IBK HN"),
        VietQrBank("970434", "Indovina Bank"),
        VietQrBank("668888", "Kasikornbank"),
        VietQrBank("970452", "KienlongBank"),
        VietQrBank("970463", "Kookmin Bank HCM"),
        VietQrBank("970462", "Kookmin Bank HN"),
        VietQrBank("963369", "Liobank"),
        VietQrBank("970449", "LPBank"),
        VietQrBank("970422", "MB Bank"),
        VietQrBank("970414", "MBV"),
        VietQrBank("971025", "MoMo"),
        VietQrBank("970426", "MSB"),
        VietQrBank("970428", "Nam A Bank"),
        VietQrBank("970419", "NCB Bank"),
        VietQrBank("801011", "Nonghyup Bank"),
        VietQrBank("970448", "OCB Bank"),
        VietQrBank("970430", "PG Bank"),
        VietQrBank("970439", "Public Bank Vietnam"),
        VietQrBank("970412", "PVcomBank"),
        VietQrBank("970403", "Sacombank"),
        VietQrBank("970400", "SaigonBank"),
        VietQrBank("970429", "SCB"),
        VietQrBank("970440", "SeABank"),
        VietQrBank("970443", "SHB"),
        VietQrBank("970424", "Shinhan Bank"),
        VietQrBank("970410", "Standard Chartered Vietnam"),
        VietQrBank("970407", "Techcombank"),
        VietQrBank("963388", "Timo"),
        VietQrBank("970423", "TPBank"),
        VietQrBank("546035", "Ubank by VPBank"),
        VietQrBank("970458", "United Overseas Bank Vietnam"),
        VietQrBank("970441", "VIB"),
        VietQrBank("970427", "VietABank"),
        VietQrBank("970433", "VietBank"),
        VietQrBank("970436", "Vietcombank"),
        VietQrBank("970415", "VietinBank"),
        VietQrBank("970421", "VietNgaBank"),
        VietQrBank("970406", "Vikki Bank"),
        VietQrBank("970432", "VPBank"),
        VietQrBank("970457", "Woori Bank"),
    )

    fun byBin(bin: String): VietQrBank? = ALL.firstOrNull { it.bin == bin }
}
