package com.babegetthis.android.feature.shoppingitems.ui.viewModels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.babegetthis.android.core.model.Category
import com.babegetthis.android.testing.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertNull
import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.data.repository.CategoryRepository
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import com.babegetthis.android.core.sync.data.repository.ShareRepository
import com.babegetthis.android.core.sync.data.repository.SyncEngine
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import com.babegetthis.android.testing.FakeSharedListRemote
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
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.TelemetryMarkers
import com.babegetthis.android.core.telemetry.Marker
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.CategorySource
import com.babegetthis.android.core.telemetry.model.InputMethod
import io.mockk.verify

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
    private lateinit var pinRepository: com.babegetthis.android.core.pin.data.PinRepository
    private lateinit var shareRepository: ShareRepository
    private lateinit var syncEngine: SyncEngine
    private lateinit var sharedListRemote: FakeSharedListRemote
    private lateinit var analytics: AnalyticsRepository
    private lateinit var markers: TelemetryMarkers
    private lateinit var itemsFlow: MutableStateFlow<List<ShoppingItem>>
    private lateinit var shareCodeFlow: MutableStateFlow<String?>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        itemRepository = mockk(relaxed = true)
        categoryRepository = mockk(relaxed = true)
        authStateManager = mockk(relaxed = true)
        listRepository = mockk(relaxed = true)
        pinRepository = mockk(relaxed = true)
        shareRepository = mockk(relaxed = true)
        syncEngine = mockk(relaxed = true)
        sharedListRemote = FakeSharedListRemote()
        analytics = mockk(relaxed = true)
        // relaxed returns false for firstTime(), i.e. "already fired" — so
        // once-per-user events stay silent unless a test asks for them.
        markers = mockk(relaxed = true)
        itemsFlow = MutableStateFlow(emptyList())
        shareCodeFlow = MutableStateFlow(null)

        every { itemRepository.getItemsByListId(any()) } returns itemsFlow
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        every { authStateManager.authState } returns MutableStateFlow(AuthState.Unauthenticated)
        every { listRepository.getListById(any()) } returns MutableStateFlow(null)
        every { listRepository.getShareCode(any()) } returns shareCodeFlow
        every { pinRepository.pinExists } returns MutableStateFlow(false)
        coEvery { syncEngine.catchUp(any()) } returns Result.Success(Unit)
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
        pinRepository = pinRepository,
        shareRepository = shareRepository,
        syncEngine = syncEngine,
        sharedListRemote = sharedListRemote,
        analytics = analytics,
        markers = markers,
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

    // -- Derived state --

    // uiState and items are stateIn(WhileSubscribed), so nothing updates unless
    // something downstream is collecting. The screen supplies that in production.
    private fun TestScope.collecting(viewModel: ShoppingItemsViewModel): ShoppingItemsViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        return viewModel
    }

    @Test
    fun `uiState splits active from completed`() = runTest {
        val viewModel = collecting(buildViewModel())
        itemsFlow.value = listOf(item("a"), item("b", isPickedUp = true), item("c"))

        val state = viewModel.uiState.value
        assertEquals(listOf("a", "c"), state.activeItems.map { it.id })
        assertEquals(listOf("b"), state.completedItems.map { it.id })
        assertEquals(3, state.totalCount)
        assertEquals(1, state.completedCount)
    }

    @Test
    fun `uiState is empty for an empty list`() = runTest {
        val viewModel = collecting(buildViewModel())

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `activeSections groups by shop and buckets missing shops under null`() = runTest {
        val viewModel = collecting(buildViewModel())
        itemsFlow.value = listOf(
            item("a").copy(shop = "Aldi"),
            item("b").copy(shop = "Aldi"),
            item("c").copy(shop = null),
        )

        val sections = viewModel.uiState.value.activeSections
        val aldi = sections.first { it.shopName == "Aldi" }
        val noShop = sections.first { it.shopName == null }
        assertEquals(listOf("a", "b"), aldi.categories.flatMap { it.items }.map { it.id })
        assertEquals(listOf("c"), noShop.categories.flatMap { it.items }.map { it.id })
    }

    @Test
    fun `activeSections excludes picked-up items`() = runTest {
        val viewModel = collecting(buildViewModel())
        itemsFlow.value = listOf(
            item("a").copy(shop = "Aldi"),
            item("b", isPickedUp = true).copy(shop = "Aldi"),
        )

        val aldi = viewModel.uiState.value.activeSections.first { it.shopName == "Aldi" }
        assertEquals(listOf("a"), aldi.categories.flatMap { it.items }.map { it.id })
    }

    @Test
    fun `activeSections sorts categories alphabetically with uncategorized last`() = runTest {
        val viewModel = collecting(buildViewModel())
        itemsFlow.value = listOf(
            item("a").copy(shop = "Aldi", categoryName = "Snacks"),
            item("b").copy(shop = "Aldi", categoryName = "Bakery"),
            item("c").copy(shop = "Aldi", categoryName = null),
        )

        val aldi = viewModel.uiState.value.activeSections.first { it.shopName == "Aldi" }
        // Bakery before Snacks (alphabetical); the uncategorized bucket (null) last.
        assertEquals(listOf("Bakery", "Snacks", null), aldi.categories.map { it.label })
        assertEquals(listOf("c"), aldi.categories.last().items.map { it.id })
    }

    @Test
    fun `listId and listName come from SavedStateHandle`() {
        val viewModel = buildViewModel()

        assertEquals("L1", viewModel.listId)
        assertEquals("Groceries", viewModel.listName)
    }

    @Test
    fun `missing SavedStateHandle keys fall back to empty strings`() {
        val viewModel = ShoppingItemsViewModel(
            savedStateHandle = SavedStateHandle(),
            itemRepository = itemRepository,
            categoryRepository = categoryRepository,
            authStateManager = authStateManager,
            listRepository = listRepository,
            pinRepository = pinRepository,
            shareRepository = shareRepository,
            syncEngine = syncEngine,
            sharedListRemote = sharedListRemote,
            analytics = analytics,
            markers = markers,
            applicationScope = CoroutineScope(testDispatcher),
        )

        assertEquals("", viewModel.listId)
        assertEquals("", viewModel.listName)
    }

    // -- Lock behavior --

    // isLocked requires BOTH the row flag and a PIN existing, so a stale flag left
    // behind by a failed unlockAll can never lock the user out of their own data.
    @Test
    fun `isLocked requires both the row flag and an existing PIN`() = runTest {
        every { listRepository.getListById(any()) } returns
            MutableStateFlow(TestData.list(id = "L1", isLocked = true))
        every { pinRepository.pinExists } returns MutableStateFlow(false)
        val viewModel = buildViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isLocked.collect { }
        }

        assertFalse("a locked row with no PIN must not gate access", viewModel.isLocked.value)
    }

    @Test
    fun `isLocked is true when the row is locked and a PIN exists`() = runTest {
        every { listRepository.getListById(any()) } returns
            MutableStateFlow(TestData.list(id = "L1", isLocked = true))
        every { pinRepository.pinExists } returns MutableStateFlow(true)
        val viewModel = buildViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isLocked.collect { }
        }

        assertTrue(viewModel.isLocked.value)
    }

    @Test
    fun `session unlock and re-lock toggle sessionUnlocked`() {
        val viewModel = buildViewModel()

        viewModel.onSessionUnlocked()
        assertTrue(viewModel.sessionUnlocked.value)

        viewModel.lockSession()
        assertFalse(viewModel.sessionUnlocked.value)
    }

    // Locking happens while viewing the list, so the session stays unlocked —
    // otherwise the list you just locked would immediately re-prompt for the PIN.
    @Test
    fun `locking keeps the current session unlocked`() = runTest {
        val viewModel = buildViewModel()

        viewModel.setListLocked(true)

        assertTrue(viewModel.sessionUnlocked.value)
        coVerify { listRepository.setLocked("L1", true) }
    }

    @Test
    fun `unlocking does not touch the session flag`() = runTest {
        val viewModel = buildViewModel()

        viewModel.setListLocked(false)

        assertFalse(viewModel.sessionUnlocked.value)
        coVerify { listRepository.setLocked("L1", false) }
    }

    // The lock cannot be sidestepped by exporting to text.
    @Test
    fun `share is refused while locked and not yet verified`() = runTest {
        every { listRepository.getListById(any()) } returns
            MutableStateFlow(TestData.list(id = "L1", isLocked = true))
        every { pinRepository.pinExists } returns MutableStateFlow(true)
        val viewModel = buildViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isLocked.collect { }
        }

        viewModel.events.test {
            viewModel.onShareClick()
            expectNoEvents()
        }
    }

    @Test
    fun `share is allowed once the session is verified`() = runTest {
        every { listRepository.getListById(any()) } returns
            MutableStateFlow(TestData.list(id = "L1", isLocked = true))
        every { pinRepository.pinExists } returns MutableStateFlow(true)
        val viewModel = collecting(buildViewModel())
        itemsFlow.value = listOf(item("a", name = "Milk"))
        viewModel.onSessionUnlocked()

        viewModel.events.test {
            viewModel.onShareClick()
            val event = awaitItem()
            assertTrue(event is ShoppingItemsViewModel.UiEvent.ShareList)
            assertTrue((event as ShoppingItemsViewModel.UiEvent.ShareList).text.contains("Milk"))
        }
    }

    @Test
    fun `share on an unlocked list emits the formatted text`() = runTest {
        val viewModel = collecting(buildViewModel())
        itemsFlow.value = listOf(item("a", name = "Milk"))

        viewModel.events.test {
            viewModel.onShareClick()
            val event = awaitItem() as ShoppingItemsViewModel.UiEvent.ShareList
            assertTrue(event.text.contains("Groceries"))
            assertTrue(event.text.contains("Milk"))
        }
    }

    // -- Dialog state --

    @Test
    fun `add dialog opens and dismisses`() {
        val viewModel = buildViewModel()

        viewModel.onAddItemClick()
        assertTrue(viewModel.showAddItemDialog.value)

        viewModel.onDismissAddItemDialog()
        assertFalse(viewModel.showAddItemDialog.value)
    }

    @Test
    fun `edit dialog holds the item being edited`() {
        val viewModel = buildViewModel()
        val target = item("a")

        viewModel.onEditItemClick(target)
        assertEquals(target, viewModel.editingItem.value)

        viewModel.onDismissEditItemDialog()
        assertNull(viewModel.editingItem.value)
    }

    // -- addItem / togglePickedUp / addCategory --

    @Test
    fun `addItem success closes the dialog`() = runTest {
        coEvery { itemRepository.addItem(any(), any(), any(), any(), any(), any()) } returns
            Result.Success("new-id")
        val viewModel = buildViewModel()
        viewModel.onAddItemClick()

        viewModel.addItem("Milk", "2", null, null, null)

        assertFalse(viewModel.showAddItemDialog.value)
        coVerify { itemRepository.addItem("L1", "Milk", "2", null, null, null) }
    }

    @Test
    fun `addItem failure surfaces the error and keeps the dialog open`() = runTest {
        coEvery { itemRepository.addItem(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(AppError.ValidationError("Item name can't be empty."))
        val viewModel = buildViewModel()
        viewModel.onAddItemClick()

        viewModel.errorMessage.test {
            viewModel.addItem("", "", null, null, null)
            assertEquals("Item name can't be empty.", awaitItem())
        }
        assertTrue(viewModel.showAddItemDialog.value)
    }

    @Test
    fun `togglePickedUp delegates to the repository`() = runTest {
        coEvery { itemRepository.togglePickedUp(any(), any()) } returns Result.Success(Unit)
        val viewModel = buildViewModel()

        viewModel.togglePickedUp("a", true)

        coVerify { itemRepository.togglePickedUp("a", true) }
    }

    @Test
    fun `togglePickedUp failure surfaces the error`() = runTest {
        coEvery { itemRepository.togglePickedUp(any(), any()) } returns
            Result.Error(AppError.DatabaseError())
        val viewModel = buildViewModel()

        viewModel.errorMessage.test {
            viewModel.togglePickedUp("a", true)
            assertEquals(AppError.DatabaseError().message, awaitItem())
        }
    }

    // The callback receives a Category built locally from the name passed in,
    // not a read-back of what was stored. Pinned so that if the repository ever
    // normalises names, this stops silently disagreeing with the database.
    @Test
    fun `addCategory hands the new category to its callback`() = runTest {
        coEvery { categoryRepository.addCategory("Dairy") } returns Result.Success("cat-1")
        val viewModel = buildViewModel()
        var created: Category? = null

        viewModel.addCategory("Dairy") { created = it }

        assertEquals(Category(id = "cat-1", name = "Dairy", isDefault = false), created)
    }

    @Test
    fun `addCategory failure surfaces the error and skips the callback`() = runTest {
        coEvery { categoryRepository.addCategory(any()) } returns
            Result.Error(AppError.DatabaseError())
        val viewModel = buildViewModel()
        var called = false

        viewModel.errorMessage.test {
            viewModel.addCategory("Dairy") { called = true }
            assertEquals(AppError.DatabaseError().message, awaitItem())
        }
        assertFalse(called)
    }

    // -- Voice --

    @Test
    fun `addItemsWithVoice delegates and returns the result unchanged`() = runTest {
        val expected = Result.Success("L1")
        coEvery { listRepository.addItemsToList(any(), any()) } returns expected
        val viewModel = buildViewModel()
        val drafts = listOf(TestData.draft(name = "Milk"))

        val result = viewModel.addItemsWithVoice(drafts)

        assertEquals(expected, result)
        coVerify { listRepository.addItemsToList("L1", drafts) }
    }

    // -- Undo regressions fixed in task 4.2 --

    @Test
    fun `deleteItem offers no undo when the item was never in state`() = runTest {
        coEvery { itemRepository.deleteItem("ghost") } returns Result.Success(Unit)
        val viewModel = collecting(buildViewModel())

        viewModel.undoDeleteEvent.test {
            viewModel.deleteItem("ghost")
            expectNoEvents()
        }
    }

    // A failed restore used to clear the cache anyway, so the error message told
    // the user to retry something that was already unrecoverable.
    @Test
    fun `a failed restore keeps the cache so undo can be retried`() = runTest {
        coEvery { itemRepository.deleteItem("a") } returns Result.Success(Unit)
        coEvery { itemRepository.restoreItem(any()) } returns Result.Error(AppError.DatabaseError())
        val viewModel = collecting(buildViewModel())
        itemsFlow.value = listOf(item("a"))
        viewModel.deleteItem("a")

        viewModel.undoDeleteItem()
        viewModel.undoDeleteItem()

        coVerify(exactly = 2) { itemRepository.restoreItem(any()) }
    }

    // -- Teardown: the highest-consequence path in the app --

    // Leaving a list with nothing in it cleans the list up.
    @Test
    fun `onCleared deletes a list left empty`() = runTest {
        coEvery { listRepository.deleteListIfEmpty(any()) } returns Result.Success(true)
        val viewModel = buildViewModel()

        viewModel.invokeOnCleared()

        coVerify { listRepository.deleteListIfEmpty("L1") }
    }

    // The dangerous interleaving. Undo is tapped on the snackbar and the user
    // backs out in the same breath: the restore runs on applicationScope (so it
    // survives viewModelScope being cancelled), and onCleared joins it BEFORE
    // asking whether the list is empty. Without that join the emptiness check
    // races the restore, sees zero items, and deletes the very list the user
    // just rescued — along with the item they restored.
    @Test
    fun `onCleared waits for an in-flight undo before deciding the list is empty`() = runTest {
        val restoreGate = CompletableDeferred<Unit>()
        coEvery { itemRepository.deleteItem("a") } returns Result.Success(Unit)
        coEvery { itemRepository.restoreItem(any()) } coAnswers {
            restoreGate.await()
            Result.Success(Unit)
        }
        coEvery { listRepository.deleteListIfEmpty(any()) } returns Result.Success(false)

        val viewModel = collecting(buildViewModel())
        itemsFlow.value = listOf(item("a"))
        viewModel.deleteItem("a")

        viewModel.undoDeleteItem()
        viewModel.invokeOnCleared()

        // The restore is still parked on the gate, so the emptiness check must
        // not have run yet.
        coVerify(exactly = 0) { listRepository.deleteListIfEmpty(any()) }

        restoreGate.complete(Unit)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { listRepository.deleteListIfEmpty("L1") }
    }

    @Test
    fun `onCleared proceeds immediately when no undo is in flight`() = runTest {
        coEvery { listRepository.deleteListIfEmpty(any()) } returns Result.Success(true)
        val viewModel = buildViewModel()

        viewModel.invokeOnCleared()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { listRepository.deleteListIfEmpty("L1") }
    }

    // --- live sharing ---

    @Test
    fun `opening a shared list catches up and re-checks on every realtime event`() = runTest {
        shareCodeFlow.value = "ABC234"

        buildViewModel()
        coVerify(exactly = 1) { syncEngine.catchUp("L1") }

        sharedListRemote.changeEvents.emit(Unit)
        sharedListRemote.changeEvents.emit(Unit)
        coVerify(exactly = 3) { syncEngine.catchUp("L1") }
    }

    @Test
    fun `a local-only list never touches the sync engine`() = runTest {
        buildViewModel()

        coVerify(exactly = 0) { syncEngine.catchUp(any()) }
    }

    @Test
    fun `a list shared while open starts syncing on the spot`() = runTest {
        buildViewModel()
        coVerify(exactly = 0) { syncEngine.catchUp(any()) }

        shareCodeFlow.value = "ABC234"

        coVerify(exactly = 1) { syncEngine.catchUp("L1") }
    }

    @Test
    fun `share live on a locked list does nothing until unlocked`() = runTest {
        every { listRepository.getListById("L1") } returns
            MutableStateFlow(TestData.list(id = "L1", isLocked = true))
        every { pinRepository.pinExists } returns MutableStateFlow(true)
        every { authStateManager.authState } returns
            MutableStateFlow<AuthState>(AuthState.Authenticated("user-1"))
        val viewModel = buildViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isLocked.collect { }
        }

        viewModel.onShareLiveClick()

        coVerify(exactly = 0) { shareRepository.share(any()) }
        assertFalse(viewModel.showShareAuthPrompt.value)
    }

    @Test
    fun `share live prompts sign-in when signed out`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onShareLiveClick()

        assertTrue(viewModel.showShareAuthPrompt.value)
        coVerify(exactly = 0) { shareRepository.share(any()) }

        viewModel.onDismissShareAuthPrompt()
        assertFalse(viewModel.showShareAuthPrompt.value)
    }

    @Test
    fun `share live shows the code dialog on success`() = runTest {
        every { authStateManager.authState } returns
            MutableStateFlow<AuthState>(AuthState.Authenticated("user-1"))
        coEvery { shareRepository.share("L1") } returns Result.Success("ABC234")
        val viewModel = buildViewModel()

        viewModel.onShareLiveClick()

        assertEquals("ABC234", viewModel.shareCodeDialog.value)

        viewModel.onDismissShareCodeDialog()
        assertNull(viewModel.shareCodeDialog.value)
    }

    @Test
    fun `share live surfaces failures as a snackbar`() = runTest {
        every { authStateManager.authState } returns
            MutableStateFlow<AuthState>(AuthState.Authenticated("user-1"))
        coEvery { shareRepository.share("L1") } returns
            Result.Error(AppError.NetworkError())
        val viewModel = buildViewModel()

        viewModel.errorMessage.test {
            viewModel.onShareLiveClick()
            assertEquals(AppError.NetworkError().message, awaitItem())
        }
        assertNull(viewModel.shareCodeDialog.value)
    }

    // -- Telemetry --
    //
    // These assert the two properties that make the analytics worth having:
    // once-only events fire once, and no user content is ever a parameter.

    @Test
    fun `a manual add reports the input method and who chose the category`() = runTest {
        coEvery {
            itemRepository.addItem(any(), any(), any(), any(), any(), any())
        } returns Result.Success("i1")
        val viewModel = buildViewModel()

        viewModel.addItem("Milk", "1", "cat-dairy-eggs", null, null)
        viewModel.addItem("Eggs", "6", null, null, null)

        verify {
            analytics.track(AnalyticsEvent.ItemAdded(InputMethod.Manual, CategorySource.User))
            analytics.track(AnalyticsEvent.ItemAdded(InputMethod.Manual, CategorySource.None))
        }
    }

    @Test
    fun `activation is reported on the first item only`() = runTest {
        coEvery {
            itemRepository.addItem(any(), any(), any(), any(), any(), any())
        } returns Result.Success("i1")
        // The marker claims the first call and refuses the rest.
        every { markers.firstTime(Marker.FirstItemAdded, any()) } returnsMany listOf(true, false)
        val viewModel = buildViewModel()

        viewModel.addItem("Milk", "1", null, null, null)
        viewModel.addItem("Eggs", "6", null, null, null)

        verify(exactly = 1) {
            analytics.track(AnalyticsEvent.FirstItemAdded(InputMethod.Manual))
        }
    }

    @Test
    fun `voice items report one add and one auto-category each`() = runTest {
        coEvery { listRepository.addItemsToList(any(), any()) } returns Result.Success("L1")
        val viewModel = buildViewModel()

        viewModel.addItemsWithVoice(
            listOf(
                TestData.draft(name = "Milk", category = "cat-dairy-eggs"),
                TestData.draft(name = "Something odd", category = null),
            ),
        )

        verify {
            analytics.track(AnalyticsEvent.ItemAdded(InputMethod.Voice, CategorySource.Auto))
            analytics.track(AnalyticsEvent.ItemAdded(InputMethod.Voice, CategorySource.None))
            analytics.track(AnalyticsEvent.CategoryAutoAssigned("cat-dairy-eggs"))
            analytics.track(AnalyticsEvent.CategoryAutoAssigned(null))
        }
    }

    @Test
    fun `checking an item off is reported, un-checking is not`() = runTest {
        coEvery { itemRepository.togglePickedUp(any(), any()) } returns Result.Success(Unit)
        val viewModel = buildViewModel()

        viewModel.togglePickedUp("i1", isPickedUp = true)
        viewModel.togglePickedUp("i1", isPickedUp = false)

        // Un-checking is a correction. Counting it would inflate the shopping
        // activity this event exists to measure.
        verify(exactly = 1) { analytics.track(AnalyticsEvent.ItemCheckedOff) }
    }

    @Test
    fun `changing a category reports the correction with both taxonomy ids`() = runTest {
        itemsFlow.value = listOf(item("1").copy(categoryId = "cat-dairy-eggs"))
        coEvery { itemRepository.updateItem(any()) } returns Result.Success(Unit)
        val viewModel = buildViewModel()

        viewModel.editItem("1", "Milk", "1", "cat-frozen-foods", null, null)

        verify {
            analytics.track(
                AnalyticsEvent.CategoryCorrected("cat-dairy-eggs", "cat-frozen-foods"),
            )
        }
    }

    @Test
    fun `an edit that leaves the category alone is not a correction`() = runTest {
        itemsFlow.value = listOf(item("1").copy(categoryId = "cat-dairy-eggs"))
        coEvery { itemRepository.updateItem(any()) } returns Result.Success(Unit)
        val viewModel = buildViewModel()

        // Renaming only. Reporting this would make the taxonomy look far worse
        // than it is.
        viewModel.editItem("1", "Whole milk", "1", "cat-dairy-eggs", null, null)

        verify(exactly = 0) { analytics.track(ofType<AnalyticsEvent.CategoryCorrected>()) }
    }

    @Test
    fun `re-opening the share dialog does not report a second share`() = runTest {
        every { authStateManager.authState } returns MutableStateFlow(AuthState.Authenticated("u1"))
        coEvery { shareRepository.share("L1") } returns Result.Success("ABC123")
        // share() hands back the EXISTING code on every call after the first,
        // so only the marker can tell a new share from a re-open.
        every { markers.firstTime(Marker.ShareCodeCreated, "L1") } returnsMany listOf(true, false)
        val viewModel = buildViewModel()

        viewModel.onShareLiveClick()
        viewModel.onShareLiveClick()

        verify(exactly = 1) { analytics.track(AnalyticsEvent.ShareCodeCreated) }
    }

    @Test
    fun `copying the code is what counts as sharing it`() {
        buildViewModel().onShareCodeCopied()

        verify { analytics.track(AnalyticsEvent.ShareCodeShared) }
    }

    @Test
    fun `only the joining device reports a joiner's first edit, and only once`() = runTest {
        every { markers.has(Marker.JoinedList, "L1") } returns true
        every { markers.firstTime(Marker.SharedListFirstEdit, "L1") } returnsMany
            listOf(true, false)
        coEvery { itemRepository.togglePickedUp(any(), any()) } returns Result.Success(Unit)
        val viewModel = buildViewModel()

        viewModel.togglePickedUp("i1", isPickedUp = true)
        viewModel.togglePickedUp("i2", isPickedUp = true)

        verify(exactly = 1) { analytics.track(AnalyticsEvent.SharedListFirstEditByJoiner) }
    }

    @Test
    fun `the owner's device never reports a joiner's first edit`() = runTest {
        // Owner and joiner devices are identical once the list syncs — this is
        // the marker written at join time doing the only work that can tell
        // them apart.
        every { markers.has(Marker.JoinedList, "L1") } returns false
        coEvery { itemRepository.togglePickedUp(any(), any()) } returns Result.Success(Unit)
        val viewModel = buildViewModel()

        viewModel.togglePickedUp("i1", isPickedUp = true)

        verify(exactly = 0) { analytics.track(AnalyticsEvent.SharedListFirstEditByJoiner) }
    }

    @Test
    fun `completing a list reports the trip, and the first one reports activation`() = runTest {
        every { markers.firstTime(Marker.FirstListCompleted, any()) } returns true
        val viewModel = buildViewModel()

        viewModel.events.test {
            itemsFlow.value = listOf(item("1"), item("2"))
            expectNoEvents()
            itemsFlow.value = listOf(
                item("1", isPickedUp = true),
                item("2", isPickedUp = true),
            )
            awaitItem()
        }

        verify {
            analytics.track(AnalyticsEvent.ListCompleted(itemCount = 2))
            analytics.track(AnalyticsEvent.FirstListCompleted(itemCount = 2))
        }
    }

    // onCleared is protected on ViewModel, so reach it the way the framework
    // would. Reflection is confined to this one helper rather than sprinkled
    // through the tests that need it.
    private fun ShoppingItemsViewModel.invokeOnCleared() {
        ViewModel::class.java.getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(this)
    }
}
