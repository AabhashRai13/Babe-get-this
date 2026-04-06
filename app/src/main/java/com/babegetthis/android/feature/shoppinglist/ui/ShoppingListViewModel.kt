package com.babegetthis.android.feature.shoppinglist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList
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
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingListRepository,
) : ViewModel() {

    val shoppingLists: StateFlow<List<ShoppingList>> = repository.getAllLists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val showCreateDialog = MutableStateFlow(false)

    // SharedFlow for one-time navigation events.
    // Unlike StateFlow, it doesn't replay — fires once and is consumed.
    // Like an Event in BLoC or a one-shot callback.
    private val _navigateToList = MutableSharedFlow<Pair<String, String>>()
    val navigateToList = _navigateToList.asSharedFlow()

    fun onCreateListClick() {
        showCreateDialog.value = true
    }

    fun onDismissCreateDialog() {
        showCreateDialog.value = false
    }

    fun createList(name: String) {
        viewModelScope.launch {
            val listId = repository.createList(name)
            showCreateDialog.value = false
            // Navigate to the newly created list
            _navigateToList.emit(listId to name)
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            repository.deleteList(listId)
        }
    }
}
