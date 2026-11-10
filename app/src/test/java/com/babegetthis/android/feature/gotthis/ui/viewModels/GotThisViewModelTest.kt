package com.babegetthis.android.feature.gotthis.ui.viewModels

import androidx.lifecycle.SavedStateHandle
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.feature.gotthis.data.BoughtHint
import com.babegetthis.android.feature.gotthis.data.GotThisAccess
import com.babegetthis.android.feature.gotthis.data.GotThisRepository
import com.babegetthis.android.feature.gotthis.data.GotThisTracker
import com.babegetthis.android.feature.gotthis.data.UsualItem
import com.babegetthis.android.feature.gotthis.domain.GotThisRules
import com.babegetthis.android.feature.gotthis.domain.HintSource
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val usualsFlow = MutableStateFlow<List<UsualItem>>(emptyList())

    private fun due(count: Int) = (1..count).map { UsualItem("key$it", "Item $it", "cat$it", count - it) }

    private fun viewModel(
        showsHints: Boolean = true,
        shadows: Boolean = false,
        showsRunningLow: Boolean = true,
    ): GotThisViewModel {
        every { repository.usualsFor(owner) } returns usualsFlow
        every { access.showsRunningLow(owner) } returns showsRunningLow
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

    // --- the usuals row ---

    private fun TestScope.collectUsuals(vm: GotThisViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.usuals.collect {} }
    }

    @Test
    fun `no row while nothing is due`() = runTest {
        val vm = viewModel()
        collectUsuals(vm)

        assertNull(vm.usuals.value)
        coVerify(exactly = 0) { tracker.onUsualsPresent(any(), any()) }
    }

    @Test
    fun `eight due items show six chips and a plus two`() = runTest {
        val vm = viewModel()
        collectUsuals(vm)

        usualsFlow.value = due(8)

        val state = vm.usuals.value!!
        assertEquals(due(8).take(6), state.chips)
        assertEquals(2, state.more)
        assertFalse(state.expanded)
        coVerify { tracker.onUsualsPresent(owner, 8) }
    }

    @Test
    fun `six due items have nothing to expand`() = runTest {
        val vm = viewModel()
        collectUsuals(vm)

        usualsFlow.value = due(6)

        assertEquals(6, vm.usuals.value!!.chips.size)
        assertEquals(0, vm.usuals.value!!.more)
    }

    @Test
    fun `expanding shows every chip`() = runTest {
        val vm = viewModel()
        collectUsuals(vm)
        usualsFlow.value = due(8)

        vm.onExpand()

        val state = vm.usuals.value!!
        assertEquals(8, state.chips.size)
        assertEquals(0, state.more)
        assertTrue(state.expanded)
    }

    @Test
    fun `tapping a chip adds the item with quantity one and its category`() = runTest {
        coEvery { itemRepository.addItem(any(), any(), any(), any()) } returns Result.Success("new")
        val vm = viewModel()
        val chip = due(1).single()

        vm.onChip(chip)

        coVerify { itemRepository.addItem("L1", "Item 1", "1", "cat1") }
        verify { analytics.track(AnalyticsEvent.GotThisUsualAdded) }
    }

    @Test
    fun `a chip that could not be added is not counted`() = runTest {
        coEvery { itemRepository.addItem(any(), any(), any(), any()) } returns Result.Error(AppError.UnknownError("x"))
        val vm = viewModel()

        vm.onChip(due(1).single())

        verify(exactly = 0) { analytics.track(AnalyticsEvent.GotThisUsualAdded) }
    }

    @Test
    fun `not now hides the row for a day`() = runTest {
        val vm = viewModel()
        collectUsuals(vm)
        usualsFlow.value = due(3)

        vm.onNotNow()

        assertNull(vm.usuals.value)
        assertEquals(now + GotThisRules.NOT_NOW_MILLIS, prefs.usualsHiddenUntil)

        now += GotThisRules.NOT_NOW_MILLIS - 1
        vm.onResumed()
        assertNull(vm.usuals.value)

        now += 1
        vm.onResumed()
        assertEquals(3, vm.usuals.value!!.chips.size)
    }

    @Test
    fun `a row hidden by an earlier session stays hidden`() = runTest {
        prefs.usualsHiddenUntil = now + 1
        val vm = viewModel()
        collectUsuals(vm)

        usualsFlow.value = due(3)

        assertNull(vm.usuals.value)
        coVerify(exactly = 0) { tracker.onUsualsPresent(any(), any()) }
    }

    @Test
    fun `the holdout gets no row but the tracker still hears about it`() = runTest {
        val vm = viewModel(showsRunningLow = false, shadows = true)
        collectUsuals(vm)

        usualsFlow.value = due(8)

        assertNull(vm.usuals.value)
        coVerify { tracker.onUsualsPresent(owner, 8) }
    }

    @Test
    fun `switched off gets no row and no tracking`() = runTest {
        val vm = viewModel(showsRunningLow = false, shadows = false)
        collectUsuals(vm)

        usualsFlow.value = due(8)

        assertNull(vm.usuals.value)
        coVerify(exactly = 0) { tracker.onUsualsPresent(any(), any()) }
    }

    @Test
    fun `the heads-up offer follows the setting and the notification gate`() = runTest {
        var enabled = false
        val vm = viewModel()
        every { gate.notificationsEnabled() } answers { enabled }
        collectUsuals(vm)
        usualsFlow.value = due(2)

        vm.onResumed()
        assertTrue(vm.usuals.value!!.offerHeadsUp)

        enabled = true
        vm.onResumed()
        assertFalse(vm.usuals.value!!.offerHeadsUp)

        enabled = false
        prefs.runningLowEnabled = false
        vm.onResumed()
        assertFalse(vm.usuals.value!!.offerHeadsUp)
    }
}
