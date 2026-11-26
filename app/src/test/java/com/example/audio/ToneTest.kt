package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneTest {
    private val sr = 44100f

    @Test fun flatIsExactlyPassThrough() {
        for (f in floatArrayOf(60f, 500f, 3000f, 12000f)) assertEquals(0.0, Tone.responseDb(Tone.FLAT, sr, f), 1e-6)
    }

    @Test fun clearCutsLowEndAndLiftsPresenceAndAir() {
        assertTrue(Tone.responseDb(Tone.CLEAR, sr, 60f) < -2.0)
        assertTrue(Tone.responseDb(Tone.CLEAR, sr, 350f) < -1.5)
        assertEquals(0.0, Tone.responseDb(Tone.CLEAR, sr, 1000f), 0.6)
        assertEquals(2.5, Tone.responseDb(Tone.CLEAR, sr, 3200f), 0.6)
        assertTrue(Tone.responseDb(Tone.CLEAR, sr, 12000f) > 2.0)
    }

    @Test fun warmIsTheOpposite() {
        assertTrue(Tone.responseDb(Tone.WARM, sr, 60f) > 1.5)
        assertTrue(Tone.responseDb(Tone.WARM, sr, 12000f) < -2.0)
    }

    @Test fun noBoostEverExceedsThreeDb() {
        for (p in 0 until Tone.COUNT) for (f in 30..20000 step 50) assertTrue(Math.abs(Tone.responseDb(p, sr, f.toFloat())) < 3.2)
    }

    @Test fun engineWithToneRendersDifferentlyAndStaysClean() {
        fun render(tone: Int): ShortArray {
            val e = SynthEngine()
            e.send(Cmd(Op.TONE, tone))
            for (n in intArrayOf(48, 55, 60, 64, 67)) e.send(Cmd(Op.NOTE_ON, 0, n, 100))
            e.send(Cmd(Op.NOTE_ON, 9, 42, 100))
            return e.renderOffline(22050)
        }
        val flat = render(Tone.FLAT)
        val clear = render(Tone.CLEAR)
        var diff = 0L
        for (i in flat.indices) diff += Math.abs(flat[i] - clear[i])
        assertTrue("tone must change the sound", diff > 50000)
        assertTrue("no clipping", clear.maxOf { Math.abs(it.toInt()) } < 32700)
    }
}
