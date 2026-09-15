package io.github.leolaurindo.tunnelvision.source

class JavaScriptPsiSourceTest : SourceTestCase() {

    private val source = PsiSource()

    fun testMatchesTheDeclarationAndItsUsesInsideTheEnclosingFunction() {
        myFixture.configureByText(
            "sample.js",
            """
            function run() {
                let co<caret>unt = 0;
                count++;
                console.log(count);
            }

            function other() {
                let count = 5;
                count--;
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals("count", symbolOf(result))
        assertEquals(listOf("count", "count", "count"), matchedText(result))
    }

    fun testKeepsStatementsLinesAndScopeHeadsVisible() {
        myFixture.configureByText(
            "sample.js",
            """
            function run() {
                let co<caret>unt = 0;
                try {
                    count++;
                } catch (e) {
                    count--;
                } finally {
                    count += 1;
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals(listOf("count", "count", "count", "count"), matchedText(result))
        assertEquals(
            listOf("let count = 0;", "count++;", "count--;", "count += 1;"),
            statementTexts(result),
        )
        assertEquals(listOf("function run()", "try", "catch (e)", "finally"), trimmed(scopeHeadTexts(result)))
        assertEquals(4, lineTexts(result).size)
    }

    fun testKeepsSwitchClausesVisible() {
        myFixture.configureByText(
            "sample.js",
            """
            function run() {
                let co<caret>unt = 0;
                switch (count) {
                    case 1: count++; break;
                    default: count--;
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals(listOf("function run()", "switch (count) {"), trimmed(scopeHeadTexts(result)))

        val statements = statementTexts(result)
        assertTrue(statements.toString(), statements.contains("count++;"))
        assertTrue(statements.toString(), statements.contains("count--;"))
    }

    fun testScopesAnArrowFunctionToTheEnclosingNamedFunction() {
        myFixture.configureByText(
            "sample.js",
            """
            function run() {
                let co<caret>unt = 0;
                const bump = () => { count++; };
                count--;
            }
            """.trimIndent(),
        )

        // Arrow-function scope is deferred, so the whole enclosing function is searched.
        assertEquals(listOf("count", "count", "count"), matchedText(compute(source)))
    }
}
