package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrumKitTest {
    @Test fun standardXgProgramsMapToKits() {
        assertEquals(DrumKit.STANDARD, DrumKit.fromProgram(0))
        assertEquals(DrumKit.ROOM, DrumKit.fromProgram(8))
        assertEquals(DrumKit.ELECTRO, DrumKit.fromProgram(24))
        assertEquals(DrumKit.JAZZ, DrumKit.fromProgram(32))
        assertEquals(DrumKit.BRUSH, DrumKit.fromProgram(40))
    }

    @Test fun productSpecificProgramsAreUnknownAndFallBackToStandard() {
        for (p in intArrayOf(35, 36, 80, 87, 88, 91)) {
            assertEquals(-1, DrumKit.fromProgram(p))
            assertEquals(DrumKit.STANDARD, DrumKit.resolve(p))
        }
    }

    @Test fun standardKitChangesNothing() {
        for (k in 0..13) {
            assertEquals(1f, DrumKit.pitchFactor(DrumKit.STANDARD, k), 0f)
            assertEquals(1f, DrumKit.decayFactor(DrumKit.STANDARD, k), 0f)
        }
    }

    @Test fun factorsStayInASafeRangeAndBadIdsAreNeutral() {
        for (kit in 0 until DrumKit.COUNT) for (k in 0..13) {
            assertTrue(DrumKit.pitchFactor(kit, k) in 0.5f..1.5f)
            assertTrue(DrumKit.decayFactor(kit, k) in 0.5f..1.5f)
        }
        assertEquals(1f, DrumKit.pitchFactor(99, 0), 0f)
        assertEquals(1f, DrumKit.decayFactor(0, 99), 0f)
    }

    @Test fun stretchedEnvelopeDecaysSlowerWhenFactorAbove1() {
        val c = 0.999f
        assertTrue(DrumKit.stretchCoef(c, 1.4f) > c)
        assertTrue(DrumKit.stretchCoef(c, 0.7f) < c)
        assertEquals(c, DrumKit.stretchCoef(c, 1f), 0f)
    }

    @Test fun analogKickIsLowerAndLongerThanStandard() {
        assertTrue(DrumKit.pitchFactor(DrumKit.ANALOG, DrumMap.KICK) < 1f)
        assertTrue(DrumKit.decayFactor(DrumKit.ANALOG, DrumMap.KICK) > 1f)
    }
}
