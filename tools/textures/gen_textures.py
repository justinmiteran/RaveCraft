"""Texture generator: the mod's ORIGINAL 16x16 pixel-art textures, produced by code (Pillow, deterministic).

Usage (from repo root):
    python tools/textures/gen_textures.py                 # write every texture + art/textures/preview.png
    python tools/textures/gen_textures.py --only portable_speaker_front

Add a texture: write a function returning a 16x16 RGBA Image, then register it in BLOCKS, ANIMATED
(frames + frametime, writes the .png.mcmeta) or ITEMS. A texture retouched by hand (Blockbench, Aseprite...)
must be removed from here, or it will be overwritten.
"""
from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

MOD_ID = "ravecraft"
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / f"src/main/resources/assets/{MOD_ID}"
BLOCK_DIR = ASSETS / "textures/block"
ITEM_DIR = ASSETS / "textures/item"
ICON = ASSETS / "icon.png"
PREVIEW = ROOT / "art/textures/preview.png"
S = 16

# --- Palette: keep it small and shared so every texture looks like the same mod.
C = {
    "out": (22, 24, 30),        # outline / deepest shadow
    "case_d": (44, 48, 58),
    "case": (62, 67, 80),
    "case_l": (86, 92, 108),
    "case_h": (118, 125, 142),  # bevel highlight
    "rivet": (140, 148, 166),
    "accent_d": (20, 110, 130),
    "accent": (40, 200, 220),
    "accent_l": (150, 245, 255),
    "white": (235, 250, 255),
}


def rgba(c, a=255):
    return (*c, a)


def new(fill=None):
    return Image.new("RGBA", (S, S), rgba(fill) if fill else (0, 0, 0, 0))


def px(img, x, y, c, a=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), rgba(c, a))


def rect(img, x0, y0, x1, y1, c):
    ImageDraw.Draw(img).rectangle([x0, y0, x1, y1], fill=rgba(c))


def grain(img, seed, amount=1):
    """Subtle per-pixel value jitter, vanilla-style (no smooth gradients)."""
    rnd = random.Random(seed)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            d = rnd.choice((-6, 0, 0, 0, 5)) * amount
            img.putpixel((x, y), (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a))


def casing(seed):
    """Bevelled metal panel with corner rivets, lit from the top-left."""
    img = new(C["case"])
    grain(img, seed)
    for i in range(S):
        px(img, i, 0, C["out"]); px(img, i, S - 1, C["out"])
        px(img, 0, i, C["out"]); px(img, S - 1, i, C["out"])
    for i in range(1, S - 1):
        px(img, i, 1, C["case_h"]); px(img, 1, i, C["case_h"])
        px(img, i, S - 2, C["case_d"]); px(img, S - 2, i, C["case_d"])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, x, y, C["rivet"]); px(img, x + 1, y + 1, C["case_d"])
    return img


def inset(img, x0, y0, x1, y1, fill):
    """Recessed window: dark top/left lip, light bottom/right lip."""
    rect(img, x0, y0, x1, y1, fill)
    for x in range(x0, x1 + 1):
        px(img, x, y0 - 1, C["case_d"]); px(img, x, y1 + 1, C["case_l"])
    for y in range(y0, y1 + 1):
        px(img, x0 - 1, y, C["case_d"]); px(img, x1 + 1, y, C["case_l"])


# ---------------------------------------------------------------- textures


# Portable speaker: the model maps each face pixel-exactly onto a region of its 16x16 texture
# (front 10x8 at (3,4), side 6x8 at (5,4), top 10x6 at (3,5)); the rest of the tile is plain casing so the
# texture still reads well as a particle or in the preview.


def panel(img, x0, y0, w, h):
    """Small bevelled casing panel (outline, top-left highlight, bottom-right shadow) inside a region."""
    x1, y1 = x0 + w - 1, y0 + h - 1
    rect(img, x0, y0, x1, y1, C["case"])
    for x in range(x0, x1 + 1):
        px(img, x, y0, C["out"]); px(img, x, y1, C["out"])
    for y in range(y0, y1 + 1):
        px(img, x0, y, C["out"]); px(img, x1, y, C["out"])
    for x in range(x0 + 1, x1):
        px(img, x, y0 + 1, C["case_h"]); px(img, x, y1 - 1, C["case_d"])
    for y in range(y0 + 1, y1):
        px(img, x0 + 1, y, C["case_h"]); px(img, x1 - 1, y, C["case_d"])


