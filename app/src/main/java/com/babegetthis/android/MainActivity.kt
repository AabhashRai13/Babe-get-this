package com.babegetthis.android

import android.content.Intent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.onboarding.data.repository.WelcomeRepository
import com.babegetthis.android.core.reminder.ReminderIntentParser
import com.babegetthis.android.core.reminder.ReminderTarget
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.TelemetryContext
import com.babegetthis.android.core.telemetry.model.Screen
import com.babegetthis.android.core.update.InAppUpdateManager
import com.babegetthis.android.navigation.BgtNavGraph
import com.babegetthis.android.ui.theme.BabeGetThisTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Hilt injects the singleton AuthStateManager here.
    // Like using GetIt.instance<AuthStateManager>() in Flutter.
    @Inject lateinit var authStateManager: AuthStateManager

    @Inject lateinit var inAppUpdateManager: InAppUpdateManager

    @Inject lateinit var telemetryContext: TelemetryContext

    @Inject lateinit var welcomeRepository: WelcomeRepository

    @Inject lateinit var analytics: AnalyticsRepository

    // The list a tapped reminder wants opened. Nulled by the nav graph once
    // consumed, so a rotation does not navigate again.
    private val pendingReminder = MutableStateFlow<ReminderTarget?>(null)

    // Set when the running-low notification had no list to open: the list
    // catalog shows with the name dialog open. Reset once consumed.
    private val pendingCreateList = MutableStateFlow(false)

    private var startAtWelcome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, deliberately: this is what swaps the launch
        // theme (Theme.BabeGetThis.Starting) for the app theme. Called any later
        // and the splash window is already gone.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if user has a saved token — this determines the start screen
        authStateManager.initialize()
        startAtWelcome = welcomeRepository.shouldShowWelcome(authStateManager.authState.value)
        pendingReminder.value = ReminderIntentParser.handleReminderIntent(
            intent,
            startAtWelcome,
            restored = savedInstanceState != null,
            analytics::track,
        )
        pendingCreateList.value = !startAtWelcome && savedInstanceState == null &&
            ReminderIntentParser.wantsCreateList(intent)

        setContent {
            BabeGetThisTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Hoisted out of BgtNavGraph's default so the destination
                    // listener below can attach to it. One hook feeds screen
                    // views, crash breadcrumbs, and the last-screen key —
                    // there is no second place that knows about navigation.
                    val navController = rememberNavController()
                    DisposableEffect(navController) {
                        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                            // destination.route is the PATTERN, arguments and
                            // all — resolved through the Screen enum so the
                            // user-authored list name in SHOPPING_ITEMS never
                            // reaches telemetry.
                            telemetryContext.onScreen(Screen.fromRoute(destination.route))
                        }
                        navController.addOnDestinationChangedListener(listener)
                        onDispose { navController.removeOnDestinationChangedListener(listener) }
                    }

                    val reminder by pendingReminder.collectAsStateWithLifecycle()
                    val createList by pendingCreateList.collectAsStateWithLifecycle()
                    BgtNavGraph(
                        authStateManager = authStateManager,
                        navController = navController,
                        startAtWelcome = startAtWelcome,
                        pendingReminder = reminder,
                        onPendingReminderConsumed = { pendingReminder.value = null },
                        pendingCreateList = createList,
                        onPendingCreateListConsumed = { pendingCreateList.value = false },
                    )

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

    // The reminder's intent is SINGLE_TOP | CLEAR_TOP, so a tap while the app is
    // running delivers here instead of starting a second activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Re-read, not the value from onCreate: the user may have signed out
        // since, and a tap must then be ignored rather than land an evicted
        // list on top of Welcome. Kept local on purpose: the startAtWelcome
        // field feeds NavHost's startDestination, and changing it would make
        // the next recomposition swap the graph and reset the back stack.
        val welcomeNow = welcomeRepository.shouldShowWelcome(authStateManager.authState.value)
        pendingReminder.value =
            ReminderIntentParser.handleReminderIntent(intent, welcomeNow, restored = false, analytics::track)
        pendingCreateList.value = !welcomeNow && ReminderIntentParser.wantsCreateList(intent)
    }

    override fun onResume() {
        super.onResume()
        // Also catches an update flow that was already in progress when the
        // app was last backgrounded (Play's own recommendation).
        inAppUpdateManager.checkForUpdate(this)
    }
}
