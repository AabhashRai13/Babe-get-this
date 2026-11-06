package com.babegetthis.android.core.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPermissionTest {

    @Test
    fun `below api 33 is NotRequired regardless of the other inputs`() {
        for (granted in listOf(false, true)) {
            for (requested in listOf(false, true)) {
                for (showRationale in listOf(false, true)) {
                    assertEquals(
                        PermissionState.NotRequired,
                        permissionState(
                            sdkInt = 32,
                            granted = granted,
                            requested = requested,
                            showRationale = showRationale,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `granted wins on api 33`() {
        for (requested in listOf(false, true)) {
            for (showRationale in listOf(false, true)) {
                assertEquals(
                    PermissionState.Granted,
                    permissionState(
                        sdkInt = 33,
                        granted = true,
                        requested = requested,
                        showRationale = showRationale,
                    ),
                )
            }
        }
    }

    @Test
    fun `the four requested-rationale combinations`() {
        // Ungranted on api 33: only requested && !showRationale is a permanent refusal.
        assertEquals(
            PermissionState.Requestable,
            permissionState(sdkInt = 33, granted = false, requested = false, showRationale = false),
        )
        assertEquals(
            PermissionState.Requestable,
            permissionState(sdkInt = 33, granted = false, requested = false, showRationale = true),
        )
        assertEquals(
            PermissionState.PermanentlyRefused,
            permissionState(sdkInt = 33, granted = false, requested = true, showRationale = false),
        )
        assertEquals(
            PermissionState.Requestable,
            permissionState(sdkInt = 33, granted = false, requested = true, showRationale = true),
        )
    }
}
