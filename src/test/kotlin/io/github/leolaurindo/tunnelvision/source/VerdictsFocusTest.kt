package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.view.CaretData
import com.intellij.openapi.editor.impl.view.IterationState
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import io.github.leolaurindo.tunnelvision.core.FocusResult
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import io.github.leolaurindo.tunnelvision.settings.MatchSource
import io.github.leolaurindo.tunnelvision.settings.TunnelVisionSettings
import io.github.leolaurindo.tunnelvision.ui.FocusColors
import io.github.leolaurindo.tunnelvision.ui.FocusRenderer
import java.awt.Color
import java.nio.file.Path

/** A synthetic counterpart of the reported sample: lambdas and multiline return arguments. */
class VerdictsFocusTest : SourceTestCase() {

    override fun getTestDataPath(): String = Path.of("src/test/testData").toAbsolutePath().toString()

    override fun setUp() {
        super.setUp()
        myFixture.configureByFile("Verdicts.kt")
    }

    fun testDefaultFocusRetainsOnlyVerdictsLinesInTheSelectedFunction() {
        val document = myFixture.editor.document
        val enabled = TunnelVisionSettings.State().areas.toSet()
        for ((declarationLine, expectedLines) in listOf(
            4 to listOf(4, 9, 11, 12),
            19 to listOf(19, 26, 27, 28),
        )) {
            val declaration = document.text.indexOf("verdicts", document.getLineStartOffset(declarationLine))
            val result = compute(PsiSource(), caretOffset = declaration, areas = enabled)

            assertEquals(List(4) { "verdicts" }, matchedText(result))
            assertEquals(expectedLines, rangesOf(result).map { document.getLineNumber(it.startOffset) })
            assertEquals(
                expectedLines,
                rangesOf(result, HighlightArea.LINE).map { document.getLineNumber(it.startOffset) },
            )
            assertEquals(emptyList<String>(), statementTexts(result))
            assertEquals(emptyList<String>(), scopeHeadTexts(result))
        }
    }

    fun testDimmingWinsOverSemanticColorsButKeepsMatchesAndSelectionVisible() {
        val editor = myFixture.editor as EditorEx
        val text = editor.document.text
        val enabled = TunnelVisionSettings.State().areas.toSet()
        val result = compute(PsiSource(), caretOffset = text.indexOf("val verdicts") + 4, areas = enabled)
        assertTrue("the sample must resolve before testing rendering", result is FocusResult.Matched)
        val areas = (result as FocusResult.Matched).areas

        // Isolate styles from the user's scheme. Syntax colors must survive on retained lines.
        editor.colorsScheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        val dimColor = Color(90, 90, 90)
        val semanticColor = Color(30, 210, 70)
        val symbolBackground = Color(120, 80, 160)
        editor.colorsScheme.setAttributes(FocusColors.DIM, TextAttributes(dimColor, null, null, null, 0))
        editor.colorsScheme.setAttributes(FocusColors.SYMBOL, TextAttributes(null, symbolBackground, null, null, 0))
        editor.colorsScheme.setAttributes(FocusColors.LINE, TextAttributes())
        editor.colorsScheme.setColor(EditorColors.SELECTION_FOREGROUND_COLOR, Color.WHITE)
        editor.colorsScheme.setColor(EditorColors.SELECTION_BACKGROUND_COLOR, Color.BLUE)

        // Language-daemon colors live in document markup at ADDITIONAL_SYNTAX, not lexer SYNTAX.
        val markup = DocumentMarkupModel.forDocument(editor.document, project, true)
        val semantic = markup.addRangeHighlighter(
            0, text.length, HighlighterLayer.ADDITIONAL_SYNTAX,
            TextAttributes(semanticColor, null, null, null, 0), HighlighterTargetArea.EXACT_RANGE,
        )
        val lifetime = Disposer.newDisposable("VerdictsRenderingTest")
        val renderer = FocusRenderer(editor, lifetime)
        try {
            val unrelated = listOf(
                text.indexOf("getSettings()"),
                text.indexOf("batch.map"),
                text.indexOf("threshold = settings.threshold"),
                text.indexOf("countOther(guids"),
                text.lastIndexOf("val verdicts") + 4,
            )
            for (offset in unrelated) assertEquals(semanticColor, mergedAttributesAt(offset).foregroundColor)

            renderer.render(areas, enabled)

            for (offset in unrelated) {
                assertEquals("unrelated code at offset $offset must dim", dimColor, mergedAttributesAt(offset).foregroundColor)
            }
            for (occurrence in areas.ranges(HighlightArea.SYMBOL)) {
                val attributes = mergedAttributesAt(occurrence.startOffset)
                assertEquals(semanticColor, attributes.foregroundColor)
                assertEquals(symbolBackground, attributes.backgroundColor)
            }
            assertEquals(semanticColor, mergedAttributesAt(text.indexOf("groupingBy")).foregroundColor)

            val warningOffset = unrelated.first()
            val warning = markup.addRangeHighlighter(
                warningOffset, warningOffset + "getSettings".length, HighlighterLayer.WEAK_WARNING,
                TextAttributes(Color.MAGENTA, null, null, null, 0), HighlighterTargetArea.EXACT_RANGE,
            )
            try {
                assertEquals(Color.MAGENTA, mergedAttributesAt(warningOffset).foregroundColor)
            } finally {
                markup.removeHighlighter(warning)
            }

            val selected = unrelated.last()
            editor.selectionModel.setSelection(selected, selected + "verdicts".length)
            assertEquals(Color.WHITE, mergedAttributesAt(selected).foregroundColor)
            assertEquals(Color.BLUE, mergedAttributesAt(selected).backgroundColor)
        } finally {
            editor.selectionModel.removeSelection()
            renderer.clear()
            Disposer.dispose(lifetime)
            markup.removeHighlighter(semantic)
        }
    }

    /** IntelliJ's painting merge, including document markup and the actual selection. */
    private fun mergedAttributesAt(offset: Int): TextAttributes {
        val editor = myFixture.editor as EditorEx
        val state = IterationState(
            editor, offset, editor.document.textLength,
            CaretData.createCaretData(editor.document, editor.caretModel),
            false, false, false, false,
        )
        assertFalse("no painted segment at offset $offset", state.atEnd())
        return state.mergedAttributes
    }

    fun testWordFocusIncludesBothDistinctVerdictsVariables() {
        val result = compute(
            WordSource(),
            caretOffset = myFixture.editor.document.text.indexOf("val verdicts") + 4,
            source = MatchSource.WORD,
            areas = TunnelVisionSettings.State().areas.toSet(),
        )

        assertEquals(List(8) { "verdicts" }, matchedText(result))
        assertEquals(
            listOf(4, 9, 11, 12, 19, 26, 27, 28),
            rangesOf(result).map { myFixture.editor.document.getLineNumber(it.startOffset) },
        )
    }
}
