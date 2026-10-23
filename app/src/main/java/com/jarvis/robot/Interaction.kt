package com.jarvis.robot

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.sp

/** One short vibration tick for a tap, a detent or a release; `strong` is the firmer one for presses that do something big (hold to drive, danger buttons). */
@Composable
fun rememberTick(strong: Boolean = false): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, strong) {
        { haptic.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) }
    }
}

/** A small popup that explains the control: it opens on a long press (touch) or on hover (mouse) and closes by itself. No hint = the content as it is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hinted(hint: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // The caller's modifier (a row weight, a width) goes on a plain Box: TooltipBox would swallow a weight and the first button would take the whole row.
    Box(modifier) {
        if (hint == null) {
            content()
        } else {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(hint) } },
                state = rememberTooltipState(),
                content = content,
            )
        }
    }
}

/** The one confirm popup used for everything that is hard to undo (deep sleep, a new voice server, reset). */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, danger: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val tick = rememberTick(strong = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { tick(); onConfirm() }) { Text(confirmLabel, color = if (danger) p.danger else p.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The version of the app that is running, as the phone knows it ("2.15"), or "" when it cannot be read. */
@Composable
fun appVersionName(): String {
    val context = LocalContext.current
    return remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
}

/** A small "App v2.15" line, so it is always clear which build is on the phone. */
@Composable
fun AppVersionLabel(modifier: Modifier = Modifier) {
    val version = appVersionName()
    if (version.isNotEmpty()) Text(if (BuildConfig.DEMO) "Demo app v${version.removeSuffix("-demo")}" else "App v$version", color = LocalPalette.current.muted, fontSize = 11.sp, maxLines = 1, softWrap = false, modifier = modifier)
}
