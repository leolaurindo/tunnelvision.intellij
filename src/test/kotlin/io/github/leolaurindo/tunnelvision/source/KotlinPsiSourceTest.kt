package io.github.leolaurindo.tunnelvision.source

class KotlinPsiSourceTest : SourceTestCase() {

    private val source = PsiSource()

    fun testMatchesTheDeclarationAndItsUsesInsideTheEnclosingFunction() {
        myFixture.configureByText(
            "Sample.kt",
            """
            class Sample {
                fun run() {
                    var co<caret>unt = 0
                    count++
                    println(count)
                }

                fun other() {
                    var count = 5
                    count--
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals("count", symbolOf(result))
        assertEquals(listOf("count", "count", "count"), matchedText(result))
    }

    fun testKeepsStatementsLinesAndScopeHeadsVisible() {
        myFixture.configureByText(
            "Sample.kt",
            """
            fun run() {
                var co<caret>unt = 0
                if (count > 0) count++ else count--
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals(listOf("count", "count", "count", "count"), matchedText(result))
        assertEquals(
            listOf("var count = 0", "if (count > 0) count++ else count--"),
            statementTexts(result),
        )
        assertEquals(listOf("fun run()", "if (count > 0)", "else"), trimmed(scopeHeadTexts(result)))
        assertEquals(listOf("var count = 0", "if (count > 0) count++ else count--"), trimmed(lineTexts(result)))
    }

    fun testKeepsWhenBranchesOutOfTheScopeHead() {
        myFixture.configureByText(
            "Sample.kt",
            """
            fun run() {
                var co<caret>unt = 0
                when (count) {
                    1 -> count++
                    else -> count--
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals(listOf("fun run()", "when (count) {"), trimmed(scopeHeadTexts(result)))
        // Declaration, the `when` itself, and each of its two entries.
        assertEquals(
            listOf("count++", "count--"),
            statementTexts(result).filter { it.startsWith("count") },
        )
    }

    fun testKeepsLoopAndTryClausesVisible() {
        myFixture.configureByText(
            "Sample.kt",
            """
            fun run() {
                var co<caret>unt = 0
                for (i in 1..count) count++
                try {
                    count--
                } catch (e: Exception) {
                    count = 0
                } finally {
                    count++
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals(
            listOf("fun run()", "for (i in 1..count)", "try", "catch (e: Exception)", "finally"),
            trimmed(scopeHeadTexts(result)),
        )

        val statements = statementTexts(result)
        assertTrue(statements.toString(), statements.contains("count++"))
        assertTrue(statements.toString(), statements.contains("count--"))
        assertTrue(statements.toString(), statements.contains("count = 0"))
    }

    fun testScopesASymbolInsideALambdaToTheEnclosingNamedFunction() {
        myFixture.configureByText(
            "Sample.kt",
            """
            fun run() {
                var co<caret>unt = 0
                listOf(1).forEach { count++ }
                count--
            }
            """.trimIndent(),
        )

        // Lambda scope is deferred, so the whole enclosing function is searched.
        assertEquals(listOf("count", "count", "count"), matchedText(compute(source)))
    }
}
