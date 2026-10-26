"""Writes app/src/main/java/com/jarvis/robot/I18nWords.kt: the Dutch word list of the app.

Most of the Dutch texts are the landing page's (main/boards/wall-e-xiao/web/index.html, so the two always agree); the voice-command lists are paired from the
landing page's English and Dutch lists; the app's own texts are written below.

    python -X utf8 tools/gen_i18n.py        (needs node on the PATH to read the landing page's list)
"""
import json
import re
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
LANDING = HERE.parents[1] / "main" / "boards" / "wall-e-xiao" / "web" / "index.html"
OUT = HERE.parent / "app" / "src" / "main" / "java" / "com" / "jarvis" / "robot" / "I18nWords.kt"

NODE = r'''
const fs = require("fs");
const s = fs.readFileSync(process.argv[1], "utf8");
function slice(startMarker, endMarker) { const a = s.indexOf(startMarker), b = s.indexOf(endMarker, a); return s.slice(a, b); }
let NL, KEYS;
eval(slice("var NL = {", "  var KEYS").replace("var NL = ", "NL = ").replace(/;\s*$/, ""));
eval(slice("var KEYS = {", "  function dur").replace("var KEYS = ", "KEYS = ").replace(/;\s*$/, ""));
function grab(name) { const a = s.indexOf(name + " = ["); let i = s.indexOf("[", a), d = 0, j = i; for (; j < s.length; j++) { if (s[j] == "[") d++; else if (s[j] == "]") { d--; if (!d) break; } } return eval(s.slice(i, j + 1)); }
const en = grab("var VOICE_COMMANDS"), nl = grab("window.VOICE_COMMANDS_NL");
console.log(JSON.stringify({ NL, KEYS, en, nl }));
'''
data = json.loads(subprocess.run(["node", "-e", NODE, str(LANDING)], capture_output=True, text=True, encoding="utf-8", check=True).stdout)
words = dict(data["NL"])
keys = dict(data["KEYS"])

# the voice commands: groups, labels, descriptions (and the group names without their "Instant: " prefix, which is how the tiles show them)
for (gen, items_en, *_), (gnl, items_nl, *_) in zip(data["en"], data["nl"]):
    words[gen] = gnl
    words[re.sub(r"^Instant: ", "", gen)] = re.sub(r"^Direct: ", "", gnl)
    for (a, b), (c, d) in zip(items_en, items_nl):
        words[a] = c
        words[b] = d

