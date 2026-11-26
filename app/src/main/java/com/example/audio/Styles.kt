package com.example.audio

/** Arranger sections, like the SX900 style-control buttons. */
enum class Sec(val label: String, val bars: Int) {
    INTRO_1("INTRO 1", 2), INTRO_2("INTRO 2", 2), INTRO_3("INTRO 3", 2),
    MAIN_A("MAIN A", 2), MAIN_B("MAIN B", 2), MAIN_C("MAIN C", 2), MAIN_D("MAIN D", 2),
    FILL_A("FILL A", 1), FILL_B("FILL B", 1), FILL_C("FILL C", 1), FILL_D("FILL D", 1),
    BREAK("BREAK", 1),
    END_1("ENDING 1", 2), END_2("ENDING 2", 2), END_3("ENDING 3", 2);

    val isIntro: Boolean get() = name.startsWith("INTRO")
    val isMain: Boolean get() = name.startsWith("MAIN")
    val isFill: Boolean get() = name.startsWith("FILL")
    val isEnding: Boolean get() = name.startsWith("END")

    fun fillFor(): Sec = when (this) {
        MAIN_A -> FILL_A
        MAIN_B -> FILL_B
        MAIN_C -> FILL_C
        MAIN_D -> FILL_D
        else -> this
    }
}

/** The 8 accompaniment tracks. */
object Track {
    const val RHYTHM1 = 0
    const val RHYTHM2 = 1
    const val BASS = 2
    const val CHORD1 = 3
    const val CHORD2 = 4
    const val PAD = 5
    const val PHRASE1 = 6
    const val PHRASE2 = 7
    val NAMES = listOf("RHY 1", "RHY 2", "BASS", "CHORD 1", "CHORD 2", "PAD", "PHRASE 1", "PHRASE 2")
    /** MIDI-style channel used by each track (9 = drums). */
    val CHANNEL = intArrayOf(9, 9, 0, 1, 2, 3, 4, 5)
}

/** One event inside a bar. For BASS `note` is a semitone offset from the chord root.
 *  For chord/phrase tracks `note` is a chord-tone index (-1 = whole chord). */
class Ev(
    @JvmField val step: Int,
    @JvmField val track: Int,
    @JvmField val note: Int,
    @JvmField val len: Int,
    @JvmField val vel: Int,
    @JvmField val oct: Int = 0,
    /** true = imported (STY) event: `note` is an absolute MIDI note in the style's source chord. */
    @JvmField val abs: Boolean = false,
    @JvmField val srcRoot: Int = 0,   // source chord root (0 = C)
    @JvmField val ntt: Int = 0,       // 0 = play as written, otherwise convert with the played chord
    @JvmField val highKey: Int = 5,   // roots above this key are moved down an octave
    @JvmField val lo: Int = 0,        // note range limits after conversion
    @JvmField val hi: Int = 127
)

object Chords {
    val NAMES = arrayOf("Maj", "min", "7", "maj7", "m7", "sus4", "sus2", "dim", "aug", "6", "m6", "m7b5", "dim7", "5")
    val INTERVALS = arrayOf(
        intArrayOf(0, 4, 7), intArrayOf(0, 3, 7), intArrayOf(0, 4, 7, 10), intArrayOf(0, 4, 7, 11),
        intArrayOf(0, 3, 7, 10), intArrayOf(0, 5, 7), intArrayOf(0, 2, 7), intArrayOf(0, 3, 6),
        intArrayOf(0, 4, 8), intArrayOf(0, 4, 7, 9), intArrayOf(0, 3, 7, 9), intArrayOf(0, 3, 6, 10),
        intArrayOf(0, 3, 6, 9), intArrayOf(0, 7, 12)
    )
    val NOTE_NAMES = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    private val masks = IntArray(INTERVALS.size) { q ->
        var m = 0
        for (iv in INTERVALS[q]) m = m or (1 shl (iv % 12))
        m
    }

    fun name(root: Int, q: Int): String =
        NOTE_NAMES[((root % 12) + 12) % 12] + " " + NAMES[q.coerceIn(0, NAMES.size - 1)]

