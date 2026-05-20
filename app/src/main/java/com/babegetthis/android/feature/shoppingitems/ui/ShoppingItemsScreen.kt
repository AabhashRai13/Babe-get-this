package com.babegetthis.android.feature.shoppingitems.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.R
import com.babegetthis.android.core.auth.ui.AuthPromptDialog
import com.babegetthis.android.core.ui.components.BgtTopAppBar
import com.babegetthis.android.core.ui.components.SwipeableCard
import com.babegetthis.android.core.ui.haptics.Haptic
import com.babegetthis.android.core.ui.haptics.rememberHaptic
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingItemsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
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

    // Auth prompt dialog — shown when an unauthenticated user taps Share
    var showAuthPrompt by remember { mutableStateOf(false) }

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
                // Share button — gates behind auth check
                showActionIcon = true,
                onActionClick = {
                    if (viewModel.isAuthenticated()) {
                        // User is logged in — sharing not built yet (v2)
                        viewModel.showComingSoonMessage()
                    } else {
                        // Not logged in — prompt to sign in
                        showAuthPrompt = true
                    }
                },
            )
        },
        floatingActionButton = {
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

                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
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

    // Auth prompt — shown when unauthenticated user taps Share
    if (showAuthPrompt) {
        AuthPromptDialog(
            onLogin = onNavigateToLogin,
            onRegister = onNavigateToRegister,
            onDismiss = { showAuthPrompt = false },
        )
    }
}

