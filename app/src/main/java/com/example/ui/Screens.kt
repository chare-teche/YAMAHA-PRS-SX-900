package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.audio.Chords
import com.example.audio.Styles
import com.example.audio.Track
import com.example.audio.Voices
import com.example.viewmodel.ArrangerViewModel
import com.example.viewmodel.KeyLayout
import com.example.viewmodel.Screen
import com.example.viewmodel.UiState

/** Full-screen pages opened from the LCD dock / panel buttons. Shows nothing when no page is open. */
@Composable
fun OverlayHost(vm: ArrangerViewModel, s: UiState) {
    val scr = s.screen ?: return
    val title = when (scr) {
        Screen.VOICE -> "VOICE SELECT"
        Screen.STYLE -> "STYLE SELECT"
        Screen.MIXER -> "MIXER"
        Screen.PADS -> "MULTI PAD"
        Screen.REGIST -> "REGISTRATION MEMORY"
        Screen.SETTINGS -> "SETTINGS"
        Screen.USER -> "USER"
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xF20A0B0D)).pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Pal.gold, fontSize = fsp(15f), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                PanelButton("CLOSE", Modifier.width(90.dp).fillMaxHeight(), fontSize = 10f) { vm.openScreen(null) }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (scr) {
                    Screen.VOICE -> VoicePage(vm, s)
                    Screen.STYLE -> StylePage(vm, s)
                    Screen.MIXER -> MixerPage(vm, s)
                    Screen.PADS -> PadsPage(vm)
                    Screen.REGIST -> RegistPage(vm, s)
                    Screen.SETTINGS -> SettingsPage(vm, s)
                    Screen.USER -> UserPage(vm, s)
                }
            }
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Pal.gold, activeTrackColor = Pal.blue, inactiveTrackColor = Pal.edge
)

