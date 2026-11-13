package com.babegetthis.android.feature.gotthis.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.babegetthis.android.feature.gotthis.data.UsualItem
import com.babegetthis.android.feature.gotthis.ui.viewModels.UsualsState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsualsRowTest {

    @get:Rule val compose = createComposeRule()

    private val eggs = UsualItem("eggs", "Eggs", null, 1)
    private val milk = UsualItem("milk", "Milk", "dairy", 0)

    private var added: UsualItem? = null
    private var expanded = 0
    private var notNow = 0
    private var headsUp = 0

    private fun row(more: Int = 2, offerHeadsUp: Boolean = false) = compose.setContent {
        UsualsRow(
            state = UsualsState(listOf(eggs, milk), more, expanded = false, offerHeadsUp = offerHeadsUp),
            onChip = { added = it },
            onExpand = { expanded++ },
            onNotNow = { notNow++ },
            onHeadsUp = { headsUp++ },
        )
    }

    @Test
    fun `shows the title and one chip per item`() {
        row()

        compose.onNodeWithText("Usually bought around now").assertIsDisplayed()
        compose.onNodeWithText("+ Eggs").assertIsDisplayed()
        compose.onNodeWithText("+ Milk").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithTag("usual_chip").fetchSemanticsNodes().size)
    }

    @Test
    fun `tapping a chip reports that item`() {
        row()

        compose.onNodeWithText("+ Eggs").performClick()

        assertEquals(eggs, added)
    }

    @Test
    fun `the more chip expands the row`() {
        row()

        compose.onNodeWithText("+2").performClick()

        assertEquals(1, expanded)
    }

    @Test
    fun `no more chip when nothing is left over`() {
        row(more = 0)

        compose.onNodeWithTag("usuals_more").assertDoesNotExist()
    }

    @Test
    fun `not now reports a dismissal`() {
        row()

        compose.onNodeWithContentDescription("Not now").performClick()

        assertEquals(1, notNow)
    }

    @Test
    fun `the heads-up button only appears when offered`() {
        row(offerHeadsUp = false)
        compose.onNodeWithText("Get a weekly heads-up").assertDoesNotExist()
    }

    @Test
    fun `the heads-up button reports a tap`() {
        row(offerHeadsUp = true)

        compose.onNodeWithText("Get a weekly heads-up").performClick()

        assertEquals(1, headsUp)
    }

    @Test
    fun `the row carries its test tag`() {
        row()

        compose.onNodeWithTag("usuals_row").assertIsDisplayed()
    }
}
