package com.example.audio

/**
 * Drum note -> synthesized drum kind. Pure Kotlin (no Android) so it can be unit tested.
 *
 * Notes that were already mapped before (GM core kit) keep exactly the same kind and pitch, so built-in styles
 * sound as before. Notes that used to fall through to a 600 Hz "blip" now get a proper percussion sound,
 * and notes that are not sounds at all (FX, scratches, out of range) are silent.
 */
object DrumMap {
    const val SILENT = -1
    const val KICK = 0
    const val SNARE = 1
    const val CLAP = 2
    const val HAT_CLOSED = 3
    const val HAT_OPEN = 4
    const val TOM = 5
    const val CRASH = 6
    const val RIDE = 7
    const val BLIP = 8        // short pitched percussion (cowbell, bongo, conga, agogo...)
    const val TAMB = 9        // tambourine / maracas
    const val STICK = 10      // side stick
    const val SHAKER = 11     // shaker, cabasa, guiro, vibraslap (soft noise)
    const val RING = 12       // triangle, bell tree (long pitched ring)
    const val WOOD = 13       // claves, wood block, castanets, clicks (very short pitched)
    const val COWBELL = 14    // two inharmonic square-ish tones (540 + 800 Hz) with a click, like a real cowbell
    const val CONGA = 15      // bongos and congas: low pitched skin with a pitch drop and a slap

    private val kinds = IntArray(128) { SILENT }
    private val freqs = FloatArray(128)

    private fun set(note: Int, kind: Int, freq: Float = 600f) { kinds[note] = kind; freqs[note] = freq }

    init {
        // ---- unchanged from the original engine ----
        set(35, KICK); set(36, KICK)
        set(38, SNARE); set(40, SNARE)
        set(37, STICK)
        set(39, CLAP)
        set(42, HAT_CLOSED); set(44, HAT_CLOSED)
        set(46, HAT_OPEN)
        set(41, TOM, 80f); set(43, TOM, 100f); set(45, TOM, 125f)
        set(47, TOM, 150f); set(48, TOM, 180f); set(50, TOM, 210f)
        set(49, CRASH); set(57, CRASH)
        set(51, RIDE); set(53, RIDE); set(59, RIDE)
        set(54, TAMB); set(70, TAMB)
        // ---- changed on request: cowbell and congas used to be the same plain sine blip (thick, wrong octave) ----
        set(56, COWBELL, 540f)
        set(60, CONGA, 420f); set(61, CONGA, 320f)           // high / low bongo
        set(62, CONGA, 310f); set(63, CONGA, 310f); set(64, CONGA, 230f)  // mute / open high conga, low conga
        // ---- new: previously fell to the 600 Hz blip ----
        set(52, CRASH); set(55, CRASH)                       // Chinese cymbal, splash
        set(58, SHAKER)                                      // vibraslap
        set(65, BLIP, 540f); set(66, BLIP, 410f)             // high / low timbale
        set(67, BLIP, 900f); set(68, BLIP, 680f)             // high / low agogo
        set(69, SHAKER)                                      // cabasa
        set(71, BLIP, 2400f); set(72, RING, 2400f)           // short / long whistle
        set(73, SHAKER); set(74, SHAKER)                     // short / long guiro
        set(75, WOOD, 2400f)                                 // claves
        set(76, WOOD, 1500f); set(77, WOOD, 1000f)           // high / low wood block
        set(78, BLIP, 380f); set(79, BLIP, 300f)             // mute / open cuica
        set(80, WOOD, 3200f); set(81, RING, 3200f)           // mute / open triangle
        set(82, SHAKER)                                      // shaker
        set(83, WOOD, 3800f)                                 // jingle bell
        set(84, RING, 3000f)                                 // bell tree
        set(85, WOOD, 1800f)                                 // castanets
        set(86, TOM, 65f); set(87, TOM, 55f)                 // mute / open surdo
        set(28, STICK)                                       // slap
        set(31, WOOD, 1800f); set(32, WOOD, 1400f)           // sticks, square click
        set(33, WOOD, 1600f); set(34, WOOD, 2600f)           // metronome click / bell
        // everything else (0-27, 29, 30, 88-127) stays SILENT
    }

    /**
     * Crash-family cymbals (crash 1/2, Chinese, splash) are switched off on purpose (version 3, requested by the user:
     * they were harsh). They make no sound and are not counted as missing sounds. To bring them back, return false.
     */
    fun isMuted(note: Int): Boolean = note == 49 || note == 57 || note == 52 || note == 55

    fun kindOf(note: Int): Int = if (note in 0..127) kinds[note] else SILENT
    fun freqOf(note: Int): Float = if (note in 0..127) freqs[note] else 600f

    /** Maximum sounding time (seconds) of a drum voice. After that it fades out in a few ms. Frees voices early. */
    fun lifeSeconds(kind: Int): Float = when (kind) {
        KICK -> 1.2f
        SNARE -> 0.8f
        CLAP -> 0.6f
        HAT_CLOSED -> 0.2f
        HAT_OPEN -> 1.0f
        TOM -> 1.4f
        CRASH -> 3.0f
        RIDE -> 2.2f
        BLIP -> 0.6f
        TAMB -> 0.45f
        STICK -> 0.3f
        SHAKER -> 0.3f
        RING -> 1.5f
        WOOD -> 0.25f
        COWBELL -> 0.5f
        CONGA -> 0.6f
        else -> 0.5f
    }

    /** Short hand-drum strokes: bongos and the muted conga ring less than the open conga strokes. */
    fun isShortStroke(note: Int): Boolean = note == 60 || note == 61 || note == 62

    /** Kinds that are built from noise and therefore get a short attack ramp (no step edge on the first sample). */
    fun isNoiseKind(kind: Int): Boolean =
        kind == CLAP || kind == HAT_CLOSED || kind == HAT_OPEN || kind == CRASH || kind == RIDE || kind == TAMB || kind == SHAKER
}
