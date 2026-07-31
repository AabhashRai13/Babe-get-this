package com.babegetthis.android.feature.shoppingitems.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.babegetthis.android.core.ui.TestTags
import com.babegetthis.android.core.ui.components.SwipeableCard
import com.babegetthis.android.feature.shoppingitems.ui.components.FirstItemPrompt
import com.babegetthis.android.feature.shoppingitems.ui.components.ProgressCard
import com.babegetthis.android.feature.shoppingitems.ui.components.SectionHeader
import com.babegetthis.android.feature.shoppingitems.ui.components.ShopSubHeader
import com.babegetthis.android.feature.shoppingitems.ui.components.ShoppingItemCard
import com.babegetthis.android.testing.TestData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Leaf composables of the shoppingitems feature.
@RunWith(RobolectricTestRunner::class)
class ShoppingItemComponentsTest {

    @get:Rule val compose = createComposeRule()

    // --- ShoppingItemCard ---

    private fun itemCard(
        isPickedUp: Boolean = false,
        note: String? = null,
        quantity: String = "1",
        onClick: () -> Unit = {},
        onToggle: () -> Unit = {},
    ) = compose.setContent {
        ShoppingItemCard(
            item = TestData.item(
                id = "i1", name = "Milk", quantity = quantity,
                isPickedUp = isPickedUp, note = note,
            ),
            onClick = onClick,
            onTogglePickedUp = onToggle,
        )
    }

    @Test
    fun `card shows the item name`() {
        itemCard()

        compose.onNodeWithText("Milk").assertIsDisplayed()
    }

    @Test
    fun `card shows a note when there is one`() {
        itemCard(note = "semi-skimmed")

        compose.onNodeWithText("semi-skimmed").assertIsDisplayed()
    }

    @Test
    fun `card omits the note row when blank`() {
        itemCard(note = "   ")

        compose.onNodeWithText("   ").assertDoesNotExist()
    }

    @Test
    fun `tapping the row invokes onClick, not the toggle`() {
        var clicked = false
        var toggled = false
        itemCard(onClick = { clicked = true }, onToggle = { toggled = true })

        compose.onNodeWithTag(TestTags.itemCard("i1")).performClick()

        assertTrue("row tap should open the editor", clicked)
        assertTrue("row tap must not tick the item off", !toggled)
    }

    @Test
    fun `tapping the checkbox invokes the toggle, not onClick`() {
        var clicked = false
        var toggled = false
        itemCard(onClick = { clicked = true }, onToggle = { toggled = true })

        compose.onNodeWithTag(TestTags.itemCheckbox("i1")).performClick()

        assertTrue("checkbox should tick the item off", toggled)
        assertTrue("checkbox must not open the editor", !clicked)
    }

    @Test
    fun `a picked-up item still renders its name`() {
        itemCard(isPickedUp = true)

        compose.onNodeWithText("Milk").assertIsDisplayed()
    }

    // --- ProgressCard ---

