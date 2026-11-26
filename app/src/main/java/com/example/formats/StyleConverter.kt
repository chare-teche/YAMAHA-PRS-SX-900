package com.example.formats

import com.example.audio.DrumReport
import com.example.audio.Ev
import com.example.audio.Sec
import com.example.audio.Style
import com.example.audio.Track
import com.example.audio.Voices

/** Turns parsed Yamaha style data into the engine's [Style]. The synth engine itself is not touched. */
object StyleConverter {
    /** Steps per quarter note used for imported styles (24 = 16ths, 8th triplets and 16th triplets all land on steps). */
    const val STEPS_PER_QUARTER = 24
    private const val MAX_BARS = 64

    /** Yamaha section marker -> engine section. Intro D / Ending D have no button and are skipped. */
    fun sectionFor(name: String): Sec? = when (name) {
        "Intro A" -> Sec.INTRO_1
        "Intro B" -> Sec.INTRO_2
        "Intro C" -> Sec.INTRO_3
        "Main A" -> Sec.MAIN_A
        "Main B" -> Sec.MAIN_B
        "Main C" -> Sec.MAIN_C
        "Main D" -> Sec.MAIN_D
        "Fill In AA" -> Sec.FILL_A
        "Fill In BB" -> Sec.FILL_B
        "Fill In CC" -> Sec.FILL_C
        "Fill In DD" -> Sec.FILL_D
        "Fill In BA" -> Sec.BREAK
        "Ending A" -> Sec.END_1
        "Ending B" -> Sec.END_2
        "Ending C" -> Sec.END_3
        else -> null
    }

    /** General MIDI program -> one of the app's voices. */
    fun voiceForProgram(p: Int): String = when (p) {
        in 0..1 -> "grand"
        in 2..5 -> "epiano"
        in 6..7 -> "grand"
        in 8..15 -> "epiano"
        in 16..23 -> "organ"
        in 24..31 -> "guitar"
        in 32..39 -> "bass"
        in 40..44 -> "strings"
        45 -> "guitar"
        46 -> "krar"
        47 -> "strings"
        in 48..51 -> "strings"
        in 52..54 -> "pad"
        55 -> "brass"
        in 56..63 -> "brass"
        in 64..67 -> "brass"
        in 68..71 -> "flute"
        in 72..79 -> "flute"
        in 80..87 -> "lead"
        in 88..103 -> "pad"
        in 104..108 -> "krar"
        109 -> "washint"
        110 -> "masinko"
        111 -> "washint"
        in 112..119 -> "epiano"
        else -> "pad"
    }

    private val DEFAULT_VOICES = listOf("bass", "epiano", "guitar", "strings", "brass", "flute")

    fun nameFromFile(file: String): String {
        var n = file.substringAfterLast('/').substringBeforeLast('.')
        n = n.replace('_', ' ').trim()
        // "REGGAE 1 S362" -> drop trailing catalogue number like S362
        val parts = n.split(' ').toMutableList()
        if (parts.size > 1 && Regex("^[Ss]\\d{2,4}$").matches(parts.last())) parts.removeAt(parts.size - 1)
        n = parts.joinToString(" ").trim()
        return if (n.isEmpty()) "Imported Style" else n
    }

