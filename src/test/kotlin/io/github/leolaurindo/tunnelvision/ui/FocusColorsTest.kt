package io.github.leolaurindo.tunnelvision.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import java.awt.Color

class FocusColorsTest : BasePlatformTestCase() {

    private val key = TextAttributesKey.createTextAttributesKey("TUNNELVISION_TEST")

    fun testOpaqueColorsArePaintedAsTheyAre() {
        val attributes = resolve(Color(10, 20, 30))

        assertEquals(Color(10, 20, 30), attributes.foregroundColor)
    }

    fun testTranslucentColorsAreBlendedIntoTheEditorBackground() {
        // Half of the color over white, because IntelliJ paints highlighter colors opaquely.
        val attributes = resolve(Color(0, 0, 0, 128), background = Color(255, 255, 255))

        assertEquals(Color(127, 127, 127), attributes.foregroundColor)
    }

    fun testEveryAreaHasItsOwnKey() {
        val keys = HighlightArea.entries.map { FocusColors.keyOf(it) }

        assertEquals(HighlightArea.entries.size, keys.distinct().size)
    }

    /** Resolves against a copy, so the shared scheme keeps whatever the user configured. */
    private fun resolve(color: Color, background: Color = Color.WHITE): TextAttributes {
        val scheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        scheme.setAttributes(key, TextAttributes(color, null, null, null, 0))
        return FocusColors.resolve(key, scheme, background)
    }
}
