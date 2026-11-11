package com.babegetthis.android.feature.gotthis.data

import androidx.room.withTransaction
import app.cash.turbine.test
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.sync.SyncKicker
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.TickOutboxEntity
import com.babegetthis.android.feature.gotthis.domain.HintSource
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.InMemoryDatabaseRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class GotThisRepositoryTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val day = 86_400_000L

    // 2026-11-01 12:00 UTC
    private val now = 1_793_534_400_000L
    private val prefs = FakeGotThisPrefs()
    private var kicks = 0
    private val repo by lazy {
        GotThisRepository(
            dbRule.db,
            prefs,
            SyncKicker {
                kicks++
            },
            ReminderClock { now },
            { TimeZone.getTimeZone("UTC") },
        )
    }
    private var seq = 0

    private suspend fun list(id: String = "list-1", accountId: String? = "u1", locked: Boolean = false) =
        dbRule.listDao.insertList(
            ShoppingListEntity(
                id = id,
                name = id,
                createdAt = 1L,
                updatedAt = 2L,
                accountId = accountId,
                isLocked = locked,
            ),
        )

    private suspend fun item(name: String, id: String = "i-$name", listId: String = "list-1", ticked: Boolean = false) =
        dbRule.itemDao.insertItem(
            ShoppingItemEntity(
                id = id,
                listId = listId,
                name = name,
                quantity = "1",
                createdAt = 1L,
                updatedAt = 2L,
                isPickedUp = ticked,
            ),
        )

    private suspend fun buy(
        name: String,
        vararg daysAgo: Int,
        category: String? = null,
        account: String? = "u1",
        key: String = ItemKey.of(name),
        source: String = "i",
    ) {
        dbRule.purchaseDao.upsert(
            daysAgo.map {
                seq++
                PurchaseEntity(
                    id = "p$seq",
                    tickId = "t$seq",
                    accountId = account,
                    sourceItemId = source,
                    name = name,
                    itemKey = key,
                    categoryId = category,
                    boughtAt = now - it * day,
                    deletedAt = null,
                    pendingUpload = false,
                )
            },
        )
    }

    @Test
    fun `an unticked item bought 2 days ago in a short-life category shows a hint`() = runTest {
        list()
        item("milk")
        buy("milk", 2, category = "cat-dairy-eggs")

        repo.hintsFor("list-1").test {
            val h = awaitItem().getValue("i-milk")
            assertEquals(2, h.daysAgo)
            assertEquals(HintSource.Category, h.source)
            assertEquals(ItemKey.of("milk"), h.canonicalKey)
            cancel()
        }
    }

    @Test
    fun `6 days ago is outside the window, a ticked item gets none`() = runTest {
        list()
        item("milk")
        item("eggs", ticked = true)
        buy("milk", 6, category = "cat-dairy-eggs")
        buy("eggs", 1, category = "cat-dairy-eggs")

        repo.hintsFor("list-1").test {
            assertTrue(awaitItem().isEmpty())
            cancel()
        }
    }

    @Test
    fun `a typo finds purchases stored under the right spelling`() = runTest {
        list()
        item("bannana")
        buy("banana", 1, category = "cat-fruits-vegetables")

        repo.hintsFor("list-1").test {
            assertEquals(1, awaitItem().getValue("i-bannana").daysAgo)
            cancel()
        }
    }

    @Test
    fun `a weekly regular gets the regular source and a 3-day window`() = runTest {
        list()
        item("eggs")
        buy("eggs", 1, 8, 15, 22)

        repo.hintsFor("list-1").test {
            val h = awaitItem().getValue("i-eggs")
            assertEquals(HintSource.Regular, h.source)
            assertEquals(1, h.daysAgo)
            cancel()
        }
    }

    @Test
    fun `a live mute on any key of the group hides the hint, an expired one does not`() = runTest {
        list()
        item("bannana")
        buy("banana", 1, 2)
        buy("bannana", 1)
        dbRule.stateDao.upsertMute(ItemMuteEntity("u1", ItemKey.of("bannana"), 2, now + day))

        repo.hintsFor("list-1").test {
            assertTrue(awaitItem().isEmpty())
            cancel()
        }

        dbRule.stateDao.upsertMute(ItemMuteEntity("u1", ItemKey.of("bannana"), 2, now - 1))
        repo.hintsFor("list-1").test {
            assertEquals(1, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `hints re-emit when a purchase is inserted`() = runTest {
        list()
        item("milk")
        repo.hintsFor("list-1").test {
            assertTrue(awaitItem().isEmpty())
            buy("milk", 1, category = "cat-dairy-eggs")
            assertEquals(1, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `ownerOf is the list's account, or null when unknown`() = runTest {
        list()
        assertNull(repo.ownerOf("none"))
        assertEquals("u1", repo.ownerOf("list-1"))
    }

    @Test
    fun `usuals list a due item with the latest name and category, most overdue first`() = runTest {
        list()
        buy("Eggs", 21, 14, 7, category = "cat-dairy-eggs")
        buy("Rice", 28, 21, 14, category = "cat-pantry-dry-goods")
        buy("Tea", 20, 13, 6) // not due until tomorrow
        buy("Eggs", 6, category = "cat-other")

        repo.usualsFor("u1").test {
            val usuals = awaitItem()
            assertEquals(listOf("Rice", "Eggs"), usuals.map { it.name })
            assertEquals(7, usuals[0].daysOverdue)
            assertEquals("cat-other", usuals[1].categoryId)
            cancel()
        }
    }

    @Test
    fun `an unticked near-spelling on any of the owner's lists removes the usual`() = runTest {
        list()
        list("list-2")
        buy("banana", 21, 14, 7)
        buy("Eggs", 21, 14, 7)
        item("bannana", listId = "list-2")

        repo.usualsFor("u1").test {
            assertEquals(listOf("Eggs"), awaitItem().map { it.name })
            cancel()
        }
    }

    @Test
    fun `a muted group is not a usual, and guest and account histories do not mix`() = runTest {
        list()
        buy("Eggs", 21, 14, 7)
        buy("Rice", 21, 14, 7)
        buy("Salt", 21, 14, 7, account = null)
        dbRule.stateDao.upsertMute(ItemMuteEntity("u1", ItemKey.of("Rice"), 2, now + day))

        repo.usualsFor("u1").test {
            assertEquals(listOf("Eggs"), awaitItem().map { it.name })
            cancel()
        }
        repo.usualsFor(null).test {
            assertEquals(listOf("Salt"), awaitItem().map { it.name })
            cancel()
        }
    }

    @Test
    fun `the name comes from the latest purchase under the canonical key, not a later typo`() = runTest {
        buy("Banana", 28, 21, 14, category = "cat-fruit")
        buy("bannana", 7, category = "cat-other")

        repo.usualsFor("u1").test {
            val usual = awaitItem().single()
            assertEquals("Banana", usual.name)
            assertEquals("cat-fruit", usual.categoryId)
            cancel()
        }
        assertEquals(listOf("Banana"), repo.dueItems("u1", 0).map { it.name })
    }

    // ---- Locked lists (R24) ----

    @Test
    fun `a regular last bought on a locked list is not named until the list is unlocked`() = runTest {
        list("secret", locked = true)
        item("Eggs", id = "i-secret", listId = "secret", ticked = true)
        buy("Eggs", 21, 14, 7, source = "i-secret")
        buy("Rice", 21, 14, 7)

        assertEquals(listOf("Rice"), repo.dueItems("u1", 0).map { it.name })
        repo.usualsFor("u1").test {
            assertEquals(listOf("Rice"), awaitItem().map { it.name })
            dbRule.listDao.setLocked("secret", false)
            // The other sources also watch shopping_lists and may re-emit the old answer first.
            var names = awaitItem().map { it.name }
            while (names == listOf("Rice")) names = awaitItem().map { it.name }
            assertEquals(listOf("Eggs", "Rice"), names)
            cancel()
        }
        assertEquals(listOf("Eggs", "Rice"), repo.dueItems("u1", 0).map { it.name })
    }

    @Test
    fun `an older purchase from a locked list does not hide a regular last bought elsewhere`() = runTest {
        list("secret", locked = true)
        list("open")
        item("Eggs", id = "i-secret", listId = "secret", ticked = true)
        item("Eggs", id = "i-open", listId = "open", ticked = true)
        buy("Eggs", 21, 14, source = "i-secret")
        buy("Eggs", 7, source = "i-open")

        assertEquals(listOf("Eggs"), repo.dueItems("u1", 0).map { it.name })
        repo.usualsFor("u1").test {
            assertEquals(listOf("Eggs"), awaitItem().map { it.name })
            cancel()
        }
    }

    @Test
    fun `a latest purchase whose item was deleted counts as not locked`() = runTest {
        list("secret", locked = true)
        item("Eggs", id = "i-gone", listId = "secret", ticked = true)
        item("Rice", id = "i-tomb", listId = "secret", ticked = true)
        buy("Eggs", 21, 14, 7, source = "i-gone")
        buy("Rice", 21, 14, 7, source = "i-tomb")
        dbRule.itemDao.deleteItem("i-gone")
        dbRule.itemDao.softDeleteItem("i-tomb", now = now)

        assertEquals(listOf("Eggs", "Rice"), repo.dueItems("u1", 0).map { it.name })
        repo.usualsFor("u1").test {
            assertEquals(listOf("Eggs", "Rice"), awaitItem().map { it.name })
            cancel()
        }
    }

    @Test
    fun `dueItems looks ahead by the given days`() = runTest {
        buy("Tea", 20, 13, 6)
        buy("Eggs", 21, 14, 7)

        assertEquals(listOf("Eggs"), repo.dueItems("u1", 0).map { it.name })
        assertEquals(listOf("Eggs", "Tea"), repo.dueItems("u1", 1).map { it.name }.sorted())
    }

    @Test
    fun `purchaseDays lists live purchases of the owner`() = runTest {
        buy("Eggs", 3, 1)
        buy("Salt", 2, account = null)
        assertEquals(listOf(now - 3 * day, now - day), repo.purchaseDays("u1").sorted())
    }

    @Test
    fun `clearing an account keeps the outbox, sets the pending clear and kicks a push`() = runTest {
        buy("Eggs", 3, 1)
        buy("Salt", 2, account = null)
        dbRule.purchaseDao.insertOutbox(TickOutboxEntity("t", "u1", "i", "list-1", now, "Eggs", null, null, 1))
        dbRule.stateDao.upsertMute(ItemMuteEntity("u1", "eggs", 2, now + day))

        repo.clearHistory("u1")

        assertTrue(repo.purchaseDays("u1").isEmpty())
        assertEquals(1, repo.purchaseDays(null).size)
        assertTrue(dbRule.stateDao.mutes("u1").isEmpty())
        assertEquals(1, dbRule.purchaseDao.countOutbox("u1"))
        assertEquals(now, prefs.pendingClear("u1"))
        assertEquals(1, kicks)
    }

    @Test
    fun `clearing a guest removes purchases and state and sends nothing`() = runTest {
        buy("Salt", 2, account = null)
        buy("Eggs", 1)
        dbRule.stateDao.upsertMute(ItemMuteEntity("", "salt", 2, now + day))

        repo.clearHistory(null)

        assertTrue(repo.purchaseDays(null).isEmpty())
        assertEquals(1, repo.purchaseDays("u1").size)
        assertTrue(dbRule.stateDao.mutes("").isEmpty())
        assertNull(prefs.pendingClear(""))
        assertEquals(0, kicks)
    }

    @Test
    fun `a mute inserted while collecting removes the hint`() = runTest {
        list()
        item("milk")
        buy("milk", 1, category = "cat-dairy-eggs")
        repo.hintsFor("list-1").test {
            assertEquals(1, awaitItem().size)
            dbRule.stateDao.upsertMute(ItemMuteEntity("u1", ItemKey.of("milk"), 2, now + day))
            assertTrue(awaitItem().isEmpty())
            cancel()
        }
    }

    @Test
    fun `a mute inserted while collecting removes the usual`() = runTest {
        buy("Eggs", 21, 14, 7)
        repo.usualsFor("u1").test {
            assertEquals(1, awaitItem().size)
            dbRule.stateDao.upsertMute(ItemMuteEntity("u1", ItemKey.of("Eggs"), 2, now + day))
            assertTrue(awaitItem().isEmpty())
            cancel()
        }
    }

    @Test
    fun `an edit that changes no unticked name does not recompute the usuals`() = runTest {
        list()
        buy("Eggs", 21, 14, 7)
        repo.usualsFor("u1").test {
            assertEquals(1, awaitItem().size)
            // A ticked item changes the items table but no unticked name.
            item("Bread", ticked = true)
            // The next emission must come from this mute, not from the edit above.
            dbRule.stateDao.upsertMute(ItemMuteEntity("u1", ItemKey.of("Eggs"), 2, now + day))
            assertTrue(awaitItem().isEmpty())
            cancel()
        }
    }

    @Test
    fun `an edit that changes no unticked name does not recompute the hints`() = runTest {
        list()
        item("milk")
        buy("milk", 1, category = "cat-dairy-eggs")
        repo.hintsFor("list-1").test {
            assertEquals(1, awaitItem().size)
            item("Bread", ticked = true)
            dbRule.stateDao.upsertMute(ItemMuteEntity("u1", ItemKey.of("milk"), 2, now + day))
            assertTrue(awaitItem().isEmpty())
            cancel()
        }
    }

    @Test
    fun `a guest mute is read under the empty account id`() = runTest {
        buy("Eggs", 21, 14, 7, account = null)
        repo.usualsFor(null).test {
            assertEquals(1, awaitItem().size)
            dbRule.stateDao.upsertMute(ItemMuteEntity("", ItemKey.of("Eggs"), 2, now + day))
            assertTrue(awaitItem().isEmpty())
            cancel()
        }
    }

    @Test
    fun `an unknown list has no hints even when guest purchases exist`() = runTest {
        buy("milk", 1, category = "cat-dairy-eggs", account = null)
        repo.hintsFor("none").test {
            assertTrue(awaitItem().isEmpty())
            cancel()
        }
    }

    @Test
    fun `hints follow the list when an account claims it`() = runTest {
        list(accountId = null)
        item("milk")
        buy("milk", 1, category = "cat-dairy-eggs", account = null)
        repo.hintsFor("list-1").test {
            assertEquals(1, awaitItem().size)
            // One transaction, so the flow sees one change rather than racing two.
            dbRule.db.withTransaction {
                buy("milk", 2, category = "cat-dairy-eggs", account = "u1")
                dbRule.listDao.claimList("list-1", "u1")
            }
            assertEquals(2, awaitItem().getValue("i-milk").daysAgo)
            cancel()
        }
    }

    @Test
    fun `summaryCounts counts regular groups and live purchases of one owner`() = runTest {
        buy("eggs", 1, 8, 15, 22)
        buy("tea", 3)
        buy("milk", 2, 9, 16, 23, account = "u2")

        val counts = repo.summaryCounts("u1")

        assertEquals(GotThisCounts(regulars = 1, purchases = 5), counts)
        assertEquals(GotThisCounts(regulars = 1, purchases = 4), repo.summaryCounts("u2"))
        assertEquals(GotThisCounts(regulars = 0, purchases = 0), repo.summaryCounts(null))
    }
}
