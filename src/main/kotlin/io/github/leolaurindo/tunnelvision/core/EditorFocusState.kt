package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer

/**
 * Focus state for a single editor. One instance per editor that currently has focus enabled.
 */
class EditorFocusState(val editor: Editor) {

    /** Parent of the listeners, alarms and background tasks owned by this state. */
    val lifetime: CheckedDisposable = Disposer.newCheckedDisposable("TunnelVision.EditorFocusState")

    /** Version of the last accepted refresh; results carrying an older version are stale. */
    var version: Long = 0
        private set

    /** Offset the symbol is selected at. Static focus keeps it, dynamic focus moves it. */
    var anchorOffset: Int = editor.caretModel.offset

    /** Last accepted result, or null while nothing has been computed yet. */
    var result: FocusResult? = null

    val isDisposed: Boolean
        get() = lifetime.isDisposed

    fun nextVersion(): Long = ++version

    /** Detaches the editor listeners and cancels pending and in-flight work. */
    fun dispose() {
        Disposer.dispose(lifetime)
    }
}
