package com.example.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.Chords
import com.example.audio.Styles
import com.example.audio.Tone
import com.example.audio.Track
import com.example.audio.Voices
import com.example.viewmodel.ArrangerViewModel
import com.example.viewmodel.KeyLayout
import com.example.viewmodel.Screen
import com.example.viewmodel.UiState

/** Full-screen pages opened inside the console LCD area. Shows nothing when no page is open. */
@Composable
fun LcdPages(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val scr = s.screen ?: return
    val title = when (scr) {
        Screen.VOICE -> "VOICE SELECT"
        Screen.STYLE -> "STYLE SELECT"
        Screen.MIXER -> "SLIDER"
        Screen.PADS -> "MULTI PAD"
        Screen.REGIST -> "REGISTRATION MEMORY"
        Screen.SETTINGS -> "Main Menu  |  Setting"
        Screen.USER -> "USER LIBRARY"
    }
    val isSetting = scr == Screen.SETTINGS
    val headerBrush = if (isSetting) {
        Brush.horizontalGradient(listOf(Color(0xFFE0115F), Color(0xFFB80C4B), Color(0xFFE0115F)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF102446), Color(0xFF1A386B), Color(0xFF102446)))
    }
    val frameBorder = if (isSetting) Color(0xFFE0115F) else Pal.gold.copy(alpha = 0.7f)
    val lcdShape = RoundedCornerShape(6.dp)

    Box(
        modifier
            .clip(lcdShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF090E1A), Color(0xFF04060A))))
            .border(2.dp, frameBorder, lcdShape)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(Modifier.fillMaxSize().padding(4.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(headerBrush)
                    .padding(start = 8.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlagBadge(Modifier.size(width = 18.dp, height = 12.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    color = if (isSetting) Color.White else Pal.goldBright,
                    fontSize = fsp(11f),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSetting) {
                    Text(
                        text = "KEY LEN " + (s.blackHeight * 100).toInt() + "%",
                        color = Pal.goldBright,
                        fontSize = fsp(9f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                PanelButton("EXIT", Modifier.width(72.dp).fillMaxHeight(), fontSize = 9f) { vm.openScreen(null) }
            }
            Spacer(Modifier.height(4.dp))
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
    thumbColor = Pal.goldBright,
    activeTrackColor = Pal.blue,
    inactiveTrackColor = Pal.edge
)

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Pal.text, fontSize = fsp(10f), modifier = Modifier.width(148.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = sliderColors()
        )
        Text(
            text = valueText,
            color = Pal.goldBright,
            fontSize = fsp(10f),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(68.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Pal.gold,
        fontSize = fsp(11f),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

/** P1, P2, P3 ... tab row. Pages hold [per] items each; at least 3 tabs are shown. */
@Composable
private fun PageTabs(
    total: Int,
    per: Int,
    page: Int,
    onPlus: (() -> Unit)? = null,
    onPage: (Int) -> Unit
) {
    val pages = maxOf(3, (total + per - 1) / per)
    Row(Modifier.fillMaxWidth().height(40.dp)) {
        for (i in 0 until pages) {
            PanelButton(
                text = "P" + (i + 1),
                modifier = Modifier.width(56.dp).fillMaxHeight(),
                active = page == i,
                activeColor = Pal.gold,
                fontSize = 11f
            ) { onPage(i) }
        }
        if (onPlus != null) {
            PanelButton("+", Modifier.width(56.dp).fillMaxHeight(), false, Pal.green, fontSize = 16f) { onPlus() }
        }
    }
}

private fun noteName(n: Int): String = Chords.NOTE_NAMES[n % 12] + (n / 12 - 1)

/** Live visual key-length & octave preview strip matching the reference keyboard silhouette. */
@Composable
private fun KeyLengthPreview(blackHeight: Float, blackWidth: Float, showLabels: Boolean) {
    val clampedBlackH = blackHeight.coerceIn(0.40f, 0.80f)
    val clampedBlackW = blackWidth.coerceIn(0.35f, 0.85f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Pal.navyDeep)
            .border(1.dp, Pal.edge, RoundedCornerShape(5.dp))
            .padding(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "KEY LENGTH & PROPORTION PREVIEW",
                color = Pal.goldBright,
                fontSize = fsp(9f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Black Key Height: ${(clampedBlackH * 100).toInt()}%  |  Width: ${(clampedBlackW * 100).toInt()}%",
                color = Pal.silver,
                fontSize = fsp(8.5f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF05070C))
        ) {
            val whiteCount = 14
            val ww = size.width / whiteCount
            val h = size.height
            val bLen = h * clampedBlackH
            val isBlack = listOf(false, true, false, true, false, false, true, false, true, false, true, false)

            for (i in 0 until whiteCount) {
                val x = i * ww
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFFCFDFE), Color(0xFFD5DAE3))),
                    topLeft = Offset(x, 0f),
                    size = Size((ww - 1.5f).coerceAtLeast(1f), h)
                )
                drawRect(
                    color = Color(0x38000000),
                    topLeft = Offset(x + ww - 1.5f, 0f),
                    size = Size(1.5f, h)
                )
            }
            for (i in 0 until whiteCount) {
                val noteMod = i % 12
                if (noteMod + 1 < isBlack.size && isBlack[noteMod + 1]) {
                    val bW = ww * clampedBlackW
                    val bx = (i + 1) * ww - bW / 2f
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF3A4458), Color(0xFF05070C))),
                        topLeft = Offset(bx, 0f),
                        size = Size(bW, bLen)
                    )
                    drawRect(
                        color = Pal.gold.copy(alpha = 0.75f),
                        topLeft = Offset(bx, (bLen - 3f).coerceAtLeast(0f)),
                        size = Size(bW, 3f)
                    )
                }
            }
            if (showLabels) {
                drawRect(
                    color = Pal.blue.copy(alpha = 0.35f),
                    topLeft = Offset(0f, h - 6f),
                    size = Size(size.width, 6f)
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
//  VOICE
// ----------------------------------------------------------------------
@Composable
private fun VoicePage(vm: ArrangerViewModel, s: UiState) {
    val sel = s.selectedPart
    val p = s.parts[sel]
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .width(260.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(Pal.panel)
                .border(1.dp, Pal.edge, RoundedCornerShape(5.dp))
                .padding(6.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.fillMaxWidth().height(42.dp)) {
                for (i in 0..3) {
                    PanelButton(
                        text = s.parts[i].name.replace("RIGHT ", "R"),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        active = sel == i,
                        activeColor = Pal.gold,
                        fontSize = 10f
                    ) { vm.selectPart(i) }
                }
            }
            PanelButton(
                text = if (p.enabled) p.name + " : ON" else p.name + " : OFF",
                modifier = Modifier.fillMaxWidth().height(42.dp),
                active = p.enabled,
                activeColor = Pal.green,
                fontSize = 10f
            ) { vm.togglePart(sel) }
            LabeledSlider("Volume", p.volume, 0f..1f, (p.volume * 100).toInt().toString()) { vm.setPartVolume(sel, it) }
            LabeledSlider(
                label = "Pan",
                value = p.pan,
                range = -1f..1f,
                valueText = if (p.pan < -0.05f) "L" + (-p.pan * 100).toInt() else if (p.pan > 0.05f) "R" + (p.pan * 100).toInt() else "C"
            ) { vm.setPartPan(sel, it) }
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelButton("OCT -", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.setPartOctave(sel, p.octave - 1) }
                Text(
                    text = "Octave " + (if (p.octave > 0) "+" else "") + p.octave,
                    color = Pal.goldBright,
                    fontSize = fsp(10f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1.2f)
                )
                PanelButton("OCT +", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.setPartOctave(sel, p.octave + 1) }
            }
            Text(
                text = "Voices are synthesized in the app (not recorded samples).",
                color = Pal.dim,
                fontSize = fsp(8f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        var page by remember { mutableIntStateOf(0) }
        val per = 8
        Column(Modifier.weight(1f).fillMaxHeight()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val from = page * per
                val to = minOf(Voices.ALL.size, from + per)
                items(maxOf(0, to - from)) { k ->
                    val i = from + k
                    val v = Voices.ALL[i]
                    PanelButton(
                        text = v.name + "\n" + v.category,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        active = p.voiceIdx == i,
                        activeColor = Pal.blue,
                        fontSize = 10f
                    ) { vm.setPartVoice(sel, i) }
                }
            }
            PageTabs(Voices.ALL.size, per, page, { vm.requestImportFiles() }) { page = it }
            if (s.kitName.isNotEmpty()) {
                Text("Drum kit: " + s.kitName, color = Pal.goldBright, fontSize = fsp(8f))
            }
        }
    }
}

// ----------------------------------------------------------------------
//  STYLE
// ----------------------------------------------------------------------
@Composable
private fun StylePage(vm: ArrangerViewModel, s: UiState) {
    val cats = listOf("ALL") + Styles.all.map { it.category }.distinct()
    var cat by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    val idxs = (0 until Styles.all.size).filter { cat == 0 || cat >= cats.size || Styles.all[it].category == cats[cat] }
    val per = 8
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.width(120.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            for (ci in cats.indices) {
                PanelButton(
                    text = cats[ci],
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    active = cat == ci,
                    activeColor = Pal.gold,
                    fontSize = 10f
                ) {
                    cat = ci
                    page = 0
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val from = page * per
                val to = minOf(idxs.size, from + per)
                items(maxOf(0, to - from)) { k ->
                    val i = idxs[from + k]
                    val st = Styles.all[i]
                    PanelButton(
                        text = st.name + "\n" + st.category + "  " + st.tempo + " BPM  " + st.timeSig,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        active = s.styleIdx == i,
                        activeColor = Pal.blue,
                        fontSize = 11f
                    ) { vm.selectStyle(i) }
                }
            }
            PageTabs(idxs.size, per, page, { vm.requestImportFiles() }) { page = it }
            Text(
                text = "+ imports .STY / .style files. Imported styles appear under their category in these pages.",
                color = Pal.dim,
                fontSize = fsp(8f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ----------------------------------------------------------------------
//  MIXER
// ----------------------------------------------------------------------
@Composable
private fun MixerPage(vm: ArrangerViewModel, s: UiState) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(Pal.panel)
                .border(1.dp, Pal.edge, RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("STYLE TRACKS")
            for (i in 0..7) {
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Track.NAMES[i], color = Pal.text, fontSize = fsp(10f), modifier = Modifier.width(76.dp))
                    Slider(
                        value = s.trackVol[i],
                        onValueChange = { vm.setTrackVol(i, it) },
                        modifier = Modifier.weight(1f),
                        colors = sliderColors()
                    )
                    PanelButton(
                        text = "MUTE",
                        modifier = Modifier.width(56.dp).fillMaxHeight(0.8f),
                        active = s.trackMute[i],
                        activeColor = Pal.red,
                        fontSize = 9f
                    ) { vm.toggleTrackMute(i) }
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(Pal.panel)
                .border(1.dp, Pal.edge, RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("KEYBOARD PARTS")
            for (i in 0..3) {
                val p = s.parts[i]
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = Pal.text, fontSize = fsp(10f), modifier = Modifier.width(76.dp))
                    Slider(
                        value = p.volume,
                        onValueChange = { vm.setPartVolume(i, it) },
                        modifier = Modifier.weight(1f),
                        colors = sliderColors()
                    )
                    PanelButton(
                        text = if (p.enabled) "ON" else "OFF",
                        modifier = Modifier.width(56.dp).fillMaxHeight(0.8f),
                        active = p.enabled,
                        activeColor = Pal.green,
                        fontSize = 9f
                    ) { vm.togglePart(i) }
                }
            }
            SectionTitle("MASTER")
            LabeledSlider("Master volume", s.masterVol, 0f..1f, (s.masterVol * 100).toInt().toString()) { vm.setMaster(it) }
            LabeledSlider("Style volume", s.styleVol, 0f..1f, (s.styleVol * 100).toInt().toString()) { vm.setStyleVol(it) }
            LabeledSlider("Reverb", s.reverb, 0f..0.6f, (s.reverb / 0.6f * 100).toInt().toString()) { vm.setReverb(it) }
        }
    }
}

// ----------------------------------------------------------------------
//  MULTI PADS
// ----------------------------------------------------------------------
private val PADS = listOf(
    "SHAKER" to 82, "KICK" to 36, "SNARE" to 38, "TAMBOURINE" to 54,
    "CLAP" to 39, "TOM" to 45, "CONGA" to 62, "COWBELL" to 56
)

@Composable
private fun PadsPage(vm: ArrangerViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(PADS.size) { i ->
            PanelButton(
                text = PADS[i].first,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                active = false,
                activeColor = Pal.blue,
                fontSize = 12f
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
            text = "Each slot remembers the style, tempo, main section and the four keyboard parts (voice, on/off, volume, octave).",
            color = Pal.dim,
            fontSize = fsp(9f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(8) { i ->
                val txt = s.regs[i]
                val styleName = if (txt.isEmpty()) "Empty"
                else Styles.all.getOrNull(txt.split(',')[0].toIntOrNull() ?: -1)?.name ?: "Saved"
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .background(Pal.panel, RoundedCornerShape(5.dp))
                        .border(1.dp, if (s.activeReg == i) Pal.blue else Pal.edge, RoundedCornerShape(5.dp))
                        .padding(6.dp)
                ) {
                    Text("REGIST " + (i + 1), color = Pal.goldBright, fontSize = fsp(11f), fontWeight = FontWeight.Bold)
                    Text(
                        text = styleName,
                        color = if (txt.isEmpty()) Pal.dim else Pal.text,
                        fontSize = fsp(10f),
                        modifier = Modifier.weight(1f)
                    )
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
//  SETTINGS (keyboard size, key length, black keys, arranger, audio)
// ----------------------------------------------------------------------
@Composable
private fun SettingsPage(vm: ArrangerViewModel, s: UiState) {
    val whites = vm.whiteCount(s.keyLayout)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        KeyLengthPreview(
            blackHeight = s.blackHeight,
            blackWidth = s.blackWidth,
            showLabels = s.showLabels
        )

        SectionTitle("KEYBOARD - NUMBER OF KEYS")
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            for (l in KeyLayout.entries) {
                PanelButton(
                    text = l.label,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    active = s.keyLayout == l,
                    activeColor = Pal.gold,
                    fontSize = 11f
                ) { vm.setKeyLayout(l) }
            }
        }

        SectionTitle("KEYBOARD - KEY LENGTH & SIZE")
        LabeledSlider(
            label = "Keyboard height",
            value = s.keyHeight,
            range = 0.35f..0.75f,
            valueText = (s.keyHeight * 100).toInt().toString() + "%"
        ) { vm.setKeyHeight(it) }
        LabeledSlider(
            label = "Black key length",
            value = s.blackHeight,
            range = 0.40f..0.80f,
            valueText = (s.blackHeight * 100).toInt().toString() + "%"
        ) { vm.setBlackHeight(it) }
        LabeledSlider(
            label = "Black key width",
            value = s.blackWidth,
            range = 0.35f..0.85f,
            valueText = (s.blackWidth * 100).toInt().toString() + "%"
        ) { vm.setBlackWidth(it) }
        LabeledSlider(
            label = "Keys on screen (white)",
            value = s.visibleWhites.toFloat(),
            range = 8f..whites.toFloat(),
            valueText = s.visibleWhites.toString()
        ) { vm.setVisibleWhites(it.toInt()) }
        LabeledSlider(
            label = "Split point (LEFT | RIGHT)",
            value = s.splitNote.toFloat(),
            range = 40f..72f,
            valueText = noteName(s.splitNote)
        ) { vm.setSplit(it.toInt()) }

        Row(Modifier.fillMaxWidth().height(42.dp)) {
            PanelButton(
                text = if (s.showLabels) "KEY LABELS: ON" else "KEY LABELS: OFF",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                active = s.showLabels,
                activeColor = Pal.blue,
                fontSize = 10f
            ) { vm.setShowLabels(!s.showLabels) }
            PanelButton(
                text = if (s.fixedVelocity) "VELOCITY: FIXED" else "VELOCITY: TOUCH POSITION",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                active = !s.fixedVelocity,
                activeColor = Pal.blue,
                fontSize = 10f
            ) { vm.setFixedVelocity(!s.fixedVelocity) }
        }
        Text(
            text = "Touch position: pressing lower on a key plays louder. Drag the gold bar above the keys to scroll along the keyboard.",
            color = Pal.dim,
            fontSize = fsp(8f),
            modifier = Modifier.padding(top = 4.dp)
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
            text = "Yamaha .STY (and any MIDI style with a CASM chunk) and Music Studio Rhythm .Style files are imported and played with the app's own voices. " +
                "The sounds stored inside a .Style file are not played yet, and the time signature is assumed to be 4/4. " +
                "Other formats (.set, .template, .music) are read and reported only; the original file is kept unchanged.",
            color = Pal.dim,
            fontSize = fsp(8f),
            modifier = Modifier.padding(top = 4.dp)
        )

        SectionTitle("AUDIO")
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            for (i in 0 until Tone.COUNT) {
                PanelButton(
                    text = "TONE: " + Tone.NAMES[i],
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    active = s.tone == i,
                    activeColor = Pal.gold,
                    fontSize = 10f
                ) { vm.setTone(i) }
            }
        }
        Text(
            text = "Master tone is an equalizer on the final mix. CLEAR cuts low-end mud and adds presence and air, WARM does the opposite, FLAT turns it off.",
            color = Pal.dim,
            fontSize = fsp(8f),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            for (f in listOf(128, 256, 512)) {
                PanelButton(
                    text = "BUFFER " + f,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    active = s.bufferFrames == f,
                    activeColor = Pal.gold,
                    fontSize = 10f
                ) { vm.setBuffer(f) }
            }
        }
        Text(
            text = "Smaller buffer = lower delay but more risk of crackle. If you hear crackles, choose 512. " +
                "Status: " + (if (s.audioOk) "audio running" else "audio stopped") +
                ", dropouts (underruns): " + s.underruns + ", voices sounding: " + s.voices + " of 80.",
            color = Pal.text,
            fontSize = fsp(9f),
            modifier = Modifier.padding(top = 4.dp)
        )

        SectionTitle("ABOUT")
        Text(
            text = "ETHIO YAMAHA is an independently developed arranger keyboard inspired by the performance " +
                "experience of professional arranger workstations. It is not official Yamaha software. " +
                "Sounds are synthesized inside the app; real sampled instruments and SoundFont support are planned for a later stage.",
            color = Pal.dim,
            fontSize = fsp(9f),
            modifier = Modifier.padding(bottom = 20.dp)
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
        Row(
            Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelButton("ADD FOLDER", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.requestAddFolder() }
            PanelButton("IMPORT FILES", Modifier.weight(1f).fillMaxHeight(), fontSize = 10f) { vm.requestImportFiles() }
            PanelButton("UP", Modifier.weight(0.6f).fillMaxHeight(), u.tree.isNotEmpty(), Pal.orange, fontSize = 10f) { vm.userUp() }
            PanelButton("TOP", Modifier.weight(0.6f).fillMaxHeight(), fontSize = 10f) { vm.userTop() }
        }
        Text(
            text = if (u.tree.isEmpty()) "USER  >  folders" else "USER  >  " + u.titles.joinToString("  >  "),
            color = Pal.goldBright,
            fontSize = fsp(10f),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(u.entries.size) { i ->
                val e = u.entries[i]
                val tag = if (e.isDir) "[ DIR ]" else "[ " + e.kind.label + " ]"
                PanelButton(
                    text = tag + "   " + e.name,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    active = !e.isDir && e.kind.supported,
                    activeColor = if (e.isDir) Pal.gold else Pal.blue,
                    fontSize = 11f
                ) { if (e.isDir) vm.userOpen(e) else vm.userPick(e) }
            }
        }
        Text(
            text = (if (u.busy) "Working...  " else "") + u.message,
            color = if (u.busy) Pal.orange else Pal.dim,
            fontSize = fsp(9f),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 110.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp)
        )
    }
}
