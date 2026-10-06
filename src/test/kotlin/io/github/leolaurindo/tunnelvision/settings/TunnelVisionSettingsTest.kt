package io.github.leolaurindo.tunnelvision.settings

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelVisionSettingsTest {

    @Test
    fun `defaults retain statements and highlight symbols`() {
        val state = TunnelVisionSettings.State()

        assertEquals(FocusMode.STATIC, state.mode)
        assertEquals(MatchSource.PSI, state.source)
        assertEquals(FocusScope.FUNCTION, state.scope)
        assertEquals(TunnelVisionSettings.DEFAULT_DEBOUNCE_MILLIS, state.debounceMillis)
        assertEquals(TunnelVisionSettings.DEFAULT_MAX_FILE_LINES, state.maxFileLines)
        assertEquals(listOf(HighlightArea.STATEMENT, HighlightArea.SYMBOL), state.areas)
    }

    @Test
    fun `loadState clamps out of range values`() {
        val settings = TunnelVisionSettings()
        settings.loadState(
            TunnelVisionSettings.State(
                debounceMillis = -10,
                maxFileLines = 1,
            )
        )

        assertEquals(0, settings.state.debounceMillis)
        assertEquals(100, settings.state.maxFileLines)
    }

    @Test
    fun `loadState keeps disabled areas disabled`() {
        val settings = TunnelVisionSettings()
        settings.loadState(TunnelVisionSettings.State(areas = mutableListOf(HighlightArea.LINE)))

        assertTrue(settings.isAreaEnabled(HighlightArea.LINE))
        assertEquals(false, settings.isAreaEnabled(HighlightArea.SYMBOL))
    }

    @Test
    fun `state survives an xml round trip`() {
        val original = TunnelVisionSettings.State(
            mode = FocusMode.DYNAMIC,
            source = MatchSource.WORD,
            debounceMillis = 400,
            maxFileLines = 1_000,
            areas = mutableListOf(HighlightArea.SYMBOL, HighlightArea.STATEMENT),
        )

        val restored = XmlSerializer.deserialize(
            XmlSerializer.serialize(original),
            TunnelVisionSettings.State::class.java,
        )

        assertEquals(original, restored)
    }
}
