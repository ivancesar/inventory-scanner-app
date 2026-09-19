package com.inventoryscanner

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatFilterTest {
    /** Feeds one frame every 100 ms; returns what each frame reported. */
    private fun run(vararg frames: List<String>): List<String?> {
        val f = RepeatFilter(1500)
        return frames.mapIndexed { i, codes -> f.onFrame(codes, i * 100L) }
    }

    private val a = listOf("A")
    private val b = listOf("B")
    private val none = emptyList<String>()

    @Test fun sameCodeHeldIsReportedOnce() = assertEquals(listOf("A", null, null), run(a, a, a))

    @Test fun heldForTenSecondsStillOnce() = assertEquals(listOf("A"), run(*Array(100) { a }).filterNotNull())

    @Test fun briefDropoutDoesNotRearm() = assertEquals(listOf("A", null, null, null), run(a, none, none, a))

    @Test fun sameCodeAfterQuietGapIsReportedAgain() {
        val f = RepeatFilter(1500)
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

    @Test fun differentCodesAreReported() = assertEquals(listOf("A", "B", "A"), run(a, b, a))

    @Test fun emptyFramesReportNothing() = assertEquals(listOf(null, null), run(none, none))

    @Test fun multipleCodesInOneFrameOneAtATime() = assertEquals(
        listOf("A", "B", null, null),
        run(listOf("A", "B"), listOf("A", "B"), listOf("A", "B"), listOf("B", "A")),
    )
}
