package com.babegetthis.android.feature.gotthis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemKeyTest {

    private val keys = listOf(
        "Milk" to "milk",
        "  2 bags of Rice " to "rice",
        "milk 2l" to "milk",
        "milk 2 l" to "milk",
        "Tomatoes" to "tomato",
        "Berries" to "berry",
        "Fresh Organic Bananas" to "banana",
        "Crème fraîche" to "creme fraiche",
        "Boxes" to "box",
        "Glass" to "glass",
        "Hummus" to "hummus",
        "1/2 dozen eggs" to "egg",
        "Coconut-Milk!" to "coconut milk",
        "2 kg" to "2 kg",
        "!!!" to "!!!",
        "دودھ" to "دودھ",
        "दूध" to "दूध",
        "كِتَاب" to "كِتَاب",
        "Dozen eggs" to "dozen egg",
        "Apple 3 pack" to "apple",
        "Peaches" to "peach",
        "Dishes" to "dish",
        "3 of" to "3 of",
        "2 of rice" to "rice",
        "Pack 2 chips" to "pack 2 chip",
    )

    @Test
    fun `keys are normalised`() {
        keys.forEach { (name, key) -> assertEquals(name, key, ItemKey.of(name)) }
    }

    @Test
    fun `version is one`() {
        assertEquals(1, ItemKey.VERSION)
    }

    private val matching = listOf(
        Triple("milk", "milk", true),
        Triple("banana", "bannana", true),
        Triple("banana", "bananna", true),
        Triple("milk", "silk", false),
        Triple("milk", "coconut milk", false),
        Triple("green apple", "green aple", true),
        Triple("bannana", "bannanna", true),
        Triple("banana", "bannanna", false),
        Triple("tomato", "tomtao", true),
        Triple("tomato", "tomota", false),
        Triple("green apple", "greenapple s", false),
        Triple("banana", "bananaaaa", false),
        Triple("banana", "banane", true),
        Triple("banana", "bonono", false),
    )

    @Test
    fun `keys match`() {
        matching.forEach { (a, b, expected) ->
            assertEquals("$a / $b", expected, ItemKey.matches(a, b))
            assertEquals("$b / $a", expected, ItemKey.matches(b, a))
        }
        assertTrue(ItemKey.matches("a", "a"))
        assertFalse(ItemKey.matches("ab", "ac"))
    }
}
