package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import io.github.leolaurindo.tunnelvision.settings.TunnelVisionSettings
import io.github.leolaurindo.tunnelvision.source.SourceComputation
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns which editors have focus enabled and the refresh pipeline of each one.
 *
 * [activate], [deactivate] and [toggle] are called from the EDT; readers may come from any
 * thread, so the session map is concurrent.
 */
class TunnelVisionCore {

    /**
     * Matching implementation used by refreshes. Replaced by tests and by later stages; the
     * default honours the configured [io.github.leolaurindo.tunnelvision.settings.MatchSource].
     */
    @Volatile
    var computation: FocusComputation = SourceComputation()

    private val sessions = ConcurrentHashMap<Editor, FocusSession>()

    /** @return the state of the editor, reusing the one it already has. */
    fun activate(editor: Editor): EditorFocusState {
        sessions[editor]?.let { return it.state }

        val session = FocusSession(
            state = EditorFocusState(editor),
            settingsProvider = { TunnelVisionSettings.getInstance() },
            computationProvider = { computation },
        )
        sessions[editor] = session
        session.start()
        return session.state
    }

    /** Detaches the editor's listeners and cancels its pending and in-flight work. */
    fun deactivate(editor: Editor) {
        sessions.remove(editor)?.dispose()
    }

    /** @return whether focus is enabled after the toggle. */
    fun toggle(editor: Editor): Boolean {
        if (isActive(editor)) {
            deactivate(editor)
            return false
        }
        activate(editor)
        return true
    }

    /** Recomputes occurrences for an active editor without retargeting it. */
    fun refresh(editor: Editor) {
        sessions[editor]?.refreshNow()
    }

    /**
     * Recomputes every active editor, which is how a settings change reaches the editors that are
     * already focused.
     */
    fun refreshAll() {
        sessions.values.forEach { it.refreshNow() }
    }

    /** @return whether the caret moved to another match of an active editor. */
    fun jumpToMatch(editor: Editor, forward: Boolean): Boolean =
        sessions[editor]?.jumpToMatch(forward) ?: false

    fun isActive(editor: Editor): Boolean = sessions.containsKey(editor)

    fun stateOf(editor: Editor): EditorFocusState? = sessions[editor]?.state

    companion object {
        fun getInstance(): TunnelVisionCore =
            ApplicationManager.getApplication().getService(TunnelVisionCore::class.java)
    }
}
