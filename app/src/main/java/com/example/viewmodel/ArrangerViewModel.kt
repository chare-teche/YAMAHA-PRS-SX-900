package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import com.example.formats.FileInspector
import com.example.formats.FormatException
import com.example.formats.Kind
import com.example.formats.Library
import com.example.formats.MusicStudioStyle
import com.example.formats.StyleConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.Chords
import com.example.audio.Cmd
import com.example.audio.DrumSampleSet
import com.example.audio.MusicStudioKit
import com.example.audio.Op
import com.example.audio.Sec
import com.example.audio.Styles
import com.example.audio.SynthEngine
import com.example.audio.Track
import com.example.audio.Voices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { VOICE, STYLE, MIXER, PADS, REGIST, SETTINGS, USER }

enum class KeyLayout(val label: String, val lo: Int, val hi: Int) {
    K49("49 keys", 36, 84), K61("61 keys", 36, 96), K76("76 keys", 28, 103), K88("88 keys", 21, 108)
}

/** Parts in display order: RIGHT 1, RIGHT 2, RIGHT 3, LEFT. */
/** One row of the USER browser. */
data class UserEntry(val name: String, val docId: String, val isDir: Boolean, val kind: Kind)

/** State of the USER (file browser) page. level 0 = list of added folders. */
data class UserState(
    val roots: List<String> = emptyList(),       // tree uris of folders the user added
    val tree: String = "",                       // current tree uri ("" = top level)
    val docStack: List<String> = emptyList(),    // document ids from the tree root to the current folder
    val titles: List<String> = emptyList(),
    val entries: List<UserEntry> = emptyList(),
    val busy: Boolean = false,
    val message: String = "Add a folder (ADD FOLDER) to browse your styles."
)

data class PartUi(
    val name: String,
    val enabled: Boolean,
    val voiceIdx: Int,
    val volume: Float = 0.9f,
    val pan: Float = 0f,
    val octave: Int = 0
)

data class UiState(
    val screen: Screen? = null,
    val parts: List<PartUi> = listOf(
        PartUi("RIGHT 1", true, 0),
        PartUi("RIGHT 2", false, 3),
        PartUi("RIGHT 3", false, 6),
        PartUi("LEFT", false, 3)
    ),
    val selectedPart: Int = 0,
    val styleIdx: Int = 0,
    val tempo: Int = 110,
    val timeSig: String = "4/4",
    val running: Boolean = false,
    val playingSection: Int = Sec.MAIN_A.ordinal,
    val targetMain: Int = Sec.MAIN_A.ordinal,
    val armedIntro: Int = -1,
    val measure: Int = 1,
    val beat: Int = 1,
    val chordText: String = "C Maj",
    /** User chord mode: 0 = automatic, 1/2/3 = the chord changes only when that many keys are held. */
    val chordKeys: Int = 0,
    val kitName: String = "",
    val acmp: Boolean = true,
    val autoFill: Boolean = true,
    val syncStart: Boolean = false,
    val syncStop: Boolean = false,
    val fadeActive: Boolean = false,
    val sustain: Boolean = false,
    val pitchBend: Float = 0f,
    val modulation: Float = 0f,
    val transpose: Int = 0,
    val masterVol: Float = 0.8f,
    val styleVol: Float = 0.9f,
    val reverb: Float = 0.18f,
    val tone: Int = 1,                  // master tone: 0 FLAT, 1 CLEAR, 2 WARM
    val trackVol: List<Float> = List(8) { 0.85f },
    val trackMute: List<Boolean> = List(8) { false },
    val pressed: Set<Int> = emptySet(),
    val splitNote: Int = 59,
    val panelLock: Boolean = false,
    val memoryArmed: Boolean = false,
    val regs: List<String> = List(8) { "" },
    val activeReg: Int = -1,
    val activeOts: Int = -1,
    val banner: String = "Ready",
    val audioOk: Boolean = false,
    val underruns: Int = 0,
    val voices: Int = 0,
    val peak: Float = 0f,
    val user: UserState = UserState(),
    // keyboard settings
    val keyLayout: KeyLayout = KeyLayout.K61,
    val keyHeight: Float = 0.51f,       // share of the screen height used by the keyboard (the control deck gets the rest)
    val visibleWhites: Int = 15,
    val startIdx: Int = 7,              // first visible white key (index in the layout)
    val blackWidth: Float = 0.60f,      // black key width relative to a white key
    val blackHeight: Float = 0.62f,     // black key length relative to a white key
    val showLabels: Boolean = true,
    val fixedVelocity: Boolean = false,
    val bufferFrames: Int = 256
)

class ArrangerViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        val PART_CH = intArrayOf(6, 7, 8, 10)  // RIGHT1, RIGHT2, RIGHT3, LEFT
    }

    val engine = SynthEngine()
    private val prefs = app.getSharedPreferences("ethio_yamaha", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(loadState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val active = HashMap<Int, List<Int>>()   // input note -> engine (channel*256+note)
    private val leftHeld = HashSet<Int>()
    private val taps = ArrayList<Long>()

    /** Set by MainActivity: opens the system folder / file pickers. */
    var onPickFolder: (() -> Unit)? = null
    var onPickFiles: (() -> Unit)? = null
    private val importedKeys = java.util.concurrent.ConcurrentHashMap<String, Int>()

    init {
        loadUserLibrary()
        restoreKit()
        engine.start(_ui.value.bufferFrames)
        applyAllToEngine()
        viewModelScope.launch {
            while (true) {
                delay(33)
                poll()
            }
        }
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }

    // ------------------------------------------------------------------
    //  Persistence
    // ------------------------------------------------------------------
    private fun loadState(): UiState {
        val d = UiState()
        val layout = KeyLayout.values().getOrElse(prefs.getInt("layout", d.keyLayout.ordinal)) { d.keyLayout }
        val regs = List(8) { prefs.getString("reg$it", "") ?: "" }
        return d.copy(
            keyLayout = layout,
            keyHeight = prefs.getFloat("keyHeight3", d.keyHeight),
            visibleWhites = prefs.getInt("visible", d.visibleWhites),
            startIdx = prefs.getInt("startIdx", d.startIdx),
            blackWidth = prefs.getFloat("blackW", d.blackWidth),
            blackHeight = prefs.getFloat("blackH", d.blackHeight),
            showLabels = prefs.getBoolean("labels", d.showLabels),
            fixedVelocity = prefs.getBoolean("fixedVel", d.fixedVelocity),
            bufferFrames = prefs.getInt("buffer", d.bufferFrames),
            splitNote = prefs.getInt("split", d.splitNote),
            masterVol = prefs.getFloat("master", d.masterVol),
            styleVol = prefs.getFloat("styleVol", d.styleVol),
            reverb = prefs.getFloat("reverb", d.reverb),
            tone = prefs.getInt("tone", d.tone).coerceIn(0, 2),
            chordKeys = prefs.getInt("chordKeys", 0).coerceIn(0, 3),
            regs = regs,
            tempo = Styles.all[0].tempo,
            timeSig = Styles.all[0].timeSig
        )
    }

    private fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        val e = prefs.edit()
        e.block()
        e.apply()
    }

    private fun applyAllToEngine() {
        val s = _ui.value
        engine.send(Cmd(Op.STYLE, s.styleIdx))
        engine.send(Cmd(Op.TEMPO, s.tempo))
        engine.send(Cmd(Op.MASTER, f = s.masterVol))
        engine.send(Cmd(Op.STYLE_VOL, f = s.styleVol))
        engine.send(Cmd(Op.REVERB, f = s.reverb))
        engine.send(Cmd(Op.TONE, s.tone))
        engine.send(Cmd(Op.ACMP, if (s.acmp) 1 else 0))
        engine.send(Cmd(Op.AUTOFILL, if (s.autoFill) 1 else 0))
        for (i in 0..3) pushPart(i)
    }

    private fun pushPart(i: Int) {
        val p = _ui.value.parts[i]
        val ch = PART_CH[i]
        engine.send(Cmd(Op.PROGRAM, ch, Voices.ALL[p.voiceIdx].timbre))
        engine.send(Cmd(Op.CH_VOL, ch, f = p.volume))
        engine.send(Cmd(Op.CH_PAN, ch, f = p.pan))
    }

    // ------------------------------------------------------------------
    //  Live status from the audio thread
    // ------------------------------------------------------------------
    private fun poll() {
        val e = engine
        val s = _ui.value
        val running = e.pubRunning
        val sec = e.pubSection
        val tgt = e.pubTarget
        val armed = e.pubArmed
        val meas = e.pubMeasure
        val beat = e.pubBeat
        val tempo = e.pubTempo
        val ok = e.audioOk
        val ur = e.underruns
        val vc = e.activeVoices
        val pk = (e.peak * 10).toInt() / 10f
        val fadeOff = s.fadeActive
        if (running != s.running || sec != s.playingSection || tgt != s.targetMain || armed != s.armedIntro ||
            meas != s.measure || beat != s.beat || tempo != s.tempo || ok != s.audioOk || ur != s.underruns ||
            vc != s.voices || pk != s.peak || fadeOff != s.fadeActive
        ) {
            _ui.update {
                it.copy(
                    running = running, playingSection = sec, targetMain = tgt, armedIntro = armed,
                    measure = meas, beat = beat, tempo = tempo, audioOk = ok, underruns = ur,
                    voices = vc, peak = pk, fadeActive = fadeOff
                )
            }
        }
    }

    // ------------------------------------------------------------------
    //  Keyboard
    // ------------------------------------------------------------------
    fun noteDown(n: Int, yFrac: Float) {
        val s = _ui.value
        val vel = if (s.fixedVelocity) 100 else (45 + yFrac.coerceIn(0f, 1f) * 82f).toInt()
        val left = n <= s.splitNote
        val sent = ArrayList<Int>()
        for (i in 0..3) {
            val p = s.parts[i]
            if (!p.enabled) continue
            if ((i == 3) != left) continue
            val note = (n + 12 * p.octave + s.transpose).coerceIn(0, 127)
            engine.noteOn(PART_CH[i], note, vel)
            sent.add(PART_CH[i] * 256 + note)
        }
        active[n] = sent
        if (left && s.acmp) {
            leftHeld.add(n)
            val c = Chords.detectKeys(leftHeld, s.chordKeys)
            if (c != null) {
                engine.send(Cmd(Op.CHORD, c[0], c[1]))
                _ui.update { it.copy(chordText = Chords.name(c[0], c[1])) }
            }
            if (s.syncStart && !engine.pubRunning) {
                engine.send(Cmd(Op.START))
                _ui.update { it.copy(syncStart = false) }
            }
        }
        _ui.update { it.copy(pressed = it.pressed + n) }
    }

    fun noteUp(n: Int) {
        val sent = active.remove(n)
        if (sent != null) for (code in sent) engine.noteOff(code / 256, code % 256)
        val s = _ui.value
        if (leftHeld.remove(n) && leftHeld.isEmpty() && s.syncStop && engine.pubRunning) {
            engine.send(Cmd(Op.STOP))
        }
        _ui.update { it.copy(pressed = it.pressed - n) }
    }

    fun allNotesOff() {
        for ((_, list) in active) for (code in list) engine.noteOff(code / 256, code % 256)
        active.clear()
        leftHeld.clear()
        engine.send(Cmd(Op.ALL_OFF, -1))
        _ui.update { it.copy(pressed = emptySet()) }
    }

    fun setPitchBend(v: Float) {
        for (ch in PART_CH) engine.send(Cmd(Op.BEND, ch, f = v * 2f))
        _ui.update { it.copy(pitchBend = v) }
    }

    fun setModulation(v: Float) {
        for (ch in PART_CH) engine.send(Cmd(Op.MOD, ch, f = v))
        _ui.update { it.copy(modulation = v) }
    }

    fun setSustain(on: Boolean) {
        for (ch in PART_CH) engine.send(Cmd(Op.SUSTAIN, ch, if (on) 1 else 0))
        _ui.update { it.copy(sustain = on) }
    }

    // ------------------------------------------------------------------
    //  Arranger controls
    // ------------------------------------------------------------------
    private fun locked() = _ui.value.panelLock

    fun pressSection(sec: Sec) {
        if (locked()) return
        engine.send(Cmd(Op.SECTION, sec.ordinal))
    }

    fun pressFill() {
        if (locked()) return
        val s = _ui.value
        val cur = Sec.values()[s.targetMain]
        engine.send(Cmd(Op.SECTION, cur.fillFor().ordinal))
    }

    fun startStop() {
        if (locked()) return
        if (engine.pubRunning) engine.send(Cmd(Op.STOP)) else engine.send(Cmd(Op.START))
        _ui.update { it.copy(syncStart = false) }
    }

    fun toggleSyncStart() {
        if (locked()) return
        _ui.update { it.copy(syncStart = !it.syncStart) }
    }

    fun toggleSyncStop() {
        if (locked()) return
        _ui.update { it.copy(syncStop = !it.syncStop) }
    }

    fun toggleFade() {
        if (locked()) return
        engine.send(Cmd(Op.FADE))
        _ui.update { it.copy(fadeActive = true) }
        viewModelScope.launch {
            delay(2600)
            _ui.update { it.copy(fadeActive = false) }
        }
    }

    fun toggleAcmp() {
        if (locked()) return
        val v = !_ui.value.acmp
        engine.send(Cmd(Op.ACMP, if (v) 1 else 0))
        _ui.update { it.copy(acmp = v) }
    }

    fun toggleAutoFill() {
        if (locked()) return
        val v = !_ui.value.autoFill
        engine.send(Cmd(Op.AUTOFILL, if (v) 1 else 0))
        _ui.update { it.copy(autoFill = v) }
    }

    fun setTempo(bpm: Int) {
        if (locked()) return
        val t = bpm.coerceIn(30, 300)
        engine.send(Cmd(Op.TEMPO, t))
        _ui.update { it.copy(tempo = t) }
    }

    fun nudgeTempo(d: Int) = setTempo(_ui.value.tempo + d)

    fun resetTempo() = setTempo(Styles.all[_ui.value.styleIdx].tempo)

    fun tapTempo() {
        if (locked()) return
        val now = System.currentTimeMillis()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.add(now)
        if (taps.size > 5) taps.removeAt(0)
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()).toFloat() / (taps.size - 1)
            setTempo((60000f / avg).toInt())
        }
    }

    fun transpose(d: Int) {
        if (locked()) return
        val t = (_ui.value.transpose + d).coerceIn(-12, 12)
        allNotesOff()
        engine.send(Cmd(Op.TRANSPOSE, t))
        _ui.update { it.copy(transpose = t, banner = "Transpose " + (if (t > 0) "+" else "") + t) }
    }

    fun selectStyle(idx: Int) {
        if (locked()) return
        val st = Styles.all[idx]
        engine.send(Cmd(Op.STYLE, idx))
        engine.send(Cmd(Op.TEMPO, st.tempo))
        _ui.update { it.copy(styleIdx = idx, tempo = st.tempo, timeSig = st.timeSig, banner = "Style: " + st.name) }
    }

    fun setTrackVol(i: Int, v: Float) {
        engine.send(Cmd(Op.TRACK_VOL, i, f = v))
        _ui.update { s -> s.copy(trackVol = s.trackVol.toMutableList().also { it[i] = v }) }
    }

    fun toggleTrackMute(i: Int) {
        val m = !_ui.value.trackMute[i]
        engine.send(Cmd(Op.TRACK_MUTE, i, if (m) 1 else 0))
        _ui.update { s -> s.copy(trackMute = s.trackMute.toMutableList().also { it[i] = m }) }
    }

    // ------------------------------------------------------------------
    //  Master section
    // ------------------------------------------------------------------
    fun setMaster(v: Float) {
        engine.send(Cmd(Op.MASTER, f = v)); save { putFloat("master", v) }
        _ui.update { it.copy(masterVol = v) }
    }

    fun setStyleVol(v: Float) {
        engine.send(Cmd(Op.STYLE_VOL, f = v)); save { putFloat("styleVol", v) }
        _ui.update { it.copy(styleVol = v) }
    }

    fun setTone(p: Int) {
        engine.send(Cmd(Op.TONE, p)); save { putInt("tone", p) }
        _ui.update { it.copy(tone = p, banner = "Master tone: " + com.example.audio.Tone.NAMES[p]) }
    }

    fun setReverb(v: Float) {
        engine.send(Cmd(Op.REVERB, f = v)); save { putFloat("reverb", v) }
        _ui.update { it.copy(reverb = v) }
    }

    fun setChordKeys(n: Int) {
        leftHeld.clear()
        _ui.update { it.copy(chordKeys = n.coerceIn(0, 3), banner = if (n <= 0) "CHORD: AUTO" else "CHORD: " + n + " KEY") }
        save { putInt("chordKeys", n.coerceIn(0, 3)) }
    }

    fun toggleLock() = _ui.update { it.copy(panelLock = !it.panelLock, banner = if (!it.panelLock) "PANEL LOCK ON" else "PANEL LOCK OFF") }

    // ------------------------------------------------------------------
    //  Parts and voices
    // ------------------------------------------------------------------
    fun selectPart(i: Int) = _ui.update { it.copy(selectedPart = i) }

    fun togglePart(i: Int) {
        if (locked()) return
        allNotesOff()
        _ui.update { s ->
            s.copy(selectedPart = i, parts = s.parts.toMutableList().also { it[i] = it[i].copy(enabled = !it[i].enabled) })
        }
    }

    private fun editPart(i: Int, f: (PartUi) -> PartUi) {
        _ui.update { s -> s.copy(parts = s.parts.toMutableList().also { it[i] = f(it[i]) }) }
        pushPart(i)
    }

    fun setPartVoice(i: Int, voiceIdx: Int) {
        if (locked()) return
        editPart(i) { it.copy(voiceIdx = voiceIdx, enabled = true) }
        _ui.update { it.copy(banner = Voices.ALL[voiceIdx].name) }
    }

    fun setPartVolume(i: Int, v: Float) = editPart(i) { it.copy(volume = v) }
    fun setPartPan(i: Int, v: Float) = editPart(i) { it.copy(pan = v) }
    fun setPartOctave(i: Int, o: Int) { allNotesOff(); editPart(i) { it.copy(octave = o.coerceIn(-6, 6)) } }

    fun applyOts(n: Int) {
        if (locked()) return
        val ids = Styles.all[_ui.value.styleIdx].ots[n]
        allNotesOff()
        for (i in 0..2) {
            val id = ids[i]
            if (id.isEmpty()) editPart(i) { it.copy(enabled = false) }
            else editPart(i) { it.copy(enabled = true, voiceIdx = Voices.indexOf(id).coerceAtLeast(0)) }
        }
        _ui.update { it.copy(activeOts = n, banner = "One Touch Setting " + (n + 1)) }
    }

    // ------------------------------------------------------------------
    //  Multi pads
    // ------------------------------------------------------------------
    fun padHit(note: Int) {
        engine.noteOn(9, note, 112)
    }

    // ------------------------------------------------------------------
    //  Registration memory
    // ------------------------------------------------------------------
    fun toggleMemoryArm() {
        if (locked()) return
        _ui.update { it.copy(memoryArmed = !it.memoryArmed, banner = if (!it.memoryArmed) "Press 1-8 to STORE" else "Ready") }
    }

    fun storeReg(i: Int) {
        val s = _ui.value
        val sb = StringBuilder()
        sb.append(s.styleIdx).append(',').append(s.tempo).append(',').append(s.targetMain)
        for (p in s.parts) {
            sb.append(',').append(p.voiceIdx).append(',').append(if (p.enabled) 1 else 0)
                .append(',').append((p.volume * 100).toInt()).append(',').append(p.octave)
        }
        save { putString("reg$i", sb.toString()) }
        _ui.update { st ->
            st.copy(regs = st.regs.toMutableList().also { it[i] = sb.toString() }, memoryArmed = false,
                activeReg = i, banner = "Stored registration " + (i + 1))
        }
    }

    fun recallReg(i: Int) {
        if (locked()) return
        val s = _ui.value
        if (s.memoryArmed) { storeReg(i); return }
        val txt = s.regs[i]
        if (txt.isEmpty()) { _ui.update { it.copy(banner = "Registration " + (i + 1) + " is empty") }; return }
        try {
            val v = txt.split(',').map { it.toInt() }
            allNotesOff()
            selectStyle(v[0].coerceIn(0, Styles.all.size - 1))
            setTempo(v[1])
            engine.send(Cmd(Op.SECTION, v[2]))
            for (k in 0..3) {
                val b = 3 + k * 4
                editPart(k) {
                    it.copy(voiceIdx = v[b].coerceIn(0, Voices.ALL.size - 1), enabled = v[b + 1] == 1,
                        volume = v[b + 2] / 100f, octave = v[b + 3])
                }
            }
            _ui.update { it.copy(activeReg = i, banner = "Registration " + (i + 1)) }
        } catch (e: Exception) {
            _ui.update { it.copy(banner = "Registration " + (i + 1) + " could not be read") }
        }
    }

    // ------------------------------------------------------------------
    //  Navigation and settings
    // ------------------------------------------------------------------
    fun openScreen(s: Screen?) {
        if (locked() && s != null) return
        _ui.update { it.copy(screen = s) }
    }

    fun setKeyLayout(l: KeyLayout) {
        allNotesOff()
        _ui.update { s ->
            val whites = whiteCount(l)
            val vis = s.visibleWhites.coerceAtMost(whites)
            s.copy(keyLayout = l, visibleWhites = vis, startIdx = s.startIdx.coerceIn(0, whites - vis))
        }
        save { putInt("layout", l.ordinal) }
    }

    fun setKeyHeight(v: Float) { _ui.update { it.copy(keyHeight = v) }; save { putFloat("keyHeight3", v) } }
    fun setBlackWidth(v: Float) { _ui.update { it.copy(blackWidth = v) }; save { putFloat("blackW", v) } }
    fun setBlackHeight(v: Float) { _ui.update { it.copy(blackHeight = v) }; save { putFloat("blackH", v) } }
    fun setShowLabels(v: Boolean) { _ui.update { it.copy(showLabels = v) }; save { putBoolean("labels", v) } }
    fun setFixedVelocity(v: Boolean) { _ui.update { it.copy(fixedVelocity = v) }; save { putBoolean("fixedVel", v) } }
    fun setSplit(v: Int) { allNotesOff(); _ui.update { it.copy(splitNote = v) }; save { putInt("split", v) } }

    fun setVisibleWhites(v: Int) {
        allNotesOff()
        _ui.update { s ->
            val whites = whiteCount(s.keyLayout)
            val vis = v.coerceIn(8, whites)
            s.copy(visibleWhites = vis, startIdx = s.startIdx.coerceIn(0, whites - vis))
        }
        save { putInt("visible", _ui.value.visibleWhites) }
    }

    fun setStartIdx(v: Int) {
        _ui.update { s ->
            val max = (whiteCount(s.keyLayout) - s.visibleWhites).coerceAtLeast(0)
            s.copy(startIdx = v.coerceIn(0, max))
        }
        save { putInt("startIdx", _ui.value.startIdx) }
    }

    fun setBuffer(frames: Int) {
        allNotesOff()
        _ui.update { it.copy(bufferFrames = frames, banner = "Audio buffer " + frames + " frames") }
        save { putInt("buffer", frames) }
        engine.stop()
        engine.start(frames)
        applyAllToEngine()
    }

    fun whiteCount(l: KeyLayout): Int {
        var c = 0
        for (n in l.lo..l.hi) if ((n % 12) !in intArrayOf(1, 3, 6, 8, 10)) c++
        return c
    }

    // ------------------------------------------------------------------
    //  USER library: folders, style import
    // ------------------------------------------------------------------
    private fun libDir(): File = File(getApplication<Application>().filesDir, "library/styles").also { it.mkdirs() }

    private fun safeName(n: String): String = n.replace('/', '_').replace('\\', '_').ifEmpty { "style.sty" }

    private fun userMsg(m: String) = _ui.update { it.copy(user = it.user.copy(message = m, busy = false)) }

    /** Restores added folders and re-reads the styles imported earlier (original bytes are kept in app storage). */
    private fun loadUserLibrary() {
        val roots = (prefs.getStringSet("roots", emptySet()) ?: emptySet()).toList().sorted()
        _ui.update { it.copy(user = it.user.copy(roots = roots, entries = rootEntries(roots))) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val files = libDir().listFiles()?.sortedBy { it.name } ?: emptyList()
                for (f in files) {
                    try {
                        val fb = f.readBytes()
                        val st = if (MusicStudioStyle.isStyle(fb)) MusicStudioStyle.fromBytes(fb, f.name) else StyleConverter.fromBytes(fb, f.name)
                        val idx = Styles.addImported(st)
                        importedKeys["file:" + f.name] = idx
                    } catch (e: Exception) { /* a damaged saved file is skipped */ }
                }
            }
        }
    }

    private fun rootEntries(roots: List<String>): List<UserEntry> = roots.map {
        val label = Uri.parse(it).lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: it
        UserEntry(if (label.isEmpty()) "Folder" else label, it, true, Kind.FOLDER)
    }

    fun requestAddFolder() { onPickFolder?.invoke() }
    fun requestImportFiles() { onPickFiles?.invoke() }

    fun openUserPage() {
        openScreen(Screen.USER)
    }

    fun addRoot(uri: Uri) {
        val key = uri.toString()
        val roots = (_ui.value.user.roots + key).distinct().sorted()
        prefs.edit().putStringSet("roots", roots.toSet()).apply()
        _ui.update { it.copy(user = it.user.copy(roots = roots)) }
        openScreen(Screen.USER)
        openDir(key, DocumentsContract.getTreeDocumentId(uri), listOf(rootEntries(listOf(key))[0].name), emptyList())
    }

    fun removeRoot(key: String) {
        val roots = _ui.value.user.roots - key
        prefs.edit().putStringSet("roots", roots.toSet()).apply()
        _ui.update { it.copy(user = it.user.copy(roots = roots, entries = rootEntries(roots))) }
    }

    private fun openDir(tree: String, docId: String, titles: List<String>, stackBefore: List<String>) {
        val stack = stackBefore + docId
        _ui.update { it.copy(user = it.user.copy(tree = tree, docStack = stack, titles = titles, busy = true, message = "Reading folder...")) }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { Library.list(getApplication<Application>().contentResolver, Uri.parse(tree), docId) }
            }
            res.onSuccess { list ->
                _ui.update {
                    it.copy(user = it.user.copy(
                        entries = list.map { e -> UserEntry(e.name, e.docId, e.isDir, e.kind) }, busy = false,
                        message = if (list.isEmpty()) "No styles, pads, registrations or folders here." else list.count { e -> !e.isDir }.toString() + " file(s)"
                    ))
                }
            }.onFailure {
                userMsg("Cannot read this folder. Add it again with ADD FOLDER (permission may have expired).")
            }
        }
    }

    /** Tap on a folder row. At the top level the row is one of the added folders. */
    fun userOpen(e: UserEntry) {
        val u = _ui.value.user
        if (u.tree.isEmpty()) {
            openDir(e.docId, DocumentsContract.getTreeDocumentId(Uri.parse(e.docId)), listOf(e.name), emptyList())
        } else {
            openDir(u.tree, e.docId, u.titles + e.name, u.docStack)
        }
    }

    fun userUp() {
        val u = _ui.value.user
        if (u.tree.isEmpty()) return
        if (u.docStack.size <= 1) {
            _ui.update { it.copy(user = it.user.copy(tree = "", docStack = emptyList(), titles = emptyList(), entries = rootEntries(it.user.roots), message = "")) }
        } else {
            val st = u.docStack.dropLast(1)
            openDir(u.tree, st.last(), u.titles.dropLast(1), st.dropLast(1))
        }
    }

    fun userTop() {
        _ui.update { it.copy(user = it.user.copy(tree = "", docStack = emptyList(), titles = emptyList(), entries = rootEntries(it.user.roots), message = "")) }
    }

    /** Tap on a file row. */
    fun userPick(e: UserEntry) {
        val u = _ui.value.user
        when (e.kind) {
            Kind.YAMAHA_STY, Kind.ORG_STYLE -> importStyle(Library.docUri(Uri.parse(u.tree), e.docId), e.name, "doc:" + e.docId)
            Kind.SET -> inspectFile(Library.docUri(Uri.parse(u.tree), e.docId), e.name, "doc:" + e.docId)
            Kind.MIDI -> userMsg(e.name + ": MIDI song playback is not in this version yet.")
            Kind.PAD -> userMsg(e.name + ": Multi Pad banks (.pad) are not in this version yet.")
            Kind.REGIST -> userMsg(e.name + ": registration banks (.RGT) are not in this version yet.")
            Kind.INSTRUMENT -> importKit(Library.docUri(Uri.parse(u.tree), e.docId), e.name)
            else -> {}
        }
    }

    /** Files chosen with IMPORT FILES. */
    fun importFiles(uris: List<Uri>) {
        val cr = getApplication<Application>().contentResolver
        openScreen(Screen.USER)
        for (uri in uris) {
            val name = Library.displayName(cr, uri)
            val k = Library.kindOf(name)
            if (k == Kind.YAMAHA_STY || k == Kind.ORG_STYLE) importStyle(uri, name, "uri:" + uri)
            else if (k == Kind.INSTRUMENT) importKit(uri, name)
            else if (k == Kind.SET || k == Kind.OTHER) inspectFile(uri, name, "uri:" + uri)
            else userMsg(name + ": this file type cannot be imported in this version.")
        }
    }

    private fun kitFile(): File = File(File(getApplication<Application>().filesDir, "library").also { it.mkdirs() }, "kit.instrument")

    /** Music Studio drum kit (.Instrument): its sounds replace the built-in recorded drums note by note. Kept for the next start. */
    private fun importKit(uri: Uri, name: String) {
        _ui.update { it.copy(user = it.user.copy(busy = true, message = "Reading drum kit " + name + " ...")) }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = Library.readBytes(getApplication<Application>().contentResolver, uri)
                    val set = MusicStudioKit.parse(bytes) ?: throw FormatException("This is not a readable drum kit file (only drum kits are supported, not pitched voices)")
                    kitFile().writeBytes(bytes)
                    set
                }
            }
            res.onSuccess { set ->
                engine.setDrumSamples(DrumSampleSet.default.overlay(set))
                _ui.update { it.copy(kitName = name, banner = "DRUM KIT: " + name) }
                userMsg(name + ": drum kit loaded, " + set.noteCount + " sounds. Notes the kit does not have keep the built-in drums.")
            }.onFailure { err ->
                userMsg(name + ": " + (if (err is FormatException) err.message ?: "Unsupported file" else "Could not read the file"))
            }
        }
    }

    private fun restoreKit() {
        viewModelScope.launch {
            val set = withContext(Dispatchers.IO) { runCatching { kitFile().takeIf { it.exists() }?.readBytes()?.let { MusicStudioKit.parse(it) } }.getOrNull() }
            if (set != null) engine.setDrumSamples(DrumSampleSet.default.overlay(set))
        }
    }

    private fun inboxDir(): File = File(getApplication<Application>().filesDir, "library/inbox").also { it.mkdirs() }

    /**
     * ORG .style / .set / .template and Yamaha .SET: read the real structure, show it, and keep the ORIGINAL file unchanged
     * (streamed copy, large packages are not loaded into memory). A file that turns out to be a Yamaha-type style
     * (MIDI with a CASM chunk) is imported as a style. Nothing else is claimed to play.
     */
    private fun inspectFile(uri: Uri, name: String, key: String) {
        _ui.update { it.copy(user = it.user.copy(busy = true, message = "Reading " + name + " ...")) }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val cr = getApplication<Application>().contentResolver
                    val rep = cr.openInputStream(uri)!!.use { FileInspector.inspect(name, it) }
                    if (!rep.playable) {
                        cr.openInputStream(uri)!!.use { i -> File(inboxDir(), safeName(name)).outputStream().use { o -> i.copyTo(o) } }
                    }
                    rep
                }
            }
            res.onSuccess { rep ->
                if (rep.playable) importStyle(uri, name, key)
                else userMsg(rep.text() + "\nStored unchanged in the app inbox. This format cannot be played yet.")
            }.onFailure { userMsg(name + ": could not be read (" + (it.message ?: it.javaClass.simpleName) + ")") }
        }
    }

    private fun importStyle(uri: Uri, name: String, key: String) {
        (importedKeys[key] ?: importedKeys["file:" + safeName(name)])?.let { idx -> selectImported(idx); return }
        _ui.update { it.copy(user = it.user.copy(busy = true, message = "Importing " + name + " ...")) }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = Library.readBytes(getApplication<Application>().contentResolver, uri)
                    val st = if (MusicStudioStyle.isStyle(bytes)) MusicStudioStyle.fromBytes(bytes, name) else StyleConverter.fromBytes(bytes, name)
                    // keep a copy so the style is still there after restart
                    File(libDir(), safeName(name)).writeBytes(bytes)
                    st
                }
            }
            res.onSuccess { st ->
                val idx = Styles.addImported(st)
                importedKeys[key] = idx
                importedKeys["file:" + safeName(name)] = idx
                selectImported(idx)
            }.onFailure { err ->
                val m = if (err is FormatException) err.message ?: "Unsupported file" else "Could not read the file"
                userMsg(name + ": " + m)
            }
        }
    }

    private fun selectImported(idx: Int) {
        allNotesOff()
        selectStyle(idx)
        _ui.update { it.copy(screen = null, user = it.user.copy(busy = false, message = "")) }
    }

    fun onBackground() {
        allNotesOff()
        engine.send(Cmd(Op.PANIC))   // hard stop with a few ms of fade (keyboard all-off no longer cuts style drums)
        engine.send(Cmd(Op.STOP))
    }
}
