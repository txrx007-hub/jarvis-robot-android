package com.jarvis.robot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "What can I say?": the same list as the classic Voice commands panel, with a search field, category chips and groups that fold open. */
@Composable
fun V2Say(state: MainUiState) {
    val v = v2Colors()
    val musicOn = state.music?.enabled == true
    val groups = remember(state.haOk, musicOn) {
        VOICE_COMMANDS.filter { (state.haOk || it.second.none { c -> c.ha }) && (musicOn || it.second.none { c -> c.music }) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val q = query.trim().lowercase()
    val shown = groups
        .filter { chosen == null || it.first == chosen }
        .map { (title, cmds) ->
            title to if (q.isEmpty()) cmds else cmds.filter { c ->
                listOf(c.title, c.example, tr(c.title), tr(c.example), title, tr(title)).any { it.lowercase().contains(q) }
            }
        }
        .filter { it.second.isNotEmpty() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { V2Title("What can I say?", "Say “Jarvis” first, then one of these") }
        item {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(v.surfaceHigh).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(V2Icons.Search, contentDescription = null, tint = v.muted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Try “timer” or “dance”", color = v.muted, fontSize = 15.sp)
                    BasicTextField(
                        query, { query = it }, singleLine = true, cursorBrush = SolidColor(v.accent),
                        textStyle = TextStyle(color = v.text, fontSize = 15.sp), modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) V2IconButton(V2Icons.Close, "Clear", { query = "" }, size = 36.dp)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V2Chip("All", chosen == null) { chosen = null }
                for ((title, _) in groups) V2Chip(title, chosen == title) { chosen = if (chosen == title) null else title }
            }
        }
        items(shown, key = { it.first }) { (title, cmds) ->
            val isOpen = open[title] == true || q.isNotEmpty() || chosen != null
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(v.surfaceHigh)) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { open[title] = !(open[title] ?: false) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, color = v.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (!isOpen) Text(cmds.first().example, color = v.muted, fontSize = 13.sp, maxLines = 1)
                    }
                    Text("${cmds.size}", color = v.muted, fontSize = 13.sp)
                    Icon(if (isOpen) V2Icons.ChevronUp else V2Icons.ChevronDown, contentDescription = null, tint = v.muted, modifier = Modifier.size(18.dp))
                }
                if (isOpen) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (c in cmds) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(c.title, color = v.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    if (c.instant) V2Badge("instant", v.warmContainer, v.onWarmContainer)
                                    if (c.ha) V2Badge("Home", v.accentContainer, v.onAccentContainer)
                                    if (c.music) V2Badge("music", v.accentContainer, v.onAccentContainer)
                                }
                                Text(c.example, color = v.muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) item { Text("Nothing found for “$query”.", color = v.muted, fontSize = 14.sp, modifier = Modifier.padding(8.dp)) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun V2Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val v = v2Colors()
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.height(36.dp).clip(shape).background(if (selected) v.accentContainer else v.bg)
            .let { if (selected) it else it.border(1.dp, v.outline, shape) }
            .clickable(role = Role.Checkbox, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) v.onAccentContainer else v.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) }
}

@Composable
private fun V2Badge(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 8.dp, vertical = 1.dp)) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
