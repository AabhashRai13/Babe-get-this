package com.babegetthis.android.feature.shoppingitems.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.R
import com.babegetthis.android.core.ui.components.BgtTopAppBar
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem

@Composable
fun ShoppingItemsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ShoppingItemsViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val showDialog by viewModel.showAddItemDialog.collectAsState()

    val activeItems = items.filter { !it.isPickedUp }
    val completedItems = items.filter { it.isPickedUp }

    Scaffold(
        topBar = {
            BgtTopAppBar(
                title = viewModel.listName,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onNavigateBack,
                showActionIcon = false,
            )
        },
        floatingActionButton = {
            // Only show FAB when there are already items
            if (items.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { viewModel.onAddItemClick() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            // New list or empty list — show the first item prompt
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
                // -- ACTIVE ITEMS section --
                if (activeItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.shopping_items_active),
                            count = stringResource(R.string.shopping_items_count, activeItems.size),
                        )
                    }
                    items(activeItems, key = { it.id }) { shoppingItem ->
                        ShoppingItemCard(
                            item = shoppingItem,
                            onTogglePickedUp = { viewModel.togglePickedUp(shoppingItem.id, !shoppingItem.isPickedUp) },
                            onDelete = { viewModel.deleteItem(shoppingItem.id) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // -- COMPLETED section --
                if (completedItems.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = stringResource(R.string.shopping_items_completed),
                            count = null,
                        )
                    }
                    items(completedItems, key = { it.id }) { shoppingItem ->
                        ShoppingItemCard(
                            item = shoppingItem,
                            onTogglePickedUp = { viewModel.togglePickedUp(shoppingItem.id, !shoppingItem.isPickedUp) },
                            onDelete = { viewModel.deleteItem(shoppingItem.id) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showDialog) {
        AddItemDialog(
            categories = categories,
            onDismiss = { viewModel.onDismissAddItemDialog() },
            onAdd = { name, quantity, categoryId ->
                viewModel.addItem(name, quantity, categoryId)
            },
            onCreateCategory = { name, onCreated ->
                viewModel.addCategory(name, onCreated)
            }
        )
    }
}

@Composable
private fun FirstItemPrompt(
    modifier: Modifier = Modifier,
    onAddItem: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Shopping cart icon with a styled background
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.shopping_items_list_created),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.shopping_items_add_first),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Big add button instead of FAB
        Surface(
            onClick = onAddItem,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.shopping_items_add_first_button),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing * 1.5f,
        )
        if (count != null) {
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShoppingItemCard(
    item: ShoppingItem,
    onTogglePickedUp: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPickedUp)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (item.isPickedUp) 0.dp else 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Custom checkbox — circle with check icon, matching the mock
            Surface(
                onClick = onTogglePickedUp,
                shape = CircleShape,
                color = if (item.isPickedUp)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surface,
                border = if (!item.isPickedUp)
                    CardDefaults.outlinedCardBorder()
                else
                    null,
                modifier = Modifier.size(32.dp),
            ) {
                if (item.isPickedUp) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (item.isPickedUp) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = if (item.isPickedUp)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.quantity.isNotBlank()) {
                    Text(
                        text = item.quantity,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isPickedUp)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.categoryName != null && !item.isPickedUp) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = item.categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
