package com.example.formats

/** Thrown for files that are not valid / are damaged. The message is shown to the user. */
class FormatException(message: String) : Exception(message)

/** One MIDI channel-voice event. status = full status byte (e.g. 0x93). */
class MidiEvent(@JvmField val tick: Long, @JvmField val status: Int, @JvmField val d1: Int, @JvmField val d2: Int) {
    val type: Int get() = status and 0xF0
    val channel: Int get() = status and 0x0F
}

/** A meta event (marker, tempo, time signature, track name ...). */
class MidiMeta(@JvmField val tick: Long, @JvmField val type: Int, @JvmField val data: ByteArray) {
    fun text(): String = String(data, Charsets.ISO_8859_1).trim { it <= ' ' }
}

class MidiTrack(val events: List<MidiEvent>, val metas: List<MidiMeta>)

class MidiData(
    val format: Int,
    val division: Int,
    val tracks: List<MidiTrack>,
    /** Every chunk after the MIDI tracks (e.g. CASM, OTSc in a Yamaha style). Tag -> raw body. */
    val extraChunks: List<Pair<String, ByteArray>>
)

/**
 * Standard MIDI file reader. Safe against damaged files: every read is bounds-checked and
 * limits stop absurd files. Handles running status, velocity-0 note-on is left as written
 * (use [isNoteOff]) so callers can decide.
 */
object MidiReader {
    const val MAX_BYTES = 64 * 1024 * 1024
    private const val MAX_EVENTS = 4_000_000

    fun isNoteOff(e: MidiEvent): Boolean = e.type == 0x80 || (e.type == 0x90 && e.d2 == 0)
    fun isNoteOn(e: MidiEvent): Boolean = e.type == 0x90 && e.d2 > 0

    private fun u32(d: ByteArray, i: Int): Long =
        ((d[i].toLong() and 0xFF) shl 24) or ((d[i + 1].toLong() and 0xFF) shl 16) or
            ((d[i + 2].toLong() and 0xFF) shl 8) or (d[i + 3].toLong() and 0xFF)

    private fun u16(d: ByteArray, i: Int): Int = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)

    fun read(d: ByteArray): MidiData {
        if (d.size < 14) throw FormatException("File too small to be a MIDI file")
        if (d.size > MAX_BYTES) throw FormatException("File too large (over 64 MB)")
        if (!(d[0] == 'M'.code.toByte() && d[1] == 'T'.code.toByte() && d[2] == 'h'.code.toByte() && d[3] == 'd'.code.toByte()))
            throw FormatException("Not a MIDI file (missing MThd header)")
        val hdrLen = u32(d, 4).toInt()
        if (hdrLen < 6 || 8 + hdrLen > d.size) throw FormatException("Damaged MIDI header")
        val format = u16(d, 8)
        val declared = u16(d, 10)
        val division = u16(d, 12)
        if (division and 0x8000 != 0) throw FormatException("SMPTE time-code MIDI files are not supported")
        if (division <= 0) throw FormatException("Invalid MIDI time division")
        var i = 8 + hdrLen
        val tracks = ArrayList<MidiTrack>()
        val extra = ArrayList<Pair<String, ByteArray>>()
        var total = 0
        while (i + 8 <= d.size) {
            val tag = String(d, i, 4, Charsets.ISO_8859_1)
            val len = u32(d, i + 4)
            val start = i + 8
            // A damaged length is clamped to what is really in the file.
            val end = if (len < 0 || start + len > d.size) d.size else (start + len).toInt()
            if (tag == "MTrk") {
                val t = readTrack(d, start, end, total)
                total += t.events.size + t.metas.size
                tracks.add(t)
            } else {
                extra.add(Pair(tag, d.copyOfRange(start, end)))
            }
            i = end
        }
        if (tracks.isEmpty()) throw FormatException("MIDI file has no tracks")
        if (declared != 0 && tracks.size < declared && tracks.size == 0) throw FormatException("MIDI tracks are missing")
        return MidiData(format, division, tracks, extra)
    }

    private fun readTrack(d: ByteArray, start: Int, end: Int, already: Int): MidiTrack {
        val ev = ArrayList<MidiEvent>()
        val metas = ArrayList<MidiMeta>()
        var p = start
        var tick = 0L
        var running = 0
        var count = already
        try {
            while (p < end) {
                // delta time (variable length, max 4 bytes)
                var delta = 0L
                var n = 0
                while (true) {
                    if (p >= end) return MidiTrack(ev, metas)
                    val b = d[p++].toInt() and 0xFF
                    delta = (delta shl 7) or (b and 0x7F).toLong()
                    n++
                    if (b and 0x80 == 0) break
                    if (n >= 4) throw FormatException("Damaged delta time")
                }
                tick += delta
                if (p >= end) break
                var status = d[p].toInt() and 0xFF
                if (status == 0xFF) {
                    if (p + 1 >= end) break
                    val type = d[p + 1].toInt() and 0xFF
                    p += 2
                    var l = 0
                    var k = 0
                    while (true) {
                        if (p >= end) return MidiTrack(ev, metas)
                        val b = d[p++].toInt() and 0xFF
                        l = (l shl 7) or (b and 0x7F)
                        k++
                        if (b and 0x80 == 0) break
                        if (k >= 4) throw FormatException("Damaged meta length")
                    }
                    if (l < 0 || p + l > end) { p = end; break }
                    metas.add(MidiMeta(tick, type, d.copyOfRange(p, p + l)))
                    p += l
                    if (type == 0x2F) break
                } else if (status == 0xF0 || status == 0xF7) {
                    p++
                    var l = 0
                    var k = 0
                    while (true) {
                        if (p >= end) return MidiTrack(ev, metas)
                        val b = d[p++].toInt() and 0xFF
                        l = (l shl 7) or (b and 0x7F)
                        k++
                        if (b and 0x80 == 0) break
                        if (k >= 4) throw FormatException("Damaged sysex length")
                    }
                    if (l < 0 || p + l > end) { p = end; break }
                    p += l   // sysex is skipped (not needed for playback in this app)
                } else {
                    if (status and 0x80 != 0) { running = status; p++ } else status = running
                    if (status == 0) { p++; continue }   // data without any status: skip a byte
                    val hi = status and 0xF0
                    val need = if (hi == 0xC0 || hi == 0xD0) 1 else 2
                    if (p + need > end) break
                    val a = d[p].toInt() and 0x7F
                    val b = if (need == 2) d[p + 1].toInt() and 0x7F else 0
                    p += need
                    ev.add(MidiEvent(tick, status, a, b))
                }
                count++
                if (count > MAX_EVENTS) throw FormatException("MIDI file has too many events")
            }
        } catch (e: IndexOutOfBoundsException) {
            // keep what was read before the damage
        }
        return MidiTrack(ev, metas)
    }
}
