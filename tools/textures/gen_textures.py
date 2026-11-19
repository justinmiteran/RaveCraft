"""Texture generator: the mod's ORIGINAL 16x16 pixel-art textures, produced by code (Pillow, deterministic).

Usage (from repo root):
    python tools/textures/gen_textures.py                 # write every texture + art/textures/preview.png
    python tools/textures/gen_textures.py --only portable_speaker_fabric

Add a texture: write a function returning a 16x16 RGBA Image, then register it in BLOCKS, ANIMATED
(frames + frametime, writes the .png.mcmeta) or ITEMS. GUI sprites (any size) go in GUI, with their gui
scaling (nine_slice, tile, or none for a plain stretch) written to the .png.mcmeta. A texture retouched by hand (Blockbench, Aseprite...)
must be removed from here, or it will be overwritten.
"""
from __future__ import annotations

import argparse
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

MOD_ID = "ravecraft"
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / f"src/main/resources/assets/{MOD_ID}"
BLOCK_DIR = ASSETS / "textures/block"
ITEM_DIR = ASSETS / "textures/item"
GUI_DIR = ASSETS / "textures/gui/sprites"
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


# Portable speaker (barrel shape, models/block/portable_speaker.json). The fabric is a light grey weave tinted in
# game by the dye colour (tintindex 0), so it stays bright and low-contrast; rubber, radiator and the copper logo
# are never tinted. The details texture is a transparent overlay (cutout): equaliser logo at
# (0,0)-(8,5), top controls row at v=8 (front edge of the top, the handle posts cover v=9-10).
RUBBER = [(26, 27, 32), (38, 40, 47), (52, 55, 64), (72, 76, 88)]
COPPER, COPPER_L = (214, 132, 72), (240, 172, 112)
LED_OFF, LED_ON = (60, 30, 30), (110, 255, 140)
EQ_LOGO = ["....#...",
           "..#.#...",
           "..#.#.#.",
           "#.#.#.#.",
           "........"]


def portable_speaker_fabric():
    rnd = random.Random(11)
    img = new()
    for y in range(S):
        for x in range(S):
            v = 236 if (x + y) % 2 else 206
            if (x + 2 * y) % 7 == 0:
                v -= 16
            v += rnd.randint(-6, 6)
            px(img, x, y, (v, v, v))
    return img


def portable_speaker_rubber():
    rnd = random.Random(5)
    img = new()
    for y in range(S):
        for x in range(S):
            px(img, x, y, RUBBER[1] if rnd.random() > 0.18 else RUBBER[2])
    return img


def portable_speaker_radiator():
    """End cap: rubber rim and a recessed passive radiator centred on (8, 8)."""
    img = portable_speaker_rubber()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 3.1 < d <= 3.9:
                px(img, x, y, RUBBER[3] if x + y < 15 else RUBBER[0])
            elif 2.0 < d <= 3.1:
                px(img, x, y, (16, 17, 21))
            elif d <= 2.0:
                px(img, x, y, RUBBER[2] if x + y < 15 else RUBBER[1])
    px(img, 7, 7, RUBBER[3])
    return img


def speaker_details(on):
    img = new()
    for y, row in enumerate(EQ_LOGO):
        for x, ch in enumerate(row):
            if ch == "#":
                px(img, x, y, COPPER_L if y == 0 or EQ_LOGO[y - 1][x] != "#" else COPPER)
    led = LED_ON if on else LED_OFF
    # Top controls along the front edge (v=8), clear of the handle posts.
    px(img, 2, 8, RUBBER[3])                 # volume down
    px(img, 4, 8, COPPER)                    # play
    px(img, 6, 8, RUBBER[3])                 # volume up
    px(img, 7, 8, led)                       # power LED
    return img


def portable_speaker_details():
    return speaker_details(False)


def portable_speaker_details_on():
    return speaker_details(True)


