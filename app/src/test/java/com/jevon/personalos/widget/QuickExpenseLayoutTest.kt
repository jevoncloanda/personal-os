package com.jevon.personalos.widget

import org.junit.Assert.*
import org.junit.Test

class QuickExpenseLayoutTest {
    @Test
    fun minimumFootprintReservesItsOnlyActionForCustomAmount() {
        val layout = quickExpenseLayout(140f, 64f)
        assertEquals(0, layout.presetCapacity)
        assertFalse(layout.showHeader)
        assertTrue(layout.compact)
    }

    @Test
    fun shortTwoColumnFootprintIsCustomOnly() {
        assertEquals(1, quickExpenseLayout(206f, 110f).actionSlots)
    }

    @Test
    fun narrowMediumFitsOnePresetAndCustomAmount() {
        val layout = quickExpenseLayout(206f, 234f)
        assertEquals(1, layout.presetCapacity)
        assertEquals(1, layout.columns)
        assertTrue(layout.showHeader)
    }

    @Test
    fun wideMediumFitsThreePresetsAndCustomAmount() {
        val layout = quickExpenseLayout(429f, 234f)
        assertEquals(3, layout.presetCapacity)
        assertEquals(2, layout.columns)
    }

    @Test
    fun spaciousAndTallNarrowInstancesRestoreAllPresets() {
        assertFalse(quickExpenseLayout(320f, 480f).compact)
        val narrow = quickExpenseLayout(206f, 480f)
        assertEquals(4, narrow.actionSlots)
        assertEquals(1, narrow.columns)
    }

    @Test
    fun intermediateFootprintsSpendSpaceOnActionsBeforeAHeader() {
        val narrow = quickExpenseLayout(240f, 170f)
        assertEquals(2, narrow.actionSlots)
        assertFalse(narrow.showHeader)
        val wide = quickExpenseLayout(429f, 110f)
        assertEquals(2, wide.actionSlots)
        assertEquals(2, wide.columns)
        assertFalse(wide.showHeader)
    }

    @Test
    fun largerTextReducesCapacityBeforeTargetsBecomeCrowded() {
        assertEquals(4, quickExpenseLayout(429f, 220f).actionSlots)
        assertEquals(2, quickExpenseLayout(429f, 220f, 1.5f).actionSlots)
    }

    @Test
    fun changingSizeOnlyChangesTheVisiblePrefixOfTheSavedOrder() {
        val saved = listOf("third", "first", "second")
        assertEquals(emptyList<String>(), saved.take(quickExpenseLayout(206f, 110f).presetCapacity))
        assertEquals(listOf("third"), saved.take(quickExpenseLayout(206f, 234f).presetCapacity))
        assertEquals(saved, saved.take(quickExpenseLayout(429f, 234f).presetCapacity))
        assertEquals(listOf("third", "first", "second"), saved)
    }
}
