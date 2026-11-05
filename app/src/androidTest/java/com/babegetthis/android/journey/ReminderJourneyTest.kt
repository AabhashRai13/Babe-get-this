package com.babegetthis.android.journey

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.babegetthis.android.MainActivity
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.pin.data.PinStore
import com.babegetthis.android.core.reminder.ReminderIntentParser
import com.babegetthis.android.core.reminder.ReminderNotifier
import com.babegetthis.android.core.reminder.ReminderScheduler
import com.babegetthis.android.core.reminder.WorkManagerEnqueuer
import com.babegetthis.android.core.ui.TestTags
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import com.babegetthis.android.testing.ResetAppStateRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

// The reminder feature end to end on the real stack: ticking part of a list
// arms the job, the real ReminderWorker posts the notification when its delay is
// met, a second list replaces the first, and tapping the notification opens
// that list above the catalog.
//
// WorkManager is the test build from TestReminderPlatformModule (synchronous
// executor, foreground pinned to false), so the three-hour wait is skipped by
// asking the test driver to treat the initial delay as met.
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReminderJourneyTest {

    // Granted before anything launches, so the permission prompt never shows.
    @get:Rule(order = 0)
    val grant: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var database: AppDatabase

    @Inject lateinit var authStateManager: AuthStateManager

    @Inject lateinit var pinStore: PinStore

    @Inject lateinit var listDao: ShoppingListDao

    @Inject lateinit var featureFlags: FeatureFlagCache

    @get:Rule(order = 2)
    val reset = ResetAppStateRule(hilt, { database }, { authStateManager }, { pinStore })

    @get:Rule(order = 3)
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    private lateinit var launchIntent: Intent

    @Before
    fun setUp() {
        launchIntent = compose.activity.intent
        notificationManager.cancelAll()
        // The kill switch defaults to off until BabeGetThisApp loads the remote
        // flags, which a test build never does; the feature is live in prod.
        featureFlags.update(mapOf(ReminderScheduler.FLAG_KEY to true))
    }

    // MainActivity keeps the intent it is handed in onNewIntent, and
    // ActivityScenario only tracks an activity whose intent still matches the
    // one it launched with. Without restoring it, the scenario never sees the
    // activity being destroyed and close() hangs for 45 seconds, then fails.
    @After
    fun restoreLaunchIntent() {
        compose.activityRule.scenario.onActivity { it.intent = launchIntent }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitGone(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun present(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun anItemCheckbox() = SemanticsMatcher("testTag starts with item checkbox prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)
            ?.startsWith(TestTags.ITEM_CHECKBOX_PREFIX) == true
    }

    private fun createList(name: String) {
        compose.onNodeWithText(
            if (present("Create your first list")) "Create your first list" else "Create list",
        ).performClick()
        awaitText("Type")
        compose.onNodeWithText("Type").performClick()
        awaitText("New List")
        compose.onNodeWithText("List name").performTextInput(name)
        compose.onNodeWithText("Create").performClick()
    }

    private fun addItem(name: String) {
        if (present("Add first item")) {
            compose.onNodeWithText("Add first item").performClick()
        } else {
            compose.onNodeWithTag(TestTags.ADD_ITEM_FAB).performClick()
        }
        awaitText("Add Item")
        compose.onNodeWithText("Item name").performTextInput(name)
        compose.onNodeWithText("Quantity (required)").performTextInput("1")
        compose.onNode(hasText("Add") and !hasTestTag(TestTags.ADD_ITEM_FAB)).performClick()
        awaitGone("Add Item")
        awaitText(name)
    }

    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    // Ticks the first still-active item and waits for the tick to land, which
    // goes through Room's background executor that the compose clock cannot see.
    private fun tickFirstItem(progress: String) {
        compose.onAllNodes(anItemCheckbox()).onFirst().performClick()
        awaitText(progress)
    }

    // The pending reminder job. After a retarget the replaced job lingers as
    // CANCELLED, so the live one is the ENQUEUED entry.
    private fun pendingJobs(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(WorkManagerEnqueuer.UNIQUE_NAME).get()
        .filter { it.state == WorkInfo.State.ENQUEUED }

    private fun awaitPendingJob(notIn: Set<UUID> = emptySet()): UUID {
        compose.waitUntil(timeoutMillis = 10_000) { pendingJobs().any { it.id !in notIn } }
        return pendingJobs().single { it.id !in notIn }.id
    }

    private fun fireReminder(jobId: UUID) {
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(jobId)
    }

    private fun activeReminderTitles(): List<String> = notificationManager.activeNotifications
        .filter { it.id == ReminderNotifier.NOTIFICATION_ID }
        .map { it.notification.extras.getString(Notification.EXTRA_TITLE).orEmpty() }

    private fun listId(name: String): String = runBlocking {
        listDao.getListsWithItemCount(null).first().single { it.name == name }.id
    }

    @Test
    fun aHalfShoppedListNudgesOnceASecondListReplacesItAndTheTapOpensIt() {
        // Act 1: tick one of two items and let the reminder fire.
        createList("Weekend shop")
        awaitText("Add your first item to get started")
        addItem("Milk")
        addItem("Eggs")
        awaitText("0 of 2 picked up")
        tickFirstItem("1 of 2 picked up")

        val weekendJob = awaitPendingJob()
        fireReminder(weekendJob)

        compose.waitUntil(timeoutMillis = 10_000) { notificationManager.activeNotifications.size == 1 }
        assertEquals(1, activeReminderTitles().size)
        assertTrue(activeReminderTitles().single().contains("Weekend shop"))

        // Act 2: a second half-shopped list takes over the single reminder.
        back()
        awaitText("Create list")
        createList("Hardware")
        awaitText("Add your first item to get started")
        addItem("Nails")
        addItem("Screws")
        awaitText("0 of 2 picked up")
        tickFirstItem("1 of 2 picked up")

        val hardwareJob = awaitPendingJob(notIn = setOf(weekendJob))
        fireReminder(hardwareJob)

        compose.waitUntil(timeoutMillis = 10_000) { activeReminderTitles().any { it.contains("Hardware") } }
        assertEquals(1, notificationManager.activeNotifications.size)
        assertEquals(1, activeReminderTitles().size)
        assertTrue(activeReminderTitles().single().contains("Hardware"))

        // Act 3: the tap. The content intent cannot be sent from a test, so the
        // activity is started again with the same extras; it is single-top, so
        // this reaches onNewIntent on the running instance, as the tap would.
        // Leave the Hardware list first so the tap has to bring it back.
        val hardwareId = listId("Hardware")
        back()
        awaitText("Create list")
        compose.activity.startActivity(
            Intent(compose.activity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(ReminderIntentParser.EXTRA_LIST_ID, hardwareId)
                putExtra(ReminderIntentParser.EXTRA_LIST_NAME, "Hardware")
            },
        )
        awaitText("Nails")
        awaitText("Screws")

        // The catalog is beneath the list: Back lands on it, with both lists.
        Espresso.pressBack()
        awaitGone("Nails")
        awaitText("Hardware")
        awaitText("Weekend shop")
    }
}
