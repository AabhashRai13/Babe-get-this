package com.babegetthis.android.feature.gotthis.domain

data class KeyStat(val key: String, val purchases: Int, val lastBoughtAt: Long)

data class ItemGroup(val canonicalKey: String, val keys: List<String>)

// Folds keys that differ by one typo into the group of the most bought key.
object ItemGroups {

    // Most bought first, then most recent, then alphabetical, so the canonical
    // key of each group is the first one to arrive.
    fun build(stats: List<KeyStat>): List<ItemGroup> {
        val ordered = stats.sortedWith(
            compareByDescending<KeyStat> { it.purchases }
                .thenByDescending { it.lastBoughtAt }
                .thenBy { it.key },
        )
        val groups = mutableListOf<ItemGroup>()
        for (stat in ordered) {
            val index = groups.indexOfFirst { ItemKey.matches(it.canonicalKey, stat.key) }
            if (index < 0) {
                groups.add(ItemGroup(stat.key, listOf(stat.key)))
            } else {
                groups[index] = groups[index].copy(keys = groups[index].keys + stat.key)
            }
        }
        return groups
    }

    fun groupFor(key: String, groups: List<ItemGroup>): ItemGroup? =
        groups.firstOrNull { ItemKey.matches(it.canonicalKey, key) }
}
