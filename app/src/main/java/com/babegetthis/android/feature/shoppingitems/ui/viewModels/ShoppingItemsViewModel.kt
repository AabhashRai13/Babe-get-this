package com.babegetthis.android.feature.shoppingitems.ui.viewModels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.data.di.ApplicationScope
import com.babegetthis.android.core.data.repository.CategoryRepository
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.model.Category
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItemsUiState
import com.babegetthis.android.feature.shoppingitems.share.ShoppingListShareText
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingItemsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itemRepository: ShoppingItemRepository,
    private val categoryRepository: CategoryRepository,
    private val authStateManager: AuthStateManager,
    private val listRepository: ShoppingListRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    val listId: String = savedStateHandle.get<String>("listId") ?: ""
    val listName: String = savedStateHandle.get<String>("listName") ?: ""

    // True only when we arrived here straight from creating this list (voice or
    // the type flow pass isNew=true). Used to gate the auto-delete-if-empty in
    // onCleared so we only clean up lists the user just made and abandoned — not
    // an existing empty list they happened to open and back out of.
    private val isNewlyCreated: Boolean = savedStateHandle.get<Boolean>("isNew") ?: false

    // Check if the user is logged in — used to gate the share feature.
    fun isAuthenticated(): Boolean {
        return authStateManager.authState.value is AuthState.Authenticated
    }

    val items: StateFlow<List<ShoppingItem>> = itemRepository.getItemsByListId(listId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Derived UI state: filter + groupBy run once per upstream emission
    // here, instead of on every recomposition in the screen. Keeps the
    // hot animation paths (ProgressCard color pulse) cheap.
    val uiState: StateFlow<ShoppingItemsUiState> = items
        .map { list ->
            val active = list.filter { !it.isPickedUp }
            ShoppingItemsUiState(
                items = list,
                activeItems = active,
                completedItems = list.filter { it.isPickedUp },
                activeByShop = active.groupBy { it.shop ?: "" },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ShoppingItemsUiState(),
        )

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val showAddItemDialog = MutableStateFlow(false)

    // When non-null, the edit dialog is shown pre-filled with this item's data
    val editingItem = MutableStateFlow<ShoppingItem?>(null)

    // Undo delete — temporarily stores the deleted item so it can be restored
    private var pendingDeleteItem: ShoppingItem? = null

    // Events for the UI to show snackbars (errors + undo)
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    private val _undoDeleteEvent = MutableSharedFlow<String>() // emits item name
    val undoDeleteEvent = _undoDeleteEvent.asSharedFlow()

    // One-shot UI events the screen consumes (e.g. fire a Success haptic
    // when the list just became all-done). SharedFlow keeps the pattern
    // consistent with errorMessage / undoDeleteEvent above.
    // Flutter analogue: a one-off Stream the View listens to and reacts to.
    sealed class UiEvent {
        data object ListJustCompleted : UiEvent()
        data class ShareList(val text: String) : UiEvent()
    }
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    init {
        // Watch the items flow and detect the TRANSITION from "not all done"
        // to "all done". We only emit on the transition itself — not on
        // initial load of an already-completed list, otherwise opening any
        // finished list would buzz.
        viewModelScope.launch {
            var wasAllDone: Boolean? = null
            items.collect { itemList ->
                val isAllDone = itemList.isNotEmpty() && itemList.all { it.isPickedUp }
                if (wasAllDone == false && isAllDone) {
                    _events.emit(UiEvent.ListJustCompleted)
                }
                wasAllDone = isAllDone
            }
        }
    }

    fun onAddItemClick() {
        showAddItemDialog.value = true
    }

    fun onDismissAddItemDialog() {
        showAddItemDialog.value = false
    }

    fun onEditItemClick(item: ShoppingItem) {
        editingItem.value = item
    }

    fun onDismissEditItemDialog() {
        editingItem.value = null
    }

    fun addItem(name: String, quantity: String, categoryId: String?, shop: String?, note: String?) {
        viewModelScope.launch {
            when (val result = itemRepository.addItem(
                listId = listId,
                name = name,
                quantity = quantity,
                categoryId = categoryId,
                shop = shop,
                note = note,
            )) {
                is Result.Success -> {
                    showAddItemDialog.value = false
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    // Voice persist lambda for "add items to this list". The voice sheet calls
    // this with the parsed drafts; we append them to the current list and return
    // its id so the voice VM transitions to Done. No navigation — the user is
    // already here, and the new rows appear via the items Flow (with an insert
    // animation in the screen). Mirrors ShoppingListViewModel.createListWithVoice.
    suspend fun addItemsWithVoice(drafts: List<ItemDraft>): Result<String> {
        return listRepository.addItemsToList(listId, drafts)
    }

    fun editItem(
        itemId: String,
        name: String,
        quantity: String,
        categoryId: String?,
        shop: String?,
        note: String?,
    ) {
        viewModelScope.launch {
            // Find the current item so we preserve fields like listId, createdAt, isPickedUp
            val currentItem = items.value.find { it.id == itemId } ?: return@launch
            val updatedItem = currentItem.copy(
                name = name,
                quantity = quantity,
                categoryId = categoryId,
                shop = shop,
                note = note,
            )
            when (val result = itemRepository.updateItem(updatedItem)) {
                is Result.Success -> {
                    editingItem.value = null
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    fun addCategory(name: String, onCategoryCreated: (Category) -> Unit) {
        viewModelScope.launch {
            when (val result = categoryRepository.addCategory(name)) {
                is Result.Success -> {
                    onCategoryCreated(Category(id = result.data, name = name, isDefault = false))
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    fun togglePickedUp(itemId: String, isPickedUp: Boolean) {
        viewModelScope.launch {
            when (val result = itemRepository.togglePickedUp(itemId, isPickedUp)) {
                is Result.Success -> { /* UI auto-updates via Flow */ }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            // Cache the item before deleting so we can restore on undo
            val itemToDelete = items.value.find { it.id == itemId }
            when (val result = itemRepository.deleteItem(itemId)) {
                is Result.Success -> {
                    pendingDeleteItem = itemToDelete
                    _undoDeleteEvent.emit(itemToDelete?.name ?: "Item")
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    // Builds the share text off the current item snapshot and emits it for the
    // screen to hand to ACTION_SEND. No Context here — the ViewModel only
    // produces the String; the screen owns the Intent.
    fun onShareClick() {
        val text = ShoppingListShareText.format(listName, items.value)
        viewModelScope.launch { _events.emit(UiEvent.ShareList(text)) }
    }

    fun undoDeleteItem() {
        viewModelScope.launch {
            val item = pendingDeleteItem ?: return@launch
            pendingDeleteItem = null
            when (val result = itemRepository.restoreItem(item)) {
                is Result.Success -> { /* item reappears via Flow */ }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    // We launch on applicationScope, NOT viewModelScope: by the time onCleared()
    // runs, viewModelScope is already cancelled, so the delete would never run.
    // applicationScope outlives this screen, so the DB write completes. The
    // repository re-checks the real item count, so a list with items is safe.
    override fun onCleared() {
        super.onCleared()
        // Only auto-clean a list we just created and the user left empty. An
        // existing empty list they opened and backed out of stays put.
        if (isNewlyCreated) {
            applicationScope.launch {
                listRepository.deleteListIfEmpty(listId)
            }
        }
    }
}
