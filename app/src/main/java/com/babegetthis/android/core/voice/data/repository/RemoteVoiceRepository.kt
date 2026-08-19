package com.babegetthis.android.core.voice.data.repository

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import com.babegetthis.android.core.network.NetworkMonitor
import com.babegetthis.android.core.voice.data.remote.TranscribeApiService
import com.babegetthis.android.core.voice.data.remote.dto.TranscribeItemDto
import com.babegetthis.android.core.voice.model.ItemDraft
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

// Real VoiceRepository: uploads the recorded audio to POST /transcribe and maps
// the response into ItemDrafts. safeCall wraps the network call and maps any
// exception (no internet, timeout, 4xx/5xx) to the right AppError, so the
// ViewModel just gets a Result back — same contract the Mock honored.
class RemoteVoiceRepository @Inject constructor(
    private val api: TranscribeApiService,
    private val networkMonitor: NetworkMonitor,
) : VoiceRepository {

    override suspend fun transcribeAndParse(audioFile: File): Result<List<ItemDraft>> {
        // Proactive guard — transcription needs the backend. Fail fast offline
        // instead of waiting for the upload to time out.
        if (!networkMonitor.isOnline()) {
            return Result.Error(AppError.NetworkError())
        }
        return safeCall(
            // A 4xx here is about the recording (bad/garbled audio, too large),
            // not authentication — give a transcribe-appropriate message instead
            // of safeCall's default auth-flavored one.
            onClientError = { code ->
                AppError.ServerError(code, "Couldn't process that recording. Please try again.")
            },
        ) {
            // Build the multipart "audio" part from the recorded .m4a file. "audio/mp4"
            // is the correct container MIME for our AAC-in-MPEG4 recording.
            val requestBody = audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData(
                name = "audio",
                filename = audioFile.name,
                body = requestBody,
            )
            api.transcribe(part).items.map { it.toItemDraft() }
        }
    }
}

// Flatten quantity + unit into the single quantity string ItemDraft uses:
//   2 + "bottles" -> "2 bottles"   |   2 + null -> "2"   |   null + null -> null
// category + note are carried through verbatim.
// internal (not private) so the quantity+unit flatten logic is unit-testable.
internal fun TranscribeItemDto.toItemDraft(): ItemDraft = ItemDraft(
    name = name,
    quantity = listOfNotNull(quantity?.toString(), unit).joinToString(" ").ifBlank { null },
    note = note,
    category = category,
    shop = location,
)
