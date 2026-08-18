package com.babegetthis.android.core.telemetry.data

import com.babegetthis.android.core.data.local.DEFAULT_CATEGORIES
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.bucketCount
import com.babegetthis.android.core.telemetry.model.bucketMillis

// A vendor-shaped event: a name plus parameters, split by type because GA4
// treats them differently — strings become dimensions you can segment by,
// numbers become metrics you can average. Sending item count as both is
// deliberate: the bucket answers "do big lists behave differently?", the raw
// number answers "how many items per utterance?", and neither substitutes.
//
// Deliberately NOT an android.os.Bundle. Keeping this plain Kotlin means the
// mapper — the only piece here with real logic — unit-tests on the JVM with no
// Robolectric, and means a future PostHog implementation reuses it unchanged.
data class MappedEvent(
    val name: String,
    val text: Map<String, String> = emptyMap(),
    val numbers: Map<String, Long> = emptyMap(),
)

// Translates the sealed catalog into GA4's shape. The single place in the
// codebase that knows what an event is called on the wire.
object AnalyticsEventMapper {

    // GA4's documented limits. Exceeding any of them is SILENT data loss at
    // the backend — the event uploads, the offending part is dropped, and you
    // find out weeks later when a dimension is empty. Enforced here rather
    // than trusted.
    private const val MAX_NAME_LENGTH = 40
    private const val MAX_PARAM_NAME_LENGTH = 40
    private const val MAX_TEXT_VALUE_LENGTH = 100
    private const val MAX_PARAMS = 25

    // Reserved by Google. An event using one of these prefixes is rejected.
    private val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")

    private val KNOWN_CATEGORY_IDS: Set<String> = DEFAULT_CATEGORIES.map { it.id }.toSet()

    // Categories the user created themselves have generated ids and
    // user-authored names. Neither may be transmitted — the id would be a
    // high-cardinality value that means nothing to anyone reading a report,
    // and the name is user content outright. Collapsed to one label, which
    // still answers the useful question: how often does our taxonomy fail to
    // have a home for something?
    private const val CUSTOM_CATEGORY = "custom"
    private const val NO_CATEGORY = "none"

