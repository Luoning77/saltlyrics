package com.luoning.saltlyrics.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricSplitTest {
    @Test fun splitRetainsExplicitEndIncludingTheFinalSegment() {
        val out=LyricSplit.split(listOf(LyricLine(0,"今はまだ知らない 出会ってすらいない",endMs=4000)))
        assertTrue(out.size>=2)
        assertEquals(4000L,out.last().endMs)
        assertEquals(out[1].timeMs,out[0].endMs)
    }
    @Test fun splitCannotTurnInvalidSourceTimingIntoSyncableLines() {
        val out=LyricSplit.split(listOf(LyricLine(0,"今はまだ知らない 出会ってすらいない",endMs=4000,timesValid=false)))
        assertTrue(out.size>=2)
        assertTrue(out.none { it.timesValid })
    }
}
