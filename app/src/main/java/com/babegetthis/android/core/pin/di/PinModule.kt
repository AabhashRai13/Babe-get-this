package com.babegetthis.android.core.pin.di

import com.babegetthis.android.core.pin.data.PinClock
import com.babegetthis.android.core.pin.data.SystemPinClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// PinStore and PinRepository are @Inject/@Singleton constructors, so Hilt
// provides them directly. Only the clock interface needs a binding.
@Module
@InstallIn(SingletonComponent::class)
abstract class PinModule {
    @Binds
    @Singleton
    abstract fun bindPinClock(impl: SystemPinClock): PinClock
}
