package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.editor.Editor
import io.github.leolaurindo.tunnelvision.settings.FocusMode
import io.github.leolaurindo.tunnelvision.settings.FocusScope
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import io.github.leolaurindo.tunnelvision.settings.MatchSource

/** What one refresh asks for: the anchor to match from and the configuration it must honour. */
data class FocusRequest(
    val version: Long,
    val caretOffset: Int,
    val mode: FocusMode,
    val source: MatchSource,
    val scope: FocusScope,
    /** Areas the user enabled; a source skips the work of the areas that are not asked for. */
    val areas: Set<HighlightArea>,
)

/** Occurrences computed for one [FocusRequest]. */
sealed interface FocusResult {

    /** [symbol] resolved to [areas], in document order. */
    data class Matched(val symbol: String, val areas: FocusAreas) : FocusResult

    /**
     * The configured source cannot answer. [reason] explains why, and [report] marks the reasons
     * the user has to act on: the caret resting between symbols, or code that does not resolve
     * yet, is ordinary and would only be noise.
     */
    data class Unavailable(val reason: String, val report: Boolean = false) : FocusResult
}

/**
 * One matching pass.
 *
 * Runs on a background thread inside a cancellable read action: implementations must not touch
 * Swing state, must read the document and PSI only through the given [editor], and should call
 * `ProgressManager.checkCanceled()` between steps.
 */
fun interface FocusComputation {
    fun compute(editor: Editor, request: FocusRequest): FocusResult
}
