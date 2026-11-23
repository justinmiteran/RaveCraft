"""Materials textures (PR 7a): shale family, oil shale, neodymium ore and the raw materials.

Procedural textures are built here from their seeds; the ones whose validated shape was drawn by hand during the
proposals (shale bricks, chiseled shale, the item silhouettes, the oil shale tar seams) are stored as indexed maps:
a palette, dark to light, and 16 rows of palette indices ("." = transparent, or "keep the base" for an overlay).

Two textures start from a Mojang texture read from the local Minecraft jar at generation time (see
LICENSE-ASSETS.md): the neodymium ore draws its grains on vanilla deepslate, and the neodymium ingot is a recolour
of the vanilla iron ingot.
"""
from __future__ import annotations

import io
import random
import zipfile
from functools import lru_cache
from pathlib import Path

from PIL import Image

S = 16
MC_JAR = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"


def vanilla(path):
    """A texture from the local Minecraft client jar, e.g. "block/deepslate"."""
    if not MC_JAR.exists():
        raise SystemExit(f"Minecraft jar not found: {MC_JAR}. Run ./gradlew build once so Loom downloads it.")
    with zipfile.ZipFile(MC_JAR) as jar:
        data = jar.read(f"assets/minecraft/textures/{path}.png")
    return Image.open(io.BytesIO(data)).convert("RGBA")


def indexed(spec, base=None):
    """Draws an indexed map (palette, rows...) on a transparent tile, or over a copy of base."""
    pal, *rows = spec
    img = base.copy() if base is not None else Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), (*pal[int(ch, 36)], 255))
    return img


# ---------------------------------------------------------------- shale: laminae in the colours of a real shale
BLUE = [(110, 116, 128), (126, 133, 145), (146, 153, 165)]
GREY = [(96, 92, 86), (116, 112, 106), (138, 135, 129)]
RUST = [(96, 81, 71), (114, 100, 89), (132, 117, 106)]
OCHRE = [(112, 95, 70), (134, 116, 90), (155, 138, 113)]
CREVICE = (66, 62, 58)
LIP = {"BLUE": (166, 173, 184), "GREY": (156, 153, 147), "RUST": (148, 134, 122), "OCHRE": (172, 155, 128)}
FAMILIES = {"BLUE": BLUE, "GREY": GREY, "RUST": RUST, "OCHRE": OCHRE}
SHALE_WEIGHTS = {"BLUE": 4, "GREY": 3, "RUST": 2, "OCHRE": 2}
SHALE_VARIANTS = 6
SHALE_COLOURS = 10


