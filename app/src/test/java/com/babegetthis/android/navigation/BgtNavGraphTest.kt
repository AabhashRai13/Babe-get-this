package com.babegetthis.android.navigation

import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.core.reminder.ReminderTarget
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The real graph needs Hilt view models, so these drive [openReminder] against
 * a stand-in graph that shares the real route strings and arguments.
 */
@RunWith(RobolectricTestRunner::class)
class BgtNavGraphTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        navController.graph =
            navController.createGraph(startDestination = Routes.SHOPPING_LIST) {
                composable(Routes.SHOPPING_LIST) {}
                composable(Routes.SETTINGS) {}
                composable(
                    Routes.SHOPPING_ITEMS,
                    arguments = listOf(
                        navArgument("listId") { type = NavType.StringType },
                        navArgument("listName") { type = NavType.StringType },
                    ),
                ) {}
            }
    }

    /** The back stack as route patterns, without the graph's own root entry. */
    private fun stack(): List<String?> = navController.currentBackStack.value
        .map { it.destination.route }
        .filter { it != null }

    private fun assertOnTarget(target: ReminderTarget) {
        val args = navController.currentBackStackEntry?.arguments
        assertEquals(target.listId, args?.getString("listId"))
        assertEquals(target.listName, args?.getString("listName"))
    }

    @Test
    fun `a pending reminder lands on its items route with the catalog directly beneath`() {
        val target = ReminderTarget("list-1", "Weekly shop")

        navController.openReminder(target)

        assertEquals(listOf(Routes.SHOPPING_LIST, Routes.SHOPPING_ITEMS), stack())
        assertOnTarget(target)
    }

    @Test
    fun `from another list's items screen the old entry is popped`() {
        navController.navigate(Routes.shoppingItems("other", "Other list"))
        val target = ReminderTarget("list-1", "Weekly shop")

        navController.openReminder(target)

        assertEquals(listOf(Routes.SHOPPING_LIST, Routes.SHOPPING_ITEMS), stack())
        assertOnTarget(target)
    }

    @Test
    fun `from settings the settings entry is popped`() {
        navController.navigate(Routes.SETTINGS)
        val target = ReminderTarget("list-1", "Weekly shop")

        navController.openReminder(target)

        assertEquals(listOf(Routes.SHOPPING_LIST, Routes.SHOPPING_ITEMS), stack())
        assertOnTarget(target)
    }

    @Test
    fun `a second reminder replaces the first rather than stacking`() {
        navController.openReminder(ReminderTarget("first", "First"))
        val second = ReminderTarget("second", "Second")

        navController.openReminder(second)

        assertEquals(listOf(Routes.SHOPPING_LIST, Routes.SHOPPING_ITEMS), stack())
        assertOnTarget(second)
    }

    @Test
    fun `a name with slashes and question marks round-trips through the route`() {
        val target = ReminderTarget("list-1", "Dad's / BBQ? #1")

        navController.openReminder(target)

        assertOnTarget(target)
    }
}
