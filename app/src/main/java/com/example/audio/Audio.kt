package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A message from the screen to the audio thread. The screen never touches audio data directly. */
class Cmd(
    @JvmField val t: Int,
    @JvmField val a: Int = 0,
    @JvmField val b: Int = 0,
    @JvmField val c: Int = 0,
    @JvmField val f: Float = 0f
)

object Op {
    const val NOTE_ON = 1
    const val NOTE_OFF = 2
    const val PROGRAM = 3
    const val CH_VOL = 4
    const val CH_PAN = 5
    const val SUSTAIN = 6
    const val BEND = 7
    const val MOD = 8
    const val ALL_OFF = 9
    const val TEMPO = 10
    const val START = 11
    const val STOP = 12
    const val SECTION = 13
    const val CHORD = 14
    const val STYLE = 15
    const val TRACK_VOL = 16
    const val TRACK_MUTE = 17
    const val FADE = 18
    const val REVERB = 19
    const val MASTER = 20
    const val AUTOFILL = 21
    const val TRANSPOSE = 22
    const val STYLE_VOL = 23
    const val ACMP = 24
    /** Hard stop of every voice (used when the app goes to the background). Voices fade in a few ms: no pop. */
    const val PANIC = 25
    /** Master tone preset: 0 FLAT, 1 CLEAR, 2 WARM (a = preset). */
    const val TONE = 26
}

class TimbreDef(
    val name: String, val a: FloatArray, val b: FloatArray,
    val attack: Float, val decayTc: Float, val sustain: Float, val release: Float,
    val m0: Float, val m1: Float, val mTc: Float,
    val vibRate: Float = 0f, val vibDepth: Float = 0f,
    val detune: Float = 0f, val breath: Float = 0f, val gain: Float = 1f
)

private fun amps(vararg v: Float): FloatArray {
    val r = FloatArray(33)
    for (i in v.indices) if (i + 1 < 33) r[i + 1] = v[i]
    return r
}

private fun series(k: Float, maxN: Int = 32, oddOnly: Boolean = false): FloatArray {
    val r = FloatArray(33)
    for (n in 1..maxN) if (!oddOnly || n % 2 == 1) r[n] = 1f / n.toFloat().pow(k)
    return r
}

/**
 * Synthesized instrument definitions. Each voice crossfades between a bright and a dull
 * single-cycle wavetable (A -> B) which gives a natural "bright attack, mellow sustain" tone.
 * These are NOT recorded samples.
 */
val TIMBRES: Array<TimbreDef> = arrayOf(
    TimbreDef("Grand Piano", series(0.9f), series(2.6f), 0.003f, 1.6f, 0f, 0.25f, 0f, 1f, 0.7f, gain = 1.1f),
    TimbreDef("E.Piano", amps(1f, 0.45f, 0.08f, 0.22f, 0f, 0.12f), amps(1f, 0.12f), 0.004f, 1.3f, 0f, 0.3f, 0f, 1f, 0.5f),
    TimbreDef("Organ", amps(1f, 0.8f, 0.55f, 0.5f, 0f, 0.3f, 0f, 0.25f), amps(1f, 0.8f, 0.55f, 0.5f, 0f, 0.3f, 0f, 0.25f),
        0.008f, 0.05f, 1f, 0.06f, 0f, 0f, 1f, vibRate = 6f, vibDepth = 0.04f, gain = 0.8f),
    TimbreDef("Strings", series(1.1f), series(1.9f), 0.28f, 0.3f, 0.9f, 0.6f, 1f, 0f, 1f,
        vibRate = 5.2f, vibDepth = 0.05f, detune = 9f, gain = 0.8f),
    TimbreDef("Brass", series(0.8f, 14), series(1.6f, 14), 0.07f, 0.25f, 0.85f, 0.14f, 1f, 0f, 0.15f,
        vibRate = 5f, vibDepth = 0.03f, gain = 0.9f),
    TimbreDef("Synth Lead", series(1f, 32, true), series(1.7f, 32, true), 0.01f, 0.15f, 0.8f, 0.12f, 0f, 1f, 1.5f,
        vibRate = 5.5f, vibDepth = 0.08f, detune = 7f, gain = 0.7f),
    TimbreDef("Warm Pad", series(1.5f), series(2.6f), 0.6f, 0.5f, 1f, 1.2f, 1f, 0f, 2f,
        vibRate = 0.4f, vibDepth = 0.05f, detune = 12f, gain = 0.8f),
    TimbreDef("Nylon Guitar", series(0.8f, 20), series(2.2f, 20), 0.002f, 1.1f, 0f, 0.15f, 0f, 1f, 0.25f, gain = 1.1f),
    TimbreDef("Finger Bass", amps(1f, 0.55f, 0.35f, 0.18f, 0.1f), amps(1f, 0.12f), 0.005f, 0.5f, 0.35f, 0.12f, 0f, 1f, 0.2f, gain = 1.4f),
    TimbreDef("Flute", amps(1f, 0.18f, 0.07f), amps(1f, 0.1f), 0.06f, 0.1f, 0.95f, 0.12f, 0f, 0f, 1f,
        vibRate = 5f, vibDepth = 0.07f, breath = 0.05f, gain = 0.9f),
    TimbreDef("Krar", series(0.7f, 24), series(2f, 24), 0.002f, 0.9f, 0f, 0.2f, 0f, 1f, 0.22f, detune = 4f),
    TimbreDef("Masinko", amps(1f, 0.7f, 0.9f, 0.5f, 0.6f, 0.3f, 0.25f, 0.1f), amps(1f, 0.5f, 0.6f, 0.3f, 0.35f, 0.15f),
        0.09f, 0.2f, 0.9f, 0.15f, 0f, 1f, 1.2f, vibRate = 6f, vibDepth = 0.14f, breath = 0.03f, gain = 0.8f),
    TimbreDef("Washint", amps(1f, 0.1f, 0.05f), amps(1f, 0.05f), 0.05f, 0.1f, 0.95f, 0.12f, 0f, 0f, 1f,
        vibRate = 5f, vibDepth = 0.05f, breath = 0.1f, gain = 0.9f)
)

/**
 * The sound engine. Design rules (to avoid dropouts and "missing notes"):
 *  - All voices are allocated once at start. The audio thread never allocates or locks.
 *  - The screen talks to the audio thread only through a lock-free queue.
 *  - The accompaniment sequencer runs INSIDE the audio thread, so timing is sample-accurate.
 *  - When all voices are busy, the quietest releasing (or oldest) voice is stolen smoothly.
 *  - A soft limiter + saturator prevents harsh clipping when many notes play.
 */
