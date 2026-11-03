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
    "Higher if photos come out dark, lower if bright spots wash out.": "Hoger als fotoâ€™s te donker zijn, lager als lichte plekken overbelicht raken.",
    "Camera colour": "Camerakleur",
    "Higher for stronger colours, lower for paler ones. 0 is the camera's own.": "Hoger voor sterkere kleuren, lager voor flauwere. 0 is die van de camera zelf.",
    "Nothing playing. Say: Jarvis, play something random.": "Er speelt niets. Zeg: Jarvis, play something random.", "Next song": "Volgend nummer",
    "Live view from Jarvis's camera": "Live beeld van Jarvis\u2019 camera", "Drive forward": "Vooruit rijden", "Turn left": "Links draaien", "Turn right": "Rechts draaien",
    "Drive backward": "Achteruit rijden",
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

# Jarvis v2 (V2*.kt): added only where the lists above have no Dutch yet, so the classic app's wording never changes
V2 = {
    "1 voice enrolled": "1 stem ingeschreven", "Next": "Volgende",
    # the app's own backend pages, the rebuilt sheets and the connect screen
    "1 voice sample": "1 stemvoorbeeld", "A small model on the PC compares each sentence with the voices below": "Een klein model op de pc vergelijkt elke zin met de stemmen hieronder",
    "A small picture about every 20 s while it is idle; two sure looks and it speaks": "Een kleine foto ongeveer elke 20 s als hij niets doet; twee zekere blikken en hij praat",
    "Add a sentence to this voice": "Voeg een zin toe aan deze stem", "Anyone": "Iedereen", "Only me": "Alleen ik", "At most per day": "Hoogstens per dag", "Once per": "Eens per",
    "Checkingâ€¦": "Controlerenâ€¦", "Scanningâ€¦": "Scannenâ€¦", "Could not reach the backend. It is asked again in a moment.": "De backend is niet bereikt. Hij wordt zo opnieuw gevraagd.",
    "Details": "Details", "Enrol (6 sentences)": "Inschrijven (6 zinnen)", "Enrol a new voice": "Een nieuwe stem inschrijven", "Enrolled voices": "Ingeschreven stemmen",
    "Everything Jarvis noted about the people it talks to is deleted. It starts again with the next conversation.":
        "Alles wat Jarvis noteerde over de mensen met wie hij praat wordt gewist. Hij begint opnieuw bij het volgende gesprek.",
    "Forget": "Vergeten", "Forget the notes?": "De notities vergeten?", "Forget these notes": "Deze notities vergeten", "Forget this voice": "Vergeet deze stem", "Forget this voice?": "Deze stem vergeten?",
    "Found a Jarvis here Â· tap to connect": "Hier een Jarvis gevonden Â· tik om te verbinden", "Go back to the xiaozhi.me cloud? Jarvis restarts.": "Terug naar de xiaozhi.me-cloud? Jarvis herstart.",
    "Greet now (test)": "Nu begroeten (test)", "Greet when it sees you": "Begroeten als hij je ziet",
    "Higher = fewer strangers get your name, but more of your own sentences stay unlabelled.": "Hoger = minder vreemden krijgen jouw naam, maar meer van je eigen zinnen blijven zonder label.",
    "How sure the face finder must be that it is you (it calls 0.35 and up you).": "Hoe zeker de gezichtszoeker moet zijn dat jij het bent (vanaf 0,35 noemt hij het jou).",
    "Jarvis says good morning when it sees you": "Jarvis zegt goedemorgen als hij je ziet", "Last greetings": "Laatste begroetingen", "Less": "Minder", "More": "Meer", "Limits": "Grenzen",
    "Match needed": "Nodige overeenkomst", "Music and radio": "Muziek en radio", "Music folder": "Muziekmap", "Name": "Naam", "No greeting in this time": "Geen begroeting in deze tijd",
    "Nothing remembered yet.": "Nog niets onthouden.", "Off Â· switched in the panel": "Uit Â· wordt in het paneel geschakeld", "Off: no folder set on the PC": "Uit: geen map ingesteld op de pc",
    "On Â· switched in the panel (it restarts the voice server)": "Aan Â· wordt in het paneel geschakeld (dat herstart de spraakserver)", "Open in the panel": "Openen in het paneel",
    "Open items": "Open punten", "Open the backend in the browser": "De backend in de browser openen", "Own server address": "Adres van de eigen server", "Preferences": "Voorkeuren",
    "Quiet hours": "Stille uren", "Quiet now": "Nu stil", "Recent matches": "Recente overeenkomsten", "Recognise voices": "Stemmen herkennen", "Someone": "Iemand",
    "Stayed silent because of": "Stil gebleven door", "Stop (keep what was learned)": "Stoppen (bewaar wat is geleerd)", "Strictness": "Strengheid", "Sure match from": "Zeker vanaf",
    "This phone must be on the same Wi-Fi as the robot. Its address is on the robot's screen status, or let the app look for it.":
        "Deze telefoon moet op hetzelfde wifi zitten als de robot. Het adres staat in de status op het scherm van de robot, of laat de app ernaar zoeken.",
    "This robot's firmware has no volume control.": "De firmware van deze robot heeft geen volumeregeling.", "Today": "Vandaag", "Try saying": "Zeg bijvoorbeeld",
    "Turn to me first": "Eerst naar mij draaien", "Turns until your face is in the middle, then speaks": "Draait tot je gezicht in het midden staat, en praat dan",
    "What Jarvis remembers about the people it talks to": "Wat Jarvis onthoudt over de mensen met wie hij praat", "When Jarvis is quieter at night": "Wanneer Jarvis â€™s nachts zachter is",
    "Who": "Wie", "Who is speaking: label only, nothing is blocked": "Wie er praat: alleen een label, er wordt niets geblokkeerd",
    "a known voice": "een bekende stem", "sentences checked": "zinnen gecontroleerd", "per check": "per controle", "hours, for the same person": "uur, voor dezelfde persoon", "your name": "je naam",
    "a look failed": "een blik mislukte", "daily cap reached": "dagmaximum bereikt", "follow mode on": "volgmodus aan", "just talked to Jarvis": "net met Jarvis gepraat",
    "microphone muted": "microfoon uit", "music or radio playing": "muziek of radio speelt", "nobody seen": "niemand gezien", "not sure it is you": "niet zeker dat jij het bent",
    "quiet hours": "stille uren", "robot busy (talking, listening)": "robot bezig (praat, luistert)", "robot not reachable": "robot niet bereikbaar", "somebody else": "iemand anders",
    "waiting for the cooldown": "wacht op de pauze", "Status": "Status", "Chip temperature": "Chiptemperatuur", "Camera detected": "Camera gevonden", "MAC address": "MAC-adres", "Mode": "Modus", "Uptime": "Aan sinds",
    "Board": "Bord", "Console": "Console", "Battery": "Batterij", "15 min": "15 min", "15 minutes less": "15 minuten minder", "15 minutes more": "15 minuten meer",
    "All": "Alles", "All details": "Alle details", "Fewer details": "Minder details", "All settings reset to defaults.": "Alle instellingen staan weer op standaard.",
    "Asking the backend\u2026": "Backend wordt gevraagd\u2026", "Audio and wake word": "Geluid en wekwoord", "Auto": "Auto", "Back": "Terug",
    "Backend panel": "Backendpaneel", "Backward": "Achteruit", "Forward": "Vooruit", "Brain": "Brein", "Brain model, web search": "Breinmodel, zoeken op het web",
    "Camera": "Camera", "Change robot or address": "Andere robot of ander adres", "Changes save on the robot at once.": "Wijzigingen worden meteen op de robot opgeslagen.",
    "Changes apply and save right away. On your home network only.": "Wijzigingen gaan meteen in en worden bewaard. Alleen op je thuisnetwerk.",
    "Check the address and that this phone is on the robot's Wi-Fi.": "Controleer het adres en of deze telefoon op het wifi van de robot zit.",
    "Chip": "Chip", "Clear": "Wissen", "Colours": "Kleuren", "Connecting to its server": "Verbindt met zijn server", "Connecting\u2026": "Verbinden\u2026",
    "Dark": "Donker", "Light": "Licht", "Drive": "Rijden", "Ears, speech detector, smart turn": "Oren, spraakdetector, smart turn",
    "English": "English", "Nederlands": "Nederlands", "Face and movement": "Gezicht en beweging", "Face me": "Kijk naar mij",
    "Faces, voices, greeter": "Gezichten, stemmen, begroeten", "Follow me": "Volg mij", "Going to sleep": "Gaat slapen",
    "Good morning when it sees you": "Goedemorgen als hij je ziet", "Greeter": "Begroeten", "Health": "Gezondheid", "Hearing": "Horen",
    "Drag the knob to drive · let go to stop": "Sleep de knop om te rijden · laat los om te stoppen",
    "Home": "Start", "House": "Huis", "instant": "direct", "music": "muziek", "Home Assistant, songs, radio": "Home Assistant, nummers, radio", "Home and music": "Huis en muziek",
    "How it sounds, what it remembers": "Hoe hij klinkt, wat hij onthoudt",
    "It may be napping off Wi-Fi, asleep, or out of range. Pull down to try again.": "Hij doet misschien een dutje zonder wifi, slaapt, of is buiten bereik. Trek omlaag om het opnieuw te proberen.",
    "Jarvis has not said which voice server it uses yet.": "Jarvis heeft nog niet gezegd welke spraakserver hij gebruikt.", "Jarvis is not reachable": "Jarvis is niet bereikbaar",
    "Jarvis talks to the xiaozhi.me cloud now. The brain settings live on your own voice server: set it under Settings \u203a Voice server.":
        "Jarvis praat nu met de xiaozhi.me-cloud. De breininstellingen staan op je eigen spraakserver: stel die in onder Instellingen \u203a Spraakserver.",
    "Jarvis turns off Wi-Fi and the screen and wakes up by itself after the time you pick, or when you press its BOOT button.":
        "Jarvis zet wifi en het scherm uit en wordt vanzelf wakker na de gekozen tijd, of als je op zijn BOOT-knop drukt.",
    "Language": "Taal", "Last photo": "Laatste foto", "Listening": "Luistert", "Live": "Live", "Live view from the robot's camera": "Live beeld van de camera van de robot",
    "Live view is off": "Live beeld staat uit", "Loading settings\u2026": "Instellingen laden\u2026", "Look": "Weergave", "Lost the connection to Jarvis": "Verbinding met Jarvis verloren",
    "Louder": "Harder", "Quieter": "Zachter", "Memory": "Geheugen", "Memory is switched on the panel: it restarts the voice server.": "Geheugen zet je aan of uit in het paneel: dat herstart de spraakserver.",
    "Mood and memory": "Stemming en geheugen", "Motors": "Motoren", "Mute": "Dempen", "Unmute": "Dempen uit", "Nap": "Dutje", "Napping": "Doet een dutje", "Wake up": "Wakker worden",
    "Needs your own voice server": "Heeft je eigen spraakserver nodig", "Nobody enrolled yet": "Nog niemand ingeschreven", "Not on this backend": "Niet op deze backend",
    "Notes about the people it talks to": "Notities over de mensen met wie hij praat", "Nothing playing": "Er speelt niets", "Off": "Uit", "On": "Aan",
    "Off \u00b7 turns to your face": "Uit \u00b7 draait naar je gezicht", "On \u00b7 keeps turning to you": "Aan \u00b7 blijft naar je draaien", "Offline": "Offline", "Online": "Online",
    "Open the full panel": "Open het hele paneel", "Other time": "Andere tijd", "Photo": "Foto", "Ping": "Ping", "Ready to listen": "Klaar om te luisteren",
    "Reply speed, fallbacks, quotas": "Antwoordsnelheid, reserves, quota", "Reset": "Terugzetten", "Reset robot settings\u2026": "Robotinstellingen terugzetten\u2026",
    "Robot details": "Robotdetails", "Robot, services, alerts": "Robot, diensten, meldingen", "STOP": "STOP", "Say": "Zeggen",
    "Say \u201cJarvis\u201d first, then one of these": "Zeg eerst \u201cJarvis\u201d, dan een van deze", "Say: Jarvis, play something random.": "Zeg: Jarvis, play something random.",
    "Screen and quiet hours": "Scherm en stille uren", "Seeing": "Zien", "Setting up Wi-Fi": "Wifi instellen", "Settings": "Instellingen",
    "Settings show once Jarvis is reachable.": "De instellingen verschijnen zodra Jarvis bereikbaar is.", "Sleep until the button": "Slapen tot de knop", "Sleep\u2026": "Slapen\u2026",
    "Smart turn": "Smart turn", "Speaking": "Spreken", "Speed and usage": "Snelheid en gebruik", "Start live view": "Live beeld starten", "Stop live view": "Live beeld stoppen",
    "Starting": "Start op", "Stop": "Stop", "Take a photo": "Maak een foto", "Taking a photo\u2026": "Foto maken\u2026", "Talking": "Praat",
    "The model file is missing": "Het modelbestand ontbreekt", "The speaker model file is missing": "Het sprekermodel ontbreekt", "The voice server on your PC": "De spraakserver op je pc",
    "Thinking": "Denken", "This app": "Deze app", "Try \u201ctimer\u201d or \u201cdance\u201d": "Probeer \u201ctimer\u201d of \u201cdans\u201d", "Until the button": "Tot de knop", "Updating": "Bijwerken",
    "Voice recognition": "Stemherkenning", "Voice, personality": "Stem, persoonlijkheid", "Volume": "Volume", "Waiting for the first picture\u2026": "Wachten op het eerste beeld\u2026",
    "Waits when you pause mid-sentence": "Wacht als je halverwege een zin pauzeert", "Weather": "Weer", "What can I say?": "Wat kan ik zeggen?", "While Jarvis talks": "Terwijl Jarvis praat",
    "Wi-Fi dBm": "Wifi dBm", "not found": "niet gevonden", "not set": "niet ingesteld", "own voice server": "eigen spraakserver", "xiaozhi.me cloud": "xiaozhi.me-cloud",
    "Visualizer": "Visualisatie", "Voice server": "Spraakserver", "Own server": "Eigen server", "Change address": "Adres wijzigen", "Deep sleep": "Diepe slaap",
    "(leave empty for off)": "(leeg laten voor uit)", "e.g. Amsterdam (leave empty for off)": "bijv. Amsterdam (leeg laten voor uit)",
    # mood, Home Assistant, health / speed / usage pages
    "A backup brain answered": "Een reservebrein antwoordde", "API keys, debug console": "API-sleutels, foutopsporing", "Access token": "Toegangstoken",
    "Address and token in the panel": "Adres en token in het paneel", "Ask first": "Eerst vragen", "At once": "Meteen", "Never": "Nooit", "Automations": "Automatiseringen",
    "Backups in the last 24 h": "Reserves in de laatste 24 uur", "Blinds and curtains": "JaloezieÃ«n en gordijnen", "Brain answers": "Antwoorden van het brein",
    "Brain requests": "Verzoeken aan het brein", "Check Groq's limits now": "Limieten van Groq nu controleren", "Conversations": "Gesprekken",
    "Could not read the settings.": "De instellingen konden niet worden gelezen.", "Did not work.": "Werkte niet.", "Fans": "Ventilators",
    "Health and usage": "Gezondheid en gebruik", "Heating and cooling": "Verwarming en koeling", "Helper switches": "Hulpschakelaars", "Home Assistant": "Home Assistant",
    "How Jarvis sounds": "Hoe Jarvis klinkt", "How Jarvis sounds. It applies at once.": "Hoe Jarvis klinkt. Het gaat meteen in.", "Instant commands": "Directe commandoâ€™s",
    "It gave up after too many restarts: look at the PC.": "Hij gaf het op na te veel herstarts: kijk bij de pc.",
    "It is gone for about 10 seconds. The robot reconnects by itself.": "Hij is zoâ€™n 10 seconden weg. De robot verbindt vanzelf opnieuw.",
    "Jarvis answers the way it always does.": "Jarvis antwoordt zoals altijd.",
    "Jarvis stops talking until it is started again (here, or on the PC).": "Jarvis houdt op met praten tot hij weer wordt gestart (hier, of op de pc).",
    "Keys and logs": "Sleutels en logboek", "Lamps": "Lampen", "Last 24 h": "Laatste 24 uur", "Last 7 days": "Laatste 7 dagen", "Latest actions": "Laatste acties",
    "Latest answers": "Laatste antwoorden", "Latest restarts of the robot": "Laatste herstarts van de robot",
    "Locks, alarms and garage doors are always refused. â€œAsk firstâ€ means Jarvis does it after you say yes.":
        "Sloten, alarmen en garagedeuren worden altijd geweigerd. â€œEerst vragenâ€ betekent dat Jarvis het doet nadat jij ja zegt.",
    "Mood": "Stemming", "No fallbacks needed: every service answered.": "Geen reserves nodig: elke dienst antwoordde.",
    "No spoken turns yet in this time.": "Nog geen gesproken beurten in deze periode.", "Normal": "Normaal", "Cheerful": "Vrolijk", "Calm": "Kalm", "Serious": "Serieus", "Dry wit": "Droog",
    "Nothing done yet.": "Nog niets gedaan.", "PC memory used": "pc-geheugen gebruikt", "PC processor": "pc-processor", "Plugs and switches": "Stekkers en schakelaars",
    "Requests per day, last 14 days": "Verzoeken per dag, laatste 14 dagen", "Restart": "Herstarten", "Restart server": "Server herstarten", "Restart the voice server?": "De spraakserver herstarten?",
    "Robot is online": "Robot is online", "Robot not answering": "Robot antwoordt niet", "Robot, server, speed, quotas": "Robot, server, snelheid, quota",
    "Robot, voice server, speed and quotas": "Robot, spraakserver, snelheid en quota", "Save (restarts the voice server)": "Opslaan (herstart de spraakserver)",
    "Save and restart?": "Opslaan en herstarten?", "Saved. The voice server restarts now.": "Opgeslagen. De spraakserver herstart nu.", "Scenes": "ScÃ¨nes", "Scripts": "Scripts",
    "Speed": "Snelheid", "Start": "Start", "Stop the voice server?": "De spraakserver stoppen?", "Starting the voice serverâ€¦": "De spraakserver startâ€¦",
    "Stopping the voice serverâ€¦": "De spraakserver stoptâ€¦", "Restarting the voice serverâ€¦": "De spraakserver herstartâ€¦", "Could not start it.": "Kon niet starten.",
    "TVs and speakers": "Tvâ€™s en speakers", "Tavily search": "Tavily-zoeken", "Test the connection": "Verbinding testen", "Testingâ€¦": "Testenâ€¦",
    "The backend did not accept that.": "De backend accepteerde dat niet.", "The backend did not answer.": "De backend antwoordde niet.",
    "The main brain answers": "Het hoofdbrein antwoordt",
    "The voice server restarts to apply this (about 10 seconds). Jarvis reconnects by itself.": "De spraakserver herstart om dit toe te passen (ongeveer 10 seconden). Jarvis verbindt vanzelf opnieuw.",
    "Updated.": "Bijgewerkt.", "Usage": "Gebruik", "Vacuum robot": "Stofzuigrobot", "Voice control of the house": "Spraakbesturing van het huis",
    "Voice server and PC": "Spraakserver en pc", "Watchdog is off": "Waakhond staat uit", "Watchdog is on": "Waakhond staat aan", "What Jarvis may do": "Wat Jarvis mag doen",
    "Wi-Fi average, dBm": "Wifi gemiddeld, dBm", "Wi-Fi drops": "Wifi-onderbrekingen", "Wi-Fi weakest, dBm": "Wifi zwakst, dBm",
    "You can also say: be cheerful, be calm, be serious, be dry, or back to normal.": "Je kunt ook zeggen: wees vrolijk, wees kalm, wees serieus, wees droog, of terug naar normaal.",
    "chip, hottest": "chip, heetst", "credits today": "credits vandaag", "disk free": "schijf vrij", "false wake-ups": "valse wekkingen", "hearing you": "horen", "thinking": "denken",
    "memory low point": "geheugen laagste punt", "mic clipping": "microfoon clipt", "ping to the PC": "ping naar de pc", "requests today": "verzoeken vandaag",
    "restarts, 24 h": "herstarts, 24 u", "tokens today": "tokens vandaag", "up in 24 h": "online in 24 u", "wake-ups": "wekkingen", "conversations": "gesprekken",
    "Not seen yet": "Nog niet gezien", "just now": "zojuist", "Wi-Fi, average": "Wifi, gemiddeld",
}
for _k, _v in V2.items():
    words.setdefault(_k, _v)

