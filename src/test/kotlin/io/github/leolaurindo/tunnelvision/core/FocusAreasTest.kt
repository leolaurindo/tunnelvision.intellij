package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.util.TextRange
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusAreasTest {

    @Test
    fun `areas compose from the broadest to the narrowest`() {
        val areas = FocusAreas.of(
            symbol = listOf(TextRange(5, 10)),
            line = listOf(TextRange(0, 20)),
            statement = listOf(TextRange(2, 15)),
            scopeHead = listOf(TextRange(0, 3)),
        )

        assertEquals(
            listOf(HighlightArea.SCOPE_HEAD, HighlightArea.STATEMENT, HighlightArea.LINE, HighlightArea.SYMBOL),
            areas.areas(),
        )
    }

    @Test
    fun `retained merges the enabled areas and leaves the disabled ones out`() {
        val areas = FocusAreas.of(
            symbol = listOf(TextRange(5, 10), TextRange(30, 35)),
            line = listOf(TextRange(0, 12), TextRange(28, 40)),
        )

        assertEquals(
            listOf(TextRange(5, 10), TextRange(30, 35)),
            areas.retained(setOf(HighlightArea.SYMBOL)),
        )
        assertEquals(listOf(TextRange(0, 12), TextRange(28, 40)), areas.retained(setOf(HighlightArea.LINE)))
        assertEquals(listOf(TextRange(0, 12), TextRange(28, 40)), areas.retained(HighlightArea.entries.toSet()))
    }

    @Test
    fun `merge joins touching and overlapping ranges`() {
        assertEquals(
            listOf(TextRange(0, 12), TextRange(20, 24)),
            FocusAreas.merge(listOf(TextRange(20, 24), TextRange(0, 8), TextRange(6, 12))),
        )
    }

    @Test
    fun `retained of nothing enabled is empty`() {
        val areas = FocusAreas.of(symbol = listOf(TextRange(1, 2)))

        assertEquals(emptyList<TextRange>(), areas.retained(emptySet()))
    }
}
