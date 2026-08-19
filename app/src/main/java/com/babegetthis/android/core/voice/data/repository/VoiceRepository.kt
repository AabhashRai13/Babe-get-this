package com.babegetthis.android.core.voice.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.voice.model.ItemDraft
import java.io.File

// Contract for "turn this audio file into a list of items".
// The real implementation will POST the file to a backend that runs
// Whisper (speech-to-text) + Haiku (parse into items). For now we'll
// bind a Mock so the rest of the feature can be built without the API.
interface VoiceRepository {
    suspend fun transcribeAndParse(audioFile: File): Result<List<ItemDraft>>
}
