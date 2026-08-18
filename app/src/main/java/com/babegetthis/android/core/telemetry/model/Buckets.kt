package com.babegetthis.android.core.telemetry.model

// Unbounded numbers become bounded labels here, and only here.
//
// Two reasons, and the second is the one that bites. GA4 turns every distinct
// string parameter value into a dimension value; a raw item count would spread
// one question ("do big lists behave differently?") across hundreds of buckets
// of one user each, which is both unreadable and, at the tail, identifying —
// "the user with 4,318 items" is a person.
//
// Call sites pass real numbers. The boundaries live here so that changing one
// is a single edit rather than a search across features.

// Counts: items in a list, items from one utterance, pending sync operations.
fun bucketCount(count: Int): String = when {
    count <= 0 -> "0"
    count == 1 -> "1"
    count <= 5 -> "2-5"
    count <= 10 -> "6-10"
    count <= 20 -> "11-20"
    count <= 50 -> "21-50"
    else -> "50+"
}

// Durations: recording length, transcription latency.
//
// Boundaries chosen around what the voice flow actually feels like rather than
// round numbers: under 1s is a mis-tap, over 10s is where users start assuming
// the app has hung. The buckets have to be able to show us that.
fun bucketMillis(millis: Long): String = when {
    millis < 0L -> "unknown"
    millis < 1_000L -> "<1s"
    millis < 3_000L -> "1-3s"
    millis < 5_000L -> "3-5s"
    millis < 10_000L -> "5-10s"
    millis < 30_000L -> "10-30s"
    else -> "30s+"
}
