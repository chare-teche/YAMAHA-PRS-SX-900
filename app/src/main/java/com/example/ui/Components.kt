package com.example.ui

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * Workstation colour palette: deep black/charcoal chassis, dark navy panels,
 * electric blue active illumination, and metallic gold accents.
 */
object Pal {
    val bg = Color(0xFF06070A)
    val panel = Color(0xFF0E1626)
    val panel2 = Color(0xFF142138)
    val navyDeep = Color(0xFF0A1020)
    val navyMid = Color(0xFF13223D)
    val edge = Color(0xFF2B3A55)
    val gold = Color(0xFFE2BE6A)
    val goldBright = Color(0xFFFFD875)
    val blue = Color(0xFF2997FF)
    val blueGlow = Color(0xFF66BAFF)
    val orange = Color(0xFFFF8C24)
    val green = Color(0xFF36D982)
    val red = Color(0xFFE84A52)
    val text = Color(0xFFF2F4F8)
    val silver = Color(0xFFB8C2D1)
    val dim = Color(0xFF8893A4)
}

val LocalScale = compositionLocalOf { 1f }

@Composable
fun fsp(x: Float): TextUnit = (x * LocalScale.current).sp

/** Hardware-style panel button with top LED indicator, bevelled gradient, and touch press animation. */
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
    val top = when {
        pressed -> Color(0xFF3E4C66)
        active -> activeColor.copy(alpha = 0.36f)
        else -> Color(0xFF222B3D)
    }
    val bottom = when {
        pressed -> Color(0xFF171E2C)
        active -> Color(0xFF101929)
        else -> Color(0xFF10141C)
    }
    val borderCol = when {
        pressed -> Pal.gold
        active -> activeColor
        else -> Pal.edge
    }
    val shape = RoundedCornerShape(4.dp)
    val sc by animateFloatAsState(if (pressed) 0.94f else 1f, tween(80), label = "btnPress")

    Box(
        modifier
            .padding(1.dp)
            .shadow(if (active) 3.dp else 1.dp, shape, clip = false)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, borderCol, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { cb() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .fillMaxWidth(0.46f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (active) activeColor else Color(0xFF07090E))
        )
        Text(
            text = text,
            color = textColor ?: if (active) Color.White else Pal.text,
            fontSize = fsp(fontSize),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            lineHeight = fsp(fontSize * 1.05f),
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp)
        )
    }
}

/**
 * Hardware-style key with top silkscreen label above a tactile dark-navy/charcoal keycap
 * and an illuminated LED bar when active.
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
    val top = if (pressed) Color(0xFF4A5873) else Color(0xFF283246)
    val bottom = if (pressed) Color(0xFF1D2433) else Color(0xFF131822)
    val capBorder = if (active) activeColor.copy(alpha = 0.85f) else Color(0xFF44526B)

    Column(
        modifier.padding(horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (active) Pal.goldBright else Pal.silver,
            fontSize = fsp(fontSize),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            lineHeight = fsp(fontSize * 1.05f),
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 1.dp, bottom = 1.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .border(1.dp, capBorder, shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { cb() }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth(0.48f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (active) activeColor else Color(0xFF07090E))
            )
        }
    }
}

/** Rotary knob with metallic gold / electric-blue arc indicator. Drag vertically to adjust. */
@Composable
fun Knob(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
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
            val stroke = r * 0.11f
            drawArc(
                color = Color(0xFF1D2636),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Pal.blue, Pal.gold)),
                startAngle = 135f,
                sweepAngle = 270f * value.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawCircle(Color(0xFF05070B), r * 0.80f, c)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF4A566E), Color(0xFF101622)),
                    center = Offset(c.x - r * 0.2f, c.y - r * 0.25f),
                    radius = r * 0.8f
                ),
                radius = r * 0.72f,
                center = c
            )
            drawCircle(
                color = Pal.gold.copy(alpha = 0.45f),
                radius = r * 0.72f,
                center = c,
                style = Stroke(width = 1.dp.toPx())
            )
            val ang = Math.toRadians((135.0 + 270.0 * value.coerceIn(0f, 1f)))
            drawCircle(
                color = Pal.goldBright,
                radius = r * 0.075f,
                center = Offset(
                    c.x + (r * 0.5f * cos(ang)).toFloat(),
                    c.y + (r * 0.5f * sin(ang)).toFloat()
                )
            )
        }
        Text(
            text = label,
            color = Pal.silver,
            fontSize = fsp(8f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Jog/data wheel with metallic gold & electric blue rim accent. Drag around/across to step values. */
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
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF3E4B63), Color(0xFF0A0E17)), center = c, radius = r),
            radius = r * 0.95f,
            center = c
        )
        drawCircle(
            color = Pal.gold.copy(alpha = 0.5f),
            radius = r * 0.92f,
            center = c,
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawCircle(Color(0xFF07090E), r * 0.78f, c)
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    Color(0xFF2B374D),
                    Color(0xFF101622),
                    Pal.blue.copy(alpha = 0.45f),
                    Color(0xFF101622),
                    Color(0xFF2B374D)
                )
            ),
            radius = r * 0.74f,
            center = c
        )
        val a = Math.toRadians(angle.toDouble() - 45.0)
        drawCircle(
            color = Pal.goldBright,
            radius = r * 0.075f,
            center = Offset(
                c.x + (r * 0.55f * cos(a)).toFloat(),
                c.y + (r * 0.55f * sin(a)).toFloat()
            )
        )
    }
}

