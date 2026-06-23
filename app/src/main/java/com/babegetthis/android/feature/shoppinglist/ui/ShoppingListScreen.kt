package com.babegetthis.android.feature.shoppinglist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.R
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.ui.components.BgtTopAppBar
import com.babegetthis.android.core.ui.components.SwipeableCard
import com.babegetthis.android.core.ui.haptics.Haptic
import com.babegetthis.android.core.ui.haptics.rememberHaptic
import com.babegetthis.android.feature.profile.ui.ProfileBottomSheet
import com.babegetthis.android.core.util.TimePeriod
import com.babegetthis.android.core.util.displayName
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
    onNavigateToLogin: () -> Unit = {},
    viewModel: ShoppingListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDialog by viewModel.showCreateDialog.collectAsState()
    val editingList by viewModel.editingList.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()
    val haptic = rememberHaptic()

    val authState by authStateManager.authState.collectAsState()
    val isLoggedIn = authState is AuthState.Authenticated
    val userName by authStateManager.userName.collectAsState()
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
                haptic(Haptic.Light)
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
                actionSlot = {
                    AccountAction(
                        isLoggedIn = isLoggedIn,
                        userName = userName,
                        onOpenProfile = {
                            haptic(Haptic.Light)
                            showProfileSheet = true
                        },
                        onSignIn = {
                            haptic(Haptic.Light)
                            onNavigateToLogin()
                        },
                    )
                },
                useLargeTopBar = true,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (uiState.isActiveTab && !uiState.hasNoLists) {
                ExtendedFloatingActionButton(
                    onClick = {
                        haptic(Haptic.Medium)
                        viewModel.onCreateListClick()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.shopping_list_create),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    ) { padding ->

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
                TabPillRow(
                    tabs = listOf(
                        TabPill(
                            label = "Active",
                            iconInactive = Icons.Outlined.ShoppingCart,
                            iconActive = Icons.Filled.ShoppingCart,
                        ),
                        TabPill(
                            label = "Completed",
                            iconInactive = Icons.Outlined.CheckCircle,
                            iconActive = Icons.Filled.CheckCircle,
                        )
                    ),
                    selectedIndex = uiState.selectedTab,
                    onTabSelected = { viewModel.setSelectedTab(it) },
                )
                AnimatedContent(
                    targetState = uiState.selectedTab,
                    transitionSpec = {
                        val goingRight = targetState > initialState
                        val anim = tween<IntOffset>(durationMillis = 280)
                        val fade = tween<Float>(durationMillis = 280)
                        if (goingRight) {
                            (slideInHorizontally(anim) { width -> width } + fadeIn(fade)) togetherWith
                                (slideOutHorizontally(anim) { width -> -width } + fadeOut(fade))
                        } else {
                            (slideInHorizontally(anim) { width -> -width } + fadeIn(fade)) togetherWith
                                (slideOutHorizontally(anim) { width -> width } + fadeOut(fade))
                        }
                    },
                    label = "tab-content",
                ) { tabIndex ->
                    val isActiveForPane = tabIndex == 0
                    val listsForPane =
                        if (isActiveForPane) uiState.activeLists else uiState.completedLists
                    val groupedForPane =
                        if (isActiveForPane) uiState.groupedActive else uiState.groupedCompleted

                    if (listsForPane.isEmpty()) {
                        TabEmptyState(isActiveTab = isActiveForPane)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        ) {
                            // Greeting only on Active tab
                            if (isActiveForPane) {
                                item(key = "greeting") {
                                    GreetingSection(
                                        listCount = uiState.activeLists.size,
                                        itemsToGet = uiState.activeItemsToGet,
                                        userName = userName,
                                    )
                                }
                            }

                            groupedForPane.forEach { (period, periodLists) ->
                                item(key = "header-$period-$tabIndex") {
                                    TimePeriodHeader(period = period)
                                }

                                itemsIndexed(periodLists, key = { _, list -> list.id }) { _, list ->
                                    val accent = getAccentForList(list.id, isDark)

                                    SwipeableCard(
                                        onSwipeLeft = { viewModel.deleteList(list.id) },
                                    ) {
                                        ShoppingListCard(
                                            list = list,
                                            accent = accent,
                                            isCompletedTab = !isActiveForPane,
                                            onClick = { onNavigateToList(list.id, list.name) },
                                            onLongPress = { viewModel.onEditListClick(list) },
                                        )
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

// The Home top-bar account control. Its appearance is the visual cue for
// auth state: an initial-avatar when signed in, a "Sign in" pill when not.
@Composable
private fun AccountAction(
    isLoggedIn: Boolean,
    userName: String?,
    onOpenProfile: () -> Unit,
    onSignIn: () -> Unit,
) {
    if (isLoggedIn) {
        // Circular initial avatar — same language as the Profile sheet avatar.
        val initial = userName?.trim()?.firstOrNull()?.uppercase()
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            if (initial != null) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                // No name cached yet — fall back to a person glyph.
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Account",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    } else {
        // Outlined "Sign in" pill — clearly an invitation, not just an icon.
        val pillShape = RoundedCornerShape(percent = 50)
        Row(
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(pillShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = pillShape,
                )
                .clickable(onClick = onSignIn)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sign in",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