# Inventory icon (flat, GUI only, boombox front with radiator end caps): layer 0 is the grey fabric, tinted with the dye colour like leather armour;
# the overlay holds what is never tinted (outline, rubber, copper logo, controls).
SPEAKER_ICON = ["................",
                "....oooooooo....",
                "...ohhhhhhhho...",
                "...orrrrrrrro...",
                "...or......ro...",
                ".ooTTTTTTTTgToo.",
                "orrffffffffffhro",
                "rrrffffCCffffrrR",
                "rkrffffccffffrkR",
                "rkrfCCfccffffrkR",
                "rkrfccfccfCCfrkR",
                "rrrfccfccfccfrrR",
                "orrFFFFFFFFFFrro",
                ".ooDDDDDDDDDDoo.",
                "...oooooooooo...",
                "................"]
ICON_FABRIC = {"T": 205, "f": 175, "F": 135, "D": 105}
ICON_DETAILS = {"o": (14, 14, 18), "r": RUBBER[1], "R": RUBBER[0], "h": RUBBER[3], "k": (16, 17, 21),
                "c": COPPER, "C": COPPER_L, "g": LED_OFF}


def speaker_icon_layer(fabric, on=False):
    img = new()
    for y, row in enumerate(SPEAKER_ICON):
        for x, ch in enumerate(row):
            if fabric and ch in ICON_FABRIC:
                v = ICON_FABRIC[ch]
                px(img, x, y, (v, v, v))
            elif not fabric and ch in ICON_DETAILS:
                px(img, x, y, LED_ON if on and ch == "g" else ICON_DETAILS[ch])
    return img


def portable_speaker():
    return speaker_icon_layer(True)


def portable_speaker_overlay():
    return speaker_icon_layer(False)


def portable_speaker_overlay_on():
    return speaker_icon_layer(False, on=True)


BLOCKS = {
    "portable_speaker_radiator": portable_speaker_radiator,
    "portable_speaker_fabric": portable_speaker_fabric,
    "portable_speaker_rubber": portable_speaker_rubber,
    "portable_speaker_details": portable_speaker_details,
    "portable_speaker_details_on": portable_speaker_details_on,
}
ANIMATED = {}  # name -> (frames fn, frametime ticks)
ITEMS = {  # name -> fn (flat item textures)
    "portable_speaker": portable_speaker,
    "portable_speaker_overlay": portable_speaker_overlay,
    "portable_speaker_overlay_on": portable_speaker_overlay_on,
}


# ---------------------------------------------------------------- GUI sprites (speaker screen)
# A vanilla-style container window (light grey, black outline with cut corners, white/dark bevels), recessed
# panels, the speaker fabric tinted at draw time, a copper progress bar and 9x9 icons with a drop shadow.
GUI_GREY, GUI_LIGHT, GUI_DARK, GUI_INK = (198, 198, 198), (255, 255, 255), (85, 85, 85), (0, 0, 0)
PANEL, PANEL_TL, PANEL_BR = (139, 139, 139), (55, 55, 55), (255, 255, 255)
ICON_SHADOW = (40, 40, 40)
COPPER_D = (166, 92, 46)
CYAN = (64, 214, 232)
OFF_GREY = (176, 176, 176)
WHITE, WHITE_D = (240, 240, 240), (190, 190, 190)
PLAY_INK = (58, 30, 14)


