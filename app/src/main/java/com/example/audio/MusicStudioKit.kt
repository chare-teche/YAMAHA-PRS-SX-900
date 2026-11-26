package com.example.audio

/**
 * Reader for "Music Studio Instrument" drum kit files (.Instrument). Pure Kotlin, unit tested.
 * The layout was worked out from real kit files (every record chain ends exactly at the end of the file):
 *   [note:1][name:32, zero padded][rate:u16 @+0x20][bits:1 @+0x22][channels:1 @+0x23][frames:u32 @+0x2c][PCM @+0x60]
 * little endian; the next record's note byte follows the PCM data. Files that do not match give null (nothing is guessed).
 */
object MusicStudioKit {
    private const val MAX_SECONDS = 8
    private const val MAX_TOTAL_FRAMES = 12_000_000

    private class Rec(val note: Int, val name: String, val rate: Int, val bits: Int, val ch: Int, val frames: Int, val pcm: Int, val end: Int)

    private fun u16(d: ByteArray, o: Int) = (d[o].toInt() and 255) or ((d[o + 1].toInt() and 255) shl 8)
    private fun u32(d: ByteArray, o: Int): Long =
        (u16(d, o).toLong()) or (u16(d, o + 2).toLong() shl 16)

    private fun tryRecord(d: ByteArray, p: Int): Rec? {
        if (p < 1 || p + 0x60 > d.size) return null
        val note = d[p - 1].toInt() and 255
        if (note > 127) return null
        var z = -1
        for (i in 0 until 32) if (d[p + i].toInt() == 0) { z = i; break }
        if (z <= 0) return null
        for (i in 0 until z) { val c = d[p + i].toInt() and 255; if (c < 32 || c >= 127) return null }
        for (i in z until 32) if (d[p + i].toInt() != 0) return null
        val rate = u16(d, p + 0x20)
        if (rate !in 4000..192000) return null
        val bits = d[p + 0x22].toInt() and 255
        val ch = d[p + 0x23].toInt() and 255
        if ((bits != 8 && bits != 16) || (ch != 1 && ch != 2)) return null
        val frames = u32(d, p + 0x2c)
        if (frames <= 0L || frames > 50_000_000L) return null
        val end = p.toLong() + 0x60 + frames * ch * (bits / 8)
        if (end > d.size) return null
        return Rec(note, String(d, p, z, Charsets.US_ASCII).trim(), rate, bits, ch, frames.toInt(), p + 0x60, end.toInt())
    }

    private fun chain(d: ByteArray): List<Rec> {
        var best: List<Rec> = emptyList()
        val limit = minOf(d.size, 4000)
        for (start in 1 until limit) {
            val first = tryRecord(d, start) ?: continue
            val c = ArrayList<Rec>(); c.add(first)
            while (true) { c.add(tryRecord(d, c[c.size - 1].end + 1) ?: break) }
            if (c.size > best.size) best = c
            if (c.size >= 2) break
        }
        return best
    }

    /** Number of sample records in the file (0 = not a kit file). */
    fun recordCount(bytes: ByteArray): Int = chain(bytes).size

    /** Reads the kit into a drum set keyed by MIDI note, or null when the file is not a readable kit. */
    fun parse(bytes: ByteArray): DrumSampleSet? {
        val recs = chain(bytes)
        if (recs.isEmpty()) return null
        val map = HashMap<Int, DrumSample>()
        var total = 0
        for (r in recs) {
            if (map.containsKey(r.note) || r.note < 24 || r.note > 100) continue
            val n = minOf(r.frames, r.rate * MAX_SECONDS)
            if (total + n > MAX_TOTAL_FRAMES) break
            val a = FloatArray(n)
            val step = r.ch * (r.bits / 8)
            var peak = 0f
            for (i in 0 until n) {
                var sum = 0f
                for (c in 0 until r.ch) {
                    val o = r.pcm + i * step + c * (r.bits / 8)
                    sum += if (r.bits == 16) ((bytes[o].toInt() and 255) or (bytes[o + 1].toInt() shl 8)).toShort() / 32768f
                    else bytes[o].toInt() / 128f
                }
                val v = sum / r.ch
                a[i] = v
                if (Math.abs(v) > peak) peak = Math.abs(v)
            }
            if (peak < 0.001f) continue
            // clean edges: a short fade in, and a fade out (longer when the recording is cut off while still loud)
            val tailLen = maxOf(1, r.rate / 200)
            var tail = 0f
            for (i in maxOf(0, n - tailLen) until n) tail = maxOf(tail, Math.abs(a[i]))
            val fo = minOf(n, maxOf(r.rate / 250, if (tail > 0.01f * peak.coerceAtLeast(0.1f)) minOf(r.rate / 25, n / 4) else 0))
            for (i in 0 until fo) a[n - fo + i] *= 0.5f * (1f + Math.cos(Math.PI * i / fo).toFloat())
            val fi = minOf(n, r.rate / 3000 + 1)
            for (i in 0 until fi) a[i] *= i / fi.toFloat()
            // only turn down what is too loud; quiet recordings keep their own level
            val g = if (peak > 0.95f) 0.95f / peak else 1f
            val pcm = ShortArray(n) { Math.round(a[it] * g * 32767f).toShort() }
            map[r.note] = DrumSample(r.name, r.rate, pcm)
            total += n
        }
        return if (map.isEmpty()) null else DrumSampleSet.fromMap(map)
    }
}
