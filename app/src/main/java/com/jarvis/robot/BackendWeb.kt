package com.jarvis.robot

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** False in the screenshot tests: the page is drawn as a placeholder there. */
val LocalLiveWeb = staticCompositionLocalOf { true }

/**
 * The own server's control panel, shown inside the app. The panel needs a PIN from other devices; it lets a visitor in
 * without one when the request says it comes from the robot's page (its `/auto` address and the robot as Referer), the
 * same way the landing page's button does. The panel follows the app's colour theme and day/night mode.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BackendWeb(panelUrl: String, robotHost: String, palette: AppPalette, modifier: Modifier = Modifier, anchor: String = "", onFullscreen: ((Boolean) -> Unit)? = null) {
    val p = LocalPalette.current
    if (LocalInspectionMode.current || !LocalLiveWeb.current) { // previews and the screenshot tests have no real WebView
        Box(modifier.background(p.card), contentAlignment = Alignment.Center) {
            Text("The Jarvis backend page shows here", color = p.muted, fontSize = 14.sp)
        }
        return
    }
    val context = LocalContext.current
    val demo = BuildConfig.DEMO   // the demo serves the panel's page from the app itself (DemoWeb)
    val base = if (demo) "https://${DemoWeb.HOST}/" else if (panelUrl.endsWith("/")) panelUrl else "$panelUrl/"
    val panelHost = remember(base) { Uri.parse(base).host.orEmpty() }
    // Jarvis v2 opens the page at one section: scroll there once the panel has drawn its cards (they come in a moment after the page itself).
    val scrollJs = if (anchor.isEmpty()) "" else
        "setTimeout(function(){var e=document.getElementById('$anchor');if(e){var c=e.closest('.card')||e;c.scrollIntoView({block:'start'});}},700);"
    val themeJs = themeScript(p.isDark, palette, I18n.lang) + scrollJs
    var latestJs by remember { mutableStateOf(themeJs) }
    latestJs = themeJs

    // The live stats page (/stats of the panel) asks for the whole screen through window.Jarvis.fullscreen(true) and gives it back with (false) (its X button);
    // the Android back button closes it the same way. `full` only tells the screen around the page to hide its own bars.
    var full by remember { mutableStateOf(false) }
    val fullCallback by androidx.compose.runtime.rememberUpdatedState(onFullscreen)
    val web = remember(base) {
        WebView(context).apply {
            addJavascriptInterface(object {
                @JavascriptInterface
                fun fullscreen(on: Boolean) {
                    post { full = on; fullCallback?.invoke(on) }
                }
            }, "Jarvis")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(0x00000000)
            webViewClient = object : WebViewClient() {
                // Stay on the panel: other addresses are not opened inside the app.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.host != panelHost

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    if (demo) DemoWeb.serve(context, request) else null

                override fun onPageFinished(view: WebView, url: String?) {
                    if (url?.contains("/stats") != true && full) { full = false; fullCallback?.invoke(false) }   // left the stats page some other way
                    view.evaluateJavascript(latestJs, null)
                    (view.parent as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout)?.isRefreshing = false   // a pull-down reload has finished
                }
            }
            if (demo) loadUrl(DemoWeb.START) else loadUrl(base + "auto", mapOf("Referer" to "http://$robotHost/"))
        }
    }
    // Pull down at the top of the page to reload it (Android's own pull-to-refresh; only when the page is scrolled to the top).
    val accent = p.accent.toArgb()
    val holder = remember(web) {
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout(context).apply {
            addView(web)
            setColorSchemeColors(accent)
            setOnChildScrollUpCallback { _, _ -> web.canScrollVertically(-1) }
            setOnRefreshListener { web.reload() }
        }
    }
    DisposableEffect(web) { onDispose { holder.removeView(web); web.destroy(); fullCallback?.invoke(false) } }
    BackHandler(enabled = full) { web.evaluateJavascript("closeStats()", null) }
    LaunchedEffect(themeJs) { web.evaluateJavascript(themeJs, null) }
    AndroidView(factory = { holder }, modifier = modifier)
}

/** Puts the panel in the same day/night mode and colour theme as the app (the panel reads these from its page settings). */
private fun themeScript(dark: Boolean, palette: AppPalette, lang: Lang): String {
    val theme = if (dark) "dark" else "light"
    val pal = palette.name.lowercase()
    val lg = lang.code
    return "(function(){var d=document.documentElement;d.dataset.theme='$theme';" +
        "try{localStorage.setItem('theme','$theme');localStorage.setItem('palette','$pal');}catch(e){}" +
        (if (palette == AppPalette.CLASSIC) "delete d.dataset.palette;" else "d.dataset.palette='$pal';") +
        "var s=document.getElementById('paletteSel');if(s)s.value='$pal';" +
        "try{localStorage.setItem('lang','$lg');}catch(e){}if(window.setPanelLang)window.setPanelLang('$lg');" +
        "var l=document.getElementById('langSw');if(l)l.style.display='none';})();"
}

/** The phone's full-screen backend page: a bar with a back button on top, the panel below. */
@Composable
fun BackendScreen(state: MainUiState, onClose: () -> Unit) {
    val p = LocalPalette.current
    BackHandler(onBack = onClose)
    var full by remember { mutableStateOf(false) }   // the live stats page is open: it takes the whole screen (bars hidden, like a browser in full screen)
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(full) {
        val window = (view.context as? android.app.Activity)?.window
        val ctl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (full) {
            ctl?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    Box(Modifier.fillMaxSize()) {
        ThemeBackdrop()
        Column(Modifier.fillMaxSize().then(if (full) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())) {
            if (!full) PageTabs(advancedSelected = true, advancedVisible = true, onSettings = onClose, onAdvanced = {}, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            BackendWeb(panelUrlOf(state.status), state.host, state.palette, Modifier.fillMaxSize(), onFullscreen = { full = it })
        }
    }
}
