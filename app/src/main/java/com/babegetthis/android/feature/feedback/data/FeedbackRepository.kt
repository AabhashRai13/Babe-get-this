package com.babegetthis.android.feature.feedback.data

import android.util.Log
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

// The row we insert into the Supabase "feedback" table. @Serializable is
// kotlinx.serialization (like json_serializable in Flutter, but compile-time
// with no codegen files). Column names are snake_case in Postgres, hence
// @SerialName on the one multi-word field.
//
// user_id and created_at are NOT sent from the client — the table fills them
// with `auth.uid()` and `now()` defaults, so a user can't submit feedback as
// someone else.
@Serializable
private data class FeedbackRow(
    val liked: String?,
    val disliked: String?,
    @SerialName("would_use") val wouldUse: Boolean,
    val improvements: String?,
)

@Singleton
class FeedbackRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    // Blank answers are stored as NULL instead of empty strings so a future
    // "how many people answered this?" query is a simple COUNT(column).
    suspend fun submitFeedback(
        liked: String,
        disliked: String,
        wouldUse: Boolean,
        improvements: String,
    ): Result<Unit> {
        val row = FeedbackRow(
            liked = liked.trim().ifBlank { null },
            disliked = disliked.trim().ifBlank { null },
            wouldUse = wouldUse,
            improvements = improvements.trim().ifBlank { null },
        )
        return try {
            supabaseClient.postgrest.from("feedback").insert(row)
            Result.Success(Unit)
        } catch (e: RestException) {
            // Supabase REST errors are verbose JSON blobs — never show those to
            // the user. Full detail goes to Logcat for debugging; the toast gets
            // one short, human sentence.
            Log.e("FeedbackRepository", "Feedback insert rejected by Supabase", e)
            Result.Error(AppError.UnknownError("Couldn't send feedback. Please try again."))
        } catch (e: Exception) {
            // Connectivity and everything else — reuse safeCall's mapping so
            // offline still says "no internet" like the rest of the app.
            Log.e("FeedbackRepository", "Feedback insert failed", e)
            safeCall<Unit> { throw e }
        }
    }
}
