package io.github.leolaurindo.tunnelvision.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import io.github.leolaurindo.tunnelvision.core.FocusAreas
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import java.awt.Color

/**
 * Paints the focus areas of one editor.
 *
 * Dimming covers whatever the retained areas do not, so the focused syntax keeps its own colors
 * instead of being recolored. Each area paints in its own layer, in the order the areas compose —
 * scope head, statement, line, symbol — and all of them stay below warnings, errors and the
 * selection. Everything this renderer paints is dropped by [clear], which is called with the
 * editor's lifetime.
 */
class FocusRenderer(private val editor: Editor, lifetime: Disposable) {

    private val highlighters = mutableListOf<RangeHighlighter>()
    private var rendered: Rendered? = null

    init {
        // Attributes are resolved when they are painted, so a scheme change repaints them.
        ApplicationManager.getApplication().messageBus.connect(lifetime)
            .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { repaint() })
    }

    /** Paints [areas], keeping only the [enabled] ones. Called on the EDT. */
    fun render(areas: FocusAreas, enabled: Set<HighlightArea>) {
        clear()
        rendered = Rendered(areas, enabled)

        val scheme = editor.colorsScheme
        val background = scheme.defaultBackground
        val retained = areas.retained(enabled)
        // Dimming sets the retained areas apart: with nothing retained there is nothing to set
        // apart, and hiding the whole document would only make the editor unreadable.
        if (retained.isEmpty()) return

        paint(FocusColors.resolve(FocusColors.DIM, scheme, background), complement(retained, documentLength()), DIM_LAYER)
        paintComposed(areas, enabled, scheme, background)
    }

    /**
     * Paints the retained ranges, one highlighter per range that shares a style.
     *
     * The areas compose from the broadest to the narrowest, so a statement keeps its own style on
     * a line that is retained as a whole, and the symbol keeps its own on top of both. Composing
     * here rather than stacking highlighters is what keeps the syntax colors of retained code
     * readable: only the fields an area actually styles are replaced.
     */
    private fun paintComposed(
        areas: FocusAreas,
        enabled: Set<HighlightArea>,
        scheme: EditorColorsScheme,
        background: Color,
    ) {
        val styles = HighlightArea.entries
            .filter { it in enabled }
            .mapNotNull { area ->
                FocusColors.resolve(FocusColors.keyOf(area), scheme, background)
                    .takeIf { !it.isEmpty }
                    ?.let { area to it }
            }
        if (styles.isEmpty()) return

        // Every range boundary, so each piece between two of them is covered by a fixed set of areas.
        val boundaries = styles.flatMap { (area, _) -> areas.ranges(area) }
            .flatMap { listOf(it.startOffset, it.endOffset) }
            .distinct()
            .sorted()

        // Pieces are walked in order, so each area's ranges only ever move forward: the sweep stays
        // linear instead of scanning every range of every area for every piece.
        val rangesPerArea = styles.map { (area, _) -> areas.ranges(area).sortedBy { it.startOffset } }
        val covered = IntArray(rangesPerArea.size)

        val byStyle = mutableMapOf<TextAttributes, MutableList<TextRange>>()
        val length = documentLength()
        for (index in 0 until boundaries.size - 1) {
            val start = boundaries[index]
            val end = boundaries[index + 1].coerceAtMost(length)
            if (start >= length || start >= end) continue

            var composed: TextAttributes? = null
            for (areaIndex in rangesPerArea.indices) {
                val ranges = rangesPerArea[areaIndex]
                while (covered[areaIndex] < ranges.size && ranges[covered[areaIndex]].endOffset <= start) covered[areaIndex]++

                val range = ranges.getOrNull(covered[areaIndex]) ?: continue
                if (range.startOffset > start || range.endOffset < end) continue

                val attributes = styles[areaIndex].second
                composed = composed?.let { TextAttributes.merge(it, attributes) } ?: attributes
            }
            if (composed != null) byStyle.getOrPut(composed) { mutableListOf() } += TextRange(start, end)
        }

        for ((attributes, ranges) in byStyle) paint(attributes, FocusAreas.merge(ranges), RETAINED_LAYER)
    }

    /** Drops the highlighters, leaving nothing of this renderer behind. Called with the editor. */
    fun clear() {
        rendered = null
        val markup = editor.markupModel
        highlighters.forEach { markup.removeHighlighter(it) }
        highlighters.clear()
    }

    /** Repaints the last result, which is how a color scheme change reaches the editor. */
    private fun repaint() {
        rendered?.let { render(it.areas, it.enabled) }
    }

    private fun paint(attributes: TextAttributes, ranges: List<TextRange>, layer: Int) {
        if (attributes.isEmpty) return

        val markup = editor.markupModel
        val length = documentLength()
        for (range in ranges) {
            if (range.isEmpty || range.startOffset >= length) continue
            // A stale result may describe text an edit has since shortened: never paint past it.
            highlighters += markup.addRangeHighlighter(
                range.startOffset,
                minOf(range.endOffset, length),
                layer,
                attributes,
                HighlighterTargetArea.EXACT_RANGE,
            )
        }
    }

    private fun documentLength(): Int = editor.document.textLength

    private class Rendered(val areas: FocusAreas, val enabled: Set<HighlightArea>)

    companion object {

        /**
         * Semantic colors use ADDITIONAL_SYNTAX, above lexer syntax. Both focus layers must win
         * over those colors, but stay below weak warnings, errors, and selection.
         */
        private const val DIM_LAYER = HighlighterLayer.ADDITIONAL_SYNTAX + 1

        /** Composed from the broadest area to the narrowest, so no one area masks another. */
        private const val RETAINED_LAYER = DIM_LAYER + 1

        /** @return the parts of `[0, length)` that [retained] does not cover, in document order. */
        fun complement(retained: List<TextRange>, length: Int): List<TextRange> {
            val rest = mutableListOf<TextRange>()
            var offset = 0
            for (range in retained.sortedBy { it.startOffset }) {
                if (range.startOffset > offset) rest += TextRange(offset, range.startOffset)
                offset = maxOf(offset, range.endOffset)
            }
            if (offset < length) rest += TextRange(offset, length)
            return rest
        }
    }
}
