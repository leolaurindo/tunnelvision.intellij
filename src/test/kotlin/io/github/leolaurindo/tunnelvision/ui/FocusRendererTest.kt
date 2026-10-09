package io.github.leolaurindo.tunnelvision.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.leolaurindo.tunnelvision.core.FocusAreas
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import java.awt.Color

/** Rendering tests: composition, complement ranges, disabled areas, and scheme changes. */
class FocusRendererTest : BasePlatformTestCase() {

    /** Distinct colors per style, which is how a highlighter is traced back to its area. */
    private val styles = mapOf(
        FocusColors.DIM to TextAttributes(DIM_COLOR, null, null, null, 0),
        FocusColors.SCOPE_HEAD to TextAttributes(SCOPE_HEAD_COLOR, null, null, null, 0),
        FocusColors.STATEMENT to TextAttributes(STATEMENT_COLOR, null, null, null, 0),
        // The line style is a band, as the shipped defaults have it.
        FocusColors.LINE to TextAttributes(null, LINE_BAND, null, null, 0),
        FocusColors.SYMBOL to TextAttributes(SYMBOL_COLOR, null, null, null, 0),
    )

    private val lifetime = Disposer.newDisposable("TunnelVisionTest")
    private lateinit var renderer: FocusRenderer

    override fun setUp() {
        super.setUp()
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                int count = 0;

                void run() {
                    count++;
                }
            }
            """.trimIndent(),
        )
        for ((key, attributes) in styles) myFixture.editor.colorsScheme.setAttributes(key, attributes)
        renderer = FocusRenderer(myFixture.editor, lifetime)
    }

    override fun tearDown() {
        try {
            // The keys belong to the plugin, so leaving them unset restores the shared scheme.
            for (key in styles.keys) myFixture.editor.colorsScheme.setAttributes(key, TextAttributes())
            Disposer.dispose(lifetime)
        } finally {
            super.tearDown()
        }
    }

    fun testComposesTheAreasThatCoverARange() {
        renderer.render(areas(), HighlightArea.entries.toSet())
        val declaration = range("int count = 0;")
        val symbol = range("count = 0")

        // A statement keeps its own style on a line that the line area retains as a whole.
        assertEquals(STATEMENT_COLOR, attributesAt(declaration.startOffset)?.foregroundColor)
        assertEquals(LINE_BAND, attributesAt(declaration.startOffset)?.backgroundColor)

        // The symbol keeps its own on top of both.
        assertEquals(SYMBOL_COLOR, attributesAt(symbol.startOffset)?.foregroundColor)
        assertEquals(LINE_BAND, attributesAt(symbol.startOffset)?.backgroundColor)

        // And the rest of a retained line is just the band, with the syntax colors left alone.
        assertEquals(null, attributesAt(lineOf(1).startOffset)?.foregroundColor)
        assertEquals(LINE_BAND, attributesAt(lineOf(1).startOffset)?.backgroundColor)
    }

    fun testKeepsTheScopeHeadStyleOnARetainedLine() {
        // The method header line holds a scope head, and the line area retains the occurrence line.
        renderer.render(areas(), HighlightArea.entries.toSet())

        assertEquals(SCOPE_HEAD_COLOR, attributesAt(range("void run()").startOffset)?.foregroundColor)
    }

    fun testStaysAboveSemanticColorsAndBelowDiagnosticsAndSelection() {
        renderer.render(areas(), HighlightArea.entries.toSet())

        for (highlighter in highlighters()) {
            assertTrue(
                "layer ${highlighter.layer} would hide diagnostics",
                highlighter.layer > HighlighterLayer.ADDITIONAL_SYNTAX && highlighter.layer < HighlighterLayer.WEAK_WARNING,
            )
            assertTrue("layer ${highlighter.layer} would hide the selection", highlighter.layer < HighlighterLayer.SELECTION)
        }
        assertTrue("dimming must sit below the retained style", dimLayer() < retainedLayer())
    }

    fun testDimsOnlyWhatTheRetainedAreasDoNotCover() {
        renderer.render(areas(), HighlightArea.entries.toSet())

        assertEquals(complementOf(areas().retained(HighlightArea.entries.toSet())), rangesOf(DIM_COLOR))
    }

    fun testLeavesOutDisabledAreasAndDimsTheirRanges() {
        val enabled = setOf(HighlightArea.LINE, HighlightArea.SYMBOL)

        renderer.render(areas(), enabled)

        val declaration = range("int count = 0;")
        assertEquals(LINE_BAND, attributesAt(declaration.startOffset)?.backgroundColor)
        // No statement style, so the text keeps whatever the syntax highlighter gives it.
        assertEquals(null, attributesAt(declaration.startOffset)?.foregroundColor)
        assertEquals(SYMBOL_COLOR, attributesAt(range("count = 0").startOffset)?.foregroundColor)
        assertEquals(complementOf(areas().retained(enabled)), rangesOf(DIM_COLOR))
    }

    fun testKeepsAnUnstyledAreaUndimmedWithoutPaintingIt() {
        myFixture.editor.colorsScheme.setAttributes(FocusColors.LINE, TextAttributes())
        val declaration = range("int count = 0;")

        renderer.render(areas(), setOf(HighlightArea.LINE, HighlightArea.SYMBOL))

        // Nothing is painted over the statement, so its syntax colors are untouched...
        assertEquals(null, attributesAt(declaration.startOffset)?.backgroundColor)
        assertEquals(null, attributesAt(declaration.startOffset)?.foregroundColor)
        // ...but the line is retained, so it is not dimmed either.
        assertEquals(
            emptyList<TextRange>(),
            rangesOf(DIM_COLOR).filter { it.startOffset <= declaration.startOffset && declaration.endOffset <= it.endOffset },
        )
    }

    fun testPaintsNothingForStylesTheSchemeDoesNotDefine() {
        myFixture.editor.colorsScheme.setAttributes(FocusColors.SYMBOL, TextAttributes())

        renderer.render(areas(), HighlightArea.entries.toSet())

        assertEquals(null, attributesAt(range("count = 0").startOffset)?.foregroundColor?.takeIf { it == SYMBOL_COLOR })
    }

    fun testRepaintsWhenTheColorSchemeChanges() {
        val repainted = Color(6, 6, 6)
        renderer.render(areas(), setOf(HighlightArea.SYMBOL))

        myFixture.editor.colorsScheme.setAttributes(FocusColors.SYMBOL, TextAttributes(repainted, null, null, null, 0))
        ApplicationManager.getApplication().messageBus.syncPublisher(EditorColorsManager.TOPIC)
            .globalSchemeChange(myFixture.editor.colorsScheme)

        assertEquals(areas().ranges(HighlightArea.SYMBOL), rangesOf(repainted))
    }

    fun testHidesNothingWhenNoAreaIsEnabled() {
        renderer.render(areas(), emptySet())

        // With nothing retained, dimming the whole document would hide the focused symbol too.
        assertEquals(emptyList<RangeHighlighter>(), highlighters())
    }

    fun testLeavesOutRangesThatTheDocumentNoLongerHas() {
        val beyond = FocusAreas.of(symbol = listOf(TextRange(0, 5), TextRange(10_000, 10_005)))

        renderer.render(beyond, setOf(HighlightArea.SYMBOL))

        assertEquals(listOf(TextRange(0, 5)), rangesOf(SYMBOL_COLOR))
    }

    fun testClearLeavesNoHighlighterBehind() {
        renderer.render(areas(), HighlightArea.entries.toSet())

        renderer.clear()

        assertEquals(emptyList<RangeHighlighter>(), highlighters())
    }

    /** The fixture's declaration, its use, and one statement and scope head around them. */
    private fun areas(): FocusAreas = FocusAreas.of(
        symbol = listOf(range("count = 0"), range("count++")),
        line = listOf(lineOf(1), lineOf(4)),
        statement = listOf(range("int count = 0;")),
        scopeHead = listOf(range("void run()")),
    )

    private fun range(text: String): TextRange {
        val start = myFixture.editor.document.text.indexOf(text)
        return TextRange(start, start + text.length)
    }

    private fun lineOf(number: Int): TextRange {
        val document = myFixture.editor.document
        return TextRange(document.getLineStartOffset(number), document.getLineEndOffset(number))
    }

    private fun complementOf(retained: List<TextRange>): List<TextRange> =
        FocusRenderer.complement(retained, myFixture.editor.document.textLength)

    private fun highlighters(): List<RangeHighlighter> =
        myFixture.editor.markupModel.allHighlighters
            .sortedWith(compareBy({ it.layer }, { it.startOffset }))

    /** @return the style painted at [offset], which is the one from the highest layer covering it. */
    private fun attributesAt(offset: Int): TextAttributes? = highlighters()
        .filter { it.startOffset <= offset && offset < it.endOffset }
        .maxByOrNull { it.layer }
        ?.textAttributes

    private fun rangesOf(foreground: Color): List<TextRange> = highlighters()
        .filter { it.textAttributes?.foregroundColor == foreground }
        .map { TextRange(it.startOffset, it.endOffset) }

    private fun layerOf(key: TextAttributesKey): Int {
        val layers = highlighters().filter { it.textAttributes == styles.getValue(key) }.map { it.layer }
        assertFalse("nothing painted for $key", layers.isEmpty())
        return layers.min()
    }

    private fun dimLayer(): Int = layerOf(FocusColors.DIM)

    private fun retainedLayer(): Int = layerOf(FocusColors.LINE)

    private companion object {
        val DIM_COLOR = Color(1, 1, 1)
        val SCOPE_HEAD_COLOR = Color(2, 2, 2)
        val STATEMENT_COLOR = Color(3, 3, 3)
        val SYMBOL_COLOR = Color(5, 5, 5)
        val LINE_BAND = Color(4, 4, 4)
    }
}
