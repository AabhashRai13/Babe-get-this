package com.babegetthis.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.update.InAppUpdateManager
import com.babegetthis.android.navigation.BgtNavGraph
import com.babegetthis.android.ui.theme.BabeGetThisTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Hilt injects the singleton AuthStateManager here.
    // Like using GetIt.instance<AuthStateManager>() in Flutter.
    @Inject lateinit var authStateManager: AuthStateManager
    @Inject lateinit var inAppUpdateManager: InAppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if user has a saved token — this determines the start screen
        authStateManager.initialize()

        setContent {
            BabeGetThisTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    BgtNavGraph(authStateManager = authStateManager)

                    val snackbarHostState = remember { SnackbarHostState() }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    // Only surfaces when a flexible update finished downloading
                    // while the app was in the foreground (see InAppUpdateManager).
                    val updateReady by inAppUpdateManager.updateReadyToInstall.collectAsStateWithLifecycle()
                    LaunchedEffect(updateReady) {
                        if (updateReady) {
                            val result = snackbarHostState.showSnackbar(
                                message = "Update ready to install",
                                actionLabel = "Restart",
                                duration = SnackbarDuration.Indefinite,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                inAppUpdateManager.completeUpdate()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Also catches an update flow that was already in progress when the
        // app was last backgrounded (Play's own recommendation).
        inAppUpdateManager.checkForUpdate(this)
    }
}
