package com.example.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One, two and three key user chords: the chord only changes when that many keys are held. */
class ChordKeysTest {
    @Test fun autoModeIsTheNormalDetection() {
        assertArrayEquals(Chords.detect(listOf(48, 52, 55)), Chords.detectKeys(listOf(48, 52, 55), 0))
        assertArrayEquals(intArrayOf(0, 0), Chords.detectKeys(listOf(48), 0))
    }

    @Test fun oneKeyMakesAMajorChordFromASingleNote() {
        assertArrayEquals(intArrayOf(7, 0), Chords.detectKeys(listOf(43), 1))     // G -> G major
        assertNull(Chords.detectKeys(emptyList(), 1))
    }

    @Test fun twoKeysChangeOnlyWhenTwoKeysAreHeld() {
        assertNull(Chords.detectKeys(listOf(48), 2))                               // one key: keep the old chord
        assertArrayEquals(intArrayOf(0, 0), Chords.detectKeys(listOf(48, 52), 2))   // C + E = C major
        assertArrayEquals(intArrayOf(0, 1), Chords.detectKeys(listOf(48, 51), 2))   // C + Eb = C minor
        assertArrayEquals(intArrayOf(0, 0), Chords.detectKeys(listOf(48, 55), 2))   // C + G = C major
        assertArrayEquals(intArrayOf(0, 2), Chords.detectKeys(listOf(48, 58), 2))   // C + Bb = C7
        assertArrayEquals(intArrayOf(9, 1), Chords.detectKeys(listOf(57, 60), 2))   // A + C = A minor
    }

    @Test fun threeKeysChangeOnlyWhenThreeKeysAreHeld() {
        assertNull(Chords.detectKeys(listOf(48), 3))
        assertNull(Chords.detectKeys(listOf(48, 52), 3))
        assertArrayEquals(intArrayOf(0, 0), Chords.detectKeys(listOf(48, 52, 55), 3))
        assertArrayEquals(intArrayOf(9, 1), Chords.detectKeys(listOf(57, 60, 64), 3))
    }
}