@Composable
private fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String,
    onChange: (Float) -> Unit, modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Pal.text, fontSize = fsp(10f), modifier = Modifier.width(140.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange,
            valueRange = range, modifier = Modifier.weight(1f), colors = sliderColors()
        )
        Text(valueText, color = Pal.gold, fontSize = fsp(10f), textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Pal.gold, fontSize = fsp(11f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

/** P1, P2, P3 ... tab row. Pages hold [per] items each; at least 3 tabs are shown. */
@Composable
private fun PageTabs(total: Int, per: Int, page: Int, onPage: (Int) -> Unit) {
    val pages = maxOf(3, (total + per - 1) / per)
    Row(Modifier.fillMaxWidth().height(38.dp)) {
        for (i in 0 until pages) {
            PanelButton("P" + (i + 1), Modifier.width(64.dp).fillMaxHeight(), page == i, Pal.gold, fontSize = 11f) { onPage(i) }
        }
    }
}

private fun noteName(n: Int): String = Chords.NOTE_NAMES[n % 12] + (n / 12 - 1)

// ----------------------------------------------------------------------
//  VOICE
// ----------------------------------------------------------------------
@Composable
private fun VoicePage(vm: ArrangerViewModel, s: UiState) {
    val sel = s.selectedPart
    val p = s.parts[sel]
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().height(42.dp)) {
                for (i in 0..3) {
                    PanelButton(
                        s.parts[i].name.replace("RIGHT ", "R"), Modifier.weight(1f).fillMaxHeight(),
                        sel == i, Pal.gold, fontSize = 10f
                    ) { vm.selectPart(i) }
                }
            }
            PanelButton(
                if (p.enabled) p.name + " : ON" else p.name + " : OFF", Modifier.fillMaxWidth().height(42.dp),
                p.enabled, Pal.green, fontSize = 10f
            ) { vm.togglePart(sel) }
            LabeledSlider("Volume", p.volume, 0f..1f, (p.volume * 100).toInt().toString(), { vm.setPartVolume(sel, it) })
            LabeledSlider("Pan", p.pan, -1f..1f, if (p.pan < -0.05f) "L" + (-p.pan * 100).toInt() else if (p.pan > 0.05f) "R" + (p.pan * 100).toInt() else "C", { vm.setPartPan(sel, it) })
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelButton("OCT -", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.setPartOctave(sel, p.octave - 1) }
                Text(
                    "Octave " + (if (p.octave > 0) "+" else "") + p.octave, color = Pal.gold, fontSize = fsp(10f),
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f)
                )
                PanelButton("OCT +", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.setPartOctave(sel, p.octave + 1) }
            }
            Text(
                "Voices are synthesized in the app (not recorded samples).",
                color = Pal.dim, fontSize = fsp(8f), modifier = Modifier.padding(top = 8.dp)
            )
        }
        var page by remember { mutableStateOf(0) }
        val per = 5
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PageTabs(Voices.ALL.size, per, page) { page = it }
            LazyVerticalGrid(
                GridCells.Adaptive(150.dp), Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val from = page * per
                val to = minOf(Voices.ALL.size, from + per)
                items(maxOf(0, to - from)) { k ->
                    val i = from + k
                    val v = Voices.ALL[i]
                    PanelButton(
                        v.name + "\n" + v.category, Modifier.fillMaxWidth().height(56.dp),
                        p.voiceIdx == i, Pal.blue, fontSize = 10f
                    ) { vm.setPartVoice(sel, i) }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
//  STYLE
// ----------------------------------------------------------------------
@Composable
private fun StylePage(vm: ArrangerViewModel, s: UiState) {
    Column(Modifier.fillMaxSize()) {
        var page by remember { mutableStateOf(0) }
        val per = 8
        PageTabs(Styles.all.size, per, page) { page = it }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val from = page * per
            val to = minOf(Styles.all.size, from + per)
            items(maxOf(0, to - from)) { k ->
                val i = from + k
                val st = Styles.all[i]
                PanelButton(
                    st.name + "   -   " + st.category + "   -   " + st.tempo + " BPM   -   " + st.timeSig,
                    Modifier.fillMaxWidth().height(46.dp), s.styleIdx == i, Pal.orange, fontSize = 11f
                ) { vm.selectStyle(i) }
            }
        }
        Text(
            "Built-in styles are generated patterns. Imported Yamaha .STY styles appear here as User styles; " +
                "use USER on the main screen to browse folders. ORG, SET, MIDI and pad files are not supported yet.",
            color = Pal.dim, fontSize = fsp(8f), modifier = Modifier.padding(top = 6.dp)
        )
    }
}

// ----------------------------------------------------------------------
//  MIXER
// ----------------------------------------------------------------------
@Composable
private fun MixerPage(vm: ArrangerViewModel, s: UiState) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            SectionTitle("STYLE TRACKS")
            for (i in 0..7) {
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Track.NAMES[i], color = Pal.text, fontSize = fsp(10f), modifier = Modifier.width(76.dp))
                    Slider(
                        value = s.trackVol[i], onValueChange = { vm.setTrackVol(i, it) },
                        modifier = Modifier.weight(1f), colors = sliderColors()
                    )
                    PanelButton("MUTE", Modifier.width(56.dp).fillMaxHeight(0.8f), s.trackMute[i], Pal.red, fontSize = 9f) { vm.toggleTrackMute(i) }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            SectionTitle("KEYBOARD PARTS")
            for (i in 0..3) {
                val p = s.parts[i]
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = Pal.text, fontSize = fsp(10f), modifier = Modifier.width(76.dp))
                    Slider(
                        value = p.volume, onValueChange = { vm.setPartVolume(i, it) },
                        modifier = Modifier.weight(1f), colors = sliderColors()
                    )
                    PanelButton(if (p.enabled) "ON" else "OFF", Modifier.width(56.dp).fillMaxHeight(0.8f), p.enabled, Pal.green, fontSize = 9f) { vm.togglePart(i) }
                }
            }
            SectionTitle("MASTER")
            LabeledSlider("Master volume", s.masterVol, 0f..1f, (s.masterVol * 100).toInt().toString(), { vm.setMaster(it) })
            LabeledSlider("Style volume", s.styleVol, 0f..1f, (s.styleVol * 100).toInt().toString(), { vm.setStyleVol(it) })
            LabeledSlider("Reverb", s.reverb, 0f..0.6f, (s.reverb / 0.6f * 100).toInt().toString(), { vm.setReverb(it) })
        }
    }
}

// ----------------------------------------------------------------------
//  MULTI PADS (real drum / percussion hits)
// ----------------------------------------------------------------------
private val PADS = listOf(
    "CRASH" to 49, "KICK" to 36, "SNARE" to 38, "TAMBOURINE" to 54,
    "CLAP" to 39, "TOM" to 45, "CONGA" to 62, "COWBELL" to 56
)

@Composable
private fun PadsPage(vm: ArrangerViewModel) {
    LazyVerticalGrid(
        GridCells.Fixed(4), Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(PADS.size) { i ->
            PanelButton(
                PADS[i].first, Modifier.fillMaxWidth().height(96.dp), false, Pal.blue, fontSize = 13f
            ) { vm.padHit(PADS[i].second) }
        }
    }
}