// Progress card — shows how far along the shopping trip is.
// A visual focal point that gives the screen character.
//
// Celebration sub-animations when the list transitions to all-done:
//   1) Card pops in: scale snaps to 0.85f, springs back to 1.0f
//   2) Filled.CheckCircle appears next to "All done!", rotating from
//      -90° to 0° and fading in over 200ms
//   3) (Haptic.Success is handled at the screen level via the ViewModel
//       UiEvent.ListJustCompleted flow — see ShoppingItemsScreen.)
//
// All three skip on the initial composition (hasInitialized guard) so
// opening a list that's already complete doesn't replay the celebration.
@Composable
private fun ProgressCard(
    totalItems: Int,
    completedCount: Int,
) {
    val progress = if (totalItems > 0) completedCount.toFloat() / totalItems else 0f
    val allDone = completedCount == totalItems && totalItems > 0

    // Animatable lets us snapTo (jump instantly) then animateTo, which is
    // how we get "card appears at 0.85 then springs up to 1.0" rather
    // than the smooth lerp animateFloatAsState would produce.
    val cardScale = remember { Animatable(1f) }
    // Icon scales 0 -> 1 with a bouncy spring so it overshoots naturally.
    // AnimatedVisibility handles the layout slot reveal + fade; graphicsLayer
    // scale adds the bouncy pop on top.
    val checkScale = remember { Animatable(if (allDone) 1f else 0f) }
    // One-shot 0 -> 1 -> 0 pulse for the container color flash.
    val pulseProgress = remember { Animatable(0f) }
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(allDone) {
        if (hasInitialized && allDone) {
            // Just transitioned into all-done — play the celebration.
            cardScale.snapTo(0.85f)
            checkScale.snapTo(0f)
            pulseProgress.snapTo(0f)

            launch {
                cardScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                )
            }
            launch {
                checkScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            launch {
                pulseProgress.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                pulseProgress.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
            }
        } else if (!allDone) {
            // Re-opening or unticking — reset without animating.
            checkScale.snapTo(0f)
            pulseProgress.snapTo(0f)
        }
        hasInitialized = true
    }

    // Pulse the container from primaryContainer toward primary and back on
    // completion, then settle. Steady-state color is unchanged.
    val baseContainer = if (allDone)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerLow
    val pulseTarget = MaterialTheme.colorScheme.primary
    val containerColor = lerp(baseContainer, pulseTarget, pulseProgress.value)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale.value),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = if (!allDone) BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // AnimatedVisibility collapses the icon + spacer slot when
                    // not complete so the title row isn't padded with empty
                    // space. The bouncy graphicsLayer scale plays on top of
                    // the slot reveal for the "pop" effect.
                    AnimatedVisibility(
                        visible = allDone,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer {
                                        scaleX = checkScale.value
                                        scaleY = checkScale.value
                                    },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                    Text(
                        text = if (allDone) "All done!"
                               else "$completedCount of $totalItems picked up",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (allDone)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Percentage badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (allDone)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (allDone)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Progress bar — animates smoothly as items get checked off.
            // Uses the primary color so it ties into the overall theme.
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
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
        // Layered circle — matching the empty state style from the list screen
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            )
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.shopping_items_list_created),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.shopping_items_add_first),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        androidx.compose.material3.Button(
            onClick = onAddItem,
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.shopping_items_add_first_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        if (count != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun ShopSubHeader(shopName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Place,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = shopName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}


@Composable
private fun ShoppingItemCard(
    item: ShoppingItem,
    onClick: () -> Unit,
    onTogglePickedUp: () -> Unit,
) {
    // --- Sub-animations for the pick-up moment ---
    // Following the checklist's "break it into sub-animations" pattern:
    //   1) card container colour eases instead of snapping
    //   2) checkbox surface colour eases
    //   3) check icon Crossfades in/out
    //   4) checkbox scales 1.0 → 1.15 → 1.0 on every TOGGLE (spring pop)
    //   5) text colour eases to/from the muted state
    val animSpec = tween<Color>(durationMillis = 220)
    val cardColor by animateColorAsState(
        targetValue = if (item.isPickedUp)
            MaterialTheme.colorScheme.surfaceContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = animSpec,
        label = "card-color",
    )
    val checkboxColor by animateColorAsState(
        targetValue = if (item.isPickedUp)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = animSpec,
        label = "checkbox-color",
    )
    val textColor by animateColorAsState(
        targetValue = if (item.isPickedUp)
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = animSpec,
        label = "text-color",
    )

    // Scale pop on the checkbox — driven by a target that we briefly bump
    // to 1.15f and let spring back. `hasInitialized` skips the pop on the
    // very first composition (e.g. when scrolling into view), so it only
    // plays on a real user toggle.
    var scaleTarget by remember(item.id) { mutableFloatStateOf(1f) }
    var hasInitialized by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.isPickedUp) {
        if (hasInitialized) {
            scaleTarget = 1.15f
            delay(120)
            scaleTarget = 1f
        }
        hasInitialized = true
    }
    val checkboxScale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkbox-scale",
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (item.isPickedUp) 0.dp else 2.dp,
        ),
        border = if (!item.isPickedUp) BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Circular checkbox — filled when picked up, outlined when active.
            // Colour eases via animateColorAsState; the check icon Crossfades
            // in; the whole circle pops on toggle via the scale modifier.
            Surface(
                onClick = onTogglePickedUp,
                shape = CircleShape,
                color = checkboxColor,
                border = if (!item.isPickedUp)
                    CardDefaults.outlinedCardBorder()
                else
                    null,
                modifier = Modifier
                    .size(30.dp)
                    .scale(checkboxScale),
            ) {
                Crossfade(
                    targetState = item.isPickedUp,
                    animationSpec = tween(180),
                    label = "check-icon",
                ) { pickedUp ->
                    if (pickedUp) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isPickedUp) FontWeight.Normal else FontWeight.Medium,
                    textDecoration = if (item.isPickedUp) TextDecoration.LineThrough else TextDecoration.None,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!item.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = if (item.isPickedUp)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Chips for quantity and category — only on active items
                if (!item.isPickedUp && (item.quantity.isNotBlank() || item.categoryName != null)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.quantity.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                                ),
                            ) {
                                Text(
                                    text = "Qty: ${item.quantity}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                        }
                        if (item.categoryName != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                border = BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                                ),
                            ) {
                                Text(
                                    text = item.categoryName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
