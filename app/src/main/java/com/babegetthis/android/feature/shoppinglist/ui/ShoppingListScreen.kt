package com.babegetthis.android.feature.shoppinglist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.R
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.ui.components.BgtTopAppBar
import com.babegetthis.android.core.ui.components.SwipeableCard
import com.babegetthis.android.feature.profile.ui.ProfileBottomSheet
import com.babegetthis.android.core.util.TimePeriod
import com.babegetthis.android.core.util.displayName
import com.babegetthis.android.core.util.getTimePeriod
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList
import com.babegetthis.android.feature.shoppinglist.ui.components.GreetingSection
import com.babegetthis.android.feature.shoppinglist.ui.components.ShoppingListCard
import com.babegetthis.android.feature.shoppinglist.ui.components.TabEmptyState
import com.babegetthis.android.feature.shoppinglist.ui.components.TabPill
import com.babegetthis.android.feature.shoppinglist.ui.components.TabPillRow
import com.babegetthis.android.feature.shoppinglist.ui.viewModels.ShoppingListViewModel
import com.babegetthis.android.ui.theme.DarkListAccentPalette
import com.babegetthis.android.ui.theme.ListAccentColor
import com.babegetthis.android.ui.theme.ListAccentPalette
import kotlin.math.abs

// Picks a stable accent color for a list based on its ID.
// Uses the hash of the ID so the color never changes for a given list,
// but different lists get different colors — like Google Keep.
private fun getAccentForList(listId: String, isDark: Boolean): ListAccentColor {
    val palette = if (isDark) DarkListAccentPalette else ListAccentPalette
    val index = abs(listId.hashCode()) % palette.size
    return palette[index]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    authStateManager: com.babegetthis.android.core.auth.data.AuthStateManager,
    onNavigateToList: (listId: String, listName: String) -> Unit = { _, _ -> },
    onNavigateToNewList: (listId: String, listName: String) -> Unit = { _, _ -> },
    viewModel: ShoppingListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDialog by viewModel.showCreateDialog.collectAsState()
    val editingList by viewModel.editingList.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()

    val authState by authStateManager.authState.collectAsState()
    val isLoggedIn = authState is AuthState.Authenticated
    var showProfileSheet by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.navigateToList.collect { (listId, listName) ->
            onNavigateToNewList(listId, listName)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            snackBarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.undoDeleteEvent.collect { listName ->
            val result = snackBarHostState.showSnackbar(
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            BgtTopAppBar(
                title = stringResource(R.string.app_name),
                // Profile icon — only visible when logged in
                showActionIcon = isLoggedIn,
                actionIcon = Icons.Default.Person,
                onActionClick = { showProfileSheet = true },
                useLargeTopBar = true,
                scrollBehavior = scrollBehavior,
            )
        },
        // FAB only shows on the Active tab when there's at least one list —
        // on the empty state, the centered "Create list" button is the sole CTA.
        floatingActionButton = {
            if (uiState.isActiveTab && !uiState.hasNoLists) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.onCreateListClick() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.shopping_list_create),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    ) { padding ->
        // No lists at all — show the empty state (no tabs needed)
        if (uiState.hasNoLists) {
            ShoppingListEmptyState(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .fillMaxSize(),
                onCreateList = { viewModel.onCreateListClick() }
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                // Custom pill-style tab row — no divider line.
                // Selected tab gets a tinted pill background with bold text.
                // Uses ripple indication for touch feedback.
                TabPillRow(
                    tabs = listOf(
                        TabPill(label = "Active", icon = Icons.Outlined.ShoppingCart),
                        TabPill(label = "Completed", icon = Icons.Default.Check)
                    ),
                    selectedIndex = uiState.selectedTab,
                    onTabSelected = { viewModel.setSelectedTab(it) },
                )

                // Tab content
                if (uiState.displayedListsAreEmpty) {
                    TabEmptyState(isActiveTab = uiState.isActiveTab)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        // Greeting only on Active tab
                        if (uiState.isActiveTab) {
                            item(key = "greeting") {
                                GreetingSection(
                                    listCount = uiState.activeLists.size,
                                    itemsToGet = uiState.activeItemsToGet,
                                )
                            }
                        }

                       uiState.groupedLists.forEach { (period, periodLists) ->
                            item(key = "header-$period-${uiState.selectedTab}") {
                                TimePeriodHeader(period = period)
                            }

                            itemsIndexed(periodLists, key = { _, list -> list.id }) { _, list ->
                                val accent = getAccentForList(list.id, isDark)

                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn() + slideInVertically(
                                        initialOffsetY = { it / 2 }
                                    ),
                                ) {
                                    SwipeableCard(
                                        onSwipeLeft = { viewModel.deleteList(list.id) },
                                    ) {
                                        ShoppingListCard(
                                            list = list,
                                            accent = accent,
                                            isCompletedTab = !uiState.isActiveTab,
                                            onClick = { onNavigateToList(list.id, list.name) },
                                            onLongPress = { viewModel.onEditListClick(list) },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            item { Spacer(modifier = Modifier.height(4.dp)) }
                        }

                        item { Spacer(modifier = Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }

    if (showDialog) {
        CreateListDialog(
            onDismiss = { viewModel.onDismissCreateDialog() },
            onCreate = { name -> viewModel.createList(name) }
        )
    }

    editingList?.let { list ->
        CreateListDialog(
            currentName = list.name,
            onDismiss = { viewModel.onDismissEditListDialog() },
            onCreate = {},
            onRename = { newName -> viewModel.editList(list.id, newName) }
        )
    }

    // Auto-dismiss the profile sheet when the user logs out
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            showProfileSheet = false
        }
    }

    // Profile bottom sheet — only rendered when the user taps the profile icon
    if (showProfileSheet && isLoggedIn) {
        ProfileBottomSheet(
            onDismiss = { showProfileSheet = false },
            onToast = { message ->
                viewModel.showSnackBar(message)
            },
        )
    }
}
@Composable
private fun TimePeriodHeader(period: TimePeriod) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = period.displayName(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

