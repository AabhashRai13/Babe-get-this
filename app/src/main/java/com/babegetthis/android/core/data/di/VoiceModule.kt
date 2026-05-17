package com.babegetthis.android.core.data.di

import com.babegetthis.android.core.voice.data.repository.MockVoiceRepository
import com.babegetthis.android.core.voice.data.repository.VoiceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Hilt module that tells the graph: "When someone asks for VoiceRepository,
// give them MockVoiceRepository." When the backend lands, change this one
// line to bind RemoteVoiceRepository and the rest of the app doesn't move.
//
// Uses @Binds (not @Provides) because the impl already has @Inject constructor —
// Hilt knows how to build it, we just need to say which interface it satisfies.
// @Binds modules must be abstract classes (not objects).
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindVoiceRepository(impl: MockVoiceRepository): VoiceRepository
}
