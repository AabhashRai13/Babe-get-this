package com.babegetthis.android.feature.gotthis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemGroupsTest {

    private val banana = KeyStat("banana", 2, 100)
    private val bannana = KeyStat("bannana", 1, 200)

    @Test
    fun `typos share a group led by the most bought key`() {
        val groups = ItemGroups.build(listOf(bannana, banana))
        assertEquals(listOf(ItemGroup("banana", listOf("banana", "bannana"))), groups)
    }

    @Test
    fun `a key two edits from the canonical key gets its own group`() {
        val groups = ItemGroups.build(listOf(banana, bannana, KeyStat("bannanna", 1, 50)))
        assertEquals(listOf("banana", "bannanna"), groups.map { it.canonicalKey })
        assertEquals(listOf("banana", "bannana"), groups[0].keys)
    }

    @Test
    fun `a name that contains another is a separate group`() {
        val groups = ItemGroups.build(listOf(KeyStat("milk", 3, 1), KeyStat("coconut milk", 1, 1)))
        assertEquals(listOf("milk", "coconut milk"), groups.map { it.canonicalKey })
    }

    @Test
    fun `ties go to the more recent key then the alphabetical one`() {
        val recent = ItemGroups.build(listOf(KeyStat("banana", 1, 100), KeyStat("bannana", 1, 200)))
        assertEquals("bannana", recent.single().canonicalKey)
        val alphabetical = ItemGroups.build(listOf(KeyStat("bannana", 1, 100), KeyStat("banana", 1, 100)))
        assertEquals("banana", alphabetical.single().canonicalKey)
    }

    @Test
    fun `groupFor finds the group whose canonical key matches`() {
        val groups = ItemGroups.build(listOf(banana, bannana))
        assertEquals("banana", ItemGroups.groupFor("bananna", groups)?.canonicalKey)
        assertNull(ItemGroups.groupFor("kiwi", groups))
    }

    @Test
    fun `no stats means no groups`() {
        assertTrue(ItemGroups.build(emptyList()).isEmpty())
    }
}
