package com.babegetthis.android.core.telemetry.model

import com.babegetthis.android.core.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

// These labels are what GA4 segments failures by. They are derived from the
// error TYPE, never its message, because ServerError and UnknownError can carry
// text that came off the wire.
class FailureReasonsTest {

    @Test
    fun `voice failures map by type`() {
        assertEquals(VoiceFailureReason.Network, VoiceFailureReason.from(AppError.NetworkError()))
        assertEquals(VoiceFailureReason.Timeout, VoiceFailureReason.from(AppError.TimeoutError()))
        assertEquals(VoiceFailureReason.Server, VoiceFailureReason.from(AppError.ServerError(503)))
        // A 4xx from transcribe is "bad audio" — the repository maps it to a
        // ValidationError, and here that reads as nothing heard.
        assertEquals(VoiceFailureReason.NothingHeard, VoiceFailureReason.from(AppError.ValidationError("x")))
        assertEquals(VoiceFailureReason.Unknown, VoiceFailureReason.from(AppError.UnknownError()))
        assertEquals(VoiceFailureReason.Unknown, VoiceFailureReason.from(AppError.DatabaseError()))
    }

    @Test
    fun `join failures map by type`() {
        assertEquals(JoinFailureReason.InvalidCode, JoinFailureReason.from(AppError.NotFoundError()))
        assertEquals(JoinFailureReason.NotSignedIn, JoinFailureReason.from(AppError.AuthError()))
        assertEquals(JoinFailureReason.NotSignedIn, JoinFailureReason.from(AppError.UnauthorizedError()))
        assertEquals(JoinFailureReason.Network, JoinFailureReason.from(AppError.NetworkError()))
        assertEquals(JoinFailureReason.Network, JoinFailureReason.from(AppError.TimeoutError()))
        // The code was good and the pull failed: membership without data, the
        // worst outcome this flow has, so it gets its own label.
        assertEquals(JoinFailureReason.CatchUpFailed, JoinFailureReason.from(AppError.ServerError(500)))
        assertEquals(JoinFailureReason.CatchUpFailed, JoinFailureReason.from(AppError.DatabaseError()))
        assertEquals(JoinFailureReason.Unknown, JoinFailureReason.from(AppError.UnknownError()))
        assertEquals(JoinFailureReason.Unknown, JoinFailureReason.from(AppError.ValidationError("x")))
    }
}
