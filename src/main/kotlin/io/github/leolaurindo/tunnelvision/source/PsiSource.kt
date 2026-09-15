package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import io.github.leolaurindo.tunnelvision.core.FocusComputation
import io.github.leolaurindo.tunnelvision.core.FocusRequest
import io.github.leolaurindo.tunnelvision.core.FocusResult

/**
 * Semantic occurrences of the symbol at the caret, restricted to its enclosing named function.
 *
 * The reference search runs over a [LocalSearchScope], so it stays inside the function. Local
 * symbols are answered from PSI alone, but methods can still reach for the index, which is why
 * the refresh waits for smart mode and why an index that is not ready is reported here.
 */
class PsiSource : FocusComputation {

    override fun compute(editor: Editor, request: FocusRequest): FocusResult {
        val project = editor.project
            ?: return FocusResult.Unavailable("the editor is not attached to a project")
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            ?: return FocusResult.Unavailable("the document has no PSI file")

        ProgressManager.checkCanceled()
        val resolution = indexed { TargetResolver.resolve(file, request.caretOffset) }
            ?: return INDEXING
        val target = when (resolution) {
            is TargetResolver.Resolution.Resolved -> resolution.target
            TargetResolver.Resolution.NoSymbol -> return FocusResult.Unavailable("no symbol at the caret")
            TargetResolver.Resolution.UnresolvedReference ->
                return FocusResult.Unavailable("the symbol at the caret does not resolve to a declaration")
        }

        val element = target.element
        // The scope comes from where the caret is, not from where the declaration happens to be:
        // a field declared outside the function still scopes to the function that uses it.
        val adapter = StructuralAdapter.forElement(target.anchor)
            ?: return FocusResult.Unavailable(
                "${target.anchor.language.displayName} has no structural adapter; use the word source instead",
                report = true,
            )
        val function = adapter.enclosingFunction(target.anchor)
            ?: return FocusResult.Unavailable(
                "no named function encloses ${target.name}; use the word source for whole-file matching",
                report = true,
            )

        ProgressManager.checkCanceled()
        val ranges = mutableListOf<TextRange>()
        // The declaration is part of the result only when the selected scope contains it.
        if (function.textRange.contains(target.nameRange)) ranges += target.nameRange

        val scope = LocalSearchScope(function)
        val references = indexed { ReferencesSearch.search(element, scope, false).findAll() }
            ?: return INDEXING
        for (reference in references) {
            ProgressManager.checkCanceled()
            ranges += reference.absoluteRange
        }

        val occurrences = ranges.distinct().sortedBy { it.startOffset }
        val areas = Areas.ofPsi(editor.document, file, adapter, occurrences, request.areas)
        return FocusResult.Matched(target.name, areas)
    }

    /**
     * Runs [work], which may consult the indexes, and returns null while they are not ready.
     *
     * The refresh waits for smart mode, so this only catches indexing that starts mid-refresh:
     * the alternative is letting the exception escape the background action.
     */
    private fun <T> indexed(work: () -> T): T? = try {
        work()
    } catch (e: IndexNotReadyException) {
        null
    }

    private companion object {
        val INDEXING = FocusResult.Unavailable("the indexes are not ready yet")
    }
}
