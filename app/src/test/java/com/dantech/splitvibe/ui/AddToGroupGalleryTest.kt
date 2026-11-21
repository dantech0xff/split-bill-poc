package com.dantech.splitvibe.ui

import android.content.Context
import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.R
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.fx.FxRepository
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.Payment
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.text
import com.dantech.splitvibe.ui.groups.AddToGroupSheet
import com.dantech.splitvibe.ui.groups.AddToGroupViewModel
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the sheet that adds a split bill to a group: a dinner in dollars, added to the fixtures' trip
 * kept in baht, with each person on the bill matched to a member and the total at the day's rate, as
 * the stand-in for the proxy answers. It compares the screen with the reference image in
 * `src/test/screenshots`; record a new image after an intended change with `-Pscreenshots=record`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class AddToGroupGalleryTest {

    /** Closed only once the sheet is gone: the compose rule inside this one removes it first. */
    @get:Rule(order = 0)
    val outsideTheSheet = object : ExternalResource() {
        override fun after() {
            server.shutdown()
            storage.close()
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()

    @Test
    fun `adding a bill in dollars to a group kept in baht`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.path) {
                    "/v1/groups" -> GroupFixtures.groupList()
                    "/v1/fx-rates" -> FX_RATES
                    else -> GroupFixtures.groupState()
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        val proxy = TestProxy(server, context).apply { account.saveSession(Session("gallery-session", GroupFixtures.FAR_FUTURE)) }
        val cache = ProxyCache(storage.database.proxyCache())
        val viewModel = AddToGroupViewModel(
            groups = GroupRepository(proxy.api, cache, proxy.account),
            requests = RequestRepository(proxy.api, cache, proxy.account),
            fx = FxRepository(proxy.api, File(context.cacheDir, "fx-add-to-group-gallery.json")),
        ).also(storage::closeWith)
        compose.setContent {
            SplitVibeTheme {
                // Rippling is left out, so the picture does not depend on where a test last touched.
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    AddToGroupSheet(saved = DINNER, viewModel = viewModel, nameOf = ::nameOf, onLinked = {}, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()

        // The trip, as the user picks it among their groups.
        viewModel.choose(DINNER, GroupFixtures.GROUP_ID)
        awaitOnMain { viewModel.state.value.let { it.group != null && !it.working } }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/05-add-to-group.png"))
    }

    /** The name the bill gives the person [id]: the user is "You", the others are as typed. */
    private fun nameOf(id: String): UiText =
        if (id == ME_ID) text(R.string.common_you) else UiText.Raw(DINNER.plan.participants.first { it.id == id }.name)

    private companion object {
        /** A dinner of $42.00 that the user paid, shared with Minh, split with before, and Lan. */
        val DINNER = SavedBill(
            id = "bill-dinner",
            status = BillStatus.FINAL,
            createdAt = 1,
            updatedAt = 1,
            bill = Bill(
                "Dinner at Khao Soi Mae Sai",
                "2026-11-14",
                BillCurrency("USD", 2),
                emptyList(),
                emptyList(),
                printedTotalMinor = 4_200,
            ),
            plan = SplitPlan(
                participants = listOf(Participant.ME, Participant("p1", "Minh", personId = "local-person-1"), Participant("p2", "Lan")),
                payments = listOf(Payment(ME_ID, 4_200)),
            ),
        )

        /** The day's rates of the stand-in for the proxy: 1.25 dollars and 44.375 baht to the euro, so 35.5 baht to the dollar. */
        const val FX_RATES = """{"base":"EUR","source":"Frankfurter","fetchedAt":1,"rates":""" +
            """{"EUR":{"rate":1,"date":"2026-11-14"},"USD":{"rate":1.25,"date":"2026-11-14"},"THB":{"rate":44.375,"date":"2026-11-14"}}}"""
    }
}
