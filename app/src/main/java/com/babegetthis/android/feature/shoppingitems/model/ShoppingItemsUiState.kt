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
    // Active items grouped Shop -> Category -> items, ready to render top to
    // bottom. Shops keep first-seen order; categories are alphabetical with the
    // uncategorized bucket last (see the ViewModel's grouping).
    val activeSections: List<ShopSection> = emptyList(),
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val totalCount: Int get() = items.size
    val completedCount: Int get() = completedItems.size
}

// One shop's worth of active items, split into category buckets.
// shopName == null means "no shop assigned".
data class ShopSection(
    val shopName: String?,
    val categories: List<CategorySection>,
)

// One category bucket inside a shop. label == null means "uncategorized"
// (item has no categoryId); the screen decides whether to draw a header for it.
data class CategorySection(
    val label: String?,
    val items: List<ShoppingItem>,
)
