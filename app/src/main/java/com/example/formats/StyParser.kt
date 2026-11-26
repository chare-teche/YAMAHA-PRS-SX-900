package com.example.formats

/** CASM channel rule for one section (from a Ctb2 / Ctab record). */
class CasmEntry(
    val src: Int,          // source channel in the style data (0-15)
    val name: String,
    val dest: Int,         // destination channel 8..15 = Rhythm1, Rhythm2, Bass, Chord1, Chord2, Pad, Phrase1, Phrase2
    val srcRoot: Int,      // source chord root (0 = C)
    val ntt: Int,          // 0 = bypass (rhythm), else transposed with the chord
    val bassOn: Boolean,
    val highKey: Int,
    val lo: Int,
    val hi: Int,
    val rtr: Int
)

class StyNote(
    @JvmField val tick: Long,   // relative to the section start
    @JvmField val len: Long,
    @JvmField val ch: Int,
    @JvmField val note: Int,
    @JvmField val vel: Int
)

class StySection(
    val name: String,
    val startTick: Long,
    val endTick: Long,
    val notes: List<StyNote>,
    val casm: List<CasmEntry>,
    /** Program change at the start of the section, per source channel (-1 = none). */
    val program: IntArray,
    /** Volume (CC7) at the start of the section, per source channel (-1 = none). */
    val volume: IntArray
)

class StyData(
    val format: String,            // "SFF1" / "SFF2" / "unknown"
    val title: String,
    val tempoBpm: Int,
    val tsNum: Int,
    val tsDen: Int,
    val ppq: Int,
    val sections: List<StySection>,
    val setupProgram: IntArray,
    val setupVolume: IntArray,
    /** One entry per One Touch Setting found: programs for channels 0..3 (-1 = none). */
    val ots: List<IntArray>,
    val hasCasm: Boolean,
    val warnings: List<String>
) {
    val ticksPerBar: Long get() = ppq.toLong() * 4L * tsNum / tsDen
}

/**
 * Yamaha style (.STY, SFF1 / SFF2) reader.
 *
 * File = a standard MIDI file whose first track holds all sections, separated by marker meta events
 * ("Main A", "Fill In AA", "Intro B", "Ending C" ...), followed by a CASM chunk (channel mapping and
 * note transposition rules) and an optional OTSc chunk (One Touch Settings).
 */
object StyParser {
    private val SECTION_NAMES = setOf(
        "Intro A", "Intro B", "Intro C", "Intro D",
        "Main A", "Main B", "Main C", "Main D",
        "Fill In AA", "Fill In BB", "Fill In CC", "Fill In DD", "Fill In BA",
        "Ending A", "Ending B", "Ending C", "Ending D"
    )

    fun isStyle(bytes: ByteArray): Boolean {
        if (bytes.size < 14 || bytes[0] != 'M'.code.toByte() || bytes[1] != 'T'.code.toByte()) return false
        return try {
            val d = MidiReader.read(bytes)
            d.tracks.isNotEmpty() && d.tracks[0].metas.any { it.type == 6 && (it.text() == "SFF1" || it.text() == "SFF2") }
        } catch (e: FormatException) { false }
    }

