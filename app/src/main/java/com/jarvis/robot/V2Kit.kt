package com.jarvis.robot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The look of Jarvis v2 (build type "v2"): Material 3 Expressive shapes and hierarchy on top of the app's own four colour themes, so a theme or day/night
// change in Settings changes this app too. Every colour here is worked out from the palette (LocalPalette), nothing is fixed.

/** The colour roles of v2, from the current palette. */
data class V2Colors(
    val bg: Color,
    val surface: Color,        // cards and the navigation bar
    val surfaceHigh: Color,    // tiles
    val surfaceHighest: Color, // pressed or nested elements, the mini player
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val warm: Color,           // the second accent: music, "instant" commands, section labels
    val warmContainer: Color,
    val onWarmContainer: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
    val danger: Color,
    val success: Color,
)

@Composable
fun v2Colors(): V2Colors {
    val p = LocalPalette.current
    val toward = if (p.isDark) Color.White else Color.Black
    val accentContainer = lerp(p.bg, p.accent, if (p.isDark) 0.32f else 0.22f)
    val warmContainer = lerp(p.bg, p.instant, if (p.isDark) 0.30f else 0.20f)
    return V2Colors(
        bg = p.bg,
        surface = lerp(p.bg, toward, if (p.isDark) 0.035f else 0.0f).let { if (p.isDark) it else lerp(p.bg, Color.White, 0.55f) },
        surfaceHigh = if (p.isDark) lerp(p.bg, toward, 0.075f) else Color.White,
        surfaceHighest = if (p.isDark) lerp(p.bg, toward, 0.12f) else lerp(p.bg, Color.Black, 0.06f),
        accent = p.accent,
        onAccent = if (p.isDark) lerp(p.accent, Color.Black, 0.82f) else Color.White,
        accentContainer = accentContainer,
        onAccentContainer = if (p.isDark) lerp(p.accent, Color.White, 0.45f) else lerp(p.accent, Color.Black, 0.55f),
        warm = p.instant,
        warmContainer = warmContainer,
        onWarmContainer = if (p.isDark) lerp(p.instant, Color.White, 0.55f) else lerp(p.instant, Color.Black, 0.55f),
        text = p.text,
        muted = p.muted,
        outline = p.border,
        danger = p.danger,
        success = p.success,
    )
}

