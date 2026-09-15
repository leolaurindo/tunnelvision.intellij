package io.github.leolaurindo.tunnelvision.source

/** TypeScript shares the JavaScript adapter; this fixture pins that down. */
class TypeScriptPsiSourceTest : SourceTestCase() {

    private val source = PsiSource()

    fun testMatchesTheDeclarationAndItsUsesInsideTheEnclosingFunction() {
        myFixture.configureByText(
            "sample.ts",
            """
            function run(): void {
                let co<caret>unt: number = 0;
                count++;
                console.log(count);
            }

            function other(): void {
                let count: number = 5;
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
            "sample.ts",
            """
            function run(): void {
                let co<caret>unt: number = 0;
                if (count > 0) { count++; } else { count--; }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals(listOf("count", "count", "count", "count"), matchedText(result))
        assertEquals(
            listOf(
                "let count: number = 0;",
                "if (count > 0) { count++; } else { count--; }",
                "count++;",
                "count--;",
            ),
            statementTexts(result),
        )
        assertEquals(
            listOf("function run(): void", "if (count > 0)", "else"),
            trimmed(scopeHeadTexts(result)),
        )
        assertEquals(
            listOf("let count: number = 0;", "if (count > 0) { count++; } else { count--; }"),
            trimmed(lineTexts(result)),
        )
    }
}