    fun parse(bytes: ByteArray): StyData {
        val midi = MidiReader.read(bytes)
        val warnings = ArrayList<String>()
        val trk = midi.tracks[0]
        val markers = trk.metas.filter { it.type == 6 }
        val fmt = markers.firstOrNull { it.text() == "SFF1" || it.text() == "SFF2" }?.text()
            ?: throw FormatException("Not a Yamaha style: no SFF1/SFF2 marker found (this may be a MIDI song or another format)")

        var tempoUs = 500000
        trk.metas.firstOrNull { it.type == 0x51 && it.data.size == 3 }?.let {
            tempoUs = ((it.data[0].toInt() and 0xFF) shl 16) or ((it.data[1].toInt() and 0xFF) shl 8) or (it.data[2].toInt() and 0xFF)
        }
        if (tempoUs <= 0) tempoUs = 500000
        var num = 4
        var den = 4
        trk.metas.firstOrNull { it.type == 0x58 && it.data.size >= 2 }?.let {
            num = it.data[0].toInt() and 0xFF
            den = 1 shl (it.data[1].toInt() and 0xFF).coerceIn(0, 6)
        }
        if (num <= 0 || den <= 0) { num = 4; den = 4 }
        val title = trk.metas.firstOrNull { it.type == 3 }?.text() ?: ""

        // ---- section boundaries (marker positions) ----
        class Mark(val name: String, val tick: Long)
        val secMarks = markers.map { Mark(it.text(), it.tick) }.filter { it.name in SECTION_NAMES }.sortedBy { it.tick }
        if (secMarks.isEmpty()) throw FormatException("Style contains no recognisable sections (Main A, Intro ...)")
        var lastTick = 0L
        for (e in trk.events) if (e.tick > lastTick) lastTick = e.tick
        val ppq = midi.division
        val tpb = ppq.toLong() * 4L * num / den
        val firstSecTick = secMarks[0].tick

        // ---- pair note-on / note-off over the whole track ----
        class Open(val tick: Long, val vel: Int)
        val open = HashMap<Int, ArrayDeque<Open>>()
        class Raw(val tick: Long, val len: Long, val ch: Int, val note: Int, val vel: Int)
        val raws = ArrayList<Raw>()
        val setupProg = IntArray(16) { -1 }
        val setupVol = IntArray(16) { -1 }
        val secProg = HashMap<Int, IntArray>()    // section index -> program per channel
        val secVol = HashMap<Int, IntArray>()
        fun secIndexAt(t: Long): Int {
            var idx = -1
            for (k in secMarks.indices) if (secMarks[k].tick <= t) idx = k else break
            return idx
        }
        for (e in trk.events) {
            val ch = e.channel
            when {
                MidiReader.isNoteOn(e) -> {
                    val key = ch * 128 + e.d1
                    open.getOrPut(key) { ArrayDeque() }.addLast(Open(e.tick, e.d2))
                }
                MidiReader.isNoteOff(e) -> {
                    val q = open[ch * 128 + e.d1]
                    if (q != null && q.isNotEmpty()) {
                        val o = q.removeFirst()
                        raws.add(Raw(o.tick, maxOf(1L, e.tick - o.tick), ch, e.d1, o.vel))
                    }
                }
                e.type == 0xC0 -> {
                    val si = secIndexAt(e.tick)
                    if (si < 0 || e.tick < firstSecTick) { if (setupProg[ch] < 0) setupProg[ch] = e.d1 }
                    else {
                        val arr = secProg.getOrPut(si) { IntArray(16) { -1 } }
                        if (arr[ch] < 0 && e.tick - secMarks[si].tick <= ppq) arr[ch] = e.d1
                    }
                }
                e.type == 0xB0 && e.d1 == 7 -> {
                    val si = secIndexAt(e.tick)
                    if (si < 0 || e.tick < firstSecTick) { if (setupVol[ch] < 0) setupVol[ch] = e.d2 }
                    else {
                        val arr = secVol.getOrPut(si) { IntArray(16) { -1 } }
                        if (arr[ch] < 0 && e.tick - secMarks[si].tick <= ppq) arr[ch] = e.d2
                    }
                }
            }
        }
        // notes that never got a note-off: give them a short length instead of dropping them
        for ((key, q) in open) for (o in q) raws.add(Raw(o.tick, ppq.toLong() / 4, key / 128, key % 128, o.vel))
        if (open.values.any { it.isNotEmpty() }) warnings.add("Some notes had no note-off and were shortened")

        // ---- CASM ----
        val casmBySection = HashMap<String, ArrayList<CasmEntry>>()
        var hasCasm = false
        for ((tag, body) in midi.extraChunks) {
            if (tag == "CASM") {
                hasCasm = true
                try { parseCasm(body, casmBySection) } catch (e: Exception) { warnings.add("CASM data damaged; default channel mapping used") ; casmBySection.clear(); hasCasm = false }
            }
        }

        // ---- build sections ----
        val sections = ArrayList<StySection>()
        for ((k, m) in secMarks.withIndex()) {
            val end = if (k + 1 < secMarks.size) secMarks[k + 1].tick else maxOf(lastTick, m.tick + tpb)
            if (end <= m.tick) continue
            val entries: List<CasmEntry> = if (hasCasm) (casmBySection[m.name] ?: emptyList()) else defaultCasm()
            val allowed = HashSet<Int>()
            for (c in entries) allowed.add(c.src)
            val notes = ArrayList<StyNote>()
            for (r in raws) {
                if (r.tick < m.tick || r.tick >= end || r.ch !in allowed) continue
                val l = minOf(r.len, end - r.tick)
                notes.add(StyNote(r.tick - m.tick, l, r.ch, r.note, r.vel))
            }
            notes.sortBy { it.tick }
            sections.add(
                StySection(
                    m.name, m.tick, end, notes, entries,
                    secProg[k] ?: IntArray(16) { -1 }, secVol[k] ?: IntArray(16) { -1 }
                )
            )
        }
        if (!hasCasm) warnings.add("No CASM data: channels 9-16 are played without chord conversion rules")

        // ---- OTS ----
        val ots = ArrayList<IntArray>()
        for ((tag, body) in midi.extraChunks) {
            if (tag == "OTSc") {
                try {
                    // the OTSc body is a sequence of MTrk chunks
                    var p = 0
                    while (p + 8 <= body.size) {
                        val len = ((body[p + 4].toInt() and 0xFF) shl 24) or ((body[p + 5].toInt() and 0xFF) shl 16) or
                            ((body[p + 6].toInt() and 0xFF) shl 8) or (body[p + 7].toInt() and 0xFF)
                        val isTrk = body[p] == 'M'.code.toByte() && body[p + 1] == 'T'.code.toByte() &&
                            body[p + 2] == 'r'.code.toByte() && body[p + 3] == 'k'.code.toByte()
                        val end = if (len < 0 || p + 8 + len > body.size) body.size else p + 8 + len
                        if (isTrk) {
                            val fake = ByteArray(14 + (end - p)) // header + this track
                            fake[0] = 'M'.code.toByte(); fake[1] = 'T'.code.toByte(); fake[2] = 'h'.code.toByte(); fake[3] = 'd'.code.toByte()
                            fake[7] = 6; fake[9] = 0; fake[11] = 1; fake[12] = 0x01; fake[13] = 0xE0.toByte()
                            System.arraycopy(body, p, fake, 14, end - p)
                            val t = MidiReader.read(fake).tracks[0]
                            val prog = IntArray(4) { -1 }
                            for (e in t.events) if (e.type == 0xC0 && e.channel < 4 && prog[e.channel] < 0) prog[e.channel] = e.d1
                            ots.add(prog)
                        }
                        p = end
                    }
                } catch (e: Exception) { warnings.add("OTS data could not be read") }
            }
        }

        return StyData(
            fmt, title, (60000000L / tempoUs).toInt().coerceIn(30, 300), num, den, ppq,
            sections, setupProg, setupVol, ots, hasCasm, warnings
        )
    }

