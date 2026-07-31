package com.babegetthis.android.feature.shoppinglist.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// No test tags here: every control is a Material3 TextButton or a labelled text
// field, all of which merge their descendants' semantics, so matching on the
// visible text reaches the clickable directly.
@RunWith(RobolectricTestRunner::class)
class CreateListDialogTest {

    @get:Rule val compose = createComposeRule()

    private var created: String? = null
    private var renamed: String? = null
    private var dismissed = false

    private fun showCreate() = compose.setContent {
        CreateListDialog(
            onDismiss = { dismissed = true },
            onCreate = { created = it },
        )
    }

    private fun showEdit(currentName: String = "Groceries") = compose.setContent {
        CreateListDialog(
            onDismiss = { dismissed = true },
            onCreate = { created = it },
            currentName = currentName,
            onRename = { renamed = it },
        )
    }

    @Test
    fun `create mode shows the new-list title and an empty field`() {
        showCreate()

        compose.onNodeWithText("New List").assertExists()
        compose.onNodeWithText("Create").assertExists()
    }

    @Test
    fun `confirm is disabled until a name is typed`() {
        showCreate()

        compose.onNodeWithText("Create").assertIsNotEnabled()

        compose.onNodeWithText("List name").performTextInput("Groceries")

        compose.onNodeWithText("Create").assertIsEnabled()
    }

    @Test
    fun `confirm stays disabled for whitespace only`() {
        showCreate()

        compose.onNodeWithText("List name").performTextInput("   ")

        compose.onNodeWithText("Create").assertIsNotEnabled()
    }

    @Test
    fun `creating passes the typed name`() {
        showCreate()

        compose.onNodeWithText("List name").performTextInput("Groceries")
        compose.onNodeWithText("Create").performClick()

        assertEquals("Groceries", created)
    }

    @Test
    fun `the created name is trimmed`() {
        showCreate()

        compose.onNodeWithText("List name").performTextInput("  Groceries  ")
        compose.onNodeWithText("Create").performClick()

        assertEquals("Groceries", created)
    }

    @Test
    fun `a name of exactly forty characters is accepted`() {
        showCreate()

        compose.onNodeWithText("List name").performTextInput("x".repeat(40))
        compose.onNodeWithText("Create").performClick()

        assertEquals(40, created?.length)
    }

    // Pins current behavior, which is NOT truncation: onValueChange is
    // `if (it.length <= 40) listName = it`, so an over-long value is rejected
    // wholesale rather than clipped. Typing stops dead at 40 (fine), but pasting
    // a 41+ character name leaves the field empty with no feedback at all.
    // Logged as a finding rather than changed here — see findings.md, task 3.5.
    @Test
    fun `pasting more than forty characters is rejected outright`() {
        showCreate()

        compose.onNodeWithText("List name").performTextInput("x".repeat(41))

        // Nothing landed, so there is nothing to create.
        compose.onNodeWithText("Create").assertIsNotEnabled()
        assertNull(created)
    }

    @Test
    fun `cancel dismisses without creating`() {
        showCreate()

        compose.onNodeWithText("Cancel").performClick()

        assertTrue(dismissed)
        assertNull(created)
    }

    @Test
    fun `edit mode pre-fills the current name and offers Save`() {
        showEdit(currentName = "Groceries")

        compose.onNodeWithText("Rename List").assertExists()
        compose.onNodeWithText("Groceries").assertExists()
        compose.onNodeWithText("Save").assertExists()
    }

    @Test
    fun `edit mode renames rather than creating`() {
        showEdit(currentName = "Groceries")

        compose.onNodeWithText("Groceries").performTextReplacement("Hardware")
        compose.onNodeWithText("Save").performClick()

        assertEquals("Hardware", renamed)
        assertNull("edit mode must not fall through to create", created)
    }

    @Test
    fun `edit mode disables Save when the name is cleared`() {
        showEdit(currentName = "Groceries")

        compose.onNodeWithText("Groceries").performTextReplacement("")

        compose.onNodeWithText("Save").assertIsNotEnabled()
    }
}
