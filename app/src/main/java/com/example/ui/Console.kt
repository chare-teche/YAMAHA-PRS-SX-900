package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.Sec
import com.example.audio.Styles
import com.example.audio.Track
import com.example.viewmodel.ArrangerViewModel
import com.example.viewmodel.Screen
import com.example.viewmodel.UiState

/**
 * Main screen. Layout (landscape):
 *   [speaker][ left controls ][ LCD menu + one-touch row ][ right wheel/buttons ][speaker]
 *   [ style-control strip: intro, variation, break, start, fill, ending, registration, pads ]
 *   [ large keyboard with pitch / mod wheels ]
 * Every button calls a real ViewModel function.
 */
@Composable
fun ConsoleApp(vm: ArrangerViewModel) {
    val s by vm.ui.collectAsState()
    Box(Modifier.fillMaxSize().background(Pal.bg)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val h = maxHeight
            val scale = (h.value / 360f).coerceIn(0.8f, 1.6f)
            CompositionLocalProvider(LocalScale provides scale) {
                Column(Modifier.fillMaxSize()) {
                    PanelArea(vm, s, Modifier.fillMaxWidth().weight(1f))
                    StripRow(vm, s, Modifier.fillMaxWidth().height(h * 0.115f))
                    KeyboardArea(vm, s, Modifier.fillMaxWidth().height(h * s.keyHeight))
                }
            }
        }
    }
}

private fun bodyBrush(): Brush = Brush.verticalGradient(listOf(Color(0xFF2A2C31), Color(0xFF121316)))
private fun boxBrush(): Brush = Brush.verticalGradient(listOf(Color(0xFF1B1D21), Color(0xFF0E0F11)))

// ----------------------------------------------------------------------
//  Upper panel (SX900-style): left controls | LCD + one-touch row | right wheel/functions
//  Two decorative speaker grilles frame the panel (drawn in code, no pictures, no coloured lights).
// ----------------------------------------------------------------------
@Composable
private fun PanelArea(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    Row(
        modifier.background(bodyBrush()).padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Speaker(Modifier.weight(0.8f).fillMaxHeight())
        if (s.screen == null) {
            LeftControls(vm, s, Modifier.weight(1.55f).fillMaxHeight())
            Column(Modifier.weight(2.7f).fillMaxHeight()) {
                Lcd(vm, s, Modifier.weight(1f).fillMaxWidth())
                OtsRow(vm, s, Modifier.fillMaxWidth().height(26.dp * LocalScale.current))
            }
            RightControls(vm, s, Modifier.weight(1.9f).fillMaxHeight())
        } else {
            // a page is open: the LCD takes the whole control area (all pages live inside the LCD)
            LcdPages(vm, s, Modifier.weight(6.15f).fillMaxHeight())
        }
        Speaker(Modifier.weight(0.8f).fillMaxHeight())
    }
}

/** Speaker grille: dark cabinet, concentric rings and a cone in the middle. Purely decorative. */
@Composable
private fun Speaker(modifier: Modifier) {
    Canvas(
        modifier.clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1D21), Color(0xFF09090B))))
            .border(1.dp, Pal.edge, RoundedCornerShape(6.dp))
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = minOf(size.width, size.height) * 0.46f
        drawCircle(Color(0xFF0D0E10), r, c)
        var k = 0
        while (k < 7) {
            drawCircle(Color(0xFF34373D), r * (1f - k * 0.12f), c, style = Stroke(width = 1.5f))
            k++
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFF4A4E56), Color(0xFF121316)), center = c, radius = r * 0.3f), r * 0.3f, c)
    }
}

