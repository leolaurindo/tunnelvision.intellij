package io.github.leolaurindo.tunnelvision.source

import io.github.leolaurindo.tunnelvision.settings.HighlightArea

class WordSourceTest : SourceTestCase() {

    private val source = WordSource()

    fun testMatchesEveryOccurrenceOverTheCompleteDocument() {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void run() {
                    int co<caret>unt = 0;
                    // count keeps countdown at bay
                    System.out.println("count " + count + recount);
                }

                void other() {
                    int count = 5;
                }
            }
            """.trimIndent(),
        )

        val result = compute(source)

        assertEquals("count", symbolOf(result))
        // Declaration, comment, string literal, argument, and the second method: no scope, and
        // `countdown` and `recount` are not occurrences of the word.
        assertEquals(listOf("count", "count", "count", "count", "count"), matchedText(result))
    }

    fun testMatchesTheWordTheCaretSitsImmediatelyAfter() {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void run() {
                    int count<caret> = 0;
                }
            }
            """.trimIndent(),
        )

        assertEquals(listOf("count"), matchedText(compute(source)))
    }

    fun testLeavesTheStructuralAreasToThePsiSource() {
        myFixture.configureByText("Sample.java", "class Sample {\n    int co<caret>unt = 0;\n}")

        assertEquals(listOf(HighlightArea.LINE, HighlightArea.SYMBOL), areasOf(compute(source)))
    }

    fun testSkipsTheAreasThatAreNotEnabled() {
        myFixture.configureByText("Sample.java", "class Sample {\n    int co<caret>unt = 0;\n}")

        val result = compute(source, areas = setOf(HighlightArea.SYMBOL))

        assertEquals(listOf(HighlightArea.SYMBOL), areasOf(result))
    }

    fun testReportsAnUnavailableWordWhenTheCaretIsNotOnOne() {
        myFixture.configureByText("Sample.java", "class Sample {\n    <caret>\n}")

        assertUnavailable(compute(source), "no word at the caret")
    }
}