    fun convert(d: StyData, fileName: String): Style {
        val ppq = d.ppq.toDouble()
        val tpb = d.ticksPerBar
        val spb = Math.round(tpb * STEPS_PER_QUARTER / ppq).toInt().coerceAtLeast(1)
        val beatSteps = when {
            d.tsDen == 8 && d.tsNum % 3 == 0 -> 36          // compound time: dotted-quarter beat
            else -> (STEPS_PER_QUARTER * 4 / d.tsDen).coerceAtLeast(1)
        }
        val secs = Sec.values()
        val bySec = arrayOfNulls<StySection>(secs.size)
        for (s in d.sections) {
            val e = sectionFor(s.name) ?: continue
            if (bySec[e.ordinal] == null) bySec[e.ordinal] = s
        }

        // per-track voice + level, taken from the first Main section (fallback: any section)
        val order = listOf("Main A", "Main B", "Main C", "Main D") + d.sections.map { it.name }
        val trackVoice = Array(8) { "" }
        val trackLevel = DoubleArray(8) { 1.0 }
        for (name in order) {
            val sec = d.sections.firstOrNull { it.name == name } ?: continue
            for (c in sec.casm) {
                val t = c.dest - 8
                if (t !in 0..7 || trackVoice[t].isNotEmpty()) continue
                val prog = if (sec.program[c.src] >= 0) sec.program[c.src] else d.setupProgram[c.src]
                if (t >= 2) trackVoice[t] = if (prog >= 0) voiceForProgram(prog) else DEFAULT_VOICES[t - 2]
                else trackVoice[t] = "drums"
                val vol = if (sec.volume[c.src] >= 0) sec.volume[c.src] else d.setupVolume[c.src]
                if (vol >= 0) trackLevel[t] = (vol / 127.0).coerceIn(0.2, 1.0)
            }
        }
        // drum kit program: first rhythm rule (RHY1 before RHY2) of the first Main section that has one
        var kitProgram = -1
        for (name in order) {
            val sec = d.sections.firstOrNull { it.name == name } ?: continue
            val rule = sec.casm.filter { it.dest == 8 }.firstOrNull() ?: sec.casm.filter { it.dest == 9 }.firstOrNull() ?: continue
            val prog = if (sec.program[rule.src] >= 0) sec.program[rule.src] else d.setupProgram[rule.src]
            if (prog >= 0) { kitProgram = prog; break }
        }
        val drumNotes = ArrayList<Int>()
        val voices = List(6) { i -> if (trackVoice[i + 2].isNotEmpty()) trackVoice[i + 2] else DEFAULT_VOICES[i] }

        // ---- build sections ----
        val empty = emptyArray<Ev>()
        val built = arrayOfNulls<Array<Array<Array<Ev>>>>(secs.size)
        val notesPerSec = IntArray(secs.size)
        for (si in secs.indices) {
            val src = bySec[si] ?: continue
            val bars = Math.round((src.endTick - src.startTick).toDouble() / tpb).toInt().coerceIn(1, MAX_BARS)
            val grid = Array(bars) { arrayOfNulls<ArrayList<Ev>>(spb) }
            val rules = HashMap<Int, CasmEntry>()
            for (c in src.casm) if (!rules.containsKey(c.src)) rules[c.src] = c
            var count = 0
            for (n in src.notes) {
                val c = rules[n.ch] ?: continue
                val t = c.dest - 8
                if (t !in 0..7) continue
                if (t <= 1) drumNotes.add(n.note)
                var step = Math.round(n.tick * STEPS_PER_QUARTER / ppq).toInt()
                val total = bars * spb
                if (step >= total) step %= total
                val bar = step / spb
                val sib = step % spb
                val len = Math.round(n.len * STEPS_PER_QUARTER / ppq).toInt().coerceAtLeast(1)
                val vel = (n.vel * trackLevel[t]).toInt().coerceIn(1, 127)
                val ev = Ev(
                    step = sib, track = t, note = n.note, len = len, vel = vel, oct = 0,
                    abs = true, srcRoot = c.srcRoot, ntt = c.ntt, highKey = c.highKey, lo = c.lo, hi = c.hi
                )
                var l = grid[bar][sib]
                if (l == null) { l = ArrayList<Ev>(2); grid[bar][sib] = l }
                l.add(ev)
                count++
            }
            notesPerSec[si] = count
            built[si] = Array(bars) { b -> Array(spb) { s -> grid[b][s]?.toTypedArray() ?: empty } }
        }

        // ---- fall-backs so that every button plays something ----
        val warnings = ArrayList<String>(d.warnings)
        fun firstBar(sec: Sec): Array<Array<Array<Ev>>>? = built[sec.ordinal]?.let { arrayOf(it[0]) }
        fun lastBar(sec: Sec): Array<Array<Array<Ev>>>? = built[sec.ordinal]?.let { arrayOf(it[it.size - 1]) }
        val mains = listOf(Sec.MAIN_A, Sec.MAIN_B, Sec.MAIN_C, Sec.MAIN_D)
        for ((i, m) in mains.withIndex()) {
            if (built[m.ordinal] != null) continue
            var from: Sec? = null
            for (k in i - 1 downTo 0) if (built[mains[k].ordinal] != null) { from = mains[k]; break }
            if (from == null) for (k in i + 1..3) if (built[mains[k].ordinal] != null) { from = mains[k]; break }
            if (from != null) { built[m.ordinal] = built[from.ordinal]; warnings.add(m.label + " not in file: uses " + from.label) }
        }
        if (mains.none { built[it.ordinal] != null }) throw FormatException("Style has no Main sections with notes")
        val anyMain = mains.first { built[it.ordinal] != null }
        val fills = listOf(Sec.FILL_A, Sec.FILL_B, Sec.FILL_C, Sec.FILL_D)
        for ((i, f) in fills.withIndex()) {
            if (built[f.ordinal] == null) { built[f.ordinal] = lastBar(mains[i]) ?: lastBar(anyMain) }
        }
        for (i in listOf(Sec.INTRO_1, Sec.INTRO_2, Sec.INTRO_3, Sec.END_1, Sec.END_2, Sec.END_3)) {
            if (built[i.ordinal] == null) built[i.ordinal] = firstBar(anyMain)
        }
        if (built[Sec.BREAK.ordinal] == null) built[Sec.BREAK.ordinal] = Array(1) { Array(spb) { empty } }

        val sections = Array(secs.size) { built[it] ?: Array(1) { Array(spb) { empty } } }

        // ---- One Touch Settings ----
        val ots = ArrayList<List<String>>()
        for (k in 0 until 4) {
            val p = d.ots.getOrNull(k)
            if (p == null) ots.add(listOf(voices[1], "", ""))
            else {
                val r1 = if (p[0] >= 0) voiceForProgram(p[0]) else "grand"
                val r2 = if (p[1] > 0) voiceForProgram(p[1]) else ""
                val r3 = if (p[2] > 0) voiceForProgram(p[2]) else ""
                ots.add(listOf(r1, r2, r3))
            }
        }

        val sb = StringBuilder()
        sb.append(d.format).append(" style, ").append(d.tempoBpm).append(" BPM, ").append(d.tsNum).append('/').append(d.tsDen).append('\n')
        for (si in secs.indices) {
            if (bySec[si] != null) {
                sb.append(secs[si].label).append(": ").append(built[si]!!.size).append(" bar(s), ").append(notesPerSec[si]).append(" notes\n")
            }
        }
        val drumReport = DrumReport.build(drumNotes, kitProgram)
        for (l in drumReport.lines()) sb.append(l).append('\n')
        sb.append(if (d.hasCasm) "CASM channel rules read\n" else "No CASM\n")
        sb.append(d.ots.size).append(" One Touch Setting(s)\n")
        for (w in warnings) sb.append("Note: ").append(w).append('\n')

        return Style(
            name = nameFromFile(fileName), category = "User", tempo = d.tempoBpm,
            timeSig = d.tsNum.toString() + "/" + d.tsDen,
            stepsPerBar = spb, beatSteps = beatSteps,
            trackVoices = voices, ots = ots, sections = sections,
            clock = STEPS_PER_QUARTER, source = fileName, info = sb.toString(),
            drumKit = drumReport.kitId
        )
    }

    /** Parse + convert in one call. Throws [FormatException] with a user-readable message on failure. */
    fun fromBytes(bytes: ByteArray, fileName: String): Style = convert(StyParser.parse(bytes), fileName)
}
