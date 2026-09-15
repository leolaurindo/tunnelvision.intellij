package io.github.leolaurindo.tunnelvision.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.leolaurindo.tunnelvision.core.TunnelVisionCore

class DisableFocusAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val editor = ToggleFocusAction.editorOf(event)
        val core = TunnelVisionCore.getInstance()
        event.presentation.isEnabled = editor != null && core.isActive(editor)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = ToggleFocusAction.editorOf(event) ?: return
        TunnelVisionCore.getInstance().deactivate(editor)
    }
}
