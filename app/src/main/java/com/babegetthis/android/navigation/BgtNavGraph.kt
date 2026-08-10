package com.babegetthis.android.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
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

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val SHOPPING_LIST = "shopping_list"
    const val SHOPPING_ITEMS = "shopping_items/{listId}/{listName}"
    const val SETTINGS = "settings"

    fun shoppingItems(listId: String, listName: String): String {
        return "shopping_items/$listId/$listName"
    }
}

// No login wall — the app always starts at the shopping list screen.
// Auth is optional: users can create lists and items fully offline.
// Login/register are reachable on demand (e.g., when tapping Share).
// After login, popBackStack() returns the user to where they were.
// This is like GoRouter in Flutter, but without a redirect guard.

@Composable
fun BgtNavGraph(
    authStateManager: AuthStateManager,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SHOPPING_LIST,
    ) {
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
                }
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
                // Close/back and success both return to the app. Anchored to the
                // start destination so it works no matter how auth was entered.
                onNavigateBack = {
                    navController.popBackStack(Routes.SHOPPING_LIST, inclusive = false)
                },
                onLoginSuccess = {
                    navController.popBackStack(Routes.SHOPPING_LIST, inclusive = false)
                },
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                // Back returns to Login (it's stacked on top of it).
                onNavigateBack = { navController.popBackStack() },
                // A successful reset signs the user in — leave auth entirely.
                onResetSuccess = {
                    navController.popBackStack(Routes.SHOPPING_LIST, inclusive = false)
                },
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
                // Close/back and success both return to the app. Anchored to the
                // start destination so it works no matter how auth was entered.
                onNavigateBack = {
                    navController.popBackStack(Routes.SHOPPING_LIST, inclusive = false)
                },
                onRegisterSuccess = {
                    navController.popBackStack(Routes.SHOPPING_LIST, inclusive = false)
                },
            )
        }
    }
}
