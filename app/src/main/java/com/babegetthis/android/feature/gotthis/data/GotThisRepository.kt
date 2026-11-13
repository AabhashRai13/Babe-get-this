package com.babegetthis.android.feature.gotthis.data

import androidx.room.withTransaction
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.sync.SyncKicker
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.dao.KeyStatRow
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.domain.HintSource
import com.babegetthis.android.feature.gotthis.domain.ItemGroup
import com.babegetthis.android.feature.gotthis.domain.ItemGroups
import com.babegetthis.android.feature.gotthis.domain.ItemJudge
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.feature.gotthis.domain.JudgedPurchase
import com.babegetthis.android.feature.gotthis.domain.KeyStat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.TimeZone

data class BoughtHint(val itemId: String, val daysAgo: Int, val source: HintSource, val canonicalKey: String)

data class GotThisCounts(val regulars: Int, val purchases: Int)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun hintsFor(listId: String): Flow<Map<String, BoughtHint>> = listDao.getListById(listId).flatMapLatest { list ->
        if (list == null) {
            flowOf(emptyMap())
        } else {
            val owner = list.accountId
            // Every flow is cut down to what the result reads and de-duplicated: a
            // note edit, a tick elsewhere or a pull that re-stores the same rows
            // would otherwise redo the whole judgement (see due()).
            combine(
                itemDao.getItemsByListId(listId)
                    .map { items -> items.filter { !it.isPickedUp && it.deletedAt == null }.map { it.id to it.name } }
                    .distinctUntilChanged(),
                purchaseDao.keyStats(owner).distinctUntilChanged(),
                stateDao.observeMutes(owner ?: GUEST_STATE_ID).distinctUntilChanged(),
            ) { unticked, rows, mutes ->
                val now = clock.now()
                val groups = ItemGroups.build(rows.map(::toKeyStat))
                val matched = unticked
                    .mapNotNull { (id, name) -> ItemGroups.groupFor(ItemKey.of(name), groups)?.let { id to it } }
                val keys = matched.flatMap { it.second.keys }.distinct()
                val byKey = keys.chunked(MAX_KEYS_PER_QUERY)
                    .flatMap { purchaseDao.liveForKeys(owner, it) }
                    .groupBy { it.itemKey }
                val muted = mutedKeys(mutes, now)
                val profiles = matched.map { it.second }.distinct().associate {
                    it.canonicalKey to ItemJudge.profile(it.purchasesIn(byKey), now, tz())
                }
                matched.mapNotNull { (itemId, group) ->
                    val profile = profiles.getValue(group.canonicalKey)
                    if (!profile.showsHint || group.keys.any { it in muted }) return@mapNotNull null
                    itemId to BoughtHint(itemId, profile.hintDaysAgo!!, profile.hintSource, group.canonicalKey)
                }.toMap()
            }
        }
    }

    fun usualsFor(ownerId: String?): Flow<List<UsualItem>> = combine(
        purchaseDao.keyStats(ownerId).distinctUntilChanged(),
        itemDao.uncheckedNames(ownerId).distinctUntilChanged(),
        stateDao.observeMutes(ownerId ?: GUEST_STATE_ID).distinctUntilChanged(),
        itemDao.lockedItemIds().distinctUntilChanged(),
    ) { rows, names, mutes, locked ->
        val now = clock.now()
        due(ownerId, rows, names, mutedKeys(mutes, now), locked.toSet(), now)
    }

    suspend fun dueItems(ownerId: String?, withinDays: Int): List<UsualItem> {
        val now = clock.now()
        val rows = purchaseDao.keyStats(ownerId).first()
        val names = itemDao.uncheckedNames(ownerId).first()
        val mutes = stateDao.mutes(ownerId ?: GUEST_STATE_ID)
        val locked = itemDao.lockedItemIds().first().toSet()
        return due(ownerId, rows, names, mutedKeys(mutes, now), locked, now + withinDays * DAY_MILLIS)
    }

    // How many of the owner's item groups are regulars, and how many live purchases there are.
    suspend fun summaryCounts(ownerId: String?): GotThisCounts {
        val now = clock.now()
        val groups = ItemGroups.build(purchaseDao.keyStats(ownerId).first().map(::toKeyStat))
        val live = purchaseDao.live(ownerId)
        val byKey = live.groupBy { it.itemKey }
        val regulars = groups.count { group ->
            val purchases = group.keys.flatMap { byKey[it].orEmpty() }
            ItemJudge.profile(purchases.map { JudgedPurchase(it.boughtAt, it.categoryId) }, now, tz()).isRegular
        }
        return GotThisCounts(regulars, live.size)
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
        muted: Set<String>,
        lockedItems: Set<String>,
        judgedAt: Long,
    ): List<UsualItem> {
        val groups = ItemGroups.build(rows.map(::toKeyStat))
        val onLists = unticked.mapNotNull { ItemGroups.groupFor(ItemKey.of(it), groups)?.canonicalKey }.toSet()
        val byKey = purchaseDao.live(ownerId).groupBy { it.itemKey }
        return groups.mapNotNull { group ->
            val purchases = group.keys.flatMap { byKey[it].orEmpty() }
            val profile = ItemJudge.profile(
                purchases.map { JudgedPurchase(it.boughtAt, it.categoryId) },
                judgedAt,
                tz(),
            )
            // A regular last bought on a PIN-locked list is not named anywhere (R24).
            if (!profile.isDue ||
                group.canonicalKey in onLists ||
                group.keys.any { it in muted } ||
                purchases.maxBy { it.boughtAt }.sourceItemId in lockedItems
            ) {
                return@mapNotNull null
            }
            // Name and category come from the latest purchase under the canonical
            // key, so a recent typo does not rename the group.
            val named = purchases.filter { it.itemKey == group.canonicalKey }.ifEmpty { purchases }
            val latest = named.maxBy { it.boughtAt }
            UsualItem(group.canonicalKey, latest.name, latest.categoryId, profile.daysOverdue)
        }.sortedWith(compareByDescending<UsualItem> { it.daysOverdue }.thenBy { it.name })
    }

    private fun mutedKeys(mutes: List<ItemMuteEntity>, now: Long): Set<String> =
        mutes.filter { (it.mutedUntil ?: 0L) > now }.map { it.itemKey }.toSet()

    private fun ItemGroup.purchasesIn(byKey: Map<String, List<PurchaseEntity>>): List<JudgedPurchase> =
        keys.flatMap { byKey[it].orEmpty() }.map { JudgedPurchase(it.boughtAt, it.categoryId) }

    private fun toKeyStat(row: KeyStatRow) = KeyStat(row.itemKey, row.purchases, row.lastBoughtAt)

    private companion object {
        const val GUEST_STATE_ID = ""
        const val MAX_KEYS_PER_QUERY = 900
        const val DAY_MILLIS = 86_400_000L
    }
}
