package com.babegetthis.android.core.voice.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.voice.model.ItemDraft
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject

// Fake VoiceRepository so we can build/test the ViewModel and UI today
// without waiting for the backend. Ignores the audio file completely —
// just waits a beat and returns a hardcoded list.
//
// Swap this binding for RemoteVoiceRepository in VoiceModule once the API lands.
class MockVoiceRepository @Inject constructor() : VoiceRepository {
    override suspend fun transcribeAndParse(audioFile: File): Result<List<ItemDraft>> {
        delay(800) // pretend the network call took ~800ms — proves the spinner works
        return Result.Success(
            listOf(
                ItemDraft(name = "Eggs", quantity = "1 crate"),
                ItemDraft(name = "Coke", quantity = "2 L"),
                ItemDraft(name = "Dish soap", quantity = null),
            )
        )
    }
}
