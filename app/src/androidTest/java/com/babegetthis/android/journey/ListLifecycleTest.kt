package com.babegetthis.android.journey

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babegetthis.android.MainActivity
import com.babegetthis.android.core.ui.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.pin.data.PinStore
import com.babegetthis.android.testing.ResetAppStateRule
import javax.inject.Inject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The journey that would make the app pointless if it broke: make a list, put
// things in it, tick them off.
//
// Runs against the real MainActivity, the real navigation graph and a real
// in-memory Room database — only the network-facing modules are replaced. That
// is the point of this layer: the unit and Compose suites each verify one piece
// in isolation, and this checks they are genuinely wired together.
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ListLifecycleTest {

    // Order matters: Hilt must inject before the Activity launches.
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var authStateManager: AuthStateManager
    @Inject lateinit var pinStore: PinStore

    // Order 1: injects, then wipes state left by the previous test — before the
    // Activity rule below composes anything.
    @get:Rule(order = 1)
    val reset = ResetAppStateRule(hilt, { database }, { authStateManager }, { pinStore })

    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    // Explicit conditions, never sleeps — a fixed delay is how instrumented
    // suites become flaky, and the e2e spec forbids it outright.
    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Item ids are generated UUIDs, so a test cannot name a specific checkbox.
    // Matching on the tag prefix finds "the first item's checkbox" instead.
    private fun anItemCheckbox() = SemanticsMatcher("testTag starts with item checkbox prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)
            ?.startsWith(TestTags.ITEM_CHECKBOX_PREFIX) == true
    }

    private fun createList(name: String) {
        // Empty-state CTA when there are no lists yet, FAB once there are.
        val fresh = compose.onAllNodes(hasText("Create your first list"))
            .fetchSemanticsNodes().isNotEmpty()
        compose.onNodeWithText(if (fresh) "Create your first list" else "Create list").performClick()

        awaitText("Type")
        compose.onNodeWithText("Type").performClick()

        awaitText("New List")
        compose.onNodeWithText("List name").performTextInput(name)
        compose.onNodeWithText("Create").performClick()
    }

    private fun addItem(name: String, quantity: String = "1") {
        // The FAB only exists once the list has items — an empty list shows its
        // own button inside FirstItemPrompt instead. Same shape as createList.
        val fresh = compose.onAllNodes(hasText("Add first item"))
            .fetchSemanticsNodes().isNotEmpty()
        if (fresh) {
            compose.onNodeWithText("Add first item").performClick()
        } else {
            // The FAB and the dialog's confirm button both read "Add", so the FAB
            // is addressed by tag and the confirm by text once the dialog is up.
            compose.onNodeWithTag(TestTags.ADD_ITEM_FAB).performClick()
        }
        awaitText("Add Item")

        compose.onNodeWithText("Item name").performTextInput(name)
        compose.onNodeWithText("Quantity or notes (e.g. 2 large, slightly firm)")
            .performTextInput(quantity)
        // Both the FAB and the dialog's confirm read "Add", and once the list has
        // items BOTH are on screen — so the confirm is "the Add that isn't the
        // FAB". This is the ambiguity the FAB tag exists for; it just has to be
        // used from both sides.
        compose.onNode(hasText("Add") and !hasTestTag(TestTags.ADD_ITEM_FAB)).performClick()

        awaitText(name)
    }

    @Test
    fun createAListAddItemsAndTickThemOff() {
        createList("Groceries")
        awaitText("Add your first item to get started")

        addItem("Milk")
        addItem("Eggs")
        addItem("Bread")

        awaitText("0 of 3 picked up")

        // Tick the first ACTIVE item three times: a ticked row moves down into the
        // completed section, so the first match is always the next one still to do.
        repeat(3) {
            compose.onAllNodes(anItemCheckbox()).onFirst().performClick()
            compose.waitForIdle()
        }

        awaitText("All done!")
    }

    @Test
    fun aNewListStartsEmpty() {
        createList("Hardware")

        awaitText("Add your first item to get started")
        compose.onNodeWithText("Add your first item to get started").assertIsDisplayed()
    }

    @Test
    fun anItemCanBeRenamedFromItsRow() {
        createList("Groceries")
        awaitText("Add your first item to get started")
        addItem("Mlik")

        // Tapping the row opens the editor; tapping the checkbox would tick it.
        compose.onNodeWithText("Mlik").performClick()
        awaitText("Edit Item")

        compose.onNode(hasSetTextAction() and hasText("Mlik")).performTextReplacement("Milk")
        compose.onNodeWithText("Save").performClick()

        awaitText("Milk")
    }
}