// ---------------------------------------------------------------------------------------------------------------------------------- icons
/** An outline icon from SVG path data (24 x 24, round ends), so the app needs no icon library; [filled] fills the shape instead (the active tab). */
fun svgIcon(name: String, vararg paths: String, filled: Boolean = false, stroke: Float = 1.8f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) {
            addPath(
                pathData = addPathNodes(d),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (filled) null else SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

object V2Icons {
    val Home = svgIcon("home", "M4 11l8-7 8 7v9h-5v-6H9v6H4z")
    val HomeFilled = svgIcon("homeF", "M4 11l8-7 8 7v9h-5v-6H9v6H4z", filled = true)
    val Drive = svgIcon("drive", circle(12f, 12f, 9f), circle(12f, 12f, 3f), "M12 3v6M12 15v6M3 12h6M15 12h6")
    val Say = svgIcon("say", "M4 5h16v11H9l-5 4z")
    val SayFilled = svgIcon("sayF", "M4 5h16v11H9l-5 4z", filled = true)
    val Brain = svgIcon("brain", "M8 4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4z", "M9 9h6v6H9z", "M9 2v2M15 2v2M9 20v2M15 20v2M2 9h2M2 15h2M20 9h2M20 15h2")
    val Settings = svgIcon("settings", "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
    val Face = svgIcon("face", "M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2", "M9.5 10h.01M14.5 10h.01", "M9 14.5c1.7 1.3 4.3 1.3 6 0", stroke = 2f)
    val Volume = svgIcon("volume", "M4 9v6h4l5 4V5L8 9H4z", "M16 9a3 3 0 0 1 0 6")
    val Moon = svgIcon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val MicOff = svgIcon("micOff", "M9 9v3a3 3 0 0 0 5.1 2.1M15 10V6a3 3 0 0 0-5.8-1", "M19 11a7 7 0 0 1-1.2 3.9M5 11a7 7 0 0 0 11.3 5.5M12 18v3M3 3l18 18")
    val Mic = svgIcon("mic", "M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z", "M5 11a7 7 0 0 0 14 0M12 18v3")
    val Power = svgIcon("power", "M12 3v9", "M6.3 6.3a8 8 0 1 0 11.4 0")
    val Camera = svgIcon("camera", "M4 8h3l2-3h6l2 3h3v11H4z", circle(12f, 13f, 3.5f))
    val Next = svgIcon("next", "M6 5l9 7-9 7V5z", "M18 5v14", stroke = 2f)
    val Stop = svgIcon("stop", "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", stroke = 2f)
    val Chevron = svgIcon("chev", "M9 6l6 6-6 6", stroke = 2f)
    val ChevronDown = svgIcon("chevD", "M6 9l6 6 6-6", stroke = 2f)
    val ChevronUp = svgIcon("chevU", "M6 15l6-6 6 6", stroke = 2f)
    val Back = svgIcon("back", "M15 6l-6 6 6 6", stroke = 2f)
    val Close = svgIcon("close", "M6 6l12 12M18 6L6 18", stroke = 2f)
    val Up = svgIcon("up", "M12 19V5M6 11l6-6 6 6", stroke = 2.2f)
    val Down = svgIcon("down", "M12 5v14M6 13l6 6 6-6", stroke = 2.2f)
    val Left = svgIcon("left", "M19 12H5M11 6l-6 6 6 6", stroke = 2.2f)
    val Right = svgIcon("right", "M5 12h14M13 6l6 6-6 6", stroke = 2.2f)
    val Search = svgIcon("search", circle(11f, 11f, 6.5f), "M16 16l4 4", stroke = 2f)
    val Robot = svgIcon("robot", "M9 7h6a4 4 0 0 1 4 4v3a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4v-3a4 4 0 0 1 4-4z", "M12 7V4M9.5 12.5h.01M14.5 12.5h.01")
    val Motors = svgIcon("motors", circle(7f, 17f, 3f), circle(17f, 17f, 3f), "M7 14V7h7l3 7")
    val Screen = svgIcon("screen", "M7 5h10a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M9 20h6")
    val Clock = svgIcon("clock", circle(12f, 13f, 7f), "M12 9v4l2.5 2M9 3h6")
    val Sun = svgIcon("sun", circle(12f, 12f, 4f), "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val Eye = svgIcon("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))
    val Wave = svgIcon("wave", "M4 12h2M8 8v8M12 5v14M16 8v8M20 12h0")
    val Ear = svgIcon("ear", "M8 10a4 4 0 1 1 8 0c0 3-3 4-3 7a2 2 0 0 1-4 0")
    val Speak = svgIcon("speak", "M4 9v6h4l5 4V5L8 9H4z", "M16 9a3 3 0 0 1 0 6", "M18.5 6.5a7 7 0 0 1 0 11")
    val Heart = svgIcon("heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val House = svgIcon("house", "M3 11l9-7 9 7", "M5 10v10h14V10")
    val Gear = svgIcon("gear", circle(12f, 12f, 3f), "M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1")
    val Wifi = svgIcon("wifi", "M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01")
    val External = svgIcon("ext", "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")
}

// ---------------------------------------------------------------------------------------------------------------------------------- pieces
/** A rounded tile (the bento grid's cell). [onClick] null: not clickable. */
@Composable
fun V2Tile(
    modifier: Modifier = Modifier,
    color: Color = v2Colors().surfaceHigh,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = modifier.clip(shape).background(color).let { if (onClick != null) it.clickable(role = role, onClick = onClick) else it }.padding(padding)
    Column(m, content = content)
}

/** A round icon in a tinted square, for list rows and tiles. */
@Composable
fun V2IconBadge(icon: ImageVector, bg: Color, fg: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.35f)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

/** The robot's own Wi-Fi bars: same levels (-55, -65, -72, -80 dBm) and the same colours, smallest red to tallest bright green. */
@Composable
fun V2WifiBars(rssi: Int?, height: Dp = 14.dp) {
    val v = v2Colors()
    val level = when {
        rssi == null || rssi == 0 -> 0
        rssi >= -55 -> 5
        rssi >= -65 -> 4
        rssi >= -72 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    val lit = listOf(Color(0xFFE53935), Color(0xFFFF9800), Color(0xFFE65100), Color(0xFF43A047), Color(0xFF22EE66))
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(height)) {
        for (i in 0 until 5) {
            Box(Modifier.width(3.dp).height(height * ((i + 1) / 5f)).background(if (level > i) lit[i] else v.outline))
        }
    }
}

/** A connected button group (M3 Expressive): one row, the ends rounder than the middle, the chosen one filled. */
@Composable
fun <T> V2Connected(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val v = v2Colors()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, (value, label) ->
            val first = i == 0
            val last = i == options.lastIndex
            val on = value == selected
            val big = height / 2
            val small = 8.dp
            val shape = if (on) RoundedCornerShape(big) else RoundedCornerShape(
                topStart = if (first) big else small, bottomStart = if (first) big else small,
                topEnd = if (last) big else small, bottomEnd = if (last) big else small,
            )
            Box(
                Modifier.weight(1f).height(height).clip(shape).background(if (on) v.accent else v.surfaceHigh)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) v.onAccent else v.text, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** A list row with a title, an optional second line and something at the end (a switch, a value, a chevron). */
@Composable
fun V2Row(title: String, sub: String? = null, onClick: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val v = v2Colors()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = v.text, fontSize = 15.sp)
            if (!sub.isNullOrEmpty()) Text(sub, color = v.muted, fontSize = 13.sp)
        }
        trailing()
    }
}

@Composable
fun V2Switch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val v = v2Colors()
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = v.onAccent, checkedTrackColor = v.accent, checkedBorderColor = v.accent,
            uncheckedThumbColor = v.muted, uncheckedTrackColor = v.surfaceHighest, uncheckedBorderColor = v.muted,
        ),
    )
}

