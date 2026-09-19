package com.inventoryscanner

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatFilterTest {
    /** Feeds one frame every 100 ms; returns what each frame reported. Per-code rule only (no gap, no confirmation). */
    private fun run(vararg frames: List<String>): List<String?> {
        val f = RepeatFilter(1500, gapMs = 0, confirmFrames = 1)
        return frames.mapIndexed { i, codes -> f.onFrame(codes, i * 100L) }
    }

    private val a = listOf("A")
    private val b = listOf("B")
    private val none = emptyList<String>()

    @Test fun sameCodeHeldIsReportedOnce() = assertEquals(listOf("A", null, null), run(a, a, a))

    @Test fun heldForTenSecondsStillOnce() = assertEquals(listOf("A"), run(*Array(100) { a }).filterNotNull())

    @Test fun briefDropoutDoesNotRearm() = assertEquals(listOf("A", null, null, null), run(a, none, none, a))

    @Test fun sameCodeAfterQuietGapIsReportedAgain() {
        val f = RepeatFilter(1500, gapMs = 0, confirmFrames = 1)
        assertEquals("A", f.onFrame(a, 0))
        assertEquals(null, f.onFrame(a, 1000))
        assertEquals("A", f.onFrame(a, 2600))
    }

    @Test fun differentCodesAreReported() = assertEquals(listOf("A", "B"), run(a, b).filterNotNull())

    // Regression: reading B used to re-arm A, so sweeping between two nearby codes re-read A at once.
    @Test fun anotherCodeDoesNotRearm() = assertEquals(listOf("A", "B", null, null, null), run(a, b, a, b, a))

    @Test fun eachCodeKeepsItsOwnTimer() {
        val f = RepeatFilter(1500, gapMs = 0, confirmFrames = 1)
        assertEquals("A", f.onFrame(a, 0))
        assertEquals("B", f.onFrame(b, 1000)) // A last seen at 0
        assertEquals("A", f.onFrame(a, 1600)) // A out of view > 1.5 s: a new scan
        assertEquals(null, f.onFrame(b, 2000)) // B still within its wait
    }

    @Test fun emptyFramesReportNothing() = assertEquals(listOf(null, null), run(none, none))

    @Test fun multipleCodesInOneFrameOneAtATime() = assertEquals(
        listOf("A", "B", null, null),
        run(listOf("A", "B"), listOf("A", "B"), listOf("A", "B"), listOf("B", "A")),
    )

    // A moving barcode misread as a shorter number right after the good read is dropped.
    @Test fun nothingWithinGapOfPreviousRead() {
        val f = RepeatFilter(1500, gapMs = 3000, confirmFrames = 1)
        assertEquals("4006381333931", f.onFrame(listOf("4006381333931"), 0))
        assertEquals(null, f.onFrame(listOf("006381"), 200)) // misread
        assertEquals(null, f.onFrame(b, 2900)) // another product, still within the gap
        assertEquals("B", f.onFrame(b, 3000)) // gap over
    }

    @Test fun codeNeedsConsecutiveFrames() {
        val f = RepeatFilter(1500, gapMs = 0, confirmFrames = 3)
        assertEquals(null, f.onFrame(a, 0))
        assertEquals(null, f.onFrame(a, 100))
        assertEquals("A", f.onFrame(a, 200))
    }

    // A partial read that flickers in for a frame or two never counts; a missed frame starts over.
    @Test fun flickeringMisreadIsDropped() {
        val f = RepeatFilter(1500, gapMs = 0, confirmFrames = 3)
        val real = listOf("4006381333931")
        val frames = listOf(listOf("006381"), real, listOf("0063"), real, real, real)
        assertEquals(listOf(null, null, null, null, null, "4006381333931"), frames.mapIndexed { i, c -> f.onFrame(c, i * 100L) })
    }

    // Soft lock / no lock: a code still in view is read again once the gap is over.
    @Test fun withoutHoldSameCodeRepeatsAfterGap() {
        val f = RepeatFilter(1500, gapMs = 3000, confirmFrames = 1)
        assertEquals("A", f.onFrame(a, 0, holdRepeats = false))
        assertEquals(null, f.onFrame(a, 2900, holdRepeats = false))
        assertEquals("A", f.onFrame(a, 3000, holdRepeats = false))
    }

    // A popup is open: nothing is read, and the gap restarts when it closes.
    @Test fun pauseRestartsTheGap() {
        val f = RepeatFilter(1500, gapMs = 3000, confirmFrames = 1)
        assertEquals(null, f.onFrame(a, 0, paused = true))
        assertEquals(null, f.onFrame(a, 5000, paused = true))
        assertEquals(null, f.onFrame(a, 7900)) // 2.9 s after the popup closed
        assertEquals("A", f.onFrame(a, 8000))
    }

    @Test fun defaultsConfirmThreeFramesAndWaitThreeSeconds() {
        val f = RepeatFilter()
        val reads = (0..2).map { f.onFrame(a, it * 100L) } // A confirmed on its 3rd frame, at 200 ms
        assertEquals(listOf(null, null, "A"), reads)
        for (t in listOf(300L, 400L, 3100L)) assertEquals(null, f.onFrame(b, t)) // B confirmed, but within the 3 s gap
        assertEquals("B", f.onFrame(b, 3200)) // 3 s after A
        assertEquals(null, f.onFrame(a, 3300)) // A still counts as the same scan (seen again within 3 s)
    }
}
