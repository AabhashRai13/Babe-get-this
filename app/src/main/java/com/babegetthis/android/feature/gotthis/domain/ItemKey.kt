package com.babegetthis.android.feature.gotthis.domain

import java.text.Normalizer
import java.util.Locale

// Turns a shopping-item name into the key used to recognise repeat purchases.
object ItemKey {

    // Bump when the normalisation rules change, so stored keys can be rebuilt.
    const val VERSION = 1

    private const val MIN_TYPO_LENGTH = 5

    private val UNITS = setOf(
        "x", "g", "kg", "ml", "l", "pack", "packs", "bag", "bags", "bottle", "bottles",
        "can", "cans", "box", "boxes", "dozen",
    )
    private val DROPPED_WORDS = setOf("fresh", "organic", "large", "small", "big", "medium")
    private val ACCENTS = Regex("[\\u0300-\\u036F]")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}\\s]")
    private val WHITESPACE = Regex("\\s+")
    private val NUMBER = Regex("\\p{N}+")
    private val NUMBER_WITH_UNIT = Regex("(\\p{N}+)(\\p{L}+)")

    fun of(name: String): String {
        val folded = Normalizer.normalize(name, Normalizer.Form.NFKD)
            .replace(ACCENTS, "")
            .lowercase(Locale.ROOT)
        val words = folded.replace(NOT_WORD, " ").trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val key = dropQuantity(words)
            .filter { it !in DROPPED_WORDS }
            .joinToString(" ") { singular(it) }
        return key.ifEmpty { name.trim().lowercase(Locale.ROOT) }
    }

    fun matches(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < MIN_TYPO_LENGTH || b.length < MIN_TYPO_LENGTH) return false
        if (a.count { it == ' ' } != b.count { it == ' ' }) return false
        return withinOneEdit(a, b)
    }

    private fun isNumber(word: String) = NUMBER.matches(word)

    private fun isQuantity(word: String): Boolean {
        if (isNumber(word)) return true
        val glued = NUMBER_WITH_UNIT.matchEntire(word) ?: return false
        return glued.groupValues[2] in UNITS
    }

    // A unit only counts as part of a quantity when it sits next to a number.
    private fun dropQuantity(words: List<String>): List<String> {
        val list = words.toMutableList()
        var droppedNumber = false
        while (list.isNotEmpty()) {
            val first = list.first()
            when {
                isQuantity(first) -> droppedNumber = true
                droppedNumber && first in UNITS -> Unit
                droppedNumber && first == "of" -> Unit
                else -> break
            }
            list.removeAt(0)
        }
        while (list.isNotEmpty()) {
            val last = list.last()
            val unitAfterNumber = last in UNITS && list.size > 1 && isNumber(list[list.size - 2])
            if (!isQuantity(last) && !unitAfterNumber) break
            list.removeAt(list.size - 1)
        }
        return list
    }

    private fun singular(word: String): String = when {
        word.endsWith("ies") && word.length > 3 -> word.dropLast(3) + "y"
        word.endsWith("oes") -> word.dropLast(2)
        word.endsWith("ses") || word.endsWith("xes") || word.endsWith("ches") || word.endsWith("shes") ->
            word.dropLast(2)
        word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is") ->
            word.dropLast(1)
        else -> word
    }

    // Damerau-Levenshtein distance of 1: one insert, delete or substitution,
    // or one swap of two neighbouring letters.
    private fun withinOneEdit(a: String, b: String): Boolean {
        if (Math.abs(a.length - b.length) > 1) return false
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        val restA = a.substring(i)
        val restB = b.substring(i)
        return when {
            restA.length > restB.length -> restA.substring(1) == restB
            restA.length < restB.length -> restB.substring(1) == restA
            else -> restA.substring(1) == restB.substring(1) || swapped(restA, restB)
        }
    }

    private fun swapped(a: String, b: String): Boolean =
        a.length >= 2 && a[0] == b[1] && a[1] == b[0] && a.substring(2) == b.substring(2)
}
