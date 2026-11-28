package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.Sec
import com.example.audio.Styles
import com.example.audio.Track
import com.example.audio.Voices
import com.example.viewmodel.ArrangerViewModel
import com.example.viewmodel.Screen
import com.example.viewmodel.UiState

/**
 * Main hardware-inspired arranger console for ETHIO YAMAHA.
 * Preserves all ViewModel callbacks and features while implementing the workstation chassis,
 * three consecutive LCD views (Main Overview, Style/Rhythm with P1.. tabs, Voice/Instrument/Drum with P1.. tabs),
 * style-control strip, registration memory, multi pads, and wide playable piano keyboard.
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

private fun bodyBrush(): Brush = Brush.verticalGradient(
    listOf(Color(0xFF1A2334), Color(0xFF0C1018), Color(0xFF06070A))
)

private fun panelBoxBrush(): Brush = Brush.verticalGradient(
    listOf(Pal.panel2, Pal.panel, Pal.navyDeep)
)

// ----------------------------------------------------------------------
//  Upper panel: speakers | left controls | 3-panel LCD + OTS row | right controls
// ----------------------------------------------------------------------
@Composable
private fun PanelArea(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    Row(
        modifier.background(bodyBrush()).padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Speaker(Modifier.weight(0.5f).fillMaxHeight())
        // The side panels and the LCD never change size or disappear: only the text and pages inside the LCD change.
        LeftControls(vm, s, Modifier.weight(1.6f).fillMaxHeight())
        Column(Modifier.weight(3.2f).fillMaxHeight()) {
            val lcdShape = RoundedCornerShape(6.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(lcdShape)
                    .background(Color(0xFF04060A))
                    .border(1.5.dp, Pal.gold.copy(alpha = 0.65f), lcdShape)
                    .padding(2.dp)
            ) {
                if (s.screen == null) MainPageLcd(vm, s, Modifier.fillMaxSize())
                else LcdPages(vm, s, Modifier.fillMaxSize())
            }
            OtsRow(vm, s, Modifier.fillMaxWidth().height(25.dp * LocalScale.current))
        }
        RightControls(vm, s, Modifier.weight(1.85f).fillMaxHeight())
        Speaker(Modifier.weight(0.5f).fillMaxHeight())
    }
}

/** Speaker grille with concentric dark rings, gold trim ring, and central cone. */
@Composable
private fun Speaker(modifier: Modifier) {
    Canvas(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF131D30), Color(0xFF06080D))))
            .border(1.dp, Pal.edge, RoundedCornerShape(6.dp))
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = minOf(size.width, size.height) * 0.45f
        drawCircle(Color(0xFF070A10), r, c)
        var k = 0
        while (k < 7) {
            drawCircle(
                color = if (k == 0) Pal.gold.copy(alpha = 0.45f) else Color(0xFF283650),
                radius = r * (1f - k * 0.12f),
                center = c,
                style = Stroke(width = 1.5f)
            )
            k++
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF394966), Color(0xFF0B101B)),
                center = c,
                radius = r * 0.3f
            ),
            radius = r * 0.3f,
            center = c
        )
    }
}