@Composable
private fun LeftControls(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val part = s.parts[s.selectedPart]
    Column(modifier.clip(RoundedCornerShape(5.dp)).background(boxBrush()).border(1.dp, Color(0xFF4A4E56), RoundedCornerShape(5.dp)).padding(3.dp)) {
        // top: MASTER knob + the eight style-track keys
        Row(Modifier.fillMaxWidth().weight(1.5f)) {
            Knob("MASTER", s.masterVol, { vm.setMaster(it) }, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(2.3f).fillMaxHeight()) {
                Text("STYLE TRACKS", color = Pal.gold, fontSize = fsp(6f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().weight(1f)) { for (i in 0..3) TrackBtn(vm, s, i, Modifier.weight(1f)) }
                Row(Modifier.fillMaxWidth().weight(1f)) { for (i in 4..7) TrackBtn(vm, s, i, Modifier.weight(1f)) }
            }
        }
        // middle: BALANCE and REVERB knobs + tempo / octave / transpose
        Row(Modifier.fillMaxWidth().weight(1.5f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Knob("BALANCE", s.styleVol, { vm.setStyleVol(it) }, Modifier.weight(1f).fillMaxWidth())
            }
            Column(Modifier.weight(2.3f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Group("TEMPO " + s.tempo, 1f) {
                        HwButton("-", Modifier.weight(1f).fillMaxHeight()) { vm.nudgeTempo(-1) }
                        HwButton("+", Modifier.weight(1f).fillMaxHeight()) { vm.nudgeTempo(1) }
                    }
                    Group("OCTAVE " + (if (part.octave > 0) "+" else "") + part.octave, 1f) {
                        HwButton("-", Modifier.weight(1f).fillMaxHeight()) { vm.setPartOctave(s.selectedPart, part.octave - 1) }
                        HwButton("+", Modifier.weight(1f).fillMaxHeight()) { vm.setPartOctave(s.selectedPart, part.octave + 1) }
                    }
                    Group("TRAN " + (if (s.transpose > 0) "+" else "") + s.transpose, 1f) {
                        HwButton("-", Modifier.weight(1f).fillMaxHeight()) { vm.transpose(-1) }
                        HwButton("+", Modifier.weight(1f).fillMaxHeight()) { vm.transpose(1) }
                    }
                }
            }
        }
        // bottom: REVERB knob + fill / fade / sync keys
        Row(Modifier.fillMaxWidth().weight(1.3f)) {
            Knob("REVERB", s.reverb, { vm.setReverb(it) }, Modifier.weight(1f).fillMaxHeight())
            Row(Modifier.weight(2.3f).fillMaxHeight()) {
                HwButton("AUTO FILL", Modifier.weight(1f).fillMaxHeight(), s.autoFill, Pal.green) { vm.toggleAutoFill() }
                HwButton("FADE", Modifier.weight(1f).fillMaxHeight(), s.fadeActive, Pal.gold) { vm.toggleFade() }
                HwButton("SYNC START", Modifier.weight(1f).fillMaxHeight(), s.syncStart) { vm.toggleSyncStart() }
                HwButton("SYNC STOP", Modifier.weight(1f).fillMaxHeight(), s.syncStop) { vm.toggleSyncStop() }
            }
        }
    }
}

@Composable
private fun TrackBtn(vm: ArrangerViewModel, s: UiState, i: Int, modifier: Modifier) {
    val label = listOf("RHY 1", "RHY 2", "BASS", "CHORD 1", "CHORD 2", "PAD", "PHR 1", "PHR 2")[i]
    HwButton(label, modifier.fillMaxHeight(), !s.trackMute[i], Pal.green) { vm.toggleTrackMute(i) }
}

// ----------------------------------------------------------------------
//  LCD: SX900-style main menu (magenta title bar, blue/orange tiles)
// ----------------------------------------------------------------------
@Composable
private fun Lcd(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val style = Styles.all[s.styleIdx]
    val sec = Sec.values()[if (s.running) s.playingSection else s.targetMain]
    Column(
        modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFF040507))
            .border(2.dp, Color(0xFF30333A), RoundedCornerShape(5.dp)).padding(2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().weight(0.55f).background(Color(0xFFE0115F)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ETHIO YAMAHA", color = Pal.gold, fontSize = fsp(8f), fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text("   Main Menu", color = Color.White, fontSize = fsp(8f), fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            val pulse by animateFloatAsState(if (s.running && s.beat % 2 == 1) 1f else 0.2f, tween(140), label = "beat")
            Box(Modifier.padding(end = 4.dp).size(7.dp).alpha(pulse).clip(CircleShape).background(Color(0xFF7FD6FF)))
            Text(s.chordText, color = Color.White, fontSize = fsp(8f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        val blue = Color(0xFF1E5FB5)
        val orange = Color(0xFFC8661C)
        Row(Modifier.fillMaxWidth().weight(1f)) {
            MenuItem("Voice", "4 part voices", blue, Modifier.weight(1f)) { vm.openScreen(Screen.VOICE) }
            MenuItem("Style", "Built-in + imported", orange, Modifier.weight(1f)) { vm.openScreen(Screen.STYLE) }
            MenuItem("USER", "Folders and files", orange, Modifier.weight(1f)) { vm.openUserPage() }
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            MenuItem("Slider", "Track levels", orange, Modifier.weight(1f)) { vm.openScreen(Screen.MIXER) }
            MenuItem("Multi Pad", "Drum hits", blue, Modifier.weight(1f)) { vm.openScreen(Screen.PADS) }
            MenuItem("Registration", "Store / recall", blue, Modifier.weight(1f)) { vm.openScreen(Screen.REGIST) }
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            MenuItem("Settings", "Keys, audio", orange, Modifier.weight(1f)) { vm.openScreen(Screen.SETTINGS) }
            MenuItem("Add Folder", "Pick a style folder", blue, Modifier.weight(1f)) { vm.requestAddFolder() }
            MenuItem("Import File", "Pick .STY files", blue, Modifier.weight(1f)) { vm.requestImportFiles() }
        }
        Text(
            style.name + "  |  " + sec.label + "  |  " + s.tempo + " BPM " + s.timeSig + "  |  M " + String.format("%03d.%d", s.measure, s.beat) +
                "  |  " + (if (s.audioOk) "AUDIO OK" else "AUDIO OFF") + " V" + s.voices + " U" + s.underruns +
                (if (s.transpose != 0) "  |  TRANS " + (if (s.transpose > 0) "+" else "") + s.transpose else "") +
                (if (s.panelLock) "  |  LOCKED" else "") + "  |  " + s.banner,
            color = if (s.audioOk && s.underruns == 0) Pal.green else Pal.orange,
            fontSize = fsp(7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().weight(0.5f).background(Color(0xFF14161A)).padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun MenuItem(title: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val cb by rememberUpdatedState(onClick)
    var down by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (down) 0.94f else 1f, tween(90), label = "menuPress")
    Column(
        modifier.fillMaxHeight().padding(1.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .background(Brush.verticalGradient(listOf(color.copy(alpha = if (down) 1f else 0.92f), color.copy(alpha = if (down) 0.8f else 0.55f))))
            .pointerInput(Unit) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { cb() }) }.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color.White, fontSize = fsp(10f), fontWeight = FontWeight.Bold, maxLines = 1)
        Text(sub, color = Color(0xCCFFFFFF), fontSize = fsp(6.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OtsRow(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    Row(modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("ONE TOUCH\nSETTING", color = Pal.gold, fontSize = fsp(6f), fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
        for (i in 0..3) {
            PanelButton((i + 1).toString(), Modifier.weight(1f).fillMaxHeight(), s.activeOts == i, Pal.blue, fontSize = 9f) { vm.applyOts(i) }
        }
    }
}

// ----------------------------------------------------------------------
//  Right side: menu / exit, data wheel (tempo), page and part buttons
// ----------------------------------------------------------------------
@Composable
private fun RightControls(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(5.dp)).background(boxBrush()).border(1.dp, Color(0xFF4A4E56), RoundedCornerShape(5.dp)).padding(3.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // left half: menu / exit, big data wheel, tempo arrows
            Column(Modifier.weight(1.15f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().weight(0.55f)) {
                    HwButton("MENU", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.SETTINGS) { vm.openScreen(Screen.SETTINGS) }
                    HwButton("EXIT", Modifier.weight(1f).fillMaxHeight()) { vm.openScreen(null) }
                }
                DataWheel({ vm.nudgeTempo(it) }, Modifier.fillMaxWidth().weight(1.5f).padding(2.dp))
                Row(Modifier.fillMaxWidth().weight(0.5f), verticalAlignment = Alignment.CenterVertically) {
                    HwButton("<", Modifier.weight(1f).fillMaxHeight()) { vm.nudgeTempo(-1) }
                    HwButton(">", Modifier.weight(1f).fillMaxHeight()) { vm.nudgeTempo(1) }
                }
                Text("WHEEL = TEMPO " + s.tempo, color = Pal.dim, fontSize = fsp(6f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth())
            }
            // right half: function keys (label above each key)
            Column(Modifier.weight(1.9f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    HwButton("STYLE", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.STYLE) { vm.openScreen(Screen.STYLE) }
                    HwButton("VOICE", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.VOICE) { vm.openScreen(Screen.VOICE) }
                    HwButton("SLIDER", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.MIXER) { vm.openScreen(Screen.MIXER) }
                    HwButton("MULTI PAD", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.PADS) { vm.openScreen(Screen.PADS) }
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    HwButton("REGIST", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.REGIST) { vm.openScreen(Screen.REGIST) }
                    HwButton("ACMP", Modifier.weight(1f).fillMaxHeight(), s.acmp, Pal.green) { vm.toggleAcmp() }
                    HwButton("TAP TEMPO", Modifier.weight(1f).fillMaxHeight()) { vm.tapTempo() }
                    HwButton("RESET TEMPO", Modifier.weight(1f).fillMaxHeight()) { vm.resetTempo() }
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    HwButton("CHORD AUTO", Modifier.weight(1f).fillMaxHeight(), s.chordKeys == 0, Pal.green) { vm.setChordKeys(0) }
                    HwButton("1 KEY CHORD", Modifier.weight(1f).fillMaxHeight(), s.chordKeys == 1, Pal.green) { vm.setChordKeys(1) }
                    HwButton("2 KEY CHORD", Modifier.weight(1f).fillMaxHeight(), s.chordKeys == 2, Pal.green) { vm.setChordKeys(2) }
                    HwButton("3 KEY CHORD", Modifier.weight(1f).fillMaxHeight(), s.chordKeys == 3, Pal.green) { vm.setChordKeys(3) }
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    HwButton("PANEL LOCK", Modifier.weight(1f).fillMaxHeight(), s.panelLock, Pal.orange) { vm.toggleLock() }
                    Box(Modifier.weight(3f).fillMaxHeight())
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
//  Style-control strip (one row, with group titles)
// ----------------------------------------------------------------------
@Composable
private fun SecButton(vm: ArrangerViewModel, s: UiState, sec: Sec, label: String, modifier: Modifier) {
    val playing = s.running && s.playingSection == sec.ordinal
    val target = sec.isMain && s.targetMain == sec.ordinal
    val armed = s.armedIntro == sec.ordinal
    PanelButton(label, modifier.fillMaxHeight(), playing || target || armed, if (playing) Pal.orange else Pal.blue, fontSize = 9f) {
        vm.pressSection(sec)
    }
}

@Composable
private fun RowScope.Group(title: String, weight: Float, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.weight(weight).fillMaxHeight().padding(horizontal = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF8A8F98)))
            Text(" " + title + " ", color = Color(0xFFE6E8EB), fontSize = fsp(6.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
            Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF8A8F98)))
        }
        Row(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

@Composable
private fun StripRow(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    Row(modifier.background(Brush.verticalGradient(listOf(Color(0xFF1D1F23), Color(0xFF0C0D0F)))).padding(horizontal = 3.dp, vertical = 1.dp)) {
        Group("INTRO", 3f) {
            SecButton(vm, s, Sec.INTRO_1, "I", Modifier.weight(1f))
            SecButton(vm, s, Sec.INTRO_2, "II", Modifier.weight(1f))
            SecButton(vm, s, Sec.INTRO_3, "III", Modifier.weight(1f))
        }
        Group("VARIATION", 4f) {
            SecButton(vm, s, Sec.MAIN_A, "A", Modifier.weight(1f))
            SecButton(vm, s, Sec.MAIN_B, "B", Modifier.weight(1f))
            SecButton(vm, s, Sec.MAIN_C, "C", Modifier.weight(1f))
            SecButton(vm, s, Sec.MAIN_D, "D", Modifier.weight(1f))
        }
        Group("BREAK", 1f) { SecButton(vm, s, Sec.BREAK, "BRK", Modifier.weight(1f)) }
        Group("START", 1.5f) {
            PanelButton("START\nSTOP", Modifier.weight(1f).fillMaxHeight(), s.running, Pal.blue, fontSize = 8f) { vm.startStop() }
        }
        Group("FILL", 4f) {
            SecButton(vm, s, Sec.FILL_A, "1", Modifier.weight(1f))
            SecButton(vm, s, Sec.FILL_B, "2", Modifier.weight(1f))
            SecButton(vm, s, Sec.FILL_C, "3", Modifier.weight(1f))
            SecButton(vm, s, Sec.FILL_D, "4", Modifier.weight(1f))
        }
        Group("ENDING", 3f) {
            SecButton(vm, s, Sec.END_1, "I", Modifier.weight(1f))
            SecButton(vm, s, Sec.END_2, "II", Modifier.weight(1f))
            SecButton(vm, s, Sec.END_3, "III", Modifier.weight(1f))
        }
        Group("REGISTRATION MEMORY", 8.5f) {
            for (i in 0..7) {
                val stored = s.regs[i].isNotEmpty()
                PanelButton(
                    (i + 1).toString(), Modifier.weight(1f).fillMaxHeight(), s.activeReg == i,
                    if (s.memoryArmed) Pal.gold else Pal.blue, textColor = if (stored) Pal.gold else null, fontSize = 9f
                ) { vm.recallReg(i) }
            }
            PanelButton("MEM", Modifier.weight(1f).fillMaxHeight(), s.memoryArmed, Pal.gold, fontSize = 7f) { vm.toggleMemoryArm() }
        }
        Group("PADS", 4f) {
            val pads = listOf(82, 36, 38, 54)
            for (i in 0..3) PanelButton((i + 1).toString(), Modifier.weight(1f).fillMaxHeight(), fontSize = 9f) { vm.padHit(pads[i]) }
        }
    }
}

// ----------------------------------------------------------------------
//  Keyboard
// ----------------------------------------------------------------------
@Composable
private fun KeyboardArea(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val l = s.keyLayout
    val whites = vm.whiteCount(l)
    Row(modifier.background(Color(0xFF0A0A0C))) {
        Column(Modifier.width(68.dp).fillMaxHeight().padding(2.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                VerticalWheel("PITCH", s.pitchBend, true, { vm.setPitchBend(it) }, Modifier.weight(1f).fillMaxHeight())
                VerticalWheel("MOD", s.modulation, false, { vm.setModulation(it) }, Modifier.weight(1f).fillMaxHeight())
            }
            PanelButton("SUSTAIN", Modifier.fillMaxWidth().height(24.dp), s.sustain, Pal.gold, fontSize = 7f) { vm.setSustain(!s.sustain) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ScrollStrip(whites, s.visibleWhites, s.startIdx, { vm.setStartIdx(it) }, Modifier.fillMaxWidth().height(12.dp).padding(vertical = 2.dp))
            PianoKeyboard(
                lo = l.lo, hi = l.hi, startIdx = s.startIdx, visible = s.visibleWhites,
                blackWidth = s.blackWidth, blackHeight = s.blackHeight, showLabels = s.showLabels, split = s.splitNote,
                pressed = s.pressed,
                onDown = { n, y -> vm.noteDown(n, y) }, onUp = { n -> vm.noteUp(n) },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}
