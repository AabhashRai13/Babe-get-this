package com.babegetthis.android.feature.gotthis.domain

import com.babegetthis.android.core.data.local.DEFAULT_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GotThisRulesTest {

    @Test
    fun `every default category has a window and sits in at most one group`() {
        val groups = GotThisRules.CATEGORY_GROUPS.values.map { it.toSet() }
        DEFAULT_CATEGORIES.forEach { category ->
            val inGroups = groups.count { category.id in it }
            if (category.id == "cat-other") {
                assertEquals("cat-other must fall to Unknown", 0, inGroups)
                assertEquals(7, GotThisRules.categoryWindowDays(category.id))
            } else {
                assertEquals("${category.id} must be in exactly one group", 1, inGroups)
            }
        }
    }

    @Test
    fun `groups hold only default category ids`() {
        val ids = DEFAULT_CATEGORIES.map { it.id }.toSet()
        GotThisRules.CATEGORY_GROUPS.values.flatten().forEach { assertTrue("$it", it in ids) }
    }

    @Test
    fun `spot values of the window table`() {
        assertEquals(3, GotThisRules.categoryWindowDays("cat-meat-seafood"))
        assertEquals(5, GotThisRules.categoryWindowDays("cat-dairy-eggs"))
        assertEquals(14, GotThisRules.categoryWindowDays("cat-snacks"))
        assertEquals(30, GotThisRules.categoryWindowDays("cat-household-cleaning"))
        assertEquals(90, GotThisRules.categoryWindowDays("cat-clothing"))
    }

    @Test
    fun `custom and missing categories get the unknown window`() {
        assertEquals(7, GotThisRules.categoryWindowDays("custom-1"))
        assertEquals(7, GotThisRules.categoryWindowDays(null))
    }
}
