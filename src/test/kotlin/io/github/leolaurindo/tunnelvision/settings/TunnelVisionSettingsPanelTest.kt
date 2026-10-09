package io.github.leolaurindo.tunnelvision.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import io.github.leolaurindo.tunnelvision.core.TunnelVisionCore
import javax.swing.JComboBox
import javax.swing.JLabel

/** Guards the settings page against dropping a setting on the way through its widgets. */
class TunnelVisionSettingsPanelTest : BasePlatformTestCase() {

    fun testRoundTripsEverySettingThroughTheControls() {
        val panel = TunnelVisionSettingsPanel()
        val state = TunnelVisionSettings.State(
            mode = FocusMode.DYNAMIC,
            source = MatchSource.WORD,
            scope = FocusScope.FUNCTION,
            debounceMillis = 400,
            maxFileLines = 1_000,
            areas = mutableListOf(HighlightArea.LINE, HighlightArea.SYMBOL),
        )

        panel.setState(state)

        assertEquals(state, panel.state())
    }

    fun testScopeIsVisibleOnlyForPsiIncludingWhenLoadingSavedWordSettings() {
        val panel = TunnelVisionSettingsPanel()
        val boxes = UIUtil.findComponentsOfType(panel.component, JComboBox::class.java)
        val source = boxes.single { it.selectedItem is MatchSource }
        val scope = boxes.single { it.selectedItem is FocusScope }
        val label = UIUtil.findComponentsOfType(panel.component, JLabel::class.java).single { it.text == "Scope:" }

        assertTrue(scope.isVisible)
        assertTrue(label.isVisible)

        source.selectedItem = MatchSource.WORD
        assertFalse(scope.isVisible)
        assertFalse(label.isVisible)

        source.selectedItem = MatchSource.PSI
        assertTrue(scope.isVisible)
        assertTrue(label.isVisible)
        assertEquals(FocusScope.FUNCTION, panel.state().scope)

        panel.setState(TunnelVisionSettings.State(source = MatchSource.WORD))
        assertFalse(scope.isVisible)
        assertFalse(label.isVisible)
        assertEquals(MatchSource.WORD, panel.state().source)
    }

    fun testDefaultsComeBackUnchanged() {
        val panel = TunnelVisionSettingsPanel()

        panel.setState(TunnelVisionSettings.State())

        assertEquals(TunnelVisionSettings.State(), panel.state())
    }

    fun testApplyingThePageRecomputesTheEditorsThatAreAlreadyFocused() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val core = TunnelVisionCore.getInstance()
        val editor = myFixture.editor
        val version = core.activate(editor).version
        val configurable = TunnelVisionConfigurable()
        configurable.createComponent()
        configurable.reset()

        configurable.apply()

        assertTrue(core.stateOf(editor)!!.version > version)
        configurable.disposeUIResources()
        core.deactivate(editor)
    }

    fun testThePageIsUnmodifiedUntilSomethingChanges() {
        val configurable = TunnelVisionConfigurable()
        configurable.createComponent()

        configurable.reset()

        assertFalse(configurable.isModified())
        configurable.disposeUIResources()
    }
}
