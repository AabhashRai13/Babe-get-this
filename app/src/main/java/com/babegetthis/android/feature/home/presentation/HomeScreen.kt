package com.babegetthis.android.feature.home.presentation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babegetthis.android.R
import com.babegetthis.android.core.ui.components.BgtTopAppBar

@Composable
fun HomeScreen(
    onCreateList: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            // On Home, share is not needed. Keep it clean.
            BgtTopAppBar(
                title = stringResource(R.string.app_name),
                showActionIcon = false
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateList) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        HomeEmptyState(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .fillMaxSize(),
            onCreateList = onCreateList
        )
    }
}
