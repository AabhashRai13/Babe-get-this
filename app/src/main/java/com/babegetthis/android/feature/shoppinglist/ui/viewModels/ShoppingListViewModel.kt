package com.babegetthis.android.feature.shoppinglist.ui.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.data.di.ApplicationScope
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.sync.data.repository.ShareRepository
import kotlinx.coroutines.CoroutineScope
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList
import com.babegetthis.android.feature.shoppinglist.model.ShoppingListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.babegetthis.android.core.util.TimePeriod
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import com.babegetthis.android.core.util.getTimePeriod

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingListRepository,
    private val shareRepository: ShareRepository,
    private val authStateManager: AuthStateManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    // Which tab is selected: 0 = Active, 1 = Completed.
    // Lives in the VM (not the screen) so derived state can react to it.
    private val _selectedTab = MutableStateFlow(0)

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    val shoppingLists: StateFlow<List<ShoppingList>> = repository.getAllLists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<ShoppingListUiState> = combine(
        shoppingLists,
        _selectedTab,
    ) {
        lists, tab ->
        val active = lists.filter { !it.isCompleted }
        val completed = lists.filter { it.isCompleted }

        // Group BOTH tabs up front so the AnimatedContent on the screen
        // can render the outgoing pane with its own data during a tab
        // swap, instead of both panes snapping to whichever tab is now
        // selected. linkedMapOf preserves insertion order so time-period
        // headers (Today / This week / Older) stay in the right sequence.
        fun groupByPeriod(source: List<ShoppingList>): Map<TimePeriod, List<ShoppingList>> {
            val out = linkedMapOf<TimePeriod, MutableList<ShoppingList>>()
            for (list in source) {
                val period = getTimePeriod(list.createdAt)
                out.getOrPut(period) { mutableListOf() }.add(list)
            }
            return out
        }

        ShoppingListUiState(
            activeLists = active,
            completedLists = completed,
            selectedTab = tab,
            groupedActive = groupByPeriod(active),
            groupedCompleted = groupByPeriod(completed),
            activeItemsToGet = active.sumOf { it.itemCount - it.completedItemCount },
        )
    }.stateIn(
        scope = viewModelScope,
        started =  SharingStarted.WhileSubscribed(5000),
        initialValue = ShoppingListUiState()
    )

    val showCreateDialog = MutableStateFlow(false)

    // When non-null, the edit dialog is shown for this list
    val editingList = MutableStateFlow<ShoppingList?>(null)

    // Undo delete — cache both the list and its items, because the
    // shopping_items CASCADE wipes items when the list row is deleted.
    // Without caching items, undo would restore an empty list and the
    // derived isCompleted flag would always come back as false.
    private var pendingDeleteList: ShoppingList? = null
    private var pendingDeleteItems: List<ShoppingItemEntity> = emptyList()

    // One-time error events — like showing a snackBar.
    // SharedFlow fires once and is consumed, unlike StateFlow which replays.
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    private val _undoDeleteEvent = MutableSharedFlow<String>()
    val undoDeleteEvent = _undoDeleteEvent.asSharedFlow()

    private val _navigateToList = MutableSharedFlow<Pair<String, String>>()
    val navigateToList = _navigateToList.asSharedFlow()

    // Show a snack bar message — used by the profile bottom sheet to surface toasts
    fun showSnackBar(message: String) {
        viewModelScope.launch {
            _errorMessage.emit(message)
        }
    }

    // --- Join a shared list by code ---

    val showJoinDialog = MutableStateFlow(false)
    val showJoinAuthPrompt = MutableStateFlow(false)
    val joinInProgress = MutableStateFlow(false)
    val joinError = MutableStateFlow<String?>(null)

    fun onJoinListClick() {
        if (authStateManager.authState.value is AuthState.Authenticated) {
            joinError.value = null
            showJoinDialog.value = true
        } else {
            showJoinAuthPrompt.value = true
        }
    }

    fun onDismissJoinDialog() {
        showJoinDialog.value = false
    }

    fun onDismissJoinAuthPrompt() {
        showJoinAuthPrompt.value = false
    }

    fun joinList(code: String) {
        viewModelScope.launch {
            joinInProgress.value = true
            joinError.value = null
            when (val result = shareRepository.join(code)) {
                // The replica landed in Room — the list appears via the
                // existing Flow, no navigation or refresh needed.
                is Result.Success -> showJoinDialog.value = false
                is Result.Error -> joinError.value = result.error.message
            }
            joinInProgress.value = false
        }
    }

    fun onCreateListClick() {
        showCreateDialog.value = true
    }

    fun onDismissCreateDialog() {
        showCreateDialog.value = false
    }

    fun createList(name: String) {
        viewModelScope.launch {
            when (val result = repository.createList(name)) {
                is Result.Success -> {
                    showCreateDialog.value = false
                    _navigateToList.emit(result.data to name)
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    fun onEditListClick(list: ShoppingList) {
        editingList.value = list
    }

    fun onDismissEditListDialog() {
        editingList.value = null
    }

    fun editList(listId: String, newName: String) {
        viewModelScope.launch {
            when (val result = repository.updateListName(listId, newName)) {
                is Result.Success -> {
                    editingList.value = null
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            val listToDelete = shoppingLists.value.find { it.id == listId }
            when (val result = repository.deleteListAndCaptureItems(listId)) {
                is Result.Success -> {
                    pendingDeleteList = listToDelete
                    pendingDeleteItems = result.data
                    // Only offer Undo when we actually captured the list to
                    // restore. Previously a miss on find() still emitted the
                    // undo event, so the user got an Undo button that did
                    // nothing at all when tapped — undoDeleteList() returns
                    // early on a null pending list, silently and with no
                    // feedback. Don't advertise an action we can't honour.
                    if (listToDelete != null) {
                        _undoDeleteEvent.emit(listToDelete.name)
                    }
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    // Voice flow's persist callback — invoked by the voice VM with the parsed
    // drafts. There's no review step, so the name is derived from the items here
    // (not entered by the user). Returns the new list id (so the voice VM can
    // transition to Done) and side-effect-emits navigateToList so the existing
    // screen-level collector handles navigation. No snackbar on error — the voice
    // sheet renders the failure inline in its Failed state.
    suspend fun createListWithVoice(drafts: List<ItemDraft>): Result<String> {
        val name = autoNameVoiceList(drafts)
        val result = repository.createListWithItems(name, drafts)
        if (result is Result.Success) {
            _navigateToList.emit(result.data to name)
        }
        return result
    }

    // applicationScope, NOT viewModelScope — the same reasoning
    // ShoppingItemsViewModel.undoDeleteItem already spells out for items, which
    // this (destroying strictly more data) somehow never got. Undo is tapped on
    // a snackbar, and tapping it then immediately navigating away is a completely
    // ordinary gesture; on viewModelScope that cancels the restore mid-flight and
    // the list plus every item it held is gone for good, with no second chance.
    fun undoDeleteList() {
        applicationScope.launch {
            val list = pendingDeleteList ?: return@launch
            val items = pendingDeleteItems
            when (val result = repository.restoreListWithItems(list, items)) {
                is Result.Success -> {
                    // Clear only now that the data is safely back. Clearing up
                    // front (as this used to) meant a failed restore discarded
                    // the only copy of the list — the error snackbar told the
                    // user to try again, and there was nothing left to retry.
                    pendingDeleteList = null
                    pendingDeleteItems = emptyList()
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }
}

// Name an auto-created voice list from its first item — nobody wants to name a
// grocery list. Single item → just its name; multiple → "Milk + 2 more".
// Deriving from contents also fixes the same-day collision the old date-based
// name had (three lists made today are no longer all "List · 24 Jun").
//
// Top-level + internal (not a private method) so it's unit-testable without
// constructing the whole ViewModel — it's pure: input drafts → output name.
internal fun autoNameVoiceList(drafts: List<ItemDraft>): String {
    // Cap the first item's name so a mis-parsed, paragraph-length item[0] can't
    // produce an absurd list title.
    val first = drafts.firstOrNull()?.name?.trim().orEmpty().take(40).ifBlank { "List" }
    val others = drafts.size - 1
    return if (others > 0) "$first + $others more" else first
}