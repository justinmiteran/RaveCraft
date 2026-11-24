"""Machine sound loops, cut from CC0 recordings (BigSoundBank, https://bigsoundbank.com/licenses.html).

Usage (from repo root):
    python tools/sounds/cut_sounds.py

Each machine takes a steady stretch of its source recording (art/sounds/source), loudness-matched, with the end
crossfaded into the start so the clip loops without a click. Written mono (Minecraft only positions mono sounds)
as Ogg Vorbis to src/main/resources/assets/ravecraft/sounds/machine/<machine>.ogg.
Needs numpy and soundfile (pip install numpy soundfile).
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art/sounds/source"
OUT = ROOT / "src/main/resources/assets/ravecraft/sounds/machine"

TARGET_RMS_DB = -20.0
PEAK_LIMIT_DB = -1.0
CROSSFADE_S = 0.4

# machine: (source file, start s, loop length s)
LOOPS = {
    "coil_winder": ("bsb_3547_drone.ogg", 7.5, 3.5),
    "magnetizer": ("bsb_2437_transformer.ogg", 2.0, 4.0),
    "extruder": ("bsb_0856_air_compressor.ogg", 10.0, 6.0),
    "etcher": ("bsb_0395_laser_printer.ogg", 1.5, 5.0),
}


def mono(path: Path) -> tuple[np.ndarray, int]:
    """Reads a recording as mono floats."""
    data, rate = sf.read(path, dtype="float64")
    return (data if data.ndim == 1 else data.mean(axis=1)), rate


def seamless(data: np.ndarray, rate: int, start: float, length: float) -> np.ndarray:
    """A loop of `length` seconds from `start`: the crossfade after the loop is blended into its first samples."""
    a = int(start * rate)
    n = int(length * rate)
    x = int(CROSSFADE_S * rate)
    clip = data[a:a + n].copy()
    tail = data[a + n:a + n + x]
    fade = np.linspace(0.0, 1.0, x)
    clip[:x] = clip[:x] * fade + tail * (1.0 - fade)
    return clip


def level(clip: np.ndarray) -> np.ndarray:
    """Matches loudness across machines, then keeps the peaks under the limit."""
    rms = np.sqrt(np.mean(clip ** 2))
    clip = clip * (10 ** (TARGET_RMS_DB / 20) / rms)
    peak = np.abs(clip).max()
    limit = 10 ** (PEAK_LIMIT_DB / 20)
    return clip * (limit / peak) if peak > limit else clip


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for machine, (source, start, length) in LOOPS.items():
        data, rate = mono(SOURCE / source)
        clip = level(seamless(data, rate, start, length))
        sf.write(OUT / f"{machine}.ogg", clip, rate, format="OGG", subtype="VORBIS")
        print(f"{machine}: {source} {start}s +{length}s")


if __name__ == "__main__":
    main()
