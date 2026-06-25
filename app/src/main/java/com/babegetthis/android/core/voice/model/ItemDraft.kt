package com.babegetthis.android.core.voice.model

// One parsed item the user spoke. The backend returns quantity + unit as
// separate fields (e.g. 2 + "bottles"); the repository flattens them into the
// single `quantity` string here (e.g. "2 bottles") so voice items look identical
// to manually-typed ones. All but `name` are nullable — the model can't always
// extract them.
//
// `category` is a category id from the backend (e.g. "cat-dairy-eggs"), matching
// the ids in our categories table. The repository validates it against known
// rows when persisting and stores it as the item's categoryId (unknown → null).
data class ItemDraft(
    val name: String,
    val quantity: String? = null,
    val note: String? = null,
    val category: String? = null,
)