// ----------------------------------------------------------------------
//  REGISTRATION MEMORY
// ----------------------------------------------------------------------
@Composable
private fun RegistPage(vm: ArrangerViewModel, s: UiState) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Each slot remembers the style, tempo, main section and the four keyboard parts (voice, on/off, volume, octave).",
            color = Pal.dim, fontSize = fsp(9f), modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyVerticalGrid(
            GridCells.Fixed(4), Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(8) { i ->
                val txt = s.regs[i]
                val styleName = if (txt.isEmpty()) "Empty"
                else Styles.all.getOrNull(txt.split(',')[0].toIntOrNull() ?: -1)?.name ?: "Saved"
                Column(
                    Modifier.fillMaxWidth().height(104.dp).background(Pal.panel, RoundedCornerShape(5.dp))
                        .border(1.dp, if (s.activeReg == i) Pal.blue else Pal.edge, RoundedCornerShape(5.dp)).padding(6.dp)
                ) {
                    Text("REGIST " + (i + 1), color = Pal.gold, fontSize = fsp(11f), fontWeight = FontWeight.Bold)
                    Text(styleName, color = if (txt.isEmpty()) Pal.dim else Pal.text, fontSize = fsp(10f), modifier = Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().height(34.dp)) {
                        PanelButton("RECALL", Modifier.weight(1f).fillMaxHeight(), fontSize = 9f) { vm.recallReg(i) }
                        PanelButton("STORE", Modifier.weight(1f).fillMaxHeight(), false, Pal.gold, fontSize = 9f) { vm.storeReg(i) }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
//  SETTINGS (keyboard size, black keys, audio)
// ----------------------------------------------------------------------
@Composable
private fun SettingsPage(vm: ArrangerViewModel, s: UiState) {
    val whites = vm.whiteCount(s.keyLayout)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("KEYBOARD - NUMBER OF KEYS")
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            for (l in KeyLayout.values()) {
                PanelButton(l.label, Modifier.weight(1f).fillMaxHeight(), s.keyLayout == l, Pal.gold, fontSize = 11f) { vm.setKeyLayout(l) }
            }
        }
        SectionTitle("KEYBOARD - SIZE")
        LabeledSlider("Keyboard height", s.keyHeight, 0.35f..0.75f, (s.keyHeight * 100).toInt().toString() + "%", { vm.setKeyHeight(it) })
        LabeledSlider("Keys on screen (white)", s.visibleWhites.toFloat(), 8f..whites.toFloat(), s.visibleWhites.toString(), { vm.setVisibleWhites(it.toInt()) })
        LabeledSlider("Black key width", s.blackWidth, 0.35f..0.85f, (s.blackWidth * 100).toInt().toString() + "%", { vm.setBlackWidth(it) })
        LabeledSlider("Black key length", s.blackHeight, 0.40f..0.80f, (s.blackHeight * 100).toInt().toString() + "%", { vm.setBlackHeight(it) })
        LabeledSlider("Split point (LEFT | RIGHT)", s.splitNote.toFloat(), 40f..72f, noteName(s.splitNote), { vm.setSplit(it.toInt()) })
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            PanelButton(if (s.showLabels) "KEY LABELS: ON" else "KEY LABELS: OFF", Modifier.weight(1f).fillMaxHeight(), s.showLabels, Pal.blue, fontSize = 10f) { vm.setShowLabels(!s.showLabels) }
            PanelButton(
                if (s.fixedVelocity) "VELOCITY: FIXED" else "VELOCITY: TOUCH POSITION",
                Modifier.weight(1f).fillMaxHeight(), !s.fixedVelocity, Pal.blue, fontSize = 10f
            ) { vm.setFixedVelocity(!s.fixedVelocity) }
        }
        Text(
            "Touch position: pressing lower on a key plays louder. Drag the gold bar above the keys to scroll along the keyboard.",
            color = Pal.dim, fontSize = fsp(8f), modifier = Modifier.padding(top = 4.dp)
        )
        SectionTitle("ARRANGER")
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            PanelButton("ACCOMPANIMENT", Modifier.weight(1f).fillMaxHeight(), s.acmp, Pal.green, fontSize = 9f) { vm.toggleAcmp() }
            PanelButton("AUTO FILL", Modifier.weight(1f).fillMaxHeight(), s.autoFill, Pal.green, fontSize = 9f) { vm.toggleAutoFill() }
            PanelButton("SYNC START", Modifier.weight(1f).fillMaxHeight(), s.syncStart, Pal.blue, fontSize = 9f) { vm.toggleSyncStart() }
            PanelButton("SYNC STOP", Modifier.weight(1f).fillMaxHeight(), s.syncStop, Pal.blue, fontSize = 9f) { vm.toggleSyncStop() }
        }
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            PanelButton("FADE IN/OUT", Modifier.weight(1f).fillMaxHeight(), s.fadeActive, Pal.gold, fontSize = 9f) { vm.toggleFade() }
            PanelButton("TAP TEMPO", Modifier.weight(1f).fillMaxHeight(), fontSize = 9f) { vm.tapTempo() }
            PanelButton("RESET TEMPO", Modifier.weight(1f).fillMaxHeight(), fontSize = 9f) { vm.resetTempo() }
            PanelButton("PANEL LOCK", Modifier.weight(1f).fillMaxHeight(), s.panelLock, Pal.orange, fontSize = 9f) { vm.toggleLock() }
        }
        SectionTitle("FILES AND IMPORT")
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            PanelButton("ADD FOLDER", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.requestAddFolder() }
            PanelButton("IMPORT FILES", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.requestImportFiles() }
            PanelButton("OPEN USER LIBRARY", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.openUserPage() }
        }
        Text(
            "Yamaha .STY (and any style that is a MIDI file with a CASM chunk, whatever its extension): imported and played. " +
                "ORG .style / .template / .music and Yamaha or ORG .set packages: the file is read, its real structure is shown " +
                "(chunks, ZIP contents) and the original is kept unchanged, but they cannot be played yet because their format " +
                "is not confirmed. Pick the file here and send the report text to continue the work.",
            color = Pal.dim, fontSize = fsp(8f), modifier = Modifier.padding(top = 4.dp)
        )
        SectionTitle("AUDIO")
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            for (i in 0 until com.example.audio.Tone.COUNT) {
                PanelButton("TONE: " + com.example.audio.Tone.NAMES[i], Modifier.weight(1f).fillMaxHeight(), s.tone == i, Pal.gold, fontSize = 10f) { vm.setTone(i) }
            }
        }
        Text(
            "Master tone is an equalizer on the final mix. CLEAR cuts low-end mud and adds presence and air, WARM does the opposite, FLAT turns it off.",
            color = Pal.dim, fontSize = fsp(8f), modifier = Modifier.padding(vertical = 4.dp)
        )
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            for (f in listOf(128, 256, 512)) {
                PanelButton("BUFFER " + f, Modifier.weight(1f).fillMaxHeight(), s.bufferFrames == f, Pal.gold, fontSize = 10f) { vm.setBuffer(f) }
            }
        }
        Text(
            "Smaller buffer = lower delay but more risk of crackle. If you hear crackles, choose 512. " +
                "Status: " + (if (s.audioOk) "audio running" else "audio stopped") +
                ", dropouts (underruns): " + s.underruns + ", voices sounding: " + s.voices + " of 80.",
            color = Pal.text, fontSize = fsp(9f), modifier = Modifier.padding(top = 4.dp)
        )
        SectionTitle("ABOUT")
        Text(
            "ETHIO YAMAHA is an independently developed arranger keyboard inspired by the performance " +
                "experience of professional arranger workstations. It is not official Yamaha software. " +
                "Sounds are synthesized inside the app; real sampled instruments and SoundFont support are planned for a later stage.",
            color = Pal.dim, fontSize = fsp(9f), modifier = Modifier.padding(bottom = 20.dp)
        )
    }
}


