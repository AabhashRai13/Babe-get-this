package com.babegetthis.android.feature.shoppingitems.model

// All the derived UI state for the shopping items screen.
// The ViewModel computes this from the raw items list once per upstream
// emission so the screen just reads — no filter / groupBy on every
// recomposition (which would re-run every animation frame while the
// ProgressCard's color pulse is in flight).
data class ShoppingItemsUiState(
    val items: List<ShoppingItem> = emptyList(),
    val activeItems: List<ShoppingItem> = emptyList(),
    val completedItems: List<ShoppingItem> = emptyList(),
    // Active items grouped by shop name. Key is the shop string or "" for
    // items with no shop assigned.
    val activeByShop: Map<String, List<ShoppingItem>> = emptyMap(),
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val totalCount: Int get() = items.size
    val completedCount: Int get() = completedItems.size
}
