package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
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

/** Pages that open INSIDE the fixed-size LCD. The LCD never grows: only its content changes. */
@Composable
fun LcdPages(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val scr = s.screen ?: return
    Box(modifier.pointerInput(Unit) { detectTapGestures { } }) {
        when (scr) {
            Screen.STYLE -> StyleBrowser(vm, s, Modifier.fillMaxSize())
            Screen.VOICE -> VoiceBrowser(vm, s, Modifier.fillMaxSize())
            Screen.SETTINGS -> MenuLcd(vm, s, Modifier.fillMaxSize())
            else -> {
                val title = when (scr) {
                    Screen.MIXER -> "SLIDER"
                    Screen.PADS -> "MULTI PAD"
                    Screen.REGIST -> "REGISTRATION MEMORY"
                    else -> "USER LIBRARY"
                }
                PageFrame(title, Color(0xFF1A386B), Pal.goldBright, { vm.openScreen(null) }, Modifier.fillMaxSize()) {
                    when (scr) {
                        Screen.MIXER -> MixerPage(vm, s)
                        Screen.PADS -> PadsPage(vm)
                        Screen.REGIST -> RegistPage(vm, s)
                        else -> UserPage(vm, s)
                    }
                }
            }
        }
    }
}

/** Title bar + EXIT + content. Used by the smaller pages (slider, pads, registration, user library, settings). */
@Composable
private fun PageFrame(
    title: String,
    barColor: Color,
    titleColor: Color,
    onExit: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier.background(Brush.verticalGradient(listOf(Color(0xFF090E1A), Color(0xFF04060A))))) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(20.dp * LocalScale.current)
                .background(Brush.horizontalGradient(listOf(barColor, barColor.copy(alpha = 0.8f), barColor)))
                .padding(start = 6.dp, end = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlagBadge(Modifier.size(width = 14.dp, height = 9.dp))
            Text(
                text = "  " + title, color = titleColor, fontSize = fsp(8f), fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            PanelButton("EXIT", Modifier.width(48.dp).fillMaxHeight(), fontSize = 7f) { onExit() }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(3.dp)) { content() }
    }
}

