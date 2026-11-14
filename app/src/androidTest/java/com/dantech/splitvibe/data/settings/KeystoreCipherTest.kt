package com.dantech.splitvibe.data.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreCipherTest {

    private val cipher = KeystoreCipher(alias = "split_bill_test")

    @Test
    fun secretSurvivesEncryptionAndIsNotStoredInTheClear() {
        val secret = "not-a-real-key-0123456789"

        val stored = cipher.encrypt(secret)

        assertFalse(stored.contains(secret))
        assertNotEquals("each encryption must use a fresh nonce", stored, cipher.encrypt(secret))
        assertEquals(secret, cipher.decrypt(stored))
    }

    @Test
    fun damagedValueDecryptsToNothingInsteadOfCrashing() {
        val stored = cipher.encrypt("secret")
        val damaged = stored.dropLast(4) + "AAAA"

        assertNull(cipher.decrypt(damaged))
        assertNull(cipher.decrypt("not base64 !!"))
        assertNull(cipher.decrypt(""))
    }
}
