package com.babegetthis.android.feature.shoppinglist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.R
import com.babegetthis.android.core.ui.components.BgtTopAppBar
import com.babegetthis.android.core.ui.components.SwipeableCard
import com.babegetthis.android.core.util.TimePeriod
import com.babegetthis.android.core.util.displayName
import com.babegetthis.android.core.util.getTimePeriod
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList

@Composable
fun ShoppingListScreen(
    onNavigateToList: (listId: String, listName: String) -> Unit = { _, _ -> },
    onNavigateToNewList: (listId: String, listName: String) -> Unit = { _, _ -> },
    viewModel: ShoppingListViewModel = hiltViewModel()
) {
    val lists by viewModel.shoppingLists.collectAsState()
    val showDialog by viewModel.showCreateDialog.collectAsState()
    val editingList by viewModel.editingList.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Group lists by time period (Today, Yesterday, Last 7 Days, etc.)
    // linkedMapOf preserves insertion order — so Today always appears first.
    val groupedLists = remember(lists) {
        val grouped = linkedMapOf<TimePeriod, MutableList<ShoppingList>>()
        // TimePeriod enum is ordered: TODAY, YESTERDAY, LAST_WEEK, THIS_MONTH, OLDER
        // We initialize in order so the map keys are always sorted correctly
        for (list in lists) {
            val period = getTimePeriod(list.createdAt)
            grouped.getOrPut(period) { mutableListOf() }.add(list)
        }
        grouped
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToList.collect { (listId, listName) ->
            onNavigateToNewList(listId, listName)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.undoDeleteEvent.collect { listName ->
            val result = snackbarHostState.showSnackbar(
                message = "$listName deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteList()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BgtTopAppBar(
                title = stringResource(R.string.app_name),
                showActionIcon = false
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onCreateListClick() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (lists.isEmpty()) {
            ShoppingListEmptyState(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .fillMaxSize(),
                onCreateList = { viewModel.onCreateListClick() }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                groupedLists.forEach { (period, periodLists) ->
                    // Time period header
                    item(key = "header-$period") {
                        TimePeriodHeader(period = period)
                    }

                    items(periodLists, key = { it.id }) { list ->
                        SwipeableCard(
                            onSwipeLeft = { viewModel.deleteList(list.id) },
                        ) {
                            ShoppingListCard(
                                list = list,
                                onClick = { onNavigateToList(list.id, list.name) },
                                onLongPress = { viewModel.onEditListClick(list) },
                                onDelete = { viewModel.deleteList(list.id) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (showDialog) {
        CreateListDialog(
            onDismiss = { viewModel.onDismissCreateDialog() },
            onCreate = { name -> viewModel.createList(name) }
        )
    }

    // Edit list dialog — reuses CreateListDialog with pre-filled name
    editingList?.let { list ->
        CreateListDialog(
            currentName = list.name,
            onDismiss = { viewModel.onDismissEditListDialog() },
            onCreate = {}, // Not used in edit mode
            onRename = { newName -> viewModel.editList(list.id, newName) }
        )
    }
}

@Composable
private fun TimePeriodHeader(period: TimePeriod) {
    Text(
        text = period.displayName(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing * 1.5f,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingListCard(
    list: ShoppingList,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.shopping_list_items_count, list.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.shopping_list_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}
