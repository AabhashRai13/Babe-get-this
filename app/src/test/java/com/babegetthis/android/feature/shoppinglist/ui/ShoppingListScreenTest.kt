package com.babegetthis.android.feature.shoppinglist.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.ui.TestTags
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList
import com.babegetthis.android.feature.shoppinglist.ui.viewModels.ShoppingListViewModel
import com.babegetthis.android.testing.TestData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Driven by a REAL ShoppingListViewModel over a mocked repository, rather than a
// mocked ViewModel. Stubbing ten StateFlows by hand would let the screen and the
// ViewModel's derived state drift apart silently — here the tab split, grouping
// and counts are the genuine article, and only the data source is faked.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShoppingListScreenTest {

    @get:Rule val compose = createComposeRule()

    private val repository = mockk<ShoppingListRepository>(relaxed = true)
    private val listsFlow = MutableStateFlow<List<ShoppingList>>(emptyList())
    private val authStateManager = mockk<AuthStateManager>(relaxed = true)

    private var navigatedTo: Pair<String, String>? = null
    private var navigatedToSettings = false
    private var navigatedToLogin = false

    private val now = System.currentTimeMillis()

    private fun list(
        id: String,
        name: String,
        createdAt: Long = now,
        itemCount: Int = 0,
        completedItemCount: Int = 0,
        isLocked: Boolean = false,
    ) = TestData.list(id, name, createdAt, createdAt, isLocked, itemCount, completedItemCount)

    private fun render(
        lists: List<ShoppingList> = emptyList(),
        loggedIn: Boolean = true,
    ) {
        listsFlow.value = lists
        every { repository.getAllLists() } returns listsFlow
        every { authStateManager.authState } returns MutableStateFlow(
            if (loggedIn) AuthState.Authenticated("u1") else AuthState.Unauthenticated
        )
        every { authStateManager.userName } returns MutableStateFlow(if (loggedIn) "Aabhash" else null)
        every { authStateManager.userEmail } returns MutableStateFlow(if (loggedIn) "a@b.c" else null)

        val vm = ShoppingListViewModel(
            repository,
            mockk(relaxed = true),
            authStateManager,
            mockk(relaxed = true),
            mockk(relaxed = true),
            CoroutineScope(UnconfinedTestDispatcher()),
        )
        compose.setContent {
            ShoppingListScreen(
                authStateManager = authStateManager,
                onNavigateToList = { id, name -> navigatedTo = id to name },
                onNavigateToLogin = { navigatedToLogin = true },
                onNavigateToSettings = { navigatedToSettings = true },
                viewModel = vm,
            )
        }
        viewModel = vm
    }

    private lateinit var viewModel: ShoppingListViewModel

    // --- 3.2 states ---

    @Test
    fun `no lists shows the whole-screen empty state`() {
        render(lists = emptyList())

        compose.onNodeWithText("No lists yet").assertIsDisplayed()
        compose.onNodeWithText("Create your first list").assertIsDisplayed()
    }

    // The FAB is suppressed in the empty state — the empty state has its own CTA,
    // and two create buttons on one screen would be noise.
    @Test
    fun `no lists hides the create FAB`() {
        render(lists = emptyList())

        compose.onNodeWithText("Create list").assertDoesNotExist()
    }

    // Scrolled to explicitly. This is a LazyColumn under a large top app bar and a
    // greeting card, so on Robolectric's default display the second row is below
    // the fold — and a LazyColumn does not compose what it has not scrolled to, so
    // the node does not merely fail assertIsDisplayed, it does not exist. Scrolling
    // is the honest way to assert it renders; a bigger fake display would only be
    // hiding the same dependency.
    @Test
    fun `active lists render on the active tab`() {
        render(lists = listOf(list("a", "Groceries"), list("b", "Hardware")))

        compose.onNodeWithText("Groceries").assertIsDisplayed()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hardware"))
        compose.onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `completed lists are hidden on the active tab`() {
        render(
            lists = listOf(
                list("a", "Groceries", itemCount = 1),
                list("b", "Done List", itemCount = 2, completedItemCount = 2),
            )
        )

        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText("Done List").assertDoesNotExist()
    }

    @Test
    fun `switching to the completed tab swaps which lists show`() {
        render(
            lists = listOf(
                list("a", "Groceries", itemCount = 1),
                list("b", "Done List", itemCount = 2, completedItemCount = 2),
            )
        )

        compose.onNodeWithTag(TestTags.listTab(1)).performClick()

        compose.onNodeWithText("Done List").assertIsDisplayed()
        compose.onNodeWithText("Groceries").assertDoesNotExist()
    }

    @Test
    fun `an all-completed set shows the active tab's empty state`() {
        render(lists = listOf(list("b", "Done List", itemCount = 2, completedItemCount = 2)))

        compose.onNodeWithText("All done! All your lists are completed.").assertIsDisplayed()
    }

    @Test
    fun `no completed lists shows that tab's empty state`() {
        render(lists = listOf(list("a", "Groceries", itemCount = 1)))

        compose.onNodeWithTag(TestTags.listTab(1)).performClick()

        compose.onNodeWithText("No completed lists yet").assertIsDisplayed()
    }

    @Test
    fun `time period headers render in order`() {
        val day = 24 * 60 * 60 * 1000L
        render(
            lists = listOf(
                list("a", "Today List", createdAt = now),
                list("b", "Old List", createdAt = now - 90 * day),
            )
        )

        compose.onNodeWithText("Today").assertIsDisplayed()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Older"))
        compose.onNodeWithText("Older").assertIsDisplayed()
    }

    @Test
    fun `the greeting summarises outstanding items`() {
        render(lists = listOf(list("a", "Groceries", itemCount = 5, completedItemCount = 2)))

        compose.onNodeWithText("1 list · 3 items to get").assertIsDisplayed()
    }

    @Test
    fun `a locked list shows its lock badge`() {
        render(lists = listOf(list("a", "Private", isLocked = true)))

        compose.onNodeWithContentDescription("Locked").assertIsDisplayed()
    }

    // --- 3.3 interactions ---

    // One list on purpose: a second card would sit below the fold and a LazyColumn
    // doesn't compose what it hasn't scrolled to, so the node wouldn't exist to
    // tap. The claim under test — that the tapped card passes ITS own id and name
    // — is fully exercised by one card with distinctive values.
    @Test
    fun `tapping a card navigates with that list's id and name`() {
        render(lists = listOf(list("b", "Hardware")))

        compose.onNodeWithTag(TestTags.listCard("b")).performClick()

        assertEquals("b" to "Hardware", navigatedTo)
    }

    @Test
    fun `long pressing a card opens the rename dialog`() {
        render(lists = listOf(list("a", "Groceries")))

        compose.onNodeWithTag(TestTags.listCard("a")).performTouchInput { longClick() }

        compose.onNodeWithText("Rename List").assertIsDisplayed()
    }

    @Test
    fun `the FAB opens the create chooser`() {
        render(lists = listOf(list("a", "Groceries")))

        compose.onNodeWithText("Create list").performClick()

        compose.onNodeWithText("Type").assertIsDisplayed()
        compose.onNodeWithText("Voice").assertIsDisplayed()
    }

    @Test
    fun `choosing Type opens the create dialog`() {
        render(lists = listOf(list("a", "Groceries")))
        compose.onNodeWithText("Create list").performClick()

        compose.onNodeWithText("Type").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("New List").assertIsDisplayed()
    }

    @Test
    fun `creating from the dialog reaches the repository`() {
        coEvery { repository.createList(any()) } returns Result.Success("new-id")
        render(lists = listOf(list("a", "Groceries")))
        compose.onNodeWithText("Create list").performClick()
        compose.onNodeWithText("Type").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("List name").performTextInput("Hardware")
        compose.onNodeWithText("Create").performClick()

        coVerify { repository.createList("Hardware") }
    }

    @Test
    fun `renaming from the long-press dialog reaches the repository`() {
        coEvery { repository.updateListName(any(), any()) } returns Result.Success(Unit)
        render(lists = listOf(list("a", "Groceries")))
        compose.onNodeWithTag(TestTags.listCard("a")).performTouchInput { longClick() }

        // "Groceries" now matches twice — the card still behind the dialog, and
        // the dialog's pre-filled field. Narrow to the one that accepts text.
        compose.onNode(hasSetTextAction() and hasText("Groceries"))
            .performTextReplacement("Hardware")
        compose.onNodeWithText("Save").performClick()

        coVerify { repository.updateListName("a", "Hardware") }
    }

    @Test
    fun `the settings action navigates to settings`() {
        render(lists = listOf(list("a", "Groceries")))

        compose.onNodeWithContentDescription("Settings").performClick()

        assertTrue(navigatedToSettings)
    }

    // --- 3.4 events ---

    @Test
    fun `an error emission shows a snackbar with that exact message`() {
        coEvery { repository.createList(any()) } returns
            Result.Error(AppError.ValidationError("List name can't be empty."))
        render(lists = listOf(list("a", "Groceries")))

        viewModel.createList("x")

        compose.onNodeWithText("List name can't be empty.").assertIsDisplayed()
    }

    @Test
    fun `deleting emits a snackbar naming the list, with an Undo action`() {
        coEvery { repository.deleteListAndCaptureItems("a") } returns Result.Success(emptyList())
        render(lists = listOf(list("a", "Groceries")))

        viewModel.deleteList("a")

        compose.onNodeWithText("Groceries deleted").assertIsDisplayed()
        compose.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun `tapping Undo on the snackbar restores the list`() {
        coEvery { repository.deleteListAndCaptureItems("a") } returns Result.Success(emptyList())
        coEvery { repository.restoreListWithItems(any(), any()) } returns Result.Success(Unit)
        render(lists = listOf(list("a", "Groceries")))
        viewModel.deleteList("a")

        compose.onNodeWithText("Undo").performClick()

        coVerify { repository.restoreListWithItems(any(), any()) }
    }
}
