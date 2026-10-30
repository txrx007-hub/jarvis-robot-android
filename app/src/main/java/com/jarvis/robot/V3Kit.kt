package com.jarvis.robot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Jarvis v3 (build type "v3"): the v2 screens in the Material 3 Expressive design language, using the library's own expressive components (material3 1.4.0):
// a floating toolbar for navigation, connected button groups (shape morphing on press), the loading indicator, wavy progress, and MaterialShapes.
// Every screen, every function and every panel call is the v2 code; only the look of its building blocks changes when [V3.on].

/** Whether the v3 look is on: the "v3" build type, or a test that switches it on. */
object V3 {
    @JvmField
    var on: Boolean = BuildConfig.UI_V3
}

/** The expressive theme: components from the library get the expressive motion (springs) and shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun V3Theme(scheme: ColorScheme, content: @Composable () -> Unit) {
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = Typography(), content = content)
}

/** A connected button group: the ends round, the middle square, the chosen one a full pill; each button morphs while it is pressed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> V3Connected(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier, height: Dp) {
    val v = v2Colors()
    val outer = 26.dp
    val inner = 8.dp
    val pill = RoundedCornerShape(outer)
    val pressed = RoundedCornerShape(inner)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, (value, label) ->
            val rest = when (i) {   // the ends round, the middle square; the chosen one is a full pill; pressed, any of them morphs to a small radius
                0 -> RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
                options.lastIndex -> RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
                else -> pressed
            }
            val shapes = ToggleButtonDefaults.shapes(shape = rest, pressedShape = pressed, checkedShape = pill)
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { onSelect(value) },
                shapes = shapes,
                modifier = Modifier.weight(1f).heightIn(min = height),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = v.surfaceHigh, contentColor = v.text,
                    checkedContainerColor = v.accent, checkedContentColor = v.onAccent,
                ),
            ) { Text(label, maxLines = 1, fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Medium) }
        }
    }
}

/** The expressive loading indicator: a shape that morphs through a few others while the backend is asked. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun V3Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(color = v2Colors().accent)
    }
}

/** The navigation: a floating toolbar; the open tab is a pill with its name, the others are plain icons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun V3FloatingNav(current: V2Tab, onSelect: (V2Tab) -> Unit, modifier: Modifier = Modifier) {
    val v = v2Colors()
    // The look of the library's floating toolbar (a full pill, lifted on a shadow, the open tab a pill with its name), built by hand: the library's own toolbar draws a
    // large circle in the screenshot renderer (its expand animation never runs there), so it could not be checked here.
    Row(
        modifier.shadow(10.dp, CircleShape).clip(CircleShape).background(v.surfaceHighest).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (t in V2Tab.entries) {
            if (t == current) {
                Row(
                    Modifier.height(48.dp).clip(CircleShape).background(v.accent).clickable { onSelect(t) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(t.iconOn, contentDescription = null, tint = v.onAccent, modifier = Modifier.size(24.dp))
                    Text(t.label, color = v.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            } else {
                IconButton(onClick = { onSelect(t) }, modifier = Modifier.size(48.dp)) {
                    Icon(t.icon, contentDescription = tr(t.label), tint = v.muted, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** A wavy slider: the part up to the thumb is a wave, the rest a straight line. Drag or tap anywhere; the value is sent when the finger lifts. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun V3WavySlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val v = v2Colors()
    var width by remember { mutableIntStateOf(1) }
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    fun at(x: Float) = range.start + (x / width).coerceIn(0f, 1f) * (range.endInclusive - range.start)
    Box(
        modifier.fillMaxWidth().height(44.dp).onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(range) { detectTapGestures { onChange(at(it.x)); onDone() } }
            .pointerInput(range) { detectHorizontalDragGestures(onDragEnd = onDone, onDragCancel = onDone) { change, _ -> change.consume(); onChange(at(change.position.x)) } }
            .semantics {
                contentDescription = tr(label)
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { target -> onChange(target.coerceIn(range.start, range.endInclusive)); onDone(); true }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(), color = v.accent, trackColor = v.surfaceHighest)
        Box(
            Modifier.offset { IntOffset((width * frac).toInt() - 3.dp.roundToPx(), 0) }.width(6.dp).height(34.dp).clip(CircleShape).background(v.accent),
        )
    }
}

/** The slider the backend pages use: the wavy one in v3, the normal one in v2. [onDone] runs when the finger lifts. */
@Composable
internal fun V2Slider(value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, range: ClosedFloatingPointRange<Float>, label: String) {
    if (V3.on) { V3WavySlider(value, range, onChange, onDone, label); return }
    val v = v2Colors()
    androidx.compose.material3.Slider(
        value, onChange, onValueChangeFinished = onDone, valueRange = range,
        modifier = Modifier.semantics { contentDescription = tr(label) },
        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = v.accent, activeTrackColor = v.accent, inactiveTrackColor = v.surfaceHighest),
    )
}

/** The robot's face on a slowly turning cookie (a MaterialShapes polygon): the "hero moment" of the Home screen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun V3Cookie(size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val v = v2Colors()
    val shape = MaterialShapes.Cookie9Sided.toShape()
    val turn by rememberInfiniteTransition(label = "cookie").animateFloat(0f, 360f, infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).graphicsLayer { rotationZ = turn }.clip(shape).background(v.accent))
        content()
    }
}
