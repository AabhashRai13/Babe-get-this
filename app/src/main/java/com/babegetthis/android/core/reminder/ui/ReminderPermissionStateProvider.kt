package com.babegetthis.android.core.reminder.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.babegetthis.android.core.reminder.PermissionState
import com.babegetthis.android.core.reminder.permissionState

// POST_NOTIFICATIONS is a compile-time constant that is inlined, so referencing it below API 33 is
// safe. Callers only act on it when the state is Requestable, which implies API 33+.
@SuppressLint("InlinedApi")
const val NOTIFICATION_PERMISSION: String = Manifest.permission.POST_NOTIFICATIONS

/**
 * The current [PermissionState], recomputed whenever the screen resumes (the user may have
 * changed it in system settings) and whenever [requested] flips (the system dialog just ran).
 */
// The replacement LocalLifecycleOwner lives in lifecycle-runtime-compose 2.8+; this project is on 2.6.1.
@Suppress("DEPRECATION")
@Composable
fun rememberPermissionState(requested: Boolean): PermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun compute(): PermissionState {
        val granted = ContextCompat.checkSelfPermission(
            context,
            NOTIFICATION_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
        val showRationale = context.findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, NOTIFICATION_PERMISSION)
        } ?: false
        return permissionState(Build.VERSION.SDK_INT, granted, requested, showRationale)
    }

    var state by remember { mutableStateOf(compute()) }

    DisposableEffect(lifecycleOwner, requested) {
        // `requested` may have just changed, so the value held from before is stale.
        state = compute()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = compute()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
