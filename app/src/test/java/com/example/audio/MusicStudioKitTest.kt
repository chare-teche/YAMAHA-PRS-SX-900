package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Importing a Music Studio .Instrument drum kit, and playing it through the real engine. */
class MusicStudioKitTest {
    private fun bytes(name: String): ByteArray =
        MusicStudioKitTest::class.java.getResourceAsStream("/kits/$name")!!.use { it.readBytes() }
    private fun maxAbs(a: ShortArray, from: Int = 0, to: Int = a.size): Int {
        var m = 0
        for (i in from until to) m = maxOf(m, Math.abs(a[i].toInt()))
        return m
    }

    @Test fun readsEveryRecordOfASmallKit() {
        val b = bytes("modern_studio_kit.Instrument")
        assertEquals(3, MusicStudioKit.recordCount(b))
        val set = MusicStudioKit.parse(b)!!
        assertEquals(listOf(36, 37, 38), set.notes())
        assertEquals(44100, set.get(36)!!.rate)
        assertEquals(9950, set.get(36)!!.frames)
    }

    @Test fun readsMonoAndStereoRecordsAndKeepsTheirNames() {
        val set = MusicStudioKit.parse(bytes("room_kit_gm.Instrument"))!!
        assertEquals(listOf(35, 36, 38, 46, 49, 56), set.notes())
        assertEquals("HH2 Open p", set.get(46)!!.name)
        assertEquals(48000, set.get(46)!!.rate)
        assertEquals(81627, set.get(38)!!.frames)       // stereo record, mixed to mono
    }

    @Test fun importedSamplesAreCleanAndEndInSilence() {
        for (f in arrayOf("modern_studio_kit.Instrument", "room_kit_gm.Instrument")) {
            val set = MusicStudioKit.parse(bytes(f))!!
            for (n in set.notes()) {
                val s = set.get(n)!!
                assertTrue("peak of note $n", s.pcm.maxOf { Math.abs(it.toInt()) } in 100..31500)
                assertTrue("note $n must end in silence", Math.abs(s.pcm[s.pcm.size - 1].toInt()) <= 40)
            }
        }
    }

    @Test fun notAKitGivesNullNotAGuess() {
        assertNull(MusicStudioKit.parse(ByteArray(0)))
        assertNull(MusicStudioKit.parse("MThd this is a midi file".toByteArray()))
        assertNull(MusicStudioKit.parse(ByteArray(5000) { (it * 7).toByte() }))
        assertEquals(0, MusicStudioKit.recordCount(ByteArray(200)))
    }

    @Test fun kitOverlaysTheBuiltInDrumsNoteByNote() {
        val kit = MusicStudioKit.parse(bytes("room_kit_gm.Instrument"))!!
        val mix = DrumSampleSet.default.overlay(kit)
        assertEquals(kit.get(36)!!.name, mix.get(36)!!.name)            // replaced
        assertEquals(DrumSampleSet.default.get(42)!!.name, mix.get(42)!!.name)   // kept
        assertTrue(mix.noteCount >= DrumSampleSet.default.noteCount)
    }

    @Test fun engineSoundsTheImportedKitAndFallsBackForOtherNotes() {
        val kit = MusicStudioKit.parse(bytes("room_kit_gm.Instrument"))!!
        val e = SynthEngine()
        e.setDrumSamples(DrumSampleSet.default.overlay(kit))
        e.send(Cmd(Op.NOTE_ON, 9, 36, 110))
        val kick = e.renderOffline(22050)
        assertTrue("kit kick must sound", maxAbs(kick, 0, 4410) > 300)
        assertTrue(maxAbs(kick) <= 32000)
        e.send(Cmd(Op.NOTE_ON, 9, 42, 110))        // not in the kit: built-in closed hat
        assertTrue(maxAbs(e.renderOffline(4410)) > 200)
        e.setDrumSamples(DrumSampleSet.EMPTY)       // an empty set never silences the drums
        e.send(Cmd(Op.NOTE_ON, 9, 38, 110))
        assertTrue(maxAbs(e.renderOffline(4410)) > 200)
    }

    @Test fun crashInAnImportedKitStaysOff() {
        val kit = MusicStudioKit.parse(bytes("room_kit_gm.Instrument"))!!
        assertNotNull(kit.get(49))                  // the kit has a crash recording ...
        val e = SynthEngine()
        e.setDrumSamples(DrumSampleSet.default.overlay(kit))
        e.send(Cmd(Op.NOTE_ON, 9, 49, 127))
        assertEquals(0, maxAbs(e.renderOffline(8820)))   // ... but crash is muted for every kit
    }
}
