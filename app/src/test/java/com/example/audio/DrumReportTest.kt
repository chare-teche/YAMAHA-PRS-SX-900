package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrumReportTest {
    @Test fun countsSilentNotesAndSortsThemByUse() {
        val notes = listOf(36, 36, 42, 24, 24, 24, 29, 100)
        val r = DrumReport.build(notes, -1)
        assertEquals(8, r.totalHits)
        assertEquals(5, r.silentHits)
        assertEquals(listOf(24 to 3, 29 to 1, 100 to 1), r.silentNotes)
    }

    @Test fun reportNamesKnownKitAndStaysHonest() {
        val r = DrumReport.build(listOf(36, 38), 24)
        val text = r.lines().joinToString("\n")
        assertTrue(text.contains("Electro"))
        assertTrue(text.contains("not the original kit"))
        assertTrue(text.contains("every drum note has a sound"))
    }

    @Test fun unknownKitProgramIsReportedNotGuessed() {
        val r = DrumReport.build(listOf(36), 87)
        assertEquals(DrumKit.STANDARD, r.kitId)
        assertTrue(r.lines().joinToString("\n").contains("product-specific"))
    }

    @Test fun emptyStyleHasNoDrumsLine() {
        assertEquals(listOf("Drums: no drum notes"), DrumReport.build(emptyList(), -1).lines())
    }
}
