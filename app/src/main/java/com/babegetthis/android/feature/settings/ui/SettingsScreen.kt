package com.babegetthis.android.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.BuildConfig
import com.babegetthis.android.R
import com.babegetthis.android.core.pin.ui.ChangePinDialog
import com.babegetthis.android.core.pin.ui.PinSetupDialog
import com.babegetthis.android.core.pin.ui.RecoveryResetDialog
import com.babegetthis.android.core.pin.ui.RegenerateRecoveryDialog
import com.babegetthis.android.core.pin.ui.RemovePinDialog
import com.babegetthis.android.core.ui.components.BgtTopAppBar
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.Switch
import com.babegetthis.android.core.ui.components.SettingsRow
import kotlinx.coroutines.launch

private enum class SettingsDialog { None, SetUp, Change, Remove, Regenerate, Forgot }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val pinExists by viewModel.pinExists.collectAsState()
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsState()
    val crashReportingEnabled by viewModel.crashReportingEnabled.collectAsState()
    val lockedCount by viewModel.lockedCount.collectAsState()
    var dialog by remember { mutableStateOf(SettingsDialog.None) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(pinExists) { viewModel.refreshLockedCount() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            BgtTopAppBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
                onNavigationClick = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_pin_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
            )

            if (!pinExists) {
                SettingsRow(
                    icon = Icons.Outlined.Password,
                    title = stringResource(R.string.settings_pin_set_up),
                    subtitle = stringResource(R.string.settings_pin_set_up_subtitle),
                    onClick = { dialog = SettingsDialog.SetUp },
                )
            } else {
                SettingsRow(
                    icon = Icons.Outlined.Password,
                    title = stringResource(R.string.settings_pin_change),
                    onClick = { dialog = SettingsDialog.Change },
                )
                SettingsRow(
                    icon = Icons.Outlined.LockOpen,
                    title = stringResource(R.string.settings_pin_remove),
                    onClick = { dialog = SettingsDialog.Remove },
                    tint = MaterialTheme.colorScheme.error,
                )
                SettingsRow(
                    icon = Icons.Outlined.Refresh,
                    title = stringResource(R.string.settings_pin_regenerate),
                    onClick = { dialog = SettingsDialog.Regenerate },
                )
                SettingsRow(
                    icon = Icons.Outlined.LockReset,
                    title = stringResource(R.string.settings_pin_forgot),
                    onClick = { dialog = SettingsDialog.Forgot },
                )
            }

            Text(
                text = stringResource(R.string.settings_pin_device_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            Text(
                text = stringResource(R.string.settings_privacy_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
            )

            // Two switches, not one: crash reporting stands on a different
            // footing to product analytics, and a user may want to keep one.
            SettingsRow(
                icon = Icons.Outlined.Analytics,
                title = stringResource(R.string.settings_analytics_title),
                subtitle = stringResource(R.string.settings_analytics_subtitle),
                onClick = { viewModel.setAnalyticsEnabled(!analyticsEnabled) },
                trailing = {
                    Switch(
                        checked = analyticsEnabled,
                        onCheckedChange = { viewModel.setAnalyticsEnabled(it) },
                    )
                },
            )

            SettingsRow(
                icon = Icons.Outlined.BugReport,
                title = stringResource(R.string.settings_crash_title),
                subtitle = stringResource(R.string.settings_crash_subtitle),
                onClick = { viewModel.setCrashReportingEnabled(!crashReportingEnabled) },
                trailing = {
                    Switch(
                        checked = crashReportingEnabled,
                        onCheckedChange = { viewModel.setCrashReportingEnabled(it) },
                    )
                },
            )

            Text(
                text = stringResource(R.string.settings_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            // Debug-only: the one reliable way to confirm crash reporting still
            // works end to end. `adb shell am crash` does NOT do it — its
            // RemoteServiceException comes from the system and bypasses the
            // handler Crashlytics installs, leaving no report on disk at all.
            // This throws from app code on the main thread, which is exactly
            // what gets caught.
            //
            // Strings are literals, not resources: this is never seen by a user
            // and never translated, so putting it in strings.xml would ship
            // developer copy to translators and to the release APK.
            if (BuildConfig.DEBUG) {
                Text(
                    text = "Debug",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                )
                SettingsRow(
                    icon = Icons.Outlined.BugReport,
                    title = "Force a test crash",
                    subtitle = "Relaunch afterwards — reports upload on next start, not on crash.",
                    onClick = { throw RuntimeException("Test crash from Settings") },
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }

    fun toast(resId: Int) {
        scope.launch { snackbar.showSnackbar(context.getString(resId)) }
    }

    when (dialog) {
        SettingsDialog.SetUp -> PinSetupDialog(
            onComplete = { dialog = SettingsDialog.None },
            onDismiss = { dialog = SettingsDialog.None },
        )
        SettingsDialog.Change -> ChangePinDialog(
            onComplete = { dialog = SettingsDialog.None; toast(R.string.settings_pin_changed) },
            onDismiss = { dialog = SettingsDialog.None },
        )
        SettingsDialog.Remove -> RemovePinDialog(
            lockedCount = lockedCount,
            onRemoved = {
                viewModel.onPinRemoved()
                dialog = SettingsDialog.None
                toast(R.string.settings_pin_removed)
            },
            onDismiss = { dialog = SettingsDialog.None },
        )
        SettingsDialog.Regenerate -> RegenerateRecoveryDialog(
            onComplete = { dialog = SettingsDialog.None },
            onDismiss = { dialog = SettingsDialog.None },
        )
        SettingsDialog.Forgot -> RecoveryResetDialog(
            onComplete = { dialog = SettingsDialog.None },
            onDismiss = { dialog = SettingsDialog.None },
        )
        SettingsDialog.None -> Unit
    }
}