class SynthEngine {
    companion object {
        const val SR = 44100
        const val TS = 2048
        const val MEL = 64
        const val DRM = 48
        /** Time constant of the fast fade used for choke, stealing and life end (seconds). */
        const val FAST_FADE = 0.004f
        /** Attack ramp of noise drums (seconds). */
        const val NOISE_ATTACK = 0.0012f
        /** Level of each of the 6 crash partials. */
        const val METAL_GAIN = 0.15f
        /** Inharmonic crash partial frequencies (Hz): not multiples of each other, so it sounds metallic, not like a chord. */
        val METAL_HZ = floatArrayOf(2100f, 3110f, 3890f, 4650f, 5870f, 7320f)
        private val BAND_H = intArrayOf(32, 16, 5)
        /** Hi-hat metal partials (Hz): higher than the crash set, short and bright. */
        val METAL_HAT_HZ = floatArrayOf(3400f, 4700f, 5900f, 7300f, 8800f, 10300f)
        const val HAT_METAL_GAIN = 0.07f
        /** Voices below this level are inaudible (about -67 dBFS) and are freed. Was 0.0006. */
        const val DEAD_ENV = 0.002f
        /** When more than this many melodic voices sound, tails decay twice as fast (stops the voice pool filling up). */
        const val TAIL_PRESSURE = 36
    }

    // ---- values the screen may read (updated by the audio thread) ----
    @Volatile var pubRunning = false
    @Volatile var pubSection = Sec.MAIN_A.ordinal
    @Volatile var pubTarget = Sec.MAIN_A.ordinal
    @Volatile var pubArmed = -1
    @Volatile var pubMeasure = 1
    @Volatile var pubBeat = 1
    @Volatile var pubTempo = 110
    @Volatile var underruns = 0
    @Volatile var peak = 0f
    @Volatile var audioOk = false
    @Volatile var activeVoices = 0
    /** Diagnostics (read by tests / LCD): drum hits stolen from a playing voice, and notes that make no sound. */
    @Volatile var drumSteals = 0
    @Volatile var drumSilentHits = 0

    private val queue = ConcurrentLinkedQueue<Cmd>()
    @Volatile private var threadRunning = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    // ---- tables ----
    private val sinTab = FloatArray(TS) { sin(2.0 * PI * it / TS).toFloat() }
    private val tabs: Array<Array<Array<FloatArray>>> = Array(TIMBRES.size) { t ->
        Array(2) { w ->
            Array(3) { band -> buildTable(if (w == 0) TIMBRES[t].a else TIMBRES[t].b, BAND_H[band]) }
        }
    }

    private fun buildTable(a: FloatArray, maxH: Int): FloatArray {
        val t = FloatArray(TS)
        var mx = 0f
        val top = min(maxH, a.size - 1)
        for (i in 0 until TS) {
            var s = 0f
            for (n in 1..top) {
                val am = a[n]
                if (am != 0f) s += am * sinTab[(n * i) and (TS - 1)]
            }
            t[i] = s
            mx = max(mx, abs(s))
        }
        if (mx > 0f) for (i in 0 until TS) t[i] = t[i] / mx
        return t
    }

    // ---- voices ----
    private class V {
        @JvmField var active = false
        @JvmField var ch = 0
        @JvmField var note = 0
        @JvmField var timbre = 0
        @JvmField var band = 0
        @JvmField var held = false
        @JvmField var sustained = false
        @JvmField var seq = false
        @JvmField var ph1 = 0f
        @JvmField var ph2 = 0f
        @JvmField var inc1 = 0f
        @JvmField var inc2 = 0f
        @JvmField var env = 0f
        @JvmField var stage = 0
        @JvmField var atkInc = 0f
        @JvmField var decCoef = 0f
        @JvmField var sus = 0f
        @JvmField var relCoef = 0f
        @JvmField var mix = 0f
        @JvmField var mixT = 0f
        @JvmField var mixC = 0f
        @JvmField var vibPh = 0f
        @JvmField var amp = 0f
        @JvmField var order = 0L
        // drums
        @JvmField var dKind = 0
        @JvmField var dPitch = 0f
        @JvmField var dF0 = 0f
        @JvmField var dF1 = 0f
        @JvmField var dPitchCoef = 0f
        @JvmField var dToneEnv = 0f
        @JvmField var dToneCoef = 0f
        @JvmField var dNoiseEnv = 0f
        @JvmField var dNoiseCoef = 0f
        @JvmField var dToneAmt = 0f
        @JvmField var dNoiseAmt = 0f
        @JvmField var dLpA = 0f
        @JvmField var dLp = 0f
        @JvmField var dAtk = 1f      // noise attack ramp 0..1
        @JvmField var dAtkInc = 0f
        // metallic partials (crash) and second tone (cowbell); all reset for every drum hit
        @JvmField val dPh = FloatArray(6)
        @JvmField var dMetalSet = 0    // 0 crash partials, 1 hi-hat partials
        @JvmField var dMetalAmt = 0f
        @JvmField var dMetalEnv = 0f
        @JvmField var dMetalCoef = 0f
        @JvmField var dRatio = 0f      // second tone frequency ratio (0 = none)
        @JvmField var dAmt2 = 0f
        @JvmField var dSquare = false  // add 3rd and 5th harmonics (square-ish) to the tones
        @JvmField var dLife = 0      // samples until the forced fade-out
        // recorded drum sample playback (dSmp == null means the synthesized drum above)
        @JvmField var dSmp: ShortArray? = null
        @JvmField var dPos = 0f      // read position in the sample (fractional)
        @JvmField var dStep = 0f     // sample frames per output frame (rate / SR)
        @JvmField var dSEnv = 1f     // fast-fade gain (1 normally, decays after choke / panic)
        @JvmField var dSCoef = 1f
    }

    private val mel = Array(MEL) { V() }
    private val drm = Array(DRM) { V() }
    /** Kit of the current style (see DrumKit): tunes pitch and decay of the synthesized drums. */
    private var drumKit = DrumKit.STANDARD
    /** Recorded drum samples. Set to DrumSampleSet.EMPTY to force the synthesized drums (tests). */
    @Volatile internal var drumSamples: DrumSampleSet = DrumSampleSet.default