// ----------------------------------------------------------------------
//  Browser in the LCD: category column | 2 x 4 items | navigation column, page tabs P1.. under the items.
//  Same size as the main page, whatever the number of items.
// ----------------------------------------------------------------------
private class BrowserItem(val title: String, val sub: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
private fun BrowserCell(modifier: Modifier, item: BrowserItem?, accent: Color, cellColor: Color) {
    if (item == null) { Box(modifier.padding(1.dp)); return }
    val cb by rememberUpdatedState(item.onClick)
    var down by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (down) 0.96f else 1f, tween(80), label = "browserPress")
    Column(
        modifier
            .padding(1.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(3.dp))
            .background(if (item.selected) Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.55f))) else Brush.horizontalGradient(listOf(cellColor, Color(0xFF080F1E))))
            .border(0.8.dp, if (item.selected) Pal.goldBright else Pal.edge, RoundedCornerShape(3.dp))
            .pointerInput(Unit) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { cb() }) }
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(item.title, color = if (item.selected) Color.White else Pal.text, fontSize = fsp(7.5f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.sub, color = if (item.selected) Pal.goldBright else Pal.dim, fontSize = fsp(6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BrowserLcd(
    info: String,
    cats: List<String>,
    cat: Int,
    onCat: (Int) -> Unit,
    items: List<BrowserItem>,
    page: Int,
    onPage: (Int) -> Unit,
    accent: Color,
    cellColor: Color,
    onBack: () -> Unit,
    onGear: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier
) {
    val per = 8
    val pages = maxOf(1, (items.size + per - 1) / per)
    val cur = page.coerceIn(0, pages - 1)
    val first = (cur / 5) * 5
    Row(modifier.background(Brush.verticalGradient(listOf(Color(0xFF0E1B33), Color(0xFF070D1A))))) {
        Column(Modifier.width(54.dp * LocalScale.current).fillMaxHeight().verticalScroll(rememberScrollState())) {
            for (ci in cats.indices) {
                val on = ci == cat
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp * LocalScale.current)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (on) accent else Color(0xFF13223D))
                        .pointerInput(ci) { detectTapGestures { onCat(ci) } }
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(cats[ci], color = if (on) Color.White else Pal.silver, fontSize = fsp(6.8f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                text = info + "   " + (cur + 1) + "/" + pages + "   (" + items.size + ")",
                color = Pal.goldBright, fontSize = fsp(6.3f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 3.dp)
            )
            for (r in 0..3) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0..1) {
                        BrowserCell(Modifier.weight(1f).fillMaxHeight(), items.getOrNull(cur * per + r * 2 + c), accent, cellColor)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(18.dp * LocalScale.current)) {
                for (pi in first until minOf(pages, first + 5)) {
                    PanelButton("P" + (pi + 1), Modifier.weight(1f).fillMaxHeight(), pi == cur, Pal.gold, fontSize = 7.5f) { onPage(pi) }
                }
            }
        }
        Column(Modifier.width(34.dp * LocalScale.current).fillMaxHeight()) {
            PanelButton("BACK", Modifier.weight(1f).fillMaxWidth(), fontSize = 6.5f) { onBack() }
            PanelButton("MENU", Modifier.weight(1f).fillMaxWidth(), fontSize = 6.5f) { onGear() }
            PanelButton("UP", Modifier.weight(1f).fillMaxWidth(), fontSize = 7f) { onPage(maxOf(0, cur - 1)) }
            PanelButton("DOWN", Modifier.weight(1f).fillMaxWidth(), fontSize = 6.5f) { onPage(minOf(pages - 1, cur + 1)) }
            PanelButton("+", Modifier.weight(1f).fillMaxWidth(), false, Pal.green, fontSize = 12f) { onPlus() }
        }
    }
}

@Composable
private fun StyleBrowser(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val all = Styles.all
    val cats = listOf("ALL") + all.map { it.category }.distinct()
    var cat by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    val safeCat = if (cat < cats.size) cat else 0
    val idxs = all.indices.filter { safeCat == 0 || all[it].category == cats[safeCat] }
    val items = idxs.map { i ->
        BrowserItem(all[i].name, all[i].category + "  " + all[i].tempo + " bpm", s.styleIdx == i) { vm.selectStyle(i) }
    }
    BrowserLcd(
        "STYLE", cats, safeCat, { cat = it; page = 0 }, items, page, { page = it },
        Pal.blue, Color(0xFF3A1414), { vm.openScreen(null) }, { vm.openScreen(Screen.SETTINGS) }, { vm.requestImportFiles() }, modifier
    )
}

@Composable
private fun VoiceBrowser(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    val all = Voices.ALL
    val cats = listOf("ALL") + all.map { it.category }.distinct()
    var cat by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    val safeCat = if (cat < cats.size) cat else 0
    val sel = s.selectedPart
    val cur = s.parts[sel].voiceIdx
    val idxs = all.indices.filter { safeCat == 0 || all[it].category == cats[safeCat] }
    val items = idxs.map { i -> BrowserItem(all[i].name, all[i].category, cur == i) { vm.setPartVoice(sel, i) } }
    BrowserLcd(
        "VOICE " + s.parts[sel].name.replace("RIGHT ", "R") + (if (s.kitName.isNotEmpty()) "  kit: " + s.kitName else ""),
        cats, safeCat, { cat = it; page = 0 }, items, page, { page = it },
        Pal.blue, Color(0xFF0E2447), { vm.openScreen(null) }, { vm.openScreen(Screen.SETTINGS) }, { vm.requestImportFiles() }, modifier
    )
}

// ----------------------------------------------------------------------
//  MAIN MENU (2 pages of 8 entries, pink title bar) and SETTING inside it
// ----------------------------------------------------------------------
private class MenuEntry(val title: String, val sub: String, val color: Color, val active: Boolean, val action: () -> Unit)

@Composable
private fun MenuLcd(vm: ArrangerViewModel, s: UiState, modifier: Modifier) {
    var inSetting by remember { mutableStateOf(false) }
    var mpage by remember { mutableIntStateOf(0) }
    val pink = Color(0xFFD01457)
    if (inSetting) {
        PageFrame("Main Menu  |  Setting", pink, Color.White, { inSetting = false }, modifier) { SettingsPage(vm, s) }
        return
    }
    val blue = Color(0xFF1B3F7A)
    val page1 = listOf(
        MenuEntry("Style", "Choose a style", blue, false) { vm.openScreen(Screen.STYLE) },
        MenuEntry("Voice", "Choose voices", blue, false) { vm.openScreen(Screen.VOICE) },
        MenuEntry("Slider", "Volumes and mute", blue, false) { vm.openScreen(Screen.MIXER) },
        MenuEntry("Multi Pad", "Drum and rhythm pads", blue, false) { vm.openScreen(Screen.PADS) },
        MenuEntry("Registration", "8 memories", blue, false) { vm.openScreen(Screen.REGIST) },
        MenuEntry("User Library", "Folders and files", Color(0xFF14543A), false) { vm.openUserPage() },
        MenuEntry("Import Files", "+ .STY / .Style / kit", Color(0xFF14543A), false) { vm.requestImportFiles() },
        MenuEntry("Setting", "App preferences", Color(0xFF7A4A12), false) { inSetting = true }
    )
    val page2 = listOf(
        MenuEntry("Add Folder", "Folder of styles", Color(0xFF14543A), false) { vm.requestAddFolder() },
        MenuEntry("Tap Tempo", "Tap to set tempo", blue, false) { vm.tapTempo() },
        MenuEntry("Reset Tempo", "Style tempo", blue, false) { vm.resetTempo() },
        MenuEntry("Panel Lock", if (s.panelLock) "ON" else "OFF", Color(0xFF7A4A12), s.panelLock) { vm.toggleLock() },
        MenuEntry("Auto Fill", if (s.autoFill) "ON" else "OFF", blue, s.autoFill) { vm.toggleAutoFill() },
        MenuEntry("Fade", if (s.fadeActive) "ON" else "OFF", blue, s.fadeActive) { vm.toggleFade() },
        MenuEntry("Sync Start", if (s.syncStart) "ON" else "OFF", blue, s.syncStart) { vm.toggleSyncStart() },
        MenuEntry("Sync Stop", if (s.syncStop) "ON" else "OFF", blue, s.syncStop) { vm.toggleSyncStop() }
    )
    val list = if (mpage == 0) page1 else page2
    Column(modifier.background(Color(0xFF05080F))) {
        Row(
            Modifier.fillMaxWidth().height(17.dp * LocalScale.current)
                .background(Brush.horizontalGradient(listOf(pink, Color(0xFFB00F48), pink))).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Main Menu  " + (mpage + 1) + "/2", color = Color.White, fontSize = fsp(8f), fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
            Text(s.chordText, color = Color.White, fontSize = fsp(7f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                for (r in 0..3) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0..1) {
                            val e = list[r * 2 + c]
                            BrowserCell(Modifier.weight(1f).fillMaxHeight(), BrowserItem(e.title, e.sub, e.active) { e.action() }, e.color, e.color.copy(alpha = 0.55f))
                        }
                    }
                }
            }
            Column(Modifier.width(34.dp * LocalScale.current).fillMaxHeight()) {
                PanelButton("BACK", Modifier.weight(1f).fillMaxWidth(), fontSize = 6.5f) { vm.openScreen(null) }
                PanelButton("UP", Modifier.weight(1f).fillMaxWidth(), fontSize = 7f) { mpage = 0 }
                PanelButton("DOWN", Modifier.weight(1f).fillMaxWidth(), fontSize = 6.5f) { mpage = 1 }
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
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Pal.text, fontSize = fsp(10f), modifier = Modifier.width(104.dp))
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
            modifier = Modifier.width(52.dp)
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
