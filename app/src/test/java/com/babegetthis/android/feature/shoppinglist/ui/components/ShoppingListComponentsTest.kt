package com.babegetthis.android.feature.shoppinglist.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.babegetthis.android.core.ui.TestTags
import com.babegetthis.android.testing.TestData
import com.babegetthis.android.ui.theme.ListAccentColor
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Covers the leaf composables of the shoppinglist feature: the card, the tab
// pills, both empty states, the greeting, and the create chooser.
@RunWith(RobolectricTestRunner::class)
class ShoppingListComponentsTest {

    @get:Rule val compose = createComposeRule()

    private val accent = ListAccentColor(container = Color.Gray, onContainer = Color.Black)

    // --- ShoppingListCard ---

    private fun card(
        itemCount: Int = 0,
        completedItemCount: Int = 0,
        isLocked: Boolean = false,
        isCompletedTab: Boolean = false,
        onClick: () -> Unit = {},
        onLongPress: () -> Unit = {},
    ) = compose.setContent {
        ShoppingListCard(
            list = TestData.list(
                id = "l1",
                name = "Groceries",
                itemCount = itemCount,
                completedItemCount = completedItemCount,
                isLocked = isLocked,
            ),
            accent = accent,
            isCompletedTab = isCompletedTab,
            onClick = onClick,
            onLongPress = onLongPress,
        )
    }

    @Test
    fun `card shows the list name`() {
        card()

        compose.onNodeWithText("Groceries").assertIsDisplayed()
    }

    @Test
    fun `an empty list says so rather than showing zero progress`() {
        card(itemCount = 0)

        compose.onNodeWithText("No items yet").assertIsDisplayed()
    }

    @Test
    fun `active tab shows progress out of the total`() {
        card(itemCount = 5, completedItemCount = 3)

        compose.onNodeWithText("3/5 items").assertIsDisplayed()
    }

    @Test
    fun `active tab shows zero progress once there are items`() {
        card(itemCount = 5, completedItemCount = 0)

        compose.onNodeWithText("0/5 items").assertIsDisplayed()
    }

    @Test
    fun `completed tab shows a plain count, not progress`() {
        card(itemCount = 5, completedItemCount = 5, isCompletedTab = true)

        compose.onNodeWithText("5 items").assertIsDisplayed()
    }

    @Test
    fun `an unlocked list shows no lock badge`() {
        card(isLocked = false)

        compose.onNodeWithContentDescription("Locked").assertDoesNotExist()
    }

    @Test
    fun `a locked list shows the lock badge`() {
        card(isLocked = true)

        compose.onNodeWithContentDescription("Locked").assertIsDisplayed()
    }

    @Test
    fun `tapping the card invokes onClick`() {
        var clicked = false
        card(onClick = { clicked = true })

        compose.onNodeWithTag(TestTags.listCard("l1")).performClick()

        assertTrue(clicked)
    }

    // --- TabPillRow ---

