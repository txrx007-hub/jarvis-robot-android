"""Builds the data of the demo app ("Jarvis Demo Mel"): app/src/demo/assets/demo/panel/{index.html, theme/, data/*.json}.

It asks a running control panel (default http://127.0.0.1:8090, opened from the PC itself, so no PIN) for the *shapes* of its answers and then replaces everything
that is the owner's: addresses, the PIN, log lines, memory, Home Assistant entities, robot health. What is left is checked against a list of private words and
patterns; the script stops with an error instead of writing anything when one is found.

    python -X utf8 tools/make_demo_data.py [panel_url] [panel_project_dir]

The panel project dir (default C:/Projects/xiaozhi-server/temp-test) is where panel.html and assets/themes/ are copied from.
demo.js (the fetch shim that plays the panel's server inside the app) is written by hand and stays in place.
"""
import copy
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

PANEL = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090").rstrip("/")
PROJECT = Path(sys.argv[2] if len(sys.argv) > 2 else "C:/Projects/xiaozhi-server/temp-test")
OUT = Path(__file__).resolve().parents[1] / "app" / "src" / "demo" / "assets" / "demo" / "panel"
DATA = OUT / "data"

PC_IP, ROBOT_IP, PIN = "192.168.1.10", "192.168.1.50", "123456"


