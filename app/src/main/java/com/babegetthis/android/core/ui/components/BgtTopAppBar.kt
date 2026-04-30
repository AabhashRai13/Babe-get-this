package com.babegetthis.android.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.babegetthis.android.R

// MD3 top app bars use surface colors — not solid primary.
// LargeTopAppBar gives a warm, modern feel with a big title that collapses on scroll.
// TopAppBar is used for detail screens where a large title would be too much.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgtTopAppBar(
    title: String = stringResource(R.string.app_name),
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    showActionIcon: Boolean = false,
    actionIcon: ImageVector = Icons.Filled.Share,
    onActionClick: (() -> Unit)? = null,
    useLargeTopBar: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    // MD3 surface-tinted colors — the app bar blends with the background
    // and gets a subtle tint on scroll. Much more modern than a solid primary block.
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val largeColors = TopAppBarDefaults.largeTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val navIcon: @Composable () -> Unit = {
        if (navigationIcon != null && onNavigationClick != null) {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = null,
                )
            }
        }
    }

    val actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (showActionIcon && onActionClick != null) {
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                )
            }
        }
    }

    if (useLargeTopBar) {
        LargeTopAppBar(
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = navIcon,
            actions = actions,
            colors = largeColors,
            scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            navigationIcon = navIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}
