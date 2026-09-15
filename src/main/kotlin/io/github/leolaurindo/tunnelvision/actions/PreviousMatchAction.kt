package io.github.leolaurindo.tunnelvision.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.leolaurindo.tunnelvision.core.TunnelVisionCore

/** Moves the caret to the previous matched line, wrapping around at the start of the file. */
class PreviousMatchAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val editor = ToggleFocusAction.editorOf(event)
        event.presentation.isEnabled = editor != null && TunnelVisionCore.getInstance().isActive(editor)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = ToggleFocusAction.editorOf(event) ?: return
        TunnelVisionCore.getInstance().jumpToMatch(editor, forward = false)
    }
}
