package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.leolaurindo.tunnelvision.core.FocusAreas
import io.github.leolaurindo.tunnelvision.settings.HighlightArea

/**
 * Derives the highlight areas of a set of occurrences.
 *
 * The areas a user disabled are not computed at all, so a symbol-and-line configuration never pays
 * for the PSI walk behind statements and scope heads.
 */
internal object Areas {

    /** Longest statement, in lines, that is still worth keeping visible in full. */
    private const val MAX_STATEMENT_LINES = 50

    /** Areas derived from the document alone: the occurrences and the lines they sit on. */
    fun ofDocument(
        document: Document,
        occurrences: List<TextRange>,
        enabled: Set<HighlightArea>,
    ): FocusAreas = FocusAreas.of(
        symbol = occurrences,
        line = if (HighlightArea.LINE in enabled) lineRanges(document, occurrences) else emptyList(),
    )

    /** Areas derived through PSI: [ofDocument] plus statements and scope heads. */
    fun ofPsi(
        document: Document,
        file: PsiFile,
        adapter: StructuralAdapter,
        occurrences: List<TextRange>,
        enabled: Set<HighlightArea>,
    ): FocusAreas {
        val elements = elementsAt(file, occurrences)
        return FocusAreas.of(
            symbol = occurrences,
            line = if (HighlightArea.LINE in enabled) lineRanges(document, occurrences) else emptyList(),
            statement = if (HighlightArea.STATEMENT in enabled) statementRanges(document, elements, adapter) else emptyList(),
            scopeHead = if (HighlightArea.SCOPE_HEAD in enabled) scopeHeadRanges(elements, adapter) else emptyList(),
        )
    }

    /** @return the complete document lines the occurrences sit on. */
    fun lineRanges(document: Document, occurrences: List<TextRange>): List<TextRange> =
        occurrences.map { lineRange(document, it) }.distinct().sortedBy { it.startOffset }

    private fun lineRange(document: Document, occurrence: TextRange): TextRange {
        val line = document.getLineNumber(occurrence.startOffset)
        return TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
    }

    private fun statementRanges(
        document: Document,
        elements: List<PsiElement>,
        adapter: StructuralAdapter,
    ): List<TextRange> = elements
        .mapNotNull { adapter.statement(it) }
        .filter { it.spansAtMost(MAX_STATEMENT_LINES, document) }
        .map { it.textRange }
        .distinct()
        .sortedBy { it.startOffset }

    private fun scopeHeadRanges(elements: List<PsiElement>, adapter: StructuralAdapter): List<TextRange> =
        elements
            .flatMap { adapter.scopeHeads(it) }
            .filter { !it.isEmpty }
            .distinct()
            .sortedBy { it.startOffset }

    /** @return the PSI element each occurrence starts on. */
    private fun elementsAt(file: PsiFile, occurrences: List<TextRange>): List<PsiElement> =
        occurrences.mapNotNull { file.findElementAt(it.startOffset) }

    private fun PsiElement.spansAtMost(lines: Int, document: Document): Boolean {
        val first = document.getLineNumber(textRange.startOffset)
        val last = document.getLineNumber(textRange.endOffset)
        return last - first + 1 <= lines
    }
}
