package com.jarvis.robot

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import org.json.JSONObject
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

    init {
        I18n.restore(prefs.getString("lang", "en"))
        I18n.onChange = { prefs.edit().putString("lang", it.code).apply() }
    }

    var status by mutableStateOf<Status?>(null)
        private set

    // the camera window: the last picture, when it was taken, and what went wrong if the last try failed
    var photo by mutableStateOf<ImageBitmap?>(null)
        private set
    var photoTime by mutableStateOf<String?>(null)
        private set
    var photoBytes by mutableStateOf<ByteArray?>(null)   // the same picture as the robot sent it: Share and Download use these bytes, not a second JPEG compression
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

    /** The joystick knob, and the Speed slider (percent of full power, back to 100 at every start; it scales only the remote control, no robot setting). */
    var stick by mutableStateOf(Stick())
        private set
    var drivePower by mutableIntStateOf(100)
        private set
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

    /** Jarvis v2's Brain switches as the backend reports them (null = not asked yet or no backend); asked when the Brain tab opens and after every change. */
    var brain by mutableStateOf<BrainSwitches?>(null)
        private set

    fun loadBrain() {
        val current = api
        val url = panelUrlOf(status)
        if (url.isEmpty()) return
        viewModelScope.launch {
            brain = withContext(Dispatchers.IO) { readBrain(current, url) }
        }
    }

    /** which = greeter | voiceprint | smartturn: the panel's own switch for it ({"enabled": on}); takes effect at once on the voice server, no restart. */
    fun setBrain(which: String, on: Boolean) {
        val current = api
        val url = panelUrlOf(status)
        if (url.isEmpty() || which !in BRAIN_SWITCHES) return
        brain = brain?.let { b -> b.copy(greeter = if (which == "greeter") on else b.greeter, voices = if (which == "voiceprint") on else b.voices, smartTurn = if (which == "smartturn") on else b.smartTurn) }
        viewModelScope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { current.panelJson(url, "api/$which", JSONObject().put("enabled", on)) } }.isSuccess
            brainFeedback = Feedback(ok, if (ok) "${BRAIN_NAMES[which]} ${if (on) "on" else "off"}" else "Could not reach the backend: ${BRAIN_NAMES[which]} not changed")
            brain = withContext(Dispatchers.IO) { readBrain(current, url) }
        }
    }

    /** The answer to the last Brain switch (for the snackbar). */
    var brainFeedback by mutableStateOf<Feedback?>(null)
        private set

    /** Jarvis v2's own backend pages (greeter, voices, music, memory): one panel call, GET without [body], a change with it; [done] gets the answer or null. */
    fun panel(path: String, body: JSONObject?, done: (JSONObject?) -> Unit) {
        val current = api
        val url = panelUrlOf(status)
        if (url.isEmpty()) { done(null); return }
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { current.panelJson(url, path, body) } }.getOrNull()
            done(r)
            if (body != null) brain = withContext(Dispatchers.IO) { readBrain(current, url) }   // a change on a page also shows on the Brain tab's switches
        }
    }

    private fun readBrain(current: RobotApi, url: String): BrainSwitches {
        fun get(path: String) = runCatching { current.panelJson(url, path) }.getOrNull()
        val g = get("api/greeter")
        val vp = get("api/voiceprint")
        val st = get("api/smartturn")
        val mem = get("api/memory")
        return BrainSwitches(
            greeter = g?.optBoolean("enabled"),
            voices = vp?.optBoolean("enabled"),
            voicesPeople = vp?.optJSONArray("people")?.length() ?: 0,
            voicesModel = vp?.optBoolean("model_ready") ?: false,
            smartTurn = st?.optBoolean("enabled"),
            smartModel = st?.optBoolean("model_ready") ?: false,
            memoryOn = mem?.optBoolean("on"),
        )
    }

    /** True while a pull-down refresh is asking the robot (and the backend) again. */
    var refreshing by mutableStateOf(false)
        private set

    /** Pull down to refresh: status, settings, music and Home Assistant are asked again at once instead of waiting for their next turn. */
    fun refresh() {
        if (refreshing || host.isEmpty()) return
        refreshing = true
        musicCheckedAt = 0L
        haCheckedAt = 0L
        val current = api
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { current.status() } }.onSuccess { s ->
                if (current === api) {
                    status = s
                    online = true
                    failures = 0
                    loadHaOk(current, s)
                    loadMusic(current, s)
                }
            }
            runCatching { withContext(Dispatchers.IO) { current.settings() } }.onSuccess {
                if (current === api) {
                    settings = it
                    settingsError = null
                }
            }
            delay(400)   // long enough to see that it happened
            refreshing = false
        }
    }

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
                    photoBytes = bytes
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

    /** The knob moved (finger down or dragging): the wheels follow it, re-sent every 150 ms while it is away from the centre. */
    fun steer(to: Stick) {
        stick = to.limited()
        updateDrive()
    }

    fun chooseDrivePower(percent: Int) {
        drivePower = percent.coerceIn(10, 100)
    }

    /** Lets go of the knob and tells the robot to stop (also called when the app is hidden). */
    fun releaseAll() {
        if (stick == Stick() && driveJob == null) return
        stick = Stick()
        updateDrive()
    }

    private fun updateDrive() {
        if (stick.idle) {
            val wasDriving = driveJob != null
            driveJob?.cancel()
            driveJob = null
            val current = api
            if (wasDriving) viewModelScope.launch(Dispatchers.IO) { runCatching { current.drive(0, 0) } }
            return
        }
        if (driveJob?.isActive == true) return
        val current = api
        // Each command drives for only 500 ms on the robot, so a dropped connection stops it by itself.
        driveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                // Live view on = looking through the robot's camera: the controls are reversed (right is left, up is down). The knob on the screen still follows the finger.
                val (l, r) = wheelCommand(if (liveOn) stick.flipped() else stick, drivePower)
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

    /** Mutes or unmutes the microphone (the Mute button of the power card); the status shows the result a second later. */
    fun setMuted(on: Boolean) {
        val current = api
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { current.power(if (on) "mute" else "unmute") } }
            powerFeedback = r.fold({ Feedback(it.ok, it.message) }, { Feedback(false, "Could not reach Jarvis") })
            if (r.isSuccess) {
                delay(600)
                runCatching { withContext(Dispatchers.IO) { current.status() } }.onSuccess { if (current === api) status = it }
            }
        }
    }

    /** Follow mode on or off (the Follow me switch); the status shows the result a second later. */
    fun setFollow(on: Boolean) {
        val current = api
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { current.power(if (on) "follow_on" else "follow_off") } }
            powerFeedback = r.fold({ Feedback(it.ok, it.message) }, { Feedback(false, "Could not reach Jarvis") })
            if (r.isSuccess) {
                delay(700)
                runCatching { withContext(Dispatchers.IO) { current.status() } }.onSuccess { if (current === api) status = it }
            }
        }
    }

    /** The eyes follow your face (Jarvis stays where it is); the status shows the result a second later. */
    fun setEyes(on: Boolean) {
        val current = api
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { current.power(if (on) "eyes_on" else "eyes_off") } }
            powerFeedback = r.fold({ Feedback(it.ok, it.message) }, { Feedback(false, "Could not reach Jarvis") })
            if (r.isSuccess) {
                delay(700)
                runCatching { withContext(Dispatchers.IO) { current.status() } }.onSuccess { if (current === api) status = it }
            }
        }
    }

    /** "Face me": one look, then up to three short turns towards the face. */
    fun faceMe() {
        val current = api
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { current.power("face") } }
            powerFeedback = r.fold({ Feedback(it.ok, it.message) }, { Feedback(false, "Could not reach Jarvis") })
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
