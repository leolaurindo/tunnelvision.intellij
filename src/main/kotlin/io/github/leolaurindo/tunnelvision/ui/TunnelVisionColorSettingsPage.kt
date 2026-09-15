package io.github.leolaurindo.tunnelvision.ui

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import javax.swing.Icon

/**
 * Color page for the five focus styles.
 *
 * The styles themselves are ordinary scheme attributes, so this page is the only UI for them and
 * the defaults ship as `additionalTextAttributes` fragments next to it.
 */
class TunnelVisionColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = "TunnelVision"

    override fun getIcon(): Icon? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> =
        HighlightArea.entries.map { AttributesDescriptor(it.displayName, FocusColors.keyOf(it)) }
            .plus(AttributesDescriptor("Dimmed text", FocusColors.DIM))
            .toTypedArray()

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

    override fun getDemoText(): String = """
        class Sample {
            <scope_head>void run() {</scope_head>
                <statement>int count = 0;</statement>
                <line>count++;</line>
                System.out.println(<symbol>count</symbol>);
                <dim>// everything outside the areas above is dimmed</dim>
            }
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "dim" to FocusColors.DIM,
        "scope_head" to FocusColors.SCOPE_HEAD,
        "statement" to FocusColors.STATEMENT,
        "line" to FocusColors.LINE,
        "symbol" to FocusColors.SYMBOL,
    )
}
