package com.babegetthis.android.core.voice.data.remote.dto

import kotlinx.serialization.Serializable

// Mirrors the JSON returned by POST /transcribe exactly — nothing more.
// The repository maps these into the app's ItemDraft model; the rest of the
// app never sees these DTOs (repositories return domain data classes, never
// raw API shapes). Json is configured with ignoreUnknownKeys, so extra fields
// the backend adds later won't break parsing.
@Serializable
data class TranscribeResponseDto(
    val transcript: String? = null,
    val items: List<TranscribeItemDto> = emptyList(),
)

// One parsed item. quantity is a number and unit is separate (e.g. 2 + "bottles");
// the repository flattens them into ItemDraft's single quantity string. category
// is a slug like "dairy_eggs" — carried through but not persisted yet (see
// RemoteVoiceRepository / the auto-categorization TODO). All optional because the
// model can't always extract them.
@Serializable
data class TranscribeItemDto(
    val name: String,
    val quantity: Int? = null,
    val unit: String? = null,
    val category: String? = null,
    val location: String? = null,   // backend now sends the store, e.g. "Dan Murphy's"
    val note: String? = null,
)
