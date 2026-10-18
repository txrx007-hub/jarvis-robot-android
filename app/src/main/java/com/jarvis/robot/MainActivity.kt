package com.jarvis.robot

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

class MainActivity : ComponentActivity() {
    private val vm: RobotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NetworkUtil.bindToWifi(applicationContext)
        setContent {
            JarvisTheme(vm.themeMode) { Root(vm) }
        }
    }

    override fun onStop() {
        // Never leave the robot driving when the app goes to the background.
        vm.releaseAll()
        super.onStop()
    }
}

@Composable
private fun Root(vm: RobotViewModel) {
    val palette = LocalPalette.current
    val activity = LocalContext.current as ComponentActivity
    var editingAddress by remember { mutableStateOf(vm.host.isEmpty()) }

    // Status-bar icon colours follow the app's theme, not just the phone's.
    DisposableEffect(palette.isDark) {
        activity.enableEdgeToEdge(
            statusBarStyle = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        onDispose { }
    }

    // Poll the robot only while the app is on screen.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.startPolling()
            try {
                awaitCancellation()
            } finally {
                vm.stopPolling()
            }
        }
    }

    // Splash photo for 4 s on a fresh start (not again on rotation), then a short fade into the app.
    // Polling already runs underneath, so the info table is filled by the time it ends.
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(SPLASH_MS)
            showSplash = false
        }
    }

    Crossfade(targetState = showSplash, animationSpec = tween(450), label = "splash") { splash ->
        when {
            splash -> SplashScreen()
            editingAddress -> ConnectScreen(vm, canCancel = vm.host.isNotEmpty()) { editingAddress = false }
            else -> MainScreen(vm) { editingAddress = true }
        }
    }
}

const val SPLASH_MS = 4000L
const val SPLASH_SIZE = 0.7f // share of the screen's width and height the photo takes

/** The robot in the user's hand, background blurred (made by tools/make_splash.py), at 70% of the screen. */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(LocalPalette.current.bg),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.splash),
            contentDescription = "Jarvis",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(SPLASH_SIZE)
                .fillMaxHeight(SPLASH_SIZE)
                .clip(RoundedCornerShape(24.dp)),
        )
    }
}
