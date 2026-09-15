package io.github.leolaurindo.tunnelvision.source

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.TextRange

/**
 * Finds the symbol a refresh is anchored at, through language-neutral PSI APIs.
 *
 * A reference under the caret names the declaration it resolves to; a caret on a declaration
 * names the declaration itself. Nothing here knows about a specific language, so an unsupported
 * language simply fails to resolve.
 */
object TargetResolver {

    /** How far up the PSI tree a reference may sit from the leaf under the caret. */
    private const val MAX_PARENT_HOPS = 3

    /** The symbol to search for: its declaration, its name, and where the caret sits. */
    class Target(
        /** The declaration whose references are searched. */
        val element: PsiElement,
        val name: String,
        /** Exact range of the name of [element]. */
        val nameRange: TextRange,
        /** The element under the caret, which is what decides the enclosing scope. */
        val anchor: PsiElement,
    )

    sealed interface Resolution {

        /** [target] is the declaration to search references for. */
        data class Resolved(val target: Target) : Resolution

        /** Nothing named sits at the caret. */
        data object NoSymbol : Resolution

        /** A reference sits at the caret but does not resolve to a declaration. */
        data object UnresolvedReference : Resolution
    }

    fun resolve(file: PsiFile, offset: Int): Resolution {
        val onOffset = resolveAt(file, offset)
        if (onOffset !is Resolution.NoSymbol) return onOffset

        // The caret may sit right after the symbol, where the offset is already outside it.
        return if (offset > 0) resolveAt(file, offset - 1) else onOffset
    }

    private fun resolveAt(file: PsiFile, offset: Int): Resolution {
        val element = file.findElementAt(offset) ?: return Resolution.NoSymbol

        referenceAt(element, offset)?.let { reference ->
            val declaration = reference.resolve() ?: return Resolution.UnresolvedReference
            val name = (declaration as? PsiNamedElement)?.name ?: return Resolution.NoSymbol
            val nameRange = nameRangeOf(declaration, name) ?: declaration.textRange
            return Resolution.Resolved(Target(declaration, name, nameRange, reference.element))
        }

        val declaration = PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java, false)
            ?: return Resolution.NoSymbol
        val name = declaration.name ?: return Resolution.NoSymbol
        val nameRange = nameRangeOf(declaration, name) ?: return Resolution.NoSymbol

        // The caret must sit on the declaration's name, not elsewhere inside the declaration.
        return if (nameRange.containsOffset(offset)) {
            Resolution.Resolved(Target(declaration, name, nameRange, declaration))
        } else {
            Resolution.NoSymbol
        }
    }

    /** @return the innermost reference whose own range contains [offset], if any. */
    private fun referenceAt(element: PsiElement, offset: Int): PsiReference? {
        var current: PsiElement? = element
        repeat(MAX_PARENT_HOPS) {
            val candidate = current ?: return null
            val reference = candidate.reference
            val range = reference?.rangeInElement?.shiftRight(candidate.textRange.startOffset)
            if (reference != null && range != null && range.containsOffset(offset)) return reference

            current = candidate.parent
        }
        return null
    }

    /**
     * Range of the name of [declaration], preferring the language's own name identifier. Elements
     * of languages that do not expose one, such as JavaScript, fall back to the first occurrence
     * of the name inside the declaration.
     */
    private fun nameRangeOf(declaration: PsiElement, name: String): TextRange? {
        val nameIdentifier = (declaration as? PsiNameIdentifierOwner)?.nameIdentifier
        if (nameIdentifier != null) return nameIdentifier.textRange

        return Words.occurrences(declaration.text, name).firstOrNull()
            ?.shiftRight(declaration.textRange.startOffset)
    }
}
