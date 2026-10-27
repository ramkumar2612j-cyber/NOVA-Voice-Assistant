package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UltronChunksTest {
    @Test fun shortTextStaysOnePiece() {
        assertEquals(listOf("Namaste, main NOVA hoon."), UltronChunks.split("Namaste, main NOVA hoon."))
    }

    @Test fun longTextIsCutAtSentenceEndsAndKeepsAllWords() {
        val text = (1..12).joinToString(" ") { "Ye sentence number $it hai aur ye thoda lamba bhi hai." }
        val parts = UltronChunks.split(text)
        assertTrue(parts.size > 1)
        assertTrue(parts[0].length <= UltronChunks.FIRST_MAX + 60)
        assertEquals(text.split(" ").filter { it.isNotEmpty() }, parts.joinToString(" ").split(" ").filter { it.isNotEmpty() })
        parts.drop(1).forEach { assertTrue(it.length <= UltronChunks.NEXT_MAX) }
    }

    @Test fun oneVeryLongSentenceIsStillCut() {
        val text = (1..80).joinToString(" ") { "shabd$it" }
        val parts = UltronChunks.split(text)
        assertTrue(parts.size > 1)
        assertEquals(text, parts.joinToString(" "))
    }
}
