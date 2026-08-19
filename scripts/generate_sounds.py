#!/usr/bin/env python3
"""Generate tick / kata / ding WAV assets for 时感. No numpy required."""

from __future__ import annotations

import math
import random
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44100


def clamp(x: float, lo: float = -1.0, hi: float = 1.0) -> float:
    return lo if x < lo else hi if x > hi else x


def write_wav(path: Path, samples: list[float], sample_rate: int = SAMPLE_RATE) -> None:
    peak = max((abs(s) for s in samples), default=1.0)
    # Leave a little headroom so the click doesn't clip on cheap DACs
    norm = 0.92 / peak if peak > 0 else 1.0
    frames = bytearray()
    for s in samples:
        v = int(clamp(s * norm) * 32767.0)
        frames += struct.pack("<h", v)
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(bytes(frames))
    print(f"wrote {path}  {len(samples)/sample_rate*1000:.1f}ms  peak={peak:.3f}")


def render_tick(rng: random.Random) -> list[float]:
    """~55ms sharp high mechanical stopwatch click."""
    duration = 0.055
    n = int(SAMPLE_RATE * duration)
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        # Pin-strike: almost instantaneous attack, very fast decay
        env = math.exp(-t * 95.0)
        click = (
            0.70 * math.sin(2 * math.pi * 4150.0 * t)
            + 0.38 * math.sin(2 * math.pi * 6280.0 * t)
            + 0.16 * math.sin(2 * math.pi * 8900.0 * t)
        )
        # Brief broadband transient so it reads as a "tick" not a tone
        noise_env = math.exp(-t * 220.0)
        noise = (rng.random() * 2.0 - 1.0) * 0.28 * noise_env
        out.append((click + noise) * env)
    return out


def render_kata(rng: random.Random) -> list[float]:
    """~115ms lower, woodier clack — clearly not the tick."""
    duration = 0.115
    n = int(SAMPLE_RATE * duration)
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 26.0)
        body = (
            0.50 * math.sin(2 * math.pi * 210.0 * t)
            + 0.42 * math.sin(2 * math.pi * 385.0 * t)
            + 0.22 * math.sin(2 * math.pi * 640.0 * t)
            + 0.10 * math.sin(2 * math.pi * 980.0 * t)
        )
        # Wooden body: short mid noise, slightly longer than the tick transient
        wood = (rng.random() * 2.0 - 1.0) * 0.45 * math.exp(-t * 48.0)
        out.append((body + wood) * env)
    return out


def render_ding() -> list[float]:
    """~780ms inharmonic bell: pleasant, decaying, not harsh."""
    duration = 0.780
    n = int(SAMPLE_RATE * duration)
    f0 = 784.0  # G5
    # Slightly inharmonic partials, like a small hand bell
    partials = (
        (1.000, 1.00, 2.15),
        (2.000, 0.42, 2.70),
        (3.011, 0.20, 3.35),
        (4.210, 0.11, 4.10),
        (5.430, 0.06, 5.20),
        (6.800, 0.03, 6.40),
    )
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        s = 0.0
        for ratio, amp, decay in partials:
            s += amp * math.sin(2 * math.pi * f0 * ratio * t) * math.exp(-t * decay)
        attack = min(1.0, t / 0.007)
        out.append(s * attack * 0.50)
    return out


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    raw = root / "app" / "src" / "main" / "res" / "raw"
    rng_tick = random.Random(20260819)
    rng_kata = random.Random(20260820)
    write_wav(raw / "tick.wav", render_tick(rng_tick))
    write_wav(raw / "kata.wav", render_kata(rng_kata))
    write_wav(raw / "ding.wav", render_ding())


if __name__ == "__main__":
    main()
