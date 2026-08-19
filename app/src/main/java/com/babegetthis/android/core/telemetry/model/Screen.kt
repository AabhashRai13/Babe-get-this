package com.babegetthis.android.core.telemetry.model

// The screens we report, as a closed set.
//
// This exists because raw nav routes cannot be sent. Routes.SHOPPING_ITEMS is
// "shopping_items/{listId}/{listName}" — NavDestination.route hands back the
// pattern rather than the filled path, so today it happens to be harmless, but
// that is an implementation detail of Navigation, not a guarantee. One day
// someone reads currentBackStackEntry.arguments instead and a user's list name
// ("Dad's chemo supplies") lands in GA4 forever.
//
// Mapping through an enum makes that mistake impossible rather than unlikely.
enum class Screen(val screenName: String) {
    ShoppingList("shopping_list"),
    ShoppingItems("shopping_items"),
    Login("login"),
    Register("register"),
    ForgotPassword("forgot_password"),
    Settings("settings"),

    // A route the enum does not know. Reported rather than dropped: a spike
    // here means someone added a screen and forgot this file, which is worth
    // seeing in the data.
    Unknown("unknown"),
    ;

    companion object {
        // Matches on the route PATTERN, so the "shopping_items/{listId}/{listName}"
        // prefix resolves without ever touching the arguments.
        fun fromRoute(route: String?): Screen = when {
            route == null -> Unknown
            route.startsWith("shopping_items") -> ShoppingItems
            route.startsWith("shopping_list") -> ShoppingList
            route.startsWith("forgot_password") -> ForgotPassword
            route.startsWith("register") -> Register
            route.startsWith("login") -> Login
            route.startsWith("settings") -> Settings
            else -> Unknown
        }
    }
}
