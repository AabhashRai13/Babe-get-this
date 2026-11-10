package com.babegetthis.android.feature.gotthis.ui.viewModels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.feature.gotthis.data.BoughtHint
import com.babegetthis.android.feature.gotthis.data.GotThisAccess
import com.babegetthis.android.feature.gotthis.data.GotThisRepository
import com.babegetthis.android.feature.gotthis.data.GotThisTracker
import com.babegetthis.android.feature.gotthis.data.UsualItem
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.domain.GotThisRules
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the "Usually bought around now" row shows; [more] chips are folded into a "+N" chip until [expanded]. */
data class UsualsState(val chips: List<UsualItem>, val more: Int, val expanded: Boolean, val offerHeadsUp: Boolean)

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
            if (found.isNotEmpty() && (shows || access.shadowsHints(ownerId))) {
                tracker.onHintsPresent(ownerId, found.values)
            }
            emit(if (shows) found else emptyMap())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun onHintMute(itemId: String) {
        viewModelScope.launch { tracker.onHintMuted(itemId) }
    }

    // The row is hidden by the clock, so the clock is a flow: it moves on resume and on "Not now".
    private val hiddenUntil = MutableStateFlow(prefs.usualsHiddenUntil)
    private val now = MutableStateFlow(clock.now())
    private val expanded = MutableStateFlow(false)
    private val offerHeadsUp = MutableStateFlow(headsUpOffered())

    // Null means no row: nothing due, hidden for now, or running-low switched off. In the
    // holdout the tracker still hears that the row would have shown.
    val usuals: StateFlow<UsualsState?> = flow {
        val ownerId = owner.await()
        emitAll(
            combine(repository.usualsFor(ownerId), hiddenUntil, now, expanded, offerHeadsUp) {
                    due,
                    hidden,
                    time,
                    open,
                    offer,
                ->
                val wouldShow = due.isNotEmpty() && time >= hidden
                if (wouldShow && (access.showsRunningLow(ownerId) || access.shadowsRunningLow(ownerId))) {
                    tracker.onUsualsPresent(ownerId, due.size)
                }
                if (!wouldShow || !access.showsRunningLow(ownerId)) {
                    null
                } else {
                    val chips = if (open) due else due.take(GotThisRules.MAX_CHIPS)
                    UsualsState(chips, due.size - chips.size, open, offer)
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onChip(item: UsualItem) {
        viewModelScope.launch {
            val added = itemRepository.addItem(listId, item.name, "1", item.categoryId)
            if (added is Result.Success) analytics.track(AnalyticsEvent.GotThisUsualAdded)
        }
    }

    fun onExpand() {
        expanded.value = true
    }

    fun onNotNow() {
        val until = clock.now() + GotThisRules.NOT_NOW_MILLIS
        prefs.usualsHiddenUntil = until
        hiddenUntil.value = until
    }

    // The person may have changed the notification setting while away, or a day may have passed.
    fun onResumed() {
        now.value = clock.now()
        offerHeadsUp.value = headsUpOffered()
    }

    private fun headsUpOffered() = prefs.runningLowEnabled && !notificationGate.notificationsEnabled()
}
