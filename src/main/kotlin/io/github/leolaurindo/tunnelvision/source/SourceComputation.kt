package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.editor.Editor
import io.github.leolaurindo.tunnelvision.core.FocusComputation
import io.github.leolaurindo.tunnelvision.core.FocusRequest
import io.github.leolaurindo.tunnelvision.core.FocusResult
import io.github.leolaurindo.tunnelvision.settings.MatchSource

/** Routes a refresh to the implementation of the configured [MatchSource]. */
class SourceComputation(
    private val psi: FocusComputation = PsiSource(),
    private val word: FocusComputation = WordSource(),
) : FocusComputation {

    override fun compute(editor: Editor, request: FocusRequest): FocusResult = when (request.source) {
        MatchSource.PSI -> psi.compute(editor, request)
        MatchSource.WORD -> word.compute(editor, request)
    }
}
