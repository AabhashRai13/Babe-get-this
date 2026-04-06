package com.babegetthis.android.feature.shoppinglist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.error.Result
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

    // One-time error events — like showing a snackbar.
    // SharedFlow fires once and is consumed, unlike StateFlow which replays.
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

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

    fun deleteList(listId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteList(listId)) {
                is Result.Success -> { /* list removed, UI auto-updates via Flow */ }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }
}