/** Ethiopian flag badge (green, yellow, red with blue emblem). */
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

/** Coloured LCD tile card. */
@Composable
fun LcdTile(
    title: String,
    line1: String,
    line2: String,
    c1: Color,
    c2: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cb by rememberUpdatedState(onClick)
    Column(
        modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(listOf(c1, c2)))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
            .pointerInput(Unit) { detectTapGestures { cb() } }
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color(0xCCFFFFFF), fontSize = fsp(8f), maxLines = 1)
        Text(
            text = line1,
            color = Color.White,
            fontSize = fsp(13f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (line2.isNotEmpty()) {
            Text(
                text = line2,
                color = Color(0xDDFFFFFF),
                fontSize = fsp(8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Vertical pitch bend / modulation wheel. Pitch bend springs back to centre on release. */
@Composable
fun VerticalWheel(
    label: String,
    value: Float,
    bipolar: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val cb by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Pal.silver, fontSize = fsp(7f), fontWeight = FontWeight.SemiBold, maxLines = 1)
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
            drawRoundRect(
                color = Color(0xFF070A10),
                topLeft = Offset(w * 0.28f, 0f),
                size = Size(w * 0.44f, h),
                cornerRadius = CornerRadius(w * 0.2f)
            )
            if (bipolar) {
                drawLine(
                    color = Pal.blue.copy(alpha = 0.55f),
                    start = Offset(w * 0.25f, h * 0.5f),
                    end = Offset(w * 0.75f, h * 0.5f),
                    strokeWidth = 1.5f
                )
            }
            val frac = if (bipolar) ((value + 1f) / 2f).coerceIn(0f, 1f) else value.coerceIn(0f, 1f)
            val thumbH = h * 0.16f
            val ty = (1f - frac) * (h - thumbH)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF475673), Color(0xFF172030)),
                    startY = ty,
                    endY = ty + thumbH
                ),
                topLeft = Offset(w * 0.08f, ty),
                size = Size(w * 0.84f, thumbH),
                cornerRadius = CornerRadius(4f)
            )
            drawLine(
                color = if (bipolar) Pal.blueGlow else Pal.gold,
                start = Offset(w * 0.12f, ty + thumbH * 0.5f),
                end = Offset(w * 0.88f, ty + thumbH * 0.5f),
                strokeWidth = 2f
            )
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

/** Wide multitouch arranger keyboard with glissando, split indicator, and octave labels. */
@Composable
fun PianoKeyboard(
    lo: Int,
    hi: Int,
    startIdx: Int,
    visible: Int,
    blackWidth: Float,
    blackHeight: Float,
    showLabels: Boolean,
    split: Int,
    pressed: Set<Int>,
    onDown: (Int, Float) -> Unit,
    onUp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            color = 0xFF4A5568.toInt()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
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
                                    if (prev != null) {
                                        up(prev)
                                        held.remove(id)
                                    }
                                    if (note != null) {
                                        held[id] = note
                                        down(note, (c.position.y / h).coerceIn(0f, 1f))
                                    }
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
            val top = when {
                on -> Pal.blueGlow
                left -> Color(0xFFE7EEF8)
                else -> Color(0xFFFCFDFE)
            }
            val bot = when {
                on -> Pal.blue
                left -> Color(0xFFC4D2E6)
                else -> Color(0xFFD5DAE3)
            }
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(top, bot)),
                topLeft = Offset(k.x, -8f),
                size = Size(k.w - 1f, h + 8f - 1f),
                cornerRadius = CornerRadius(5f, 5f)
            )
            drawRect(Color(0x38000000), Offset(k.x + k.w - 1.5f, 0f), Size(1.5f, h))
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, if (on) Pal.blue.copy(alpha = 0.45f) else Color(0x26000000)),
                    startY = h - 10f,
                    endY = h
                ),
                topLeft = Offset(k.x, h - 10f),
                size = Size(k.w - 1f, 10f)
            )
            if (showLabels && k.note % 12 == 0) {
                paint.textSize = k.w * 0.34f
                drawContext.canvas.nativeCanvas.drawText("C" + (k.note / 12 - 1), k.x + k.w / 2f, h - 10f, paint)
            }
        }

        for (k in keys) {
            if (!k.black) continue
            val on = k.note in pressed
            val top = if (on) Pal.blueGlow else Color(0xFF3A4458)
            val bot = if (on) Color(0xFF0A4A8A) else Color(0xFF05070C)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(top, bot)),
                topLeft = Offset(k.x, -8f),
                size = Size(k.w, blackLen + 8f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRect(Color(0x48FFFFFF), Offset(k.x + k.w * 0.14f, 0f), Size(k.w * 0.14f, blackLen * 0.86f))
            drawRect(Color(0x22FFFFFF), Offset(k.x + k.w * 0.70f, 0f), Size(k.w * 0.06f, blackLen * 0.80f))
            drawRect(
                color = if (on) Pal.goldBright else Color(0xFF000000),
                topLeft = Offset(k.x, blackLen - 4f),
                size = Size(k.w, 4f)
            )
        }

        val ww = size.width / visible
        val whites = whiteNotes(lo, hi)
        val idx = whites.indexOfFirst { it > split } - startIdx
        if (idx in 1 until visible) {
            drawLine(Pal.blue, Offset(idx * ww, 0f), Offset(idx * ww, h), 2f)
        }
    }
}

/** Keyboard position overview & scroll strip with metallic gold thumb. */
@Composable
fun ScrollStrip(
    total: Int,
    visible: Int,
    start: Int,
    onStart: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
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
        drawRoundRect(
            color = Pal.navyDeep,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(6f)
        )
        drawRoundRect(
            color = Pal.edge.copy(alpha = 0.6f),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(6f),
            style = Stroke(width = 1.dp.toPx())
        )
        val safeTotal = maxOf(1, total)
        val tw = (size.width * visible / safeTotal.toFloat()).coerceAtLeast(12f)
        val tx = (size.width * start / safeTotal.toFloat()).coerceIn(0f, (size.width - tw).coerceAtLeast(0f))
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Pal.gold, Pal.goldBright, Pal.gold)),
            topLeft = Offset(tx, 1f),
            size = Size(tw, (size.height - 2f).coerceAtLeast(2f)),
            cornerRadius = CornerRadius(6f)
        )
    }
}
