package io.github.leolaurindo.tunnelvision.source.adapters

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.leolaurindo.tunnelvision.source.StructuralAdapter
import io.github.leolaurindo.tunnelvision.source.headRange
import io.github.leolaurindo.tunnelvision.source.isInside
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * Kotlin scope: the nearest enclosing named function.
 *
 * Lambdas and expression bodies are deferred, so a symbol inside one is scoped to the named
 * function that contains it. Kotlin has no statement node, so a statement is the expression or
 * declaration that sits directly in a block, a body, a `when` entry or a lambda.
 */
object KotlinStructuralAdapter : StructuralAdapter {

    override fun enclosingFunction(element: PsiElement): PsiElement? =
        PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java, false)

    override fun statement(element: PsiElement): PsiElement? {
        var current = element.parent
        while (current != null) {
            if (isInStatementPosition(current)) return current
            current = current.parent
        }
        return null
    }

    override fun scopeHeads(element: PsiElement): List<TextRange> {
        val heads = mutableListOf<TextRange>()
        var current: PsiElement? = element

        while (current != null) {
            when (current) {
                is KtNamedFunction -> current.bodyExpression?.let { heads += headRange(current, it) }

                is KtIfExpression -> {
                    current.then?.let { heads += headRange(current, it) }
                    val elseBranch = current.`else`
                    val elseKeyword = current.elseKeyword
                    if (elseBranch != null && elseKeyword != null && isInside(element, elseBranch)) {
                        heads += headRange(elseKeyword, elseBranch)
                    }
                }

                is KtLoopExpression -> current.body?.let { heads += headRange(current, it) }

                is KtWhenExpression -> current.entries.firstOrNull()?.let { heads += headRange(current, it) }

                is KtCatchClause -> current.catchBody?.let { heads += headRange(current, it) }

                is KtFinallySection -> current.finalExpression?.let { heads += headRange(current, it) }

                is KtTryExpression -> heads += headRange(current, current.tryBlock)

                else -> {}
            }
            current = current.parent
        }
        return heads
    }

    private fun isInStatementPosition(element: PsiElement): Boolean {
        if (element !is KtExpression && element !is KtDeclaration) return false
        return when (element.parent) {
            is KtBlockExpression, is KtNamedFunction, is KtWhenEntry, is KtFunctionLiteral -> true
            else -> false
        }
    }
}