# the app's own texts
words.update({
    "Back to the robot: status, power, volume and its settings": "Terug naar de robot: status, energie, volume en zijn instellingen",
    "Advanced": "Geavanceerd", "The backend control panel: servers, brains, voices and keys": "Het bedieningspaneel van de backend: servers, breinen, stemmen en sleutels",
    "Wi-Fi and the wake word go off for the time you choose, or until the BOOT button": "Wifi en het wekwoord gaan uit voor de gekozen tijd, of tot de BOOT-knop",
    "Deep sleep": "Diepe slaap",
    "Voice server": "Spraakserver", "Own server": "Eigen server", "Cloud (xiaozhi.me)": "Cloud (xiaozhi.me)", "Using the built-in default server.": "Gebruikt de ingebouwde standaardserver.",
    "Own server\u2026": "Eigen server\u2026", "Use a voice server on your own network instead of the cloud": "Gebruik een spraakserver op je eigen netwerk in plaats van de cloud",
    "Use cloud": "Cloud gebruiken", "Go back to the xiaozhi.me cloud server": "Ga terug naar de xiaozhi.me-cloudserver",
    "Open Jarvis backend": "Open Jarvis-backend", "Open Jarvis backend \u2197": "Open Jarvis-backend \u2197",
    "192.168.x.y or name.local": "192.168.x.y of naam.local",
    "Port 8003 is assumed; add :port to change it. Only addresses on your own network are accepted.": "Poort 8003 wordt aangenomen; voeg :poort toe om dat te wijzigen. Alleen adressen op je eigen netwerk worden geaccepteerd.",
    "Save and restart Jarvis": "Opslaan en Jarvis herstarten", "Change the voice server": "De spraakserver wijzigen", "Save": "Opslaan",
    "Take a picture": "Maak een foto", "Take another": "Maak er nog een", "Photo from Jarvis's camera": "Foto van Jarvis\u2019 camera", "Hide picture": "Verberg foto",
    "Show full screen": "Toon op volledig scherm", "Photo from Jarvis's camera, full screen": "Foto van Jarvis\u2019 camera, volledig scherm",
    "Close full screen": "Sluit volledig scherm",
    "Higher if photos come out dark, lower if bright spots wash out.": "Hoger als foto’s te donker zijn, lager als lichte plekken overbelicht raken.",
    "Nothing playing. Say: Jarvis, play something random.": "Er speelt niets. Zeg: Jarvis, play something random.", "Next song": "Volgend nummer",
    "Live view from Jarvis's camera": "Live beeld van Jarvis\u2019 camera", "Drive forward": "Vooruit rijden", "Turn left": "Links draaien", "Turn right": "Rechts draaien",
    "Drive backward": "Achteruit rijden",
    "Hold an arrow to drive (full speed), let go to stop. Two arrows together turn in an arc.": "Houd een pijl ingedrukt om te rijden (volle snelheid), laat los om te stoppen. Twee pijlen samen draaien in een boog.",
    "Shows what the robot sees while you drive: a new picture about once a second, only while this screen is open (stops after 3 minutes)": "Toont wat de robot ziet terwijl je rijdt: ongeveer elke seconde een nieuw beeld, alleen zolang dit scherm open is (stopt na 3 minuten)",
    "Drive forward, button, hold to drive": "Vooruit rijden, knop, ingedrukt houden om te rijden", "Driving": "Rijdt", "Hold to drive": "Ingedrukt houden om te rijden",
    "Light mode": "Lichte modus",
    "minutes of idle before the clock shows": "minuten inactiviteit voordat de klok verschijnt", "city for the weather line on the idle clock": "plaats voor de weerregel op de inactiviteitsklok",
    "what the screen shows while Jarvis speaks": "wat het scherm toont terwijl Jarvis praat", "the picture that replaces the eyes while music plays": "de afbeelding die de ogen vervangt terwijl er muziek speelt",
    "serial log detail": "detail van het seri\u00eble log", "Demo: deep sleep is pretended (the robot is napping).": "Demo: diepe slaap wordt gespeeld (de robot doet een dutje).",
    "The Jarvis backend page shows here": "De Jarvis-backendpagina verschijnt hier",
    "the picture could not be read": "de foto kon niet worden gelezen", "Live view stopped after 3 minutes.": "Live beeld gestopt na 3 minuten.",
    "Saving...": "Opslaan...", "Saved. Jarvis restarts.": "Opgeslagen. Jarvis herstart.", "Not saved.": "Niet opgeslagen.",
    "Address": "Adres", "Lost the connection to Jarvis. It may be napping off WiFi, asleep, or out of range.": "De verbinding met Jarvis is weg. Hij doet misschien een dutje zonder wifi, slaapt, of is buiten bereik.",
    "Change address": "Adres wijzigen", "Connect to another robot": "Verbinden met een andere robot", "Hide commands": "Verberg opdrachten",
    "Reset all settings?": "Alle instellingen terugzetten?", "Every setting goes back to its default. This cannot be undone.": "Elke instelling gaat terug naar de standaardwaarde. Dit kan niet ongedaan worden gemaakt.",
    "Could not reset the settings.": "Kon de instellingen niet terugzetten.",
    "Connect to Jarvis": "Verbinden met Jarvis",
    "Enter the robot's IP address (shown on its screen status and by !status over USB), or scan your WiFi network for it. This phone must be on the same WiFi as the robot.": "Voer het IP-adres van de robot in (te zien op zijn scherm en via !status over USB), of scan je wifi-netwerk ernaar. Deze telefoon moet op hetzelfde wifi zitten als de robot.",
    "Robot address": "Robotadres", "Checking...": "Controleren...", "Connect": "Verbinden",
    "Only addresses on your own network are accepted (192.168.x.x, 10.x.x.x, 172.16-31.x.x, a .local name, or Tailscale 100.x.x.x).": "Alleen adressen op je eigen netwerk worden geaccepteerd (192.168.x.x, 10.x.x.x, 172.16-31.x.x, een .local-naam of Tailscale 100.x.x.x).",
    "Scanning...": "Scannen...", "Scan network": "Netwerk scannen", "Looks for the robot on your Wi-Fi": "Zoekt de robot op je wifi",
    "This phone isn't on a WiFi network.": "Deze telefoon zit niet op een wifi-netwerk.", "Use this address anyway": "Dit adres toch gebruiken",
    "Found a Jarvis here \u2014 tap to use it": "Hier een Jarvis gevonden \u2014 tik om te gebruiken",
    "Not reachable": "Niet bereikbaar", "Robot settings": "Robotinstellingen", "App": "App", "Day or night": "Dag of nacht", "Follow the device": "Volg het apparaat",
    "The Advanced page (the backend control panel) is there when Jarvis talks to your own server.": "De pagina Geavanceerd (het bedieningspaneel van de backend) is er zodra Jarvis met je eigen server praat.",
    "Reset all robot settings to defaults": "Alle robotinstellingen terugzetten naar standaard",
    "Overview": "Overzicht", "Backend": "Backend", "Appearance": "Weergave",
    # the three command texts that say "app" where the landing page says "page"
    "\u201cshow me a QR code for your settings page\u201d (Jarvis shows a code that opens the settings page)": "\u201cshow me a QR code for your settings page\u201d (Jarvis toont een code die de instellingenpagina opent)",
    "\u201cnext song\u201d, \u201cstop the music\u201d, \u201cwhat is this song?\u201d, or the Stop and Next buttons in this app": "\u201cnext song\u201d, \u201cstop the music\u201d, \u201cwhat is this song?\u201d, of de knoppen Stop en Volgende in deze app",
    "\u201cstop the radio\u201d, \u201cstop\u201d, or the Stop button in this app": "\u201cstop the radio\u201d, \u201cstop\u201d, of de knop Stop in deze app",
})


