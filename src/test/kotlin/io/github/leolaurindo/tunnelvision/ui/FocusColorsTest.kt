package io.github.leolaurindo.tunnelvision.ui

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
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

    fun testMissingStylesStillDimAndHighlightWithoutReplacingSymbolForeground() {
        // Model a scheme that supplies no plugin key defaults, rather than inheriting fragments.
        val scheme = EditorColorsSchemeImpl(object : DefaultColorsScheme() {
            override fun getKeyDefaults(key: TextAttributesKey): TextAttributes? = null
        })
        scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color.BLACK, Color.WHITE, null, null, 0))

        val dim = FocusColors.resolve(FocusColors.DIM, scheme, Color.WHITE)
        val symbol = FocusColors.resolve(FocusColors.SYMBOL, scheme, Color.WHITE)

        assertEquals(Color(145, 145, 145), dim.foregroundColor)
        assertNotNull(symbol.backgroundColor)
        assertNull(symbol.foregroundColor)
        assertEquals(0, symbol.fontType)

        scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color.WHITE, Color.BLACK, null, null, 0))
        assertEquals(Color(110, 110, 110), FocusColors.resolve(FocusColors.DIM, scheme, Color.BLACK).foregroundColor)
    }

    fun testMissingSymbolStyleUsesSearchBackgroundButNotItsForeground() {
        val scheme = EditorColorsSchemeImpl(object : DefaultColorsScheme() {
            override fun getKeyDefaults(key: TextAttributesKey): TextAttributes? = null
        })
        val searchBackground = Color(80, 90, 100)
        scheme.setAttributes(
            EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES,
            TextAttributes(Color.RED, searchBackground, null, null, 1),
        )

        val symbol = FocusColors.resolve(FocusColors.SYMBOL, scheme, Color.BLACK)

        assertEquals(searchBackground, symbol.backgroundColor)
        assertNull(symbol.foregroundColor)
        assertEquals(0, symbol.fontType)
    }

    fun testExplicitEmptyStylesDisableBothDefaultEffects() {
        val scheme = EditorColorsSchemeImpl(DefaultColorsScheme())
        scheme.setAttributes(FocusColors.DIM, TextAttributes())
        scheme.setAttributes(FocusColors.SYMBOL, TextAttributes())

        assertTrue(FocusColors.resolve(FocusColors.DIM, scheme, Color.WHITE).isEmpty)
        assertTrue(FocusColors.resolve(FocusColors.SYMBOL, scheme, Color.WHITE).isEmpty)
    }

    /** Resolves against a copy, so the shared scheme keeps whatever the user configured. */
    private fun resolve(color: Color, background: Color = Color.WHITE): TextAttributes {
        val scheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        scheme.setAttributes(key, TextAttributes(color, null, null, null, 0))
        return FocusColors.resolve(key, scheme, background)
    }
}
