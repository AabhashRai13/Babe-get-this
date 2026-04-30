package com.babegetthis.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.ui.LoginScreen
import com.babegetthis.android.core.auth.ui.RegisterScreen
import com.babegetthis.android.feature.shoppingitems.ui.ShoppingItemsScreen
import com.babegetthis.android.feature.shoppinglist.ui.ShoppingListScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val SHOPPING_LIST = "shopping_list"
    const val SHOPPING_ITEMS = "shopping_items/{listId}/{listName}?isNew={isNew}"

    fun shoppingItems(listId: String, listName: String, isNew: Boolean = false): String {
        return "shopping_items/$listId/$listName?isNew=$isNew"
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
                onNavigateToNewList = { listId, listName ->
                    navController.navigate(Routes.shoppingItems(listId, listName, isNew = true))
                }
            )
        }

        composable(
            route = Routes.SHOPPING_ITEMS,
            arguments = listOf(
                navArgument("listId") { type = NavType.StringType },
                navArgument("listName") { type = NavType.StringType },
                navArgument("isNew") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            )
        ) {
            ShoppingItemsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
            )
        }

        // -- Auth screens (navigated to on demand, not on startup) --
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                // After successful login, go back to where the user came from
                onLoginSuccess = {
                    navController.popBackStack(Routes.LOGIN, inclusive = true)
                },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onNavigateBack = {
                    // Pop back past login screen too — return to the app
                    navController.popBackStack(Routes.LOGIN, inclusive = true)
                },
                // After successful register, pop all the way back past login too
                onRegisterSuccess = {
                    navController.popBackStack(Routes.LOGIN, inclusive = true)
                },
            )
        }
    }
}
