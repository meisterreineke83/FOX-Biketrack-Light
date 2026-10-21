# komoonlight

Ein kleines Programm für das Light Phone III mit Zugriff auf meinen Komoot-Account.
Nutzbar als Navigationsgerät für das Fahrrad.

Das Repo enthält das komplette [Light SDK](https://github.com/lightphone/light-sdk)
(siehe [README.light-sdk.md](README.light-sdk.md)); der eigene Code liegt nur in `tool/`.

> Nutzt dieselben **inoffiziellen** Komoot-Endpunkte wie [KomootGPX](https://pypi.org/project/komootgpx/).
> Nur für den privaten Gebrauch mit dem eigenen Account. Komoot kann die Endpunkte jederzeit ändern.

## Funktionen (v0.6)

- **Einfach losfahren**: ohne Route sofort aufzeichnen – Kilometer, Fahrzeit, Ø-Tempo
  (geht auch ohne Komoot-Anmeldung)
- Anmeldung mit Komoot-E-Mail + Passwort – gespeichert wird nur der Token
- Touren getrennt nach **Geplant** und **Gefahren**, mit Datum und Länge
  - geplante Tour: Vorschau der ganzen Route, Navigation erst mit **„Route starten“**,
    „Beenden“ speichert die Fahrt (→ Komoot)
  - gefahrene Tour: nur ansehen – ohne Aufnahme- und Ton-Knopf
- **Karte im Hintergrund** (OpenStreetMap, Stil CARTO „Dark Matter“ bzw. „Positron“ im hellen Theme),
  darüber Route, eigener Weg gepunktet und Pfeil in Fahrtrichtung; Zoom, „Karte“ schaltet sie ab.
  Kacheln entlang der Route werden vorab geladen und 60 Tage zwischengespeichert → auch ohne Netz.
  Kontrast wird fürs Graustufen-Display angehoben; lädt die Karte nicht, steht der Grund unten links.
- Nächste Abbiegung groß ab 300 m („→ 120 m rechts, Hauptstraße“), sonst Rest-km
- **Sprachansagen** auf Deutsch: „In zweihundert Metern rechts abbiegen“, „Jetzt rechts abbiegen“,
  „Du hast die Route verlassen“, „Du hast dein Ziel erreicht“ – oben rechts „Ton an / Stumm“
- Zusätzlich Vibration: einmal ~200 m vor der Abbiegung, zweimal „jetzt“, lang bei Abweichung > 50 m
- **Fahrten landen automatisch bei Komoot unter „Gefahren“**: Nach jeder Aufzeichnung wird sie
  hochgeladen („Radtour 21.10.2026, 17:45“, Sportart Fahrrad). Ohne Netz oder Anmeldung wartet sie
  und wird beim nächsten Öffnen des Startscreens nachgeholt – antippen versucht es sofort erneut.
- Zusätzlich als GPX im **LightOS Tool Manager** (Browser) unter „Aufzeichnungen“

## Code

```
tool/
├── lighttool.toml                  ID, Name, Berechtigungen (statt AndroidManifest)
└── src/main/kotlin/de/komoonlight/
    ├── HomeScreen.kt               Login + Tourenliste (Startscreen)
    ├── NavScreen.kt                Navigation / freie Fahrt, Aufnahme, Vibration
    ├── Format.kt                   Datum, Fahrzeit (getestet)
    ├── TextEditorScreen.kt         Light-Tastatur für Eingabefelder
    ├── ToolEntryPoint.kt           Tool-Manager-Ordner „Aufzeichnungen“
    ├── Store.kt                    Token-Speicher (DataStore)
    ├── komoot/KomootClient.kt      Login, Touren, Route + Abbiegehinweise
    ├── location/LightLocation.kt   GPS über LightOS
    ├── nav/Navigator.kt            Navigationslogik (ohne Android, getestet)
    ├── nav/Turn.kt                 Komoot-Abbiegetypen → Text + Pfeil
    ├── nav/VoicePrompts.kt         Ereignis → Sprachclip
    ├── nav/VoiceAnnouncer.kt       Abspielen über LightAudio
    ├── geo/                        Geometrie, GPX, Kartenprojektion (MapView, getestet)
    ├── map/TileStore.kt            Kartenkacheln laden + zwischenspeichern
    └── ui/RouteView.kt             Karte + Route zeichnen
```

## Upload zu Komoot

`KomootClient.uploadRecorded` nutzt – wie der GPX-Import auf komoot.com – den inoffiziellen Endpunkt
`POST /v007/tours/?data_type=gpx` und setzt danach Name und Sportart (`PATCH /v007/tours/{id}`).
Welche Dateien schon oben sind, merkt sich das Tool (DataStore); die GPX-Dateien bleiben zusätzlich
lokal erhalten. Sportart ändern: Parameter `sport` (z. B. `racebike`, `mtb`, `e_touringbicycle`).

## Karte

Kartendaten © [OpenStreetMap-Mitwirkende](https://www.openstreetmap.org/copyright), Kacheln von
[CARTO](https://carto.com/attributions) (Basemaps, frei für nicht-kommerzielle Nutzung mit Namensnennung).
Eine Kartenbibliothek wie osmdroid erlaubt das Light-SDK nicht – die Kacheln werden deshalb selbst
gezeichnet (`TileStore` + `RouteView`), mit höchstens zwei gleichzeitigen Downloads.

## Sprachansagen

Das SDK gibt Tools keinen Zugriff auf Androids Sprachausgabe. Deshalb liegen die Ansagen als
fertige Clips in `tool/src/main/assets/voice/` (53 WAV-Dateien, ~3,4 MB), erzeugt offline mit
[Piper](https://github.com/rhasspy/piper) und der Stimme „Thorsten“
([Thorsten-Voice](https://github.com/thorstenMueller/Thorsten-Voice), CC0).

Texte ändern oder neu erzeugen: siehe `tools/voice/generate.py`. Der Test `VoicePromptsTest`
prüft, dass zu jeder möglichen Ansage ein Clip existiert. Straßennamen werden nicht angesagt.

## Bauen

Jeder Push auf `main` baut das Tool per GitHub Actions („Tool bauen“). Die fertige APK liegt
im jeweiligen Lauf unter **Artifacts → komoonlight-debug-apk**.

Lokal (Android Studio oder Kommandozeile, JDK 17 + Android SDK):

```bash
./gradlew -DlightSdk.toolOnly=true :tool:testDebugUnitTest   # Tests
./gradlew -DlightSdk.toolOnly=true :tool:assembleDebug        # APK
```

## Aufs Phone

Am bequemsten per WLAN über den Tool Manager im Entwicklermodus
(siehe [docs/sideloading](docs/sideloading/README.md)):

```bash
./gradlew :tool:uploadTool -Pdevice.ip=<IP des Phones> -Pdevice.token=<Schlüssel aus der Tool-Manager-URL>
```

## Geplant

- [ ] **Navigation bei ausgeschaltetem Display** – LightOS erlaubt Tools keinen eigenen
      Hintergrunddienst. Ansatz: die erlaubte Fähigkeit `detached-audio` (läuft als
      Vordergrund-Dienst im Tool-Prozess) hält die Navigation am Leben.
- [x] Sprachansagen (v0.2)
- [ ] **Display 50 m vor Abzweigung einschalten** (`NavEvent.Approaching`) – braucht einen
      Weg, das Display aus dem Tool zu wecken; im SDK noch nicht vorgesehen.
- [ ] GPX-Routen über den Tool Manager hochladen (Fallback, falls Komoot hakt)

## Release / BrightMarket

Neue Version veröffentlichen: `versionCode` und `versionName` in `tool/lighttool.toml` erhöhen
und auf `main` pushen. Der Workflow „Release“ baut die APK und legt einen GitHub-Release mit genau einer `.apk` an –
so findet [BrightMarket](https://github.com/gi-os/BrightMarket) die App (per QR-Code das Repo
hinzufügen). Der `versionCode` muss bei jedem Release steigen, sonst gibt es kein Update.
