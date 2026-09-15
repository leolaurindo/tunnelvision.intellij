package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Geometry helpers shared by the structural adapters.
 *
 * A scope head is what a construct shows before its body, so it is expressed as the range between
 * two PSI elements rather than as a list of language-specific node types.
 */
internal fun headRange(construct: PsiElement, body: PsiElement): TextRange {
    val start = construct.textRange.startOffset
    return TextRange(start, body.textRange.startOffset.coerceAtLeast(start))
}

/**
 * Text between two sibling constructs.
 *
 * `else` and `finally` clauses have no PSI element of their own, so their head is what lies
 * between the previous branch and the next one.
 */
internal fun betweenRange(before: PsiElement, after: PsiElement): TextRange {
    val start = before.textRange.endOffset
    return TextRange(start, after.textRange.startOffset.coerceAtLeast(start))
}

/** @return whether [inner] sits inside [outer]. */
internal fun isInside(inner: PsiElement, outer: PsiElement): Boolean =
    outer.textRange.contains(inner.textRange)
