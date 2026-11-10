package com.babegetthis.android.feature.gotthis.data

import androidx.room.withTransaction
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.sync.SyncKicker
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.dao.KeyStatRow
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.domain.HintSource
import com.babegetthis.android.feature.gotthis.domain.ItemGroup
import com.babegetthis.android.feature.gotthis.domain.ItemGroups
import com.babegetthis.android.feature.gotthis.domain.ItemJudge
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.feature.gotthis.domain.JudgedPurchase
import com.babegetthis.android.feature.gotthis.domain.KeyStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.TimeZone

data class BoughtHint(val itemId: String, val daysAgo: Int, val source: HintSource, val canonicalKey: String)

data class UsualItem(val canonicalKey: String, val name: String, val categoryId: String?, val daysOverdue: Int)

// The read side of purchase history: groups what is in Room, asks ItemJudge and
// applies the person's mutes. The owner of a list is its accountId (null = guest).
class GotThisRepository(
    private val db: AppDatabase,
    private val prefs: GotThisPrefs,
    private val syncKicker: SyncKicker,
    private val clock: ReminderClock,
    private val tz: () -> TimeZone,
) {
    private val itemDao = db.shoppingItemDao()
    private val listDao = db.shoppingListDao()
    private val purchaseDao = db.purchaseDao()
    private val stateDao = db.gotThisStateDao()

    fun hintsFor(listId: String): Flow<Map<String, BoughtHint>> = flow {
        val owner = ownerOf(listId)
        emitAll(
            combine(itemDao.getItemsByListId(listId), purchaseDao.keyStats(owner)) { items, rows ->
                val now = clock.now()
                val groups = ItemGroups.build(rows.map(::toKeyStat))
                val matched = items.filter { !it.isPickedUp && it.deletedAt == null }
                    .mapNotNull { item -> ItemGroups.groupFor(ItemKey.of(item.name), groups)?.let { item.id to it } }
                val keys = matched.flatMap { it.second.keys }.distinct()
                val byKey = keys.chunked(MAX_KEYS_PER_QUERY)
                    .flatMap { purchaseDao.liveForKeys(owner, it) }
                    .groupBy { it.itemKey }
                val muted = mutedKeys(owner, now)
                val profiles = matched.map { it.second }.distinct().associate {
                    it.canonicalKey to ItemJudge.profile(it.purchasesIn(byKey), now, tz())
                }
                matched.mapNotNull { (itemId, group) ->
                    val profile = profiles.getValue(group.canonicalKey)
                    if (!profile.showsHint || group.keys.any { it in muted }) return@mapNotNull null
                    itemId to BoughtHint(itemId, profile.hintDaysAgo!!, profile.hintSource, group.canonicalKey)
                }.toMap()
            },
        )
    }

    fun usualsFor(ownerId: String?): Flow<List<UsualItem>> =
        combine(purchaseDao.keyStats(ownerId), itemDao.uncheckedNames(ownerId)) { rows, names ->
            val now = clock.now()
            due(ownerId, rows, names, now, now)
        }

    suspend fun dueItems(ownerId: String?, withinDays: Int): List<UsualItem> {
        val now = clock.now()
        val rows = purchaseDao.keyStats(ownerId).first()
        val names = itemDao.uncheckedNames(ownerId).first()
        return due(ownerId, rows, names, now, now + withinDays * DAY_MILLIS)
    }

    suspend fun purchaseDays(ownerId: String?): List<Long> = purchaseDao.live(ownerId).map { it.boughtAt }

    suspend fun clearHistory(ownerId: String?) {
        val now = clock.now()
        if (ownerId == null) {
            db.withTransaction {
                purchaseDao.deleteAllFor(null)
                stateDao.clearState(GUEST_STATE_ID)
            }
            return
        }
        // First, so a pull running at the same time already discards the old rows.
        prefs.setPendingClear(ownerId, now)
        db.withTransaction {
            purchaseDao.deleteUpTo(ownerId, now)
            stateDao.clearState(ownerId)
        }
        syncKicker.pushSoon()
    }

    suspend fun ownerOf(listId: String): String? = listDao.getListRaw(listId)?.accountId

    private suspend fun due(
        ownerId: String?,
        rows: List<KeyStatRow>,
        unticked: List<String>,
        mutesAt: Long,
        judgedAt: Long,
    ): List<UsualItem> {
        val groups = ItemGroups.build(rows.map(::toKeyStat))
        val onLists = unticked.mapNotNull { ItemGroups.groupFor(ItemKey.of(it), groups)?.canonicalKey }.toSet()
        val muted = mutedKeys(ownerId, mutesAt)
        val byKey = purchaseDao.live(ownerId).groupBy { it.itemKey }
        return groups.mapNotNull { group ->
            val purchases = group.keys.flatMap { byKey[it].orEmpty() }
            val profile = ItemJudge.profile(
                purchases.map { JudgedPurchase(it.boughtAt, it.categoryId) },
                judgedAt,
                tz(),
            )
            if (!profile.isDue ||
                group.canonicalKey in onLists ||
                group.keys.any { it in muted }
            ) {
                return@mapNotNull null
            }
            // Name and category come from the latest purchase of the group.
            val latest = purchases.maxBy { it.boughtAt }
            UsualItem(group.canonicalKey, latest.name, latest.categoryId, profile.daysOverdue)
        }.sortedWith(compareByDescending<UsualItem> { it.daysOverdue }.thenBy { it.name })
    }

    private suspend fun mutedKeys(ownerId: String?, now: Long): Set<String> =
        stateDao.mutes(ownerId ?: GUEST_STATE_ID).filter { (it.mutedUntil ?: 0L) > now }.map { it.itemKey }.toSet()

    private fun ItemGroup.purchasesIn(byKey: Map<String, List<PurchaseEntity>>): List<JudgedPurchase> =
        keys.flatMap { byKey[it].orEmpty() }.map { JudgedPurchase(it.boughtAt, it.categoryId) }

    private fun toKeyStat(row: KeyStatRow) = KeyStat(row.itemKey, row.purchases, row.lastBoughtAt)

    private companion object {
        const val GUEST_STATE_ID = ""
        const val MAX_KEYS_PER_QUERY = 900
        const val DAY_MILLIS = 86_400_000L
    }
}
