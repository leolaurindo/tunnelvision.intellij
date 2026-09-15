package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.leolaurindo.tunnelvision.source.adapters.JavaScriptStructuralAdapter
import io.github.leolaurindo.tunnelvision.source.adapters.JavaStructuralAdapter
import io.github.leolaurindo.tunnelvision.source.adapters.KotlinStructuralAdapter

/**
 * Language-specific structural knowledge: which element scopes a symbol, which statement contains
 * it, and which scope heads stay visible around it.
 *
 * Adapters are chosen by language id, so the classes of an optional language plugin are loaded
 * only when one of its elements is focused. A plugin that is not installed never gets that far.
 */
interface StructuralAdapter {

    /** Nearest enclosing named function of [element], including [element] itself. */
    fun enclosingFunction(element: PsiElement): PsiElement?

    /**
     * Nearest supported statement containing [element]: a declaration, an assignment, an
     * expression statement, a return, a throw, or the branch clause of a control construct.
     *
     * @return null when the language has no supported statement there, in which case the broader
     *   areas still cover the occurrence.
     */
    fun statement(element: PsiElement): PsiElement?

    /** Head ranges of the constructs enclosing [element], their bodies excluded, innermost first. */
    fun scopeHeads(element: PsiElement): List<TextRange>

    companion object {

        /** @return the adapter for the language of [element], or null when it is unsupported. */
        fun forElement(element: PsiElement): StructuralAdapter? = when (element.language.id) {
            "JAVA" -> JavaStructuralAdapter
            "kotlin" -> KotlinStructuralAdapter
            // JavaScript and TypeScript share one adapter, and one file can hold several languages.
            "JavaScript", "ECMAScript 6", "TypeScript" -> JavaScriptStructuralAdapter
            else -> null
        }
    }
}
