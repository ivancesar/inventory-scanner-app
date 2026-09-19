package com.inventoryscanner

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatFilterTest {
    /** Feeds one frame every 100 ms; returns what each frame reported. No gap, to test the per-code rule alone. */
    private fun run(vararg frames: List<String>): List<String?> {
        val f = RepeatFilter(1500, gapMs = 0)
        return frames.mapIndexed { i, codes -> f.onFrame(codes, i * 100L) }
    }

    private val a = listOf("A")
    private val b = listOf("B")
    private val none = emptyList<String>()

    @Test fun sameCodeHeldIsReportedOnce() = assertEquals(listOf("A", null, null), run(a, a, a))

    @Test fun heldForTenSecondsStillOnce() = assertEquals(listOf("A"), run(*Array(100) { a }).filterNotNull())

    @Test fun briefDropoutDoesNotRearm() = assertEquals(listOf("A", null, null, null), run(a, none, none, a))

    @Test fun sameCodeAfterQuietGapIsReportedAgain() {
        val f = RepeatFilter(1500, gapMs = 0)
        assertEquals("A", f.onFrame(a, 0))
        assertEquals(null, f.onFrame(a, 1000))
        assertEquals("A", f.onFrame(a, 2600))
    }

    @Test fun defaultWaitsThreeSeconds() {
        val f = RepeatFilter()
        assertEquals("A", f.onFrame(a, 0))
        assertEquals(null, f.onFrame(a, 2900)) // out of view 2.9 s: still the same scan
        assertEquals("A", f.onFrame(a, 6000))
    }

    // A moving barcode misread as a shorter number right after the good read is dropped.
    @Test fun nothingWithinGapOfPreviousRead() {
        val f = RepeatFilter(1500, gapMs = 3000)
        assertEquals("4006381333931", f.onFrame(listOf("4006381333931"), 0))
        assertEquals(null, f.onFrame(listOf("006381"), 200)) // misread
        assertEquals(null, f.onFrame(b, 2900)) // another product, still within the gap
        assertEquals("B", f.onFrame(b, 3000)) // gap over
    }

    @Test fun defaultGapIsThreeSeconds() {
        val f = RepeatFilter()
        assertEquals("A", f.onFrame(a, 0))
        assertEquals(null, f.onFrame(b, 2999))
        assertEquals("B", f.onFrame(b, 3000))
    }

    @Test fun differentCodesAreReported() = assertEquals(listOf("A", "B"), run(a, b).filterNotNull())

    // Regression: reading B used to re-arm A, so sweeping between two nearby codes re-read A at once.
    @Test fun anotherCodeDoesNotRearm() = assertEquals(listOf("A", "B", null, null, null), run(a, b, a, b, a))

    @Test fun eachCodeKeepsItsOwnTimer() {
        val f = RepeatFilter(1500, gapMs = 0)
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
}
