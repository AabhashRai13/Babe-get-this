package com.babegetthis.android.core.telemetry.model

import com.babegetthis.android.core.error.AppError

// THE EVENT CATALOG. Everything this app can report is in this file.
//
// A sealed hierarchy rather than track(name: String, params: Map<...>) because
// the string version fails silently and late: a typo'd name is a dimension that
// is simply missing three weeks later, and a wrong-typed parameter is data you
// cannot use after you have already shipped. Here both are compile errors, the
// mapper's `when` is exhaustive so an unmapped event will not build, and the
// file itself answers "what does this app track?" without a wiki to drift from.
//
// It is also what makes leaving Firebase cheap. GA4's name-and-bundle shape
// exists in exactly one place (AnalyticsEventMapper); a PostHog implementation
// writes its own mapper against these same types and nothing else moves.
//
// Every event below exists to answer a stated question. An event that answers
// nothing does not belong here — see docs/technical-decisions/007-telemetry.md.
//
// PRIVACY: parameters are bounded enums, bucketed numbers, booleans, and ids
// from our own fixed taxonomy. There is deliberately no untyped map, so free
// text has no channel through which to arrive by accident.
sealed interface AnalyticsEvent {

    // -- Screen views ------------------------------------------------------
    // Firebase auto-collects screen_view for Activities only, and this is a
    // single-Activity Compose app, so without this there is no screen data.
    data class ScreenViewed(val screen: Screen) : AnalyticsEvent

    // -- Voice-to-list funnel ----------------------------------------------
    // Question: is voice actually used, and where does it lose people?
    //
    // The funnel mirrors VoiceCaptureViewModel, which has no review step —
    // transcription returning items persists them and navigates immediately.
    // So the sequence is: sheet opened, recording started, transcription
    // completed, items saved. There is no "user confirmed" stage to measure.

    data object VoiceSheetOpened : AnalyticsEvent

    data object VoiceRecordingStarted : AnalyticsEvent

    // The mic could not be opened — another app holds it, or MediaRecorder
    // refused. A device condition rather than a bug, but a high rate here
    // would mean the feature is unusable for a slice of users.
    data object VoiceRecordingFailed : AnalyticsEvent

    // Sheet dismissed mid-recording. Duration separates "opened it by
    // accident" from "spoke, then thought better of it".
    data class VoiceRecordingCancelled(val elapsedMillis: Long) : AnalyticsEvent

    // itemCount is also emitted raw, not only bucketed: items-per-utterance
    // is small, non-identifying, and the number we most want to average.
    data class VoiceTranscriptionCompleted(
        val latencyMillis: Long,
        val itemCount: Int,
    ) : AnalyticsEvent

    data class VoiceTranscriptionFailed(
        val reason: VoiceFailureReason,
        val latencyMillis: Long,
    ) : AnalyticsEvent

    data class VoiceItemsSaved(val itemCount: Int) : AnalyticsEvent

    // -- Sharing loop ------------------------------------------------------
    // Question: what fraction of users share, and does sharing change
    // retention? This is the growth mechanism, so it is the loop worth
    // measuring end to end rather than at the edges.

    // Owner side. Codes are static per list, so this fires on first share
    // only — re-opening the dialog is not a new share.
    data object ShareCodeCreated : AnalyticsEvent

    // The Android share sheet was launched. We cannot know which app the
    // user picked and do not try: that would need a chooser callback and
    // tells us little.
    data object ShareCodeShared : AnalyticsEvent

    // Joiner side. Attempted-versus-succeeded is the conversion rate.
    data object ShareJoinAttempted : AnalyticsEvent

    data object ShareJoinSucceeded : AnalyticsEvent

    data class ShareJoinFailed(val reason: JoinFailureReason) : AnalyticsEvent

    // The moment a joiner becomes a participant rather than a spectator.
    // Once per list; this is the real denominator for "did sharing work?".
    data object SharedListFirstEditByJoiner : AnalyticsEvent

    // -- Activation --------------------------------------------------------
    // Question: does an install become a user, and how fast?
    // The "first" events fire once per user, guarded by a persisted marker.

    data object AccountRegistered : AnalyticsEvent

    data object AccountLoggedIn : AnalyticsEvent

    data class FirstItemAdded(val inputMethod: InputMethod) : AnalyticsEvent

