package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

/** Releases focus state when an editor goes away, including when its project closes. */
class EditorReleaseListener : EditorFactoryListener {

    override fun editorReleased(event: EditorFactoryEvent) {
        TunnelVisionCore.getInstance().deactivate(event.editor)
    }
}
