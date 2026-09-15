package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import io.github.leolaurindo.tunnelvision.settings.FocusMode
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import io.github.leolaurindo.tunnelvision.settings.TunnelVisionSettings
import io.github.leolaurindo.tunnelvision.ui.BalloonFocusNotifier
import io.github.leolaurindo.tunnelvision.ui.FocusNotifier
import io.github.leolaurindo.tunnelvision.ui.FocusRenderer

/**
 * Refresh pipeline for one focused editor.
 *
 * Owns the caret and document listeners, the debounce timer and the background matching pass.
 * Every one of them is bound to [EditorFocusState.lifetime], so disposing the state detaches the
 * listeners and cancels pending and in-flight work. The session is confined to the EDT.
 */
class FocusSession(
    val state: EditorFocusState,
    private val settingsProvider: () -> TunnelVisionSettings,
    private val computationProvider: () -> FocusComputation,
    private val debouncer: Debouncer = AlarmDebouncer(state.lifetime),
    private val runner: RefreshRunner = ReadActionRefreshRunner(),
    private val renderer: FocusRenderer = FocusRenderer(state.editor, state.lifetime),
    private val notifier: FocusNotifier = BalloonFocusNotifier(),
) {

    /** Whether the reason the source cannot answer has already been reported. */
    private var reported = false

    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            // Static focus keeps the symbol selected on activation; only dynamic focus retargets.
            if (settingsProvider().state.mode != FocusMode.DYNAMIC) return
            state.anchorOffset = state.editor.caretModel.offset
            // The anchor moved, so a result that is still in flight describes the old symbol and
            // must not survive the debounce.
            state.nextVersion()
            schedule()
        }
    }

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            // The anchor follows the text it was placed on, so a static symbol survives edits
            // elsewhere in the file instead of drifting onto whatever moved into its offset.
            state.anchorOffset = event.shiftAnchor(state.anchorOffset)
            // The text an in-flight result was computed against is already gone, so drop it.
            state.nextVersion()
            schedule()
        }
    }

    /**
     * @return [anchor] as it is after [event]: text before it shifts by the length delta, an
     *   insertion at it moves it along with the text it was on, and an edit that covers it leaves
     *   it at the edit.
     */
    private fun DocumentEvent.shiftAnchor(anchor: Int): Int {
        val end = offset + oldLength
        return when {
            anchor < offset -> anchor
            anchor >= end -> anchor + newLength - oldLength
            else -> offset
        }
    }

    /** Starts listening and computes the first result for the caret position. */
    fun start() {
        state.editor.caretModel.addCaretListener(caretListener, state.lifetime)
        state.editor.document.addDocumentListener(documentListener, state.lifetime)
        refreshNow()
    }

    /** Debounced refresh. Supersedes a pending one, so a burst of events computes once. */
    fun schedule() {
        debouncer.cancel()
        debouncer.schedule(settingsProvider().state.debounceMillis, Runnable { refreshNow() })
    }

    /**
     * Moves the caret to the next or previous matched line, wrapping around, and lands on the
     * occurrence when that line has one.
     *
     * @return whether the caret moved.
     */
    fun jumpToMatch(forward: Boolean): Boolean {
        val matched = state.result as? FocusResult.Matched ?: return false

        val document = state.editor.document
        val occurrences = matched.areas.ranges(HighlightArea.SYMBOL)
            .filter { it.endOffset <= document.textLength }
        val lines = occurrences.map { document.getLineNumber(it.startOffset) }.distinct().sorted()
        if (lines.isEmpty()) return false

        val caretLine = document.getLineNumber(state.editor.caretModel.offset)
        val target = if (forward) {
            lines.firstOrNull { it > caretLine } ?: lines.first()
        } else {
            lines.lastOrNull { it < caretLine } ?: lines.last()
        }
        val offset = occurrences.firstOrNull { document.getLineNumber(it.startOffset) == target }?.startOffset
            ?: document.getLineStartOffset(target)

        state.editor.caretModel.moveToOffset(offset)
        return true
    }

    /** Recomputes occurrences for [EditorFocusState.anchorOffset] right now. */
    fun refreshNow() {
        if (state.isDisposed) return
        val settings = settingsProvider()
        val limit = settings.state.maxFileLines
        val lineCount = state.editor.document.lineCount
        if (lineCount > limit) {
            // Guard: a file this large costs more to search than the focus is worth, so the
            // configured limit is reported instead of being silently ignored.
            fail("the file has $lineCount lines, above the configured limit of $limit")
            return
        }

        val computation = computationProvider()
        val request = FocusRequest(
            version = state.nextVersion(),
            caretOffset = state.anchorOffset,
            mode = settings.state.mode,
            source = settings.state.source,
            scope = settings.state.scope,
            areas = settings.state.areas.toSet(),
        )
        runner.run(state, { computation.compute(state.editor, request) }) { result ->
            accept(request, result)
        }
    }

    fun dispose() {
        renderer.clear()
        state.dispose()
    }

    private fun accept(request: FocusRequest, result: FocusResult) {
        // A newer request, a released editor or a disposed session all make this result stale.
        if (request.version != state.version || state.isDisposed) return
        state.result = result
        render(result)
    }

    private fun render(result: FocusResult) {
        when (result) {
            is FocusResult.Matched -> {
                reported = false
                renderer.render(result.areas, settingsProvider().state.areas.toSet())
            }

            is FocusResult.Unavailable -> {
                renderer.clear()
                // Only the reasons the user has to act on are worth a balloon.
                if (result.report) report(result.reason)
            }
        }
    }

    /** Reports a source that cannot answer, once, until it can answer again. */
    private fun report(reason: String) {
        if (reported) return
        reported = true
        notifier.report(reason)
    }

    private fun fail(reason: String) {
        // A new version drops results that are still in flight, so the editor stays undecorated.
        state.nextVersion()
        state.result = FocusResult.Unavailable(reason, report = true)
        renderer.clear()
        report(reason)
    }
}