def shale_raw(seed, weights=SHALE_WEIGHTS, dark_lines=0.22, stack=0.55):
    """Each row is a lamina of long streaks (4-11 px). A streak takes a hue family (often the one above, so bands
    stack) and a tone; a dark streak has a crevice core, and the pixel above a crevice core gets a lit lip."""
    rnd = random.Random(seed)
    fams = list(weights)
    fam_at, tone_at = {}, {}
    for y in range(S):
        x, done = rnd.randrange(S), 0
        while done < S:
            n = rnd.randint(4, 11)
            above = fam_at.get(((x + n // 2) % S, y - 1))
            f = above if above and rnd.random() < stack else rnd.choices(fams, [weights[k] for k in fams])[0]
            dark = rnd.random() < dark_lines
            t = 0 if dark else rnd.choice((1, 1, 2))
            for i in range(n):
                xx = (x + i) % S
                fam_at[(xx, y)] = f
                tt = t
                if not dark and rnd.random() < 0.18:
                    tt = max(0, min(2, t + rnd.choice((-1, 1))))
                if dark and 0 < i < n - 1 and rnd.random() < 0.6:
                    tt = -1
                tone_at[(xx, y)] = tt
            x, done = x + n, done + n
    img = Image.new("RGBA", (S, S))
    for (x, y), t in tone_at.items():
        img.putpixel((x, y), (*(CREVICE if t == -1 else FAMILIES[fam_at[(x, y)]][t]), 255))
    for (x, y), t in tone_at.items():
        if t == -1 and tone_at[(x, (y - 1) % S)] >= 1:
            img.putpixel((x, (y - 1) % S), (*LIP[fam_at[(x, (y - 1) % S)]], 255))
    return img


def _mean(img):
    px = [img.getpixel((x, y))[:3] for y in range(S) for x in range(S)]
    blue = sum(1 for p in px if p[2] > p[0] + 6) / len(px)
    return [sum(p[c] for p in px) / len(px) for c in range(3)] + [blue * 100]


@lru_cache(maxsize=1)
def shale_variants():
    """The 6 random blockstate variants: of 400 seeds, the tiles closest to the median colour and blue share (so
    none reads as a patch on a wall), quantised together to one 10-colour palette."""
    cands = [(s, shale_raw(s)) for s in range(41, 441)]
    means = {s: _mean(img) for s, img in cands}
    med = [sorted(m[c] for m in means.values())[len(means) // 2] for c in range(4)]
    best = sorted(cands, key=lambda c: sum((means[c[0]][i] - med[i]) ** 2 for i in range(4)))[:SHALE_VARIANTS]
    strip = Image.new("RGB", (S * SHALE_VARIANTS, S))
    for i, (_, img) in enumerate(best):
        strip.paste(img.convert("RGB"), (i * S, 0))
    q = strip.quantize(colors=SHALE_COLOURS, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    q = q.convert("RGBA")
    return tuple(q.crop((i * S, 0, (i + 1) * S, S)) for i in range(SHALE_VARIANTS))


# ---------------------------------------------------------------- oil shale: shale + a straight tar seam
TAR = [(24, 21, 24), (32, 28, 31), (42, 37, 40), (58, 53, 57)]
# Per variant: the seam's first row and its two rows of TAR indices (the lower row is the thick part).
OIL_SEAMS = [
    (9, "1322111112231322", "00..00000...0..."),
    (9, "3121111111111122", "00.00000000000.."),
    (8, "111111121221..22", "0000000.0..0...."),
    (10, "221131131313....", "..0000000000...."),
    (9, "1322321111332122", "00....000000.0.."),
    (9, "2222232333122222", "..........0....."),
    (8, "1111111312..2111", "000000000....000"),
    (8, "1111211312..1311", "0000.00.0...0000"),
]


def oil_shale(i):
    y0, top, bottom = OIL_SEAMS[i]
    rows = ["." * S] * y0 + [top, bottom] + ["." * S] * (S - y0 - 2)
    return indexed((TAR, *rows), shale_variants()[i % SHALE_VARIANTS])


# ---------------------------------------------------------------- polished shale: soft gradients, bevelled frame
def _ramp(lo, hi, n=6):
    return [tuple(round(lo[c] + (hi[c] - lo[c]) * k / (n - 1)) for c in range(3)) for k in range(n)]


P_BLUE = _ramp((96, 101, 112), (150, 156, 165))
P_WARM = _ramp((98, 89, 80), (150, 139, 121))


def polished_shale(seed=52, warm_share=0.2, band=(2, 3)):
    """Like vanilla polished tuff: each row a random walk of +-1 tone around a level that alternates light and
    dark, mostly blue bands blending at their edges, lit top-left frame and shadowed bottom-right."""
    rnd = random.Random(seed)
    hue, y = [], 0
    while y < S:
        h = rnd.randint(*band)
        hue += [rnd.random() < warm_share] * h
        y += h
    img = Image.new("RGBA", (S, S))
    for y in range(1, S - 1):
        level = 3 if y % 2 else 2
        t, x = level + rnd.choice((-1, 0, 1)), 1
        while x < S - 1:
            for _ in range(rnd.randint(1, 3)):
                if x >= S - 1:
                    break
                w = hue[y % S]
                if hue[(y - 1) % S] != w and rnd.random() < 0.35:
                    w = not w
                img.putpixel((x, y), (*(P_WARM if w else P_BLUE)[max(1, min(4, t))], 255))
                x += 1
            t += rnd.choice((-1, 1)) if t == level else (level - t)
    for i in range(S):
        img.putpixel((i, 0), (*P_BLUE[4], 255))
        img.putpixel((0, i), (*P_BLUE[4], 255))
        img.putpixel((i, S - 1), (*P_WARM[1], 255))
        img.putpixel((S - 1, i), (*P_WARM[1], 255))
    img.putpixel((0, S - 1), (*P_BLUE[3], 255))
    img.putpixel((S - 1, 0), (*P_BLUE[3], 255))
    return img


# ---------------------------------------------------------------- neodymium ore: lilac grains on deepslate
ND_LILAC = ((58, 40, 62), (122, 86, 130), (160, 120, 170), (214, 184, 222))  # rim, shade, body, highlight
GRAINS = [["h#", "##s", ".sd"], [".h#", "h#s", "#sd"], ["h##", "#ss", ".d"], ["hh#", "##sd"], [".h", "h#s", "#sd", ".d"],
          ["h#.", "##s", ".sd"]]
GRAIN_GAP = 7
NEODYMIUM_SEEDS = range(110, 116)


def neodymium_ore(seed):
    """Vanilla-iron-like: 2-4 grains, no ring, the darkest ore tone inside the grain at its bottom right, plus a
    short crack or two in the deepslate."""
    rnd = random.Random(seed)
    img = vanilla("block/deepslate")
    for _ in range(rnd.randint(1, 2)):
        x, y = rnd.randint(1, 13), rnd.randint(1, 13)
        for i in range(rnd.randint(2, 3)):
            r, g, b, a = img.getpixel((x + i, y + i // 2))
            img.putpixel((x + i, y + i // 2), (int(r * .65), int(g * .65), int(b * .65), a))
    rim, shade, body, light = ND_LILAC
    tone = {"#": body, "h": light, "s": shade, "d": rim}
    placed, tries = [], 0
    target = rnd.choice((2, 3, 3, 4))
    while len(placed) < target and tries < 300:
        tries += 1
        g = rnd.choice(GRAINS)
        w = max(len(r) for r in g)
        x, y = rnd.randint(0, S - w), rnd.randint(0, S - len(g))
        if not all(abs(x - px) + abs(y - py) >= GRAIN_GAP for px, py in placed):
            continue
        placed.append((x, y))
        for cy, row in enumerate(g):
            for cx, c in enumerate(row):
                if c != ".":
                    img.putpixel(((x + cx) % S, (y + cy) % S), (*tone[c], 255))
    return img


# ---------------------------------------------------------------- neodymium ingot: the vanilla ingot, recoloured
def _ingot_ramp(lo, hi, bias):
    return [tuple(max(0, min(255, round(lo[c] + (hi[c] - lo[c]) * k / 7 + bias[c] * (1 - abs(2 * k / 7 - 1)))))
                  for c in range(3)) for k in range(8)]


INGOT = _ingot_ramp((54, 44, 62), (252, 246, 255), bias=(10, -6, 18))  # silver with a light lilac cast


def neodymium_ingot():
    """Iron ingot pixel layout, each tone mapped by luminance rank onto the neodymium ramp."""
    iron = vanilla("item/iron_ingot")
    ranks = sorted({iron.getpixel((x, y))[:3] for x in range(S) for y in range(S) if iron.getpixel((x, y))[3]},
                   key=sum)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            p = iron.getpixel((x, y))
            if p[3]:
                img.putpixel((x, y), (*INGOT[ranks.index(p[:3])], 255))
    return img


# ---------------------------------------------------------------- validated hand-made shapes (indexed maps)
SHALE_BRICKS = ([(66, 62, 58), (96, 88, 80), (116, 112, 106), (110, 116, 128), (127, 133, 143)],
    "4444244044443440",
    "4223331042332210",
    "4111111041111110",
    "4000000040000000",
    "3440444434404444",
    "2310422223104223",
    "1110411111104111",
    "0000400000004000",
    "4444244044443440",
    "4223331043332210",
    "4111111041111110",
    "4000000040000000",
    "3440444434404444",
    "2210422322104323",
    "1110411111104111",
    "0000400000004000")

CHISELED_SHALE = ([(66, 62, 58), (96, 88, 80), (114, 100, 89), (116, 112, 106), (110, 116, 128), (127, 133, 143)],
    "5555555555555550",
    "5512332433223340",
    "5400000000000520",
    "5202112131111530",
    "5301115252221530",
    "5201125135152530",
    "5401155555251540",
    "5301555355331540",
    "5301053555052530",
    "5202105055151520",
    "5301115250201510",
    "5201110151111510",
    "5401121101111510",
    "5305555555555520",
    "5433433313323430",
    "5000000000000000")

BITUMEN = ([(13, 11, 14), (22, 19, 23), (31, 27, 32), (42, 37, 43), (55, 50, 57), (74, 68, 78), (102, 97, 110), (140, 136, 150)],
    "................",
    "................",
    ".....1110.......",
    "....143630......",
    "...13357531110..",
    "..1245777555310.",
    ".135576545764410",
    ".134655344555410",
    ".012454344444410",
    "..0125444421110.",
    "...01111111000..",
    "....0000000.....",
    "................",
    "................",
    "................",
    "................")

RAW_NEODYMIUM = ([(54, 37, 60), (76, 52, 84), (98, 69, 106), (122, 86, 130), (141, 103, 150), (160, 120, 170), (187, 152, 197), (214, 184, 222)],
    "................",
    "................",
    "......110.......",
    ".....16760......",
    "....1677530.10..",
    "...167765311660.",
    "..1557754466750.",
    ".15577544657520.",
    ".1455444457740..",
    ".03444457775530.",
    "..044445555430..",
    "...0334444310...",
    "....00111100....",
    "......0000......",
    "................",
    "................")

SILICON = ([(30, 34, 46), (46, 52, 68), (80, 88, 110), (100, 109, 133), (124, 133, 158), (156, 168, 196), (196, 210, 236)],
    "................",
    "................",
    ".......10.......",
    ".....11650......",
    "....1664420.....",
    "...166443320....",
    "..16643434320...",
    ".16644443343210.",
    ".054443434644320",
    "..0343334644420.",
    "...02333444220..",
    "....024333210...",
    ".....0111100....",
    "......0000......",
    "................",
    "................")

SILICON_WAFER = ([(30, 34, 46), (62, 69, 88), (84, 88, 100), (133, 144, 176), (154, 138, 168), (163, 144, 144), (126, 159, 164), (158, 159, 141), (200, 206, 218)],
    "................",
    "................",
    "................",
    "....11111110....",
    "..113666777510..",
    ".14888366677750.",
    "1554488836667770",
    "1755544888366670",
    "1777555448883660",
    "0267775554488820",
    ".02267775554220.",
    "..002222222200..",
    "....00000000....",
    "................",
    "................",
    "................")


def _shale(i):
    return lambda: shale_variants()[i]


def _oil(i):
    return lambda: oil_shale(i)


def _ore(seed):
    return lambda: neodymium_ore(seed)


BLOCKS = {f"shale_{i}": _shale(i) for i in range(SHALE_VARIANTS)}
BLOCKS.update({f"oil_shale_{i}": _oil(i) for i in range(len(OIL_SEAMS))})
BLOCKS.update({f"deepslate_neodymium_ore_{i}": _ore(s) for i, s in enumerate(NEODYMIUM_SEEDS)})
BLOCKS.update({
    "polished_shale": polished_shale,
    "shale_bricks": lambda: indexed(SHALE_BRICKS),
    "chiseled_shale": lambda: indexed(CHISELED_SHALE),
})
ITEMS = {
    "bitumen": lambda: indexed(BITUMEN),
    "raw_neodymium": lambda: indexed(RAW_NEODYMIUM),
    "neodymium_ingot": neodymium_ingot,
    "silicon": lambda: indexed(SILICON),
    "silicon_wafer": lambda: indexed(SILICON_WAFER),
}