    /** Used when a style has no CASM: channels 8..15 map to themselves, no chord conversion for rhythm. */
    private fun defaultCasm(): List<CasmEntry> = (8..15).map { ch ->
        CasmEntry(ch, "Ch " + (ch + 1), ch, 0, if (ch <= 9) 0 else 2, false, 5, 0, 127, 1)
    }

    private fun u32(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or
            ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

    private fun parseCasm(body: ByteArray, out: HashMap<String, ArrayList<CasmEntry>>) {
        var i = 0
        while (i + 8 <= body.size) {
            val tag = String(body, i, 4, Charsets.ISO_8859_1)
            val len = u32(body, i + 4)
            if (len < 0 || i + 8 + len > body.size) break
            if (tag == "CSEG") {
                var names: List<String> = emptyList()
                val entries = ArrayList<CasmEntry>()
                var j = i + 8
                val end = i + 8 + len
                while (j + 8 <= end) {
                    val t2 = String(body, j, 4, Charsets.ISO_8859_1)
                    val l2 = u32(body, j + 4)
                    if (l2 < 0 || j + 8 + l2 > end) break
                    val b2 = body.copyOfRange(j + 8, j + 8 + l2)
                    when (t2) {
                        "Sdec" -> names = String(b2, Charsets.ISO_8859_1).split(',').map { it.trim { c -> c <= ' ' } }
                        "Ctb2" -> parseCtb2(b2)?.let { entries.add(it) }
                        "Ctab" -> parseCtab(b2)?.let { entries.add(it) }
                    }
                    j += 8 + l2
                }
                for (n in names) out.getOrPut(n) { ArrayList() }.addAll(entries)
            }
            i += 8 + len
        }
    }

    /**
     * Ctb2 layout (measured from real files): src(1) name(8) dest(1) editable(1) noteMute(2) chordMute(5)
     * srcRoot(1) srcType(1) then 3 bytes, then 6-byte sets: NTT(bit7 = bass on) highKey noteLow noteHigh RTR x.
     */
    private fun parseCtb2(b: ByteArray): CasmEntry? {
        if (b.size < 29) return null
        val dest = b[9].toInt() and 0xFF
        if (dest !in 8..15) return null
        val a = b[23].toInt() and 0xFF
        return CasmEntry(
            src = b[0].toInt() and 0x0F,
            name = String(b, 1, 8, Charsets.ISO_8859_1).trim { it <= ' ' },
            dest = dest,
            srcRoot = (b[18].toInt() and 0xFF).coerceIn(0, 11),
            ntt = if (dest <= 9) 0 else (a and 0x7F),
            bassOn = a and 0x80 != 0,
            highKey = (b[24].toInt() and 0xFF).coerceIn(0, 11),
            lo = (b[25].toInt() and 0xFF).coerceIn(0, 127),
            hi = (b[26].toInt() and 0xFF).coerceIn(0, 127),
            rtr = b[27].toInt() and 0xFF
        )
    }

    /** SFF1 Ctab: src(1) name(8) dest(1) editable(1) noteMute(2) chordMute(5) srcRoot srcType NTR NTT highKey low high RTR. */
    private fun parseCtab(b: ByteArray): CasmEntry? {
        if (b.size < 26) return null
        val dest = b[9].toInt() and 0xFF
        if (dest !in 8..15) return null
        val ntt = b[21].toInt() and 0xFF
        return CasmEntry(
            src = b[0].toInt() and 0x0F,
            name = String(b, 1, 8, Charsets.ISO_8859_1).trim { it <= ' ' },
            dest = dest,
            srcRoot = (b[18].toInt() and 0xFF).coerceIn(0, 11),
            ntt = if (dest <= 9) 0 else (ntt and 0x7F),
            bassOn = ntt and 0x80 != 0,
            highKey = (b[22].toInt() and 0xFF).coerceIn(0, 11),
            lo = (b[23].toInt() and 0xFF).coerceIn(0, 127),
            hi = (b[24].toInt() and 0xFF).coerceIn(0, 127),
            rtr = b[25].toInt() and 0xFF
        )
    }
}
