package io.github.leolaurindo.tunnelvision.source.adapters

import com.intellij.lang.javascript.psi.JSCatchBlock
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSFunctionExpression
import com.intellij.lang.javascript.psi.JSIfStatement
import com.intellij.lang.javascript.psi.JSLoopStatement
import com.intellij.lang.javascript.psi.JSStatement
import com.intellij.lang.javascript.psi.JSSwitchStatement
import com.intellij.lang.javascript.psi.JSTryStatement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.leolaurindo.tunnelvision.source.StructuralAdapter
import io.github.leolaurindo.tunnelvision.source.betweenRange
import io.github.leolaurindo.tunnelvision.source.headRange
import io.github.leolaurindo.tunnelvision.source.isInside

/**
 * JavaScript and TypeScript scope: the nearest enclosing named function.
 *
 * Arrow and anonymous function expressions are deferred, so the search keeps the declaration or
 * method that contains them. Statements are JS statements, and the scope heads are the functions,
 * branches, loops, switches and try clauses around the occurrence.
 */
object JavaScriptStructuralAdapter : StructuralAdapter {

    override fun enclosingFunction(element: PsiElement): PsiElement? {
        var function = PsiTreeUtil.getParentOfType(element, JSFunction::class.java, false)
        while (function != null && !isSupportedScope(function)) {
            function = PsiTreeUtil.getParentOfType(function, JSFunction::class.java, true)
        }
        return function
    }

    override fun statement(element: PsiElement): PsiElement? =
        PsiTreeUtil.getParentOfType(element, JSStatement::class.java)

    override fun scopeHeads(element: PsiElement): List<TextRange> {
        val heads = mutableListOf<TextRange>()
        var current: PsiElement? = element

        while (current != null) {
            when (current) {
                is JSFunction -> if (isSupportedScope(current)) {
                    current.block?.let { heads += headRange(current, it) }
                }

                is JSIfStatement -> {
                    val thenBranch = current.thenBranch
                    val elseBranch = current.elseBranch
                    if (thenBranch != null) {
                        heads += headRange(current, thenBranch)
                        if (elseBranch != null && isInside(element, elseBranch)) {
                            heads += betweenRange(thenBranch, elseBranch)
                        }
                    }
                }

                is JSLoopStatement -> current.body?.let { heads += headRange(current, it) }

                is JSSwitchStatement -> current.caseClauses.firstOrNull()?.let { heads += headRange(current, it) }

                is JSCatchBlock -> current.statement?.let { heads += headRange(current, it) }

                is JSTryStatement -> {
                    val statement = current.statement
                    if (statement != null) {
                        heads += headRange(current, statement)
                        val finallyStatement = current.finallyStatement
                        if (finallyStatement != null && isInside(element, finallyStatement)) {
                            heads += betweenRange(current.allCatchBlocks.lastOrNull() ?: statement, finallyStatement)
                        }
                    }
                }

                else -> {}
            }
            current = current.parent
        }
        return heads
    }

    private fun isSupportedScope(function: JSFunction): Boolean =
        function !is JSFunctionExpression && !function.isArrowFunction && !function.name.isNullOrBlank()
}
