#!/usr/bin/env python3
"""Generates the built-in alarm sounds (app/src/main/res/raw/*.wav).

Each is a 3-second mono 16-bit loop at 22.05 kHz, designed to repeat seamlessly.
Run from the repository root: python3 scripts/generate-sounds.py
"""
import math, os, struct, wave

RATE = 22050
SECONDS = 3
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def env(phase, attack, end, release):
    """Linear attack, hold, linear release ending at `end` seconds."""
    return max(0.0, min(1.0, phase / attack, (end - phase) / release))


def beacon(t):
    # Two rising tones (660 Hz then 880 Hz) every 1.5 s.
    phase = t % 1.5
    freq = 660 if phase < 0.5 else 880
    return 0.7 * math.sin(2 * math.pi * freq * t) * env(phase, 0.025, 1.1, 0.1)


def pulse(t):
    # Urgent double-frequency beeps, two per second.
    phase = t % 0.5
    return (0.45 * math.sin(2 * math.pi * 900 * t) + 0.22 * math.sin(2 * math.pi * 1200 * t)) * env(phase, 0.015, 0.28, 0.05)


def chime(t):
    # C5, E5, G5 bell notes, one per second, with a decaying overtone.
    phase = t % 1.0
    note = (523.25, 659.25, 783.99)[int(t) % 3]
    tone = 0.6 * math.sin(2 * math.pi * note * t) + 0.12 * math.sin(2 * math.pi * 2 * note * t)
    return tone * min(1.0, phase / 0.01) * math.exp(-3.2 * phase) * min(1.0, (1 - phase) / 0.02)


def write(name, voice):
    path = os.path.join(OUT, name + ".wav")
    frames = bytearray()
    for i in range(RATE * SECONDS):
        sample = max(-0.98, min(0.98, voice(i / RATE)))
        frames += struct.pack("<h", round(sample * 32767))
    with wave.open(path, "wb") as out:
        out.setnchannels(1); out.setsampwidth(2); out.setframerate(RATE); out.writeframes(bytes(frames))
    print(path, len(frames), "bytes")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, voice in (("beacon", beacon), ("pulse", pulse), ("chime", chime)):
        write(name, voice)
