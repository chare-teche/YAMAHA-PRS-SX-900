package com.example.formats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MusicStudioStyleTest {
    private fun load(): ByteArray =
        javaClass.getResourceAsStream("/styles/tegri_notes_only.Style")!!.readBytes()

    @Test fun recognisesTheFormat() {
        assertTrue(MusicStudioStyle.isStyle(load()))
        assertFalse(MusicStudioStyle.isStyle(ByteArray(200)))
        assertFalse(MusicStudioStyle.isStyle("MThd".toByteArray()))
    }

    @Test fun readsSectionsTempoAndNotes() {
        val d = MusicStudioStyle.parse(load())
        assertEquals("TEGRI.S916", d.title)
        assertEquals(144, d.tempoBpm)
        val names = d.sections.map { it.name }
        assertTrue(names.containsAll(listOf("Main A", "Main B", "Fill In CC")))
        val a = d.sections.first { it.name == "Main A" }
        assertEquals(52, a.notes.size)
        assertEquals(2000L, a.endTick - a.startTick)
        assertTrue(a.notes.all { it.tick in 0..2000 && it.note in 0..127 && it.vel in 1..127 })
    }

    @Test fun convertsToAPlayableStyle() {
        val st = MusicStudioStyle.fromBytes(load(), "TEGRI.S916.Style")
        assertEquals(144, st.tempo)
        val mainA = com.example.audio.Sec.MAIN_A.ordinal
        assertEquals(1, st.sectionBars(mainA, 1))
        assertTrue(st.bar(mainA, 0).any { it.isNotEmpty() })
    }

    @Test fun rejectsGarbageWithAClearMessage() {
        try { MusicStudioStyle.parse(ByteArray(300)); fail() } catch (e: FormatException) { assertTrue(e.message!!.isNotEmpty()) }
    }

    @Test fun truncatedFileDoesNotCrash() {
        val b = load()
        try { MusicStudioStyle.parse(b.copyOf(b.size / 2)) } catch (e: FormatException) { /* fine */ }
    }
}
