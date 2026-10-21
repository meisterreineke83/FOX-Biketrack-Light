#!/usr/bin/env python3
"""
Erzeugt die Sprachansagen für komoonlight als WAV-Dateien (16 kHz mono).

Stimme: Piper „de-thorsten-low“ (Thorsten-Voice, Lizenz CC0).
Die Dateinamen müssen zu tool/src/main/kotlin/de/komoonlight/nav/VoicePrompts.kt passen –
der Unit-Test VoicePromptsTest prüft das.

Benutzung:
    # einmalig: Piper + Stimme laden
    curl -L -o piper.tgz https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz
    curl -L -o voice.tgz https://github.com/rhasspy/piper/releases/download/v0.0.2/voice-de-thorsten-low.tar.gz
    tar xzf piper.tgz && mkdir -p voice && tar xzf voice.tgz -C voice

    python3 tools/voice/generate.py --piper ./piper/piper --model voice/de-thorsten-low.onnx

Texte ändern: unten in KINDS / DISTANCES / EXTRA anpassen und neu erzeugen.
"""
import argparse
import pathlib
import subprocess
import sys

# Kind (wie in Turn.kt, kleingeschrieben) → (Ansage vorab, Ansage „jetzt“)
KINDS = {
    "slight_left":  ("leicht links halten",     "jetzt leicht links"),
    "left":         ("links abbiegen",          "jetzt links abbiegen"),
    "sharp_left":   ("scharf links abbiegen",   "jetzt scharf links"),
    "slight_right": ("leicht rechts halten",    "jetzt leicht rechts"),
    "right":        ("rechts abbiegen",         "jetzt rechts abbiegen"),
    "sharp_right":  ("scharf rechts abbiegen",  "jetzt scharf rechts"),
    "keep_left":    ("links halten",            "jetzt links halten"),
    "keep_right":   ("rechts halten",           "jetzt rechts halten"),
    "u_turn":       ("wenden",                  "jetzt wenden"),
    "arrive":       ("erreichst du dein Ziel",  "Du hast dein Ziel erreicht."),
}

# Vorankündigung wird auf 50 m gerundet (Navigator: 80–200 m vorher)
DISTANCES = {50: "fünfzig", 100: "hundert", 150: "hundertfünfzig", 200: "zweihundert"}

EXTRA = {
    "off_route": "Du hast die Route verlassen.",
    "back_on_route": "Wieder auf der Route.",
    "start": "Navigation gestartet.",
}


def phrases():
    for kind, (ahead, now) in KINDS.items():
        for m, spoken in DISTANCES.items():  # Zahl ausgeschrieben → sicher richtig ausgesprochen
            yield f"up_{m}_{kind}", f"In {spoken} Metern {ahead}."
        yield f"now_{kind}", now[0].upper() + now[1:] + ("" if now.endswith(".") else ".")
    yield from EXTRA.items()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--piper", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--out", default=str(pathlib.Path(__file__).resolve().parents[2] / "tool/src/main/assets/voice"))
    ap.add_argument("--length-scale", default="1.0", help=">1 = langsamer")
    a = ap.parse_args()

    out = pathlib.Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    for name, text in phrases():
        target = out / f"{name}.wav"
        subprocess.run(
            [a.piper, "--model", a.model, "--output_file", str(target),
             "--length_scale", a.length_scale, "--sentence_silence", "0"],
            input=text.encode(), check=True, stderr=subprocess.DEVNULL,
        )
        print(f"{target.name:28s} {text}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
