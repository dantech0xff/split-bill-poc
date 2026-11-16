package com.dantech.splitvibe.ui

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.domain.Recurrence
import com.dantech.splitvibe.notifications.Notices
import java.time.LocalDate
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
        assertEquals(AppLink.JoinGroup(token), AppLink.of(view("$proxy/g/#$token"), proxy))
        assertEquals(AppLink.OpenRequest(token), AppLink.of(view("$proxy/r/#$token"), proxy))
    }

    @Test
    fun `links anywhere else, or of another shape, open nothing`() {
        listOf(
            "https://evil.example/g/#$token",
            "http://split-vibe-proxy.example.workers.dev/g/#$token",
            "https://someone@split-vibe-proxy.example.workers.dev/g/#$token",
            "$proxy/g/#short",
            "$proxy/g/more/#$token",
            "$proxy/x/#$token",
            "$proxy/g/$token",
            "$proxy/g/#${token.dropLast(2)}%2F",
        ).forEach { assertNull(it, AppLink.of(view(it), proxy)) }
        assertNull(AppLink.of(Intent(Intent.ACTION_SEND).setData(Uri.parse("$proxy/g/#$token")), proxy))
        assertNull(AppLink.of(view("$proxy/g/#$token"), ""))
    }

    @Test
    fun `a tapped notice opens the requests, or the group a claim is about`() {
        assertEquals(AppLink.Requests, AppLink.of(Intent().putExtra(Notices.EXTRA_KIND, "reported"), proxy))
        assertEquals(
            AppLink.Group("8f9a2c1e-0000-4000-8000-000000000001"),
            AppLink.of(
                Intent().putExtra(Notices.EXTRA_KIND, "claim").putExtra(Notices.EXTRA_GROUP_ID, "8f9a2c1e-0000-4000-8000-000000000001"),
                proxy,
            ),
        )
    }

    @Test
    fun `a notice the system showed while the app was away opens the same, and plain extras open nothing`() {
        val fromSystem = Intent().putExtra(
            "google.message_id",
            "0:1",
        ).putExtra("type", "claim").putExtra("groupId", "8f9a2c1e-0000-4000-8000-000000000001")
        assertEquals(AppLink.Group("8f9a2c1e-0000-4000-8000-000000000001"), AppLink.of(fromSystem, proxy))
        assertEquals(AppLink.Requests, AppLink.of(Intent().putExtra("google.message_id", "0:2").putExtra("type", "reminder"), proxy))
        assertNull(AppLink.of(Intent().putExtra("type", "claim").putExtra("groupId", "8f9a2c1e-0000-4000-8000-000000000001"), proxy))
        // A group id that is not one opens the requests, never a call to another address.
        assertEquals(
            AppLink.Requests,
            AppLink.of(Intent().putExtra(Notices.EXTRA_KIND, "claim").putExtra(Notices.EXTRA_GROUP_ID, "../account"), proxy),
        )
    }

    @Test
    fun `a tapped notice of a recurring bill's draft opens that bill, and nothing else does`() {
        val draft = Recurrence.draftId("8f9a2c1e-0000-4000-8000-000000000001", LocalDate.of(2026, 12, 1))
        val notice = { id: String? -> Intent().putExtra(Notices.EXTRA_KIND, Notices.KIND_DRAFT).putExtra(Notices.EXTRA_BILL_ID, id) }

        assertEquals(AppLink.OpenBill(draft), AppLink.of(notice(draft), proxy))
        // Any app can send these extras, so only what has the shape of a bill's id opens anything.
        listOf(null, "", "../bills", "$draft/x").forEach { assertNull(it, AppLink.of(notice(it), proxy)) }
        // A push notice of that kind, from the system, carries no bill.
        assertNull(AppLink.of(Intent().putExtra("google.message_id", "0:3").putExtra("type", Notices.KIND_DRAFT), proxy))
    }

    private fun view(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))
}
