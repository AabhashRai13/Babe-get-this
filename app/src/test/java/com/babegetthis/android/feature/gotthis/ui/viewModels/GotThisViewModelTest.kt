package com.babegetthis.android.feature.gotthis.ui.viewModels

import androidx.lifecycle.SavedStateHandle
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.feature.gotthis.data.BoughtHint
import com.babegetthis.android.feature.gotthis.data.GotThisAccess
import com.babegetthis.android.feature.gotthis.data.GotThisRepository
import com.babegetthis.android.feature.gotthis.data.GotThisTracker
import com.babegetthis.android.feature.gotthis.domain.HintSource
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GotThisViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val owner = "owner-1"
    private val repository = mockk<GotThisRepository>()
    private val access = mockk<GotThisAccess>()
    private val tracker = mockk<GotThisTracker>(relaxed = true)
    private val itemRepository = mockk<ShoppingItemRepository>(relaxed = true)
    private val prefs = FakeGotThisPrefs()
    private val gate = mockk<NotificationGate>()
    private val analytics = mockk<AnalyticsRepository>(relaxed = true)
    private var now = 1_000_000L
    private val clock = ReminderClock { now }

    private val hintsFlow = MutableStateFlow<Map<String, BoughtHint>>(emptyMap())

    private val milk = BoughtHint("i1", 3, HintSource.Regular, "milk")

    private fun viewModel(showsHints: Boolean = true, shadows: Boolean = false): GotThisViewModel {
        coEvery { repository.ownerOf("L1") } returns owner
        every { repository.hintsFor("L1") } returns hintsFlow
        every { access.showsHints(owner) } returns showsHints
        every { access.shadows(owner) } returns shadows
        every { gate.notificationsEnabled() } returns true
        return GotThisViewModel(
            savedStateHandle = SavedStateHandle(mapOf("listId" to "L1")),
            repository = repository,
            access = access,
            tracker = tracker,
            itemRepository = itemRepository,
            prefs = prefs,
            notificationGate = gate,
            analytics = analytics,
            clock = clock,
        )
    }

    @Test
    fun `hints flow through when hints are allowed`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.hints.collect {} }

        hintsFlow.value = mapOf("i1" to milk)

        assertEquals(mapOf("i1" to milk), vm.hints.value)
        coVerify { tracker.onHintsPresent(owner, match { it.single() == milk }) }
    }

    @Test
    fun `hints are empty in the holdout while the tracker still hears about them`() = runTest {
        val vm = viewModel(showsHints = false, shadows = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.hints.collect {} }

        hintsFlow.value = mapOf("i1" to milk)

        assertEquals(emptyMap<String, BoughtHint>(), vm.hints.value)
        coVerify { tracker.onHintsPresent(owner, match { it.single() == milk }) }
    }

    @Test
    fun `hints are empty and untracked when hints are switched off`() = runTest {
        val vm = viewModel(showsHints = false, shadows = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.hints.collect {} }

        hintsFlow.value = mapOf("i1" to milk)

        assertEquals(emptyMap<String, BoughtHint>(), vm.hints.value)
        coVerify(exactly = 0) { tracker.onHintsPresent(any(), any()) }
    }

    @Test
    fun `no hints means the tracker is not called`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.hints.collect {} }

        coVerify(exactly = 0) { tracker.onHintsPresent(any(), any()) }
    }

    @Test
    fun `muting a hint tells the tracker`() = runTest {
        val vm = viewModel()

        vm.onHintMute("i1")

        coVerify { tracker.onHintMuted("i1") }
    }
}