    /**
     * User chord mode: the chord only changes when at least [keys] keys are held (null = keep the old chord).
     *  1 key : one key plays the major chord of that note; more keys use the normal detection.
     *  2 keys: lowest key is the root, the second key chooses the chord (major 3rd = major, minor 3rd = minor,
     *          5th = major, minor 7th = dominant 7th, major 7th = maj7, 2nd = sus2, 4th = sus4); anything else is detected normally.
     *  3 keys: normal detection of the three keys.
     */
    fun detectKeys(notes: Collection<Int>, keys: Int): IntArray? {
        if (keys <= 0) return detect(notes)
        if (notes.size < keys) return null
        if (keys == 1) return if (notes.size == 1) intArrayOf(notes.first() % 12, 0) else detect(notes)
        if (keys == 2 && notes.size == 2) {
            val s = notes.sorted()
            val root = s[0] % 12
            return when (((s[1] - s[0]) % 12 + 12) % 12) {
                4, 7 -> intArrayOf(root, 0)
                3 -> intArrayOf(root, 1)
                10 -> intArrayOf(root, 2)
                11 -> intArrayOf(root, 3)
                2 -> intArrayOf(root, 6)
                5 -> intArrayOf(root, 5)
                else -> detect(notes)
            }
        }
        return detect(notes)
    }

    /** Finds root and quality from the notes held in the chord area. Returns null if unknown. */
    fun detect(notes: Collection<Int>): IntArray? {
        if (notes.isEmpty()) return null
        val sorted = notes.sorted()
        val pcs = BooleanArray(12)
        for (n in sorted) pcs[n % 12] = true
        val count = pcs.count { it }
        if (count == 1) return intArrayOf(sorted[0] % 12, 0)
        // roots to try: lowest note first, then the other notes from low to high
        val rootOrder = ArrayList<Int>()
        for (n in sorted) if (!rootOrder.contains(n % 12)) rootOrder.add(n % 12)
        for (root in rootOrder) {
            var mask = 0
            for (pc in 0 until 12) if (pcs[pc]) mask = mask or (1 shl ((pc - root + 12) % 12))
            for (q in masks.indices) if (masks[q] == mask) return intArrayOf(root, q)
        }
        // fallback: lowest note as root, accept major/minor triad hidden inside a bigger chord
        val root = sorted[0] % 12
        var mask = 0
        for (pc in 0 until 12) if (pcs[pc]) mask = mask or (1 shl ((pc - root + 12) % 12))
        if ((mask and masks[1]) == masks[1]) return intArrayOf(root, 1)
        if ((mask and masks[0]) == masks[0]) return intArrayOf(root, 0)
        return null
    }
}

class VoicePreset(val id: String, val name: String, val category: String, val timbre: Int)

/** Voice list. `timbre` is the index into SynthEngine.TIMBRES. These are synthesized voices, not recorded samples. */
object Voices {
    val ALL = listOf(
        VoicePreset("grand", "Grand Piano", "Piano", 0),
        VoicePreset("epiano", "E.Piano", "Keys", 1),
        VoicePreset("organ", "Drawbar Organ", "Organ", 2),
        VoicePreset("strings", "Strings", "Strings", 3),
        VoicePreset("brass", "Brass Section", "Brass", 4),
        VoicePreset("lead", "Synth Lead", "Synth", 5),
        VoicePreset("pad", "Warm Pad", "Synth", 6),
        VoicePreset("guitar", "Nylon Guitar", "Guitar", 7),
        VoicePreset("bass", "Finger Bass", "Bass", 8),
        VoicePreset("flute", "Flute", "Woodwind", 9),
        VoicePreset("krar", "Krar (Ethiopian lyre)", "Ethiopian", 10),
        VoicePreset("masinko", "Masinko (Ethiopian fiddle)", "Ethiopian", 11),
        VoicePreset("washint", "Washint (Ethiopian flute)", "Ethiopian", 12)
    )
    fun indexOf(id: String): Int = ALL.indexOfFirst { it.id == id }
}

/** Per-style pattern settings. Arrays are indexed by intensity level 0..3 (MAIN A..D). */
class Feel(
    val steps: Int, val beatSteps: Int,
    val kick: Array<IntArray>, val snare: Array<IntArray>, val hat: Array<IntArray>, val perc: Array<IntArray>,
    val bass: Array<IntArray>,   // flattened triples: step, semitone offset, length
    val chord: Array<IntArray>,  // flattened pairs: step, length
    val arp: Array<IntArray>,    // steps for arpeggio notes
    val brass: Array<IntArray>,  // flattened pairs: step, length
    val hatNote: Int = 42, val percNote: Int = 54
)

