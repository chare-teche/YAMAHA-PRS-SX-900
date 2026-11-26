package com.example.formats

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads what is REALLY inside an unknown or not-yet-supported file (ORG .style/.set/.template, Yamaha .SET ...)
 * and reports it. Nothing is guessed: it only states facts it read (magic bytes, chunk names, ZIP entries).
 * Pure Kotlin (java.io only), streaming: a big package is never loaded into memory (header 64 KB, ZIP entries are skipped).
 */
class InspectReport(val format: String, val lines: List<String>, val playable: Boolean) {
    fun text(): String = lines.joinToString("\n")
}

object FileInspector {
    private const val HEAD = 65536
    private const val MAX_ENTRIES = 40

    fun inspect(name: String, input: InputStream): InspectReport {
        val head = ByteArray(HEAD)
        var n = 0
        while (n < HEAD) { val r = input.read(head, n, HEAD - n); if (r <= 0) break; n += r }
        val out = ArrayList<String>()
        out.add("File: $name")
        if (n == 0) return InspectReport("empty", out + "The file is empty.", false)
        val h = head.copyOf(n)

        return when {
            startsWith(h, "MThd") -> smf(h, input, out)
            startsWith(h, "PK\u0003\u0004") -> zip(h, input, out)
            startsWith(h, "RIFF") -> {
                val kind = if (n >= 12) String(h, 8, 4, Charsets.ISO_8859_1) else "?"
                out.add("RIFF container, type '$kind'" + if (kind == "sfbk") " (SoundFont)" else if (kind == "WAVE") " (WAV audio)" else "")
                out.add("Not importable as a style.")
                InspectReport("RIFF " + kind, out, false)
            }
            looksLikeText(h) -> {
                out.add("Text file. First lines:")
                String(h, Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.take(5).forEach { out.add("  " + it.take(100)) }
                InspectReport("text", out, false)
            }
            else -> {
                out.add("Unknown binary format. First bytes: " + hex(h, 16))
                out.add("Magic text: '" + printable(h, 8) + "'")
                out.add("Send this file so its structure can be read; it is stored unchanged.")
                InspectReport("unknown", out, false)
            }
        }
    }

    fun inspect(name: String, bytes: ByteArray): InspectReport = inspect(name, ByteArrayInputStream(bytes))

    // ---------------------------------------------------------------- SMF
    /** Walks the chunk list of the whole file (streaming, skipping chunk bodies). Yamaha puts CASM after the tracks. */
    private fun smf(h: ByteArray, rest: InputStream, out: MutableList<String>): InspectReport {
        if (h.size < 14) { out.add("Standard MIDI header is truncated."); return InspectReport("smf-bad", out, false) }
        val fmt = u16(h, 8); val tracks = u16(h, 10); val div = u16(h, 12)
        out.add("Standard MIDI File: format $fmt, $tracks track(s), division $div")
        val markers = LinkedHashSet<String>()
        for (m in listOf("Intro A", "Intro B", "Main A", "Main B", "Main C", "Main D", "Fill In AA", "Ending A", "Break", "SInt", "Intro 1", "Main 1"))
            if (indexOf(h, m.toByteArray(Charsets.ISO_8859_1)) >= 0) markers.add(m)
        val chunks = LinkedHashMap<String, Int>()
        try {
            val all = java.io.SequenceInputStream(ByteArrayInputStream(h), rest)
            skipFully(all, 8L + ((h[4].toLong() and 0xFF) shl 24 or ((h[5].toLong() and 0xFF) shl 16) or ((h[6].toLong() and 0xFF) shl 8) or (h[7].toLong() and 0xFF)))
            val hd = ByteArray(8)
            while (true) {
                var got = 0
                while (got < 8) { val r = all.read(hd, got, 8 - got); if (r <= 0) break; got += r }
                if (got < 8) break
                val tag = String(hd, 0, 4, Charsets.ISO_8859_1)
                val len = ((hd[4].toLong() and 0xFF) shl 24) or ((hd[5].toLong() and 0xFF) shl 16) or ((hd[6].toLong() and 0xFF) shl 8) or (hd[7].toLong() and 0xFF)
                if (!tag.all { it.code in 32..126 } || chunks.size > 2000) { out.add("Chunk list ends at an unreadable tag."); break }
                chunks[tag] = (chunks[tag] ?: 0) + 1
                skipFully(all, len)
            }
        } catch (e: Exception) {
            out.add("Reading stopped: " + (e.message ?: e.javaClass.simpleName))
        }
        out.add("Chunks: " + chunks.entries.joinToString(", ") { it.key.trim() + (if (it.value > 1) " x" + it.value else "") })
        if (markers.isNotEmpty()) out.add("Section markers found: " + markers.joinToString(", "))
        val casm = chunks.containsKey("CASM")
        out.add(if (casm) "Has CASM: this is a Yamaha-type style, it can be imported."
        else "No CASM chunk: it is a plain MIDI file or a style with another layout. Not imported.")
        return InspectReport(if (casm) "Yamaha-type style" else "MIDI", out, casm)
    }

    private fun skipFully(i: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = i.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
            if (r <= 0) throw java.io.EOFException("file ends inside a chunk")
            left -= r
        }
    }