    fun map(event: AnalyticsEvent): MappedEvent = when (event) {
        is AnalyticsEvent.ScreenViewed -> ev(
            // GA4's own screen_view event, so it feeds the built-in screen
            // reports rather than sitting in a custom silo. The parameter
            // names are Google's; they are not ours to choose.
            name = "screen_view",
            text = mapOf("screen_name" to event.screen.screenName),
        )

        // -- Voice -------------------------------------------------------
        AnalyticsEvent.VoiceSheetOpened -> ev("voice_sheet_opened")

        AnalyticsEvent.VoiceRecordingStarted -> ev("voice_recording_started")

        AnalyticsEvent.VoiceRecordingFailed -> ev("voice_recording_failed")

        is AnalyticsEvent.VoiceRecordingCancelled -> ev(
            name = "voice_recording_cancelled",
            text = mapOf("duration_bucket" to bucketMillis(event.elapsedMillis)),
        )

        is AnalyticsEvent.VoiceTranscriptionCompleted -> ev(
            name = "voice_transcription_completed",
            text = mapOf(
                "latency_bucket" to bucketMillis(event.latencyMillis),
                "item_count_bucket" to bucketCount(event.itemCount),
            ),
            numbers = mapOf("item_count" to event.itemCount.toLong()),
        )

        is AnalyticsEvent.VoiceTranscriptionFailed -> ev(
            name = "voice_transcription_failed",
            text = mapOf(
                "reason" to event.reason.value,
                "latency_bucket" to bucketMillis(event.latencyMillis),
            ),
        )

        is AnalyticsEvent.VoiceItemsSaved -> ev(
            name = "voice_items_saved",
            text = mapOf("item_count_bucket" to bucketCount(event.itemCount)),
            numbers = mapOf("item_count" to event.itemCount.toLong()),
        )

        // -- Sharing -----------------------------------------------------
        AnalyticsEvent.ShareCodeCreated -> ev("share_code_created")

        AnalyticsEvent.ShareCodeShared -> ev("share_code_shared")

        AnalyticsEvent.ShareJoinAttempted -> ev("share_join_attempted")

        AnalyticsEvent.ShareJoinSucceeded -> ev("share_join_succeeded")

        is AnalyticsEvent.ShareJoinFailed -> ev(
            name = "share_join_failed",
            text = mapOf("reason" to event.reason.value),
        )

        AnalyticsEvent.SharedListFirstEditByJoiner -> ev("shared_list_first_edit")

        // -- Activation --------------------------------------------------
        AnalyticsEvent.AccountRegistered -> ev("account_registered")

        AnalyticsEvent.AccountLoggedIn -> ev("account_logged_in")

        is AnalyticsEvent.FirstItemAdded -> ev(
            name = "first_item_added",
            text = mapOf("input_method" to event.inputMethod.value),
        )

        is AnalyticsEvent.FirstListCompleted -> ev(
            name = "first_list_completed",
            text = mapOf("item_count_bucket" to bucketCount(event.itemCount)),
        )

        // -- Habit and taxonomy ------------------------------------------
        is AnalyticsEvent.ItemAdded -> ev(
            name = "item_added",
            text = mapOf(
                "input_method" to event.inputMethod.value,
                "category_source" to event.categorySource.value,
            ),
        )

        AnalyticsEvent.ItemCheckedOff -> ev("item_checked_off")

        is AnalyticsEvent.ListCompleted -> ev(
            name = "list_completed",
            text = mapOf("item_count_bucket" to bucketCount(event.itemCount)),
            numbers = mapOf("item_count" to event.itemCount.toLong()),
        )

        is AnalyticsEvent.CategoryAutoAssigned -> ev(
            name = "category_auto_assigned",
            text = mapOf("category" to categoryLabel(event.categoryId)),
        )

        is AnalyticsEvent.CategoryCorrected -> ev(
            name = "category_corrected",
            text = mapOf(
                "from_category" to categoryLabel(event.fromCategoryId),
                "to_category" to categoryLabel(event.toCategoryId),
            ),
        )
    }

    // Only ids from our own shipped taxonomy pass through as themselves.
    // Anything else is a user-created category, and its id is neither safe
    // nor useful to send.
    private fun categoryLabel(categoryId: String?): String = when {
        categoryId == null -> NO_CATEGORY
        categoryId in KNOWN_CATEGORY_IDS -> categoryId
        else -> CUSTOM_CATEGORY
    }

    // Every event is built through here, so the limits apply to all of them
    // and cannot be forgotten at one call site.
    //
    // Truncation over rejection on purpose: a slightly-clipped value is a
    // recoverable annoyance, a dropped event is a hole in a funnel. Names are
    // a different matter — they are compile-time constants in this file, so a
    // bad one is a bug rather than a runtime condition, and
    // AnalyticsEventMapperTest walks the whole catalog to catch it before it
    // ships.
    private fun ev(
        name: String,
        text: Map<String, String> = emptyMap(),
        numbers: Map<String, Long> = emptyMap(),
    ): MappedEvent = MappedEvent(
        name = name.take(MAX_NAME_LENGTH),
        text = text.entries
            .take(MAX_PARAMS)
            .associate { it.key.take(MAX_PARAM_NAME_LENGTH) to it.value.take(MAX_TEXT_VALUE_LENGTH) },
        numbers = numbers.entries
            .take(MAX_PARAMS - text.size.coerceAtMost(MAX_PARAMS))
            .associate { it.key.take(MAX_PARAM_NAME_LENGTH) to it.value },
    )

    // Exposed for AnalyticsEventMapperTest, which asserts every event in the
    // catalog produces a name GA4 will accept. Not used at runtime: by the
    // time a name is wrong, checking it changes nothing.
    fun isValidEventName(name: String): Boolean =
        name.length <= MAX_NAME_LENGTH &&
            name.isNotEmpty() &&
            name.first().isLetter() &&
            name.all { it.isLetterOrDigit() || it == '_' } &&
            RESERVED_PREFIXES.none { name.startsWith(it) }
}