class Style(
    val name: String, val category: String, val tempo: Int, val timeSig: String,
    val stepsPerBar: Int, val beatSteps: Int,
    val trackVoices: List<String>,         // voice ids for tracks BASS..PHRASE2
    val ots: List<List<String>>,           // 4 One Touch Settings: voice ids for RIGHT1..3 ("" = off)
    private val sections: Array<Array<Array<Array<Ev>>>>,
    /** Steps per quarter-note tempo beat. 4 = the original 16th-note grid of built-in styles. */
    val clock: Int = 4,
    /** Where an imported style came from (empty for built-in styles). */
    val source: String = "",
    /** Human readable import report (sections found, channels, warnings). */
    val info: String = "",
    /** Drum kit id (see [DrumKit]); 0 = Standard. Built-in styles keep the default. */
    val drumKit: Int = 0
) {
    /** Number of bars of a section (the enum default if the style has no data for it). */
    fun sectionBars(sec: Int, def: Int): Int {
        val n = sections[sec].size
        return if (n > 0) n else def
    }

    private val empty: Array<Array<Ev>> = Array(stepsPerBar) { emptyArray<Ev>() }
    fun bar(sec: Int, barIdx: Int): Array<Array<Ev>> {
        val s = sections[sec]
        if (s.isEmpty()) return empty
        return s[barIdx % s.size]
    }
}

object Styles {
    private fun ia(vararg v: Int) = v
    private val NONE = IntArray(0)
    private fun range(from: Int, to: Int, by: Int = 1): IntArray {
        val l = ArrayList<Int>()
        var i = from
        while (i <= to) { l.add(i); i += by }
        return l.toIntArray()
    }

    private val popFeel = Feel(
        steps = 16, beatSteps = 4,
        kick = arrayOf(ia(0, 8), ia(0, 8, 10), ia(0, 6, 8, 10), ia(0, 3, 6, 8, 10, 14)),
        snare = arrayOf(ia(4, 12), ia(4, 12), ia(4, 12), ia(4, 12, 15)),
        hat = arrayOf(ia(0, 4, 8, 12), range(0, 14, 2), range(0, 14, 2), range(0, 15)),
        perc = arrayOf(NONE, ia(2, 6, 10, 14), ia(2, 6, 10, 14), range(1, 15, 2)),
        bass = arrayOf(
            ia(0, 0, 8, 8, 7, 8),
            ia(0, 0, 3, 3, 0, 3, 6, 7, 2, 8, 0, 3, 11, 0, 3, 14, 7, 2),
            ia(0, 0, 2, 2, 0, 2, 4, 12, 2, 6, 0, 2, 8, 7, 2, 10, 0, 2, 12, 12, 2, 14, 7, 2),
            ia(0, 0, 2, 3, 0, 2, 6, 7, 2, 8, 0, 2, 10, 12, 2, 12, 7, 2, 14, 5, 2)
        ),
        chord = arrayOf(
            ia(0, 16), ia(0, 6, 6, 4, 10, 6), ia(0, 3, 3, 3, 6, 4, 10, 3, 12, 4),
            ia(0, 2, 2, 2, 4, 2, 6, 2, 8, 2, 10, 2, 12, 2, 14, 2)
        ),
        arp = arrayOf(NONE, ia(0, 4, 8, 12), range(0, 14, 2), range(0, 14, 2)),
        brass = arrayOf(NONE, NONE, ia(0, 3), ia(0, 3, 6, 2, 10, 2))
    )

