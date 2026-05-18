package com.babegetthis.android.core.voice.model

// One parsed item the user spoke. The backend returns name + quantity as
// separate fields (e.g. {"name": "Eggs", "quantity": "1 crate"}), so we mirror
// that shape here. Quantity is nullable — the model can't always extract one.
data class ItemDraft(
    val name: String,
    val quantity: String? = null,
)
