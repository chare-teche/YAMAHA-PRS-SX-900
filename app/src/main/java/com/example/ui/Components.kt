package com.example.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Colour palette: dark graphite, black panels, champagne gold, electric blue. */
object Pal {
    val bg = Color(0xFF08090B)
    val panel = Color(0xFF14161A)
    val panel2 = Color(0xFF1C1F24)
    val edge = Color(0xFF353A42)
    val gold = Color(0xFFD9B66B)
    val blue = Color(0xFF2E9BFF)
    val orange = Color(0xFFFF8A1F)
    val green = Color(0xFF3DDC84)
    val red = Color(0xFFE5484D)
    val text = Color(0xFFE9EAEC)
    val dim = Color(0xFF8D939C)
}

val LocalScale = compositionLocalOf { 1f }

@Composable
fun fsp(x: Float): TextUnit = (x * LocalScale.current).sp

/** A hardware-style panel button with an LED bar. */
@Composable
fun PanelButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = Pal.blue,
    textColor: Color? = null,
    fontSize: Float = 9f,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val cb by rememberUpdatedState(onClick)
    val top = if (pressed) Color(0xFF454B55) else Color(0xFF30343B)
    val bottom = if (pressed) Color(0xFF2A2E34) else Color(0xFF1D2025)
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .padding(1.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, if (active) activeColor else Pal.edge, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { cb() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .fillMaxWidth(0.4f)
                .height(2.dp)
                .background(if (active) activeColor else Color(0xFF0C0D0F))
        )
        Text(
            text,
            color = textColor ?: if (active) Color.White else Pal.text,
            fontSize = fsp(fontSize),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            lineHeight = fsp(fontSize * 1.05f),
            modifier = Modifier.padding(horizontal = 1.dp, vertical = 3.dp)
        )
    }
}

/**
 * Hardware-style key like the real panel: the label is printed ABOVE a plain grey key, and a small LED bar on the key lights up
 * when the function is on. Used for the upper panel buttons.
 */
@Composable
fun HwButton(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = Pal.blue,
    fontSize: Float = 6.5f,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val cb by rememberUpdatedState(onClick)
    val shape = RoundedCornerShape(3.dp)
    val top = if (pressed) Color(0xFF7A7F88) else Color(0xFF5E6269)
    val bottom = if (pressed) Color(0xFF3A3E44) else Color(0xFF2B2E33)
    Column(modifier.padding(horizontal = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, color = Color(0xFFD3D6DB), fontSize = fsp(fontSize), fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Clip,
            lineHeight = fsp(fontSize * 1.05f), modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 1.dp, bottom = 1.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .border(1.dp, Color(0xFF787D86), shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                        onTap = { cb() }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .fillMaxWidth(0.45f)
                    .height(2.dp)
                    .background(if (active) activeColor else Color(0xFF0A0B0D))
            )
        }
    }
}

/** Rotary knob. Drag up/down to change. */
@Composable
fun Knob(label: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cur by rememberUpdatedState(value)
    val cb by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        cb((cur - drag.y / 260f).coerceIn(0f, 1f))
                    }
                }
        ) {
            val r = min(size.width, size.height) / 2f * 0.92f
            val c = Offset(size.width / 2f, size.height / 2f)
            val stroke = r * 0.10f
            val warm = Color(0xFFE9A640)
            drawArc(
                Color(0xFF3A3D43), 135f, 270f, false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                warm, 135f, 270f * value, false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawCircle(Color(0xFF000000), r * 0.80f, c)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF6C7078), Color(0xFF16181B)), center = Offset(c.x - r * 0.2f, c.y - r * 0.25f), radius = r * 0.8f),
                radius = r * 0.72f, center = c
            )
            val ang = Math.toRadians((135.0 + 270.0 * value))
            drawCircle(
                Color.White, r * 0.07f,
                Offset(c.x + (r * 0.5f * cos(ang)).toFloat(), c.y + (r * 0.5f * sin(ang)).toFloat())
            )
        }
        Text(label, color = Pal.text, fontSize = fsp(8f), fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Data wheel: drag round it to change the tempo. */
@Composable
fun DataWheel(onDelta: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onDelta)
    var angle by remember { mutableStateOf(0f) }
    Canvas(
        modifier.pointerInput(Unit) {
            var acc = 0f
            detectDragGestures { change, drag ->
                change.consume()
                acc += drag.x - drag.y
                val steps = (acc / 10f).toInt()
                if (steps != 0) {
                    acc -= steps * 10f
                    angle += steps * 6f
                    cb(steps)
                }
            }
        }
    ) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF6A6F78), Color(0xFF111316)), center = c, radius = r), r * 0.95f, c)
        drawCircle(Color(0xFF0A0B0D), r * 0.78f, c)
        drawCircle(Brush.sweepGradient(listOf(Color(0xFF3A3E45), Color(0xFF16181C), Color(0xFF4A4F58), Color(0xFF16181C), Color(0xFF3A3E45)), c), r * 0.74f, c)
        val a = Math.toRadians(angle.toDouble() - 45.0)
        drawCircle(Color.White, r * 0.07f, Offset(c.x + (r * 0.55f * cos(a)).toFloat(), c.y + (r * 0.55f * sin(a)).toFloat()))
    }
}

