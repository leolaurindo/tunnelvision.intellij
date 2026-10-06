package io.github.leolaurindo.tunnelvision.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.leolaurindo.tunnelvision.settings.HighlightArea

class TunnelVisionColorSettingsPageTest : BasePlatformTestCase() {

    private val page = TunnelVisionColorSettingsPage()

    fun testEveryStyleHasItsOwnDescriptor() {
        val keys = page.attributeDescriptors.map { it.key }

        assertEquals(HighlightArea.entries.size + 1, keys.size)
        assertEquals(keys.distinct(), keys)
        for (area in HighlightArea.entries) {
            assertTrue("no descriptor for $area", FocusColors.keyOf(area) in keys)
        }
        assertTrue(FocusColors.DIM in keys)
    }

    fun testDemoTextTagsCoverEveryDescriptor() {
        val keys = page.attributeDescriptors.map { it.key }.toSet()

        assertEquals(keys, page.additionalHighlightingTagToDescriptorMap.values.toSet())
    }

    fun testTheShippedDefaultsStyleTheDimAndTheSymbol() {
        val scheme = EditorColorsManager.getInstance().getScheme(EditorColorsManager.DEFAULT_SCHEME_NAME)

        assertNotNull("the Default scheme must be loaded", scheme)
        assertFalse(scheme!!.getAttributes(FocusColors.DIM).isEmpty)
        val symbol = scheme.getAttributes(FocusColors.SYMBOL)
        assertNotNull(symbol.backgroundColor)
        assertNull("the default symbol style must preserve syntax foreground", symbol.foregroundColor)
        assertEquals(0, symbol.fontType)
    }

    fun testTheOtherShippedAreasAreKeptReadableWithoutBeingRecolored() {
        val scheme = EditorColorsManager.getInstance().getScheme(EditorColorsManager.DEFAULT_SCHEME_NAME)!!

        // Like the Neovim reference, a context is on by default to stay undimmed, and styling it
        // is the user's choice, so these carry no attributes out of the box.
        for (area in listOf(HighlightArea.SCOPE_HEAD, HighlightArea.STATEMENT, HighlightArea.LINE)) {
            assertTrue(area.toString(), scheme.getAttributes(FocusColors.keyOf(area)).isEmpty)
        }
    }
}
