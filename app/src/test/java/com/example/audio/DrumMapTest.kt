package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrumMapTest {
    @Test fun legacyKitIsUnchanged() {
        // note, kind, frequency used by the original engine
        val legacy = listOf(
            Triple(35, DrumMap.KICK, 600f), Triple(36, DrumMap.KICK, 600f),
            Triple(38, DrumMap.SNARE, 600f), Triple(40, DrumMap.SNARE, 600f),
            Triple(37, DrumMap.STICK, 600f), Triple(39, DrumMap.CLAP, 600f),
            Triple(42, DrumMap.HAT_CLOSED, 600f), Triple(44, DrumMap.HAT_CLOSED, 600f), Triple(46, DrumMap.HAT_OPEN, 600f),
            Triple(41, DrumMap.TOM, 80f), Triple(43, DrumMap.TOM, 100f), Triple(45, DrumMap.TOM, 125f),
            Triple(47, DrumMap.TOM, 150f), Triple(48, DrumMap.TOM, 180f), Triple(50, DrumMap.TOM, 210f),
            Triple(49, DrumMap.CRASH, 600f), Triple(57, DrumMap.CRASH, 600f),
            Triple(51, DrumMap.RIDE, 600f), Triple(53, DrumMap.RIDE, 600f), Triple(59, DrumMap.RIDE, 600f),
            Triple(54, DrumMap.TAMB, 600f), Triple(70, DrumMap.TAMB, 600f)
        )
        for ((n, k, f) in legacy) {
            assertEquals("kind of note $n", k, DrumMap.kindOf(n))
            assertEquals("freq of note $n", f, DrumMap.freqOf(n), 0.001f)
        }
    }

    @Test fun cowbellAndCongasHaveTheirOwnSoundsAndRealisticPitch() {
        assertEquals(DrumMap.COWBELL, DrumMap.kindOf(56))
        for (n in 60..64) {
            assertEquals(DrumMap.CONGA, DrumMap.kindOf(n))
            assertTrue("conga pitch of note $n must be low", DrumMap.freqOf(n) in 200f..450f)
        }
        assertTrue(DrumMap.freqOf(64) < DrumMap.freqOf(63))   // low conga below high conga
    }

    @Test fun everyPercussionNote35to87Sounds() {
        for (n in 35..87) assertTrue("note $n must sound", DrumMap.kindOf(n) != DrumMap.SILENT)
        for (n in intArrayOf(28, 31, 32, 33, 34)) assertTrue("note $n must sound", DrumMap.kindOf(n) != DrumMap.SILENT)
    }

    @Test fun fxAndOutOfRangeNotesAreSilentNotABlip() {
        for (n in intArrayOf(0, 24, 25, 26, 27, 29, 30, 88, 100, 127, -1, 128)) {
            assertEquals("note $n", DrumMap.SILENT, DrumMap.kindOf(n))
        }
    }

    @Test fun everyKindHasALifeAndNoiseKindsAreFlagged() {
        for (k in 0..15) assertTrue(DrumMap.lifeSeconds(k) > 0.1f && DrumMap.lifeSeconds(k) < 5f)
        assertTrue(DrumMap.isNoiseKind(DrumMap.HAT_CLOSED))
        assertTrue(DrumMap.isNoiseKind(DrumMap.SHAKER))
        assertTrue(!DrumMap.isNoiseKind(DrumMap.KICK))
        assertTrue(!DrumMap.isNoiseKind(DrumMap.WOOD))
    }
}
