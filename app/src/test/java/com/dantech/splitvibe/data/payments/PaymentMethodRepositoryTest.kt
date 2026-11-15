package com.dantech.splitvibe.data.payments

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.data.bills.PaymentMethodEntity
import com.dantech.splitvibe.domain.payments.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentMethodRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private var now = 1_000L
    private val methods = PaymentMethodRepository(storage.database) { now++ }

    private val vietQr = PaymentMethod.VietQr("970416", "257678859", "DANA NGUYEN")
    private val payPal = PaymentMethod.PayPalMe("DanaN")
    private val sepa = PaymentMethod.SepaQr("Dana Nguyen", "DE89370400440532013000")

    @After
    fun tearDown() = storage.close()

    @Test
    fun `the first method is the main one, and the next main one is the oldest left`() = runBlocking {
        val first = methods.add(vietQr)
        val second = methods.add(payPal)
        val third = methods.add(sepa)
        assertEquals(listOf(first to true, second to false, third to false), methods.all.first().map { it.id to it.isMain })

        methods.makeMain(third)
        assertEquals(listOf(third, first, second), methods.all.first().map { it.id })

        methods.remove(third)
        assertEquals(listOf(first to true, second to false), methods.all.first().map { it.id to it.isMain })
    }

    @Test
    fun `a method is changed in place, and one with a problem is never saved`() = runBlocking<Unit> {
        val id = methods.add(vietQr)

        methods.replace(id, vietQr.copy(account = "0011004455667"))

        assertEquals("0011004455667", (methods.get(id)?.method as PaymentMethod.VietQr).account)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { methods.add(PaymentMethod.PastedLink("http://example.com")) } }
    }

    @Test
    fun `a method this version cannot read is left out`() = runBlocking {
        storage.database.paymentMethods().upsert(PaymentMethodEntity("future", """{"type":"qris","id":"x"}""", 1, isMain = true))
        methods.add(payPal)

        assertEquals(listOf(payPal), methods.all.first().map { it.method })
    }
}
