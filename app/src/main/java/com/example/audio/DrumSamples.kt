package com.example.audio

import java.io.DataInputStream
import java.io.InputStream

/** One recorded drum hit: mono 16-bit PCM and the rate it must be played at (pitch changes are baked into [rate]). */
class DrumSample(val name: String, val rate: Int, val pcm: ShortArray) {
    val frames: Int get() = pcm.size
    val seconds: Float get() = pcm.size.toFloat() / rate
}

/**
 * Real drum samples, read once from the resource file ethio_drums.bin (app/src/main/resources).
 * Pure Kotlin (no Android classes) so it is unit tested. Never throws: a missing or broken file gives an empty set
 * and the engine keeps using its synthesized drums.
 *
 * File format (big-endian): "EDR1", int soundCount, then per sound: int rate, int frames, frames x short;
 * then int noteCount, then per note: byte note, short soundIndex, short nameLength, name bytes (UTF-8).
 * Several notes may share one sound (same sample, no duplicate memory).
 */
class DrumSampleSet private constructor(private val byNote: Array<DrumSample?>) {
    fun get(note: Int): DrumSample? = if (note in 0..127) byNote[note] else null
    val noteCount: Int get() = byNote.count { it != null }
    fun notes(): List<Int> = (0..127).filter { byNote[it] != null }
    val totalBytes: Int get() = byNote.filterNotNull().distinct().sumOf { it.pcm.size * 2 }

    companion object {
        const val RESOURCE = "/ethio_drums.bin"
        val EMPTY = DrumSampleSet(arrayOfNulls(128))

        /** The packaged set, loaded once on first use. */
        val default: DrumSampleSet by lazy {
            try {
                DrumSampleSet::class.java.getResourceAsStream(RESOURCE)?.use { parse(it) } ?: EMPTY
            } catch (e: Throwable) { EMPTY }
        }

        fun parse(input: InputStream): DrumSampleSet {
            return try {
                val d = DataInputStream(input.buffered())
                val magic = ByteArray(4); d.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "EDR1") return EMPTY
                val n = d.readInt()
                if (n < 0 || n > 4096) return EMPTY
                val rates = IntArray(n)
                val pcms = arrayOfNulls<ShortArray>(n)
                for (i in 0 until n) {
                    val rate = d.readInt(); val frames = d.readInt()
                    if (rate !in 4000..192000 || frames < 1 || frames > 44100 * 10) return EMPTY
                    val b = ByteArray(frames * 2); d.readFully(b)
                    val s = ShortArray(frames)
                    for (k in 0 until frames) s[k] = (((b[2 * k].toInt() shl 8) or (b[2 * k + 1].toInt() and 0xFF))).toShort()
                    rates[i] = rate; pcms[i] = s
                }
                val m = d.readInt()
                val out = arrayOfNulls<DrumSample>(128)
                val made = arrayOfNulls<DrumSample>(n)
                for (i in 0 until m) {
                    val note = d.readUnsignedByte(); val si = d.readShort().toInt(); val len = d.readUnsignedShort()
                    val nb = ByteArray(len); d.readFully(nb)
                    if (note > 127 || si < 0 || si >= n) continue
                    val smp = made[si] ?: DrumSample(String(nb, Charsets.UTF_8), rates[si], pcms[si]!!).also { made[si] = it }
                    out[note] = smp
                }
                DrumSampleSet(out)
            } catch (e: Exception) { EMPTY }
        }
    }
}