def speaker_front(on):
    img = casing(20)
    x0, y0 = 3, 4                       # 10x8 face
    panel(img, x0, y0, 10, 8)
    # Grille: dark field with a dotted mesh.
    rect(img, x0 + 1, y0 + 1, x0 + 8, y0 + 6, C["out"])
    for y in range(y0 + 1, y0 + 7):
        for x in range(x0 + 1, x0 + 9):
            if (x + y) % 2 == 0:
                px(img, x, y, C["case_d"])
    # Round cone (6x6) on the left: lit rim top-left, shadowed bottom-right, dust cap in the middle.
    cx, cy = x0 + 2, y0 + 1
    cone = ["  aa  ",
            " abbc ",
            "abddbc",
            "abdebc",
            " bccc ",
            "  cc  "]
    cols = {"a": C["case_h"], "b": C["case"], "c": C["case_d"], "d": C["rivet"], "e": C["case_l"]}
    for j, row in enumerate(cone):
        for i, ch in enumerate(row):
            if ch != " ":
                px(img, cx + i, cy + j, cols[ch])
    px(img, cx + 2, cy + 2, C["case_h"])
    # Status LED, top right of the grille.
    lx, ly = x0 + 8, y0 + 1
    if on:
        px(img, lx, ly, C["accent_l"]); px(img, lx, ly + 1, C["accent"])
    else:
        px(img, lx, ly, C["accent_d"]); px(img, lx, ly + 1, C["out"])
    return img


def portable_speaker_front():
    return speaker_front(False)


def portable_speaker_front_on():
    return speaker_front(True)


def portable_speaker_side():
    img = casing(21)
    x0, y0 = 5, 4                       # 6x8 face
    panel(img, x0, y0, 6, 8)
    # Three vent slots.
    for y in (y0 + 2, y0 + 4):
        for x in range(x0 + 2, x0 + 4):
            px(img, x, y, C["out"])
        px(img, x0 + 2, y + 1, C["case_l"]); px(img, x0 + 3, y + 1, C["case_l"])
    return img


def portable_speaker_top():
    img = casing(22)
    x0, y0 = 3, 5                       # 10x6 face
    panel(img, x0, y0, 10, 6)
    # Handle mounts (where the carry handle posts sit) and a recessed groove between them.
    for x in (x0 + 2, x0 + 7):
        rect(img, x, y0 + 2, x, y0 + 3, C["out"])
    for x in range(x0 + 3, x0 + 7):
        px(img, x, y0 + 2, C["case_d"]); px(img, x, y0 + 3, C["case_l"])
    # Handle swatch (sampled by the model's handle element): grip top and grip sides.
    rect(img, 3, 13, 12, 13, C["case_h"])
    rect(img, 3, 14, 12, 14, C["case_l"])
    return img


BLOCKS = {
    "portable_speaker_front": portable_speaker_front,
    "portable_speaker_front_on": portable_speaker_front_on,
    "portable_speaker_side": portable_speaker_side,
    "portable_speaker_top": portable_speaker_top,
}
ANIMATED = {}  # name -> (frames fn, frametime ticks)
ITEMS = {}  # name -> fn (flat item textures)


def save_animated(name, frames, frametime):
    strip = Image.new("RGBA", (S, S * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, S * i))
    strip.save(BLOCK_DIR / f"{name}.png")
    (BLOCK_DIR / f"{name}.png.mcmeta").write_text(
        json.dumps({"animation": {"frametime": frametime}}, indent=2) + "\n", encoding="utf-8")
    return frames[0]


def preview_sheet(tiles, scale=8, cols=5):
    pad = 4
    rows = (len(tiles) + cols - 1) // cols
    cell = S * scale + pad
    sheet = Image.new("RGBA", (cols * cell + pad, rows * cell + pad), (40, 40, 40, 255))
    for i, (_, img) in enumerate(tiles):
        x, y = pad + (i % cols) * cell, pad + (i // cols) * cell
        big = img.resize((S * scale, S * scale), Image.NEAREST)
        sheet.paste(big, (x, y), big)
    return sheet


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="generate a single texture by name")
    args = ap.parse_args()
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)

    tiles = []
    for name, fn in BLOCKS.items():
        if args.only and args.only != name:
            continue
        img = fn(); img.save(BLOCK_DIR / f"{name}.png"); tiles.append((name, img))
    for name, (fn, ft) in ANIMATED.items():
        if args.only and args.only != name:
            continue
        frames = fn()
        save_animated(name, frames, ft)
        tiles += [(f"{name}#{i}", f) for i, f in enumerate(frames[:3])]
    for name, fn in ITEMS.items():
        if args.only and args.only != name:
            continue
        img = fn(); img.save(ITEM_DIR / f"{name}.png"); tiles.append((name, img))

    if not args.only:
        # Mod icon (mod menu): the first block texture, scaled up without smoothing.
        first = next(iter(BLOCKS.values()))()
        first.resize((128, 128), Image.NEAREST).save(ICON)
    if tiles:
        preview_sheet(tiles).save(PREVIEW)
    for name, _ in tiles:
        print("wrote", name)
    print("preview:", PREVIEW.relative_to(ROOT))


if __name__ == "__main__":
    main()