    /** Replaces the drum samples (a kit imported by the user). Safe while playing: the audio thread reads the reference once per hit. */
    fun setDrumSamples(set: DrumSampleSet) { drumSamples = if (set.noteCount > 0) set else DrumSampleSet.default }

    // ---- master tone: 4 biquads (low shelf, low-mid cut, presence, high shelf) on the final stereo mix ----
    private val eqC = FloatArray(Tone.BANDS * 5)     // b0 b1 b2 a1 a2 per band (a0 = 1)
    private val eqZL = FloatArray(Tone.BANDS * 2)
    private val eqZR = FloatArray(Tone.BANDS * 2)
    private var eqOn = false
    /** Melodic voices sounding at the start of this block. Above [TAIL_PRESSURE] the release tails are shortened. */
    private var melLoad = 0
    private var orderCounter = 0L

    // ---- channels (16) ----
    private val chProg = IntArray(16)
    private val chVol = FloatArray(16) { 1f }
    private val chPan = FloatArray(16)
    private val chSus = BooleanArray(16)
    private val chBend = FloatArray(16)
    private val chMod = FloatArray(16)
    private val chGl = FloatArray(16)
    private val chGr = FloatArray(16)
    private val bendF = FloatArray(16) { 1f }

    // ---- master section ----
    private var masterVol = 0.8f
    private var styleVol = 0.9f
    private var reverbLevel = 0.18f
    private var limGain = 1f
    private var prevGain = 1f
    private var fade = 1f
    private var fadeDir = 0
    private var stopAfterFade = false
    private var rng = 22222

    // reverb (4 combs + 2 allpass)
    private val combLen = intArrayOf(1116, 1188, 1277, 1356)
    private val combBuf = Array(4) { FloatArray(combLen[it]) }
    private val combIdx = IntArray(4)
    private val combStore = FloatArray(4)
    private val apLen = intArrayOf(556, 441)
    private val apBuf = Array(2) { FloatArray(apLen[it]) }
    private val apIdx = IntArray(2)

    // ---- mix buffers (allocated once) ----
    private var mixL = FloatArray(1024)
    private var mixR = FloatArray(1024)

    // ---- sequencer state (audio thread only) ----
    private var curStyle: Style = Styles.all[0]
    private var seqRunning = false
    private var tempo = 110
    private var stepInBar = 0
    private var barInSec = 0
    private var measure = 1
    private var section = Sec.MAIN_A
    private var targetMain = Sec.MAIN_A
    private var armedIntro: Sec? = null
    private var queued: Sec? = null
    private var autoFill = true
    private var acmpOn = true
    private var stepRemaining = 0.0
    private var chordRoot = 0
    private var chordQ = 0
    private var transpose = 0
    private val trackVol = FloatArray(8) { 0.85f }
    private val trackMute = BooleanArray(8)
    private val offCh = IntArray(256)
    private val offNote = IntArray(256)
    private val offLeft = IntArray(256)
    private var offCount = 0

    // =====================================================================
    //  Public API (safe to call from any thread)
    // =====================================================================
    fun send(c: Cmd) { queue.add(c) }
    fun noteOn(ch: Int, note: Int, vel: Int) = send(Cmd(Op.NOTE_ON, ch, note, vel))
    fun noteOff(ch: Int, note: Int) = send(Cmd(Op.NOTE_OFF, ch, note))

