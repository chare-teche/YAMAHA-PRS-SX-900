package com.example.formats

import com.example.audio.Sec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests with real Yamaha SFF2 style files (copied to src/test/resources/sty).
 * Expected numbers were produced by an independent Python reader of the same files
 * (tools/verify_sty.py in the project notes), not by this code.
 */
class StyParserTest {
    private fun load(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("sty/$name")!!.readBytes()

    private fun smf(vararg track: Int): ByteArray {
        val body = track.map { it.toByte() }.toByteArray()
        val hdr = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(), 0, 0, 0, 6, 0, 1, 0, 1, 0x01, 0xE0.toByte(),
            'M'.code.toByte(), 'T'.code.toByte(), 'r'.code.toByte(), 'k'.code.toByte(), 0, 0, 0, body.size.toByte()
        )
        return hdr + body
    }

    @Test fun midiReader_runningStatusAndVelocityZeroOff() {
        // note on 60, running-status note-on 60 velocity 0 (= off), end of track
        val d = MidiReader.read(smf(0x00, 0x90, 0x3C, 0x40, 0x10, 0x3C, 0x00, 0x00, 0xFF, 0x2F, 0x00))
        val ev = d.tracks[0].events
        assertEquals(2, ev.size)
        assertTrue(MidiReader.isNoteOn(ev[0]))
        assertTrue(MidiReader.isNoteOff(ev[1]))
        assertEquals(16L, ev[1].tick)
        assertEquals(480, d.division)
    }

    @Test fun midiReader_rejectsGarbage() {
        try { MidiReader.read(ByteArray(40) { 7 }); fail("should throw") } catch (e: FormatException) { }
        try { MidiReader.read(ByteArray(3)); fail("should throw") } catch (e: FormatException) { }
    }

    @Test fun plainMidi_isNotAStyle() {
        val bytes = smf(0x00, 0x90, 0x3C, 0x40, 0x10, 0x80, 0x3C, 0x00, 0x00, 0xFF, 0x2F, 0x00)
        assertTrue(!StyParser.isStyle(bytes))
        try { StyParser.parse(bytes); fail("should throw") } catch (e: FormatException) { assertTrue(e.message!!.contains("SFF")) }
    }

    @Test fun disko_basicsAndSections() {
        val d = StyParser.parse(load("DISKO_2018_S362.STY"))
        assertEquals("SFF2", d.format)
        assertEquals(117, d.tempoBpm)
        assertEquals(4, d.tsNum); assertEquals(4, d.tsDen)
        assertEquals(1920, d.ppq)
        assertTrue(d.hasCasm)
        val byName = d.sections.associateBy { it.name }
        assertEquals(setOf("Main A", "Main B", "Main C"), byName.keys)
        assertEquals(402, byName["Main A"]!!.notes.size)
        assertEquals(418, byName["Main B"]!!.notes.size)
        assertEquals(362, byName["Main C"]!!.notes.size)
        // every note lies inside its section
        for (s in d.sections) for (n in s.notes) assertTrue(n.tick >= 0 && n.tick < s.endTick - s.startTick && n.len >= 1)
    }

    @Test fun reggae_casmAndOts() {
        val d = StyParser.parse(load("REGGAE_1_S362.STY"))
        assertEquals(87, d.tempoBpm)
        val byName = d.sections.associateBy { it.name }
        assertEquals(443, byName["Main A"]!!.notes.size)
        assertEquals(22, byName["Ending B"]!!.notes.size)
        assertEquals(36, byName["Fill In AA"]!!.notes.size)
        // Main C uses Rhythm2..Phrase2 only (no Rhythm1 rule) in this file
        assertEquals(listOf(9, 10, 11, 12, 13, 14, 15), byName["Main C"]!!.casm.map { it.dest }.sorted())
        // rhythm rules are never transposed, chord / pad rules are
        val rhythm = byName["Main A"]!!.casm.first { it.dest == 8 }
        val pad = byName["Main A"]!!.casm.first { it.dest == 13 }
        assertEquals(0, rhythm.ntt)
        assertTrue(pad.ntt != 0)
        // One Touch Settings: OTS 1 has program 3 on right 1 and 26 on right 2
        assertEquals(4, d.ots.size)
        assertEquals(3, d.ots[0][0])
        assertEquals(26, d.ots[0][1])
    }

    @Test fun slow4_eightBarMainD_andFillBars() {
        val st = StyleConverter.fromBytes(load("_SLOW_4_S362.STY"), "_SLOW_4_S362.STY")
        assertEquals("SLOW 4", st.name)
        assertEquals(88, st.tempo)
        assertEquals(8, st.sectionBars(Sec.MAIN_D.ordinal, 2))
        assertEquals(4, st.sectionBars(Sec.MAIN_A.ordinal, 2))
        assertEquals(1, st.sectionBars(Sec.FILL_A.ordinal, 9))
    }

    @Test fun converter_fillsMissingSectionsAndKeepsEveryNote() {
        val st = StyleConverter.fromBytes(load("DISKO_2018_S362.STY"), "DISKO_2018_S362.STY")
        assertEquals(96, st.stepsPerBar)
        assertEquals(24, st.beatSteps)
        // Main D is missing in the file: it plays Main C
        assertEquals(4, st.sectionBars(Sec.MAIN_D.ordinal, 2))
        var count = 0
        for (b in 0 until 4) for (step in st.bar(Sec.MAIN_A.ordinal, b)) for (ev in step) {
            count++
            assertTrue(ev.abs)
            assertTrue(ev.track in 0..7)
            assertTrue(ev.vel in 1..127)
            assertTrue(ev.step in 0 until 96)
        }
        assertEquals(402, count)
    }

    @Test fun waltzFile_importsAndLongIntroKeepsLength() {
        val st = StyleConverter.fromBytes(load("_WALTZ_1_S362.STY"), "_WALTZ_1_S362.STY")
        assertEquals(22, st.sectionBars(Sec.INTRO_3.ordinal, 2))
        assertTrue(st.info.contains("MAIN A"))
    }

    @Test fun damagedFile_neverCrashesWithOtherExceptions() {
        val full = load("REGGAE_1_S362.STY")
        for (cut in intArrayOf(20, 500, 5000, 20000, full.size - 1)) {
            try {
                StyleConverter.fromBytes(full.copyOf(cut), "x.sty")
            } catch (e: FormatException) {
                // fine: clear message
                assertTrue(e.message!!.isNotEmpty())
            }
        }
    }
}

/** Drum import report on real Ethiopian styles. Expected figures come from the independent Python reader (tools/). */
class StyDrumReportTest {
    private fun load(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("sty/$name")!!.readBytes()

    @Test fun guragePatternsUseNote24AndItIsReportedNotHidden() {
        val st = StyleConverter.convert(StyParser.parse(load("AB_GURA.S260.STY")), "AB_GURA.S260.STY")
        // Python reference: note 24 is the most used silent note in this style (about 74 hits)
        assertTrue(st.info, Regex("note 24 x[0-9]+").containsMatchIn(st.info))
        assertTrue(st.info.contains("no sound in this engine"))
    }

    @Test fun everyRealStyleGetsADrumReportLine() {
        for (n in listOf("AB_GURA.S260.STY", "Gurage.T153.STY", "ChaChaCha.T158.STY", "REGGAE_1_S362.STY")) {
            val st = StyleConverter.convert(StyParser.parse(load(n)), n)
            assertTrue(n, st.info.contains("Drums:"))
            assertTrue(n, st.drumKit in 0 until com.example.audio.DrumKit.COUNT)
        }
    }
}