    private val tabs = listOf(
        TabPill("Active", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
        TabPill("Completed", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    )

    private fun tabRow(selectedIndex: Int = 0, onTabSelected: (Int) -> Unit = {}) =
        compose.setContent {
            TabPillRow(tabs = tabs, selectedIndex = selectedIndex, onTabSelected = onTabSelected)
        }

    @Test
    fun `both tab labels render`() {
        tabRow()

        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithText("Completed").assertIsDisplayed()
    }

    @Test
    fun `selecting a tab reports its index`() {
        var selected: Int? = null
        tabRow(selectedIndex = 0, onTabSelected = { selected = it })

        compose.onNodeWithTag(TestTags.listTab(1)).performClick()

        assertEquals(1, selected)
    }

    @Test
    fun `re-selecting the current tab still reports it`() {
        var selected: Int? = null
        tabRow(selectedIndex = 0, onTabSelected = { selected = it })

        compose.onNodeWithTag(TestTags.listTab(0)).performClick()

        assertEquals(0, selected)
    }

    // --- TabEmptyState ---

    @Test
    fun `active tab empty state reads as all done`() {
        compose.setContent { TabEmptyState(isActiveTab = true) }

        compose.onNodeWithText("All done! All your lists are completed.").assertIsDisplayed()
    }

    @Test
    fun `completed tab empty state reads as nothing completed`() {
        compose.setContent { TabEmptyState(isActiveTab = false) }

        compose.onNodeWithText("No completed lists yet").assertIsDisplayed()
    }

    // --- GreetingSection ---

    @Test
    fun `greeting uses only the first name`() {
        compose.setContent { GreetingSection(listCount = 2, itemsToGet = 3, userName = "Aabhash Rai") }

        // The time-of-day half varies, so match the part that doesn't.
        compose.onNodeWithText(", Aabhash", substring = true).assertIsDisplayed()
    }

    @Test
    fun `greeting stays impersonal when signed out`() {
        compose.setContent { GreetingSection(listCount = 2, itemsToGet = 3, userName = null) }

        compose.onNodeWithText(",", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a blank name is treated as signed out`() {
        compose.setContent { GreetingSection(listCount = 1, itemsToGet = 1, userName = "   ") }

        compose.onNodeWithText(",", substring = true).assertDoesNotExist()
    }

    @Test
    fun `summary reports nothing to pick up when the count is zero`() {
        compose.setContent { GreetingSection(listCount = 3, itemsToGet = 0) }

        compose.onNodeWithText("3 lists — nothing to pick up yet").assertIsDisplayed()
    }

    @Test
    fun `summary is singular for one list`() {
        compose.setContent { GreetingSection(listCount = 1, itemsToGet = 4) }

        compose.onNodeWithText("1 list · 4 items to get").assertIsDisplayed()
    }

    @Test
    fun `summary is plural for several lists`() {
        compose.setContent { GreetingSection(listCount = 3, itemsToGet = 4) }

        compose.onNodeWithText("3 lists · 4 items to get").assertIsDisplayed()
    }

    // --- ShoppingListEmptyState ---

    @Test
    fun `whole-screen empty state offers the create CTA`() {
        var created = false
        compose.setContent {
            com.babegetthis.android.feature.shoppinglist.ui.ShoppingListEmptyState(
                onCreateList = { created = true },
            )
        }

        compose.onNodeWithText("No lists yet").assertIsDisplayed()
        compose.onNodeWithText("Create your first list").performClick()

        assertTrue(created)
    }

    // --- CreateListChooserSheet ---

    private fun chooser(
        onDismiss: () -> Unit = {},
        onPickType: () -> Unit = {},
        onPickVoice: () -> Unit = {},
    ) = compose.setContent {
        CreateListChooserSheet(
            onDismiss = onDismiss,
            onPickType = onPickType,
            onPickVoice = onPickVoice,
        )
    }

    @Test
    fun `chooser offers both entry points`() {
        chooser()

        compose.onNodeWithText("Type").assertIsDisplayed()
        compose.onNodeWithText("Voice").assertIsDisplayed()
    }

    // ModalBottomSheet animates in, so a coordinate-based performClick() can land
    // before the sheet has settled and hit nothing. Invoking the semantics action
    // asserts the same thing — this node's onClick runs that callback — without
    // depending on layout timing, which is exactly the kind of dependency that
    // makes UI suites flaky.
    @Test
    fun `picking Type dispatches only that callback`() {
        var typed = false
        var voiced = false
        chooser(onPickType = { typed = true }, onPickVoice = { voiced = true })

        compose.onNodeWithText("Type").performSemanticsAction(SemanticsActions.OnClick)

        assertTrue("onPickType should have fired", typed)
        assertTrue("onPickVoice should NOT have fired", !voiced)
    }

    @Test
    fun `picking Voice dispatches only that callback`() {
        var typed = false
        var voiced = false
        chooser(onPickType = { typed = true }, onPickVoice = { voiced = true })

        compose.onNodeWithText("Voice").performSemanticsAction(SemanticsActions.OnClick)

        assertTrue("onPickVoice should have fired", voiced)
        assertTrue("onPickType should NOT have fired", !typed)
    }
}
