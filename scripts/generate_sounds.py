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


def render_tick_soft(rng: random.Random) -> list[float]:
    """Quieter, rounder tick — less piercing in headphones."""
    duration = 0.070
    n = int(SAMPLE_RATE * duration)
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 70.0)
        click = (
            0.62 * math.sin(2 * math.pi * 2450.0 * t)
            + 0.28 * math.sin(2 * math.pi * 3680.0 * t)
        )
        noise = (rng.random() * 2.0 - 1.0) * 0.12 * math.exp(-t * 160.0)
        out.append((click + noise) * env * 0.72)
    return out


def render_tick_crisp(rng: random.Random) -> list[float]:
    """Shorter, brighter pin click."""
    duration = 0.038
    n = int(SAMPLE_RATE * duration)
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 140.0)
        click = (
            0.55 * math.sin(2 * math.pi * 5200.0 * t)
            + 0.40 * math.sin(2 * math.pi * 7800.0 * t)
            + 0.18 * math.sin(2 * math.pi * 11000.0 * t)
        )
        noise = (rng.random() * 2.0 - 1.0) * 0.35 * math.exp(-t * 280.0)
        out.append((click + noise) * env)
    return out


def render_tick_wood(rng: random.Random) -> list[float]:
    """Tiny wooden tap, still a 'second' mark."""
    duration = 0.062
    n = int(SAMPLE_RATE * duration)
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 55.0)
        body = (
            0.48 * math.sin(2 * math.pi * 920.0 * t)
            + 0.30 * math.sin(2 * math.pi * 1480.0 * t)
            + 0.14 * math.sin(2 * math.pi * 2360.0 * t)
        )
        wood = (rng.random() * 2.0 - 1.0) * 0.32 * math.exp(-t * 90.0)
        out.append((body + wood) * env)
    return out


def render_kata_deep(rng: random.Random) -> list[float]:
    """Lower, longer wooden tock."""
    duration = 0.150
    n = int(SAMPLE_RATE * duration)
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 18.0)
        body = (
            0.58 * math.sin(2 * math.pi * 140.0 * t)
            + 0.36 * math.sin(2 * math.pi * 265.0 * t)
            + 0.16 * math.sin(2 * math.pi * 410.0 * t)
        )
        wood = (rng.random() * 2.0 - 1.0) * 0.38 * math.exp(-t * 32.0)
        out.append((body + wood) * env)
    return out


def render_kata_knock(rng: random.Random) -> list[float]:
    """Door-knock clack."""
    duration = 0.100
    n = int(SAMPLE_RATE * duration)
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 32.0)
        body = (
            0.46 * math.sin(2 * math.pi * 180.0 * t)
            + 0.34 * math.sin(2 * math.pi * 320.0 * t)
            + 0.20 * math.sin(2 * math.pi * 540.0 * t)
            + 0.10 * math.sin(2 * math.pi * 860.0 * t)
        )
        knock = (rng.random() * 2.0 - 1.0) * 0.55 * math.exp(-t * 70.0)
        out.append((body + knock) * env)
    return out


def render_kata_glass(rng: random.Random) -> list[float]:
    """Higher glassy clack."""
    duration = 0.095
    n = int(SAMPLE_RATE * duration)
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 38.0)
        body = (
            0.40 * math.sin(2 * math.pi * 780.0 * t)
            + 0.32 * math.sin(2 * math.pi * 1180.0 * t)
            + 0.18 * math.sin(2 * math.pi * 1760.0 * t)
        )
        spark = (rng.random() * 2.0 - 1.0) * 0.22 * math.exp(-t * 90.0)
        out.append((body + spark) * env)
    return out


def render_ding_low() -> list[float]:
    """Lower, warmer bell."""
    duration = 0.900
    n = int(SAMPLE_RATE * duration)
    f0 = 523.25  # C5
    partials = (
        (1.000, 1.00, 1.80),
        (2.003, 0.38, 2.40),
        (2.990, 0.18, 3.10),
        (4.040, 0.09, 3.90),
        (5.120, 0.05, 5.00),
    )
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        s = 0.0
        for ratio, amp, decay in partials:
            s += amp * math.sin(2 * math.pi * f0 * ratio * t) * math.exp(-t * decay)
        attack = min(1.0, t / 0.010)
        out.append(s * attack * 0.52)
    return out


def render_ding_chime() -> list[float]:
    """Soft two-note chime."""
    duration = 0.950
    n = int(SAMPLE_RATE * duration)
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        a = math.sin(2 * math.pi * 659.25 * t) * math.exp(-t * 2.4)  # E5
        b = math.sin(2 * math.pi * 987.77 * t) * math.exp(-t * 2.8)  # B5
        attack = min(1.0, t / 0.012)
        out.append((0.62 * a + 0.38 * b) * attack * 0.48)
    return out


def render_ding_bright() -> list[float]:
    """Shorter, higher bell."""
    duration = 0.520
    n = int(SAMPLE_RATE * duration)
    f0 = 1046.5  # C6
    partials = (
        (1.000, 1.00, 3.10),
        (2.010, 0.36, 3.80),
        (3.040, 0.16, 4.60),
        (4.180, 0.07, 5.80),
    )
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        s = 0.0
        for ratio, amp, decay in partials:
            s += amp * math.sin(2 * math.pi * f0 * ratio * t) * math.exp(-t * decay)
        attack = min(1.0, t / 0.005)
        out.append(s * attack * 0.46)
    return out


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    raw = root / "app" / "src" / "main" / "res" / "raw"
    rng_tick = random.Random(20260819)
    rng_kata = random.Random(20260820)
    write_wav(raw / "tick.wav", render_tick(rng_tick))
    write_wav(raw / "tick_soft.wav", render_tick_soft(random.Random(20260821)))
    write_wav(raw / "tick_crisp.wav", render_tick_crisp(random.Random(20260822)))
    write_wav(raw / "tick_wood.wav", render_tick_wood(random.Random(20260823)))
    write_wav(raw / "kata.wav", render_kata(rng_kata))
    write_wav(raw / "kata_deep.wav", render_kata_deep(random.Random(20260824)))
    write_wav(raw / "kata_knock.wav", render_kata_knock(random.Random(20260825)))
    write_wav(raw / "kata_glass.wav", render_kata_glass(random.Random(20260826)))
    write_wav(raw / "ding.wav", render_ding())
    write_wav(raw / "ding_low.wav", render_ding_low())
    write_wav(raw / "ding_chime.wav", render_ding_chime())
    write_wav(raw / "ding_bright.wav", render_ding_bright())


if __name__ == "__main__":
    main()
