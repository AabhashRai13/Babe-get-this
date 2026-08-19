package com.babegetthis.android.core.data.local

import com.babegetthis.android.core.data.local.model.CategoryEntity

// Pre-populated categories that ship with the app.
// Using fixed IDs so they're consistent across installs — important for sync.
//
// The set is designed to be non-overlapping: every shopping item has exactly one
// obvious home. The boundary rules that decide the tricky cases (why tomato paste is
// a Sauce and not Canned, why cleaning vinegar is Oils/Vinegars and not Cleaning) live
// in docs/technical-decisions/005-category-taxonomy.md — the voice/LLM categorizer uses
// those rules. The typed keyword map cannot resolve intent-based ties (see the doc) and
// should only map the unambiguous items.
//
// The original 12 grocery IDs are preserved. Two were renamed in place:
//   cat-pantry-dry-goods : "Pantry & Dry Goods" -> "Rice, Grains & Pasta"
//   cat-toiletries-personal-care : "Toiletries & Personal Care" -> "Personal Care & Beauty"

val DEFAULT_CATEGORIES = listOf(
    // Food
    CategoryEntity(id = "cat-fruits-vegetables", name = "Fruits & Vegetables", isDefault = true),
    CategoryEntity(id = "cat-dairy-eggs", name = "Dairy & Eggs", isDefault = true),
    CategoryEntity(id = "cat-meat-seafood", name = "Meat & Seafood", isDefault = true),
    CategoryEntity(id = "cat-bakery-bread", name = "Bakery & Bread", isDefault = true),
    CategoryEntity(id = "cat-frozen-foods", name = "Frozen Foods", isDefault = true),
    CategoryEntity(id = "cat-pantry-dry-goods", name = "Rice, Grains & Pasta", isDefault = true),
    CategoryEntity(id = "cat-canned-jarred", name = "Canned & Jarred Goods", isDefault = true),
    CategoryEntity(id = "cat-baking-supplies", name = "Baking & Dry Ingredients", isDefault = true),
    CategoryEntity(id = "cat-spices-seasonings", name = "Spices & Seasonings", isDefault = true),
    CategoryEntity(id = "cat-sauces-condiments", name = "Sauces, Condiments & Pastes", isDefault = true),
    CategoryEntity(id = "cat-oils-vinegars", name = "Oils, Vinegars & Cooking Fats", isDefault = true),
    CategoryEntity(id = "cat-breakfast-cereal", name = "Breakfast & Cereal", isDefault = true),
    CategoryEntity(id = "cat-spreads", name = "Spreads, Honey & Jam", isDefault = true),
    CategoryEntity(id = "cat-snacks", name = "Snacks", isDefault = true),
    CategoryEntity(id = "cat-sweets-chocolate", name = "Sweets & Chocolate", isDefault = true),
    CategoryEntity(id = "cat-ready-meals", name = "Ready Meals & Prepared Food", isDefault = true),
    CategoryEntity(id = "cat-beverages", name = "Beverages", isDefault = true),
    CategoryEntity(id = "cat-coffee-tea", name = "Coffee & Tea", isDefault = true),
    CategoryEntity(id = "cat-alcohol", name = "Beer, Wine & Spirits", isDefault = true),

    // Home & household
    CategoryEntity(id = "cat-household-cleaning", name = "Household & Cleaning", isDefault = true),
    CategoryEntity(id = "cat-paper-disposables", name = "Paper & Disposables", isDefault = true),
    CategoryEntity(id = "cat-kitchen-dining", name = "Kitchen & Dining", isDefault = true),
    CategoryEntity(id = "cat-appliances", name = "Appliances", isDefault = true),
    CategoryEntity(id = "cat-furniture-storage", name = "Furniture & Storage", isDefault = true),
    CategoryEntity(id = "cat-home-decor", name = "Home Decor", isDefault = true),
    CategoryEntity(id = "cat-bedding-bath", name = "Bedding & Bath", isDefault = true),
    CategoryEntity(id = "cat-lighting-electrical", name = "Lighting & Electrical", isDefault = true),
    CategoryEntity(id = "cat-hardware-diy", name = "Hardware & Tools", isDefault = true),
    CategoryEntity(id = "cat-garden-outdoor", name = "Garden & Outdoor", isDefault = true),
    CategoryEntity(id = "cat-automotive", name = "Automotive", isDefault = true),

    // Personal & health
    CategoryEntity(id = "cat-toiletries-personal-care", name = "Personal Care & Beauty", isDefault = true),
    CategoryEntity(id = "cat-health-pharmacy", name = "Health & Pharmacy", isDefault = true),

    // Wearables
    CategoryEntity(id = "cat-clothing", name = "Clothing", isDefault = true),
    CategoryEntity(id = "cat-underwear-sleepwear", name = "Underwear, Socks & Sleepwear", isDefault = true),
    CategoryEntity(id = "cat-shoes", name = "Shoes & Footwear", isDefault = true),
    CategoryEntity(id = "cat-accessories-jewelry", name = "Accessories & Jewelry", isDefault = true),
    CategoryEntity(id = "cat-bags-luggage", name = "Bags & Luggage", isDefault = true),

    // People & pets
    CategoryEntity(id = "cat-baby-kids", name = "Baby & Kids", isDefault = true),
    CategoryEntity(id = "cat-pet-supplies", name = "Pet Supplies", isDefault = true),

    // Leisure & misc
    CategoryEntity(id = "cat-electronics", name = "Electronics", isDefault = true),
    CategoryEntity(id = "cat-toys-games", name = "Toys & Games", isDefault = true),
    CategoryEntity(id = "cat-books-media", name = "Books & Media", isDefault = true),
    CategoryEntity(id = "cat-sports-outdoors", name = "Sports & Outdoors", isDefault = true),
    CategoryEntity(id = "cat-office-stationery", name = "Office & Stationery", isDefault = true),
    CategoryEntity(id = "cat-arts-crafts", name = "Arts, Crafts & Hobbies", isDefault = true),
    CategoryEntity(id = "cat-party-gifts", name = "Party, Gifts & Holiday", isDefault = true),
    CategoryEntity(id = "cat-tobacco-vaping", name = "Tobacco & Vaping", isDefault = true),
    CategoryEntity(id = "cat-other", name = "Other", isDefault = true),
)
