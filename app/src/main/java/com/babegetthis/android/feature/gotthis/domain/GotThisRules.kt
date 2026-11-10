package com.babegetthis.android.feature.gotthis.domain

// Every threshold of the Got This judging rules lives here.
object GotThisRules {

    const val UNDO_WINDOW_MILLIS = 2L * 60 * 60 * 1000
    const val MAX_NAME_CHARS = 500
    const val SHOP_JOIN_DAYS = 2
    const val WINDOW_SHOPS = 7
    const val MIN_SHOPS = 3
    const val MIN_SPAN_DAYS = 14
    const val STEADY_SHARE = 2.0 / 3.0
    const val STEADY_LOW_FACTOR = 0.5
    const val STEADY_HIGH_FACTOR = 2.0
    const val LAPSE_FACTOR = 2.5
    const val DUE_FACTOR = 0.9
    const val MUTE_DAYS = 60
    const val MUTE_AFTER_KEPT = 2
    const val SHOP_DAY_LOOKBACK_DAYS = 56
    const val SHOP_DAY_MIN_DAYS = 4
    const val SHOP_DAY_MIN_SHARE = 0.4
    const val MAX_CHIPS = 6
    const val MAX_NOTIFICATION_NAMES = 3
    const val NOT_NOW_MILLIS = 24L * 60 * 60 * 1000
    const val IMPRESSION_PRUNE_DAYS = 30
    const val FOLLOW_UP_DAYS = 7
    const val UNKNOWN_WINDOW_DAYS = 7
    const val HOLDOUT_PERCENT = 10
    const val PUSH_BATCH_SIZE = 500
    const val FUTURE_REJECT_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val SQL_IN_CHUNK = 900

    // Hint window in days -> default category ids. cat-other and custom
    // categories are in no group and fall to UNKNOWN_WINDOW_DAYS.
    val CATEGORY_GROUPS: Map<Int, List<String>> = mapOf(
        3 to ids("fruits-vegetables", "meat-seafood", "bakery-bread"),
        5 to ids("dairy-eggs", "ready-meals"),
        14 to ids("snacks", "sweets-chocolate", "beverages", "breakfast-cereal", "alcohol"),
        30 to ids(
            "frozen-foods", "pantry-dry-goods", "canned-jarred", "baking-supplies",
            "spices-seasonings", "sauces-condiments", "oils-vinegars", "spreads", "coffee-tea",
            "household-cleaning", "paper-disposables", "toiletries-personal-care",
            "health-pharmacy", "baby-kids", "pet-supplies", "tobacco-vaping",
            "office-stationery", "automotive",
        ),
        90 to ids(
            "kitchen-dining", "appliances", "furniture-storage", "home-decor", "bedding-bath",
            "lighting-electrical", "hardware-diy", "garden-outdoor", "clothing",
            "underwear-sleepwear", "shoes", "accessories-jewelry", "bags-luggage",
            "electronics", "toys-games", "books-media", "sports-outdoors", "arts-crafts",
            "party-gifts",
        ),
    )

    private val windowById: Map<String, Int> =
        CATEGORY_GROUPS.flatMap { (window, group) -> group.map { it to window } }.toMap()

    fun categoryWindowDays(categoryId: String?): Int = windowById[categoryId] ?: UNKNOWN_WINDOW_DAYS

    private fun ids(vararg names: String): List<String> = names.map { "cat-$it" }
}
