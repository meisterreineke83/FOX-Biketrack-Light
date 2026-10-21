# komoonlight

Ein kleines Programm für das Light Phone III mit Zugriff auf meinen Komoot-Account.
Nutzbar als Navigationsgerät für das Fahrrad.

Das Repo enthält das komplette [Light SDK](https://github.com/lightphone/light-sdk)
(siehe [README.light-sdk.md](README.light-sdk.md)); der eigene Code liegt nur in `tool/`.

> Nutzt dieselben **inoffiziellen** Komoot-Endpunkte wie [KomootGPX](https://pypi.org/project/komootgpx/).
> Nur für den privaten Gebrauch mit dem eigenen Account. Komoot kann die Endpunkte jederzeit ändern.

## Funktionen (v0.1)

- Anmeldung mit Komoot-E-Mail + Passwort – gespeichert wird nur der Token
- Liste der geplanten und gefahrenen Touren
- Kartenlose Routenansicht: Linie, eigener Weg gepunktet, Pfeil in Fahrtrichtung, Zoom
- Nächste Abbiegung groß ab 300 m („→ 120 m rechts, Hauptstraße“), sonst Rest-km
- Vibration: einmal ~200 m vor der Abbiegung, zweimal „jetzt“, lang bei Abweichung > 50 m
- Aufzeichnung als GPX → im **LightOS Tool Manager** (Browser) unter „Aufzeichnungen“ herunterladen
  und in Komoot hochladen

## Code

```
tool/
├── lighttool.toml                  ID, Name, Berechtigungen (statt AndroidManifest)
└── src/main/kotlin/de/komoonlight/
    ├── HomeScreen.kt               Login + Tourenliste (Startscreen)
    ├── NavScreen.kt                Navigation, Aufnahme, Vibration
    ├── TextEditorScreen.kt         Light-Tastatur für Eingabefelder
    ├── ToolEntryPoint.kt           Tool-Manager-Ordner „Aufzeichnungen“
    ├── Store.kt                    Token-Speicher (DataStore)
    ├── komoot/KomootClient.kt      Login, Touren, Route + Abbiegehinweise
    ├── location/LightLocation.kt   GPS über LightOS
    ├── nav/Navigator.kt            Navigationslogik (ohne Android, getestet)
    ├── nav/Turn.kt                 Komoot-Abbiegetypen → Text + Pfeil
    ├── geo/                        Geometrie, GPX
    └── ui/RouteView.kt             Routenzeichnung
```

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
- [ ] **Sprachansagen** („In 200 Metern rechts“) – als mitgelieferte Audio-Clips über
      `LightAudio`, da das SDK keine Android-Sprachausgabe freigibt. Die Ereignisse
      dafür liefert `Navigator` schon (`NavEvent.Upcoming/Now/…`).
- [ ] **Display 50 m vor Abzweigung einschalten** (`NavEvent.Approaching`) – braucht einen
      Weg, das Display aus dem Tool zu wecken; im SDK noch nicht vorgesehen.
- [ ] GPX-Routen über den Tool Manager hochladen (Fallback, falls Komoot hakt)