# the second round of backend pages (mood, Home Assistant, health and usage, tabs Robot / Speed / Usage / System); setdefault again, so earlier wording wins
V2B = {
    "24 hours": "24 uur", "7 days": "7 dagen", "Almost used up": "Bijna op", "Getting full": "Raakt vol", "Ask": "Vraag", "Now": "Nu", "No": "Nee",
    "Applies at once, no restart. You can also say â€œbe cheerfulâ€, â€œbe calmâ€, â€œbe seriousâ€, â€œbe dryâ€ or â€œback to normalâ€.":
        "Gaat meteen in, zonder herstart. Je kunt ook zeggen: â€œbe cheerfulâ€, â€œbe calmâ€, â€œbe seriousâ€, â€œbe dryâ€ of â€œback to normalâ€.",
    "Asking Home Assistantâ€¦": "Home Assistant wordt gevraagdâ€¦", "Brain failovers, 24 h": "Reservebreinen, 24 u",
    "Check Groq limits now": "Groq-limieten nu controleren",
    "Check Groq's live numbers now. This costs 1 of the 1,000 free requests a day.": "Controleer de live cijfers van Groq. Dit kost 1 van de 1.000 gratis verzoeken per dag.",
    "Connected": "Verbonden", "Could not restart the voice server.": "De spraakserver kon niet herstarten.", "Could not save": "Opslaan lukte niet",
    "Counts what goes through Jarvis. Tavily and Groq also show their own live numbers, which include everything on those keys.":
        "Telt wat via Jarvis loopt. Tavily en Groq tonen ook hun eigen live cijfers, inclusief alles wat op die sleutels gebeurt.",
    "Credits per day, last 14 days": "Tegoed per dag, laatste 14 dagen", "Credits this billing cycle": "Tegoed in deze factuurperiode",
    "DOWN": "UIT", "Estimated cost": "Geschatte kosten", "Fallbacks": "Reserves", "Free PSRAM": "Vrije PSRAM", "In use": "In gebruik",
    "Jarvis stops talking for about 10 seconds and reconnects by itself.": "Jarvis zwijgt ongeveer 10 seconden en verbindt zelf weer.",
    "Largest free block": "Grootste vrije blok", "Largest free memory block": "Grootste vrije geheugenblok", "Last 24 hours": "Laatste 24 uur",
    "Latest restarts": "Laatste herstarts", "Latest turns": "Laatste beurten", "Microphone, last minute": "Microfoon, laatste minuut",
    "Measured on the PC from the end of your sentence to the first sound sent to the robot. Green under 2.5 s, amber under 4.5 s.":
        "Gemeten op de pc vanaf het einde van je zin tot het eerste geluid naar de robot. Groen onder 2,5 s, oranje onder 4,5 s.",
    "No data": "Geen gegevens", "No readings yet. The panel looks at the robot once a minute while it runs.": "Nog geen metingen. Het paneel kijkt Ã©Ã©n keer per minuut naar de robot zolang het draait.",
    "No spoken turns recorded in this period yet. Each time you talk to Jarvis, the time from the end of your sentence to the first sound shows up here.":
        "In deze periode zijn nog geen gesproken beurten vastgelegd. Elke keer dat je met Jarvis praat, verschijnt hier de tijd van het einde van je zin tot het eerste geluid.",
    "Normal: no extra mood instruction.": "Normaal: geen extra stemmingsopdracht.", "Not answering": "Antwoordt niet", "Not checked yet": "Nog niet gecontroleerd",
    "Not set up": "Niet ingesteld", "Not set up: events are only written to the list below.": "Niet ingesteld: gebeurtenissen komen alleen in de lijst hieronder.",
    "Nothing needed a backup in this period.": "In deze periode was geen reserve nodig.",
    "Now = switched straight away Â· Ask = only after you say yes Â· No = refused. Locks and alarms are always refused; that can only be changed in the panel.":
        "Nu = meteen geschakeld Â· Vraag = pas nadat je ja zegt Â· Nee = geweigerd. Sloten en alarmen worden altijd geweigerd; dat kun je alleen in het paneel wijzigen.",
    "Open the panel": "Open het paneel", "Paid tier: the free daily limits do not apply.": "Betaald abonnement: de gratis daglimieten gelden niet.",
    "Phone alerts": "Telefoonmeldingen", "Reply speed": "Antwoordsnelheid", "Requests today (Groq live)": "Verzoeken vandaag (Groq live)",
    "Restart the voice server": "De spraakserver herstarten", "Restarting. The robot reconnects by itself in about 10 seconds.": "Herstart. De robot verbindt zelf weer over ongeveer 10 seconden.",
    "Restartingâ€¦": "Herstartenâ€¦", "Restarts the voice server when it is down or stops answering (at most 3 times in 15 minutes).":
        "Herstart de spraakserver als die uit staat of niet meer antwoordt (hoogstens 3 keer in 15 minuten).",
    "Robot": "Robot", "Robot health": "Gezondheid van de robot", "Robot, reply speed, free quotas, services": "Robot, antwoordsnelheid, gratis quota, diensten",
    "Save and restart the voice server": "Opslaan en de spraakserver herstarten", "Saved. The voice server restarts now, about 10 seconds.": "Opgeslagen. De spraakserver herstart nu, ongeveer 10 seconden.",
    "Savingâ€¦": "Opslaanâ€¦", "Send a test alert": "Testmelding sturen", "Sent: it should arrive on the phone in a few seconds.": "Verstuurd: de melding hoort over een paar seconden op de telefoon te komen.",
    "Server watchdog": "Serverbewaking", "Services & this PC": "Diensten en deze pc", "Services on the PC": "Diensten op de pc", "Test connection": "Verbinding testen",
    "The address and the access token go under API keys in the panel.": "Het adres en de toegangstoken staan onder API-sleutels in het paneel.",
    "The alert switches (which events reach the phone) are in the panel.": "De meldingsschakelaars (welke gebeurtenissen de telefoon bereiken) staan in het paneel.",
    "The test could not be sent.": "De test kon niet worden verstuurd.",
    "The voice server restarts to use the new rules. That takes about 10 seconds; the robot reconnects by itself.":
        "De spraakserver herstart om de nieuwe regels te gebruiken. Dat duurt ongeveer 10 seconden; de robot verbindt zelf weer.",
    "Tokens per day, last 14 days": "Tokens per dag, laatste 14 dagen", "Tokens this minute (Groq live)": "Tokens deze minuut (Groq live)",
    "Tavily Â· web search": "Tavily Â· zoeken op het web", "Gemini Â· brain and camera": "Gemini Â· brein en camera", "Groq Â· brain": "Groq Â· brein",
    "Cerebras Â· brain": "Cerebras Â· brein", "Claude Â· brain (paid)": "Claude Â· brein (betaald)",
    "Try: â€œJarvis, turn off the kitchen light.â€": "Probeer: â€œJarvis, turn off the kitchen light.â€",
    "Watchdog & phone alerts": "Bewaking en telefoonmeldingen", "Watchdog and phone alerts": "Bewaking en telefoonmeldingen", "Voice server": "Spraakserver",
    "Wi-Fi drops in 24 h": "Wifi-onderbrekingen in 24 u", "Wi-Fi drops reported by the robot": "Wifi-onderbrekingen gemeld door de robot", "Wi-Fi signal": "Wifi-signaal",
    "answers from a backup brain": "antwoorden van een reservebrein", "asleep, napping off Wi-Fi, or out of range": "slaapt, dutje zonder wifi, of buiten bereik",
    "below 20 KB is trouble": "onder 20 KB is problematisch", "conversations cut off (the connection broke)": "gesprekken afgebroken (de verbinding viel weg)",
    "credits used by Jarvis today": "tegoed vandaag gebruikt door Jarvis", "false wakes: woke but heard nothing": "valse wekkingen: wakker maar niets gehoord",
    "free RAM": "vrij RAM", "free on this drive": "vrij op deze schijf", "gave up": "gaf het op", "higher is better": "hoger is beter", "in your home": "in je huis",
    "last 24 h": "laatste 24 u", "lowest in 24 h": "laagste in 24 u", "median of": "mediaan van", "memory in use": "geheugen in gebruik", "no answer": "geen antwoord",
    "no data": "geen gegevens", "not checked yet": "nog niet gecontroleerd", "not needed now": "nu niet nodig", "of the free": "van de gratis",
    "of the last 24 h it answered": "van de laatste 24 u dat hij antwoordde", "of this month's credits": "van het tegoed van deze maand", "reason not reported": "reden niet gemeld",
    "requests in 24 h": "verzoeken in 24 u", "requests this minute": "verzoeken deze minuut", "requests, last 24 h": "verzoeken, laatste 24 u",
    "restarts in 24 h (sleep wake-ups count)": "herstarts in 24 u (wakker worden uit slaap telt mee)", "samples clipped": "samples vervormd", "searches today": "zoekopdrachten vandaag",
    "sentences heard": "zinnen gehoord", "slowest 10%": "langzaamste 10%", "stopped on purpose": "bewust gestopt", "things in your home": "dingen in je huis",
    "thinking, first word": "denken, eerste woord", "this PC: processor": "deze pc: processor", "through ntfy": "via ntfy", "tokens in the last 24 h": "tokens in de laatste 24 u",
    "tokens this minute": "tokens deze minuut", "tokens, estimated in use": "tokens, geschat in gebruik", "tokens, last 24 h": "tokens, laatste 24 u", "too loud": "te hard",
    "typical answer from the brain": "typisch antwoord van het brein", "typical reply": "typisch antwoord", "voice, first sound": "stem, eerste geluid",
    "wake-ups (wake word heard)": "wekkingen (wekwoord gehoord)", "running for": "draait al", "on battery": "op batterij", "on USB": "op usb",
    "average": "gemiddeld", "worst": "slechtst", "since": "sinds", "after": "na", "away": "weg", "days": "dagen", "turns": "beurten", "hearing": "horen", "voice": "stem",
    "instant": "direct", "brain": "brein", "clipping": "vervorming", "failed": "mislukt", "of": "van", "today": "vandaag", "camera": "camera", "free": "vrij", "running": "draait",
    "live": "live", "ago": "geleden", "answers": "antwoorden", "fine": "prima", "loud": "luid",
    # the Faces page (named people for the greeter)
    "Faces": "Gezichten", "Greet more people by name": "Begroet meer mensen bij naam", "Jarvis greets the people it knows by name": "Jarvis begroet de mensen die hij kent bij naam",
    "Known": "Bekend", "People it knows": "Mensen die hij kent", "You": "Jij", "Nobody yet.": "Nog niemand.",
    "face samples": "gezichtsvoorbeelden", "1 face sample": "1 gezichtsvoorbeeld", "changed in the panel under Face finding": "aan te passen in het paneel onder Gezichten vinden",
    "Not enrolled yet: enrol your own face in the panel under Face finding.": "Nog niet ingeschreven: schrijf je eigen gezicht in via het paneel onder Gezichten vinden.",
    "Add more pictures of this person": "Meer fotoâ€™s van deze persoon toevoegen", "Forget this person": "Vergeet deze persoon", "Forget this person?": "Deze persoon vergeten?",
    "The face samples of": "De gezichtsvoorbeelden van", "Taking pictures of": "Fotoâ€™s maken van", "Look at the robot and keep still.": "Kijk naar de robot en blijf stil staan.",
    "last picture": "laatste foto", "Stop (keep what was taken)": "Stoppen (bewaar wat gemaakt is)", "Added": "Toegevoegd", "pictures to": "fotoâ€™s aan", "now": "nu", "Nothing added": "Niets toegevoegd",
    "Enrol a new person": "Een nieuwe persoon inschrijven", "Take 6 pictures": "Maak 6 fotoâ€™s", "The face model files are missing on the PC.": "De gezichtsmodelbestanden ontbreken op de pc.",
    "The person stands about one metre in front of the robot, facing the camera. The robot takes 6 pictures, one every 1.2 seconds; only numbers describing the face are kept, no pictures. Then choose Known on the Greeter page.":
        "De persoon staat ongeveer een meter voor de robot, met het gezicht naar de camera. De robot maakt 6 fotoâ€™s, een per 1,2 seconden; alleen getallen die het gezicht beschrijven blijven bewaard, geen fotoâ€™s. Kies daarna Bekend op de pagina Begroeten.",
    "Greets you and": "Begroet jou en", "Nobody is enrolled yet: add people on the Faces page.": "Nog niemand is ingeschreven: voeg mensen toe op de pagina Gezichten.",
    "this looks like the owner (the owner is enrolled in Face finding, not here)": "dit lijkt op de eigenaar (de eigenaar schrijf je in bij Gezichten vinden, niet hier)",
    "no face in this picture": "geen gezicht in deze foto", "more than one face in this picture": "meer dan een gezicht in deze foto", "the face is not clear enough": "het gezicht is niet duidelijk genoeg",
    "the robot gave no picture": "de robot gaf geen foto", "a name is needed": "een naam is nodig",
}
for _k, _v in V2B.items():
    words.setdefault(_k, _v)


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
