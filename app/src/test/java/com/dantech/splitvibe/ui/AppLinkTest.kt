package com.dantech.splitvibe.ui

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.notifications.Notices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLinkTest {

    private val proxy = "https://split-vibe-proxy.example.workers.dev"
    private val token = "AbCdEfGhIjKlMnOpQrStUv"

    @Test
    fun `invite and request links on the proxy's address open their screens`() {
        assertEquals(AppLink.JoinGroup(token), AppLink.of(view("$proxy/g/$token"), proxy))
        assertEquals(AppLink.OpenRequest(token), AppLink.of(view("$proxy/r/$token"), proxy))
    }

    @Test
    fun `links anywhere else, or of another shape, open nothing`() {
        listOf(
            "https://evil.example/g/$token",
            "http://split-vibe-proxy.example.workers.dev/g/$token",
            "https://someone@split-vibe-proxy.example.workers.dev/g/$token",
            "$proxy/g/short",
            "$proxy/g/$token/more",
            "$proxy/x/$token",
            "$proxy/g/${token.dropLast(2)}%2F..",
        ).forEach { assertNull(it, AppLink.of(view(it), proxy)) }
        assertNull(AppLink.of(Intent(Intent.ACTION_SEND).setData(Uri.parse("$proxy/g/$token")), proxy))
        assertNull(AppLink.of(view("$proxy/g/$token"), ""))
    }

    @Test
    fun `a tapped notice opens the requests, or the group a claim is about`() {
        assertEquals(AppLink.Requests, AppLink.of(Intent().putExtra(Notices.EXTRA_KIND, "reported"), proxy))
        assertEquals(
            AppLink.Group("group-1"),
            AppLink.of(Intent().putExtra(Notices.EXTRA_KIND, "claim").putExtra(Notices.EXTRA_GROUP_ID, "group-1"), proxy),
        )
    }

    private fun view(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))
}