    private val ethioFeel = Feel(
        steps = 12, beatSteps = 2,
        kick = arrayOf(ia(0, 6), ia(0, 6, 9), ia(0, 4, 6, 9), ia(0, 3, 6, 9, 10)),
        snare = arrayOf(ia(6), ia(6), ia(6, 9), ia(3, 6, 9)),
        hat = arrayOf(range(0, 10, 2), range(0, 10, 2), range(0, 11), range(0, 11)),
        perc = arrayOf(NONE, ia(3, 9), ia(1, 3, 4, 7, 9, 10), ia(1, 3, 4, 7, 9, 10)),
        bass = arrayOf(
            ia(0, 0, 6, 6, 7, 6),
            ia(0, 0, 3, 3, 7, 3, 6, 0, 3, 9, 5, 3),
            ia(0, 0, 2, 2, 7, 2, 4, 12, 2, 6, 0, 2, 8, 7, 2, 10, 5, 2),
            ia(0, 0, 2, 2, 7, 2, 4, 12, 2, 6, 0, 2, 7, 7, 1, 8, 7, 2, 10, 5, 2)
        ),
        chord = arrayOf(ia(0, 12), ia(0, 2, 6, 2), ia(0, 2, 4, 2, 6, 2, 10, 2), range(0, 10, 2).flatMap { listOf(it, 2) }.toIntArray()),
        arp = arrayOf(NONE, range(0, 10, 2), range(0, 10, 2), range(0, 11)),
        brass = arrayOf(NONE, NONE, ia(0, 2, 6, 2), ia(0, 2, 3, 2, 6, 2, 9, 2)),
        hatNote = 42, percNote = 60
    )

    private val eskistaFeel = Feel(
        steps = 12, beatSteps = 2,
        kick = arrayOf(ia(0, 6), ia(0, 3, 6, 9), ia(0, 3, 6, 9), ia(0, 2, 3, 6, 8, 9)),
        snare = arrayOf(NONE, NONE, ia(3, 9), ia(3, 9, 11)),
        hat = arrayOf(range(0, 10, 2), range(0, 10, 2), range(0, 11), range(0, 11)),
        perc = arrayOf(ia(0, 6), ia(0, 3, 6, 9), ia(0, 1, 3, 4, 6, 7, 9, 10), ia(0, 1, 3, 4, 6, 7, 9, 10)),
        bass = ethioFeel.bass, chord = ethioFeel.chord, arp = ethioFeel.arp, brass = ethioFeel.brass,
        hatNote = 42, percNote = 62
    )

    private val danceFeel = Feel(
        steps = 16, beatSteps = 4,
        kick = arrayOf(ia(0, 4, 8, 12), ia(0, 4, 8, 12), ia(0, 4, 8, 12), ia(0, 4, 8, 12)),
        snare = arrayOf(NONE, ia(4, 12), ia(4, 12), ia(4, 12)),
        hat = arrayOf(ia(2, 6, 10, 14), ia(2, 6, 10, 14), range(0, 14, 2), range(0, 15)),
        perc = arrayOf(NONE, ia(4, 12), ia(4, 12), ia(4, 12, 15)),
        bass = arrayOf(
            ia(0, 0, 8, 8, 0, 8),
            ia(2, 0, 2, 6, 0, 2, 10, 0, 2, 14, 0, 2),
            ia(0, 0, 2, 2, 12, 2, 4, 0, 2, 6, 12, 2, 8, 0, 2, 10, 12, 2, 12, 0, 2, 14, 12, 2),
            ia(0, 0, 2, 2, 0, 2, 4, 12, 2, 6, 0, 2, 8, 0, 2, 10, 12, 2, 12, 0, 2, 14, 7, 2)
        ),
        chord = arrayOf(
            ia(0, 16), ia(0, 2, 3, 2, 6, 2, 10, 2, 12, 4), ia(2, 2, 6, 2, 10, 2, 14, 2),
            ia(0, 2, 2, 2, 6, 2, 10, 2, 14, 2)
        ),
        arp = arrayOf(NONE, NONE, range(0, 14, 2), range(0, 15)),
        brass = arrayOf(NONE, NONE, NONE, ia(0, 2, 3, 2, 6, 2)),
        hatNote = 46, percNote = 39
    )

