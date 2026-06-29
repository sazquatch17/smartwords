// Unit tests for the core word-rotation logic and batch parsing.
// Mirrors ios/Tests/WordStoreTests.swift — same properties, both platforms.

package com.example.smartwords

import com.example.smartwords.data.Word
import com.example.smartwords.data.WordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WordStoreTest {

    private val words = (0 until 10).map { Word("w$it", "d") }

    // Fixed UTC calendar set to a given day/hour so the math is deterministic.
    private fun cal(year: Int, month: Int, day: Int, hour: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(year, month, day, hour, 0, 0)
        }

    @Test fun indexInBounds() {
        for (hour in 0 until 24) {
            val i = WordStore.index(words, 3, cal(2026, Calendar.JUNE, 24, hour))
            assertTrue("index $i out of bounds at hour $hour", i in words.indices)
        }
    }

    @Test fun sameSlotSameIndex() {
        val a = WordStore.index(words, 3, cal(2026, Calendar.JUNE, 24, 0))
        val b = WordStore.index(words, 3, cal(2026, Calendar.JUNE, 24, 2))   // same 3h slot
        assertEquals(a, b)
    }

    @Test fun nextSlotAdvancesByOne() {
        val a = WordStore.index(words, 3, cal(2026, Calendar.JUNE, 24, 0))
        val b = WordStore.index(words, 3, cal(2026, Calendar.JUNE, 24, 3))
        assertEquals((a + 1) % words.size, b)
    }

    @Test fun nextDayAdvancesBySlotsPerDay() {
        val a = WordStore.index(words, 3, cal(2026, Calendar.JUNE, 24, 0))
        val b = WordStore.index(words, 3, cal(2026, Calendar.JUNE, 25, 0))
        assertEquals((a + 8) % words.size, b)   // 24/3 = 8 slots/day
    }

    @Test fun singleWordAlwaysZero() {
        assertEquals(0, WordStore.index(listOf(words[0]), 3, cal(2026, Calendar.JUNE, 24, 15)))
    }

    @Test fun emptyWordsDoesNotCrash() {
        assertEquals(0, WordStore.index(emptyList(), 3, cal(2026, Calendar.JUNE, 24, 15)))
    }

    @Test fun rotationHoursClampedToValidRange() {
        // 0 / negative / >24 must not throw (coerced to 1..24).
        assertTrue(WordStore.index(words, 0, cal(2026, Calendar.JUNE, 24, 5)) in words.indices)
        assertTrue(WordStore.index(words, 99, cal(2026, Calendar.JUNE, 24, 5)) in words.indices)
    }

    @Test fun parseBatchShape() {
        val json = """
            {"date":"2026-06-24","words":[
              {"word":"ubiquitous","definition":"found everywhere.","pos":"adjective",
               "synonyms":["omnipresent"],"antonyms":["rare"]}
            ]}
        """.trimIndent()
        val parsed = WordStore.parseOrEmpty(json)
        assertEquals(1, parsed.size)
        assertEquals("ubiquitous", parsed[0].word)
        assertEquals(listOf("omnipresent"), parsed[0].synonyms)
    }

    @Test fun parseSeedShapeOmitsOptionalFields() {
        val parsed = WordStore.parseOrEmpty("""{"words":[{"word":"abate","definition":"subside."}]}""")
        assertEquals("abate", parsed[0].word)
        assertNull(parsed[0].pos)
    }

    @Test fun parseInvalidReturnsEmpty() {
        assertTrue(WordStore.parseOrEmpty("not json").isEmpty())
    }
}