def gui_window():
    """16x16, nine_slice border 4: outline with cut corners, 2 px light bevel top-left, 2 px dark bevel bottom-right."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) and y in (0, 15):
                continue
            if x in (0, 15) or y in (0, 15) or (x in (1, 14) and y in (1, 14)):
                px(img, x, y, GUI_INK)
                continue
            lt, rb = min(x - 1, y - 1), min(14 - x, 14 - y)
            if lt < 2 and lt < rb:
                px(img, x, y, GUI_LIGHT)
            elif rb < 2 and rb < lt:
                px(img, x, y, GUI_DARK)
            else:
                px(img, x, y, GUI_GREY)
    return img


def gui_inset():
    """16x16, nine_slice border 1: a recessed panel like an inventory slot (dark top-left lip, white bottom-right)."""
    img = Image.new("RGBA", (16, 16), rgba(PANEL))
    for i in range(15):
        px(img, i, 0, PANEL_TL); px(img, 0, i, PANEL_TL)
        px(img, i + 1, 15, PANEL_BR); px(img, 15, i + 1, PANEL_BR)
    return img


def gui_fabric():
    """The block's fabric weave, tiled and tinted with the speaker's colour at draw time."""
    return portable_speaker_fabric()


def gui_progress_track():
    """16x5, nine_slice border 2: a dark groove, shadowed on top."""
    img = Image.new("RGBA", (16, 5), (0, 0, 0, 0))
    for y in range(5):
        for x in range(16):
            if x in (0, 15) and y in (0, 4):
                continue
            edge = x in (0, 15) or y in (0, 4)
            px(img, x, y, (12, 12, 14) if edge else (24, 24, 28) if y == 1 else (44, 44, 50))
    return img


def gui_progress_fill():
    """16x3, nine_slice border 1: copper with a highlight on top and a darker bottom row."""
    img = Image.new("RGBA", (16, 3), (0, 0, 0, 0))
    for x in range(16):
        px(img, x, 0, COPPER_L); px(img, x, 1, COPPER); px(img, x, 2, COPPER_D)
    return img


def gui_progress_knob():
    """5x9 handle: outline with cut corners, white highlight top-left, grey shadow bottom-right."""
    img = Image.new("RGBA", (5, 9), (0, 0, 0, 0))
    for y in range(9):
        for x in range(5):
            if x in (0, 4) and y in (0, 8):
                continue
            if x in (0, 4) or y in (0, 8):
                px(img, x, y, GUI_INK)
            elif x == 1 or y == 1:
                px(img, x, y, GUI_LIGHT)
            elif x == 3 or y == 7:
                px(img, x, y, (120, 120, 120))
            else:
                px(img, x, y, (212, 212, 212))
    return img


# 9x9 icons: a glyph of at most 8x8, centred in the top-left 8x8, with a 1 px drop shadow down-right.
# Each glyph maps its characters to colours ('.' is empty).
SHUFFLE = ["......#",
           "#...###",
           ".#.#..#",
           "..#....",
           ".#.#..#",
           "#...###",
           "......#"]
REPEAT = [".....#..",
          "#######.",
          "#....#.#",
          "#......#",
          "#.#....#",
          ".#######",
          "..#....."]
POWER = ["...#...",
         ".#.#.#.",
         "#..#..#",
         "#.....#",
         "#.....#",
         ".#...#.",
         "..###.."]
ICON_GLYPHS = {
    "shuffle": (SHUFFLE, {"#": OFF_GREY}),
    "shuffle_on": (SHUFFLE, {"#": CYAN}),
    "previous": (["#...#",
                  "#..##",
                  "#.###",
                  "#####",
                  "#.###",
                  "#..##",
                  "#...#"], {"#": WHITE}),
    "play": (["#...",
              "##..",
              "###.",
              "####",
              "###.",
              "##..",
              "#..."], {"#": PLAY_INK}),
    "pause": (["##.##"] * 7, {"#": PLAY_INK}),
    "next": (["#...#",
              "##..#",
              "###.#",
              "#####",
              "###.#",
              "##..#",
              "#...#"], {"#": WHITE}),
    "repeat_off": (REPEAT, {"#": OFF_GREY}),
    "repeat_all": (REPEAT, {"#": CYAN}),
    "repeat_one": ([".....#..",
                    "#######.",
                    "#...w#.#",
                    "#..ww..#",
                    "#.#.w..#",
                    ".#######",
                    "..#....."], {"#": CYAN, "w": WHITE}),
    "power_on": (POWER, {"#": (96, 236, 120)}),
    "power_off": (POWER, {"#": OFF_GREY}),
    "plus": (["..##..",
              "..##..",
              "######",
              "######",
              "..##..",
              "..##.."], {"#": (96, 224, 96)}),
    "remove": (["##..##",
                ".####.",
                "..##..",
                "..##..",
                ".####.",
                "##..##"], {"#": (232, 72, 72)}),
    "folder": (["+++.....",
                "dddddddd",
                "d++++++d",
                "d######d",
                "d######d",
                "d######d",
                "dddddddd"], {"#": (232, 190, 80), "+": (252, 226, 140), "d": (170, 120, 36)}),
    "playlist": (["######..",
                  "........",
                  "######..",
                  "......##",
                  "####..#.",
                  "......#.",
                  "....###."], {"#": (150, 206, 255)}),
    "track": (["..##..",
               "..#+#.",
               "..#..#",
               "..#...",
               "..#...",
               "###...",
               "###..."], {"#": WHITE, "+": WHITE_D}),
    "pc": (["oooooooo",
            "o++###.o",
            "o+####.o",
            "o######o",
            "oooooooo",
            "...oo...",
            ".oooooo."], {"o": (210, 210, 214), "#": (40, 150, 190), "+": (130, 230, 250)}),
    "usb": ([".www.",
             ".w.w.",
             ".www.",
             "#####",
             "#+++#",
             "#+dd#",
             "#+dd#",
             "#####"], {"w": (200, 200, 206), "#": (30, 60, 120), "+": (90, 140, 220), "d": (50, 96, 180)}),
    "stream": ([".#...#.",
                "#..+..#",
                "#.+++.#",
                "#..+..#",
                ".#.+.#.",
                "...+...",
                "..+++.."], {"#": (150, 206, 255), "+": WHITE}),
    "lock": ([".ssss.",
              "s....s",
              "s....s",
              "######",
              "##dd##",
              "###d##",
              "######"], {"s": (176, 176, 184), "#": (226, 186, 70), "d": (110, 80, 20)}),
}


def gui_icon(name):
    rows, palette = ICON_GLYPHS[name]
    h, w = len(rows), max(len(r) for r in rows)
    x0, y0 = (8 - w) // 2, (8 - h) // 2
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    cells = [(x, y, palette[ch]) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch in palette]
    filled = {(x, y) for x, y, _ in cells}
    for x, y, _ in cells:
        if (x + 1, y + 1) not in filled:
            px(img, x0 + x + 1, y0 + y + 1, ICON_SHADOW)
    for x, y, c in cells:
        px(img, x0 + x, y0 + y, c)
    return img


LED = [".ooo.",
       "o+##o",
       "o###o",
       "o##do",
       ".ooo."]


def gui_led(on):
    main, light, dark = (LED_ON, (200, 255, 210), (60, 190, 90)) if on else (LED_OFF, (90, 50, 50), (40, 20, 20))
    colours = {"o": (16, 16, 18), "#": main, "+": light, "d": dark}
    img = Image.new("RGBA", (5, 5), (0, 0, 0, 0))
    for y, row in enumerate(LED):
        for x, ch in enumerate(row):
            if ch in colours:
                px(img, x, y, colours[ch])
    return img


def nine_slice(w, h, border):
    return {"type": "nine_slice", "width": w, "height": h, "border": border}


GUI = {  # sprite path under textures/gui/sprites -> (fn, gui scaling or None)
    "speaker/window": (gui_window, nine_slice(16, 16, 4)),
    "speaker/inset": (gui_inset, nine_slice(16, 16, 1)),
    "speaker/fabric": (gui_fabric, {"type": "tile", "width": 16, "height": 16}),
    "speaker/progress_track": (gui_progress_track, nine_slice(16, 5, 2)),
    "speaker/progress_fill": (gui_progress_fill, nine_slice(16, 3, 1)),
    "speaker/progress_knob": (gui_progress_knob, None),
    **{f"speaker/{n}": ((lambda n=n: gui_icon(n)), None) for n in ICON_GLYPHS},
    "speaker/led_on": (lambda: gui_led(True), None),
    "speaker/led_off": (lambda: gui_led(False), None),
}


def save_gui(name, img, scaling):
    path = GUI_DIR / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    meta = path.with_name(path.name + ".mcmeta")
    if scaling:
        meta.write_text(json.dumps({"gui": {"scaling": scaling}}, indent=2) + "\n", encoding="utf-8")
    elif meta.exists():
        meta.unlink()


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
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
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
    for name, (fn, scaling) in GUI.items():
        if args.only and args.only != name:
            continue
        img = fn(); save_gui(name, img, scaling); tiles.append((name, img))

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