// ----------------------------------------------------------------------
//  USER: folder browser (styles from the folders the user added)
// ----------------------------------------------------------------------
@Composable
private fun UserPage(vm: ArrangerViewModel, s: UiState) {
    val u = s.user
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelButton("ADD FOLDER", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.requestAddFolder() }
            PanelButton("IMPORT FILES", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.requestImportFiles() }
            PanelButton("UP", Modifier.weight(0.6f).fillMaxHeight(), u.tree.isNotEmpty(), Pal.orange, fontSize = 10f) { vm.userUp() }
            PanelButton("TOP", Modifier.weight(0.6f).fillMaxHeight(), fontSize = 10f) { vm.userTop() }
        }
        Text(
            if (u.tree.isEmpty()) "USER  >  folders" else "USER  >  " + u.titles.joinToString("  >  "),
            color = Pal.gold, fontSize = fsp(10f), maxLines = 1, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(u.entries.size) { i ->
                val e = u.entries[i]
                val tag = if (e.isDir) "[ DIR ]" else "[ " + e.kind.label + " ]"
                PanelButton(
                    tag + "   " + e.name,
                    Modifier.fillMaxWidth().height(42.dp),
                    !e.isDir && e.kind.supported, if (e.isDir) Pal.gold else Pal.blue, fontSize = 11f
                ) { if (e.isDir) vm.userOpen(e) else vm.userPick(e) }
            }
        }
        Text(
            (if (u.busy) "Working...  " else "") + u.message,
            color = if (u.busy) Pal.orange else Pal.dim, fontSize = fsp(9f),
            modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).verticalScroll(rememberScrollState()).padding(top = 4.dp)
        )
    }
}
