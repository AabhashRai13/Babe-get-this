package com.babegetthis.android.feature.gotthis.data

import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.feature.gotthis.data.local.model.HintImpressionEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ReminderFollowUpEntity
import com.babegetthis.android.feature.gotthis.domain.GotThisIds
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.InMemoryDatabaseRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GotThisBackfillTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_793_527_200_000L
    private val prefs = FakeGotThisPrefs().apply { keyVersion = ItemKey.VERSION }
    private val backfill by lazy { GotThisBackfill(dbRule.db, prefs, ReminderClock { now }) }

    private suspend fun list(id: String, accountId: String? = null, deletedAt: Long? = null) =
        dbRule.listDao.insertList(
            ShoppingListEntity(
                id = id,
                name = "L",
                createdAt = 1L,
                updatedAt = 2L,
                accountId = accountId,
                deletedAt = deletedAt,
            ),
        )

    private suspend fun item(
        id: String,
        listId: String,
        ticked: Boolean = true,
        pickedUpAt: Long? = null,
        deletedAt: Long? = null,
        name: String = "Milk",
    ) = dbRule.itemDao.insertItem(
        ShoppingItemEntity(
            id = id,
            listId = listId,
            name = name,
            quantity = "1",
            isPickedUp = ticked,
            categoryId = "cat-dairy-eggs",
            createdAt = 1L,
            updatedAt = 5_000L,
            pendingSync = false,
            deletedAt = deletedAt,
            pickedUpAt = pickedUpAt,
        ),
    )

    @Test
    fun `a ticked guest item gets its tick time and one guest purchase`() = runTest {
        list("g")
        item("i1", "g", name = "x".repeat(600))
        item("unticked", "g", ticked = false)
        item("timed", "g", pickedUpAt = 9L)
        item("gone", "g", deletedAt = 7L)

        backfill.runOnce()

        val stored = dbRule.itemDao.getItemRaw("i1")!!
        assertEquals(5_000L, stored.pickedUpAt)
        assertEquals(5_000L, stored.updatedAt)
        assertEquals(false, stored.pendingSync)
        val tickId = GotThisIds.tickId("i1", 5_000L)
        val purchase = dbRule.purchaseDao.byTickId(tickId).single()
        assertEquals(GotThisIds.guestPurchaseId(tickId), purchase.id)
        assertNull(purchase.accountId)
        assertEquals(500, purchase.name.length)
        assertEquals("cat-dairy-eggs", purchase.categoryId)
        assertEquals(5_000L, purchase.boughtAt)
        assertEquals(1, dbRule.purchaseDao.guestPurchases().size)
        assertNull(dbRule.itemDao.getItemRaw("unticked")!!.pickedUpAt)
        assertEquals(9L, dbRule.itemDao.getItemRaw("timed")!!.pickedUpAt)
        assertTrue(prefs.backfillDone)
    }

    @Test
    fun `account and deleted lists are untouched`() = runTest {
        list("a", accountId = "u1")
        list("d", deletedAt = 3L)
        item("ia", "a")
        item("id", "d")

        backfill.runOnce()

        assertNull(dbRule.itemDao.getItemRaw("ia")!!.pickedUpAt)
        assertNull(dbRule.itemDao.getItemRaw("id")!!.pickedUpAt)
        assertTrue(dbRule.purchaseDao.guestPurchases().isEmpty())
        assertTrue(prefs.backfillDone)
    }

    @Test
    fun `a second run does nothing`() = runTest {
        list("g")
        item("i1", "g")
        backfill.runOnce()
        item("i2", "g")

        backfill.runOnce()

        assertNull(dbRule.itemDao.getItemRaw("i2")!!.pickedUpAt)
        assertEquals(1, dbRule.purchaseDao.guestPurchases().size)
    }

    @Test
    fun `a key version change recomputes keys and clears learned state`() = runTest {
        prefs.keyVersion = ItemKey.VERSION - 1
        prefs.backfillDone = true
        dbRule.purchaseDao.upsert(
            listOf(
                PurchaseEntity("p1", "t1", null, "i", "2 Organic Bananas", "stale", null, 1L, null, false),
                PurchaseEntity("p2", "t2", "u1", "i", "Milk", "stale", null, 1L, null, false),
            ),
        )
        dbRule.stateDao.upsertMute(ItemMuteEntity("", "stale", 2, 5L))
        dbRule.stateDao.upsertMute(ItemMuteEntity("u1", "stale", 2, 5L))
        dbRule.stateDao.insertImpressionIfAbsent(HintImpressionEntity("i1", "u1", "stale", now, false))
        dbRule.stateDao.insertFollowUp(ReminderFollowUpEntity("", "2026-W45", 1L, "stale", false))

        backfill.runOnce()

        assertEquals("banana", dbRule.purchaseDao.byTickId("t1").single().itemKey)
        assertEquals("milk", dbRule.purchaseDao.byTickId("t2").single().itemKey)
        assertTrue(dbRule.stateDao.mutes("").isEmpty())
        assertTrue(dbRule.stateDao.mutes("u1").isEmpty())
        assertNull(dbRule.stateDao.impression("i1"))
        assertTrue(dbRule.stateDao.followUps("").isEmpty())
        assertEquals(ItemKey.VERSION, prefs.keyVersion)
    }

    @Test
    fun `a current key version leaves state alone`() = runTest {
        prefs.backfillDone = true
        dbRule.stateDao.upsertMute(ItemMuteEntity("", "milk", 2, 5L))

        backfill.runOnce()

        assertEquals(1, dbRule.stateDao.mutes("").size)
    }

    @Test
    fun `impressions older than 30 days are pruned on every run`() = runTest {
        prefs.backfillDone = true
        dbRule.stateDao.insertImpressionIfAbsent(HintImpressionEntity("old", "", "milk", now - 31 * day, false))
        dbRule.stateDao.insertImpressionIfAbsent(HintImpressionEntity("new", "", "milk", now - 29 * day, false))

        backfill.runOnce()

        assertNull(dbRule.stateDao.impression("old"))
        assertNotNull(dbRule.stateDao.impression("new"))
    }
}
