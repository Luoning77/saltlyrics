package com.luoning.saltlyrics.lyrics

import org.junit.Assert.*
import org.junit.Test

class ParallelLyricsTest {
    @Test fun sameTimestampKeepsOriginalAndTranslationInOneCue() {
        val words = listOf(LyricWord(1000, 500, "Hello"))
        val lyrics = Lyrics(listOf(
            LyricLine(1000, "Hello", words, 3000),
            LyricLine(1000, "你好", endMs = 3000),
            LyricLine(4000, "Next"),
        ), "test")
        val cue = lyrics.lines[lyrics.indexAt(1500)]
        assertEquals("Hello", cue.text)
        assertEquals(listOf("你好"), cue.translations)
        assertSame(words, cue.words)
        assertEquals(3000L, cue.endMs)
    }

    @Test fun bilingualLongLinesStayPairedWhenSplittingIsEnabled() {
        val lyrics = Lyrics(listOf(
            LyricLine(0, "今はまだ知らない 出会ってすらいない", endMs = 4000),
            LyricLine(0, "此刻还不曾知晓 甚至还未曾相遇", endMs = 4000),
        ), "test")
        // Exercise the splitter directly so this test does not change global settings.
        val split = LyricSplit.split(lyrics.lines)
        assertEquals(1, split.size)
        assertEquals(listOf("此刻还不曾知晓 甚至还未曾相遇"), split.single().translations)
    }

    @Test fun nearbyTimestampsAreNotCombined() {
        val lyrics = Lyrics(listOf(LyricLine(0, "Original"), LyricLine(1, "Another line")), "test")
        assertEquals(2, lyrics.lines.size)
        assertTrue(lyrics.lines.all { it.translations.isEmpty() })
    }

    @Test fun repeatedAndBlankCompanionRowsDoNotDuplicateTheText() {
        val lyrics = Lyrics(listOf(
            LyricLine(0, "Hello"), LyricLine(0, ""), LyricLine(0, "Hello"),
            LyricLine(0, "你好"), LyricLine(0, "你好"), LyricLine(0, "こんにちは"),
        ), "test")
        assertEquals(listOf("你好", "こんにちは"), lyrics.lines.single().translations)
    }

    @Test fun translationEndPreventsAZeroLengthOriginalCue() {
        val lyrics = Lyrics(listOf(
            LyricLine(1000, "Hello", endMs = 1000),
            LyricLine(1000, "你好", endMs = 3000),
        ), "test")
        assertEquals(3000L, lyrics.lines.single().endMs)
    }

    @Test fun invalidCompanionCannotBecomeASyncableTranslation() {
        val lyrics = Lyrics(listOf(
            LyricLine(0, "Original", endMs = 1000),
            LyricLine(0, "Invalid row", endMs = 1000, timesValid = false),
        ), "test")
        val cue = lyrics.lines[lyrics.indexAt(500)]
        assertEquals("Original", cue.text)
        assertTrue(cue.translations.isEmpty())
        assertFalse(lyrics.lines.last().timesValid)
    }

    @Test fun blankTimestampStillClearsPreviousLyrics() {
        val lyrics = Lyrics(listOf(LyricLine(0, "Hello"), LyricLine(1000, "")), "test")
        assertEquals("", lyrics.lines[lyrics.indexAt(1500)].text)
    }
}
