package com.babegetthis.android.feature.shoppinglist.ui.viewModels

import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.sync.data.repository.ShareRepository
import com.babegetthis.android.core.util.TimePeriod
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList
import com.babegetthis.android.testing.MainDispatcherRule
import com.babegetthis.android.testing.TestData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ShoppingListRepository>(relaxed = true)
    private val shareRepository = mockk<ShareRepository>(relaxed = true)
    private val authStateManager = mockk<AuthStateManager>(relaxed = true)
    private val authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    private val listsFlow = MutableStateFlow<List<ShoppingList>>(emptyList())

    // Separate from the main dispatcher on purpose. undoDeleteList must run on
    // the application scope, and holding this one back is how we prove it does —
    // see `undo runs on the application scope, not viewModelScope`.
    private val appDispatcher = StandardTestDispatcher()
    private val applicationScope = CoroutineScope(appDispatcher)

    // shoppingLists and uiState are stateIn(WhileSubscribed(5000)), so with no
    // downstream collector they never leave their initial value — uiState stays
    // blank and, more subtly, deleteList's `shoppingLists.value.find { }` always
    // misses. The screen supplies that subscription in production; a test has to
    // supply it too or it is exercising a ViewModel nobody is watching.
    private fun TestScope.viewModel(): ShoppingListViewModel {
        every { repository.getAllLists() } returns listsFlow
        every { authStateManager.authState } returns authState
        val vm = ShoppingListViewModel(repository, shareRepository, authStateManager, applicationScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.shoppingLists.collect { }
        }
        return vm
    }

    // Timestamps relative to the real clock, because the ViewModel calls
    // getTimePeriod() without an injected "now". Relative offsets keep these
    // assertions correct on any calendar day.
    private val now = System.currentTimeMillis()
    private fun list(
        id: String,
        name: String = "List $id",
        createdAt: Long = now,
        itemCount: Int = 0,
        completedItemCount: Int = 0,
    ) = TestData.list(
        id = id,
        name = name,
        createdAt = createdAt,
        itemCount = itemCount,
        completedItemCount = completedItemCount,
    )

    // --- 2.9 derived state ---

    @Test
    fun `starts on the active tab`() = runTest {
        assertEquals(0, viewModel().uiState.value.selectedTab)
    }

    @Test
    fun `setSelectedTab drives uiState`() = runTest {
        val vm = viewModel()

        vm.setSelectedTab(1)

        assertEquals(1, vm.uiState.value.selectedTab)
        assertFalse(vm.uiState.value.isActiveTab)
    }

    @Test
    fun `uiState splits active from completed`() = runTest {
        val vm = viewModel()
        listsFlow.value = listOf(
            list("a", itemCount = 2, completedItemCount = 1),   // active
            list("b", itemCount = 2, completedItemCount = 2),   // completed
            list("c"),                                          // empty → active
        )

        val state = vm.uiState.value
        assertEquals(listOf("a", "c"), state.activeLists.map { it.id })
        assertEquals(listOf("b"), state.completedLists.map { it.id })
    }

    @Test
    fun `activeItemsToGet sums outstanding items across active lists only`() = runTest {
        val vm = viewModel()
        listsFlow.value = listOf(
            list("a", itemCount = 5, completedItemCount = 2),   // 3 outstanding
            list("b", itemCount = 4, completedItemCount = 1),   // 3 outstanding
            list("c", itemCount = 2, completedItemCount = 2),   // completed, excluded
        )

        assertEquals(6, vm.uiState.value.activeItemsToGet)
    }

    @Test
    fun `activeItemsToGet is zero with no lists`() = runTest {
        assertEquals(0, viewModel().uiState.value.activeItemsToGet)
    }

    @Test
    fun `hasNoLists reflects both tabs being empty`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.hasNoLists)

        listsFlow.value = listOf(list("a"))

        assertFalse(vm.uiState.value.hasNoLists)
    }

    @Test
    fun `grouping preserves time-period order`() = runTest {
        val day = 24 * 60 * 60 * 1000L
        val vm = viewModel()
        // Emitted newest-first, the order getAllLists returns.
        listsFlow.value = listOf(
            list("today", createdAt = now),
            list("yesterday", createdAt = now - day),
            list("older", createdAt = now - 90 * day),
        )

        val keys = vm.uiState.value.groupedActive.keys.toList()

        assertEquals(listOf(TimePeriod.TODAY, TimePeriod.YESTERDAY, TimePeriod.OLDER), keys)
    }

    @Test
    fun `grouping buckets several lists under one period`() = runTest {
        val vm = viewModel()
        listsFlow.value = listOf(list("a"), list("b"))

        val today = vm.uiState.value.groupedActive.getValue(TimePeriod.TODAY)

        assertEquals(listOf("a", "b"), today.map { it.id })
    }

    // Both tabs are grouped every emission so the outgoing AnimatedContent pane
    // keeps its own data while sliding away.
    @Test
    fun `both tabs are grouped regardless of selection`() = runTest {
        val vm = viewModel()
        listsFlow.value = listOf(
            list("a", itemCount = 1),
            list("b", itemCount = 1, completedItemCount = 1),
        )

        assertEquals(1, vm.uiState.value.groupedActive.values.sumOf { it.size })
        assertEquals(1, vm.uiState.value.groupedCompleted.values.sumOf { it.size })
    }

    // --- 2.10 dialogs and events ---

    @Test
    fun `create dialog opens and dismisses`() = runTest {
        val vm = viewModel()

        vm.onCreateListClick()
        assertTrue(vm.showCreateDialog.value)

        vm.onDismissCreateDialog()
        assertFalse(vm.showCreateDialog.value)
    }

    @Test
    fun `edit dialog holds the list being edited`() = runTest {
        val vm = viewModel()
        val target = list("a")

        vm.onEditListClick(target)
        assertEquals(target, vm.editingList.value)

        vm.onDismissEditListDialog()
        assertNull(vm.editingList.value)
    }

    @Test
    fun `createList success navigates and closes the dialog`() = runTest {
        coEvery { repository.createList("Groceries") } returns Result.Success("new-id")
        val vm = viewModel()
        vm.onCreateListClick()

        vm.navigateToList.test {
            vm.createList("Groceries")
            assertEquals("new-id" to "Groceries", awaitItem())
        }
        assertFalse(vm.showCreateDialog.value)
    }

    @Test
    fun `createList failure surfaces the error and leaves the dialog open`() = runTest {
        coEvery { repository.createList(any()) } returns
            Result.Error(AppError.ValidationError("List name can't be empty."))
        val vm = viewModel()
        vm.onCreateListClick()

        vm.errorMessage.test {
            vm.createList("")
            assertEquals("List name can't be empty.", awaitItem())
        }
        assertTrue("dialog must stay open so the user can correct it", vm.showCreateDialog.value)
    }

    @Test
    fun `editList success closes the edit dialog`() = runTest {
        coEvery { repository.updateListName("a", "New") } returns Result.Success(Unit)
        val vm = viewModel()
        vm.onEditListClick(list("a"))

        vm.editList("a", "New")

        assertNull(vm.editingList.value)
    }

    @Test
    fun `editList failure surfaces the error and keeps the dialog open`() = runTest {
        coEvery { repository.updateListName(any(), any()) } returns
            Result.Error(AppError.NotFoundError("That list no longer exists."))
        val vm = viewModel()
        val target = list("a")
        vm.onEditListClick(target)

        vm.errorMessage.test {
            vm.editList("a", "New")
            assertEquals("That list no longer exists.", awaitItem())
        }
        assertEquals(target, vm.editingList.value)
    }

    @Test
    fun `showSnackBar emits the message verbatim`() = runTest {
        val vm = viewModel()

        vm.errorMessage.test {
            vm.showSnackBar("Signed out")
            assertEquals("Signed out", awaitItem())
        }
    }

    // --- 2.11 undo delete ---

    @Test
    fun `deleteList emits the undo event naming the list`() = runTest {
        coEvery { repository.deleteListAndCaptureItems("a") } returns Result.Success(emptyList())
        val vm = viewModel()
        listsFlow.value = listOf(list("a", name = "Groceries"))

        vm.undoDeleteEvent.test {
            vm.deleteList("a")
            assertEquals("Groceries", awaitItem())
        }
    }

    @Test
    fun `deleteList failure surfaces the error`() = runTest {
        coEvery { repository.deleteListAndCaptureItems("a") } returns
            Result.Error(AppError.DatabaseError())
        val vm = viewModel()
        listsFlow.value = listOf(list("a"))

        vm.errorMessage.test {
            vm.deleteList("a")
            assertEquals(AppError.DatabaseError().message, awaitItem())
        }
    }

    // Regression: an unknown id used to still emit an undo event, giving the user
    // an Undo button that silently did nothing when tapped.
    @Test
    fun `deleteList offers no undo when the list was never in state`() = runTest {
        coEvery { repository.deleteListAndCaptureItems("ghost") } returns Result.Success(emptyList())
        val vm = viewModel()

        vm.undoDeleteEvent.test {
            vm.deleteList("ghost")
            expectNoEvents()
        }
    }

    @Test
    fun `undoDeleteList restores the list with its captured items`() = runTest {
        val items = listOf(ShoppingItemEntity("i1", "a", "Milk", "1", false, null, null, null, 1L, 1L))
        coEvery { repository.deleteListAndCaptureItems("a") } returns Result.Success(items)
        coEvery { repository.restoreListWithItems(any(), any()) } returns Result.Success(Unit)
        val vm = viewModel()
        val target = list("a", name = "Groceries")
        listsFlow.value = listOf(target)

        vm.deleteList("a")
        vm.undoDeleteList()
        appDispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { repository.restoreListWithItems(target, items) }
    }

    @Test
    fun `undoDeleteList with nothing pending does nothing`() = runTest {
        val vm = viewModel()

        vm.undoDeleteList()
        appDispatcher.scheduler.runCurrent()

        coVerify(exactly = 0) { repository.restoreListWithItems(any(), any()) }
    }

    @Test
    fun `a second undo after a successful restore does nothing`() = runTest {
        coEvery { repository.deleteListAndCaptureItems("a") } returns Result.Success(emptyList())
        coEvery { repository.restoreListWithItems(any(), any()) } returns Result.Success(Unit)
        val vm = viewModel()
        listsFlow.value = listOf(list("a"))

        vm.deleteList("a")
        vm.undoDeleteList()
        appDispatcher.scheduler.runCurrent()
        vm.undoDeleteList()
        appDispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { repository.restoreListWithItems(any(), any()) }
    }

    @Test
    fun `undoDeleteList surfaces a restore failure`() = runTest {
        coEvery { repository.deleteListAndCaptureItems("a") } returns Result.Success(emptyList())
        coEvery { repository.restoreListWithItems(any(), any()) } returns
            Result.Error(AppError.DatabaseError())
        val vm = viewModel()
        listsFlow.value = listOf(list("a"))
        vm.deleteList("a")

        vm.errorMessage.test {
            vm.undoDeleteList()
            appDispatcher.scheduler.runCurrent()
            assertEquals(AppError.DatabaseError().message, awaitItem())
        }
    }

    // Regression: the pending cache used to be cleared before the restore was
    // attempted, so a failed restore discarded the only copy of the list and the
    // "try again" the user was shown was impossible to act on.
    @Test
    fun `a failed restore keeps the cache so undo can be retried`() = runTest {
        coEvery { repository.deleteListAndCaptureItems("a") } returns Result.Success(emptyList())
        coEvery { repository.restoreListWithItems(any(), any()) } returns
            Result.Error(AppError.DatabaseError())
        val vm = viewModel()
        listsFlow.value = listOf(list("a"))
        vm.deleteList("a")

        vm.undoDeleteList()
        appDispatcher.scheduler.runCurrent()
        vm.undoDeleteList()
        appDispatcher.scheduler.runCurrent()

        coVerify(exactly = 2) { repository.restoreListWithItems(any(), any()) }
    }

    // Proves the scope choice, not just the outcome: the restore is dispatched to
    // the injected application scope, so it is NOT tied to viewModelScope and
    // survives the user navigating away the instant they tap Undo. Holding
    // appDispatcher back means nothing runs until we say so — if this had been
    // launched on the (unconfined) main dispatcher it would already have fired.
    @Test
    fun `undo runs on the application scope, not viewModelScope`() = runTest {
        coEvery { repository.deleteListAndCaptureItems("a") } returns Result.Success(emptyList())
        coEvery { repository.restoreListWithItems(any(), any()) } returns Result.Success(Unit)
        val vm = viewModel()
        listsFlow.value = listOf(list("a"))
        vm.deleteList("a")

        vm.undoDeleteList()
        coVerify(exactly = 0) { repository.restoreListWithItems(any(), any()) }

        appDispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { repository.restoreListWithItems(any(), any()) }
    }

    // --- 2.12 voice ---

    @Test
    fun `createListWithVoice derives the name from the drafts`() = runTest {
        coEvery { repository.createListWithItems(any(), any()) } returns Result.Success("new-id")
        val vm = viewModel()
        val drafts = listOf(TestData.draft(name = "Milk"), TestData.draft(name = "Eggs"))

        vm.createListWithVoice(drafts)

        coVerify { repository.createListWithItems("Milk + 1 more", drafts) }
    }

    @Test
    fun `createListWithVoice navigates on success`() = runTest {
        coEvery { repository.createListWithItems(any(), any()) } returns Result.Success("new-id")
        val vm = viewModel()

        vm.navigateToList.test {
            vm.createListWithVoice(listOf(TestData.draft(name = "Milk")))
            assertEquals("new-id" to "Milk", awaitItem())
        }
    }

    @Test
    fun `createListWithVoice returns the result unchanged`() = runTest {
        coEvery { repository.createListWithItems(any(), any()) } returns Result.Success("new-id")
        val vm = viewModel()

        val result = vm.createListWithVoice(listOf(TestData.draft(name = "Milk")))

        assertEquals(Result.Success("new-id"), result)
    }

    // No snackbar and no navigation on failure — the voice sheet renders the
    // failure inline, so a second error surface would double up.
    @Test
    fun `createListWithVoice stays silent on failure`() = runTest {
        val failure = Result.Error(AppError.DatabaseError())
        coEvery { repository.createListWithItems(any(), any()) } returns failure
        val vm = viewModel()

        vm.navigateToList.test {
            val result = vm.createListWithVoice(listOf(TestData.draft(name = "Milk")))
            assertEquals(failure, result)
            expectNoEvents()
        }
    }

    // --- join a shared list ---

    @Test
    fun `join click opens the dialog when signed in`() = runTest {
        authState.value = AuthState.Authenticated("user-1")
        val vm = viewModel()

        vm.onJoinListClick()

        assertTrue(vm.showJoinDialog.value)
        assertFalse(vm.showJoinAuthPrompt.value)
    }

    @Test
    fun `join click prompts sign-in when signed out`() = runTest {
        authState.value = AuthState.Unauthenticated
        val vm = viewModel()

        vm.onJoinListClick()

        assertFalse(vm.showJoinDialog.value)
        assertTrue(vm.showJoinAuthPrompt.value)

        vm.onDismissJoinAuthPrompt()
        assertFalse(vm.showJoinAuthPrompt.value)
    }

    @Test
    fun `successful join closes the dialog`() = runTest {
        authState.value = AuthState.Authenticated("user-1")
        coEvery { shareRepository.join("ABC234") } returns Result.Success("list-9")
        val vm = viewModel()
        vm.onJoinListClick()

        vm.joinList("ABC234")

        assertFalse(vm.showJoinDialog.value)
        assertNull(vm.joinError.value)
        assertFalse(vm.joinInProgress.value)
    }

    @Test
    fun `failed join keeps the dialog open with the error inline`() = runTest {
        authState.value = AuthState.Authenticated("user-1")
        coEvery { shareRepository.join(any()) } returns
            Result.Error(AppError.NotFoundError("That code didn't match any list."))
        val vm = viewModel()
        vm.onJoinListClick()

        vm.joinList("XXXXXX")

        assertTrue(vm.showJoinDialog.value)
        assertEquals("That code didn't match any list.", vm.joinError.value)
        assertFalse(vm.joinInProgress.value)
    }

    @Test
    fun `reopening the join dialog clears a stale error`() = runTest {
        authState.value = AuthState.Authenticated("user-1")
        coEvery { shareRepository.join(any()) } returns
            Result.Error(AppError.NotFoundError("nope"))
        val vm = viewModel()
        vm.onJoinListClick()
        vm.joinList("XXXXXX")
        vm.onDismissJoinDialog()

        vm.onJoinListClick()

        assertNull(vm.joinError.value)
        assertTrue(vm.showJoinDialog.value)
    }
}
