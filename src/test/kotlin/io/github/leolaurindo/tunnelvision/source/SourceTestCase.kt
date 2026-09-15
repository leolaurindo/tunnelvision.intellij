package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.leolaurindo.tunnelvision.core.FocusComputation
import io.github.leolaurindo.tunnelvision.core.FocusRequest
import io.github.leolaurindo.tunnelvision.core.FocusResult
import io.github.leolaurindo.tunnelvision.settings.FocusMode
import io.github.leolaurindo.tunnelvision.settings.FocusScope
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import io.github.leolaurindo.tunnelvision.settings.MatchSource

/** Runs a source the way the refresh pipeline does: off the EDT, inside a read action. */
abstract class SourceTestCase : BasePlatformTestCase() {

    /** Areas every fixture asks for, unless a test disables one. */
    protected open val enabledAreas: Set<HighlightArea> = HighlightArea.entries.toSet()

    protected fun compute(
        computation: FocusComputation,
        caretOffset: Int = myFixture.caretOffset,
        source: MatchSource = MatchSource.PSI,
        areas: Set<HighlightArea> = enabledAreas,
    ): FocusResult = ReadAction.compute<FocusResult, RuntimeException> {
        computation.compute(
            myFixture.editor,
            FocusRequest(
                version = 1,
                caretOffset = caretOffset,
                mode = FocusMode.STATIC,
                source = source,
                scope = FocusScope.FUNCTION,
                areas = areas,
            ),
        )
    }

    /** @return the ranges of [area], as [TextRange]s in the document. */
    protected fun rangesOf(result: FocusResult, area: HighlightArea = HighlightArea.SYMBOL): List<TextRange> =
        matched(result).areas.ranges(area)

    /** @return the name of the symbol the source matched. */
    protected fun symbolOf(result: FocusResult): String = matched(result).symbol

    /** @return the areas that carry at least one range. */
    protected fun areasOf(result: FocusResult): List<HighlightArea> = matched(result).areas.areas()

    /** @return the text of the matched symbol occurrences, which reads like the fixture. */
    protected fun matchedText(result: FocusResult): List<String> = textsOf(rangesOf(result))

    /** @return the text of the statements that stay visible. */
    protected fun statementTexts(result: FocusResult): List<String> = textsOf(rangesOf(result, HighlightArea.STATEMENT))

    /** @return the text of the scope heads that stay visible, bodies excluded. */
    protected fun scopeHeadTexts(result: FocusResult): List<String> = textsOf(rangesOf(result, HighlightArea.SCOPE_HEAD))

    /** @return the text of the lines that stay visible. */
    protected fun lineTexts(result: FocusResult): List<String> = textsOf(rangesOf(result, HighlightArea.LINE))

    /** @return [texts] without the indentation that surrounds a range. */
    protected fun trimmed(texts: List<String>): List<String> = texts.map { it.trim() }

    /** Asserts that the source refused to answer, explaining itself with [fragment]. */
    protected fun assertUnavailable(result: FocusResult, fragment: String) {
        assertTrue(describe(result), result is FocusResult.Unavailable)
        val reason = (result as FocusResult.Unavailable).reason
        assertTrue(reason, reason.contains(fragment))
    }

    private fun textsOf(ranges: List<TextRange>): List<String> {
        val text = myFixture.editor.document.immutableCharSequence
        return ranges.map { text.subSequence(it.startOffset, it.endOffset).toString() }
    }

    private fun matched(result: FocusResult): FocusResult.Matched {
        assertTrue(describe(result), result is FocusResult.Matched)
        return result as FocusResult.Matched
    }

    private fun describe(result: FocusResult): String = when (result) {
        is FocusResult.Matched -> "expected the source to be unavailable, but it matched ${result.symbol}"
        is FocusResult.Unavailable -> "expected matches, but the source is unavailable: ${result.reason}"
    }
}
