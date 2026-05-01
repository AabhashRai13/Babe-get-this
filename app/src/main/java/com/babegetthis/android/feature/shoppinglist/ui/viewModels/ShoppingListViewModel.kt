package com.babegetthis.android.feature.shoppinglist.ui.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.error.Result
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
        val active = lists.filter{
            !it.isCompleted
        }
        val completed = lists.filter {
            it.isCompleted
        }
        val displayed = if (tab == 0) active else completed

        val grouped = linkedMapOf<TimePeriod, MutableList<ShoppingList>>()
        for (list in displayed){
            val period = getTimePeriod(list.createdAt)
            grouped.getOrPut(period) { mutableListOf() }.add(list)
        }
        ShoppingListUiState(activeLists = active, completedLists = completed, selectedTab = tab, groupedLists = grouped, activeItemsToGet = active.sumOf { it.itemCount - it.completedItemCount })
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
                    _undoDeleteEvent.emit(listToDelete?.name ?: "List")
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    fun undoDeleteList() {
        viewModelScope.launch {
            val list = pendingDeleteList ?: return@launch
            val items = pendingDeleteItems
            pendingDeleteList = null
            pendingDeleteItems = emptyList()
            when (val result = repository.restoreListWithItems(list, items)) {
                is Result.Success -> { /* list + items reappear via Flow */ }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }
}