    // ---------------------------------------------------------------- ZIP package
    private fun zip(h: ByteArray, rest: InputStream, out: MutableList<String>): InspectReport {
        out.add("ZIP package")
        val names = ArrayList<String>()
        var total = 0
        var bytes = 0L
        try {
            val zin = ZipInputStream(java.io.SequenceInputStream(ByteArrayInputStream(h), rest))
            val buf = ByteArray(8192)
            while (true) {
                val e = zin.nextEntry ?: break
                total++
                var size = 0L
                while (true) { val r = zin.read(buf); if (r <= 0) break; size += r }   // stream and discard, no memory growth
                bytes += size
                if (names.size < MAX_ENTRIES) names.add(e.name + (if (e.isDirectory) "" else "  (" + size + " bytes)"))
            }
        } catch (e: Exception) {
            out.add("Reading stopped: " + (e.message ?: e.javaClass.simpleName))
        }
        out.add("$total entries, $bytes bytes unpacked")
        names.forEach { out.add("  " + it) }
        if (total > names.size) out.add("  ... and " + (total - names.size) + " more")
        val exts = names.map { it.substringBefore("  (").substringAfterLast('.', "").lowercase() }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.joinToString(", ") { it.key + " x" + it.value }
        if (exts.isNotEmpty()) out.add("Contents by type: $exts")
        return InspectReport("ZIP package", out, false)
    }

    // ---------------------------------------------------------------- helpers
    private fun startsWith(b: ByteArray, s: String): Boolean {
        if (b.size < s.length) return false
        for (i in s.indices) if (b[i].toInt() and 0xFF != s[i].code) return false
        return true
    }

    private fun u16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun indexOf(h: ByteArray, p: ByteArray): Int {
        var i = 0
        while (i <= h.size - p.size) {
            var j = 0
            while (j < p.size && h[i + j] == p[j]) j++
            if (j == p.size) return i
            i++
        }
        return -1
    }

    private fun looksLikeText(h: ByteArray): Boolean {
        val n = minOf(h.size, 512)
        var ok = 0
        for (i in 0 until n) { val c = h[i].toInt() and 0xFF; if (c == 9 || c == 10 || c == 13 || c in 32..126) ok++ }
        return ok * 100 / n >= 95
    }

    private fun hex(h: ByteArray, n: Int): String = (0 until minOf(n, h.size)).joinToString(" ") { String.format("%02X", h[it].toInt() and 0xFF) }
    private fun printable(h: ByteArray, n: Int): String = (0 until minOf(n, h.size)).map { val c = h[it].toInt() and 0xFF; if (c in 32..126) c.toChar() else '.' }.joinToString("")
}
