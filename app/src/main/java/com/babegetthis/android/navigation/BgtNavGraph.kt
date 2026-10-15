package com.babegetthis.android.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.ui.ForgotPasswordScreen
import com.babegetthis.android.core.auth.ui.LoginScreen
import com.babegetthis.android.core.auth.ui.RegisterScreen
import com.babegetthis.android.feature.settings.ui.SettingsScreen
import com.babegetthis.android.feature.shoppingitems.ui.ShoppingItemsScreen
import com.babegetthis.android.feature.shoppinglist.ui.ShoppingListScreen
import com.babegetthis.android.feature.welcome.ui.WelcomeScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val SHOPPING_LIST = "shopping_list"
    const val SHOPPING_ITEMS = "shopping_items/{listId}/{listName}"
    const val SETTINGS = "settings"
    const val WELCOME = "welcome"

    fun shoppingItems(listId: String, listName: String): String = "shopping_items/$listId/$listName"
}

// No login wall. A signed-out user who hasn't chosen yet starts on Welcome
// ("Sign in" / "Continue as guest" — docs/technical-decisions/008); everyone
// else starts at the shopping list screen. Guests create lists fully offline.
// Login/register are also reachable on demand (e.g., when tapping Share), and
// after login the user returns to where they were.
// This is like GoRouter in Flutter, but without a redirect guard.

@Composable
fun BgtNavGraph(
    authStateManager: AuthStateManager,
    navController: NavHostController = rememberNavController(),
    startAtWelcome: Boolean = false,
) {
    // Signing out or deleting the account takes the account's lists off the
    // phone (docs/technical-decisions/008), so it is a fresh start: Welcome,
    // with nothing to go back to. A lost session keeps the lists and stays put.
    LaunchedEffect(navController) {
        authStateManager.signOuts.collect {
            navController.navigate(Routes.WELCOME) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (startAtWelcome) Routes.WELCOME else Routes.SHOPPING_LIST,
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onSignIn = { navController.navigate(Routes.LOGIN) },
                onContinueAsGuest = {
                    navController.navigate(Routes.SHOPPING_LIST) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }

        // -- Main app screens (no auth required) --
        composable(Routes.SHOPPING_LIST) {
            ShoppingListScreen(
                authStateManager = authStateManager,
                onNavigateToList = { listId, listName ->
                    navController.navigate(Routes.shoppingItems(listId, listName))
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN)
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        // Settings — reachable regardless of auth state (the PIN is device-wide,
        // not tied to an account).
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.SHOPPING_ITEMS,
            arguments = listOf(
                navArgument("listId") { type = NavType.StringType },
                navArgument("listName") { type = NavType.StringType },
            ),
            // Push: new screen slides in from the right; old slides slightly left.
            // Pop:  reverse — new screen comes back from the left; old slides off right.
            // Matches Material motion ("forward" pattern) for sub-screen navigation.
            // 300ms with FastOutSlowInEasing — Compose's default emphasized curve.
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> fullWidth },
                ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> -fullWidth / 4 },
                ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> fullWidth },
                ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
            },
        ) {
            ShoppingItemsScreen(
                onNavigateBack = { navController.popBackStack() },
                // Live-share sign-in prompt — same destinations the list
                // screen's auth prompt uses.
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
            )
        }

        // -- Auth screens (navigated to on demand, not on startup) --
        composable(Routes.LOGIN) {
            LoginScreen(
                // Replace Login with Register so they never stack up.
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                // Stacked on top of Login so the back arrow returns there.
                onNavigateToForgotPassword = {
                    navController.navigate(Routes.FORGOT_PASSWORD)
                },
                // Close/back returns to wherever auth was opened from; success
                // lands on the lists. See closeAuth / leaveAuth below.
                onNavigateBack = { navController.closeAuth() },
                onLoginSuccess = { navController.leaveAuth() },
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                // Back returns to Login (it's stacked on top of it).
                onNavigateBack = { navController.popBackStack() },
                // A successful reset signs the user in — leave auth entirely.
                onResetSuccess = { navController.leaveAuth() },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                // Replace Register with Login so they never stack up.
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                // Close/back returns to wherever auth was opened from; success
                // lands on the lists. See closeAuth / leaveAuth below.
                onNavigateBack = { navController.closeAuth() },
                onRegisterSuccess = { navController.leaveAuth() },
            )
        }
    }
}

// Auth can be opened from the lists (Share, voice, the profile button) or from
// Welcome, where the lists screen is not on the back stack at all — so a plain
// popBackStack(SHOPPING_LIST) would do nothing and strand the user on Login.

// Close/back: return to the lists if they're underneath, else to whatever
// opened auth (Welcome).
private fun NavHostController.closeAuth() {
    if (!popBackStack(Routes.SHOPPING_LIST, inclusive = false)) popBackStack()
}

// Signed in: land on the lists, removing Welcome if that's where auth started.
private fun NavHostController.leaveAuth() {
    if (!popBackStack(Routes.SHOPPING_LIST, inclusive = false)) {
        navigate(Routes.SHOPPING_LIST) { popUpTo(Routes.WELCOME) { inclusive = true } }
    }
}
