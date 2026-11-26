package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline rendering of the real engine code (no audio device). */
class DrumRenderTest {
    private fun maxAbs(a: ShortArray, from: Int = 0, to: Int = a.size): Int {
        var m = 0
        for (i in from until to) m = maxOf(m, Math.abs(a[i].toInt()))
        return m
    }

    @Test fun silentNoteMakesNoSoundAndIsCounted() {
        val e = SynthEngine()
        e.send(Cmd(Op.NOTE_ON, 9, 24, 100))
        val out = e.renderOffline(4410)
        assertEquals(0, maxAbs(out))
        assertEquals(1, e.drumSilentHits)
    }

    @Test fun shakerPreviouslyABlipNowSoundsAsNoise() {
        val e = SynthEngine()
        e.send(Cmd(Op.NOTE_ON, 9, 82, 100))
        val out = e.renderOffline(4410)
        assertTrue(maxAbs(out) > 200)
        assertEquals(0, e.drumSilentHits)
    }

    @Test fun keyboardAllOffNoLongerCutsStyleDrums() {
        val e = SynthEngine()
        e.send(Cmd(Op.NOTE_ON, 9, 51, 110))       // ride
        e.renderOffline(512)
        e.send(Cmd(Op.ALL_OFF, -1))
        val out = e.renderOffline(1024)
        assertTrue("ride must still ring", maxAbs(out, 0, 512) > 50)
        assertTrue(e.activeVoices >= 1)
    }

    @Test fun panicFadesEveryDrumWithinAboutOneTenthOfASecond() {
        val e = SynthEngine()
        e.send(Cmd(Op.NOTE_ON, 9, 53, 110))       // ride bell (crash cymbals are switched off in version 3)
        e.send(Cmd(Op.NOTE_ON, 9, 51, 110))
        e.renderOffline(512)
        e.send(Cmd(Op.PANIC))
        e.renderOffline(4410)
        assertEquals(0, e.activeVoices)
    }

    @Test fun closedHatChokesOpenHatQuickly() {
        val alone = SynthEngine()
        alone.send(Cmd(Op.NOTE_ON, 9, 46, 100))
        alone.renderOffline(24500)                 // 0.55 s
        assertTrue("open hat alone still sounding", alone.activeVoices >= 1)

        val choked = SynthEngine()
        choked.send(Cmd(Op.NOTE_ON, 9, 46, 100))
        choked.renderOffline(2205)                 // 50 ms
        choked.send(Cmd(Op.NOTE_ON, 9, 42, 100))
        choked.renderOffline(22300)                // total about 0.55 s
        assertEquals(0, choked.activeVoices)
    }

    @Test fun denseHitsStealQuietestVoicesWithoutOverflow() {
        val e = SynthEngine()
        for (i in 0 until 60) e.send(Cmd(Op.NOTE_ON, 9, 59, 127))   // 60 rides at once, pool is 48
        val out = e.renderOffline(8820)
        assertTrue(e.drumSteals >= 12)
        assertTrue(maxAbs(out) <= 32760)
    }

    @Test fun drumVoicesAlwaysEndByTheirLifeLimit() {
        val e = SynthEngine()
        for (n in 35..87) e.send(Cmd(Op.NOTE_ON, 9, n, 100))
        e.renderOffline(44100 * 4)                 // 4 s > longest life (crash 3 s) + fade
        assertEquals(0, e.activeVoices)
    }

    private fun rms(a: ShortArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return Math.sqrt(s / (to - from))
    }

    @Test fun crashCymbalsAreSwitchedOffAndNotCountedAsMissing() {
        for (n in intArrayOf(49, 57, 52, 55)) {
            val e = SynthEngine()
            e.send(Cmd(Op.NOTE_ON, 9, n, 127))
            val out = e.renderOffline(8820)
            assertEquals("note $n must be silent", 0, maxAbs(out))
            assertEquals(0, e.activeVoices)
            assertEquals("a muted cymbal is not a missing sound", 0, e.drumSilentHits)
        }
    }

    @Test fun rideCymbalStillRingsAndDecaysWithoutClipping() {
        val e = SynthEngine()
        e.send(Cmd(Op.NOTE_ON, 9, 51, 110))
        val out = e.renderOffline(44100 * 4)
        assertTrue("ride must sound", maxAbs(out, 0, 4410) > 300)
        assertTrue("ride must not clip", maxAbs(out) <= 32000)
        assertTrue("ride must be quieter after 2.5 s", rms(out, 220500, 221500) < rms(out, 2000, 3000) / 4)
        assertEquals(0, e.activeVoices)
    }

    @Test fun cowbellAndCongaSoundAndDifferFromEachOther() {
        val cow = SynthEngine(); cow.send(Cmd(Op.NOTE_ON, 9, 56, 110))
        val a = cow.renderOffline(22050)
        val cg = SynthEngine(); cg.send(Cmd(Op.NOTE_ON, 9, 62, 110))
        val b = cg.renderOffline(22050)
        assertTrue(maxAbs(a) > 300 && maxAbs(b) > 300)
        assertTrue("cowbell and conga must not clip", maxAbs(a) <= 32000 && maxAbs(b) <= 32000)
        var diff = 0L
        for (i in 0 until 4410) diff += Math.abs(a[i] - b[i])
        assertTrue("cowbell and conga must be different sounds", diff > 100000)
        // interleaved stereo: 0.25 s = frame 11025 = index 22050
        assertTrue("cowbell must be short", rms(a, 22050, 23050) < rms(a, 100, 1100) / 5)
    }

    @Test fun manyPadVoicesFreeUpFasterThanWithoutPressure() {
        val e = SynthEngine()
        e.send(Cmd(Op.PROGRAM, 0, 6))                       // Warm Pad, release time constant 1.2 s
        for (n in 40 until 80) e.send(Cmd(Op.NOTE_ON, 0, n, 100))   // 40 voices, above the pressure limit
        e.renderOffline(44100 * 7 / 10)
        for (n in 40 until 80) e.send(Cmd(Op.NOTE_OFF, 0, n))
        e.renderOffline(44100 * 42 / 10)                      // 4.2 s after release
        assertEquals("tails must be gone (needs 7.5 s without pressure handling)", 0, e.activeVoices)
    }

    @Test fun hiHatIsShortAndOpenHatRings() {
        val c = SynthEngine(); c.send(Cmd(Op.NOTE_ON, 9, 42, 110))
        val closed = c.renderOffline(22050)
        assertTrue(maxAbs(closed, 0, 2000) > 200)
        assertTrue("closed hat must be over after 0.2 s", rms(closed, 17000, 18000) < rms(closed, 100, 1100) / 20)
        val o = SynthEngine(); o.send(Cmd(Op.NOTE_ON, 9, 46, 110))
        val open = o.renderOffline(22050)
        assertTrue("open hat must still ring at 0.2 s", rms(open, 8800, 9800) > rms(open, 100, 1100) / 10)
    }
}
