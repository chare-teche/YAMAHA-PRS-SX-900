package com.example.audio

import kotlin.math.pow

/**
 * Drum KIT selection for styles. Pure Kotlin (no Android) so it can be unit tested.
 *
 * Yamaha style files select the drum kit of a rhythm channel with a Program Change on a drum bank
 * (Bank MSB 127 = drum kits, 126 = SFX kits). Before this class the converter dropped that information, so
 * every style used the same generic kit.
 *
 * IMPORTANT (honesty): the engine has no recorded drum samples. A kit here is a set of per-sound tuning
 * factors applied to the synthesized drums (pitch and decay). It makes an Electro, Jazz or Room style sound
 * different from a Standard one, but it is NOT the original Yamaha kit. Only the XG/GM2 program numbers that
 * are standardised are recognised; product-specific kit numbers (for example 80, 87, 88) are reported as
 * unknown and use the Standard kit.
 */
object DrumKit {
    const val STANDARD = 0
    const val ROOM = 1
    const val ROCK = 2
    const val ELECTRO = 3
    const val ANALOG = 4
    const val JAZZ = 5
    const val BRUSH = 6
    const val CLASSIC = 7
    const val COUNT = 8

    val NAMES = arrayOf("Standard", "Room", "Rock", "Electro", "Analog", "Jazz", "Brush", "Classic")

    /** Program number (XG drum kit list) -> kit id, or -1 when the number is not a standard kit. */
    fun fromProgram(program: Int): Int = when (program) {
        0, 1 -> STANDARD
        8 -> ROOM
        16 -> ROCK
        24 -> ELECTRO
        25 -> ANALOG
        32 -> JAZZ
        40 -> BRUSH
        48 -> CLASSIC
        else -> -1
    }

    /** Kit actually used for a program (unknown numbers use Standard). */
    fun resolve(program: Int): Int = fromProgram(program).let { if (it < 0) STANDARD else it }

    // [kit * DrumMap kinds + kind] -> factor. Filled once, read on the audio thread without allocation.
    private const val KINDS = 16
    private val pitch = FloatArray(COUNT * KINDS) { 1f }
    private val decay = FloatArray(COUNT * KINDS) { 1f }

    private fun p(kit: Int, kind: Int, f: Float) { pitch[kit * KINDS + kind] = f }
    private fun d(kit: Int, kind: Int, f: Float) { decay[kit * KINDS + kind] = f }

    init {
        // Room: bigger, longer tails
        d(ROOM, DrumMap.KICK, 1.15f); d(ROOM, DrumMap.SNARE, 1.3f); d(ROOM, DrumMap.TOM, 1.3f); d(ROOM, DrumMap.CRASH, 1.1f)
        // Rock: fuller kick and snare
        d(ROCK, DrumMap.KICK, 1.2f); p(ROCK, DrumMap.SNARE, 0.95f); d(ROCK, DrumMap.SNARE, 1.2f); d(ROCK, DrumMap.TOM, 1.2f)
        // Electro: tight, higher snare, short hats
        p(ELECTRO, DrumMap.KICK, 0.85f); d(ELECTRO, DrumMap.KICK, 1.1f)
        p(ELECTRO, DrumMap.SNARE, 1.2f); d(ELECTRO, DrumMap.SNARE, 0.8f)
        d(ELECTRO, DrumMap.HAT_CLOSED, 0.8f); d(ELECTRO, DrumMap.HAT_OPEN, 0.8f)
        p(ELECTRO, DrumMap.TOM, 1.2f); d(ELECTRO, DrumMap.TOM, 0.8f)
        // Analog: deep boomy kick, short snare and hats, longer clap
        p(ANALOG, DrumMap.KICK, 0.75f); d(ANALOG, DrumMap.KICK, 1.4f)
        p(ANALOG, DrumMap.SNARE, 1.1f); d(ANALOG, DrumMap.SNARE, 0.9f)
        d(ANALOG, DrumMap.HAT_CLOSED, 0.7f); d(ANALOG, DrumMap.HAT_OPEN, 0.7f); d(ANALOG, DrumMap.CLAP, 1.1f)
        p(ANALOG, DrumMap.TOM, 0.9f); d(ANALOG, DrumMap.TOM, 1.2f)
        // Jazz: small, dry kick and snare, longer ride
        p(JAZZ, DrumMap.KICK, 1.1f); d(JAZZ, DrumMap.KICK, 0.8f)
        p(JAZZ, DrumMap.SNARE, 1.05f); d(JAZZ, DrumMap.SNARE, 0.8f); d(JAZZ, DrumMap.RIDE, 1.2f)
        // Brush: soft short kick, long swishy snare, longer hats
        d(BRUSH, DrumMap.KICK, 0.7f); d(BRUSH, DrumMap.SNARE, 1.4f)
        d(BRUSH, DrumMap.HAT_CLOSED, 1.3f); d(BRUSH, DrumMap.HAT_OPEN, 1.2f)
        // Classic (orchestral): big low kick, long cymbals
        p(CLASSIC, DrumMap.KICK, 0.9f); d(CLASSIC, DrumMap.KICK, 1.4f); d(CLASSIC, DrumMap.SNARE, 1.1f); d(CLASSIC, DrumMap.CRASH, 1.2f)
    }

    fun pitchFactor(kit: Int, kind: Int): Float =
        if (kit in 0 until COUNT && kind in 0 until KINDS) pitch[kit * KINDS + kind] else 1f

    fun decayFactor(kit: Int, kind: Int): Float =
        if (kit in 0 until COUNT && kind in 0 until KINDS) decay[kit * KINDS + kind] else 1f

    /** Envelope coefficient for a time constant stretched by [decayFactor]: c^(1/f). */
    fun stretchCoef(coef: Float, factor: Float): Float = if (factor == 1f) coef else coef.pow(1f / factor)
}
