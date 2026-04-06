package com.babegetthis.android.core.data.local

import com.babegetthis.android.core.data.local.model.CategoryEntity
import java.util.UUID

// Pre-populated categories that ship with the app.
// Using fixed UUIDs so they're consistent across installs — important for future sync.

val DEFAULT_CATEGORIES = listOf(
    CategoryEntity(id = "cat-fruits-vegetables", name = "Fruits & Vegetables", isDefault = true),
    CategoryEntity(id = "cat-dairy-eggs", name = "Dairy & Eggs", isDefault = true),
    CategoryEntity(id = "cat-meat-seafood", name = "Meat & Seafood", isDefault = true),
    CategoryEntity(id = "cat-bakery-bread", name = "Bakery & Bread", isDefault = true),
    CategoryEntity(id = "cat-frozen-foods", name = "Frozen Foods", isDefault = true),
    CategoryEntity(id = "cat-pantry-dry-goods", name = "Pantry & Dry Goods", isDefault = true),
    CategoryEntity(id = "cat-beverages", name = "Beverages", isDefault = true),
    CategoryEntity(id = "cat-snacks", name = "Snacks", isDefault = true),
    CategoryEntity(id = "cat-toiletries-personal-care", name = "Toiletries & Personal Care", isDefault = true),
    CategoryEntity(id = "cat-household-cleaning", name = "Household & Cleaning", isDefault = true),
    CategoryEntity(id = "cat-baby-kids", name = "Baby & Kids", isDefault = true),
    CategoryEntity(id = "cat-pet-supplies", name = "Pet Supplies", isDefault = true),
)
