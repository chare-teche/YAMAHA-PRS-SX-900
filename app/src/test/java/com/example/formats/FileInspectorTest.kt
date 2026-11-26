package com.example.formats

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileInspectorTest {
    private fun load(name: String): ByteArray = javaClass.classLoader!!.getResourceAsStream("sty/$name")!!.readBytes()

    @Test fun realYamahaStyleIsRecognisedAsPlayable() {
        val r = FileInspector.inspect("AB_GURA.S260.STY", load("AB_GURA.S260.STY"))
        assertTrue(r.text(), r.playable)
        assertTrue(r.text().contains("CASM"))
    }

    @Test fun zipPackageEntriesAreListedWithoutInventingAnything() {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z ->
            z.putNextEntry(ZipEntry("styles/Disco.sty")); z.write(ByteArray(100) { 1 }); z.closeEntry()
            z.putNextEntry(ZipEntry("voices/Piano.wav")); z.write(ByteArray(2000) { 2 }); z.closeEntry()
        }
        val r = FileInspector.inspect("pack.set", bo.toByteArray())
        assertEquals("ZIP package", r.format)
        assertFalse(r.playable)
        val t = r.text()
        assertTrue(t, t.contains("2 entries"))
        assertTrue(t, t.contains("styles/Disco.sty  (100 bytes)"))
        assertTrue(t, t.contains("wav x1"))
    }

    @Test fun unknownBinaryShowsMagicBytes() {
        val r = FileInspector.inspect("x.set", byteArrayOf(0x4F, 0x52, 0x47, 0x31, 0, 1, 2, 3))
        assertEquals("unknown", r.format)
        assertTrue(r.text().contains("4F 52 47 31"))
        assertTrue(r.text().contains("'ORG1"))
    }

    @Test fun emptyAndTextFilesAreHandled() {
        assertEquals("empty", FileInspector.inspect("e.style", ByteArray(0)).format)
        val t = FileInspector.inspect("a.style", "[Style]\nName=Test\n".toByteArray())
        assertEquals("text", t.format)
        assertTrue(t.text().contains("Name=Test"))
    }

    @Test fun plainMidiWithoutCasmIsNotPlayable() {
        val smf = byteArrayOf(0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 1, 0, 1, 1, -32)
        val r = FileInspector.inspect("song.mid", smf)
        assertFalse(r.playable)
        assertTrue(r.text().contains("format 1, 1 track(s), division 480"))
    }
}
