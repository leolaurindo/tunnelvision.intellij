package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import io.github.leolaurindo.tunnelvision.core.FocusComputation
import io.github.leolaurindo.tunnelvision.core.FocusRequest
import io.github.leolaurindo.tunnelvision.core.FocusResult

/**
 * Lexical occurrences of the editor word at the caret, over the complete document.
 *
 * Deliberately ignores PSI and the configured scope: occurrences in comments and string literals
 * count, and so do occurrences outside the enclosing function. Statements and scope heads need
 * PSI, so this source stops at the symbol and line areas.
 */
class WordSource : FocusComputation {

    override fun compute(editor: Editor, request: FocusRequest): FocusResult {
        val document = editor.document
        val text = document.immutableCharSequence
        val word = Words.wordAt(text, request.caretOffset)
            ?: return FocusResult.Unavailable("no word at the caret")

        ProgressManager.checkCanceled()
        val occurrences = Words.occurrences(text, word)
        return FocusResult.Matched(word, Areas.ofDocument(document, occurrences, request.areas))
    }
}
