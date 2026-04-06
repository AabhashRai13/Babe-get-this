package com.babegetthis.android.feature.shoppingitems.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.data.repository.CategoryRepository
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.model.Category
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingItemsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itemRepository: ShoppingItemRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    val listId: String = savedStateHandle.get<String>("listId") ?: ""
    val listName: String = savedStateHandle.get<String>("listName") ?: ""
    val isNewList: Boolean = savedStateHandle.get<Boolean>("isNew") ?: false

    val items: StateFlow<List<ShoppingItem>> = itemRepository.getItemsByListId(listId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val showAddItemDialog = MutableStateFlow(false)

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    fun onAddItemClick() {
        showAddItemDialog.value = true
    }

    fun onDismissAddItemDialog() {
        showAddItemDialog.value = false
    }

    fun addItem(name: String, quantity: String, categoryId: String?, shop: String?) {
        viewModelScope.launch {
            when (val result = itemRepository.addItem(
                listId = listId,
                name = name,
                quantity = quantity,
                categoryId = categoryId,
                shop = shop,
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
            when (val result = itemRepository.deleteItem(itemId)) {
                is Result.Success -> { /* UI auto-updates via Flow */ }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }
}
