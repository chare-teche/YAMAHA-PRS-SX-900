package com.example.formats

/**
 * Reader for "Music Studio Rhythm" style files (.Style). Pure Kotlin, unit tested on a real (sample-stripped) file.
 *
 * Layout (little endian), worked out from real files:
 *   0x00  "Music Studio Rhythm\r1.4.6"
 *   0x20  u32 record count, then that many 140-byte records starting at 0x24.
 *         Record 0 = the style itself (name at +9). Others = sections ("Variation 1", "Fill 2", "Intro 1", "Ending 1", "Break").
 *         record: [id:4][style id:4][name:32 @+9]... [length in ticks:u16 @+0x30][tempo:u16 @+0x3e]
 *   then  a u32, then blocks:
 *     track block:      [len:u32]["Name\r1.4.6" or "Name\r1.4.6\rBank: B Program: P"][section id:4][0x20F bytes][count:u32][count x 24-byte events]
 *     instrument block: [len:u32]["Name\r1.4.6"][0x93 bytes][embedded samples] (skipped here; the app plays its own voices)
 *   event = six u32: [2 = note on | 1 = note off][event id][note][velocity][tick][length]
 * 500 ticks = one quarter note. Time signature is assumed 4/4 (not found in the file yet); reported to the user.
 */
object MusicStudioStyle {
    const val PPQ = 500
    private const val REC = 0x8c
    private const val MAX_BARS = 32

    private fun u16(d: ByteArray, o: Int) = (d[o].toInt() and 255) or ((d[o + 1].toInt() and 255) shl 8)
    private fun u32(d: ByteArray, o: Int): Long = u16(d, o).toLong() or (u16(d, o + 2).toLong() shl 16)

    private val MAGIC = "Music Studio Rhythm".toByteArray(Charsets.US_ASCII)

    fun isStyle(b: ByteArray): Boolean {
        if (b.size < 0x60) return false
        for (i in MAGIC.indices) if (b[i] != MAGIC[i]) return false
        return true
    }

    private fun readStr(d: ByteArray, p: Int): String? {
        if (p < 0 || p + 4 > d.size) return null
        val n = u32(d, p)
        if (n < 6 || n > 120 || p + 4 + n > d.size) return null
        val len = n.toInt()
        var cr = false
        for (i in 0 until len) {
            val c = d[p + 4 + i].toInt() and 255
            if (c == 13) cr = true
            if ((c < 32 && c != 13) || c > 126) return null
        }
        val s = String(d, p + 4, len, Charsets.US_ASCII)
        return if (cr && s.contains("\r1.4.6")) s else null
    }

    /** end offset of an embedded sample record chain starting at the first record's note byte, or -1. */
    private fun sampleEnd(d: ByteArray, noteByte: Int): Int {
        var p = noteByte + 1
        var end = -1
        var guard = 0
        while (guard++ < 4096) {
            val e = recordEnd(d, p)
            if (e < 0) break
            end = e
            p = e + 1
        }
        return end
    }

    private fun recordEnd(d: ByteArray, p: Int): Int {
        if (p < 1 || p + 0x60 > d.size) return -1
        if ((d[p - 1].toInt() and 255) > 127) return -1
        var z = -1
        for (i in 0 until 32) if (d[p + i].toInt() == 0) { z = i; break }
        if (z <= 0) return -1
        for (i in 0 until z) { val c = d[p + i].toInt() and 255; if (c < 32 || c >= 127) return -1 }
        for (i in z until 32) if (d[p + i].toInt() != 0) return -1
        val rate = u16(d, p + 0x20)
        if (rate !in 4000..192000) return -1
        val bits = d[p + 0x22].toInt() and 255
        val ch = d[p + 0x23].toInt() and 255
        if ((bits != 8 && bits != 16) || (ch != 1 && ch != 2)) return -1
        val frames = u32(d, p + 0x2c)
        if (frames <= 0L) return -1
        val end = p.toLong() + 0x60 + frames * ch * (bits / 8)
        return if (end > d.size) -1 else end.toInt()
    }

    private class Sec(val id: String, val name: String, val lenTicks: Int, val tempo: Int)
    private class Blk(val secId: String, val track: String, val bank: Int, val program: Int, val notes: List<StyNote>)

    private fun idHex(d: ByteArray, o: Int): String {
        val sb = StringBuilder()
        for (i in 0 until 4) sb.append(String.format("%02x", d[o + i].toInt() and 255))
        return sb.toString()
    }

    private fun cleanName(s: String): String = s.substringBefore('\r').trim()

    /** Music Studio section name -> the Yamaha marker name used by the existing converter. */
    internal fun yamahaName(n: String): String? {
        val m = Regex("^(Variation|Fill|Intro|Ending)\\s*(\\d)$").find(n.trim())
        if (m != null) {
            val k = m.groupValues[2].toInt()
            val letter = "ABCD".getOrNull(k - 1) ?: return null
            return when (m.groupValues[1]) {
                "Variation" -> "Main $letter"
                "Fill" -> "Fill In $letter$letter"
                "Intro" -> if (k <= 3) "Intro $letter" else null
                else -> if (k <= 3) "Ending $letter" else null
            }
        }
        return if (n.trim().equals("Break", true)) "Fill In BA" else null
    }

    private fun destFor(track: String): Int {
        val t = track.lowercase()
        return when {
            t.startsWith("drum") -> 8
            t.startsWith("perc") -> 9
            t.startsWith("bass") -> 10
            t.startsWith("acc") -> {
                val n = t.filter { it.isDigit() }.toIntOrNull() ?: return -1
                if (n in 1..5) 10 + n else -1
            }
            else -> -1
        }
    }

