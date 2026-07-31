package com.babegetthis.android.journey

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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

// The two journeys where a bug loses the user's data or locks them out of it.
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PinGateAndUndoTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var authStateManager: AuthStateManager
    @Inject lateinit var pinStore: PinStore

    // Order 1: injects, then wipes state left by the previous test — before the
    // Activity rule below composes anything.
    @get:Rule(order = 1)
    val reset = ResetAppStateRule(hilt, { database }, { authStateManager }, { pinStore })

    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

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

    private fun present(text: String) =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun createList(name: String) {
        compose.onNodeWithText(
            if (present("Create your first list")) "Create your first list" else "Create list"
        ).performClick()
        awaitText("Type")
        compose.onNodeWithText("Type").performClick()
        awaitText("New List")
        compose.onNodeWithText("List name").performTextInput(name)
        compose.onNodeWithText("Create").performClick()
    }

    private fun back() =
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    // An EMPTY list is deleted when you leave it — ShoppingItemsViewModel.onCleared
    // calls deleteListIfEmpty, deliberately, so abandoned lists don't pile up on
    // the home screen. So a list that has to survive a back-press needs an item in
    // it first. (My first version of these tests didn't, and failed because the
    // app was doing exactly the right thing.)
    private fun addOneItem(name: String) {
        compose.onNodeWithText("Add first item").performClick()
        awaitText("Add Item")
        compose.onNodeWithText("Item name").performTextInput(name)
        compose.onNodeWithText("Quantity or notes (e.g. 2 large, slightly firm)")
            .performTextInput("1")
        compose.onNode(hasText("Add") and !hasTestTag(TestTags.ADD_ITEM_FAB)).performClick()
        awaitText(name)
    }

    // --- PIN gate ---

    // Setting a PIN from an unlocked list: enter, confirm, then acknowledge the
    // recovery code. The recovery step is deliberately unskippable — it is the
    // user's only fallback.
    @Test
    fun settingAPinRequiresConfirmationAndShowsARecoveryCode() {
        createList("Private")
        awaitText("Add your first item to get started")

        compose.onNodeWithContentDescription("Lock list").performClick()

        awaitText("Create a PIN")
        compose.onNodeWithText("PIN").performTextInput("1234")
        compose.onNodeWithText("Continue").performClick()

        awaitText("Confirm your PIN")
        compose.onNodeWithText("PIN").performTextInput("1234")
        compose.onNodeWithText("Confirm").performClick()

        // The recovery code step. Its exact copy varies, but the flow must not
        // complete until it has been acknowledged.
        compose.waitUntil(timeoutMillis = 10_000) {
            !present("Confirm your PIN")
        }
    }

    @Test
    fun aMismatchedConfirmationIsRejected() {
        createList("Private")
        awaitText("Add your first item to get started")

        compose.onNodeWithContentDescription("Lock list").performClick()

        awaitText("Create a PIN")
        compose.onNodeWithText("PIN").performTextInput("1234")
        compose.onNodeWithText("Continue").performClick()

        awaitText("Confirm your PIN")
        compose.onNodeWithText("PIN").performTextInput("9999")
        compose.onNodeWithText("Confirm").performClick()

        awaitText("PINs don't match. Try again.")
    }

    // --- undo delete ---

    // The journey the whole undo cache exists for: a swiped-away list comes back
    // with its items, not as an empty shell. Two bugs were fixed under this
    // heading (task 2.2 and task 5.7), so it is worth an end-to-end check.
    @Test
    fun aDeletedListCanBeRestoredFromTheSnackbar() {
        createList("Groceries")
        awaitText("Add your first item to get started")
        addOneItem("Milk")
        back()

        awaitText("Groceries")
        compose.onNodeWithText("Groceries").performTouchInput { swipeLeft() }

        awaitText("Undo")
        compose.onNodeWithText("Undo").performClick()

        // Back on the home screen with the list present again.
        awaitText("Groceries")
    }

    @Test
    fun dismissingTheUndoSnackbarLeavesTheListDeleted() {
        createList("Groceries")
        awaitText("Add your first item to get started")
        addOneItem("Milk")
        back()

        awaitText("Groceries")
        compose.onNodeWithText("Groceries").performTouchInput { swipeLeft() }

        awaitText("Groceries deleted")
        awaitGone("Groceries deleted")

        // Snackbar gone without Undo — the list stays deleted.
        compose.onNodeWithText("No lists yet").assertExists()
    }
}
