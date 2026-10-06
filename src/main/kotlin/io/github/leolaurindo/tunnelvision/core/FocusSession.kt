package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.ProgressManager
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
    private var dynamicOffset: Int? = null
    private val activations = mutableListOf<Activation>()

    private data class Activation(val offset: Int, val mode: FocusMode, val replace: Boolean)

    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            // Static focus keeps the symbol selected on activation; only dynamic focus retargets.
            if (state.tracks.none { it.mode == FocusMode.DYNAMIC }) return
            dynamicOffset = state.editor.caretModel.offset
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
            state.tracks.forEach { it.anchorOffset = event.shiftAnchor(it.anchorOffset) }
            dynamicOffset = dynamicOffset?.let { event.shiftAnchor(it) }
            activations.replaceAll { it.copy(offset = event.shiftAnchor(it.offset)) }
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
    fun start(pin: Boolean = false) {
        val mode = if (pin) FocusMode.STATIC else settingsProvider().state.mode
        state.tracks.clear()
        state.tracks += FocusTrack(state.editor.caretModel.offset, mode)
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
     * Moves the caret to the next or previous occurrence across all tracks, wrapping around.
     *
     * @return whether the caret moved.
     */
    fun jumpToMatch(forward: Boolean): Boolean {
        val matched = state.result as? FocusResult.Matched ?: return false

        val document = state.editor.document
        val offsets = matched.areas.ranges(HighlightArea.SYMBOL)
            .filter { it.endOffset <= document.textLength }
            .map { it.startOffset }.distinct().sorted()
        if (offsets.isEmpty()) return false

        val caretOffset = state.editor.caretModel.offset
        val offset = if (forward) {
            offsets.firstOrNull { it > caretOffset } ?: offsets.first()
        } else {
            offsets.lastOrNull { it < caretOffset } ?: offsets.last()
        }

        state.editor.caretModel.moveToOffset(offset)
        return true
    }

    /** Recomputes every track and any candidate target as one cancellable generation. */
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

        debouncer.cancel()
        val computation = computationProvider()
        val version = state.nextVersion()
        val tracks = state.tracks.toList()
        val candidates = activations.toList()
        val movingOffset = dynamicOffset
        val requests = tracks.map { request(version, it.anchorOffset, it.mode, settings) } +
            candidates.map { request(version, it.offset, it.mode, settings) } +
            listOfNotNull(movingOffset?.let { request(version, it, FocusMode.DYNAMIC, settings) })

        runner.run(state, {
            FocusResult.Batch(requests.map {
                ProgressManager.checkCanceled()
                computation.compute(state.editor, it)
            })
        }) { batch ->
            if (version != state.version || state.isDisposed) return@run
            val results = (batch as FocusResult.Batch).results
            tracks.zip(results).forEach { (track, result) -> track.result = result }
            candidates.forEachIndexed { index, candidate ->
                val extra = results[tracks.size + index]
                if (extra is FocusResult.Matched) {
                    val duplicate = if (candidate.replace) null else state.tracks.firstOrNull {
                        it.mode == candidate.mode && (it.result as? FocusResult.Matched)?.identity == extra.identity
                    }
                    if (candidate.replace) state.tracks.clear()
                    if (duplicate == null) {
                        if (candidate.mode == FocusMode.DYNAMIC) {
                            state.tracks.removeAll { it.mode == FocusMode.DYNAMIC }
                        }
                        state.tracks += FocusTrack(candidate.offset, candidate.mode).also { it.result = extra }
                    }
                } else if (extra is FocusResult.Unavailable && extra.report) {
                    report(extra.reason)
                }
            }
            val moving = state.tracks.firstOrNull { it.mode == FocusMode.DYNAMIC }
            val retargeted = results.getOrNull(tracks.size + candidates.size)
            if (moving != null && retargeted is FocusResult.Matched) {
                moving.anchorOffset = checkNotNull(movingOffset)
                moving.result = retargeted
            }
            activations.clear()
            dynamicOffset = null
            publish()
        }
    }

    private fun request(version: Long, offset: Int, mode: FocusMode, settings: TunnelVisionSettings): FocusRequest =
        FocusRequest(version, offset, mode, settings.state.source, settings.state.scope, settings.state.areas.toSet())

    /** Resolve a new target before replacing or appending tracks. Invalid activation preserves them. */
    fun activate(replace: Boolean = false, pin: Boolean = false) {
        activations += Activation(
            state.editor.caretModel.offset,
            if (pin) FocusMode.STATIC else settingsProvider().state.mode,
            replace,
        )
        dynamicOffset = null
        refreshNow()
    }

    /** Removes the newest track at the caret, or the newest track when away from occurrences. */
    fun remove(): Boolean {
        val offset = state.editor.caretModel.offset
        val tracked = state.tracks.lastOrNull {
            val ranges = (it.result as? FocusResult.Matched)?.areas?.ranges(HighlightArea.SYMBOL).orEmpty()
            it.anchorOffset == offset || ranges.any { range -> range.containsOffset(offset) }
        }
        val pending = activations.lastOrNull { it.offset == offset }
            ?: if (tracked == null) activations.lastOrNull() else null
        if (pending != null) {
            activations.remove(pending)
        } else {
            val track = tracked ?: state.tracks.lastOrNull() ?: return false
            state.tracks.remove(track)
        }
        state.nextVersion()
        debouncer.cancel()
        dynamicOffset = null
        publish()
        val active = state.tracks.isNotEmpty() || activations.isNotEmpty()
        if (active) refreshNow()
        return active
    }

    fun dispose() {
        renderer.clear()
        activations.clear()
        dynamicOffset = null
        state.tracks.clear()
        state.result = null
        state.dispose()
    }

    private fun publish() {
        val matches = state.tracks.mapNotNull { it.result as? FocusResult.Matched }
        val result = when (matches.size) {
            0 -> state.tracks.mapNotNull { it.result as? FocusResult.Unavailable }.firstOrNull()
                ?: FocusResult.Unavailable("no tracked symbols")
            1 -> matches.single()
            else -> FocusResult.Matched(
                matches.joinToString { it.symbol }, FocusAreas.combine(matches.map { it.areas }),
            )
        }
        state.result = result
        render(result)
    }

    private fun render(result: FocusResult) {
        when (result) {
            is FocusResult.Matched -> {
                reported = false
                renderer.render(result.areas, settingsProvider().state.areas.toSet())
            }

            is FocusResult.Batch -> error("a batch must be combined before rendering")

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
