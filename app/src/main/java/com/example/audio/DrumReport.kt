package com.example.audio

/**
 * Import-time drum report: which drum notes a style uses and how many have no sound in the engine.
 * Pure Kotlin so it is unit tested. Nothing is hidden: unmapped notes are listed with their hit counts.
 */
class DrumReport(
    val totalHits: Int,
    val silentHits: Int,
    /** note -> hits, only notes that make no sound, sorted by hits (most used first). */
    val silentNotes: List<Pair<Int, Int>>,
    /** Kit program found on the drum channel (-1 = none found). */
    val kitProgram: Int
) {
    val kitId: Int get() = DrumKit.resolve(kitProgram)
    val kitKnown: Boolean get() = DrumKit.fromProgram(kitProgram) >= 0

    fun lines(): List<String> {
        val out = ArrayList<String>()
        if (totalHits == 0) { out.add("Drums: no drum notes"); return out }
        val kit = when {
            kitProgram < 0 -> "no kit program in file: Standard drums used"
            kitKnown -> "kit program " + kitProgram + " = " + DrumKit.NAMES[kitId] + " (synthesized tuning, not the original kit)"
            else -> "kit program " + kitProgram + " is product-specific and unknown here: Standard drums used"
        }
        out.add("Drums: $totalHits hits, $kit")
        if (silentHits > 0) {
            val pct = Math.round(silentHits * 1000.0 / totalHits) / 10.0
            val top = silentNotes.take(6).joinToString(", ") { "note " + it.first + " x" + it.second }
            out.add("Drums: $silentHits hits ($pct%) use notes with no sound in this engine: $top")
            out.add("Those notes belong to the original keyboard's drum kit, whose samples are not available here.")
        } else {
            out.add("Drums: every drum note has a sound")
        }
        return out
    }

    companion object {
        fun build(notes: List<Int>, kitProgram: Int): DrumReport {
            val counts = IntArray(128)
            var total = 0
            for (n in notes) { if (n in 0..127) { counts[n]++; total++ } }
            var silent = 0
            val list = ArrayList<Pair<Int, Int>>()
            for (n in 0..127) if (counts[n] > 0 && DrumMap.kindOf(n) == DrumMap.SILENT) { silent += counts[n]; list.add(n to counts[n]) }
            list.sortByDescending { it.second }
            return DrumReport(total, silent, list, kitProgram)
        }
    }
}
