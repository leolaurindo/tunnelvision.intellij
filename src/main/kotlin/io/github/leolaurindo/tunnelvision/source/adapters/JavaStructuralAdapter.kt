package io.github.leolaurindo.tunnelvision.source.adapters

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiCatchSection
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLoopStatement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiSwitchStatement
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.util.PsiTreeUtil
import io.github.leolaurindo.tunnelvision.source.StructuralAdapter
import io.github.leolaurindo.tunnelvision.source.betweenRange
import io.github.leolaurindo.tunnelvision.source.headRange
import io.github.leolaurindo.tunnelvision.source.isInside

/**
 * Java scope: the nearest enclosing method.
 *
 * Lambdas and anonymous classes are deferred, so the search keeps the named method that contains
 * them instead of guessing a narrower scope. Statements are PSI statements, and the scope heads
 * are the methods, branches, loops, switches and try clauses around the occurrence.
 */
object JavaStructuralAdapter : StructuralAdapter {

    override fun enclosingFunction(element: PsiElement): PsiElement? =
        PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)

    override fun statement(element: PsiElement): PsiElement? =
        // PsiCodeBlock is not a PsiStatement, so blocks never pose as statements.
        PsiTreeUtil.getParentOfType(element, PsiStatement::class.java)

    override fun scopeHeads(element: PsiElement): List<TextRange> {
        val heads = mutableListOf<TextRange>()
        var current: PsiElement? = element

        while (current != null) {
            when (current) {
                is PsiMethod -> current.body?.let { heads += headRange(current, it) }

                is PsiIfStatement -> {
                    current.thenBranch?.let { heads += headRange(current, it) }
                    val elseBranch = current.elseBranch
                    val elseKeyword = current.elseElement
                    if (elseBranch != null && elseKeyword != null && isInside(element, elseBranch)) {
                        heads += headRange(elseKeyword, elseBranch)
                    }
                }

                is PsiLoopStatement -> current.body?.let { heads += headRange(current, it) }

                is PsiSwitchStatement -> current.body?.let { heads += headRange(current, it) }

                is PsiCatchSection -> current.catchBlock?.let { heads += headRange(current, it) }

                is PsiTryStatement -> {
                    val tryBlock = current.tryBlock
                    if (tryBlock != null) {
                        heads += headRange(current, tryBlock)
                        val finallyBlock = current.finallyBlock
                        if (finallyBlock != null && isInside(element, finallyBlock)) {
                            heads += betweenRange(current.catchBlocks.lastOrNull() ?: tryBlock, finallyBlock)
                        }
                    }
                }

                else -> {}
            }
            current = current.parent
        }
        return heads
    }
}
