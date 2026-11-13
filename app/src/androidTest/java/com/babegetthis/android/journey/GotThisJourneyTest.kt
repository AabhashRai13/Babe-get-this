package com.babegetthis.android.journey

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.babegetthis.android.MainActivity
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.pin.data.PinStore
import com.babegetthis.android.core.ui.TestTags
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.domain.GotThisIds
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.testing.ResetAppStateRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

// Got This on one phone, as a guest: a bought-recently hint on a repeat item,
// and the "Usually bought around now" row on a list that is open and on one
// that was finished. The cross-member paths need a backend, which the
// instrumented suite never reaches; they are covered by the database and sync
// tests and by the two-phone checks done by hand before release.
//
// A guest lands in the 10% holdout by chance, and the holdout hides the whole
// feature, so the prefs are pinned before the activity starts.
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GotThisJourneyTest {

    @get:Rule(order = 0)
    val grant: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var database: AppDatabase

    @Inject lateinit var authStateManager: AuthStateManager

    @Inject lateinit var pinStore: PinStore

    @Inject lateinit var gotThisPrefs: GotThisPrefs

    @get:Rule(order = 2)
    val reset = ResetAppStateRule(hilt, { database }, { authStateManager }, { pinStore })

    // After the reset (which clears the prefs file) and before the activity.
    @get:Rule(order = 3)
    val featureVisible = object : ExternalResource() {
        override fun before() {
            gotThisPrefs.holdoutOn = false
            gotThisPrefs.killed = false
            gotThisPrefs.hintsEnabled = true
            gotThisPrefs.runningLowEnabled = true
            gotThisPrefs.usualsHiddenUntil = 0L
        }
    }

    @get:Rule(order = 4)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitGone(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitNoTag(tag: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun present(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun anItemCheckbox() = SemanticsMatcher("testTag starts with item checkbox prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)
            ?.startsWith(TestTags.ITEM_CHECKBOX_PREFIX) == true
    }

    private fun createList(name: String) {
        compose.onNodeWithText(
            if (present("Create your first list")) "Create your first list" else "Create list",
        ).performClick()
        awaitText("Type")
        compose.onNodeWithText("Type").performClick()
        awaitText("New List")
        compose.onNodeWithText("List name").performTextInput(name)
        compose.onNodeWithText("Create").performClick()
    }

    private fun addItem(name: String) {
        if (present("Add first item")) {
            compose.onNodeWithText("Add first item").performClick()
        } else {
            compose.onNodeWithTag(TestTags.ADD_ITEM_FAB).performClick()
        }
        awaitText("Add Item")
        compose.onNodeWithText("Item name").performTextInput(name)
        compose.onNodeWithText("Quantity (required)").performTextInput("1")
        compose.onNode(hasText("Add") and !hasTestTag(TestTags.ADD_ITEM_FAB)).performClick()
        awaitGone("Add Item")
        awaitText(name)
    }

    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun tickFirstItem(progress: String) {
        compose.onAllNodes(anItemCheckbox()).onFirst().performClick()
        awaitText(progress)
    }

    // Three weekly shops of the item, the last one 7 days ago: with a 7-day
    // rhythm it is due today.
    private fun seedWeeklyPurchases(name: String) = runBlocking {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val rows = listOf(21L, 14L, 7L).map { daysAgo ->
            val boughtAt = now - daysAgo * day
            val tickId = GotThisIds.tickId(UUID.randomUUID().toString(), boughtAt)
            PurchaseEntity(
                id = GotThisIds.guestPurchaseId(tickId),
                tickId = tickId,
                accountId = null,
                sourceItemId = UUID.randomUUID().toString(),
                name = name,
                itemKey = ItemKey.of(name),
                categoryId = "cat-dairy-eggs",
                boughtAt = boughtAt,
                deletedAt = null,
                pendingUpload = false,
            )
        }
        database.purchaseDao().upsert(rows)
    }

    @Test
    fun aTickedItemAddedAgainOnAnotherListShowsBoughtTodayAndCanBeRemoved() {
        createList("Weekly")
        awaitText("Add your first item to get started")
        addItem("Milk")
        tickFirstItem("All done!")
        back()
        awaitText("Create list")

        createList("Top up")
        awaitText("Add your first item to get started")
        addItem("milk")

        awaitTag("bought_hint")
        compose.onNodeWithTag("bought_hint").assertTextEquals("Bought today")
        compose.onNodeWithTag("bought_hint").performClick()
        awaitText("Remove from list")
        compose.onNodeWithText("Remove from list").performClick()

        awaitGone("milk")
        awaitNoTag("bought_hint")
    }

    @Test
    fun aDueRegularIsOfferedOnAnOpenListAndTappingTheChipAddsIt() {
        seedWeeklyPurchases("Eggs")
        createList("Weekend shop")
        awaitText("Add your first item to get started")

        awaitTag("usuals_row")
        awaitText("Usually bought around now")
        awaitText("+ Eggs")
        compose.onNodeWithText("+ Eggs").performClick()

        awaitText("Eggs")
        awaitNoTag("usual_chip")
    }

    @Test
    fun aChipOnAFinishedListMovesItBackToTheActiveTab() {
        createList("Done list")
        awaitText("Add your first item to get started")
        addItem("Bread")
        tickFirstItem("All done!")
        back()
        awaitText("Create list")

        // Finished, so it is under Completed and not under Active.
        compose.onNodeWithText("Completed").performClick()
        awaitText("Done list")
        compose.onNodeWithText("Active").performClick()
        awaitGone("Done list")
        compose.onNodeWithText("Completed").performClick()
        awaitText("Done list")

        seedWeeklyPurchases("Eggs")
        compose.onNodeWithText("Done list").performClick()
        awaitText("Usually bought around now")
        compose.onNodeWithText("+ Eggs").performClick()
        awaitText("Eggs")

        back()
        awaitText("Active")
        compose.onNodeWithText("Active").performClick()
        awaitText("Done list")
    }
}
