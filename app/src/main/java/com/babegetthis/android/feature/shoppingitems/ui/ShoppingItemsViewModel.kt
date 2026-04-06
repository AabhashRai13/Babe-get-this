package com.babegetthis.android.feature.shoppingitems.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.data.repository.CategoryRepository
import com.babegetthis.android.core.model.Category
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    // If this is a newly created list, show the inline add form right away
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

    fun onAddItemClick() {
        showAddItemDialog.value = true
    }

    fun onDismissAddItemDialog() {
        showAddItemDialog.value = false
    }

    fun addItem(name: String, quantity: String, categoryId: String?) {
        viewModelScope.launch {
            itemRepository.addItem(
                listId = listId,
                name = name,
                quantity = quantity,
                categoryId = categoryId,
            )
            showAddItemDialog.value = false
        }
    }

    fun addCategory(name: String, onCategoryCreated: (Category) -> Unit) {
        viewModelScope.launch {
            val id = categoryRepository.addCategory(name)
            onCategoryCreated(Category(id = id, name = name, isDefault = false))
        }
    }

    fun togglePickedUp(itemId: String, isPickedUp: Boolean) {
        viewModelScope.launch {
            itemRepository.togglePickedUp(itemId, isPickedUp)
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            itemRepository.deleteItem(itemId)
        }
    }
}
