package com.babegetthis.android.feature.shoppinglist.model

import com.babegetthis.android.core.util.TimePeriod

// All the derived UI state for the shopping list screen.
// The ViewModel computes this from the raw list data + selected tab,
// so the screen just reads and renders.
data class ShoppingListUiState(
    val activeLists: List<ShoppingList> = emptyList(),
    val completedLists: List<ShoppingList> = emptyList(),
    val selectedTab: Int = 0,
    val groupedLists: Map<TimePeriod, List<ShoppingList>> = emptyMap(),
    val activeItemsToGet: Int = 0,
) {
    val hasNoLists: Boolean
        get() = activeLists.isEmpty() && completedLists.isEmpty()

    val displayedListsAreEmpty: Boolean
        get() = (selectedTab == 0 && activeLists.isEmpty()) ||
                (selectedTab == 1 && completedLists.isEmpty())

    val isActiveTab: Boolean
        get() = selectedTab == 0
}