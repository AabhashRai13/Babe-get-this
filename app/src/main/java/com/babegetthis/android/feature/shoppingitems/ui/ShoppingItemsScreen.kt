package com.babegetthis.android.feature.shoppingitems.ui

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.R
import com.babegetthis.android.core.ui.components.BgtTopAppBar
import com.babegetthis.android.core.ui.components.SwipeableCard
import com.babegetthis.android.core.ui.haptics.Haptic
import com.babegetthis.android.core.ui.haptics.rememberHaptic
import com.babegetthis.android.core.voice.ui.VoiceCaptureSheet
import com.babegetthis.android.feature.shoppingitems.ui.components.FirstItemPrompt
import com.babegetthis.android.feature.shoppingitems.ui.components.ProgressCard
import com.babegetthis.android.feature.shoppingitems.ui.components.SectionHeader
import com.babegetthis.android.feature.shoppingitems.ui.components.ShopSubHeader
import com.babegetthis.android.feature.shoppingitems.ui.components.ShoppingItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingItemsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ShoppingItemsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val showDialog by viewModel.showAddItemDialog.collectAsState()
    val editingItem by viewModel.editingItem.collectAsState()

    // All derived lists/maps come from uiState — computed once per data
    // emission in the ViewModel, not on every recomposition.
    val activeItems = uiState.activeItems
    val completedItems = uiState.completedItems
    val activeByShop = uiState.activeByShop

    val snackBarHostState = remember { SnackbarHostState() }
    val haptic = rememberHaptic()
    val context = LocalContext.current

    // Drives the voice-capture sheet for adding items to THIS list.
    var showVoiceSheet by remember { mutableStateOf(false) }

    // Placement animation for item rows: when a toggle moves an item between the
    // Active and Completed sections (or a voice-added row inserts), it slides to
    // its slot instead of teleporting. No-bounce spring with medium-low stiffness
    // = a subtle settle (bouncy would read as toy-ish).
    val rowPlacementSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            snackBarHostState.showSnackbar(message)
        }
    }

    // Fire a Success haptic the moment the list goes from
    // "some unchecked" → "all checked off". The ViewModel filters out the
    // initial load of an already-complete list, so this only buzzes on
    // the actual transition.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShoppingItemsViewModel.UiEvent.ListJustCompleted -> haptic(Haptic.Success)
                is ShoppingItemsViewModel.UiEvent.ShareList -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.text)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.undoDeleteEvent.collect { itemName ->
            val result = snackBarHostState.showSnackbar(
                message = "$itemName deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                haptic(Haptic.Light)
                viewModel.undoDeleteItem()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            BgtTopAppBar(
                title = viewModel.listName,
                navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
                onNavigationClick = onNavigateBack,
                // Share the list as plain text via the OS share sheet. Default
                // actionIcon is already Icons.Filled.Share.
                showActionIcon = true,
                onActionClick = viewModel::onShareClick,
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Mic — always available (both empty + populated states). Voice
                // append is the headline quick-add, so it stays one tap in the
                // thumb zone, stacked above the manual "Add" button.
                SmallFloatingActionButton(
                    onClick = {
                        haptic(Haptic.Medium)
                        showVoiceSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.add),
                    )
                }

                // Manual "Add" — only when the list already has items. The empty
                // state shows its own big Add button inside FirstItemPrompt.
                if (!uiState.isEmpty) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptic(Haptic.Medium)
                            viewModel.onAddItemClick()
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.add),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isEmpty) {
            FirstItemPrompt(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                onAddItem = { viewModel.onAddItemClick() }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                // Progress summary card at the top
                item(key = "progress") {
                    ProgressCard(
                        totalItems = uiState.totalCount,
                        completedCount = uiState.completedCount,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // -- ACTIVE ITEMS --
                if (activeItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.shopping_items_active),
                            count = stringResource(R.string.shopping_items_count, activeItems.size),
                        )
                    }

                    activeByShop.forEach { (shopName, shopItems) ->
                        if (shopName.isNotBlank()) {
                            item(key = "shop-$shopName") {
                                ShopSubHeader(shopName = shopName)
                            }
                        } else if (activeByShop.size > 1) {
                            item(key = "shop-general") {
                                ShopSubHeader(shopName = "General")
                            }
                        }

                        items(shopItems, key = { it.id }) { shoppingItem ->
                            // animateItem = native LazyColumn insert/move animation.
                            // Voice-added rows slide in instead of popping; toggled
                            // rows slide between sections. GPU-composited, cheap.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(placementSpec = rowPlacementSpec),
                            ) {
                                SwipeableCard(
                                    onSwipeLeft = { viewModel.deleteItem(shoppingItem.id) },
                                    onSwipeRight = {
                                        viewModel.togglePickedUp(shoppingItem.id, !shoppingItem.isPickedUp)
                                    },
                                ) {
                                    ShoppingItemCard(
                                        item = shoppingItem,
                                        onClick = { viewModel.onEditItemClick(shoppingItem) },
                                        onTogglePickedUp = {
                                            // The satisfying "check it off" moment — buzz to match
                                            // the un-check path on completed items.
                                            haptic(Haptic.Light)
                                            viewModel.togglePickedUp(
                                                shoppingItem.id,
                                                !shoppingItem.isPickedUp
                                            )
                                        },
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // -- COMPLETED --
                if (completedItems.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = stringResource(R.string.shopping_items_completed),
                            count = null,
                        )
                    }
                    items(completedItems, key = { it.id }) { shoppingItem ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(placementSpec = rowPlacementSpec),
                        ) {
                            SwipeableCard(
                                onSwipeLeft = { viewModel.deleteItem(shoppingItem.id) },
                                onSwipeRight = {
                                    viewModel.togglePickedUp(shoppingItem.id, !shoppingItem.isPickedUp)
                                },
                            ) {
                                ShoppingItemCard(
                                    item = shoppingItem,
                                    onClick = { viewModel.onEditItemClick(shoppingItem) },
                                    onTogglePickedUp = {
                                        haptic(Haptic.Light)
                                        viewModel.togglePickedUp(
                                            shoppingItem.id,
                                            !shoppingItem.isPickedUp
                                        )
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }

    if (showVoiceSheet) {
        VoiceCaptureSheet(
            onDismiss = { showVoiceSheet = false },
            // "Type instead" — close voice and open the manual add dialog.
            onSwitchToType = {
                showVoiceSheet = false
                viewModel.onAddItemClick()
            },
            // The voice VM calls this with the parsed drafts (no review step).
            // We append them to THIS list and return its id so the voice VM
            // transitions to Done. The new rows appear via the items Flow and
            // animate in. No navigation — the user is already in the list.
            onConfirm = { drafts -> viewModel.addItemsWithVoice(drafts) },
        )
    }

    if (showDialog) {
        AddItemDialog(
            categories = categories,
            onDismiss = { viewModel.onDismissAddItemDialog() },
            onAdd = { name, quantity, categoryId, shop, note ->
                viewModel.addItem(name, quantity, categoryId, shop, note)
            },
            onCreateCategory = { name, onCreated ->
                viewModel.addCategory(name, onCreated)
            }
        )
    }

    editingItem?.let { item ->
        AddItemDialog(
            categories = categories,
            editingItem = item,
            onDismiss = { viewModel.onDismissEditItemDialog() },
            onAdd = { _, _, _, _, _ -> },
            onEdit = { itemId, name, quantity, categoryId, shop, note ->
                viewModel.editItem(itemId, name, quantity, categoryId, shop, note)
            },
            onCreateCategory = { name, onCreated ->
                viewModel.addCategory(name, onCreated)
            }
        )
    }
}