def get(path):
    with urllib.request.urlopen(PANEL + path, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def scrub(o):
    """Walks any JSON value and rewrites its strings: addresses, windows paths."""
    if isinstance(o, str):
        s = re.sub(r"\b100\.\d{1,3}\.\d{1,3}\.\d{1,3}\b", PC_IP, o)
        s = re.sub(r"\b192\.168\.\d{1,3}\.\d{1,3}\b", PC_IP, s)
        s = s.replace("networking, data centers, IT infrastructure", "world news, technology, sports")
        return re.sub(r"[A-Za-z]:\\[^\"'\s,)]*", "C:\\\\jarvis\\\\music", s)
    if isinstance(o, list):
        return [scrub(x) for x in o]
    if isinstance(o, dict):
        return {k: scrub(v) for k, v in o.items()}
    return o


# ----------------------------------------------------------------------------------------------------------------- what is taken from the panel
state = get("/api/state")
options = get("/api/options")
usage = get("/api/usage?demo=1")
telemetry = get("/api/telemetry")
keys = get("/api/key-check")
mood = get("/api/mood")
brain_models = {}
for b in state["brains"]:
    try:
        brain_models[b["id"]] = get("/api/brain-models?brain=" + urllib.parse.quote(b["id"]))
    except Exception:
        pass

# What must never end up in the demo: the owner's own values, read from the live panel at run time (nothing of it is written in this file): the PIN, the names of the
# Home Assistant entities, the people and notes in the memory, the briefing topics.
REAL_PRIVATE = {str(state.get("pin") or "")}
REAL_PRIVATE |= {str(r.get("entity", "")) for r in get("/api/ha-log").get("rows", [])}
for dev in get("/api/memory").get("memory", []):
    notes = dev.get("notes") or {}
    for person, facts in (notes.get("people") or {}).items():
        REAL_PRIVATE.add(person)
        REAL_PRIVATE |= {str(x) for x in facts}
    for key in ("preferences", "open_items"):
        REAL_PRIVATE |= {str(x) for x in (notes.get(key) or [])}
    REAL_PRIVATE.add(str(dev.get("device", "")))
REAL_PRIVATE |= {str(p["value"]) for p in state["params"] if p["id"] == "briefing_topics" and p.get("value")}
REAL_PRIVATE = {x for x in REAL_PRIVATE if len(x) >= 4}

# ----------------------------------------------------------------------------------------------------------------- state
state = scrub(state)
state["choice"]["ip"] = PC_IP
state["robot_url"] = f"ws://{PC_IP}:8000/xiaozhi/v1/"
state["lan_url"] = f"http://{PC_IP}:8090"
state["pin"] = PIN
state["local"] = False
state["server"] = {"running": True, "pid": 4242, "ports": [8000, 8003], "ready": True}
state["keys"] = {k: (k in ("groq_api_key", "gemini_api_key", "tavily_api_key", "ha_url", "ha_token")) for k in state["keys"]}
for p in state["params"]:
    if p["id"] == "briefing_topics":
        p["value"] = "world news, technology, the weather"
    if p["id"] == "music_dir":
        p["value"] = "music"
state["log"] = []   # the shim writes the start-up lines (times of today)

# ----------------------------------------------------------------------------------------------------------------- keys: what the panel says when all is well
keys = {k: ({"state": "ok", "msg": v.get("msg", "works")} if v.get("state") == "ok" else v) for k, v in scrub(keys).items()}

# ----------------------------------------------------------------------------------------------------------------- music: made-up songs (the panel lists a count)
music = {"enabled": True, "dir": "C:\\jarvis\\music", "exists": True, "tracks": 24, "now": None}

# ----------------------------------------------------------------------------------------------------------------- memory: made-up household
memory = {"ok": True, "on": True, "memory": [
    {"device": "AA:BB:CC:DD:EE:FF", "notes": {
        "people": {"Sam": ["likes jazz in the evening", "has a birthday in May"], "Alex": ["is learning to cook", "allergic to peanuts"]},
        "preferences": ["short answers, please", "temperatures in Celsius"],
        "open_items": ["buy 3D printer filament", "call the dentist on Monday"]}},
]}

# ----------------------------------------------------------------------------------------------------------------- home assistant log: made-up entities, times relative (the shim shifts them to now)
ha_rows = [
    ("voice", "light.living_room", "light.turn_on", {"brightness_pct": 40}),
    ("voice", "scene.movie_night", "scene.turn_on", {}),
    ("voice", "light.kitchen", "light.turn_off", {}),
    ("voice", "switch.coffee_machine", "switch.turn_on", {}),
    ("voice", "climate.hallway", "climate.set_temperature", {"temperature": 20.5}),
    ("voice", "light.bedroom", "light.turn_on", {"rgb_color": [0, 0, 255]}),
    ("voice", "fan.office", "fan.turn_off", {}),
    ("voice", "light.living_room", "light.turn_off", {}),
]
ha_log = {"rows": [{"source": s, "entity": e, "service": sv, "data": d, "ok": True, "t": -(i * 1500 + 40)} for i, (s, e, sv, d) in enumerate(ha_rows)]}

# ----------------------------------------------------------------------------------------------------------------- telemetry: speed numbers are only timings; the robot's health is made up (a healthy one)
robot = {
    "now": {"t": 0, "up": True, "ip": ROBOT_IP, "uptime": 86400 + 5400, "rssi": -52, "heap": 61, "ram": 71, "psram": 3300, "cpu": 38,
            "napping": False, "power": "battery", "volume": 70, "reset": "power on"},
    "online": True, "last_seen": 0, "samples": 1440, "up_pct": 99,
    "rssi": {"min": -63, "avg": -53}, "heap": {"min": 48}, "reboots_day": 1,
    "reboots": [{"t": -90000, "gap_s": 4, "reason": "power on", "flags": [], "rssi": -52, "heap": 61, "napping": False}],
    "rssi_series": [[1440 - i * 10, -52 + ((i * 7) % 9) - 4] for i in range(144)],
}
robot["now"].update(temp=46.5, mic=-18.4, mic_rms=-41.0, clip=0, wdisc=0, wreason=0)
robot.update(temp={"min": 41.3, "max": 49.8}, mic={"minutes": 1380, "clip_minutes": 2, "clipped": 37, "loud_minutes": 9, "peak_max": 0.0}, wifi_drops_day=1)
conv_names = {"voice_edge_piper": "Voice: Edge failed, Piper spoke", "voice_piper_edge": "Voice: Piper failed, Edge spoke", "ears_cloud_local": "Ears: cloud failed, local Whisper",
              "brain_backup": "Brain: backup brain answered", "vision_backup": "Camera: backup model answered", "vision_apology": "Camera: no answer in time"}
conv = {"day": {"wakes": 23, "sessions": 21, "false_wakes": 3, "drops": 1, "heard": 58, "fallbacks": {"voice_edge_piper": 1, "brain_backup": 2}, "robot_wifi_drops": 1},
        "week": {"wakes": 141, "sessions": 133, "false_wakes": 17, "drops": 4, "heard": 402, "fallbacks": {"voice_edge_piper": 3, "brain_backup": 9, "vision_backup": 2}, "robot_wifi_drops": 6},
        "names": conv_names, "since": -6 * 86400}
telemetry = scrub(telemetry)
telemetry["robot"] = robot
telemetry["conv"] = conv
telemetry["system"]["pc"] = {"cpu": 7, "ram_pct": 41, "ram_free_gb": 18.9, "disk_free_gb": 311}
for s in telemetry["system"]["services"]:
    s["mem_mb"] = s["mem_mb"] and 480
telemetry["speed"]["recent"] = [dict(r, t=-(i * 420 + 30)) for i, r in enumerate(telemetry["speed"]["recent"])]

# ----------------------------------------------------------------------------------------------------------------- write
DATA.mkdir(parents=True, exist_ok=True)
files = {
    "state": state, "options": scrub(options), "usage": scrub(usage), "telemetry": telemetry, "key-check": keys, "mood": mood, "memory": memory,
    "music": music, "ha-log": ha_log,
}
for b, v in brain_models.items():
    files["brain-models_" + re.sub(r"\W+", "_", b)] = scrub(v)

PRIVATE = [
    r"\b(?!192\.168\.1\.(?:10|50)\b)\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b",   # any address but the two made-up ones (also catches versions like 1.2.3.4 - none expected)
    r"[A-Za-z]:\\\\(?!jarvis)", r"(?i)\\\\users\\\\", r"(?i)appdata", r"(?i)\badmin\b", r"\b[0-9a-f]{2}(:[0-9a-f]{2}){5}\b(?<!aa:bb:cc:dd:ee:ff)",
    r"[\w.+-]+@[\w-]+\.[a-z]{2,}", r"gmail", r"AIza[0-9A-Za-z_-]{20,}", r"gsk_[0-9A-Za-z]{20,}", r"tvly-", r"eyJ[0-9A-Za-z_-]{20,}",
] + ["(?i)" + re.escape(x) for x in sorted(REAL_PRIVATE)]
bad = []
for name, value in files.items():
    text = json.dumps(value, ensure_ascii=False)
    for pat in PRIVATE:
        for m in re.finditer(pat, text):
            bad.append((name, pat, text[max(0, m.start() - 30): m.end() + 30]))
if bad:
    for b in bad[:40]:
        print("PRIVATE?", b)
    sys.exit("stopped: nothing written (see above)")

for name, value in files.items():
    (DATA / (name + ".json")).write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print("data files:", len(files))

# ----------------------------------------------------------------------------------------------------------------- the page and the background photos
page = (PROJECT / "panel.html").read_text(encoding="utf-8")
assert "<head>" in page
page = page.replace("<head>", '<head>\n<script src="/demo.js"></script>', 1)
(OUT / "index.html").write_text(page, encoding="utf-8")
(OUT / "theme").mkdir(exist_ok=True)
for f in ("blueberry.webp", "succulent.webp"):
    (OUT / "theme" / f).write_bytes((PROJECT / "assets" / "themes" / f).read_bytes())
print("page and themes copied")
