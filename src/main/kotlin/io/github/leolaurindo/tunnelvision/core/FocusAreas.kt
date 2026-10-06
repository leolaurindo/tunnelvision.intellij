package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.util.TextRange
import io.github.leolaurindo.tunnelvision.settings.HighlightArea

/**
 * Highlight ranges of one refresh, grouped by area.
 *
 * The areas are computed together because they all derive from the same occurrences: the symbol
 * ranges are the occurrences themselves, and the broader areas are derived from them.
 */
class FocusAreas(private val byArea: Map<HighlightArea, List<TextRange>>) {

    /** @return the ranges of [area], empty when the area carries none. */
    fun ranges(area: HighlightArea): List<TextRange> = byArea[area].orEmpty()

    /** @return the areas that carry at least one range, in the order the areas compose. */
    fun areas(): List<HighlightArea> = HighlightArea.entries.filter { ranges(it).isNotEmpty() }

    /** @return the ranges of the enabled areas, merged, in document order. */
    fun retained(enabled: Set<HighlightArea>): List<TextRange> =
        merge(areas().filter { it in enabled }.flatMap { ranges(it) })

    companion object {

        fun combine(areas: List<FocusAreas>): FocusAreas = FocusAreas(
            HighlightArea.entries.associateWith { area -> merge(areas.flatMap { it.ranges(area) }) },
        )

        fun of(
            symbol: List<TextRange>,
            line: List<TextRange> = emptyList(),
            statement: List<TextRange> = emptyList(),
            scopeHead: List<TextRange> = emptyList(),
        ): FocusAreas = FocusAreas(
            mapOf(
                HighlightArea.SCOPE_HEAD to scopeHead,
                HighlightArea.STATEMENT to statement,
                HighlightArea.LINE to line,
                HighlightArea.SYMBOL to symbol,
            ),
        )

        /** @return [ranges] sorted and merged where they touch or overlap. */
        fun merge(ranges: List<TextRange>): List<TextRange> {
            val merged = mutableListOf<TextRange>()
            for (range in ranges.sortedBy { it.startOffset }) {
                val last = merged.lastOrNull()
                if (last == null || range.startOffset > last.endOffset) {
                    merged += range
                } else if (range.endOffset > last.endOffset) {
                    merged[merged.lastIndex] = TextRange(last.startOffset, range.endOffset)
                }
            }
            return merged
        }
    }
}
