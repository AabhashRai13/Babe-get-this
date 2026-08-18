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
import com.babegetthis.android.core.sync.data.remote.SharedListRemote
import com.babegetthis.android.core.sync.data.repository.ShareRepository
import com.babegetthis.android.core.sync.data.repository.SyncEngine
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.Marker
import com.babegetthis.android.core.telemetry.TelemetryMarkers
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.CategorySource
import com.babegetthis.android.core.telemetry.model.InputMethod
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.feature.shoppingitems.model.CategorySection
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItemsUiState
import com.babegetthis.android.feature.shoppingitems.model.ShopSection
import com.babegetthis.android.feature.shoppingitems.share.ShoppingListShareText
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val pinRepository: com.babegetthis.android.core.pin.data.PinRepository,
    private val shareRepository: ShareRepository,
    private val syncEngine: SyncEngine,
    private val sharedListRemote: SharedListRemote,
    private val analytics: AnalyticsRepository,
    private val markers: TelemetryMarkers,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    val listId: String = savedStateHandle.get<String>("listId") ?: ""
    val listName: String = savedStateHandle.get<String>("listName") ?: ""

    // Whether this list is locked, and whether the user has entered the PIN for
    // it this session. Rendering, deletion, and share are gated on these.
    //
    // Gated on pinExists as well as the row's own flag, and that second term is
    // load-bearing: it makes "locked with no PIN in existence" unrepresentable.
    // Removing the device PIN used to rely on a separate unlockAll() pass to
    // clear every row's flag afterwards — if that pass was cancelled or failed,
    // the list stayed flagged with no credential left to open it, i.e. the user
    // was permanently locked out of their own data. Deriving the gate from both
    // means the row flag going stale is cosmetic (a lock icon on the card) rather
    // than a lockout; unlockAll() is now tidy-up, not a correctness requirement.
    val isLocked: StateFlow<Boolean> = combine(
        listRepository.getListById(listId).map { it?.isLocked == true },
        pinRepository.pinExists,
    ) { rowLocked, hasPin -> rowLocked && hasPin }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pinExists: StateFlow<Boolean> = pinRepository.pinExists

    private val _sessionUnlocked = MutableStateFlow(false)
    val sessionUnlocked: StateFlow<Boolean> = _sessionUnlocked.asStateFlow()

    fun onSessionUnlocked() { _sessionUnlocked.value = true }

    // Re-lock the session — called on ON_STOP so a backgrounded locked list
    // re-prompts on return. Not ON_PAUSE (that fires for the share sheet).
    fun lockSession() { _sessionUnlocked.value = false }

    fun setListLocked(locked: Boolean) {
        // Locking always happens while viewing the list, so keep this session
        // unlocked — otherwise the list you just locked would immediately
        // re-prompt for the PIN.
        if (locked) _sessionUnlocked.value = true
        viewModelScope.launch { listRepository.setLocked(listId, locked) }
    }

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
                activeSections = buildActiveSections(active),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ShoppingItemsUiState(),
        )

    // Group active items Shop -> Category -> items for the sectioned list.
    // Shops keep first-seen order (unchanged from the old by-shop grouping).
    // Categories are alphabetical (case-insensitive) so the order is the same on
    // every shopping trip; the uncategorized bucket (null categoryName) sorts last.
    private fun buildActiveSections(active: List<ShoppingItem>): List<ShopSection> =
        active
            .groupBy { it.shop?.ifBlank { null } }
            .map { (shop, shopItems) ->
                val categories = shopItems
                    .groupBy { it.categoryName }
                    .entries
                    .sortedWith(compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.key })
                    .map { CategorySection(label = it.key, items = it.value) }
                ShopSection(shopName = shop, categories = categories)
            }

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

    // Tracks an in-flight undo restore so onCleared's empty-list cleanup can
    // wait for it (see undoDeleteItem for why it runs on applicationScope).
    private var restoreJob: Job? = null

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

    // When non-null, the share-code dialog is showing this code.
    val shareCodeDialog = MutableStateFlow<String?>(null)

    // Live sharing needs an account; this prompts sign-in when there isn't one.
    val showShareAuthPrompt = MutableStateFlow(false)

    init {
        // Live sync while this screen is open. Catch up immediately, then treat
        // every realtime emission (events AND channel re-joins) as "worth
        // asking again" — realtime never carries data, it only triggers the
        // same catch-up query. collectLatest tears the subscription down if
        // the list stops being shared (tombstoned remotely), and viewModelScope
        // cancellation on screen close closes the channel.
        viewModelScope.launch {
            listRepository.getShareCode(listId)
                .map { it != null }
                .distinctUntilChanged()
                .collectLatest { shared ->
                    if (shared) {
                        syncEngine.catchUp(listId)
                        sharedListRemote.changes(listId).collect { syncEngine.catchUp(listId) }
                    }
                }
        }
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
                    // Reuses the existing transition detection rather than
                    // adding a second one: this fires on the transition only,
                    // so re-opening a finished list does not re-report a trip.
                    analytics.track(AnalyticsEvent.ListCompleted(itemList.size))
                    if (markers.firstTime(Marker.FirstListCompleted, currentUserId())) {
                        analytics.track(AnalyticsEvent.FirstListCompleted(itemList.size))
                    }
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
                    // Manual add: whatever category is on it, the user chose it
                    // in the dialog. Nothing was auto-assigned here.
                    trackItemAdded(
                        inputMethod = InputMethod.Manual,
                        categorySource = if (categoryId == null) CategorySource.None
                        else CategorySource.User,
                    )
                    onListEdited()
                }
                is Result.Error -> {
                    _errorMessage.emit(result.error.message)
                }
            }
        }
    }

    // Every item-added path funnels through here so the activation marker is
    // claimed in one place. Scoped to the user id, so a second account on the
    // same device still records its own activation.
    private fun trackItemAdded(inputMethod: InputMethod, categorySource: CategorySource) {
        analytics.track(AnalyticsEvent.ItemAdded(inputMethod, categorySource))
        if (markers.firstTime(Marker.FirstItemAdded, currentUserId())) {
            analytics.track(AnalyticsEvent.FirstItemAdded(inputMethod))
        }
    }

    private fun currentUserId(): String? =
        (authStateManager.authState.value as? AuthState.Authenticated)?.userId

    // Fires the joiner's-first-edit event, once per list, and only on the
    // device that joined. Owner and joiner devices are indistinguishable once
    // a shared list has synced, so this leans on the marker written at join
    // time — see Marker.JoinedList.
    private fun onListEdited() {
        if (!markers.has(Marker.JoinedList, listId)) return
        if (!markers.firstTime(Marker.SharedListFirstEdit, listId)) return
        analytics.track(AnalyticsEvent.SharedListFirstEditByJoiner)
    }

    // Voice persist lambda for "add items to this list". The voice sheet calls
    // this with the parsed drafts; we append them to the current list and return
    // its id so the voice VM transitions to Done. No navigation — the user is
    // already here, and the new rows appear via the items Flow (with an insert
    // animation in the screen). Mirrors ShoppingListViewModel.createListWithVoice.
    suspend fun addItemsWithVoice(drafts: List<ItemDraft>): Result<String> {
        val result = listRepository.addItemsToList(listId, drafts)
        if (result is Result.Success) {
            drafts.forEach { draft ->
                trackItemAdded(
                    inputMethod = InputMethod.Voice,
                    categorySource = if (draft.category == null) CategorySource.None
                    else CategorySource.Auto,
                )
                // One per item rather than one per utterance: the taxonomy
                // question is per-category, and the correction rate this
                // feeds is only meaningful against a per-category denominator.
                analytics.track(AnalyticsEvent.CategoryAutoAssigned(draft.category))
            }
            onListEdited()
        }
        return result
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
                    // The taxonomy signal that matters. An auto-assigned
                    // category the user changes is direct evidence that our
                    // fixed category set put that item in the wrong place —
                    // there is no other way to learn this.
                    if (currentItem.categoryId != categoryId) {
                        analytics.track(
                            AnalyticsEvent.CategoryCorrected(
                                fromCategoryId = currentItem.categoryId,
                                toCategoryId = categoryId,
                            ),
                        )
                    }
                    onListEdited()
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
                is Result.Success -> {
                    // Only the check, not the un-check. Un-checking is a
                    // correction, and counting it would inflate the shopping
                    // activity this is meant to measure.
                    if (isPickedUp) analytics.track(AnalyticsEvent.ItemCheckedOff)
                    onListEdited()
                }
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
                    // Only offer Undo when there is something cached to restore.
                    // Emitting unconditionally gave the user an Undo button that
                    // did nothing when tapped — undoDeleteItem returns early on a
                    // null pending item, silently and with no feedback.
                    if (itemToDelete != null) {
                        _undoDeleteEvent.emit(itemToDelete.name)
                    }
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
        // The lock cannot be bypassed by exporting to text — a locked list only
        // shares once verified for this session.
        if (isLocked.value && !_sessionUnlocked.value) return
        val text = ShoppingListShareText.format(listName, items.value)
        viewModelScope.launch { _events.emit(UiEvent.ShareList(text)) }
    }

    // Live sharing: generate (or re-show) the list's code. Same lock gate as
    // the text share — a locked list shares nothing until verified.
    fun onShareLiveClick() {
        if (isLocked.value && !_sessionUnlocked.value) return
        if (!isAuthenticated()) {
            showShareAuthPrompt.value = true
            return
        }
        viewModelScope.launch {
            when (val result = shareRepository.share(listId)) {
                is Result.Success -> {
                    shareCodeDialog.value = result.data
                    // share() returns the EXISTING code when there is one, so
                    // re-opening the dialog would otherwise report a new share
                    // every time. The marker also records that this device is
                    // the owner's — see onListEdited.
                    if (markers.firstTime(Marker.ShareCodeCreated, listId)) {
                        analytics.track(AnalyticsEvent.ShareCodeCreated)
                    }
                }
                is Result.Error -> _errorMessage.emit(result.error.message)
            }
        }
    }

    // Copying the code is the sharing act — there is no share sheet in this
    // flow, the user pastes it into whatever app they already talk in.
    fun onShareCodeCopied() {
        analytics.track(AnalyticsEvent.ShareCodeShared)
    }

    fun onDismissShareCodeDialog() {
        shareCodeDialog.value = null
    }

    fun onDismissShareAuthPrompt() {
        showShareAuthPrompt.value = false
    }

    fun undoDeleteItem() {
        // applicationScope, NOT viewModelScope: ViewModel.clear() cancels
        // viewModelScope BEFORE onCleared runs, so an undo tapped right before
        // backing out would silently die — and onCleared's empty-list cleanup
        // would then delete the whole list the user just tried to rescue.
        restoreJob = applicationScope.launch {
            val item = pendingDeleteItem ?: return@launch
            when (val result = itemRepository.restoreItem(item)) {
                is Result.Success -> {
                    // Cleared only once the item is safely back. Clearing up front
                    // (as this used to) meant a failed restore threw away the only
                    // copy while telling the user to try again.
                    pendingDeleteItem = null
                }
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
        // Any list left with zero items gets cleaned up — whether it was just
        // created and abandoned, or an old list the user emptied out. Empty
        // lists have no reason to linger on the home screen.
        applicationScope.launch {
            // A just-tapped undo may still be restoring its item — wait for it
            // so the emptiness check sees the restored row and keeps the list.
            restoreJob?.join()
            listRepository.deleteListIfEmpty(listId)
        }
    }
}
