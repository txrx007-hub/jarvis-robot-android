package com.jarvis.robot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The five destinations of the navigation bar (Material: three to five, every one labelled, fixed positions). */
enum class V2Tab(val label: String, val icon: ImageVector, val iconOn: ImageVector) {
    HOME("Home", V2Icons.Home, V2Icons.HomeFilled),
    DRIVE("Drive", V2Icons.Drive, V2Icons.Drive),
    SAY("Say", V2Icons.Say, V2Icons.SayFilled),
    BRAIN("Brain", V2Icons.Brain, V2Icons.Brain),
    SETTINGS("Settings", V2Icons.Settings, V2Icons.Settings),
}

/**
 * Jarvis v2: the same state and the same actions as the classic screen (MainUiState / MainActions), laid out as tabs, tiles and sheets.
 * Nothing here talks to the robot by itself; every button calls one of the actions the classic app has too.
 */
@Composable
fun V2App(state: MainUiState, actions: MainActions) {
    val v = v2Colors()
    var tab by rememberSaveable { mutableStateOf(V2Tab.HOME) }
    var panelAnchor by rememberSaveable { mutableStateOf<String?>(null) }   // not null: the backend page is open over everything, scrolled to this element ("" = the top)
    val snack = remember { SnackbarHostState() }
    val wide = LocalConfiguration.current.screenWidthDp >= TABLET_MIN_DP

    // Every answer the robot gives to a change comes up as a snackbar (the classic screen shows them under the card instead).
    V2FeedbackSnacks(state, snack)

    fun go(t: V2Tab) {
        if (tab == V2Tab.DRIVE && t != V2Tab.DRIVE) {   // leaving the driving screen: stop the wheels and the live pictures
            actions.onReleaseAll()
            actions.onStopLive()
        }
        tab = t
    }
    BackHandler(enabled = panelAnchor != null || tab != V2Tab.HOME) {
        if (panelAnchor != null) panelAnchor = null else go(V2Tab.HOME)
    }

    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            ThemeBackdrop()
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (tab) {
                    V2Tab.HOME -> RefreshBox(state.refreshing, actions.onRefresh) { V2Home(state, actions, onOpenBrain = { go(V2Tab.BRAIN) }) }
                    V2Tab.DRIVE -> V2Drive(state, actions)
                    V2Tab.SAY -> V2Say(state)
                    V2Tab.BRAIN -> V2Brain(state, actions, onOpenPanel = { panelAnchor = it })
                    V2Tab.SETTINGS -> RefreshBox(state.refreshing, actions.onRefresh) { V2Settings(state, actions) }
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 560.dp)) { data ->
                Snackbar(data, containerColor = v.text, contentColor = v.bg, actionColor = v.accent, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = v.surface, modifier = Modifier.statusBarsPadding()) {
                    for (t in V2Tab.entries) {
                        NavigationRailItem(
                            selected = t == tab, onClick = { go(t) },
                            icon = { Icon(if (t == tab) t.iconOn else t.icon, contentDescription = null) },
                            label = { Text(t.label, fontSize = 12.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = v.accentContainer, selectedIconColor = v.onAccentContainer, selectedTextColor = v.text,
                                unselectedIconColor = v.muted, unselectedTextColor = v.muted,
                            ),
                        )
                    }
                }
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                NavigationBar(containerColor = v.surface) {
                    for (t in V2Tab.entries) {
                        NavigationBarItem(
                            selected = t == tab, onClick = { go(t) },
                            icon = { Icon(if (t == tab) t.iconOn else t.icon, contentDescription = null) },
                            label = { Text(t.label, fontSize = 12.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = v.accentContainer, selectedIconColor = v.onAccentContainer, selectedTextColor = v.text,
                                unselectedIconColor = v.muted, unselectedTextColor = v.muted,
                            ),
                        )
                    }
                }
            }
        }
        panelAnchor?.let { anchor -> V2PanelScreen(state, anchor) { panelAnchor = null } }
    }
}

/** Shows a snackbar whenever one of the robot's answers changes: a saved setting, nap / mute / sleep, the voice server, a failed photo. */
@Composable
private fun V2FeedbackSnacks(state: MainUiState, snack: SnackbarHostState) {
    val seen = remember { mutableStateOf(state.feedback) }
    LaunchedEffect(state.feedback) {
        val changed = state.feedback.filter { (k, f) -> seen.value[k] != f }.values.lastOrNull()
        seen.value = state.feedback
        if (changed != null) snack.showSnackbar(tr(changed.text))
    }
    LaunchedEffect(state.powerFeedback) { state.powerFeedback?.let { snack.showSnackbar(tr(it.text)) } }
    LaunchedEffect(state.serverFeedback) { state.serverFeedback?.let { snack.showSnackbar(tr(it.text)) } }
    LaunchedEffect(state.photoError) { state.photoError?.let { snack.showSnackbar(tr(it)) } }
    LaunchedEffect(state.brainFeedback) { state.brainFeedback?.let { snack.showSnackbar(tr(it.text)) } }
}
