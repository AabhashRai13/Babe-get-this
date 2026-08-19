package com.babegetthis.android.journey

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babegetthis.android.MainActivity
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.pin.data.PinStore
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.ui.TestTags
import com.babegetthis.android.testing.RecordingAnalytics
import com.babegetthis.android.testing.RecordingCrashReporter
import com.babegetthis.android.testing.ResetAppStateRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

// What telemetry actually records when a person uses the app.
//
// Every other telemetry test asserts against a ViewModel in isolation. This one
// drives the real Activity, the real navigation graph and a real database, then
// reads back the payloads that would have gone to Firebase — so it catches the
// failures unit tests structurally cannot: an event wired to a screen that never
// calls it, a screen name carrying a path argument, an item name reaching a
// parameter through a route the catalog never anticipated.
//
// The privacy assertions here are the ones worth having. They use deliberately
// distinctive item names, then assert those strings appear in NOTHING that would
// be transmitted.
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TelemetryJourneyTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var authStateManager: AuthStateManager
    @Inject lateinit var pinStore: PinStore
    @Inject lateinit var analytics: RecordingAnalytics
    @Inject lateinit var crashReporter: RecordingCrashReporter

    @get:Rule(order = 1)
    val reset = ResetAppStateRule(hilt, { database }, { authStateManager }, { pinStore })

    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    // Names chosen to be unmistakable in a payload dump, and to be the kind of
    // thing a person would not want leaving their phone.
    private val secretList = "Zylthaqu Clinic Run"
    private val secretItem = "Pregnancy Test Qxwvz"

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun anItemCheckbox() = SemanticsMatcher("testTag starts with item checkbox prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)
            ?.startsWith(TestTags.ITEM_CHECKBOX_PREFIX) == true
    }

    private fun createList(name: String) {
        val fresh = compose.onAllNodes(hasText("Create your first list"))
            .fetchSemanticsNodes().isNotEmpty()
        compose.onNodeWithText(if (fresh) "Create your first list" else "Create list").performClick()
        awaitText("Type")
        compose.onNodeWithText("Type").performClick()
        awaitText("New List")
        compose.onNodeWithText("List name").performTextInput(name)
        compose.onNodeWithText("Create").performClick()
    }

    private fun addItem(name: String) {
        val fresh = compose.onAllNodes(hasText("Add first item")).fetchSemanticsNodes().isNotEmpty()
        if (fresh) {
            compose.onNodeWithText("Add first item").performClick()
        } else {
            compose.onNodeWithTag(TestTags.ADD_ITEM_FAB).performClick()
        }
        awaitText("Add Item")
        compose.onNodeWithText("Item name").performTextInput(name)
        compose.onNodeWithText("Quantity or notes (e.g. 2 large, slightly firm)")
            .performTextInput("1")
        compose.onNode(hasText("Add") and !hasTestTag(TestTags.ADD_ITEM_FAB)).performClick()
        awaitText(name)
    }

    // -- The privacy contract --

    @Test
    fun noListOrItemNameEverReachesAnalytics() {
        createList(secretList)
        awaitText("Add your first item to get started")
        addItem(secretItem)
        compose.waitForIdle()

        val transmitted = analytics.allTransmittedStrings()
        assertTrue("nothing was recorded — the journey did not exercise telemetry",
            transmitted.isNotEmpty())

        // Substring, not equality: a leak would more likely arrive embedded in a
        // route or a composed string than as a bare parameter value.
        transmitted.forEach { value ->
            assertFalse("list name leaked in '$value'", value.contains("Zylthaqu"))
            assertFalse("item name leaked in '$value'", value.contains("Qxwvz"))
        }
    }

    @Test
    fun screenNamesAreEnumLabelsNotRoutes() {
        createList(secretList)
        awaitText("Add your first item to get started")
        compose.waitForIdle()

        val screens = analytics.mapped
            .filter { it.name == "screen_view" }
            .mapNotNull { it.text["screen_name"] }

        assertTrue("no screen_view recorded", screens.isNotEmpty())
        // The SHOPPING_ITEMS route is "shopping_items/{listId}/{listName}".
        // Anything with a slash means a raw route got through.
        screens.forEach {
            assertFalse("raw route leaked as screen name: $it", it.contains("/"))
            assertFalse("route argument leaked: $it", it.contains("{"))
        }
        assertTrue(screens.contains("shopping_items"))
    }

    // -- The funnels, end to end --

    @Test
    fun addingAnItemRecordsItemAddedAndActivationExactlyOnce() {
        createList("Groceries")
        awaitText("Add your first item to get started")
        analytics.clear()

        addItem("Milk")
        addItem("Eggs")
        compose.waitForIdle()

        assertEquals(2, analytics.events.count { it is AnalyticsEvent.ItemAdded })
        // The activation marker is what makes this once-per-user rather than
        // once-per-item.
        assertEquals(1, analytics.events.count { it is AnalyticsEvent.FirstItemAdded })
    }

    @Test
    fun completingAListRecordsTheTripOnTheTransitionOnly() {
        createList("Groceries")
        awaitText("Add your first item to get started")
        addItem("Milk")
        addItem("Eggs")
        analytics.clear()

        repeat(2) {
            compose.onAllNodes(anItemCheckbox()).onFirst().performClick()
            compose.waitForIdle()
        }
        awaitText("All done!")

        assertEquals(2, analytics.events.count { it == AnalyticsEvent.ItemCheckedOff })
        // Once, on the transition — not on every emission of an all-done list.
        assertEquals(1, analytics.events.count { it is AnalyticsEvent.ListCompleted })
        assertEquals(1, analytics.events.count { it is AnalyticsEvent.FirstListCompleted })
    }

    @Test
    fun anOwnersOwnEditsAreNotReportedAsAJoinersFirstEdit() {
        // This device never joined anything, so the joiner event must not fire
        // no matter how much the list is edited.
        createList("Groceries")
        awaitText("Add your first item to get started")
        addItem("Milk")
        compose.onAllNodes(anItemCheckbox()).onFirst().performClick()
        compose.waitForIdle()

        assertEquals(0, analytics.events.count {
            it == AnalyticsEvent.SharedListFirstEditByJoiner
        })
    }

    // -- Crash reporting --

    @Test
    fun ordinaryUsageProducesNoNonFatals() {
        // Nothing here is a defect, so nothing should reach Crashlytics. A
        // regression that starts reporting routine outcomes shows up here first.
        createList("Groceries")
        awaitText("Add your first item to get started")
        addItem("Milk")
        compose.onAllNodes(anItemCheckbox()).onFirst().performClick()
        compose.waitForIdle()

        assertEquals(
            "non-fatals from a clean journey: ${crashReporter.reported}",
            0,
            crashReporter.reported.size,
        )
    }

    @Test
    fun breadcrumbsAndKeysCarryNoUserContent() {
        createList(secretList)
        awaitText("Add your first item to get started")
        addItem(secretItem)
        compose.waitForIdle()

        assertTrue("no breadcrumbs recorded", crashReporter.breadcrumbs.isNotEmpty())
        (crashReporter.breadcrumbs + crashReporter.keys.values).forEach {
            assertFalse("leak in crash context: $it", it.contains("Zylthaqu"))
            assertFalse("leak in crash context: $it", it.contains("Qxwvz"))
        }
    }

    @Test
    fun crashContextIdentifiesWhereTheUserWasAndWhatStateTheyWereIn() {
        createList("Groceries")
        compose.waitForIdle()

        // The state a triaging engineer would otherwise have to ask the user for.
        //
        // `flavor` and `auth_state` are deliberately NOT asserted here: they are
        // set by TelemetryContext.onAppStart(), which runs from
        // BabeGetThisApp.onCreate — and the instrumented suite swaps in
        // HiltTestApplication (see HiltTestRunner), so that never executes. The
        // keys below come from the navigation hook in MainActivity, which does.
        val keys = crashReporter.keys.keys.map { it.key }
        assertTrue("last_screen missing, keys=$keys", keys.contains("last_screen"))
        assertTrue("network_state missing, keys=$keys", keys.contains("network_state"))
    }
}
