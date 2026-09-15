package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The lexical core both the word source and name lookup rely on. */
class WordsTest : BasePlatformTestCase() {

    fun testReadsTheWordTheOffsetSitsIn() {
        assertEquals("count", Words.wordAt("int count = 0", 5))
    }

    fun testReadsTheWordWhenTheOffsetIsJustPastIt() {
        // The caret can rest immediately after a word, where the offset itself is outside it.
        assertEquals("count", Words.wordAt("int count = 0", 9))
    }

    fun testReadsTheWordAtTheVeryEndOfTheText() {
        assertEquals("count", Words.wordAt("count", 5))
    }

    fun testReadsTheWordBeforeAnOffsetThatSitsOnABoundary() {
        assertEquals("int", Words.wordAt("int count = 0", 3))
    }

    fun testFindsNoWordPastTheEndOrInEmptyText() {
        assertNull(Words.wordAt("int count = 0", 100))
        assertNull(Words.wordAt("", 0))
    }

    fun testMatchesOnlyWholeWords() {
        assertEquals(
            listOf(TextRange(0, 5), TextRange(18, 23)),
            Words.occurrences("count + recount + count", "count"),
        )
    }

    fun testTreatsUnderscoresAndDigitsAsPartOfTheWord() {
        assertEquals(
            listOf(TextRange(0, 7)),
            Words.occurrences("count_1 count_a count", "count_1"),
        )
    }

    fun testFindsNothingForAnEmptyWord() {
        assertEquals(emptyList<TextRange>(), Words.occurrences("count", ""))
    }
}
