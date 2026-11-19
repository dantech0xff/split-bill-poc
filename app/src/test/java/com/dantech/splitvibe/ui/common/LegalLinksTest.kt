package com.dantech.splitvibe.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where the privacy policy and the terms of use are, for each address of the proxy a build can have. */
class LegalLinksTest {

    @Test
    fun `the pages are on the proxy's own address`() {
        assertEquals(
            LegalLinks("https://split-vibe-proxy.example.workers.dev/privacy", "https://split-vibe-proxy.example.workers.dev/terms"),
            LegalLinks.of("https://split-vibe-proxy.example.workers.dev"),
        )
        assertEquals(LegalLinks("http://127.0.0.1:8787/privacy", "http://127.0.0.1:8787/terms"), LegalLinks.of("http://127.0.0.1:8787"))
    }

    @Test
    fun `a build without the proxy's address has no links`() {
        assertNull(LegalLinks.of(""))
        assertNull(LegalLinks.of("not an address"))
    }
}