@Composable
private fun LeftControls(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val part = s.parts[s.selectedPart]
    Column(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(panelBoxBrush())
            .border(1.dp, Pal.edge, RoundedCornerShape(5.dp))
            .padding(3.dp)
    ) {
        // Master volume + 8 accompaniment style tracks
        Row(Modifier.fillMaxWidth().weight(1.55f)) {
            Knob("MASTER", s.masterVol, { vm.setMaster(it) }, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(2.4f).fillMaxHeight()) {
                Text(
                    "STYLE TRACKS (8)",
                    color = Pal.gold,
                    fontSize = fsp(6f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (i in 0..3) TrackBtn(vm, s, i, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (i in 4..7) TrackBtn(vm, s, i, Modifier.weight(1f))
                }
            }
        }
        // Balance + Tempo / Octave / Transpose
        Row(Modifier.fillMaxWidth().weight(1.45f)) {
            Knob("BALANCE", s.styleVol, { vm.setStyleVol(it) }, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(2.4f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Group("TEMPO " + s.tempo, 1f) {
                        HwButton("-", Modifier.weight(1f).fillMaxHeight()) { vm.nudgeTempo(-1) }
                        HwButton("+", Modifier.weight(1f).fillMaxHeight()) { vm.nudgeTempo(1) }
                    }
                    Group("OCT " + (if (part.octave > 0) "+" else "") + part.octave, 1f) {
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
        // Reverb + Auto Fill / Fade / Sync Start / Sync Stop
        Row(Modifier.fillMaxWidth().weight(1.35f)) {
            Knob("REVERB", s.reverb, { vm.setReverb(it) }, Modifier.weight(1f).fillMaxHeight())
            Row(Modifier.weight(2.4f).fillMaxHeight()) {
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
    val shortLabel = listOf("RHY 1", "RHY 2", "BASS", "CHD 1", "CHD 2", "PAD", "PHR 1", "PHR 2")[i]
    HwButton(shortLabel, modifier.fillMaxHeight(), !s.trackMute[i], Pal.green) { vm.toggleTrackMute(i) }
}

// ----------------------------------------------------------------------
//  MAIN PAGE of the LCD (the LCD itself has a fixed size; pages open inside it, see Screens.kt)
// ----------------------------------------------------------------------
@Composable
private fun LcdCell(
    modifier: Modifier,
    selected: Boolean = false,
    color: Color = Color(0xFF0D1B33),
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val cb by rememberUpdatedState(onClick)
    var down by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (down) 0.97f else 1f, tween(80), label = "cellPress")
    Box(
        modifier
            .padding(1.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) Brush.horizontalGradient(listOf(Color(0xFF1D63B8), Color(0xFF10376B))) else Brush.horizontalGradient(listOf(color, Color(0xFF080F1E))))
            .border(0.8.dp, if (selected) Pal.goldBright else Pal.edge, RoundedCornerShape(3.dp))
            .pointerInput(Unit) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { cb() }) }
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}

/** Main page: style box on the left, the four keyboard parts on the right. Tapping a cell opens its page inside this same LCD. */
@Composable
private fun MainPageLcd(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val st = Styles.all.getOrNull(s.styleIdx)
    val sec = Sec.values()[if (s.running) s.playingSection else s.targetMain]
    Column(modifier.background(Brush.verticalGradient(listOf(Color(0xFF0C182E), Color(0xFF070E1C))))) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(15.dp * LocalScale.current)
                .background(Brush.horizontalGradient(listOf(Color(0xFF102446), Color(0xFF1A386B), Color(0xFF102446))))
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlagBadge(Modifier.size(width = 14.dp, height = 9.dp))
            Text(
                "  MAIN PAGE", color = Pal.goldBright, fontSize = fsp(7.5f), fontWeight = FontWeight.ExtraBold,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            val pulse by animateFloatAsState(if (s.running && s.beat % 2 == 1) 1f else 0.25f, tween(140), label = "beatPulse")
            Box(Modifier.padding(end = 4.dp).size(6.dp).alpha(pulse).clip(CircleShape).background(Pal.blueGlow))
            Text(sec.label + "   " + s.chordText, color = Pal.goldBright, fontSize = fsp(7f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                LcdCell(Modifier.weight(2f).fillMaxWidth(), color = Color(0xFF3A1414), onClick = { vm.openScreen(Screen.STYLE) }) {
                    Column {
                        Text(st?.name ?: "Style", color = Pal.text, fontSize = fsp(8f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("tempo " + s.tempo + " bpm", color = Pal.silver, fontSize = fsp(6.5f), maxLines = 1)
                        Text("meter " + s.timeSig + "   " + (st?.category ?: ""), color = Pal.silver, fontSize = fsp(6.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                LcdCell(Modifier.weight(1f).fillMaxWidth(), onClick = { vm.openScreen(Screen.REGIST) }) {
                    Column {
                        Text("REGIST " + (s.activeReg + 1), color = Pal.green, fontSize = fsp(7.5f), fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Registration Memory", color = Pal.dim, fontSize = fsp(6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                LcdCell(Modifier.weight(1f).fillMaxWidth(), onClick = { vm.openUserPage() }) {
                    Column {
                        Text("User Library", color = Pal.blueGlow, fontSize = fsp(7.5f), fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("styles, kits, folders", color = Pal.dim, fontSize = fsp(6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.weight(1.7f).fillMaxHeight()) {
                for (i in 0..3) {
                    val p = s.parts[i]
                    val vName = Voices.ALL.getOrNull(p.voiceIdx)?.name ?: "Voice"
                    LcdCell(
                        Modifier.weight(1f).fillMaxWidth(), selected = s.selectedPart == i,
                        onClick = { vm.selectPart(i); vm.openScreen(Screen.VOICE) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    vName, color = if (p.enabled) Color(0xFF8FD0FF) else Pal.dim, fontSize = fsp(8f), fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    p.name.replace("RIGHT ", "R") + "   octave " + p.octave + "   vol " + (p.volume * 100).toInt(),
                                    color = Pal.silver, fontSize = fsp(6.3f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                Modifier
                                    .padding(start = 3.dp)
                                    .width(5.dp)
                                    .fillMaxHeight(0.8f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (p.enabled) Pal.green else Color(0xFF2A3347))
                                    .pointerInput(i) { detectTapGestures { vm.togglePart(i) } }
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = (if (s.running) "RUN  " else "STOP  ") + s.tempo + " BPM (" + s.timeSig + ")  " + String.format("%03d.%d", s.measure, s.beat) +
                "  " + (if (s.audioOk) "AUDIO OK" else "AUDIO OFF") + "  V" + s.voices + "  " + s.banner,
            color = if (s.audioOk && s.underruns == 0) Pal.green else Pal.orange,
            fontSize = fsp(6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().background(Pal.navyDeep).padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun OtsRow(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    Row(modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "ONE TOUCH\nSETTING",
            color = Pal.gold,
            fontSize = fsp(6f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(46.dp)
        )
        for (i in 0..3) {
            PanelButton(
                text = (i + 1).toString(),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                active = s.activeOts == i,
                activeColor = Pal.blue,
                fontSize = 9f
            ) { vm.applyOts(i) }
        }
    }
}

// ----------------------------------------------------------------------
//  Right panel: Menu/Exit, DataWheel, Tempo nudge, Part/Screen/Acmp/Chord keys
// ----------------------------------------------------------------------
@Composable
private fun RightControls(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(panelBoxBrush())
            .border(1.dp, Pal.edge, RoundedCornerShape(5.dp))
            .padding(3.dp)
    ) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1.1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().weight(0.55f)) {
                    HwButton("MENU", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.SETTINGS) { vm.openScreen(Screen.SETTINGS) }
                    HwButton("EXIT", Modifier.weight(1f).fillMaxHeight()) { vm.openScreen(null) }
                }
                DataWheel({ vm.nudgeTempo(it) }, Modifier.fillMaxWidth().weight(1.5f).padding(2.dp))
                Row(Modifier.fillMaxWidth().weight(0.5f), verticalAlignment = Alignment.CenterVertically) {
                    HwButton("<", Modifier.weight(1f).fillMaxHeight()) { vm.nudgeTempo(-1) }
                    HwButton(">", Modifier.weight(1f).fillMaxHeight()) { vm.nudgeTempo(1) }
                }
                Text(
                    "TEMPO " + s.tempo,
                    color = Pal.gold,
                    fontSize = fsp(6f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(Modifier.weight(1.95f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    HwButton("STYLE", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.STYLE) { vm.openScreen(Screen.STYLE) }
                    HwButton("VOICE", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.VOICE) { vm.openScreen(Screen.VOICE) }
                    HwButton("SLIDER", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.MIXER) { vm.openScreen(Screen.MIXER) }
                    HwButton("PADS", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.PADS) { vm.openScreen(Screen.PADS) }
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    HwButton("REGIST", Modifier.weight(1f).fillMaxHeight(), s.screen == Screen.REGIST) { vm.openScreen(Screen.REGIST) }
                    HwButton("ACMP", Modifier.weight(1f).fillMaxHeight(), s.acmp, Pal.green) { vm.toggleAcmp() }
                    HwButton("TAP", Modifier.weight(1f).fillMaxHeight()) { vm.tapTempo() }
                    HwButton("RST TMP", Modifier.weight(1f).fillMaxHeight()) { vm.resetTempo() }
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    HwButton("CH AUTO", Modifier.weight(1f).fillMaxHeight(), s.chordKeys == 0, Pal.green) { vm.setChordKeys(0) }
                    HwButton("1 KEY", Modifier.weight(1f).fillMaxHeight(), s.chordKeys == 1, Pal.green) { vm.setChordKeys(1) }
                    HwButton("2 KEY", Modifier.weight(1f).fillMaxHeight(), s.chordKeys == 2, Pal.green) { vm.setChordKeys(2) }
                    HwButton("3 KEY", Modifier.weight(1f).fillMaxHeight(), s.chordKeys == 3, Pal.green) { vm.setChordKeys(3) }
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    val partNames = listOf("R1", "R2", "R3", "LEFT")
                    for (pi in 0..3) {
                        val p = s.parts[pi]
                        HwButton(
                            label = partNames[pi],
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            active = s.selectedPart == pi || p.enabled,
                            activeColor = if (s.selectedPart == pi) Pal.gold else Pal.blue
                        ) { vm.selectPart(pi) }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
//  Style-control strip: Intro, Variation, Break, Start, Fill, Ending,
//  Registration Memory, Multi Pads
// ----------------------------------------------------------------------
@Composable
private fun SecButton(vm: ArrangerViewModel, s: UiState, sec: Sec, label: String, modifier: Modifier) {
    val playing = s.running && s.playingSection == sec.ordinal
    val target = sec.isMain && s.targetMain == sec.ordinal
    val armed = s.armedIntro == sec.ordinal
    PanelButton(
        text = label,
        modifier = modifier.fillMaxHeight(),
        active = playing || target || armed,
        activeColor = if (playing) Pal.orange else Pal.blue,
        fontSize = 9f
    ) { vm.pressSection(sec) }
}

@Composable
private fun RowScope.Group(title: String, weight: Float, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.weight(weight).fillMaxHeight().padding(horizontal = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Pal.edge))
            Text(
                text = " $title ",
                color = Pal.gold,
                fontSize = fsp(6.2f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Box(Modifier.weight(1f).height(1.dp).background(Pal.edge))
        }
        Row(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

@Composable
private fun StripRow(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    Row(
        modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF141E30), Color(0xFF080B12))))
            .border(0.5.dp, Pal.edge, RoundedCornerShape(0.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
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
                    text = (i + 1).toString(),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    active = s.activeReg == i,
                    activeColor = if (s.memoryArmed) Pal.gold else Pal.blue,
                    textColor = if (stored) Pal.goldBright else null,
                    fontSize = 9f
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
//  Pitch / Mod wheels + Sustain + Wide Playable Keyboard
// ----------------------------------------------------------------------
@Composable
private fun KeyboardArea(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val l = s.keyLayout
    val whites = vm.whiteCount(l)
    Row(modifier.background(Color(0xFF06080D))) {
        Column(
            Modifier
                .width(68.dp)
                .fillMaxHeight()
                .background(Pal.navyDeep)
                .border(0.5.dp, Pal.edge)
                .padding(2.dp)
        ) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                VerticalWheel("PITCH", s.pitchBend, true, { vm.setPitchBend(it) }, Modifier.weight(1f).fillMaxHeight())
                VerticalWheel("MOD", s.modulation, false, { vm.setModulation(it) }, Modifier.weight(1f).fillMaxHeight())
            }
            PanelButton("SUSTAIN", Modifier.fillMaxWidth().height(24.dp), s.sustain, Pal.gold, fontSize = 7f) { vm.setSustain(!s.sustain) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ScrollStrip(
                total = whites,
                visible = s.visibleWhites,
                start = s.startIdx,
                onStart = { vm.setStartIdx(it) },
                modifier = Modifier.fillMaxWidth().height(12.dp).padding(vertical = 2.dp)
            )
            PianoKeyboard(
                lo = l.lo,
                hi = l.hi,
                startIdx = s.startIdx,
                visible = s.visibleWhites,
                blackWidth = s.blackWidth,
                blackHeight = s.blackHeight,
                showLabels = s.showLabels,
                split = s.splitNote,
                pressed = s.pressed,
                onDown = { n, y -> vm.noteDown(n, y) },
                onUp = { n -> vm.noteUp(n) },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}
