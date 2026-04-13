package com.babegetthis.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
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

// The nav graph now checks AuthState to decide the start destination.
// If the user has a saved token → go straight to shopping lists.
// If not → show login screen.
// This is like GoRouter's redirect in Flutter.

@Composable
fun BgtNavGraph(
    authStateManager: AuthStateManager,
    navController: NavHostController = rememberNavController(),
) {
    val authState by authStateManager.authState.collectAsState()

    // While loading (checking token), we could show a splash screen.
    // For now, default to login — it will switch instantly once AuthState resolves.
    val startDestination = when (authState) {
        is AuthState.Authenticated -> Routes.SHOPPING_LIST
        is AuthState.Unauthenticated -> Routes.LOGIN
        is AuthState.Loading -> Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        // -- Auth screens --
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // -- Main app screens --
        composable(Routes.SHOPPING_LIST) {
            ShoppingListScreen(
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
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
