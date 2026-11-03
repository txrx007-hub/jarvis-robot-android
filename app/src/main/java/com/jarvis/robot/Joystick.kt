package com.jarvis.robot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The drive joystick, in the style of the landing page's: a ring with a cross and a round knob that follows the finger anywhere on it, up to the edge of its
 * travel. [onStick] gets every position (-1..1, x right, y down), [onRelease] the lift (the knob springs back to the centre and the wheels stop).
 */
@Composable
fun Joystick(
    stick: Stick,
    onStick: (Stick) -> Unit,
    onRelease: () -> Unit,
    accent: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tickDown = rememberTick(strong = true)
    val tickUp = rememberTick()
    val move by rememberUpdatedState(onStick)
    val lift by rememberUpdatedState(onRelease)
    var active by remember { mutableStateOf(false) }
    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = tr("Joystick: drag to drive") }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun at(p: Offset): Stick {
                    val reach = size.width * STICK_REACH
                    return Stick((p.x - size.width / 2f) / reach, (p.y - size.height / 2f) / reach).limited()
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    active = true
                    tickDown()
                    move(at(down.position))
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            move(at(change.position))
                        }
                    } finally {   // also when the screen goes away mid-drag
                        active = false
                        lift()
                        tickUp()
                    }
                }
            },
    ) {
        val w = size.width
        val c = center
        val ringR = w * 0.44f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent), center = c, radius = ringR), ringR, c)
        drawCircle(accent.copy(alpha = 0.45f), ringR, c, style = Stroke(1.5.dp.toPx()))
        drawLine(accent.copy(alpha = 0.35f), Offset(w * 0.09f, c.y), Offset(w * 0.91f, c.y), 1.dp.toPx())
        drawLine(accent.copy(alpha = 0.35f), Offset(c.x, w * 0.09f), Offset(c.x, w * 0.91f), 1.dp.toPx())
        val s = stick.limited()
        val k = Offset(c.x + s.x * w * STICK_REACH, c.y + s.y * w * STICK_REACH)
        val knobR = w * 0.15f
        drawCircle(accent.copy(alpha = 0.18f), knobR * 1.35f, k)   // the glow
        drawCircle(accent.copy(alpha = if (active) 0.7f else 0.55f), knobR, k)
        drawCircle(lerp(accent, Color.White, 0.2f), knobR, k, style = Stroke(2.dp.toPx()))
        val d = w * 0.02f
        rotate(45f, k) { drawRect(ink.copy(alpha = 0.8f), Offset(k.x - d, k.y - d), Size(2 * d, 2 * d), style = Stroke(1.5.dp.toPx())) }
    }
}

/** "Left wheel: 0% · Right wheel: 0%": what the wheels are told right now (the knob and the Speed slider together). */
@Composable
fun WheelReadout(stick: Stick, power: Int, color: Color, modifier: Modifier = Modifier) {
    val (l, r) = wheelCommand(stick, power)
    Text("${tr("Left wheel:")} $l% · ${tr("Right wheel:")} $r%", color = color, fontSize = 13.sp, modifier = modifier)
}

/** The Speed slider's value from the slider's position: steps of 5, never below 10. */
fun drivePowerOf(x: Float): Int = (Math.round(x / 5f) * 5).coerceIn(10, 100)
