package com.babegetthis.android.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.pin.data.PinRepository
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Settings depends on PinRepository (PIN state) and ShoppingListRepository
// (unlock-all on removal, and the locked-count warning). Deliberately NOT on
// AuthStateManager — Settings must work signed out.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    pinRepository: PinRepository,
    private val listRepository: ShoppingListRepository,
) : ViewModel() {

    val pinExists: StateFlow<Boolean> = pinRepository.pinExists

    private val _lockedCount = MutableStateFlow(0)
    val lockedCount: StateFlow<Int> = _lockedCount.asStateFlow()

    fun refreshLockedCount() {
        viewModelScope.launch { _lockedCount.value = listRepository.lockedCount() }
    }

    // Called after the PIN has been removed — no list may stay gated by a PIN
    // that no longer exists.
    fun onPinRemoved() {
        viewModelScope.launch { listRepository.unlockAll() }
    }
}
