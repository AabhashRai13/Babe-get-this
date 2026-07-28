package com.babegetthis.android.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// Replaces Dispatchers.Main for the duration of one test and puts it back after,
// so viewModelScope work is controllable and no test leaks a main dispatcher into
// the next one. Replaces the setUp/tearDown pair the existing ViewModel tests
// each hand-roll.
//
// Defaults to UnconfinedTestDispatcher to match what those tests already do: it
// runs coroutines eagerly, so `init { }` blocks and viewModelScope.launch are
// observable without an advanceUntilIdle() after every call. Pass a
// StandardTestDispatcher when a test needs to control ordering explicitly.
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
