/* The demo app's stand-in for the Jarvis backend: the control panel page runs unchanged, this script answers its /api/* calls from the canned data in /data/
   (made up, see tools/make_demo_data.py) and remembers what you change while the page is open. Nothing leaves the phone, nothing is connected. */
(function () {
  "use strict";
  var realFetch = window.fetch.bind(window);
  var T0 = Date.now();
  var cache = {};
  var mem = {};          // things changed by the visitor: state, mood, memory, music
  var now = function () { return Date.now() / 1000; };
  var json = function (o, status) { return new Response(JSON.stringify(o), { status: status || 200, headers: { "Content-Type": "application/json" } }); };
  var wait = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
  var rnd = function (a, b) { return a + Math.random() * (b - a); };

  function data(name) {
    if (cache[name]) return cache[name];
    cache[name] = realFetch("/data/" + name + ".json").then(function (r) { return r.json(); });
    return cache[name];
  }
  function clone(o) { return JSON.parse(JSON.stringify(o)); }
  function get(name) {   // a copy that the visitor's changes live in
    if (mem[name]) return Promise.resolve(mem[name]);
    return data(name).then(function (d) { mem[name] = clone(d); return mem[name]; });
  }

  // ------------------------------------------------------------------ songs
  var SONGS = [
    ["Sunrise Boulevard", "Neon Harbour"], ["Blue Hour", "The Paper Moons"], ["Slow Orbit", "Mara Quinn"], ["Lantern Street", "Kite & Compass"],
    ["Velvet Static", "Dune Parade"], ["Cold Coffee Jazz", "The Night Owls"], ["Paper Planes at Noon", "Juno Vale"], ["After the Rain", "Harbour Lights"],
  ];
  var song = 0, restartAt = 0;
  function musicNow() {
    if (!mem.musicOn) return null;
    var s = SONGS[song % SONGS.length];
    return { title: s[0], artist: s[1], kind: "track" };
  }
  mem.musicOn = true;

  // ------------------------------------------------------------------ the log (times are today's, counted back from now)
  function hhmmss(secAgo) {
    var d = new Date(Date.now() - secAgo * 1000), p = function (n) { return (n < 10 ? "0" : "") + n; };
    return p(d.getHours()) + ":" + p(d.getMinutes()) + ":" + p(d.getSeconds());
  }
  var TURNS = [   // [seconds ago, level, area, role, text]
    [1180, "INFO", "asr", "listen", "Heard: turn on the living room lights"],
    [1179, "INFO", "instant", "tool", "Instant command: Home Assistant, light.turn_on light.living_room"],
    [1178, "INFO", "voice", "speak", "Sending the first spoken sentence: Done."],
    [900, "INFO", "asr", "listen", "Heard: play some jazz"],
    [899, "INFO", "instant", "tool", "Instant command: music, searching the folder for \"jazz\""],
    [897, "INFO", "voice", "speak", "Sending the first spoken sentence: Playing Cold Coffee Jazz."],
    [640, "INFO", "asr", "listen", "Heard: what is the weather tomorrow"],
    [639, "INFO", "brain", "think", "Brain received the question (gemini-3.5-flash-lite)"],
    [638, "INFO", "tool", "tool", "web_search: weather tomorrow"],
    [637, "INFO", "tool", "tool", "search results: 3 pages (Tavily, 0.8 s)"],
    [636, "INFO", "voice", "speak", "Sending the first spoken sentence: Tomorrow looks mild, around 17 degrees with a light breeze."],
    [410, "INFO", "asr", "listen", "Heard: how is your wifi"],
    [409, "INFO", "instant", "tool", "Instant command: robot info (wifi)"],
    [408, "INFO", "voice", "speak", "Sending the first spoken sentence: My Wi-Fi signal is minus 52 decibels, that is excellent."],
    [180, "INFO", "asr", "listen", "Heard: come here"],
    [179, "INFO", "robot", "tool", "Robot tool call: self_robot_move forward 100% for 1000 ms"],
    [178, "INFO", "voice", "speak", "Sending the first spoken sentence: Coming."],
    [60, "INFO", "asr", "listen", "Heard: tell me a joke"],
    [59, "INFO", "brain", "think", "Brain received the question (gemini-3.5-flash-lite)"],
    [57, "INFO", "voice", "speak", "Sending the first spoken sentence: I would tell you a UDP joke, but you might not get it."],
  ];
  var START = [
    [2400, "INFO", "gc_manager", "", "Started the global memory cleaner, every 300 s"],
    [2399, "INFO", "modules_initialize", "", "Initialised component: llm ok GeminiOpenAI"],
    [2399, "INFO", "modules_initialize", "", "Initialised component: memory ok mem_local_short"],
    [2398, "INFO", "modules_initialize", "", "Initialised component: vad ok SileroVAD"],
    [2394, "INFO", "modules_initialize", "", "Speech recognition module ready"],
    [2394, "INFO", "__main__", "", "OTA endpoint is http://192.168.1.10:8003/xiaozhi/ota/"],
    [2394, "INFO", "__main__", "", "Websocket address is ws://192.168.1.10:8000/xiaozhi/v1/"],
    [2390, "INFO", "connection", "", "Robot connected (demo)"],
  ];
  var PROBLEMS = [
    [1500, "WARNING", "brain", "think", "Brain gemini-3.5-flash-lite was slow (4.1 s); the fallback brain would answer next time"],
  ];
  function logLines(mode, roles) {
    var rows = mode === "problems" ? PROBLEMS : mode === "talk" ? TURNS : START.concat(PROBLEMS, TURNS).sort(function (a, b) { return b[0] - a[0]; });
    var out = rows.map(function (r) { return { time: hhmmss(r[0]), level: r[1], area: r[2], role: r[3], text: r[4], raw: r[4] }; });
    if (roles && roles.length) out = out.filter(function (l) { return roles.indexOf(l.role || "other") >= 0; });
    return out;
  }

  // ------------------------------------------------------------------ a short two-tone sample for "Test the voice"
  function sampleWav() {
    var rate = 16000, n = rate * 0.7, buf = new ArrayBuffer(44 + n * 2), v = new DataView(buf);
    var put = function (o, s) { for (var i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
    put(0, "RIFF"); v.setUint32(4, 36 + n * 2, true); put(8, "WAVEfmt "); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
    v.setUint32(24, rate, true); v.setUint32(28, rate * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true); put(36, "data"); v.setUint32(40, n * 2, true);
    for (var i = 0; i < n; i++) {
      var f = i < n / 2 ? 523 : 659, env = Math.min(1, i / 400, (n - i) / 800);
      v.setInt16(44 + i * 2, Math.sin(2 * Math.PI * f * i / rate) * 9000 * env, true);
    }
    return new Blob([buf], { type: "audio/wav" });
  }

  // ------------------------------------------------------------------ routes
  function dayLabels(series) {   // the usage charts count back from today
    var d = new Date();
    series.forEach(function (s, i) {
      var x = new Date(d.getTime() - (series.length - 1 - i) * 86400000);
      s.day = x.getFullYear() + "-" + ("0" + (x.getMonth() + 1)).slice(-2) + "-" + ("0" + x.getDate()).slice(-2);
    });
  }

  function route(method, url, body) {
    var u = new URL(url, location.href), path = u.pathname, q = u.searchParams;
    if (path.indexOf("/api/") !== 0) return null;
    var name = path.slice(5);
    if (method === "GET") {
      switch (name) {
        case "state": return get("state").then(function (s) { return json(s); });
        case "options": return data("options").then(json);
        case "usage": return data("usage").then(function (d) { var u2 = clone(d); u2.now = now(); Object.keys(u2.providers).forEach(function (k) { if (u2.providers[k].series) dayLabels(u2.providers[k].series); }); return json(u2); });
        case "telemetry": return data("telemetry").then(function (d) {
          var t = clone(d), n = now();
          t.speed.recent.forEach(function (r) { r.t = n + r.t; });
          t.robot.now.t = n; t.robot.last_seen = n; t.robot.now.uptime += Math.round((Date.now() - T0) / 1000);
          t.robot.reboots.forEach(function (r) { r.t = n + r.t; });
          if (t.conv && t.conv.since < 0) t.conv.since = n + t.conv.since;
          return json(t);
        });
        case "log": return json({ lines: logLines(q.get("mode") || "all", (q.get("roles") || "").split(",").filter(Boolean)) });
        case "key-check": return get("key-check").then(json);
        case "brain": return Promise.resolve(json({ main: "gemini-3-flash-preview", last: "gemini-3-flash-preview", last_t: now(), backup: false, failed: null }));
        case "key-reveal": return Promise.resolve(json({ value: "demo-key-not-a-real-key" }));   // the key boxes show *** and reveal this made-up text on a click
        case "mood": return get("mood").then(json);
        case "memory": return get("memory").then(json);
        case "ha-log": return data("ha-log").then(function (d) { var r = clone(d), n = now(); r.rows.forEach(function (x) { x.t = n + x.t; }); return json(r); });
        case "music": return data("music").then(function (d) { var m = clone(d); m.now = musicNow(); return json(m); });
        case "watchdog": return Promise.resolve(json({ watching: true, gave_up: false, restarts_24h: 1, events: [
          { t: now() - 5400, text: "the server was not answering: restarted" }, { t: now() - 86000, text: "phone alerts set up" }] }));
        case "notify": return Promise.resolve(json({ configured: !!mem.notifyOn, server: "ntfy.sh", events: mem.notifyEvents || { server: true, robot_offline_min: 0 } }));
        case "ping": return json({ server: { online: true, ms: Math.round(rnd(2, 8)) / 10 }, panel: { online: true, ms: Math.round(rnd(1, 5)) / 10 } });
        case "robot-net": {
          var t = (Date.now() - T0) / 1000, tx = rnd(5, 12), rx = rnd(8, 20);
          return json({ online: true, poll_ms: 333, up_s: 5400 + t, tx_pps: Math.round(tx * 10) / 10, rx_pps: Math.round(rx * 10) / 10,
            tx_mbps: Math.round(tx * 0.0007 * 10000) / 10000, rx_mbps: Math.round(rx * 0.0014 * 10000) / 10000, tx_pkts: Math.round(4321 + t * 9), rx_pkts: Math.round(6003 + t * 14),
            tx_mb: Math.round((0.88 + t * 0.0004) * 100) / 100, rx_mb: Math.round((0.92 + t * 0.0009) * 100) / 100 });
        }
        case "voices": return Promise.resolve(json({ ok: true, voices: [] }));   // the page already has the voice list from /api/options
        case "brain-models": return data("brain-models_" + (q.get("brain") || "gemini").replace(/\W+/g, "_")).then(json, function () { return json({ ok: false, error: "no list in the demo" }); });
      }
      return null;
    }
    // POST
    var b = {};
    try { b = body ? JSON.parse(body) : {}; } catch (e) { b = {}; }
    switch (name) {
      case "apply": return get("state").then(function (s) {
        s.choice.ears = b.ears || s.choice.ears; s.choice.brain = b.brain || s.choice.brain; s.choice.voice = b.voice || s.choice.voice;
        s.params.forEach(function (p) { if (b.params && b.params[p.id] !== undefined) { var v = b.params[p.id]; p.value = p.type === "int" ? parseInt(v, 10) : p.type === "float" ? parseFloat(v) : v; p.overridden = true; } });
        if (b.restart) { s.server.running = false; s.server.ready = false; setTimeout(function () { s.server.running = true; s.server.ready = true; }, 3000); }
        return json({ ok: true });
      });
      case "server": return get("state").then(function (s) {
        var a = b.action;
        if (a === "stop") { s.server.running = false; s.server.ready = false; }
        else { s.server.running = false; s.server.ready = false; setTimeout(function () { s.server.running = true; s.server.ready = true; }, 3000); }
        return json({ ok: true });
      });
      case "mood": return get("mood").then(function (m) { if (b.mood) m.current = b.mood; return json({ ok: true, current: m.current }); });
      case "memory/forget": return get("memory").then(function (m) {
        if (b.device) m.memory = m.memory.filter(function (x) { return x.device !== b.device; }); else m.memory = [];
        return json({ ok: true });
      });
      case "music/control":
        if (b.action === "stop") { mem.musicOn = false; clearTimeout(restartAt); restartAt = setTimeout(function () { mem.musicOn = true; song++; }, 20000); }   // in the demo a song starts again after a while
        else if (b.action === "next") { mem.musicOn = true; song++; }
        return Promise.resolve(json({ ok: true }));
      case "key": return get("key-check").then(function (k) { if (b.name && k[b.name]) k[b.name] = { state: "ok", msg: "works" }; return json({ ok: true }); });
      case "notify":
        if (b.action === "create" || b.action === "show") { mem.notifyOn = true; return Promise.resolve(json({ ok: true, topic: "jarvis-demo-example-topic", server: "ntfy.sh" })); }
        if (b.action === "off") mem.notifyOn = false;
        if (b.action === "save") mem.notifyEvents = { server: !!b.server, robot_offline_min: b.robot_offline_min || 0 };
        return Promise.resolve(json({ ok: true }));
      case "panel-option": return Promise.resolve(json({ ok: true, button_login: !!b.button_login }));
      case "test-llm": return wait(rnd(400, 900)).then(function () { return json({ ok: true, ms: Math.round(rnd(420, 760)), model: "gemini-3-flash-preview", text: "Jarvis online." }); });
      case "test-search": return wait(rnd(500, 1100)).then(function () { return json({ ok: true, ms: Math.round(rnd(600, 1000)), text: "3 results found (demo)" }); });
      case "test-ha": return wait(rnd(300, 700)).then(function () {
        return json({ ok: true, ms: Math.round(rnd(120, 260)), entities: 61, domains: { light: 14, switch: 9, sensor: 22, scene: 6, climate: 3, fan: 2, script: 5 } });
      });
      case "test-tts": return wait(500).then(function () { return new Response(sampleWav(), { status: 200, headers: { "Content-Type": "audio/wav" } }); });
    }
    return Promise.resolve(json({ ok: true }));
  }

  window.fetch = function (input, init) {
    var url = typeof input === "string" ? input : input.url;
    var method = ((init && init.method) || (typeof input !== "string" && input.method) || "GET").toUpperCase();
    var r = route(method, url, init && init.body);
    return r || realFetch(input, init);
  };

  // A thin ribbon, so nobody takes the demo for the real thing.
  document.addEventListener("DOMContentLoaded", function () {
    var b = document.createElement("div");
    b.textContent = "DEMO - made-up data, nothing here is connected to a robot or a server";
    b.style.cssText = "position:fixed;left:0;right:0;bottom:0;z-index:99999;text-align:center;font:600 11px system-ui,sans-serif;letter-spacing:.04em;padding:5px 8px;background:#f5b301;color:#222;opacity:.92;pointer-events:none";
    document.body.appendChild(b);
  });
})();
