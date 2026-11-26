package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Master tone presets (equalizer). Pure Kotlin so it is unit tested.
 * 4 bands, RBJ audio-EQ-cookbook biquads: low shelf, low-mid peak, presence peak, high shelf.
 * Measured on a screen recording of the app: 65% of the energy was below 500 Hz and only 3.8% above 2 kHz (dull, muddy).
 * CLEAR lowers the low end and mud and lifts presence and air. WARM does the opposite. FLAT bypasses the filters.
 */
object Tone {
    const val FLAT = 0
    const val CLEAR = 1
    const val WARM = 2
    const val COUNT = 3
    const val BANDS = 4
    val NAMES = arrayOf("FLAT", "CLEAR", "WARM")

    private const val LOW_SHELF = 0
    private const val PEAK = 1
    private const val HIGH_SHELF = 2

    /** type, frequency Hz, gain dB, Q  for every band of a preset. */
    private val presets: Array<FloatArray> = arrayOf(
        floatArrayOf(),
        floatArrayOf(
            LOW_SHELF.toFloat(), 180f, -2.5f, 0.7f,
            PEAK.toFloat(), 350f, -2.0f, 1.0f,
            PEAK.toFloat(), 3200f, 2.5f, 0.8f,
            HIGH_SHELF.toFloat(), 8000f, 2.5f, 0.7f
        ),
        floatArrayOf(
            LOW_SHELF.toFloat(), 150f, 2.0f, 0.7f,
            PEAK.toFloat(), 350f, 0f, 1.0f,
            PEAK.toFloat(), 3000f, -1.0f, 0.8f,
            HIGH_SHELF.toFloat(), 7000f, -3.0f, 0.7f
        )
    )

    /** Fills out[band*5 ..] with b0 b1 b2 a1 a2 (normalised by a0). FLAT gives pass-through. */
    fun coefficients(preset: Int, sr: Float, out: FloatArray) {
        val p = presets[preset.coerceIn(0, COUNT - 1)]
        for (b in 0 until BANDS) {
            val o = b * 5
            if (p.isEmpty()) { out[o] = 1f; out[o + 1] = 0f; out[o + 2] = 0f; out[o + 3] = 0f; out[o + 4] = 0f; continue }
            biquad(p[b * 4].toInt(), p[b * 4 + 1], p[b * 4 + 2], p[b * 4 + 3], sr, out, o)
        }
    }

    private fun biquad(type: Int, f0: Float, gainDb: Float, q: Float, sr: Float, out: FloatArray, o: Int) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / sr
        val cw = cos(w0); val sw = sin(w0)
        val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
        when (type) {
            PEAK -> {
                val al = sw / (2.0 * q)
                b0 = 1 + al * a; b1 = -2 * cw; b2 = 1 - al * a
                a0 = 1 + al / a; a1 = -2 * cw; a2 = 1 - al / a
            }
            LOW_SHELF -> {
                val al = sw / 2.0 * sqrt(2.0)       // shelf slope S = 1
                val t = 2 * sqrt(a) * al
                b0 = a * ((a + 1) - (a - 1) * cw + t)
                b1 = 2 * a * ((a - 1) - (a + 1) * cw)
                b2 = a * ((a + 1) - (a - 1) * cw - t)
                a0 = (a + 1) + (a - 1) * cw + t
                a1 = -2 * ((a - 1) + (a + 1) * cw)
                a2 = (a + 1) + (a - 1) * cw - t
            }
            else -> {
                val al = sw / 2.0 * sqrt(2.0)
                val t = 2 * sqrt(a) * al
                b0 = a * ((a + 1) + (a - 1) * cw + t)
                b1 = -2 * a * ((a - 1) + (a + 1) * cw)
                b2 = a * ((a + 1) + (a - 1) * cw - t)
                a0 = (a + 1) - (a - 1) * cw + t
                a1 = 2 * ((a - 1) - (a + 1) * cw)
                a2 = (a + 1) - (a - 1) * cw - t
            }
        }
        out[o] = (b0 / a0).toFloat(); out[o + 1] = (b1 / a0).toFloat(); out[o + 2] = (b2 / a0).toFloat()
        out[o + 3] = (a1 / a0).toFloat(); out[o + 4] = (a2 / a0).toFloat()
    }

    /** Gain in dB of a preset at a frequency (used by tests and could drive a graph). */
    fun responseDb(preset: Int, sr: Float, freq: Float): Double {
        val c = FloatArray(BANDS * 5)
        coefficients(preset, sr, c)
        val w = 2.0 * PI * freq / sr
        val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        var total = 0.0
        for (b in 0 until BANDS) {
            val o = b * 5
            val nr = c[o] + c[o + 1] * cw + c[o + 2] * c2
            val ni = -(c[o + 1] * sw + c[o + 2] * s2)
            val dr = 1 + c[o + 3] * cw + c[o + 4] * c2
            val di = -(c[o + 3] * sw + c[o + 4] * s2)
            total += 10 * Math.log10((nr * nr + ni * ni) / (dr * dr + di * di))
        }
        return total
    }
}