/** Ethiopian flag badge (green, yellow, red). */
@Composable
fun FlagBadge(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val h = size.height / 3f
        drawRect(Color(0xFF078930), Offset(0f, 0f), Size(size.width, h))
        drawRect(Color(0xFFFCDD09), Offset(0f, h), Size(size.width, h))
        drawRect(Color(0xFFDA121A), Offset(0f, h * 2), Size(size.width, h))
        drawCircle(Color(0xFF0F47AF), h * 0.9f, Offset(size.width / 2f, size.height / 2f))
    }
}

/** One coloured LCD tile. */
@Composable
fun LcdTile(
    title: String, line1: String, line2: String, c1: Color, c2: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val cb by rememberUpdatedState(onClick)
    Column(
        modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(listOf(c1, c2)))
            .pointerInput(Unit) { detectTapGestures { cb() } }
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(title, color = Color(0xCCFFFFFF), fontSize = fsp(8f), maxLines = 1)
        Text(line1, color = Color.White, fontSize = fsp(13f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (line2.isNotEmpty()) Text(line2, color = Color(0xDDFFFFFF), fontSize = fsp(8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Vertical pitch / modulation wheel. Pitch springs back to the centre. */
@Composable
fun VerticalWheel(label: String, value: Float, bipolar: Boolean, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Pal.dim, fontSize = fsp(7f), maxLines = 1)
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(bipolar) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull() ?: continue
                            if (c.pressed) {
                                val f = 1f - (c.position.y / size.height).coerceIn(0f, 1f)
                                cb(if (bipolar) f * 2f - 1f else f)
                                c.consume()
                            } else if (c.previousPressed && bipolar) {
                                cb(0f)
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(Color(0xFF0C0D0F), Offset(w * 0.3f, 0f), Size(w * 0.4f, h), androidx.compose.ui.geometry.CornerRadius(w * 0.2f))
            val frac = if (bipolar) (value + 1f) / 2f else value
            val ty = (1f - frac) * (h - h * 0.16f)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF7A808A), Color(0xFF30343B)), ty, ty + h * 0.16f),
                Offset(w * 0.08f, ty), Size(w * 0.84f, h * 0.16f), androidx.compose.ui.geometry.CornerRadius(4f)
            )
            drawLine(Pal.gold, Offset(w * 0.1f, ty + h * 0.08f), Offset(w * 0.9f, ty + h * 0.08f), 2f)
        }
    }
}

// ----------------------------------------------------------------------
//  Piano keyboard
// ----------------------------------------------------------------------
class KeyRect(val note: Int, val black: Boolean, val x: Float, val w: Float)

fun isBlackKey(n: Int): Boolean = when (n % 12) { 1, 3, 6, 8, 10 -> true else -> false }

fun whiteNotes(lo: Int, hi: Int): List<Int> = (lo..hi).filter { !isBlackKey(it) }

fun buildKeys(lo: Int, hi: Int, startIdx: Int, visible: Int, width: Float, bw: Float): List<KeyRect> {
    val whites = whiteNotes(lo, hi)
    val ww = width / visible
    val out = ArrayList<KeyRect>()
    val end = min(whites.size, startIdx + visible)
    for (i in startIdx until end) out.add(KeyRect(whites[i], false, (i - startIdx) * ww, ww))
    for (i in startIdx until end) {
        val n = whites[i]
        if (n + 1 <= hi && isBlackKey(n + 1)) {
            val cx = (i - startIdx + 1) * ww
            out.add(KeyRect(n + 1, true, cx - ww * bw / 2f, ww * bw))
        }
    }
    return out
}

private fun hitKey(keys: List<KeyRect>, x: Float, y: Float, blackLen: Float): Int? {
    if (y <= blackLen) for (k in keys) if (k.black && x >= k.x && x < k.x + k.w) return k.note
    for (k in keys) if (!k.black && x >= k.x && x < k.x + k.w) return k.note
    return null
}