    fun start(blockFrames: Int) {
        if (threadRunning) return
        val block = blockFrames.coerceIn(64, 1024)
        if (mixL.size < block) { mixL = FloatArray(block); mixR = FloatArray(block) }
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = max(minBuf, block * 4 * 3)
        val tr = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
                )
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            audioOk = false
            return
        }
        track = tr
        threadRunning = true
        val t = Thread({ loop(tr, block) }, "ethio-audio")
        thread = t
        t.start()
    }

    fun stop() {
        threadRunning = false
        try { thread?.join(800) } catch (_: InterruptedException) { }
        thread = null
    }

    // =====================================================================
    //  Audio thread
    // =====================================================================
    private fun loop(tr: AudioTrack, block: Int) {
        try { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) } catch (_: Exception) { }
        val out = ShortArray(block * 2)
        try {
            tr.play()
            audioOk = true
            while (threadRunning) {
                renderBlock(block, out)
                val w = tr.write(out, 0, out.size)
                if (w < 0) { audioOk = false; break }
                underruns = tr.underrunCount
            }
        } catch (_: Exception) {
            audioOk = false
        } finally {
            try { tr.stop() } catch (_: Exception) { }
            tr.release()
            track = null
        }
    }

    private fun renderBlock(n: Int, out: ShortArray) {
        drain()
        for (i in 0 until n) { mixL[i] = 0f; mixR[i] = 0f }
        // per-channel gains for this block
        for (c in 0 until 16) {
            val ang = (chPan[c].coerceIn(-1f, 1f) + 1f) * (PI.toFloat() / 4f)
            chGl[c] = cos(ang) * chVol[c]
            chGr[c] = sin(ang) * chVol[c]
            bendF[c] = if (chBend[c] == 0f) 1f else 2f.pow(chBend[c] / 12f)
        }
        // fade in / out of the accompaniment
        if (fadeDir != 0) {
            fade += fadeDir * (n / (SR * 2.5f))
            if (fade >= 1f) { fade = 1f; fadeDir = 0 }
            if (fade <= 0f) {
                fade = 0f; fadeDir = 0
                if (stopAfterFade) { stopSeq(); stopAfterFade = false; fade = 1f }
            }
        }
        var off = 0
        while (off < n) {
            var chunk = n - off
            if (seqRunning) {
                if (stepRemaining <= 0.0) {
                    fireStep()
                    stepRemaining += SR * 60.0 / (tempo * curStyle.clock)
                }
                if (seqRunning) {
                    val c = ceil(stepRemaining).toInt()
                    chunk = min(chunk, max(1, c))
                    stepRemaining -= chunk
                }
            }
            renderVoices(off, chunk)
            off += chunk
        }
        // reverb + master + limiter
        var pk = 0f
        val rv = reverbLevel
        for (i in 0 until n) {
            val wet = if (rv > 0.001f) reverb((mixL[i] + mixR[i]) * 0.5f * rv) else 0f
            var l = (mixL[i] + wet) * masterVol
            var r = (mixR[i] + wet) * masterVol
            if (eqOn) { l = eq(l, eqZL); r = eq(r, eqZR) }
            mixL[i] = l; mixR[i] = r
            val a = max(abs(l), abs(r))
            if (a > pk) pk = a
        }
        val target = if (pk * limGain > 0.9f) 0.9f / pk else 1f
        limGain = if (target < limGain) target else limGain + (target - limGain) * 0.02f
        val g0 = prevGain
        val g1 = limGain
        for (i in 0 until n) {
            val g = g0 + (g1 - g0) * (i / n.toFloat())
            val l = sat(mixL[i] * g)
            val r = sat(mixR[i] * g)
            out[i * 2] = (l * 32760f).toInt().toShort()
            out[i * 2 + 1] = (r * 32760f).toInt().toShort()
        }
        prevGain = g1
        peak = max(peak * 0.92f, pk)
        var cnt = 0
        for (v in mel) if (v.active) cnt++
        for (v in drm) if (v.active) cnt++
        activeVoices = cnt
    }

    /** Renders audio without an audio device (unit tests / offline checks). Returns interleaved 16-bit stereo. */
    internal fun renderOffline(frames: Int): ShortArray {
        val out = ShortArray(frames * 2)
        val tmp = ShortArray(512)
        var done = 0
        while (done < frames) {
            val n = min(256, frames - done)
            renderBlock(n, tmp)
            System.arraycopy(tmp, 0, out, done * 2, n * 2)
            done += n
        }
        return out
    }

    private fun eq(x0: Float, z: FloatArray): Float {
        var x = x0
        for (b in 0 until Tone.BANDS) {
            val o = b * 5
            val y = eqC[o] * x + z[b * 2]
            z[b * 2] = eqC[o + 1] * x - eqC[o + 3] * y + z[b * 2 + 1]
            z[b * 2 + 1] = eqC[o + 2] * x - eqC[o + 4] * y
            x = y
        }
        return x
    }

    private fun setTone(preset: Int) {
        val p = preset.coerceIn(0, Tone.COUNT - 1)
        eqOn = p != Tone.FLAT
        Tone.coefficients(p, SR.toFloat(), eqC)
        java.util.Arrays.fill(eqZL, 0f)
        java.util.Arrays.fill(eqZR, 0f)
    }

    private fun sat(x: Float): Float {
        val c = if (x > 3f) 3f else if (x < -3f) -3f else x
        return c * (27f + c * c) / (27f + 9f * c * c)
    }

    private fun reverb(x: Float): Float {
        var sum = 0f
        for (k in 0 until 4) {
            val buf = combBuf[k]
            val y = buf[combIdx[k]]
            combStore[k] = y * 0.7f + combStore[k] * 0.3f
            buf[combIdx[k]] = x + combStore[k] * 0.82f
            combIdx[k]++
            if (combIdx[k] >= buf.size) combIdx[k] = 0
            sum += y
        }
        var s = sum * 0.25f
        for (k in 0 until 2) {
            val buf = apBuf[k]
            val b = buf[apIdx[k]]
            val o = -s + b
            buf[apIdx[k]] = s + b * 0.5f
            apIdx[k]++
            if (apIdx[k] >= buf.size) apIdx[k] = 0
            s = o
        }
        return s
    }

    private fun noise(): Float {
        rng = rng * 1664525 + 1013904223
        return (rng shr 8) * (1f / 8388608f)
    }

    // ---------------------------------------------------------------------
    //  Commands
    // ---------------------------------------------------------------------
    private fun drain() {
        while (true) {
            val c = queue.poll() ?: break
            apply(c)
        }
    }

    private fun apply(c: Cmd) {
        when (c.t) {
            Op.NOTE_ON -> noteOnInternal(c.a and 15, c.b, c.c, false)
            Op.NOTE_OFF -> noteOffInternal(c.a and 15, c.b)
            Op.PROGRAM -> chProg[c.a and 15] = c.b.coerceIn(0, TIMBRES.size - 1)
            Op.CH_VOL -> chVol[c.a and 15] = c.f
            Op.CH_PAN -> chPan[c.a and 15] = c.f
            Op.SUSTAIN -> {
                val ch = c.a and 15
                val on = c.b != 0
                chSus[ch] = on
                if (!on) for (v in mel) if (v.active && v.ch == ch && v.sustained) { v.sustained = false; releaseVoice(v) }
            }
            Op.BEND -> chBend[c.a and 15] = c.f
            Op.MOD -> chMod[c.a and 15] = c.f
            Op.ALL_OFF -> {
                for (v in mel) if (v.active && (c.a < 0 || v.ch == c.a) && !v.seq) { v.sustained = false; releaseVoice(v) }
            }
            Op.PANIC -> {
                for (v in mel) if (v.active) { v.sustained = false; releaseVoice(v) }
                for (v in drm) if (v.active) fastFade(v)
                offCount = 0
            }
            Op.TEMPO -> { tempo = c.a.coerceIn(30, 300); pubTempo = tempo }
            Op.START -> if (!seqRunning) startSeq()
            Op.STOP -> if (seqRunning) stopSeq()
            Op.SECTION -> requestSection(Sec.values()[c.a.coerceIn(0, Sec.values().size - 1)])
            Op.CHORD -> { chordRoot = c.a; chordQ = c.b.coerceIn(0, Chords.INTERVALS.size - 1) }
            Op.STYLE -> setStyle(c.a)
            Op.TRACK_VOL -> trackVol[c.a and 7] = c.f
            Op.TRACK_MUTE -> trackMute[c.a and 7] = c.b != 0
            Op.FADE -> toggleFade()
            Op.REVERB -> reverbLevel = c.f
            Op.TONE -> setTone(c.a)
            Op.MASTER -> masterVol = c.f
            Op.AUTOFILL -> autoFill = c.a != 0
            Op.TRANSPOSE -> transpose = c.a
            Op.STYLE_VOL -> styleVol = c.f
            Op.ACMP -> acmpOn = c.a != 0
        }
    }

    private fun setStyle(idx: Int) {
        val st = Styles.all[idx.coerceIn(0, Styles.all.size - 1)]
        curStyle = st
        drumKit = st.drumKit
        for (i in 0 until 6) {
            val vi = Voices.indexOf(st.trackVoices[i])
            if (vi >= 0) chProg[Track.CHANNEL[i + 2]] = Voices.ALL[vi].timbre
        }
        if (seqRunning) { stepInBar = 0; barInSec = 0 }
    }

    // ---------------------------------------------------------------------
    //  Sequencer
    // ---------------------------------------------------------------------
    private fun startSeq() {
        section = armedIntro ?: targetMain
        armedIntro = null
        pubArmed = -1
        queued = null
        stepInBar = 0
        barInSec = 0
        measure = 1
        stepRemaining = 0.0
        seqRunning = true
        pubRunning = true
        pubSection = section.ordinal
        pubMeasure = 1
    }

    private fun stopSeq() {
        seqRunning = false
        pubRunning = false
        offCount = 0
        for (v in mel) if (v.active && v.seq) { v.sustained = false; releaseVoice(v) }
        fade = 1f; fadeDir = 0
    }

    private fun toggleFade() {
        if (seqRunning) {
            if (fade > 0.5f && fadeDir >= 0) { fadeDir = -1; stopAfterFade = true }
            else { fadeDir = 1; stopAfterFade = false }
        } else {
            fade = 0f; fadeDir = 1; stopAfterFade = false
            startSeq()
        }
    }

    private fun requestSection(s: Sec) {
        val steps = curStyle.stepsPerBar
        if (!seqRunning) {
            if (s.isMain) { targetMain = s; armedIntro = null; pubArmed = -1; pubTarget = s.ordinal; pubSection = s.ordinal }
            else if (s.isIntro) { armedIntro = s; pubArmed = s.ordinal; pubSection = s.ordinal }
            return
        }
        if (section.isEnding) return
        if (s.isMain) {
            targetMain = s
            pubTarget = s.ordinal
            if (section.isMain && s != section) {
                if (autoFill && stepInBar <= steps - maxOf(4, steps / 4)) { section = s.fillFor(); barInSec = 0; pubSection = section.ordinal }
                else queued = s
            }
        } else if (s.isIntro || s == Sec.BREAK || s.isEnding) {
            queued = s
        } else if (s.isFill) {
            if (section.isMain) {
                if (stepInBar <= steps - maxOf(4, steps / 4)) { section = s; barInSec = 0; pubSection = s.ordinal } else queued = s
            }
        }
    }

    private fun onBarEnd() {
        barInSec++
        measure++
        pubMeasure = measure
        val q = queued
        if (q != null) {
            section = q; queued = null; barInSec = 0
            pubSection = section.ordinal
            return
        }
        if (barInSec >= curStyle.sectionBars(section.ordinal, section.bars)) {
            barInSec = 0
            if (section.isEnding) { stopSeq(); return }
            if (section.isIntro || section.isFill || section == Sec.BREAK) section = targetMain
            pubSection = section.ordinal
        }
    }

    private fun fireStep() {
        // pending note-offs
        var i = 0
        while (i < offCount) {
            offLeft[i]--
            if (offLeft[i] <= 0) {
                noteOffInternal(offCh[i], offNote[i])
                offCount--
                offCh[i] = offCh[offCount]; offNote[i] = offNote[offCount]; offLeft[i] = offLeft[offCount]
            } else i++
        }
        val st = curStyle
        val bar = st.bar(section.ordinal, barInSec)
        if (stepInBar < bar.size) {
            for (ev in bar[stepInBar]) playEv(ev)
        }
        pubBeat = stepInBar / st.beatSteps + 1
        stepInBar++
        if (stepInBar >= st.stepsPerBar) {
            stepInBar = 0
            onBarEnd()
        }
    }

    private fun wrap12(x: Int): Int = ((x % 12) + 12) % 12

    private fun chordTone(idx: Int): Int {
        val iv = Chords.INTERVALS[chordQ]
        val n = iv.size
        val i = ((idx % n) + n) % n
        val oct = Math.floorDiv(idx, n)
        val root = chordRoot + transpose
        val base = 53 + wrap12(root - 53)
        return base + iv[i] + 12 * oct
    }

    private fun playEv(ev: Ev) {
        val t = ev.track
        if (trackMute[t] || trackVol[t] <= 0.01f) return
        if (t >= Track.BASS && !acmpOn) return
        val vel = (ev.vel * trackVol[t] * styleVol).toInt().coerceIn(1, 127)
        if (t <= Track.RHYTHM2) { noteOnInternal(9, ev.note, vel, true); return }
        val ch = Track.CHANNEL[t]
        if (ev.abs) {
            // Imported (STY) event: absolute note written for the style's source chord,
            // converted to the chord being played.
            val n = convertAbs(ev)
            noteOnInternal(ch, n, vel, true)
            schedOff(ch, n, ev.len)
            return
        }
        if (t == Track.BASS) {
            val root = chordRoot + transpose
            val n = (28 + wrap12(root - 28) + ev.note).coerceIn(0, 127)
            noteOnInternal(ch, n, vel, true)
            schedOff(ch, n, ev.len)
            return
        }
        if (ev.note < 0) {
            val cnt = Chords.INTERVALS[chordQ].size
            for (k in 0 until cnt) {
                val n = (chordTone(k) + 12 * ev.oct).coerceIn(0, 127)
                noteOnInternal(ch, n, vel, true)
                schedOff(ch, n, ev.len)
            }
        } else {
            val n = (chordTone(ev.note) + 12 * ev.oct).coerceIn(0, 127)
            noteOnInternal(ch, n, vel, true)
            schedOff(ch, n, ev.len)
        }
    }

    /**
     * Converts an imported note (written over the style's source chord, normally C) to the chord now being played.
     * Source chord tones follow the played chord (3rd, 5th, 7th), the root moves to the chord root,
     * and the result is folded into the note range given by the style.
     */
    private fun convertAbs(ev: Ev): Int {
        var n = ev.note - ev.srcRoot
        if (ev.ntt != 0) {
            val iv = Chords.INTERVALS[chordQ]
            var third = -1
            var fifth = 7
            var seventh = -1
            for (x in iv) {
                if (x == 2 || x == 3 || x == 4 || x == 5) { if (third < 0) third = x }
                else if (x == 6 || x == 7 || x == 8) fifth = x
                else if (x == 9 || x == 10 || x == 11) seventh = x
            }
            if (third < 0) third = 4
            val pc = wrap12(n)
            val base = n - pc
            val mapped = when (pc) {
                3, 4 -> third
                7 -> fifth
                10, 11 -> if (seventh >= 0) seventh else if (third == 3) 10 else 11
                else -> pc
            }
            n = base + mapped
            var shift = wrap12(chordRoot + transpose)
            if (shift > ev.highKey) shift -= 12
            n += shift
        } else {
            n += transpose
        }
        if (ev.lo < ev.hi) {
            while (n > ev.hi && n - 12 >= 0) n -= 12
            while (n < ev.lo && n + 12 <= 127) n += 12
        }
        return n.coerceIn(0, 127)
    }

    private fun schedOff(ch: Int, note: Int, len: Int) {
        if (offCount >= offCh.size) return
        offCh[offCount] = ch; offNote[offCount] = note; offLeft[offCount] = max(1, len)
        offCount++
    }

    // ---------------------------------------------------------------------
    //  Voices
    // ---------------------------------------------------------------------
    private fun noteOnInternal(ch: Int, note: Int, vel: Int, fromSeq: Boolean) {
        if (vel <= 0) { noteOffInternal(ch, note); return }
        if (ch == 9) { drumOn(note, vel, fromSeq); return }
        val def = TIMBRES[chProg[ch]]
        var v: V? = null
        for (x in mel) if (!x.active) { v = x; break }
        var startEnv = 0f
        if (v == null) {
            // steal: quietest releasing voice, otherwise the oldest voice
            var best: V? = null
            for (x in mel) if (x.stage == 2 && (best == null || x.env < best.env)) best = x
            if (best == null) for (x in mel) if (best == null || x.order < best.order) best = x
            v = best!!
            startEnv = min(v.env, 0.6f)
        }
        v.active = true
        v.ch = ch
        v.note = note
        v.timbre = chProg[ch]
        v.band = if (note < 60) 0 else if (note < 84) 1 else 2
        v.held = true
        v.sustained = false
        v.seq = fromSeq
        val f = 440f * 2f.pow((note - 69) / 12f)
        v.inc1 = f / SR
        v.inc2 = v.inc1 * 2f.pow(def.detune / 1200f)
        v.ph1 = 0f
        v.ph2 = 0.37f
        v.env = startEnv
        v.stage = 0
        v.atkInc = 1f / (max(def.attack, 0.002f) * SR)
        v.decCoef = exp(-1f / (def.decayTc * SR))
        v.sus = def.sustain
        v.relCoef = exp(-1f / (def.release * SR))
        v.mix = def.m0
        v.mixT = def.m1
        v.mixC = 1f - exp(-1f / (def.mTc * SR))
        v.vibPh = 0f
        val vv = vel / 127f
        v.amp = vv.pow(1.2f) * 0.22f * def.gain
        v.order = ++orderCounter
    }

    private fun noteOffInternal(ch: Int, note: Int) {
        if (ch == 9) return
        for (v in mel) {
            if (v.active && v.ch == ch && v.note == note && v.held) {
                v.held = false
                if (chSus[ch]) v.sustained = true else releaseVoice(v)
            }
        }
    }

    private fun releaseVoice(v: V) {
        v.held = false
        v.stage = 2
    }

    private fun renderVoices(start: Int, n: Int) {
        var load = 0
        for (v in mel) if (v.active) load++
        melLoad = load
        for (v in mel) if (v.active) renderMel(v, start, n)
        for (v in drm) if (v.active) renderDrum(v, start, n)
    }

    private fun renderMel(v: V, start: Int, n: Int) {
        val def = TIMBRES[v.timbre]
        val tA = tabs[v.timbre][0][v.band]
        val tB = tabs[v.timbre][1][v.band]
        val fd = if (v.seq) fade else 1f
        val gl = chGl[v.ch] * v.amp * fd
        val gr = chGr[v.ch] * v.amp * fd
        val bf = bendF[v.ch]
        val modD = def.vibDepth + chMod[v.ch] * 0.35f
        val vibInc = (if (def.vibRate > 0f) def.vibRate else 5.5f) / SR
        val uni = def.detune > 0f
        val breath = def.breath
        var env = v.env
        var stage = v.stage
        var mix = v.mix
        var ph1 = v.ph1
        var ph2 = v.ph2
        var vibPh = v.vibPh
        val sus = v.sus
        // many voices sounding: let released tails fade twice as fast so the pool does not fill up
        val relC = if (melLoad > TAIL_PRESSURE) v.relCoef * v.relCoef else v.relCoef
        var i = 0
        var dead = false
        while (i < n) {
            when (stage) {
                0 -> { env += v.atkInc; if (env >= 1f) { env = 1f; stage = 1 } }
                1 -> {
                    env = sus + (env - sus) * v.decCoef
                    if (sus <= 0f && env < DEAD_ENV) dead = true
                }
                else -> { env *= relC; if (env < DEAD_ENV) dead = true }
            }
            if (dead) break
            mix += (v.mixT - mix) * v.mixC
            var lfo = 0f
            if (modD > 0f) {
                lfo = sinTab[(vibPh * TS).toInt() and (TS - 1)]
                vibPh += vibInc
                if (vibPh >= 1f) vibPh -= 1f
            }
            val fm = bf * (1f + lfo * modD * 0.0578f)
            ph1 += v.inc1 * fm
            if (ph1 >= 1f) ph1 -= 1f
            val p1 = ph1 * TS
            val k1 = p1.toInt()
            val fr1 = p1 - k1
            val i1 = k1 and (TS - 1)
            val i2 = (k1 + 1) and (TS - 1)
            var s = (tA[i1] + (tA[i2] - tA[i1]) * fr1) * (1f - mix) + (tB[i1] + (tB[i2] - tB[i1]) * fr1) * mix
            if (uni) {
                ph2 += v.inc2 * fm
                if (ph2 >= 1f) ph2 -= 1f
                val p2 = ph2 * TS
                val k2 = p2.toInt()
                val fr2 = p2 - k2
                val j1 = k2 and (TS - 1)
                val j2 = (k2 + 1) and (TS - 1)
                val s2 = (tA[j1] + (tA[j2] - tA[j1]) * fr2) * (1f - mix) + (tB[j1] + (tB[j2] - tB[j1]) * fr2) * mix
                s = (s + s2) * 0.5f
            }
            if (breath > 0f) s += noise() * breath
            val o = s * env
            mixL[start + i] += o * gl
            mixR[start + i] += o * gr
            i++
        }
        v.env = env; v.stage = stage; v.mix = mix; v.ph1 = ph1; v.ph2 = ph2; v.vibPh = vibPh
        if (dead) v.active = false
    }

    // ---- drums ----
    private fun drumOn(note: Int, vel: Int, fromSeq: Boolean) {
        if (DrumMap.isMuted(note)) return          // crash cymbals are off (DrumMap.isMuted)
        val kind = DrumMap.kindOf(note)
        if (kind == DrumMap.SILENT) { drumSilentHits++; return }
        val freq = DrumMap.freqOf(note)
        // closed hat chokes open hat with a fast fade (was an instant cut = click)
        if (kind == DrumMap.HAT_CLOSED) for (x in drm) if (x.active && x.dKind == DrumMap.HAT_OPEN) fastFade(x)
        var v: V? = null
        for (x in drm) if (!x.active) { v = x; break }
        if (v == null) {
            // steal the quietest voice (its tail is already nearly silent), not the oldest
            var best = drm[0]
            var bestLevel = Float.MAX_VALUE
            for (x in drm) {
                val xs = x.dSmp
                val lvl = if (xs != null) x.amp * x.dSEnv * (1f - x.dPos / xs.size) else x.dToneEnv * x.dToneAmt + x.dNoiseEnv * x.dNoiseAmt
                if (lvl < bestLevel) { bestLevel = lvl; best = x }
            }
            v = best
            drumSteals++
        }
        v.active = true
        v.seq = fromSeq
        v.ch = 9
        v.note = note
        v.dKind = kind
        v.ph1 = 0f
        v.dPitch = 1f
        v.dLp = 0f
        v.dMetalAmt = 0f; v.dMetalEnv = 0f; v.dMetalCoef = 0f; v.dMetalSet = 0
        v.dRatio = 0f; v.dAmt2 = 0f; v.dSquare = false; v.ph2 = 0f
        v.dSmp = null; v.dSEnv = 1f; v.dSCoef = 1f
        for (k in 0 until 6) v.dPh[k] = k * 0.21f
        v.order = ++orderCounter
        v.amp = (vel / 127f).pow(1.1f) * 0.55f
        v.dLife = (DrumMap.lifeSeconds(kind) * SR).toInt()
        val smp = drumSamples.get(note)
        if (smp != null) {
            // recorded sample: plays once to its end (drums ignore Note Off), kit pitch/decay applied, no noise generator
            val pf0 = DrumKit.pitchFactor(drumKit, kind)
            val df0 = DrumKit.decayFactor(drumKit, kind)
            v.dSmp = smp.pcm
            v.dPos = 0f
            v.dStep = smp.rate.toFloat() / SR * pf0
            v.dToneAmt = 0f; v.dNoiseAmt = 0f; v.dToneEnv = 0f; v.dNoiseEnv = 0f
            v.dToneCoef = 0f; v.dNoiseCoef = 0f
            v.dAtk = 1f; v.dAtkInc = 0f
            val outFrames = smp.pcm.size / v.dStep
            v.dLife = (outFrames * (if (df0 < 1f) df0 else 1f)).toInt() + 8
            return
        }
        // f0, f1, pitchTc, toneTc, toneAmt, noiseAmt, noiseTc, lpA
        when (kind) {
            DrumMap.KICK -> cfg(v, 150f, 50f, 0.03f, 0.30f, 1f, 0.06f, 0.015f, 0.3f)
            DrumMap.SNARE -> cfg(v, 200f, 175f, 0.02f, 0.10f, 0.55f, 0.85f, 0.15f, 0.08f)
            DrumMap.CLAP -> cfg(v, 0f, 0f, 0.02f, 0.05f, 0f, 0.9f, 0.12f, 0.15f)
            DrumMap.HAT_CLOSED -> {
                // was noise only ("tsss"). Now metal partials + less noise
                cfg(v, 0f, 0f, 0.02f, 0.05f, 0f, 0.35f, 0.03f, 0.45f)
                v.dMetalAmt = HAT_METAL_GAIN; v.dMetalEnv = 1f; v.dMetalCoef = exp(-1f / (0.035f * SR)); v.dMetalSet = 1
            }
            DrumMap.HAT_OPEN -> {
                cfg(v, 0f, 0f, 0.02f, 0.05f, 0f, 0.35f, 0.30f, 0.45f)
                v.dMetalAmt = HAT_METAL_GAIN; v.dMetalEnv = 1f; v.dMetalCoef = exp(-1f / (0.28f * SR)); v.dMetalSet = 1
            }
            DrumMap.TOM -> cfg(v, freq * 1.4f, freq, 0.06f, 0.30f, 1f, 0.12f, 0.04f, 0.2f)
            DrumMap.CRASH -> {
                // was white noise only ("shhhh"). Now: 6 inharmonic partials (the metal) + a shorter noise burst
                cfg(v, 0f, 0f, 0.02f, 0.05f, 0f, 0.30f, 0.40f, 0.45f)
                v.dMetalAmt = METAL_GAIN; v.dMetalEnv = 1f; v.dMetalCoef = exp(-1f / (0.95f * SR))
            }
            DrumMap.RIDE -> cfg(v, 620f, 620f, 0.02f, 0.50f, 0.15f, 0.30f, 0.50f, 0.4f)
            DrumMap.TAMB -> cfg(v, 0f, 0f, 0.02f, 0.05f, 0f, 0.5f, 0.06f, 0.5f)
            DrumMap.STICK -> cfg(v, 400f, 400f, 0.02f, 0.05f, 0.8f, 0.3f, 0.03f, 0.2f)
            DrumMap.SHAKER -> cfg(v, 0f, 0f, 0.02f, 0.05f, 0f, 0.5f, 0.05f, 0.35f)
            DrumMap.RING -> cfg(v, freq, freq, 0.02f, 0.60f, 0.9f, 0.05f, 0.02f, 0.3f)
            DrumMap.WOOD -> cfg(v, freq, freq, 0.02f, 0.05f, 1f, 0.12f, 0.01f, 0.25f)
            DrumMap.COWBELL -> {
                // 540 Hz + 800 Hz square-ish pair, short click, ring of about 0.1 s, no pitch sweep
                cfg(v, freq, freq, 0.02f, 0.09f, 0.55f, 0.20f, 0.004f, 0.5f)
                v.dRatio = 800f / 540f; v.dAmt2 = 0.45f; v.dSquare = true
            }
            DrumMap.CONGA -> {
                // low skin tone with a downward pitch drop and a very short slap
                val tc = if (DrumMap.isShortStroke(note)) 0.08f else 0.17f
                cfg(v, freq * 1.35f, freq, 0.03f, tc, 0.85f, 0.30f, 0.012f, 0.3f)
            }
            else -> cfg(v, freq * 1.1f, freq, 0.02f, 0.14f, 0.9f, 0.08f, 0.03f, 0.3f)
        }
        // kit tuning (only styles with a recognised kit differ; Standard changes nothing). Keyboard hits use the same kit.
        val pf = DrumKit.pitchFactor(drumKit, kind)
        if (pf != 1f) { v.dF0 *= pf; v.dF1 *= pf }
        val df = DrumKit.decayFactor(drumKit, kind)
        if (df != 1f) {
            v.dToneCoef = DrumKit.stretchCoef(v.dToneCoef, df)
            v.dNoiseCoef = DrumKit.stretchCoef(v.dNoiseCoef, df)
            if (v.dMetalAmt > 0f) v.dMetalCoef = DrumKit.stretchCoef(v.dMetalCoef, df)
            if (df > 1f) v.dLife = (v.dLife * df).toInt()
        }
        if (DrumMap.isNoiseKind(kind)) { v.dAtk = 0f; v.dAtkInc = 1f / (NOISE_ATTACK * SR) } else { v.dAtk = 1f; v.dAtkInc = 0f }
    }

    /** Ends a drum voice with a short exponential fade (a few ms) instead of cutting it. */
    private fun fastFade(v: V) {
        val k = exp(-1f / (FAST_FADE * SR))
        v.dToneCoef = k
        v.dNoiseCoef = k
        v.dMetalCoef = k      // the metal partials (crash, hi-hats) must be cut too, or a choke / panic leaves them ringing
        v.dLife = 0
        v.dSCoef = k          // recorded-sample voices fade through their own gain
    }

    private fun cfg(
        v: V, f0: Float, f1: Float, pitchTc: Float, toneTc: Float, toneAmt: Float,
        noiseAmt: Float, noiseTc: Float, lpA: Float
    ) {
        v.dF0 = f0; v.dF1 = f1
        v.dPitchCoef = exp(-1f / (pitchTc * SR))
        v.dToneEnv = 1f
        v.dToneCoef = exp(-1f / (toneTc * SR))
        v.dToneAmt = toneAmt
        v.dNoiseEnv = 1f
        v.dNoiseCoef = exp(-1f / (noiseTc * SR))
        v.dNoiseAmt = noiseAmt
        v.dLpA = lpA
    }

    private fun renderSampleDrum(v: V, smp: ShortArray, start: Int, n: Int) {
        val fd = if (v.seq) fade else 1f
        val gl = chGl[9] * v.amp * fd * (1f / 32768f)
        val gr = chGr[9] * v.amp * fd * (1f / 32768f)
        var pos = v.dPos
        val step = v.dStep
        var env = v.dSEnv
        var coef = v.dSCoef
        var life = v.dLife
        val last = smp.size - 1
        var i = 0
        while (i < n) {
            val ip = pos.toInt()
            if (ip >= last) { v.active = false; break }
            val fr = pos - ip
            val o = (smp[ip] + (smp[ip + 1] - smp[ip]) * fr) * env
            mixL[start + i] += o * gl
            mixR[start + i] += o * gr
            pos += step
            env *= coef
            i++
            if (life > 0) { life--; if (life == 0) { fastFade(v); coef = v.dSCoef } }
            if (env < 0.001f) { v.active = false; break }
        }
        v.dPos = pos; v.dSEnv = env; v.dLife = life
    }

    private fun renderDrum(v: V, start: Int, n: Int) {
        val sm = v.dSmp
        if (sm != null) { renderSampleDrum(v, sm, start, n); return }
        val fd = if (v.seq) fade else 1f
        val gl = chGl[9] * v.amp * fd
        val gr = chGr[9] * v.amp * fd
        var te = v.dToneEnv
        var ne = v.dNoiseEnv
        var tp = v.ph1
        var pitch = v.dPitch
        var lp = v.dLp
        var atk = v.dAtk
        var life = v.dLife
        var me = v.dMetalEnv
        var tp2 = v.ph2
        var i = 0
        while (i < n) {
            var o = 0f
            if (v.dToneAmt > 0f) {
                val f = v.dF1 + (v.dF0 - v.dF1) * pitch
                pitch *= v.dPitchCoef
                tp += f / SR
                if (tp >= 1f) tp -= 1f
                var w = sinTab[(tp * TS).toInt() and (TS - 1)]
                if (v.dSquare) w = (w + sinTab[(tp * 3f * TS).toInt() and (TS - 1)] * 0.333f + sinTab[(tp * 5f * TS).toInt() and (TS - 1)] * 0.2f) * 0.8f
                o += w * te * v.dToneAmt
                if (v.dRatio > 0f) {
                    tp2 += f * v.dRatio / SR
                    if (tp2 >= 1f) tp2 -= 1f
                    var w2 = sinTab[(tp2 * TS).toInt() and (TS - 1)]
                    if (v.dSquare) w2 = (w2 + sinTab[(tp2 * 3f * TS).toInt() and (TS - 1)] * 0.333f + sinTab[(tp2 * 5f * TS).toInt() and (TS - 1)] * 0.2f) * 0.8f
                    o += w2 * te * v.dAmt2
                }
            }
            if (v.dMetalAmt > 0f) {
                var m = 0f
                val hz = if (v.dMetalSet == 1) METAL_HAT_HZ else METAL_HZ
                for (k in 0 until 6) {
                    var q = v.dPh[k] + hz[k] / SR
                    if (q >= 1f) q -= 1f
                    v.dPh[k] = q
                    m += sinTab[(q * TS).toInt() and (TS - 1)]
                }
                o += m * me * v.dMetalAmt * atk
                me *= v.dMetalCoef
            }
            te *= v.dToneCoef
            val nz = noise()
            lp += v.dLpA * (nz - lp)
            if (atk < 1f) { atk += v.dAtkInc; if (atk > 1f) atk = 1f }
            o += (nz - lp) * ne * v.dNoiseAmt * atk
            ne *= v.dNoiseCoef
            mixL[start + i] += o * gl
            mixR[start + i] += o * gr
            i++
            if (life > 0) { life--; if (life == 0) fastFade(v) }
            if (te < 0.001f && ne < 0.001f && (v.dMetalAmt == 0f || me < 0.001f)) { v.active = false; break }
        }
        v.dMetalEnv = me; v.ph2 = tp2
        v.dAtk = atk; v.dLife = life
        v.dToneEnv = te; v.dNoiseEnv = ne; v.ph1 = tp; v.dPitch = pitch; v.dLp = lp
    }
}
