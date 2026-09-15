package io.github.leolaurindo.tunnelvision.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import io.github.leolaurindo.tunnelvision.core.TunnelVisionCore

class ToggleFocusAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = editorOf(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = editorOf(event) ?: return
        TunnelVisionCore.getInstance().toggle(editor)
    }

    companion object {
        fun editorOf(event: AnActionEvent): Editor? = event.getData(CommonDataKeys.EDITOR)
    }
}
