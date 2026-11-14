package com.dantech.splitvibe.data.contacts

/** Source of the user's in-app contacts. A real app would back this with its user directory API. */
interface ContactRepository {
    fun contacts(): List<Contact>
}

/** Seeded, fictional contacts so the proof of concept runs without a backend. */
class SeededContactRepository : ContactRepository {
    override fun contacts(): List<Contact> = SEED

    private companion object {
        val SEED = listOf(
            Contact("c01", "Trần Quang Minh", "Minh", "minh.tran"),
            Contact("c02", "Lê Thị Lan", "Lan", "lan.le"),
            Contact("c03", "Nguyễn Hoàng An", "An", "an.nguyen"),
            Contact("c04", "Phạm Thu Hà", "Hà", "ha.pham"),
            Contact("c05", "Đỗ Minh Khoa", "Khoa", "khoa.do"),
            Contact("c06", "Vũ Đức Huy", "Huy", "huy.vu"),
            Contact("c07", "Bùi Ngọc Mai", "Mai", "mai.bui"),
            Contact("c08", "Emma Wilson", "Emma", "emma.w"),
            Contact("c09", "Liam Carter", "Liam", "liam.carter"),
            Contact("c10", "Sofia Martinez", "Sofia", "sofia.m"),
            Contact("c11", "Noah Bennett", "Noah", "noah.b"),
            Contact("c12", "Emma Schneider", "Emma", "emma.schneider"),
        )
    }
}