    private val worshipFeel = Feel(
        steps = 16, beatSteps = 4,
        kick = arrayOf(ia(0), ia(0, 10), ia(0, 8, 10), ia(0, 8, 10)),
        snare = arrayOf(NONE, ia(8), ia(4, 12), ia(4, 12)),
        hat = arrayOf(NONE, ia(0, 4, 8, 12), range(0, 14, 2), range(0, 14, 2)),
        perc = arrayOf(NONE, NONE, ia(2, 6, 10, 14), ia(2, 6, 10, 14)),
        bass = arrayOf(
            ia(0, 0, 16), ia(0, 0, 6, 8, 0, 8), ia(0, 0, 6, 8, 7, 4, 12, 0, 4),
            ia(0, 0, 4, 6, 0, 2, 8, 7, 4, 12, 0, 4)
        ),
        chord = arrayOf(ia(0, 16), ia(0, 8, 8, 8), ia(0, 6, 8, 6), ia(0, 6, 6, 4, 10, 6)),
        arp = arrayOf(NONE, ia(0, 4, 8, 12), range(0, 14, 2), range(0, 14, 2)),
        brass = arrayOf(NONE, NONE, NONE, ia(0, 8))
    )

    private const val ALL = 0xFF

    private fun main(f: Feel, level: Int, bar: Int, mask: Int): MutableList<Ev> {
        val l = level.coerceIn(0, 3)
        val e = ArrayList<Ev>()
        if (mask and (1 shl 0) != 0) {
            for (s in f.kick[l]) e.add(Ev(s, Track.RHYTHM1, 36, 1, if (s == 0) 118 else 100))
            for (s in f.snare[l]) e.add(Ev(s, Track.RHYTHM1, 38, 1, 105))
            for (s in f.hat[l]) e.add(Ev(s, Track.RHYTHM1, f.hatNote, 1, if (s % f.beatSteps == 0) 92 else 66))
        }
        if (mask and (1 shl 1) != 0) for (s in f.perc[l]) e.add(Ev(s, Track.RHYTHM2, f.percNote, 1, 72))
        if (mask and (1 shl 2) != 0) {
            val b = f.bass[l]
            var i = 0
            while (i + 2 < b.size) { e.add(Ev(b[i], Track.BASS, b[i + 1], b[i + 2], 100)); i += 3 }
        }
        if (mask and (1 shl 3) != 0) {
            val c = f.chord[l]
            var i = 0
            while (i + 1 < c.size) { e.add(Ev(c[i], Track.CHORD1, -1, c[i + 1], 82)); i += 2 }
        }
        if (mask and (1 shl 4) != 0) {
            val cycle = intArrayOf(0, 1, 2, 1)
            for ((k, s) in f.arp[l].withIndex()) e.add(Ev(s, Track.CHORD2, cycle[k % 4], 3, 70, 0))
        }
        if (mask and (1 shl 5) != 0) e.add(Ev(0, Track.PAD, -1, f.steps, 58))
        if (mask and (1 shl 6) != 0) {
            val b = f.brass[l]
            var i = 0
            while (i + 1 < b.size) { e.add(Ev(b[i], Track.PHRASE1, -1, b[i + 1], 88, 1)); i += 2 }
        }
        if (mask and (1 shl 7) != 0 && l == 3) {
            val s1 = if (bar % 2 == 0) f.steps - 4 else f.steps - 6
            e.add(Ev(s1, Track.PHRASE2, 2, 2, 80, 1))
            e.add(Ev(s1 + 2, Track.PHRASE2, 3, 2, 80, 1))
        }
        return e
    }

    private val TOMS = intArrayOf(50, 48, 47, 45, 43, 41)

    private fun fill(f: Feel, level: Int): MutableList<Ev> {
        val e = ArrayList<Ev>()
        e.add(Ev(0, Track.RHYTHM1, 36, 1, 118))
        e.add(Ev(0, Track.BASS, 0, 2, 100))
        e.add(Ev(0, Track.CHORD1, -1, 2, 85))
        var k = 0
        for (s in f.steps / 2 until f.steps) {
            val note = if (s == f.steps - 1) 38 else TOMS[k % TOMS.size]
            e.add(Ev(s, Track.RHYTHM1, note, 1, 85 + level * 5))
            k++
        }
        return e
    }

    private fun tomRoll(f: Feel): List<Ev> {
        val e = ArrayList<Ev>()
        for ((k, s) in (f.steps - 4 until f.steps).withIndex()) e.add(Ev(s, Track.RHYTHM1, TOMS[k], 1, 90))
        return e
    }

    private fun breakBar(f: Feel): List<Ev> = listOf(
        Ev(0, Track.RHYTHM1, 36, 1, 112), Ev(0, Track.RHYTHM1, 49, 1, 100),
        Ev(0, Track.CHORD1, -1, 3, 90), Ev(0, Track.PAD, -1, f.steps, 55)
    )