    @Test
    fun `progress card reports nothing done`() {
        compose.setContent { ProgressCard(totalItems = 5, completedCount = 0) }

        compose.onNodeWithText("0 of 5 picked up").assertIsDisplayed()
        compose.onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun `progress card reports partial progress`() {
        compose.setContent { ProgressCard(totalItems = 5, completedCount = 3) }

        compose.onNodeWithText("3 of 5 picked up").assertIsDisplayed()
        compose.onNodeWithText("60%").assertIsDisplayed()
    }

    // The copy switches entirely once everything is ticked off — it stops
    // counting and celebrates.
    @Test
    fun `progress card celebrates a fully completed list`() {
        compose.setContent { ProgressCard(totalItems = 5, completedCount = 5) }

        compose.onNodeWithText("All done!").assertIsDisplayed()
        compose.onNodeWithText("100%").assertIsDisplayed()
    }

    // 0/0 must not divide by zero, and an empty list is NOT "all done".
    @Test
    fun `progress card handles an empty list without dividing by zero`() {
        compose.setContent { ProgressCard(totalItems = 0, completedCount = 0) }

        compose.onNodeWithText("0 of 0 picked up").assertIsDisplayed()
        compose.onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun `progress rounds down rather than up`() {
        compose.setContent { ProgressCard(totalItems = 3, completedCount = 1) }

        // 1/3 = 33.33% -> 33, not 34.
        compose.onNodeWithText("33%").assertIsDisplayed()
    }

    // --- Section headers ---

    @Test
    fun `section header shows its title and count`() {
        compose.setContent { SectionHeader(title = "ACTIVE ITEMS", count = "3 Items") }

        compose.onNodeWithText("ACTIVE ITEMS").assertIsDisplayed()
        compose.onNodeWithText("3 Items").assertIsDisplayed()
    }

    @Test
    fun `section header without a count shows only the title`() {
        compose.setContent { SectionHeader(title = "COMPLETED", count = null) }

        compose.onNodeWithText("COMPLETED").assertIsDisplayed()
    }

    @Test
    fun `shop subheader shows the shop name`() {
        compose.setContent { ShopSubHeader(shopName = "Whole Foods") }

        compose.onNodeWithText("Whole Foods").assertIsDisplayed()
    }

    // --- FirstItemPrompt ---

    @Test
    fun `first item prompt offers its CTA`() {
        var added = false
        compose.setContent { FirstItemPrompt(onAddItem = { added = true }) }

        compose.onNodeWithText("Add your first item to get started").assertIsDisplayed()
        compose.onNodeWithText("Add first item").performClick()

        assertTrue(added)
    }

    // --- SwipeableCard ---

    @Test
    fun `swiping left fires the delete action once`() {
        var swipes = 0
        compose.setContent {
            SwipeableCard(onSwipeLeft = { swipes++ }) {
                // Full width on purpose: swipeLeft() travels across the matched
                // node's bounds, so a small wrapped Text gives too short a drag
                // to cross SwipeToDismissBox's threshold.
                Text("Row", Modifier.fillMaxWidth().height(64.dp))
            }
        }

        compose.onNodeWithText("Row").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        // Exactly once. This used to be 2: the action ran from
        // confirmValueChange, which Compose may consult more than once per
        // gesture, so a single swipe deleted twice and the second delete
        // destroyed the undo cache.
        assertTrue("expected exactly one delete, got $swipes", swipes == 1)
    }

    @Test
    fun `swiping right fires the secondary action once`() {
        var rights = 0
        compose.setContent {
            SwipeableCard(onSwipeLeft = {}, onSwipeRight = { rights++ }) {
                Text("Row", Modifier.fillMaxWidth().height(64.dp))
            }
        }

        compose.onNodeWithText("Row").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertTrue("expected exactly one right action, got $rights", rights == 1)
    }

    // Right-swipe is opt-in; with no handler the gesture must do nothing rather
    // than dismissing the row into an action that does not exist.
    @Test
    fun `swiping right does nothing when no handler is supplied`() {
        var lefts = 0
        compose.setContent {
            SwipeableCard(onSwipeLeft = { lefts++ }) {
                Text("Row", Modifier.fillMaxWidth().height(64.dp))
            }
        }

        compose.onNodeWithText("Row").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertTrue(lefts == 0)
        compose.onNodeWithText("Row").assertIsDisplayed()
    }

    @Test
    fun `an untouched card fires nothing`() {
        var swipes = 0
        compose.setContent {
            SwipeableCard(onSwipeLeft = { swipes++ }) {
                // Full width on purpose: swipeLeft() travels across the matched
                // node's bounds, so a small wrapped Text gives too short a drag
                // to cross SwipeToDismissBox's threshold.
                Text("Row", Modifier.fillMaxWidth().height(64.dp))
            }
        }

        compose.onNodeWithText("Row").assertIsDisplayed()

        assertTrue(swipes == 0)
    }
}
