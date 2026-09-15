package io.github.leolaurindo.tunnelvision.source

import com.intellij.testFramework.DumbModeTestUtils

/** Java coverage of the generic resolver: declaration inclusion, local scope, and failures. */
class PsiSourceTest : SourceTestCase() {

    private val source = PsiSource()

    fun testMatchesTheDeclarationAndItsUsesInsideTheEnclosingMethod() {
        configureRunAndOtherMethods("co<caret>unt")

        val result = compute(source)

        assertEquals("count", symbolOf(result))
        // Declaration, increment, and argument of `run`; the local of `other` is a symbol of its own.
        assertEquals(listOf("count", "count", "count"), matchedText(result))
    }

    fun testResolvesTheUsageUnderTheCaretToTheSameOccurrences() {
        configureRunAndOtherMethods("co<caret>unt++")

        assertEquals(listOf("count", "count", "count"), matchedText(compute(source)))
    }

    fun testLeavesOutADeclarationThatTheScopeDoesNotContain() {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                int shared = 0;

                void run() {
                    shared<caret>++;
                }

                void other() {
                    shared++;
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals("shared", symbolOf(result))
        // The field is declared outside `run`, and the use in `other` is outside the scope.
        assertEquals(listOf("shared"), matchedText(result))
    }

    fun testFollowsShadowingToTheDeclarationTheCaretNames() {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void run() {
                    int count = 0;
                    {
                        int co<caret>unt = 5;
                        count++;
                    }
                    count++;
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals("count", symbolOf(result))
        // Only the two occurrences of the inner variable: the outer declaration and use are
        // occurrences of the variable that the inner one shadows.
        assertEquals(2, rangesOf(result).size)
        assertEquals(listOf("int count = 5;", "count++;"), trimmed(lineTexts(result)))
    }

    fun testKeepsStatementsLinesAndScopeHeadsVisible() {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void run() {
                    int co<caret>unt = 0;
                    if (count > 0) count++;
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals(listOf("count", "count", "count"), matchedText(result))
        assertEquals(
            listOf("int count = 0;", "if (count > 0) count++;", "count++;"),
            statementTexts(result),
        )
        assertEquals(listOf("void run()", "if (count > 0)"), trimmed(scopeHeadTexts(result)))
        assertEquals(listOf("int count = 0;", "if (count > 0) count++;"), trimmed(lineTexts(result)))
    }

    fun testKeepsLoopSwitchAndTryClausesVisible() {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void run() {
                    int co<caret>unt = 0;
                    while (count > 0) count++;
                    switch (count) {
                        case 1: count--;
                        default: break;
                    }
                    try {
                        count++;
                    } catch (Exception e) {
                        count--;
                    } finally {
                        count = 0;
                    }
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals(
            listOf("void run()", "while (count > 0)", "switch (count)", "try", "catch (Exception e)", "finally"),
            trimmed(scopeHeadTexts(result)),
        )

        val statements = statementTexts(result)
        assertTrue(statements.toString(), statements.contains("while (count > 0) count++;"))
        assertTrue(statements.toString(), statements.contains("count++;"))
        assertTrue(statements.toString(), statements.contains("count--;"))
        assertTrue(statements.toString(), statements.contains("count = 0;"))
    }

    fun testResolvesLocalOccurrencesWhileTheIndexesAreBeingBuilt() {
        configureRunAndOtherMethods("co<caret>unt")

        val result = DumbModeTestUtils.computeInDumbModeSynchronously(project) { compute(source) }

        // The search is scoped to the enclosing function, so it needs no index.
        assertEquals(listOf("count", "count", "count"), matchedText(result))
    }

    fun testResolvesMethodCallsInsideTheEnclosingMethod() {
        configureHelperCalls()

        val result = compute(source)

        assertEquals("helper", symbolOf(result))
        // The two calls inside `run`; the declaration and the call in `other` are out of scope.
        assertEquals(listOf("helper", "helper"), matchedText(result))
    }

    fun testReportsAMethodSearchWhileTheIndexesAreBeingBuilt() {
        // A method is the case that reaches for the index: unlike a local variable it cannot be
        // answered from PSI alone, so it is reported instead of throwing in the background.
        configureHelperCalls()

        val result = DumbModeTestUtils.computeInDumbModeSynchronously(project) { compute(source) }

        assertUnavailable(result, "indexes are not ready")
    }

    private fun configureHelperCalls() {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void helper() {}

                void run() {
                    hel<caret>per();
                    helper();
                }

                void other() {
                    helper();
                }
            }
            """.trimIndent(),
        )
    }

    fun testReportsAFieldDeclarationThatNoFunctionEncloses() {
        configureFieldUsedByTwoFunctions("private String attempts");

        // v0 already documents this: without an enclosing function there is no scope to search.
        assertUnavailable(compute(source), "no named function encloses attempts")
    }

    fun testMatchesAFieldOnlyInsideTheFunctionTheCaretSitsIn() {
        configureFieldUsedByTwoFunctions("this.attempts")

        val result = compute(source)

        assertEquals("attempts", symbolOf(result))
        // The write in the constructor, and neither the declaration nor the use in send().
        assertEquals(listOf("attempts"), matchedText(result))
    }

    fun testMatchesAUseOfTheFieldInsideItsOwnFunction() {
        configureFieldUsedByTwoFunctions("return attempts")

        assertEquals(listOf("attempts"), matchedText(compute(source)))
    }

    /** Puts the caret on the last character of [caretAtEndOf], which must be an identifier. */
    private fun configureFieldUsedByTwoFunctions(caretAtEndOf: String) {
        val text = """
            class Sample {
                private String attempts;

                Sample() {
                    this.attempts = "x";
                }

                boolean send() {
                    return attempts.isEmpty();
                }
            }
        """.trimIndent()
        val offset = text.indexOf(caretAtEndOf) + caretAtEndOf.length - 1
        myFixture.configureByText("Sample.java", text.take(offset) + "<caret>" + text.drop(offset))
    }

    fun testResolvesWithTheCaretJustPastTheName() {
        configureRunAndOtherMethods("co<caret>unt")

        // The caret at the end of the name is the common case while typing.
        val pastTheName = myFixture.caretOffset + 1
        assertEquals(listOf("count", "count", "count"), matchedText(compute(source, caretOffset = pastTheName)))
    }

    fun testDegradesToTheLineWhenTheStatementIsTooLongToKeepVisible() {
        myFixture.configureByText(
            "Sample.java",
            buildString {
                append("class Sample {\n    void run() {\n        int co<caret>unt = 0;\n")
                append("        if (count > 0) {\n")
                append("            // a statement far too long to keep visible in full\n".repeat(60))
                append("        }\n    }\n}\n")
            },
        )

        val result = compute(source)

        // The declaration is still shown in full, while the giant `if` is left to the line area.
        assertEquals(listOf("int count = 0;"), statementTexts(result))
        assertTrue(matchedText(result).size >= 2)
    }

    fun testReportsAnUnresolvedReference() {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void run() {
                    unkn<caret>own();
                }
            }
            """.trimIndent(),
        )

        assertUnavailable(compute(source), "does not resolve")
    }

    fun testReportsLanguagesWithoutAnAdapterAndPointsAtTheWordSource() {
        // JSON is a language the plugin does not adapt, which stands in for a missing optional
        // language plugin: focus must say so instead of guessing a scope.
        myFixture.configureByText("sample.json", """{"co<caret>unt": 1}""")

        assertUnavailable(compute(source), "has no structural adapter")
    }

    fun testReportsWhenTheCaretIsNotOnASymbol() {
        myFixture.configureByText("Sample.java", "class Sample {\n    <caret>\n}")

        assertUnavailable(compute(source), "no symbol at the caret")
    }

    private fun configureRunAndOtherMethods(caret: String) {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void run() {
                    int $caret = 0;
                    count++;
                    System.out.println(count);
                }

                void other() {
                    int count = 5;
                    count--;
                }
            }
            """.trimIndent(),
        )
    }
}