    private fun hit(f: Feel): List<Ev> = listOf(
        Ev(0, Track.RHYTHM1, 36, 1, 120), Ev(0, Track.RHYTHM1, 49, 1, 105),
        Ev(0, Track.BASS, 0, f.steps - 2, 100), Ev(0, Track.CHORD1, -1, f.steps - 1, 95),
        Ev(0, Track.PAD, -1, f.steps - 1, 80), Ev(0, Track.PHRASE1, -1, 6, 95, 1)
    )

    private fun gen(f: Feel, s: Sec, bar: Int): List<Ev> {
        val last = bar == s.bars - 1
        return when {
            s.isMain -> main(f, s.ordinal - Sec.MAIN_A.ordinal, bar, ALL)
            s.isFill -> fill(f, s.ordinal - Sec.FILL_A.ordinal)
            s == Sec.BREAK -> breakBar(f)
            s.isIntro -> {
                val n = s.ordinal - Sec.INTRO_1.ordinal
                val mask = when (n) { 0 -> 0b11; 1 -> 0b111111; else -> 0b100100 }
                val l = main(f, 0, bar, mask)
                if (last && n < 2) l.addAll(tomRoll(f))
                l
            }
            else -> {
                val n = s.ordinal - Sec.END_1.ordinal
                if (last) hit(f) else main(f, if (n == 0) 1 else 0, bar, if (n == 2) 0b100100 else 0b111111)
            }
        }
    }

    private fun build(
        f: Feel, name: String, category: String, tempo: Int, sig: String,
        voices: List<String>, ots: List<List<String>>
    ): Style {
        val secs = Array(Sec.values().size) { si ->
            val s = Sec.values()[si]
            Array(s.bars) { b ->
                val ev = gen(f, s, b)
                Array(f.steps) { step -> ev.filter { it.step == step }.toTypedArray() }
            }
        }
        return Style(name, category, tempo, sig, f.steps, f.beatSteps, voices, ots, secs)
    }

    private val builtin: List<Style> by lazy { buildBuiltin() }
    @Volatile private var merged: List<Style>? = null

    /** Built-in styles followed by imported ones. Replaced as a whole when a style is added (safe to read from any thread). */
    val all: List<Style> get() = merged ?: builtin

    /** Adds an imported style and returns its index. */
    @Synchronized fun addImported(st: Style): Int {
        val n = (merged ?: builtin) + st
        merged = n
        return n.size - 1
    }

    private fun buildBuiltin(): List<Style> {
        return listOf(
            build(
                popFeel, "Pop Rock 8Beat", "Pop", 110, "4/4",
                listOf("bass", "epiano", "guitar", "strings", "brass", "flute"),
                listOf(listOf("grand", "", ""), listOf("epiano", "strings", ""), listOf("grand", "strings", "pad"), listOf("organ", "", ""))
            ),
            build(
                ethioFeel, "Ethio Jazz", "Ethio & World", 96, "6/8",
                listOf("bass", "epiano", "krar", "strings", "masinko", "washint"),
                listOf(listOf("grand", "", ""), listOf("epiano", "krar", ""), listOf("masinko", "strings", ""), listOf("washint", "pad", ""))
            ),
            build(
                eskistaFeel, "Eskista Groove", "Ethio & World", 118, "6/8",
                listOf("bass", "organ", "krar", "strings", "masinko", "washint"),
                listOf(listOf("krar", "", ""), listOf("masinko", "strings", ""), listOf("grand", "krar", ""), listOf("washint", "", ""))
            ),
            build(
                danceFeel, "Dance Pop", "Dance", 126, "4/4",
                listOf("bass", "lead", "guitar", "pad", "brass", "lead"),
                listOf(listOf("lead", "", ""), listOf("grand", "pad", ""), listOf("brass", "", ""), listOf("organ", "lead", ""))
            ),
            build(
                worshipFeel, "Worship Ballad", "Worship", 72, "4/4",
                listOf("bass", "epiano", "guitar", "strings", "strings", "flute"),
                listOf(listOf("grand", "", ""), listOf("grand", "strings", ""), listOf("epiano", "pad", ""), listOf("strings", "grand", ""))
            )
        )
    }
}