    data class FirstListCompleted(val itemCount: Int) : AnalyticsEvent

    // -- Habit and taxonomy ------------------------------------------------
    // Question: is this a weekly habit, and does the category taxonomy fit
    // how people actually shop?

    data class ItemAdded(
        val inputMethod: InputMethod,
        val categorySource: CategorySource,
    ) : AnalyticsEvent

    // No input method: ShoppingItem records no provenance, so how an item was
    // originally added is not knowable at check-off time. Inventing one would
    // be worse than the gap — the ratio would look authoritative and be wrong.
    data object ItemCheckedOff : AnalyticsEvent

    // Every item on a list is checked off — a completed shopping trip.
    // There is no explicit "clear list" action in the app, so this is
    // derived from the last unchecked item flipping.
    data class ListCompleted(val itemCount: Int) : AnalyticsEvent

    // The taxonomy pair. These two look like the least marketing-relevant
    // events in the catalog and are the most decision-relevant:
    // docs/technical-decisions/006-category-taxonomy.md commits to a fixed
    // category set, and the per-category correction rate is the only
    // evidence we will ever have about whether that set is right.
    data class CategoryAutoAssigned(val categoryId: String?) : AnalyticsEvent

    data class CategoryCorrected(
        val fromCategoryId: String?,
        val toCategoryId: String?,
    ) : AnalyticsEvent
}

// How an item got into the list. The whole voice bet is measured by the
// ratio between these two.
enum class InputMethod(val value: String) {
    Voice("voice"),
    Manual("manual"),
}

// Where an item's category came from. `User` covers both picking one in the
// add dialog and correcting one afterwards; `None` means uncategorised.
enum class CategorySource(val value: String) {
    Auto("auto"),
    User("user"),
    None("none"),
}

// Why voice failed. Bounded on purpose — the raw error message would be the
// easiest possible way to leak a server response into analytics.
enum class VoiceFailureReason(val value: String) {
    // Transcription came back fine but understood nothing. Distinct from a
    // technical failure: it means the model or the audio was the problem,
    // and it is the failure users actually experience most.
    NothingHeard("nothing_heard"),
    Network("network"),
    Timeout("timeout"),
    Server("server"),
    Unknown("unknown"),
    ;

    companion object {
        // AppError.message is user-facing copy for most types, but ServerError
        // and UnknownError can carry text that came off the wire. Mapping from
        // the TYPE rather than the message is what keeps a backend stack trace
        // out of GA4.
        fun from(error: AppError): VoiceFailureReason = when (error) {
            is AppError.NetworkError -> Network
            is AppError.TimeoutError -> Timeout
            is AppError.ServerError -> Server
            // A 4xx from the transcribe endpoint means bad audio — the voice
            // repository maps it that way already (see safeCall's
            // onClientError override).
            is AppError.ValidationError -> NothingHeard
            else -> Unknown
        }
    }
}

// Why joining a shared list failed. Mirrors what ShareRepository.join can
// actually produce — there is no expiry and no already-a-member state in
// this app, so those are not options here.
enum class JoinFailureReason(val value: String) {
    // The code matched no list. Includes typos, which is the common case.
    InvalidCode("invalid_code"),

    // The code was good but the initial full pull failed, so the user has
    // membership and no data. The worst failure mode we have, and the one
    // most worth watching.
    CatchUpFailed("catch_up_failed"),

    NotSignedIn("not_signed_in"),
    Network("network"),
    Unknown("unknown"),
    ;

    companion object {
        // Mirrors what ShareRepository.join actually throws: NotFoundError for
        // a code that matched nothing, AuthError when there is no session, and
        // whatever fullCatchUp failed with otherwise.
        fun from(error: AppError): JoinFailureReason = when (error) {
            is AppError.NotFoundError -> InvalidCode
            is AppError.AuthError -> NotSignedIn
            is AppError.UnauthorizedError -> NotSignedIn
            is AppError.NetworkError -> Network
            is AppError.TimeoutError -> Network
            // The code was good and the pull failed, so the user now has
            // membership and no data. The worst outcome this flow has, and
            // the reason it gets its own label rather than folding into
            // Unknown.
            is AppError.ServerError -> CatchUpFailed
            is AppError.DatabaseError -> CatchUpFailed
            else -> Unknown
        }
    }
}
