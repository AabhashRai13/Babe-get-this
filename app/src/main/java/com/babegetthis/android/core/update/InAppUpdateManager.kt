package com.babegetthis.android.core.update

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Release priority (Play Console → Edit release → in-app update priority, 0-5)
// at or above this is treated as urgent → IMMEDIATE flow. See
// docs/technical-decisions/003-in-app-update-and-feature-flags.md.
private const val URGENT_PRIORITY_THRESHOLD = 4
private const val UPDATE_REQUEST_CODE = 4802

@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    // Set only when a flexible download finished while the app was in the
    // foreground — MainActivity shows a Snackbar and calls completeUpdate()
    // on user consent. If the app was backgrounded when it finished, we call
    // completeUpdate() ourselves and this never flips true.
    private val _updateReadyToInstall = MutableStateFlow(false)
    val updateReadyToInstall: StateFlow<Boolean> = _updateReadyToInstall

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            onDownloadFinished()
        }
    }

    init {
        appUpdateManager.registerListener(listener)
    }

    // Call from Activity.onResume() — also catches an update that already
    // finished downloading while the app was away.
    fun checkForUpdate(activity: Activity) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                onDownloadFinished()
                return@addOnSuccessListener
            }
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener

            val updateType = if (info.updatePriority() >= URGENT_PRIORITY_THRESHOLD) {
                AppUpdateType.IMMEDIATE
            } else {
                AppUpdateType.FLEXIBLE
            }
            if (!info.isUpdateTypeAllowed(updateType)) return@addOnSuccessListener

            appUpdateManager.startUpdateFlowForResult(
                info,
                activity,
                AppUpdateOptions.newBuilder(updateType).build(),
                UPDATE_REQUEST_CODE,
            )
        }
    }

    // Triggers the actual install + process restart. Safe to call only after
    // updateReadyToInstall is true (user tapped "Restart" on the Snackbar).
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
        _updateReadyToInstall.value = false
    }

    private fun onDownloadFinished() {
        if (isAppInForeground()) {
            _updateReadyToInstall.value = true
        } else {
            completeUpdate()
        }
    }

    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
