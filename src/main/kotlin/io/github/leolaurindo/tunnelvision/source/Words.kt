package io.github.leolaurindo.tunnelvision.source

import com.intellij.openapi.util.TextRange

/**
 * Lexical word matching, shared by the word source and by name-range lookup.
 *
 * A word is a run of letters, digits and underscores, and it is matched only between word
 * boundaries: `count` matches neither `countdown` nor `recount`, as in the Neovim implementation.
 */
internal object Words {

    fun isWordChar(c: Char): Boolean = Character.isLetterOrDigit(c) || c == '_'

    /** @return the word [offset] sits in or immediately after, or null when there is none. */
    fun wordAt(text: CharSequence, offset: Int): String? {
        for (start in intArrayOf(offset, offset - 1)) {
            if (start < 0 || start >= text.length || !isWordChar(text[start])) continue

            var from = start
            while (from > 0 && isWordChar(text[from - 1])) from--

            var to = start
            while (to < text.length && isWordChar(text[to])) to++

            return text.substring(from, to)
        }
        return null
    }

    /** @return every boundary-safe occurrence of [word] in [text], in document order. */
    fun occurrences(text: CharSequence, word: String): List<TextRange> {
        if (word.isEmpty()) return emptyList()

        val ranges = ArrayList<TextRange>()
        var index = 0
        while (index <= text.length - word.length) {
            if (matchesAt(text, word, index) && isBounded(text, index, word.length)) {
                ranges += TextRange(index, index + word.length)
                index += word.length
            } else {
                index++
            }
        }
        return ranges
    }

    private fun matchesAt(text: CharSequence, word: String, index: Int): Boolean {
        for (offset in word.indices) {
            if (text[index + offset] != word[offset]) return false
        }
        return true
    }

    private fun isBounded(text: CharSequence, start: Int, length: Int): Boolean {
        val end = start + length
        val boundedBefore = start == 0 || !isWordChar(text[start - 1])
        val boundedAfter = end == text.length || !isWordChar(text[end])
        return boundedBefore && boundedAfter
    }
}
