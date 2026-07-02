package com.babegetthis.android.feature.shoppingitems.ui.viewModels

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.data.repository.CategoryRepository
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import kotlinx.coroutines.CoroutineScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingItemsViewModelTest {

    // UnconfinedTestDispatcher runs coroutines eagerly which suits ViewModel
    // tests where we want init { } and viewModelScope.launch { } work to be
    // observable without manual advanceUntilIdle() everywhere.
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var itemRepository: ShoppingItemRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var authStateManager: AuthStateManager
    private lateinit var listRepository: ShoppingListRepository
    private lateinit var itemsFlow: MutableStateFlow<List<ShoppingItem>>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        itemRepository = mockk(relaxed = true)
        categoryRepository = mockk(relaxed = true)
        authStateManager = mockk(relaxed = true)
        listRepository = mockk(relaxed = true)
        itemsFlow = MutableStateFlow(emptyList())

        every { itemRepository.getItemsByListId(any()) } returns itemsFlow
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        every { authStateManager.authState } returns MutableStateFlow(AuthState.Unauthenticated)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): ShoppingItemsViewModel = ShoppingItemsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("listId" to "L1", "listName" to "Groceries")),
        itemRepository = itemRepository,
        categoryRepository = categoryRepository,
        authStateManager = authStateManager,
        listRepository = listRepository,
        applicationScope = CoroutineScope(testDispatcher),
    )

    private fun item(
        id: String,
        isPickedUp: Boolean = false,
        name: String = "Item-$id",
    ) = ShoppingItem(
        id = id,
        listId = "L1",
        name = name,
        quantity = "1",
        isPickedUp = isPickedUp,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    // -- ListJustCompleted transition logic --

    @Test
    fun `emits ListJustCompleted when last unchecked item is ticked off`() = runTest {
        val viewModel = buildViewModel()

        viewModel.events.test {
            // Start with two unchecked items — not all done.
            itemsFlow.value = listOf(item("1"), item("2"))
            expectNoEvents()

            // Flip both to picked up — transition fires.
            itemsFlow.value = listOf(
                item("1", isPickedUp = true),
                item("2", isPickedUp = true),
            )

            assertEquals(
                ShoppingItemsViewModel.UiEvent.ListJustCompleted,
                awaitItem(),
            )
            expectNoEvents()
        }
    }

    @Test
    fun `does not emit ListJustCompleted on initial load of already-complete list`() = runTest {
        // Pre-seed the flow with an already-complete list BEFORE the VM
        // subscribes — opening a completed list should not replay the buzz.
        itemsFlow.value = listOf(
            item("1", isPickedUp = true),
            item("2", isPickedUp = true),
        )

        val viewModel = buildViewModel()

        viewModel.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `emits ListJustCompleted on the second completion when uncheck then recomplete`() = runTest {
        val viewModel = buildViewModel()

        viewModel.events.test {
            // First completion.
            itemsFlow.value = listOf(item("1"))
            itemsFlow.value = listOf(item("1", isPickedUp = true))
            assertEquals(
                ShoppingItemsViewModel.UiEvent.ListJustCompleted,
                awaitItem(),
            )

            // Uncheck, then re-complete — second transition should fire.
            itemsFlow.value = listOf(item("1", isPickedUp = false))
            itemsFlow.value = listOf(item("1", isPickedUp = true))
            assertEquals(
                ShoppingItemsViewModel.UiEvent.ListJustCompleted,
                awaitItem(),
            )
        }
    }

    @Test
    fun `empty list never triggers ListJustCompleted`() = runTest {
        val viewModel = buildViewModel()

        viewModel.events.test {
            // An empty list isn't "all done" by the VM's rule (must be non-empty AND all picked up).
            itemsFlow.value = emptyList()
            itemsFlow.value = emptyList()
            expectNoEvents()
        }
    }

    // -- Delete + Undo --

    @Test
    fun `deleteItem caches the item, calls repository, and emits undo event with item name`() = runTest {
        val target = item("1", name = "Milk")
        itemsFlow.value = listOf(target)
        coEvery { itemRepository.deleteItem("1") } returns Result.Success(Unit)

        val viewModel = buildViewModel()

        viewModel.undoDeleteEvent.test {
            viewModel.deleteItem("1")
            assertEquals("Milk", awaitItem())
        }
        coVerify { itemRepository.deleteItem("1") }
    }

    @Test
    fun `undoDeleteItem restores the previously deleted item via repository`() = runTest {
        val target = item("1", name = "Milk")
        itemsFlow.value = listOf(target)
        coEvery { itemRepository.deleteItem("1") } returns Result.Success(Unit)
        coEvery { itemRepository.restoreItem(target) } returns Result.Success(Unit)

        val viewModel = buildViewModel()

        // First delete to populate the pending-delete cache.
        viewModel.undoDeleteEvent.test {
            viewModel.deleteItem("1")
            awaitItem()
        }
        // Now undo — repository.restoreItem should fire with the cached item.
        viewModel.undoDeleteItem()
        coVerify { itemRepository.restoreItem(target) }
    }

    @Test
    fun `undoDeleteItem with no pending delete is a no-op`() = runTest {
        val viewModel = buildViewModel()

        viewModel.undoDeleteItem()

        coVerify(exactly = 0) { itemRepository.restoreItem(any()) }
    }

    @Test
    fun `deleteItem on repository error emits errorMessage and does not cache for undo`() = runTest {
        itemsFlow.value = listOf(item("1", name = "Milk"))
        coEvery { itemRepository.deleteItem("1") } returns
            Result.Error(AppError.DatabaseError("boom"))

        val viewModel = buildViewModel()

        viewModel.errorMessage.test {
            viewModel.deleteItem("1")
            assertEquals("boom", awaitItem())
        }
        // No undo emission, no restore allowed.
        coVerify(exactly = 0) { itemRepository.restoreItem(any()) }
    }

    // -- Edit flow --

    @Test
    fun `editItem preserves listId, createdAt, and isPickedUp when updating editable fields`() = runTest {
        val existing = ShoppingItem(
            id = "1",
            listId = "L1",
            name = "Milk",
            quantity = "1",
            isPickedUp = true,
            categoryId = "old-cat",
            note = "old note",
            shop = "old shop",
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )
        itemsFlow.value = listOf(existing)
        coEvery { itemRepository.updateItem(any()) } returns Result.Success(Unit)

        val viewModel = buildViewModel()

        viewModel.editItem(
            itemId = "1",
            name = "Whole Milk",
            quantity = "2",
            categoryId = "new-cat",
            shop = "new shop",
            note = "new note",
        )

        coVerify {
            itemRepository.updateItem(match { updated ->
                updated.id == "1" &&
                    updated.listId == "L1" &&
                    updated.createdAt == 1_000L &&
                    updated.isPickedUp &&
                    updated.name == "Whole Milk" &&
                    updated.quantity == "2" &&
                    updated.categoryId == "new-cat" &&
                    updated.shop == "new shop" &&
                    updated.note == "new note"
            })
        }
    }

    @Test
    fun `editItem with unknown itemId does not call repository`() = runTest {
        itemsFlow.value = listOf(item("1"))

        val viewModel = buildViewModel()

        viewModel.editItem(
            itemId = "does-not-exist",
            name = "x",
            quantity = "1",
            categoryId = null,
            shop = null,
            note = null,
        )

        coVerify(exactly = 0) { itemRepository.updateItem(any()) }
    }

    @Test
    fun `editItem on success clears the editingItem state`() = runTest {
        val existing = item("1")
        itemsFlow.value = listOf(existing)
        coEvery { itemRepository.updateItem(any()) } returns Result.Success(Unit)

        val viewModel = buildViewModel()
        viewModel.onEditItemClick(existing)
        assertEquals(existing, viewModel.editingItem.value)

        viewModel.editItem(
            itemId = "1",
            name = "renamed",
            quantity = "1",
            categoryId = null,
            shop = null,
            note = null,
        )

        assertNull(viewModel.editingItem.value)
    }

    @Test
    fun `editItem on repository error keeps the dialog open and emits errorMessage`() = runTest {
        val existing = item("1")
        itemsFlow.value = listOf(existing)
        coEvery { itemRepository.updateItem(any()) } returns
            Result.Error(AppError.DatabaseError("write failed"))

        val viewModel = buildViewModel()
        viewModel.onEditItemClick(existing)

        viewModel.errorMessage.test {
            viewModel.editItem(
                itemId = "1",
                name = "renamed",
                quantity = "1",
                categoryId = null,
                shop = null,
                note = null,
            )
            assertEquals("write failed", awaitItem())
        }
        // Dialog stays open so the user can retry — editingItem is unchanged.
        assertEquals(existing, viewModel.editingItem.value)
    }

    // -- Auth gating --

    @Test
    fun `isAuthenticated reflects AuthStateManager`() {
        every { authStateManager.authState } returns
            MutableStateFlow(AuthState.Authenticated("u1"))
        val viewModel = buildViewModel()
        assertTrue(viewModel.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns false when unauthenticated`() {
        every { authStateManager.authState } returns
            MutableStateFlow(AuthState.Unauthenticated)
        val viewModel = buildViewModel()
        assertFalse(viewModel.isAuthenticated())
    }
}