/** Long, multitouch piano. You can slide a finger across keys (glissando). */
@Composable
fun PianoKeyboard(
    lo: Int, hi: Int, startIdx: Int, visible: Int,
    blackWidth: Float, blackHeight: Float, showLabels: Boolean, split: Int,
    pressed: Set<Int>,
    onDown: (Int, Float) -> Unit, onUp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val paint = remember {
        Paint().apply { isAntiAlias = true; color = 0xFF6A6D72.toInt(); textAlign = Paint.Align.CENTER }
    }
    Canvas(
        modifier.pointerInput(lo, hi, startIdx, visible, blackWidth, blackHeight) {
            val held = HashMap<Long, Int>()
            try {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val keys = buildKeys(lo, hi, startIdx, visible, w, blackWidth)
                        for (c in ev.changes) {
                            val id = c.id.value
                            if (c.pressed) {
                                val note = hitKey(keys, c.position.x, c.position.y, h * blackHeight)
                                val prev = held[id]
                                if (note != prev) {
                                    if (prev != null) { up(prev); held.remove(id) }
                                    if (note != null) { held[id] = note; down(note, c.position.y / h) }
                                }
                                c.consume()
                            } else {
                                val prev = held.remove(id)
                                if (prev != null) up(prev)
                            }
                        }
                    }
                }
            } finally {
                for (n in held.values) up(n)
                held.clear()
            }
        }
    ) {
        val keys = buildKeys(lo, hi, startIdx, visible, size.width, blackWidth)
        val h = size.height
        val blackLen = h * blackHeight
        for (k in keys) {
            if (k.black) continue
            val on = k.note in pressed
            val left = k.note <= split
            val top = if (on) Color(0xFFD6EBFF) else Color(0xFFFCFCFA)
            val bot = if (on) Color(0xFF3AA0FF) else if (left) Color(0xFFD5DCE6) else Color(0xFFD2D2CC)
            drawRoundRect(Brush.verticalGradient(listOf(top, bot)), Offset(k.x, -8f), Size(k.w - 1f, h + 8f - 1f), androidx.compose.ui.geometry.CornerRadius(5f, 5f))
            drawRect(Color(0x33000000), Offset(k.x + k.w - 1.5f, 0f), Size(1.5f, h))
            drawRect(Color(0x22000000), Offset(k.x, h - 5f), Size(k.w - 1f, 5f))
            if (showLabels && k.note % 12 == 0) {
                paint.textSize = k.w * 0.34f
                drawContext.canvas.nativeCanvas.drawText("C" + (k.note / 12 - 1), k.x + k.w / 2f, h - 12f, paint)
            }
        }
        for (k in keys) {
            if (!k.black) continue
            val on = k.note in pressed
            val top = if (on) Color(0xFF6CB8FF) else Color(0xFF45474D)
            val bot = if (on) Color(0xFF0B4F94) else Color(0xFF030304)
            drawRoundRect(Brush.verticalGradient(listOf(top, bot)), Offset(k.x, -8f), Size(k.w, blackLen + 8f), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
            drawRect(Color(0x40FFFFFF), Offset(k.x + k.w * 0.14f, 0f), Size(k.w * 0.14f, blackLen * 0.86f))   // gloss
            drawRect(Color(0x22FFFFFF), Offset(k.x + k.w * 0.70f, 0f), Size(k.w * 0.06f, blackLen * 0.80f))
            drawRect(Color(0xFF000000), Offset(k.x, blackLen - 4f), Size(k.w, 4f))
        }
        // split marker
        val ww = size.width / visible
        val whites = whiteNotes(lo, hi)
        val idx = whites.indexOfFirst { it > split } - startIdx
        if (idx in 1 until visible) drawLine(Pal.blue, Offset(idx * ww, 0f), Offset(idx * ww, h), 2f)
    }
}

/** Thin overview bar to scroll the visible window along the whole keyboard. */
@Composable
fun ScrollStrip(total: Int, visible: Int, start: Int, onStart: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onStart)
    Canvas(
        modifier.pointerInput(total, visible) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull() ?: continue
                    if (c.pressed) {
                        val frac = (c.position.x / size.width).coerceIn(0f, 1f)
                        cb((frac * total - visible / 2f).toInt())
                        c.consume()
                    }
                }
            }
        }
    ) {
        drawRoundRect(Color(0xFF0C0D0F), Offset(0f, 0f), size, androidx.compose.ui.geometry.CornerRadius(6f))
        val tw = size.width * visible / total.toFloat()
        val tx = size.width * start / total.toFloat()
        drawRoundRect(Pal.gold, Offset(tx, 1f), Size(tw, size.height - 2f), androidx.compose.ui.geometry.CornerRadius(6f))
    }
}
