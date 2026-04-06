package com.babegetthis.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babegetthis.android.feature.shoppingitems.ui.ShoppingItemsScreen
import com.babegetthis.android.feature.shoppinglist.ui.ShoppingListScreen

object Routes {
    const val SHOPPING_LIST = "shopping_list"
    const val SHOPPING_ITEMS = "shopping_items/{listId}/{listName}?isNew={isNew}"

    fun shoppingItems(listId: String, listName: String, isNew: Boolean = false): String {
        return "shopping_items/$listId/$listName?isNew=$isNew"
    }
}

@Composable
fun BgtNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SHOPPING_LIST
    ) {
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
