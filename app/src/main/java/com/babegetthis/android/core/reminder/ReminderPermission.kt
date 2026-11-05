package com.babegetthis.android.core.reminder

/** Where the user stands on the runtime notification permission, as the reminder UI sees it. */
enum class PermissionState { NotRequired, Granted, Requestable, PermanentlyRefused }

/**
 * Derives [PermissionState] from the four observable inputs. Pure so it can be unit-tested
 * without Android.
 *
 * [requested] and [showRationale] together identify a permanent refusal: once the system has
 * been asked and no longer offers a rationale, further requests are silently ignored, so the
 * user must be sent to system settings instead.
 */
fun permissionState(sdkInt: Int, granted: Boolean, requested: Boolean, showRationale: Boolean): PermissionState = when {
    sdkInt < 33 -> PermissionState.NotRequired
    granted -> PermissionState.Granted
    requested && !showRationale -> PermissionState.PermanentlyRefused
    else -> PermissionState.Requestable
}
