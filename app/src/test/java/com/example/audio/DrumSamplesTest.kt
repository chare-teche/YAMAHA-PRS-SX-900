package com.example.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the recorded-drum loader, the note -> sample mapping and sample playback in the real engine (offline). */
class DrumSamplesTest {
    private val set = DrumSampleSet.default

    private fun maxAbs(a: ShortArray, from: Int = 0, to: Int = a.size): Int {
        var m = 0
        for (i in from until to) m = maxOf(m, Math.abs(a[i].toInt()))
        return m
    }
    private fun rms(a: ShortArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return Math.sqrt(s / (to - from))
    }
    private fun synth(): SynthEngine = SynthEngine().also { it.drumSamples = DrumSampleSet.EMPTY }

    // ---- loader ----
    @Test fun packagedFileLoadsAndCoversTheGeneralMidiPercussionRange() {
        assertTrue("sample file must load", set.noteCount >= 50)
        for (n in 35..87) assertNotNull("note $n has no sample", set.get(n))
        assertNull(set.get(24))
        assertNull(set.get(200))
    }

    @Test fun everySampleIsCleanBoundedAndFadesToSilence() {
        for (n in set.notes()) {
            val s = set.get(n)!!
            assertTrue("rate of note $n", s.rate in 20000..52000 * 2)
            assertTrue("note $n shorter than 3 s", s.seconds in 0.02f..3f)
            var peak = 0
            for (x in s.pcm) peak = maxOf(peak, Math.abs(x.toInt()))
            assertTrue("note $n peak $peak must leave headroom", peak in 2000..31500)
            val tail = Math.abs(s.pcm[s.pcm.size - 1].toInt())
            assertTrue("note $n must end at silence, ends at $tail", tail <= 40)
            assertTrue("note $n must start near silence", Math.abs(s.pcm[0].toInt()) <= 2000)
        }
    }

    @Test fun memoryStaysSmall() {
        assertTrue("sample memory ${set.totalBytes}", set.totalBytes < 4_000_000)
    }

    @Test fun differentInstrumentsAreDifferentSamples() {
        assertTrue(set.get(36)!!.pcm !== set.get(38)!!.pcm)
        assertTrue(set.get(42)!!.pcm !== set.get(46)!!.pcm)
        assertTrue(set.get(56)!!.pcm !== set.get(62)!!.pcm)
        assertTrue(set.get(53)!!.pcm !== set.get(51)!!.pcm)
        // closed hat is cut short, open hat rings
        assertTrue(set.get(42)!!.seconds < 0.2f)
        assertTrue(set.get(46)!!.seconds > 0.8f)
        // pitched variants of one recording are played at different rates
        assertTrue(set.get(47)!!.rate > set.get(45)!!.rate)
        assertTrue(set.get(68)!!.rate < set.get(67)!!.rate)
    }

    @Test fun brokenOrEmptyFilesGiveAnEmptySetWithoutCrashing() {
        assertEquals(0, DrumSampleSet.parse(ByteArrayInputStream(ByteArray(0))).noteCount)
        assertEquals(0, DrumSampleSet.parse(ByteArrayInputStream("NOPE-not-a-drum-file".toByteArray())).noteCount)
        val trunc = ByteArrayOutputStream(); DataOutputStream(trunc).apply { writeBytes("EDR1"); writeInt(1); writeInt(44100); writeInt(5000) }
        assertEquals(0, DrumSampleSet.parse(ByteArrayInputStream(trunc.toByteArray())).noteCount)
    }

    @Test fun smallHandMadeFileParsesAndSharesSounds() {
        val b = ByteArrayOutputStream(); val d = DataOutputStream(b)
        d.writeBytes("EDR1"); d.writeInt(1); d.writeInt(44100); d.writeInt(3)
        d.writeShort(100); d.writeShort(-200); d.writeShort(300)
        d.writeInt(2)
        for (note in intArrayOf(36, 35)) { d.writeByte(note); d.writeShort(0); val nm = "kick".toByteArray(); d.writeShort(nm.size); d.write(nm) }
        val s = DrumSampleSet.parse(ByteArrayInputStream(b.toByteArray()))
        assertEquals(2, s.noteCount)
        assertEquals(listOf<Short>(100, -200, 300), s.get(36)!!.pcm.toList())
        assertTrue(s.get(35)!!.pcm === s.get(36)!!.pcm)
        assertEquals("kick", s.get(35)!!.name)
    }

