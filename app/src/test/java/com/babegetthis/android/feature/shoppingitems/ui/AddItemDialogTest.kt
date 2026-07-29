package com.babegetthis.android.feature.shoppingitems.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.babegetthis.android.core.model.Category
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import com.babegetthis.android.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Buttons are actioned through the semantics tree rather than by tapping
// coordinates: this dialog stacks five text fields, so on Robolectric's default
// display the button row sits below the fold and a coordinate tap lands on
// nothing. Same reason the create-chooser sheet does it.
@RunWith(RobolectricTestRunner::class)
class AddItemDialogTest {

    @get:Rule val compose = createComposeRule()

    private data class Added(
        val name: String, val quantity: String, val categoryId: String?,
        val shop: String?, val note: String?,
    )

    private data class Edited(
        val id: String, val name: String, val quantity: String,
        val categoryId: String?, val shop: String?, val note: String?,
    )

    private var added: Added? = null
    private var edited: Edited? = null
    private var dismissed = false
    private var createdCategoryName: String? = null

    private val categories = listOf(
        TestData.category(id = "c1", name = "Dairy"),
        TestData.category(id = "c2", name = "Bakery"),
    )

    private fun show(editingItem: ShoppingItem? = null) = compose.setContent {
        AddItemDialog(
            categories = categories,
            onDismiss = { dismissed = true },
            onAdd = { n, q, c, s, note -> added = Added(n, q, c, s, note) },
            onCreateCategory = { name, onCreated ->
                createdCategoryName = name
                onCreated(Category(id = "new-cat", name = name, isDefault = false))
            },
            editingItem = editingItem,
            onEdit = { id, n, q, c, s, note -> edited = Edited(id, n, q, c, s, note) },
        )
    }

    // Both the name and the quantity field carry a label, so address each by its
    // own label rather than by position.
    private fun typeName(text: String) =
        compose.onNodeWithText("Item name").performTextInput(text)

    private fun typeQuantity(text: String) =
        compose.onNodeWithText("Quantity or notes (e.g. 2 large, slightly firm)")
            .performTextInput(text)

    @Test
    fun `add mode shows the add title`() {
        show()

        compose.onNodeWithText("Add Item").assertExists()
    }

    // Quantity is required as well as the name — a name alone is not enough.
    @Test
    fun `confirm needs both a name and a quantity`() {
        show()

        compose.onNodeWithText("Add").assertIsNotEnabled()

        typeName("Milk")
        compose.onNodeWithText("Add").assertIsNotEnabled()

        typeQuantity("2")
        compose.onNodeWithText("Add").assertIsEnabled()
    }

    @Test
    fun `confirm stays disabled for a whitespace-only name`() {
        show()

        typeName("   ")
        typeQuantity("2")

        compose.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun `adding passes the typed values`() {
        show()
        typeName("Milk")
        typeQuantity("2 litres")

        compose.onNodeWithText("Add").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("Milk", added?.name)
        assertEquals("2 litres", added?.quantity)
    }

    @Test
    fun `name and quantity are trimmed`() {
        show()
        typeName("  Milk  ")
        typeQuantity("  2  ")

        compose.onNodeWithText("Add").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("Milk", added?.name)
        assertEquals("2", added?.quantity)
    }

    // Optional fields left untouched must arrive as null, not "".
    @Test
    fun `untouched optional fields arrive as null`() {
        show()
        typeName("Milk")
        typeQuantity("2")

        compose.onNodeWithText("Add").performSemanticsAction(SemanticsActions.OnClick)

        assertNull(added?.shop)
        assertNull(added?.note)
        assertNull(added?.categoryId)
    }

    @Test
    fun `shop and note are captured and trimmed`() {
        show()
        typeName("Milk")
        typeQuantity("2")
        compose.onNodeWithText("Shop (optional)").performTextInput("  Aldi  ")
        compose.onNodeWithText("Note (optional)").performTextInput("  semi-skimmed  ")

        compose.onNodeWithText("Add").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("Aldi", added?.shop)
        assertEquals("semi-skimmed", added?.note)
    }

    // Whitespace-only optional fields collapse to null rather than being stored.
    @Test
    fun `whitespace-only optional fields collapse to null`() {
        show()
        typeName("Milk")
        typeQuantity("2")
        compose.onNodeWithText("Shop (optional)").performTextInput("   ")

        compose.onNodeWithText("Add").performSemanticsAction(SemanticsActions.OnClick)

        assertNull(added?.shop)
    }

    @Test
    fun `cancel dismisses without adding`() {
        show()
        typeName("Milk")
        typeQuantity("2")

        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)

        assertTrue(dismissed)
        assertNull(added)
    }

    // --- edit mode ---

    private val existing = TestData.item(
        id = "i1", name = "Milk", quantity = "2", categoryId = "c1",
        shop = "Aldi", note = "semi",
    )

    @Test
    fun `edit mode shows the edit title and pre-fills the item`() {
        show(editingItem = existing)

        compose.onNodeWithText("Edit Item").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Milk")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Aldi")).assertExists()
    }

    @Test
    fun `edit mode reports the item id and edited values`() {
        show(editingItem = existing)

        compose.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("i1", edited?.id)
        assertEquals("Milk", edited?.name)
        assertEquals("c1", edited?.categoryId)
        assertNull("edit mode must not fall through to add", added)
    }

    @Test
    fun `edit mode keeps the existing shop and note`() {
        show(editingItem = existing)

        compose.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("Aldi", edited?.shop)
        assertEquals("semi", edited?.note)
    }
}
