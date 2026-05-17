package com.babegetthis.android.core.voice.model

// One parsed item the user spoke, e.g. "1 crate Eggs".
// Kept tiny on purpose — quantity/unit parsing happens later if we need it.
data class ItemDraft(val name: String)