    // ---- MIDI mapping stays consistent with the synthesized kinds ----
    @Test fun everySampledNoteIsAMappedDrumNote() {
        for (n in set.notes()) assertTrue("note $n has a sample but is silent in DrumMap", DrumMap.kindOf(n) != DrumMap.SILENT)
    }

    @Test fun mappedFamiliesUseTheRightKindOfSound() {
        assertEquals("BD", set.get(35)!!.name.take(2)); assertEquals("BD", set.get(36)!!.name.take(2))
        assertTrue(set.get(38)!!.name.startsWith("SD")); assertTrue(set.get(40)!!.name.startsWith("SD"))
        assertTrue(set.get(42)!!.name.contains("Closed")); assertTrue(set.get(46)!!.name.contains("Open"))
        assertTrue(set.get(51)!!.name.contains("Ride"))
        assertTrue(set.get(56)!!.name.contains("Cowbell"))
        assertTrue(set.get(54)!!.name.contains("Tambourine"))
        assertTrue(set.get(82)!!.name.contains("Cabasa"))
        assertTrue(set.get(63)!!.name.contains("Conga"))
    }

    // ---- playback in the real engine ----
    @Test fun sampledDrumsSoundAndNeverClip() {
        for (n in 35..87) {
            if (DrumMap.isMuted(n)) continue
            val e = SynthEngine()
            e.send(Cmd(Op.NOTE_ON, 9, n, 110))
            val out = e.renderOffline(44100)
            assertTrue("note $n must sound", maxAbs(out, 0, 8820) > 150)
            assertTrue("note $n must not clip", maxAbs(out) <= 32000)
        }
    }

    @Test fun velocityChangesLoudnessSmoothly() {
        val soft = SynthEngine(); soft.send(Cmd(Op.NOTE_ON, 9, 38, 30))
        val loud = SynthEngine(); loud.send(Cmd(Op.NOTE_ON, 9, 38, 120))
        val a = maxAbs(soft.renderOffline(4410)); val b = maxAbs(loud.renderOffline(4410))
        assertTrue("soft $a loud $b", b > a * 3)
    }

    @Test fun retriggeringTheSameNoteDoesNotStopTheFirstHit() {
        val e = SynthEngine()
        e.send(Cmd(Op.NOTE_ON, 9, 51, 100)); e.renderOffline(4410)
        e.send(Cmd(Op.NOTE_ON, 9, 51, 100)); e.renderOffline(256)
        assertTrue(e.activeVoices >= 2)
    }

    @Test fun noteOffDoesNotCutAOneShotDrum() {
        val e = SynthEngine()
        e.send(Cmd(Op.NOTE_ON, 9, 51, 100)); e.renderOffline(1000)
        e.send(Cmd(Op.NOTE_OFF, 9, 51)); e.renderOffline(1000)
        assertTrue(e.activeVoices >= 1)
    }

    @Test fun closedHatChokesSampledOpenHatAndPanicClearsEverything() {
        val e = SynthEngine()
        e.send(Cmd(Op.NOTE_ON, 9, 46, 100)); e.renderOffline(2205)
        e.send(Cmd(Op.NOTE_ON, 9, 42, 100)); e.renderOffline(22300)
        assertEquals(0, e.activeVoices)
        for (n in 35..87) e.send(Cmd(Op.NOTE_ON, 9, n, 100))
        e.renderOffline(512)
        e.send(Cmd(Op.PANIC)); e.renderOffline(4410)
        assertEquals(0, e.activeVoices)
    }

    @Test fun denseRhythmDoesNotOverflowAndEveryVoiceEnds() {
        val e = SynthEngine()
        for (i in 0 until 100) { e.send(Cmd(Op.NOTE_ON, 9, 35 + (i % 53), 127)); if (i % 10 == 0) e.renderOffline(300) }
        val out = e.renderOffline(44100 * 4)
        assertTrue(maxAbs(out) <= 32760)
        assertEquals(0, e.activeVoices)
    }

    @Test fun missingSampleFallsBackToTheSynthesizedDrum() {
        val e = synth(); e.send(Cmd(Op.NOTE_ON, 9, 34, 100))   // note 34 has no recording
        assertTrue(maxAbs(e.renderOffline(4410)) > 100)
        val k = SynthEngine(); k.send(Cmd(Op.NOTE_ON, 9, 34, 100))
        assertTrue(maxAbs(k.renderOffline(4410)) > 100)
    }
}
