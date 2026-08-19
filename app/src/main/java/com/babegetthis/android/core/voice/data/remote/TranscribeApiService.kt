package com.babegetthis.android.core.voice.data.remote

import com.babegetthis.android.core.voice.data.remote.dto.TranscribeResponseDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// Retrofit interface for the audio-transcribe backend.
//
// @Multipart + @Part = a multipart/form-data upload (like a browser file form).
// The Authorization: Bearer <supabase_access_token> header is NOT set here —
// AuthInterceptor attaches it to every outgoing Retrofit request automatically.
//
// The endpoint is "transcribe" (no leading slash) — it resolves against the
// flavor's BASE_URL, which now points at the backend root (no "/api/" suffix).
interface TranscribeApiService {

    @Multipart
    @POST("transcribe")
    suspend fun transcribe(
        @Part audio: MultipartBody.Part,
    ): TranscribeResponseDto
}