/** A group of rows on one rounded card, with thin lines between them. */
@Composable
fun V2Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val v = v2Colors()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(v.surface), content = content)
}

@Composable
fun V2Divider() {
    val v = v2Colors()
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(v.outline.copy(alpha = 0.5f)))
}

/** A small label above a group ("While Jarvis talks"). */
@Composable
fun V2Label(text: String) {
    val v = v2Colors()
    Text(text, color = v.warm, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

/** A big screen title with an optional line under it. */
@Composable
fun V2Title(text: String, sub: String? = null) {
    val v = v2Colors()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(text, color = v.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (!sub.isNullOrEmpty()) Text(sub, color = v.muted, fontSize = 13.sp)
    }
}

/** A round icon button (44 dp, the smallest comfortable touch target). */
@Composable
fun V2IconButton(icon: ImageVector, label: String, onClick: () -> Unit, bg: Color = v2Colors().surfaceHigh, fg: Color = v2Colors().text, size: Dp = 44.dp, enabled: Boolean = true) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg.copy(alpha = bg.alpha * (if (enabled) 1f else 0.5f))).clickable(enabled = enabled, role = Role.Button, onClickLabel = tr(label), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = tr(label), tint = fg.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(size * 0.45f))
    }
}

/** A full-width pill button: filled (the main action) or outlined. */
@Composable
fun V2Button(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, danger: Boolean = false, enabled: Boolean = true) {
    val v = v2Colors()
    val bg = when {
        !filled -> Color.Transparent
        danger -> v.danger
        else -> v.accent
    }
    val fg = when {
        filled && danger -> v.bg
        filled -> v.onAccent
        danger -> v.danger
        else -> v.accent
    }
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.height(48.dp).clip(shape).background(bg.copy(alpha = bg.alpha * (if (enabled) 1f else 0.4f)))
            .let { if (!filled) it.border(1.dp, if (danger) v.danger.copy(alpha = 0.6f) else v.outline, shape) else it }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg.copy(alpha = if (enabled) 1f else 0.6f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
    }
}
