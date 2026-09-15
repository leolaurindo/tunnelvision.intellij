package io.github.leolaurindo.tunnelvision.ui

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import java.awt.Color

/**
 * Color-scheme keys for the focus styles.
 *
 * The styles are resolved from the active scheme instead of being parsed from a plugin-specific
 * configuration, which is what lets users edit them in the native color settings.
 */
object FocusColors {

    val DIM: TextAttributesKey = TextAttributesKey.createTextAttributesKey("TUNNELVISION_DIM")
    val SCOPE_HEAD: TextAttributesKey = TextAttributesKey.createTextAttributesKey("TUNNELVISION_SCOPE_HEAD")
    val STATEMENT: TextAttributesKey = TextAttributesKey.createTextAttributesKey("TUNNELVISION_STATEMENT")
    val LINE: TextAttributesKey = TextAttributesKey.createTextAttributesKey("TUNNELVISION_LINE")
    val SYMBOL: TextAttributesKey = TextAttributesKey.createTextAttributesKey("TUNNELVISION_SYMBOL")

    fun keyOf(area: HighlightArea): TextAttributesKey = when (area) {
        HighlightArea.SCOPE_HEAD -> SCOPE_HEAD
        HighlightArea.STATEMENT -> STATEMENT
        HighlightArea.LINE -> LINE
        HighlightArea.SYMBOL -> SYMBOL
    }

    /**
     * @return the attributes [key] is styled with in [scheme], with any translucency blended into
     *   [background], or an empty [TextAttributes] when the scheme styles nothing with it.
     */
    fun resolve(key: TextAttributesKey, scheme: EditorColorsScheme, background: Color): TextAttributes =
        scheme.getAttributes(key).blendedInto(background)

    /**
     * IntelliJ paints highlighter colors opaquely, so opacity is simulated by blending the color
     * into the editor background.
     */
    fun blend(color: Color, background: Color): Color =
        if (color.alpha == OPAQUE) {
            color
        } else {
            Color(
                blend(color.red, background.red, color.alpha),
                blend(color.green, background.green, color.alpha),
                blend(color.blue, background.blue, color.alpha),
                OPAQUE,
            )
        }

    private const val OPAQUE = 255

    private fun blend(channel: Int, background: Int, alpha: Int): Int =
        (channel * alpha + background * (OPAQUE - alpha)) / OPAQUE

    private fun TextAttributes.blendedInto(background: Color): TextAttributes {
        if (isEmpty) return this

        val blended = clone()
        blended.foregroundColor = blended.foregroundColor?.let { blend(it, background) }
        blended.backgroundColor = blended.backgroundColor?.let { blend(it, background) }
        blended.effectColor = blended.effectColor?.let { blend(it, background) }
        return blended
    }
}