def kt(s):
    return '"' + (s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n").replace("\r", "")) + '"'


items = sorted(words.items(), key=lambda kv: kv[0])
CH = 40
lines = [
    "package com.jarvis.robot",
    "",
    "// GENERATED by tools/gen_i18n.py - edit that script (or the landing page's list), not this file.",
    "// The Dutch word list of the app: what is shown for an English text when the language is Dutch (see I18n.kt).",
    "object I18nNl {",
    "    val words: Map<String, String> by lazy {",
    "        HashMap<String, String>(" + str(len(items) * 2) + ").apply {",
]
for i in range(0, len(items), CH):
    lines.append(f"            putAll(chunk{i // CH}())")
lines += ["        }", "    }", ""]
lines.append("    /** The Dutch names of the settings, by key (used in the robot's own messages, like \"motor_trim changed from 0 to 3\"). */")
lines.append("    val keys: Map<String, String> = mapOf(")
for k, v in sorted(keys.items()):
    lines.append(f"        {kt(k)} to {kt(v)},")
lines.append("    )")
lines.append("")
for i in range(0, len(items), CH):
    lines.append(f"    private fun chunk{i // CH}(): Map<String, String> = mapOf(")
    for k, v in items[i:i + CH]:
        lines.append(f"        {kt(k)} to {kt(v)},")
    lines.append("    )")
    lines.append("")
lines.append("}")
OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("wrote", OUT, len(items), "texts,", len(keys), "setting names")
