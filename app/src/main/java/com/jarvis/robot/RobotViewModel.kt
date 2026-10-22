package com.jarvis.robot

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** A one-line result under a control: green when it worked, red when it did not. */
data class Feedback(val ok: Boolean, val text: String)

class RobotViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var host by mutableStateOf(if (BuildConfig.DEMO) "192.168.1.50" else prefs.getString("host", "") ?: "")   // the demo starts "connected" to its pretend robot
        private set
    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
    )
        private set

    var palette by mutableStateOf(
        runCatching { AppPalette.valueOf(prefs.getString("palette", "CLASSIC") ?: "CLASSIC") }
            .getOrDefault(AppPalette.CLASSIC),
    )
        private set

    var status by mutableStateOf<Status?>(null)
        private set

    // the camera window: the last picture, when it was taken, and what went wrong if the last try failed
    var photo by mutableStateOf<ImageBitmap?>(null)
        private set
    var photoTime by mutableStateOf<String?>(null)
        private set
    var photoBusy by mutableStateOf(false)
        private set
    var photoError by mutableStateOf<String?>(null)
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

    var serverFeedback by mutableStateOf<Feedback?>(null)
        private set
    var backendPin by mutableStateOf("")
        private set
    private var pinFor = ""
    private var pinRetryAt = 0L

    /** True while the backend reports that Home Assistant works; the Home Assistant commands are listed only then. Asked again every 2 minutes. */
    var haOk by mutableStateOf(false)
        private set
    private var haCheckedAt = 0L

    /** The backend's music module (null = not on or not asked yet): the Music card shows while it is on. Asked every 4 seconds while the app is on screen. */
    var music by mutableStateOf<MusicStatus?>(null)
        private set

    /** Live Wi-Fi traffic of the robot, asked once a second together with the status (null until two readings exist). */
    var netRate by mutableStateOf<NetRate?>(null)
        private set
    private var netPrev: NetCounters? = null
    private var musicCheckedAt = 0L
    private var musicMisses = 0   // checks in a row that got no answer at all (a busy Wi-Fi moment); the Music card stays until several in a row fail
    private var haMisses = 0

    init {
        if (BuildConfig.DEMO) DemoRobot.sharedPhoto = { app.resources.openRawResource(R.drawable.splash).use { it.readBytes() } }   // what the pretend camera "sees"
    }

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
        serverFeedback = null
        backendPin = ""
        pinFor = ""
        haOk = false
        haCheckedAt = 0L
        music = null
        musicMisses = 0
        haMisses = 0
        musicCheckedAt = 0L
        releaseAll()
        if (pollJob?.isActive == true) loadSettings()
    }

    /** Quick check used by the connect screen before saving: does this address look like the robot? */
    suspend fun probe(candidate: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { RobotApi(candidate).status() }.isSuccess
    }

    fun choosePalette(p: AppPalette) {
        palette = p
        prefs.edit().putString("palette", p.name).apply()
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
        stopLive()
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
                runCatching { withContext(Dispatchers.IO) { current.net() } }.onSuccess { n ->   // firmware without /api/net just leaves the row empty
                    netRate = netRate(netPrev, n) ?: netRate
                    netPrev = n
                }
                loadBackendPin(current, it)
                loadHaOk(current, it)
                loadMusic(current, it)
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

    // ------------------------------------------------------------------ camera

    /** Takes one picture; it stays in the window until the next one. */
    fun takePhoto() {
        if (photoBusy) return
        val current = api
        photoBusy = true
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { current.photo() } }
            r.onSuccess { bytes ->
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                if (bmp != null) {
                    val panel = panelUrlOf(status)
                    if (panel.isNotEmpty()) {   // an own backend keeps every photo in its dcim folder; failing to hand it over never matters here
                        viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { current.uploadPhoto(panel, bytes) } } }
                    }
                    photo = bmp
                    photoTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    photoError = null
                } else {
                    photoError = "the picture could not be read"
                }
            }.onFailure { photoError = it.message?.takeIf { m -> m.isNotBlank() } ?: "no answer from Jarvis" }
            photoBusy = false
        }
    }

    // ------------------------------------------------------------------ live view (next to the drive pad)

    /** A new small picture right after the last one (about one a second on a good Wi-Fi); stops by itself after 3 minutes, after 3 failures in a row, or when the app leaves the screen. Not kept anywhere. */
    var liveOn by mutableStateOf(false)
        private set
    var liveImage by mutableStateOf<ImageBitmap?>(null)
        private set
    var liveNote by mutableStateOf<String?>(null)
        private set
    private var liveJob: Job? = null

    fun toggleLive() {
        if (liveOn) stopLive() else startLive()
    }

    fun startLive() {
        if (liveOn) return
        liveOn = true
        liveNote = null
        val current = api
        val started = System.currentTimeMillis()
        liveJob = viewModelScope.launch {
            var fails = 0
            while (isActive) {
                if (System.currentTimeMillis() - started > 180_000) {
                    liveNote = "Live view stopped after 3 minutes."
                    break
                }
                val began = System.currentTimeMillis()
                val r = runCatching { withContext(Dispatchers.IO) { current.photo(live = true) } }
                r.onSuccess { bytes ->
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    if (bmp != null) {
                        liveImage = bmp
                        fails = 0
                    } else {
                        fails++
                    }
                }.onFailure { e ->
                    if (e.message?.contains("busy") != true) fails++   // "busy" = the camera was still with the last picture: just ask again
                    if (fails >= 3) liveNote = "Live view stopped: " + (e.message?.takeIf { it.isNotBlank() } ?: "no answer from Jarvis")
                }
                if (fails >= 3) break
                delay((700 - (System.currentTimeMillis() - began)).coerceAtLeast(150))
            }
            liveOn = false
            liveJob = null
        }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
        liveOn = false
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

    // ------------------------------------------------------------------ volume and voice server

    /** Sets the requested volume (0..100); the next status poll shows what the speaker really plays. */
    fun setVolume(value: Int) {
        val current = api
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { current.setVolume(value) } }
        }
    }

    /** "default" = the xiaozhi.me cloud, otherwise a LAN address. The robot restarts about 2 s after a successful save. */
    fun setServer(value: String) {
        val current = api
        serverFeedback = Feedback(true, "Saving...")
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { current.setServer(value) } }
            serverFeedback = r.fold(
                { Feedback(it.ok, it.message.ifEmpty { if (it.ok) "Saved. Jarvis restarts." else "Not saved." }) },
                { Feedback(false, "Could not reach Jarvis") },
            )
            backendPin = ""
            pinFor = ""
        }
    }

    private fun loadMusic(current: RobotApi, s: Status) {
        val url = panelUrlOf(s)
        if (url.isEmpty()) {
            music = null
            return
        }
        val now = System.currentTimeMillis()
        if (now - musicCheckedAt < 4_000) return
        musicCheckedAt = now
        viewModelScope.launch {
            val m = runCatching { withContext(Dispatchers.IO) { current.musicStatus(url) } }.getOrNull()
            if (current === api) {
                if (m != null) {
                    music = m
                    musicMisses = 0
                } else if (++musicMisses >= 4) {   // no answer 4 times in a row (about 16 s): only then the Music card goes away
                    music = null
                }
            }
        }
    }

    fun musicStop() = musicAct("stop")

    fun musicNext() = musicAct("next")

    private fun musicAct(action: String) {
        val current = api
        val url = panelUrlOf(status)
        if (url.isEmpty()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { current.musicControl(url, action) } }
            musicCheckedAt = 0L
        }
    }

    private fun loadHaOk(current: RobotApi, s: Status) {
        val url = panelUrlOf(s)
        if (url.isEmpty()) {
            haOk = false
            return
        }
        val now = System.currentTimeMillis()
        if (now - haCheckedAt < 120_000) return
        haCheckedAt = now
        viewModelScope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { current.homeAssistantOk(url) } }.getOrNull()
            if (current === api) {
                if (ok != null) {
                    haOk = ok
                    haMisses = 0
                } else {   // no answer: keep what was known, ask again in 15 s, and give up after 3 misses in a row
                    haCheckedAt = System.currentTimeMillis() - 105_000
                    if (++haMisses >= 3) haOk = false
                }
            }
        }
    }

    /** The PIN of the own server's control panel, shown so the panel can be opened from this phone. */
    private fun loadBackendPin(current: RobotApi, s: Status) {
        val url = panelUrlOf(s)
        if (url.isEmpty()) {
            backendPin = ""
            pinFor = ""
            return
        }
        if (pinFor == url || System.currentTimeMillis() < pinRetryAt) return
        pinFor = url
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { current.backendPin(url) } }
            if (current !== api) return@launch
            r.onSuccess { backendPin = it }.onFailure {
                backendPin = ""
                pinFor = ""
                pinRetryAt = System.currentTimeMillis() + 30_000
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
