package io.github.leolaurindo.tunnelvision.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.leolaurindo.tunnelvision.core.TunnelVisionCore

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