    fun parse(d: ByteArray): StyData {
        if (!isStyle(d)) throw FormatException("Not a Music Studio Rhythm style file")
        val warnings = ArrayList<String>()
        val n = u32(d, 0x20).toInt()
        if (n < 2 || n > 64 || 0x24 + n * REC + 8 > d.size) throw FormatException("Style header is damaged")
        val title = String(d, 0x24 + 9, 32, Charsets.US_ASCII).substringBefore('\u0000').trim()
        val secs = ArrayList<Sec>()
        for (i in 1 until n) {
            val o = 0x24 + i * REC
            val nm = String(d, o + 9, 32, Charsets.US_ASCII).substringBefore('\u0000').trim()
            secs.add(Sec(idHex(d, o), nm, u16(d, o + 0x30), u16(d, o + 0x3e)))
        }

        // ---- walk the blocks (unknown stretches, e.g. instrument-setting records, are skipped by searching for the next valid block) ----
        val known = HashSet<String>(); for (sc in secs) known.add(sc.id)
        val blocks = ArrayList<Blk>()
        var skippedInstruments = 0
        var p = 0x24 + n * REC + 4
        while (p + 8 < d.size) {
            val s = readStr(d, p)
            var next = -1
            if (s != null) {
                val len = u32(d, p).toInt()
                val body = p + 4 + len
                val se = if (body + 0x95 < d.size) sampleEnd(d, body + 0x93) else -1
                if (se >= 0) {
                    skippedInstruments++
                    next = se
                } else if (body + 0x217 <= d.size && destFor(cleanName(s)) >= 0 && known.contains(idHex(d, body))) {
                    val cntOff = body + 0x213
                    val cnt = u32(d, cntOff)
                    if (cnt <= 20000L && cntOff + 4 + cnt * 24 <= d.size) {
                        val c = cnt.toInt()
                        val bank = Regex("Bank:\\s*(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                        val prog = Regex("Program:\\s*(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                        val dest = destFor(cleanName(s))
                        val notes = ArrayList<StyNote>(c / 2 + 1)
                        for (i in 0 until c) {
                            val e = cntOff + 4 + i * 24
                            if (u32(d, e) != 2L) continue
                            val note = u32(d, e + 8).toInt()
                            val vel = u32(d, e + 12).toInt()
                            val tick = u32(d, e + 16)
                            val ln = u32(d, e + 20)
                            if (note !in 0..127 || vel !in 1..127 || tick > 1_000_000L) continue
                            notes.add(StyNote(tick, if (ln in 1..200000L) ln else 120L, dest, note, vel))
                        }
                        blocks.add(Blk(idHex(d, body), cleanName(s), bank, prog, notes))
                        next = cntOff + 4 + c * 24
                    }
                }
            }
            if (next >= 0) { p = next; continue }
            p++      // not a block here: move on until the next valid one
        }
        if (blocks.isEmpty()) throw FormatException("No note data found in this style file")

        // ---- group blocks by section ----
        val outSecs = ArrayList<StySection>()
        val setupProg = IntArray(16) { -1 }
        val setupVol = IntArray(16) { -1 }
        val seenNames = HashSet<String>()
        var tempo = 0
        var unknownTracks = 0
        for (sec in secs) {
            val yn = yamahaName(sec.name) ?: continue
            if (!seenNames.add(yn)) continue
            val mine = blocks.filter { it.secId == sec.id }
            val notes = ArrayList<StyNote>()
            val prog = IntArray(16) { -1 }
            var maxTick = 0L
            for (b in mine) {
                val dest = destFor(b.track)
                if (dest < 0) { unknownTracks++; continue }
                if (b.program >= 0) { prog[dest] = b.program; if (setupProg[dest] < 0) setupProg[dest] = b.program }
                for (nn in b.notes) {
                    val t = StyNote(nn.tick, nn.len, dest, nn.note, nn.vel)
                    notes.add(t)
                    if (nn.tick > maxTick) maxTick = nn.tick
                }
            }
            if (notes.isEmpty()) continue
            val bar = PPQ * 4
            var len = sec.lenTicks
            if (len < bar || len % bar != 0 || len > MAX_BARS * bar) len = (((maxTick / bar) + 1) * bar).toInt().coerceIn(bar, MAX_BARS * bar)
            if (tempo == 0 && sec.tempo in 30..600 && yn.startsWith("Main")) tempo = sec.tempo
            outSecs.add(StySection(yn, 0L, len.toLong(), notes, casm(), prog, IntArray(16) { -1 }))
        }
        if (outSecs.isEmpty()) throw FormatException("No Variation sections with notes found in this style")
        if (tempo == 0) tempo = secs.firstOrNull { it.tempo in 30..600 }?.tempo ?: 120
        if (tempo > 240) { tempo /= 2; warnings.add("tempo in file looked doubled; using " + tempo + " BPM") }
        if (tempo < 40) tempo = 40
        warnings.add("time signature assumed 4/4; sounds are the app's own voices (embedded samples are not played)")
        if (skippedInstruments > 0) warnings.add(skippedInstruments.toString() + " embedded instrument(s) skipped")
        if (unknownTracks > 0) warnings.add(unknownTracks.toString() + " track block(s) with unknown names skipped")
        return StyData("MSR", if (title.isEmpty()) "Music Studio style" else title, tempo, 4, 4, PPQ, outSecs, setupProg, setupVol,
            emptyList(), false, warnings)
    }

    private fun casm(): List<CasmEntry> = (8..15).map { ch ->
        CasmEntry(ch, "Ch " + (ch + 1), ch, 0, if (ch <= 9) 0 else 2, false, 5, 0, 127, 1)
    }

    fun fromBytes(bytes: ByteArray, fileName: String) = StyleConverter.convert(parse(bytes), fileName)
}
