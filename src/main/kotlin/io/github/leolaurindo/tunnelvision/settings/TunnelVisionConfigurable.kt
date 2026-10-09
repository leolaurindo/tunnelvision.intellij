package io.github.leolaurindo.tunnelvision.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import io.github.leolaurindo.tunnelvision.core.TunnelVisionCore
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/** Settings page for the focus behavior. */
class TunnelVisionConfigurable : Configurable {

    private val panel = TunnelVisionSettingsPanel()

    override fun getDisplayName(): String = "TunnelVision"

    override fun createComponent(): JComponent = panel.component

    override fun isModified(): Boolean = panel.state() != TunnelVisionSettings.getInstance().state

    override fun apply() {
        // loadState is also what keeps out-of-range values from being persisted.
        TunnelVisionSettings.getInstance().loadState(panel.state())
        // Focus may be running right now, so the new configuration has to reach open editors.
        TunnelVisionCore.getInstance().refreshAll()
    }

    override fun reset() {
        panel.setState(TunnelVisionSettings.getInstance().state)
    }
}

/**
 * The page controls, kept apart from the [Configurable] so a test can round-trip every setting
 * through the widgets the user sees.
 */
internal class TunnelVisionSettingsPanel {

    private val mode = ComboBox(FocusMode.entries.toTypedArray())
    private val source = ComboBox(MatchSource.entries.toTypedArray())
    private val scope = ComboBox(FocusScope.entries.toTypedArray())
    private val scopeLabel = JLabel("Scope:").apply { labelFor = scope }
    private val areas = HighlightArea.entries.associateWith { JCheckBox(it.displayName) }
    private val debounceMillis = JBIntSpinner(
        TunnelVisionSettings.DEFAULT_DEBOUNCE_MILLIS,
        0,
        MAX_DEBOUNCE_MILLIS,
    )
    private val maxFileLines = JBIntSpinner(
        TunnelVisionSettings.DEFAULT_MAX_FILE_LINES,
        MIN_MAX_FILE_LINES,
        MAX_MAX_FILE_LINES,
    )

    val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Mode for new tracks:", mode)
        .addLabeledComponent("Occurrences from:", source)
        .addLabeledComponent(scopeLabel, scope)
        .addLabeledComponent("Highlight areas:", areaBoxes())
        .addLabeledComponent("Refresh after (ms):", debounceMillis)
        .addLabeledComponent("Leave files longer than (lines) alone:", maxFileLines)
        .addComponentFillVertically(JPanel(), 0)
        .panel
        .apply { border = JBUI.Borders.empty(10) }

    init {
        source.addActionListener {
            val showScope = source.item == MatchSource.PSI
            scopeLabel.isVisible = showScope
            scope.isVisible = showScope
            component.revalidate()
            component.repaint()
        }
    }

    fun state(): TunnelVisionSettings.State = TunnelVisionSettings.State(
        mode = mode.item as FocusMode,
        source = source.item as MatchSource,
        scope = scope.item as FocusScope,
        debounceMillis = debounceMillis.number,
        maxFileLines = maxFileLines.number,
        areas = areas.filterValues { it.isSelected }.keys.toMutableList(),
    )

    fun setState(state: TunnelVisionSettings.State) {
        mode.item = state.mode
        source.item = state.source
        scope.item = state.scope
        debounceMillis.number = state.debounceMillis
        maxFileLines.number = state.maxFileLines
        for ((area, checkbox) in areas) checkbox.isSelected = area in state.areas
    }

    private fun areaBoxes(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        areas.values.forEach { add(it) }
    }

    private companion object {
        const val MAX_DEBOUNCE_MILLIS = 5_000
        const val MIN_MAX_FILE_LINES = 100
        const val MAX_MAX_FILE_LINES = 1_000_000
    }
}
