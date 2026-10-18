package com.jarvis.robot

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** A one-line result under a control: green when it worked, red when it did not. */
data class Feedback(val ok: Boolean, val text: String)

class RobotViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var host by mutableStateOf(prefs.getString("host", "") ?: "")
        private set
    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
    )
        private set

    var status by mutableStateOf<Status?>(null)
        private set
    var online by mutableStateOf(false)
        private set
    var everConnected by mutableStateOf(false)
        private set
    var pingText by mutableStateOf("-- ms")
        private set

    var settings by mutableStateOf<List<SettingDef>>(emptyList())
        private set
    var settingsError by mutableStateOf<String?>(null)
        private set
    val feedback = mutableStateMapOf<String, Feedback>()

    val held = mutableStateListOf<Dir>()
    var powerFeedback by mutableStateOf<Feedback?>(null)
        private set
    var sleepingNow by mutableStateOf(false)
        private set

    private var api = RobotApi(host)
    private var pollJob: Job? = null
    private var driveJob: Job? = null
    private var failures = 0

    // ------------------------------------------------------------------ connection

    fun connectTo(newHost: String) {
        val clean = normalizeHost(newHost)
        host = clean
        prefs.edit().putString("host", clean).apply()
        api = RobotApi(clean)
        status = null
        online = false
        failures = 0
        settings = emptyList()
        settingsError = null
        sleepingNow = false
        releaseAll()
        if (pollJob?.isActive == true) loadSettings()
    }

    /** Quick check used by the connect screen before saving: does this address look like the robot? */
    suspend fun probe(candidate: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { RobotApi(candidate).status() }.isSuccess
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    // ------------------------------------------------------------------ polling (only while the app is visible)

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            launch { statusLoop() }
            launch { pingLoop() }
        }
        if (host.isNotEmpty()) loadSettings()
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        releaseAll()
    }

    private suspend fun statusLoop() {
        while (currentCoroutineContext().isActive) {
            if (host.isEmpty()) {
                delay(500)
                continue
            }
            val current = api
            val result = runCatching { withContext(Dispatchers.IO) { current.status() } }
            if (current !== api) continue // the address changed while the request was in flight
            result.onSuccess {
                status = it
                failures = 0
                sleepingNow = false
                if (!online) {
                    online = true
                    everConnected = true
                    if (settings.isEmpty()) loadSettings()
                }
            }.onFailure {
                failures++
                if (failures >= 3) online = false
            }
            delay(1000)
        }
    }

    private suspend fun pingLoop() {
        while (currentCoroutineContext().isActive) {
            if (host.isEmpty() || !online) {
                pingText = "-- ms"
                delay(500)
                continue
            }
            val current = api
            val ms = runCatching { withContext(Dispatchers.IO) { current.ping() } }
            pingText = ms.fold({ "$it ms" }, { "timeout" })
            delay(333)
        }
    }

    fun loadSettings() {
        val current = api
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { current.settings() } }
            if (current !== api) return@launch
            result.onSuccess {
                settings = it
                settingsError = null
            }.onFailure {
                settingsError = "Could not load settings."
            }
        }
    }

    // ------------------------------------------------------------------ settings

    fun save(key: String, value: String) {
        val current = api
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { current.save(key, value) } }
            result.onSuccess { r ->
                feedback[key] = Feedback(r.ok, r.message.ifEmpty { if (r.ok) "Saved" else "Could not change it" })
                if (r.ok && r.updated != null) {
                    settings = settings.map { if (it.key == key) r.updated else it }
                }
            }.onFailure {
                feedback[key] = Feedback(false, "Could not reach Jarvis")
            }
        }
    }

    fun resetAll(onDone: (Boolean) -> Unit) {
        val current = api
        viewModelScope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { current.reset() } }.getOrDefault(false)
            feedback.clear()
            if (ok) loadSettings()
            onDone(ok)
        }
    }

    // ------------------------------------------------------------------ remote control

    fun hold(dir: Dir, down: Boolean) {
        if (down) {
            if (dir !in held) held.add(dir)
        } else {
            held.remove(dir)
        }
        updateDrive()
    }

    /** Lets go of every arrow and tells the robot to stop (also called when the app is hidden). */
    fun releaseAll() {
        if (held.isEmpty() && driveJob == null) return
        held.clear()
        updateDrive()
    }

    private fun updateDrive() {
        if (held.isEmpty()) {
            driveJob?.cancel()
            driveJob = null
            val current = api
            viewModelScope.launch(Dispatchers.IO) { runCatching { current.drive(0, 0) } }
            return
        }
        if (driveJob?.isActive == true) return
        val current = api
        // Each command drives for only 500 ms on the robot, so a dropped connection stops it by itself.
        driveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val (l, r) = wheelCommand(held.toSet())
                runCatching { current.drive(l, r) }
                delay(150)
            }
        }
    }

    // ------------------------------------------------------------------ power

    fun nap(currentlyNapping: Boolean) {
        val current = api
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { current.power(if (currentlyNapping) "wake" else "nap") }
            }
            powerFeedback = r.fold(
                { Feedback(it.ok, it.message) },
                { Feedback(false, "Could not reach Jarvis") },
            )
        }
    }

    fun deepSleep(minutes: Int) {
        val current = api
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { current.power("deepsleep", minutes) } }
            r.onSuccess {
                powerFeedback = Feedback(it.ok, it.message)
                sleepingNow = it.ok
            }.onFailure {
                powerFeedback = Feedback(false, "Could not reach Jarvis")
            }
        }
    }
}
