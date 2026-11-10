package com.babegetthis.android.feature.gotthis.ui.viewModels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.feature.gotthis.data.BoughtHint
import com.babegetthis.android.feature.gotthis.data.GotThisAccess
import com.babegetthis.android.feature.gotthis.data.GotThisRepository
import com.babegetthis.android.feature.gotthis.data.GotThisTracker
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GotThisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GotThisRepository,
    private val access: GotThisAccess,
    private val tracker: GotThisTracker,
    private val itemRepository: ShoppingItemRepository,
    private val prefs: GotThisPrefs,
    private val notificationGate: NotificationGate,
    private val analytics: AnalyticsRepository,
    private val clock: ReminderClock,
) : ViewModel() {

    private val listId: String = savedStateHandle.get<String>("listId") ?: ""

    // The list's owner decides holdout and access; looked up once.
    private val owner: Deferred<String?> = viewModelScope.async { repository.ownerOf(listId) }

    // Empty unless hints are allowed. In the holdout the tracker still hears about
    // them, so shown-versus-hidden can be compared.
    val hints: StateFlow<Map<String, BoughtHint>> = flow {
        val ownerId = owner.await()
        repository.hintsFor(listId).collect { found ->
            val shows = access.showsHints(ownerId)
            if (found.isNotEmpty() && (shows || access.shadows(ownerId))) {
                tracker.onHintsPresent(ownerId, found.values)
            }
            emit(if (shows) found else emptyMap())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun onHintMute(itemId: String) {
        viewModelScope.launch { tracker.onHintMuted(itemId) }
    }
}
