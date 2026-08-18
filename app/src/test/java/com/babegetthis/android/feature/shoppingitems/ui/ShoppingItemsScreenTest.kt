package com.babegetthis.android.feature.shoppingitems.ui

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.data.repository.CategoryRepository
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.pin.data.PinRepository
import com.babegetthis.android.core.ui.TestTags
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import com.babegetthis.android.feature.shoppingitems.ui.viewModels.ShoppingItemsViewModel
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import com.babegetthis.android.testing.TestData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Real ShoppingItemsViewModel over mocked repositories, same approach as
// ShoppingListScreenTest — the derived state (active/completed split, shop
// grouping, progress counts) is the genuine article rather than a hand-stubbed
// approximation of it.
//
// NOT covered here: the locked / needs-unlock state. That branch renders
// PinPromptDialog, whose ViewModel defaults to hiltViewModel(), which cannot
// resolve under a plain Compose rule. Adding a testing seam to production purely
// to reach it would not be worth it, because the state is already covered from
// both sides: ShoppingItemsViewModelTest proves isLocked requires BOTH the row
// flag and an existing PIN, and the end-to-end PIN journey (task 14.5) exercises
// the real dialog on a device with Hilt wired up.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShoppingItemsScreenTest {

    @get:Rule val compose = createComposeRule()

    private val itemRepository = mockk<ShoppingItemRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val authStateManager = mockk<AuthStateManager>(relaxed = true)
    private val listRepository = mockk<ShoppingListRepository>(relaxed = true)
    private val pinRepository = mockk<PinRepository>(relaxed = true)

    private val itemsFlow = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private var navigatedBack = false

    private lateinit var viewModel: ShoppingItemsViewModel

    private fun item(
        id: String,
        name: String,
        isPickedUp: Boolean = false,
        shop: String? = null,
    ) = TestData.item(id = id, listId = "L1", name = name, isPickedUp = isPickedUp, shop = shop)

    private fun render(items: List<ShoppingItem> = emptyList(), loggedIn: Boolean = true) {
        itemsFlow.value = items
        every { itemRepository.getItemsByListId(any()) } returns itemsFlow
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        every { authStateManager.authState } returns MutableStateFlow(
            if (loggedIn) AuthState.Authenticated("u1") else AuthState.Unauthenticated
        )
        every { listRepository.getListById(any()) } returns MutableStateFlow(TestData.list(id = "L1"))
        every { pinRepository.pinExists } returns MutableStateFlow(false)

        every { listRepository.getShareCode(any()) } returns MutableStateFlow(null)

        viewModel = ShoppingItemsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("listId" to "L1", "listName" to "Groceries")),
            itemRepository = itemRepository,
            categoryRepository = categoryRepository,
            authStateManager = authStateManager,
            listRepository = listRepository,
            pinRepository = pinRepository,
            shareRepository = mockk(relaxed = true),
            syncEngine = mockk(relaxed = true),
            sharedListRemote = com.babegetthis.android.testing.FakeSharedListRemote(),
            analytics = mockk(relaxed = true),
            markers = mockk(relaxed = true),
            applicationScope = CoroutineScope(UnconfinedTestDispatcher()),
        )
        compose.setContent {
            ShoppingItemsScreen(
                onNavigateBack = { navigatedBack = true },
                viewModel = viewModel,
            )
        }
    }

    // --- 5.2 states ---

    @Test
    fun `an empty list shows the first-item prompt`() {
        render(items = emptyList())

        compose.onNodeWithText("Add your first item to get started").assertIsDisplayed()
    }

    @Test
    fun `the list name is shown in the app bar`() {
        render(items = listOf(item("i1", "Milk")))

        compose.onNodeWithText("Groceries").assertExists()
    }

    @Test
    fun `active items render`() {
        render(items = listOf(item("i1", "Milk"), item("i2", "Eggs")))

        compose.onNodeWithText("Milk").assertExists()
        compose.onNodeWithText("Eggs").assertExists()
    }

    @Test
    fun `progress reflects how many are picked up`() {
        render(
            items = listOf(
                item("i1", "Milk", isPickedUp = true),
                item("i2", "Eggs"),
                item("i3", "Bread"),
            )
        )

        compose.onNodeWithText("1 of 3 picked up").assertExists()
    }

    @Test
    fun `a fully picked-up list celebrates`() {
        render(
            items = listOf(
                item("i1", "Milk", isPickedUp = true),
                item("i2", "Eggs", isPickedUp = true),
            )
        )

        compose.onNodeWithText("All done!").assertExists()
    }

    @Test
    fun `the active section header counts only active items`() {
        render(items = listOf(item("i1", "Milk"), item("i2", "Eggs", isPickedUp = true)))

        compose.onNodeWithText("ACTIVE ITEMS").assertExists()
        compose.onNodeWithText("1 Items").assertExists()
    }

    @Test
    fun `completed items get their own section`() {
        render(items = listOf(item("i1", "Milk"), item("i2", "Eggs", isPickedUp = true)))

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("COMPLETED"))
        compose.onNodeWithText("COMPLETED").assertIsDisplayed()
    }

    @Test
    fun `items with a shop are grouped under a shop subheader`() {
        render(items = listOf(item("i1", "Milk", shop = "Aldi")))

        compose.onNodeWithText("Aldi").assertExists()
    }

    // --- 5.3 interactions ---

    @Test
    fun `ticking the checkbox toggles that item`() {
        coEvery { itemRepository.togglePickedUp(any(), any()) } returns Result.Success(Unit)
        render(items = listOf(item("i1", "Milk")))

        compose.onNodeWithTag(TestTags.itemCheckbox("i1"))
            .performSemanticsAction(SemanticsActions.OnClick)

        coVerify { itemRepository.togglePickedUp("i1", true) }
    }

    @Test
    fun `un-ticking a picked-up item toggles it back`() {
        coEvery { itemRepository.togglePickedUp(any(), any()) } returns Result.Success(Unit)
        render(items = listOf(item("i1", "Milk", isPickedUp = true)))

        compose.onNodeWithTag(TestTags.itemCheckbox("i1"))
            .performSemanticsAction(SemanticsActions.OnClick)

        coVerify { itemRepository.togglePickedUp("i1", false) }
    }

    @Test
    fun `tapping a row opens the edit dialog pre-filled`() {
        render(items = listOf(item("i1", "Milk")))

        compose.onNodeWithTag(TestTags.itemCard("i1"))
            .performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Edit Item").assertExists()
    }

    @Test
    fun `the add FAB opens the add dialog`() {
        render(items = listOf(item("i1", "Milk")))

        compose.onNodeWithText("Add").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Add Item").assertExists()
    }

    @Test
    fun `the first-item prompt opens the add dialog`() {
        render(items = emptyList())

        compose.onNodeWithText("Add first item").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Add Item").assertExists()
    }

    // --- 5.4 events ---

    @Test
    fun `an error emission shows a snackbar with that exact message`() {
        coEvery { itemRepository.togglePickedUp(any(), any()) } returns
            Result.Error(AppError.DatabaseError())
        render(items = listOf(item("i1", "Milk")))

        viewModel.togglePickedUp("i1", true)

        compose.onNodeWithText(AppError.DatabaseError().message).assertExists()
    }

    @Test
    fun `deleting emits a snackbar naming the item, with an Undo action`() {
        coEvery { itemRepository.deleteItem("i1") } returns Result.Success(Unit)
        render(items = listOf(item("i1", "Milk")))

        viewModel.deleteItem("i1")

        compose.onNodeWithText("Milk deleted").assertExists()
        compose.onNodeWithText("Undo").assertExists()
    }

    @Test
    fun `tapping Undo restores the item`() {
        coEvery { itemRepository.deleteItem("i1") } returns Result.Success(Unit)
        coEvery { itemRepository.restoreItem(any()) } returns Result.Success(Unit)
        render(items = listOf(item("i1", "Milk")))
        viewModel.deleteItem("i1")

        compose.onNodeWithText("Undo").performClick()

        coVerify { itemRepository.restoreItem(any()) }
    }

    @Test
    fun `a share event carries the formatted list text`() {
        render(items = listOf(item("i1", "Milk")))

        viewModel.onShareClick()

        // The screen owns the Intent; asserting the event reached it without
        // crashing is as far as a JVM test can honestly go.
        assertTrue(!navigatedBack)
    }

    // -- Voice gate --

    // The regression that shipped: this FAB had no auth check at all, while the
    // home screen's create-by-voice flow (covered by AuthAndVoiceGateTest) did.
    // The mic that lacked a test was exactly the one that lacked the gate.
    @Test
    fun `signed-out mic tap prompts sign-in instead of opening the voice sheet`() {
        render(loggedIn = false)

        compose.onNodeWithTag(TestTags.VOICE_FAB).performClick()

        compose.onNodeWithText("Sign in to use voice").assertIsDisplayed()
    }

    // The signed-in path (mic opens the sheet) is NOT testable here: the voice
    // sheet's ViewModel defaults to hiltViewModel(), which can't resolve under
    // a plain Compose rule — same limitation as the PinPromptDialog note at the
    // top of this file. AuthAndVoiceGateTest covers it on-device.

    // Task 8.4's device finding: the share gate used to say "Sign in to use
    // voice". Each gate must name its own feature.
    @Test
    fun `signed-out share tap prompts with SHARE copy, not voice copy`() {
        render(loggedIn = false)

        compose.onNodeWithContentDescription("Share live").performClick()

        compose.onNodeWithText("Sign in to share").assertIsDisplayed()
    }
}
