package com.babegetthis.android.feature.shoppinglist.model

import com.babegetthis.android.core.util.TimePeriod

// All the derived UI state for the shopping list screen.
// The ViewModel computes this from the raw list data + selected tab,
// so the screen just reads and renders.
//
// Both tabs' grouped data are exposed (groupedActive + groupedCompleted)
// rather than a single "currently selected tab" group. Reason: the tab
// content lives inside AnimatedContent, so the outgoing pane needs to
// keep rendering its OWN tab's data while sliding away — if we exposed
// only one grouped map, both panes would re-read the new tab's data the
// moment selectedTab flipped, and the transition would visually skip.
data class ShoppingListUiState(
    val activeLists: List<ShoppingList> = emptyList(),
    val completedLists: List<ShoppingList> = emptyList(),
    val selectedTab: Int = 0,
    val groupedActive: Map<TimePeriod, List<ShoppingList>> = emptyMap(),
    val groupedCompleted: Map<TimePeriod, List<ShoppingList>> = emptyMap(),
    val activeItemsToGet: Int = 0,
) {
    val hasNoLists: Boolean
        get() = activeLists.isEmpty() && completedLists.isEmpty()

    val isActiveTab: Boolean
        get() = selectedTab == 0
}