"""Machine textures: the coil winder (brushed steel casing, copper spool, status lamp), its products (copper wire,
voice coil) and the machine screen (furnace-like layout: the inputs, a redstone bolt gauge above the energy slot, a
progress arrow tinted by the input, large output). Imported by gen_textures.py. All original, drawn with Pillow; the
shapes were picked by the user from proposals (block A v2, wire 5 "hank", coil 7 "ribbed", screen A with the thin
arrow and bolt 2)."""
from __future__ import annotations

import math
import random as _r

from PIL import Image, ImageDraw

S = 16


COPPER = [(92, 44, 30), (128, 62, 40), (166, 84, 54), (196, 106, 70), (224, 136, 96), (243, 170, 128)]


IRON_F = [(70, 72, 76), (120, 122, 126), (178, 180, 183), (216, 218, 220)]


LAMP_OFF = (70, 34, 32)


LAMP_ON = [(120, 220, 110), (200, 255, 170)]


def px(img, x, y, c):
    img.putpixel((x, y), (*c, 255))


def spool(img, x0, y0, w, h, k=0, flange=IRON_F, horizontal=True):
    """Spool with a horizontal axis: iron flanges a pixel taller than the copper windings, the windings shaded like a
    cylinder (light near the top), one turn per column, and a dark seam that moves down with the frame (rotation)."""
    rows = h - 2
    shade = [4, 5, 3, 3, 2, 1, 1, 0][:rows] if rows <= 8 else [4, 5] + [3] * (rows - 5) + [2, 1, 0]
    seam = (k * 2) % rows if k is not None else None
    for y in range(y0, y0 + h):
        tone = 3 if y == y0 else (1 if y == y0 + h - 1 else 2)
        px(img, x0, y, flange[tone])
        px(img, x0 + w - 1, y, flange[max(0, tone - 1)])
    for i in range(rows):
        y = y0 + 1 + i
        for x in range(x0 + 1, x0 + w - 1):
            t = shade[i] + (1 if (x - x0) % 2 and shade[i] < 5 else 0)
            if seam is not None and i == seam and 0 < i < rows - 1:
                t = max(0, shade[i] - 2)
            px(img, x, y, COPPER[t])
    # axle hubs
    px(img, x0, y0 + h // 2, flange[0])
    px(img, x0 + w - 1, y0 + h // 2, flange[0])


def lamp(img, x, y, on, size=2):
    for dy in range(size - 1):
        for dx in range(size):
            px(img, x + dx, y + dy, LAMP_ON[0] if on else LAMP_OFF)
    if on:
        px(img, x, y, LAMP_ON[1])


G_BG, G_LIGHT, G_DARK, G_EDGE = (198, 198, 198), (255, 255, 255), (85, 85, 85), (0, 0, 0)


SLOT_IN, SLOT_DARK = (139, 139, 139), (55, 55, 55)


def gui_base():
    img = Image.new("RGBA", (176, 166), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((1, 1, 174, 164), fill=G_BG)
    d.line((1, 0, 172, 0), fill=G_EDGE); d.line((0, 1, 0, 162), fill=G_EDGE)
    d.line((3, 165, 174, 165), fill=G_EDGE); d.line((175, 3, 175, 164), fill=G_EDGE)
    d.line((1, 1, 172, 1), fill=G_LIGHT); d.line((1, 1, 1, 162), fill=G_LIGHT)
    d.line((2, 2, 171, 2), fill=G_LIGHT); d.line((2, 2, 2, 161), fill=G_LIGHT)
    d.line((3, 164, 174, 164), fill=G_DARK); d.line((174, 3, 174, 164), fill=G_DARK)
    d.line((4, 163, 173, 163), fill=G_DARK); d.line((173, 4, 173, 163), fill=G_DARK)
    for row in range(3):
        for col in range(9):
            slot(img, 7 + col * 18, 83 + row * 18)
    for col in range(9):
        slot(img, 7 + col * 18, 141)
    return img


def slot(img, x, y, size=18):
    d = ImageDraw.Draw(img)
    d.rectangle((x, y, x + size - 1, y + size - 1), fill=SLOT_IN)
    d.line((x, y, x + size - 2, y), fill=SLOT_DARK); d.line((x, y, x, y + size - 2), fill=SLOT_DARK)
    d.line((x + 1, y + size - 1, x + size - 1, y + size - 1), fill=G_LIGHT)
    d.line((x + size - 1, y + 1, x + size - 1, y + size - 1), fill=G_LIGHT)


def blank():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


STEEL2 = [(34, 36, 40), (46, 48, 53), (58, 61, 66), (71, 74, 79), (85, 88, 93), (100, 103, 108), (117, 120, 125),
          (136, 139, 143), (158, 160, 163)]


def brushed(img, pal, x0, y0, x1, y1, top, bottom, seed, spread=1):
    """Each row is a walk of short horizontal streaks (2-5 px) around a level that fades from top to bottom."""
    rnd = _r.Random(seed)
    for y in range(y0, y1 + 1):
        f = (y - y0) / max(1, y1 - y0)
        level = top + (bottom - top) * f
        x = x0
        while x <= x1:
            t = round(level + rnd.uniform(-spread - 0.4, spread + 0.4))
            for _ in range(rnd.randint(2, 5)):
                if x > x1:
                    break
                px(img, x, y, pal[max(0, min(len(pal) - 1, t))])
                x += 1


def bevel2(img, pal, x0, y0, x1, y1, light, dark, seed):
    rnd = _r.Random(seed)
    for x in range(x0, x1 + 1):
        px(img, x, y0, pal[light - (rnd.random() < 0.25)])
        px(img, x, y1, pal[dark + (rnd.random() < 0.25)])
    for y in range(y0, y1 + 1):
        px(img, x0, y, pal[light - (rnd.random() < 0.25) - (1 if y > (y0 + y1) / 2 else 0)])
        px(img, x1, y, pal[dark + (rnd.random() < 0.25)])


def casing2(seed):
    img = blank()
    brushed(img, STEEL2, 0, 0, 15, 15, 5.2, 3.4, seed)
    bevel2(img, STEEL2, 0, 0, 15, 15, 8, 1, seed + 1)
    bevel2(img, STEEL2, 1, 1, 14, 14, 6, 2, seed + 2)
    return img


def a2_front(k=None):
    img = casing2(111)
    # recessed window: dark gradient, lit bottom-right lip
    brushed(img, [(16, 16, 19), (22, 22, 26), (29, 30, 34), (37, 38, 43)], 3, 3, 12, 10, 1.2, 2.6, 113, 0)
    bevel2(img, STEEL2, 2, 2, 13, 11, 1, 7, 114)
    spool(img, 4, 3, 8, 8, k)
    # control strip: two knobs and the lamp
    brushed(img, STEEL2, 3, 13, 12, 13, 2, 2, 115, 0)
    for x in (4, 6):
        px(img, x, 13, STEEL2[7]); px(img, x, 12, STEEL2[6])
    lamp(img, 11, 13, k is not None)
    return img


def a2_side():
    img = casing2(121)
    for y in (5, 10):
        for x in range(2, 14):
            px(img, x, y, STEEL2[1])
            px(img, x, y + 1, STEEL2[6 if x < 8 else 5])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, x, y, STEEL2[8]); px(img, x + (1 if x < 8 else -1), y + (1 if y < 8 else -1), STEEL2[1])
    return img


def a2_top():
    img = casing2(131)
    brushed(img, [(16, 16, 19), (24, 24, 28), (32, 33, 37)], 5, 5, 10, 10, 0.6, 1.6, 132, 0)
    for y in range(5, 11, 2):
        for x in range(5, 11):
            px(img, x, y, STEEL2[3] if x < 8 else STEEL2[2])
    bevel2(img, STEEL2, 4, 4, 11, 11, 1, 7, 133)
    return img


CU = [(74, 34, 24), (102, 48, 32), (132, 64, 42), (160, 82, 54), (188, 102, 68), (214, 126, 86), (236, 154, 110),
      (250, 190, 150)]


def torus(img, cx, cy, rx, ry, th, pal, strands=0, seed=0):
    """Thick ring at 3/4: lit on its top-left outer face, shadowed inside bottom-right; strands = dark turn lines."""
    rnd = _r.Random(seed)
    n = len(pal)
    inner = 1 - th / max(rx, ry)
    for y in range(S):
        for x in range(S):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = math.hypot(dx, dy)
            if not inner <= d <= 1:
                continue
            ang = math.atan2(dy, dx)
            across = (d - inner) / (1 - inner)
            light = -math.cos(ang + math.pi * 0.75)
            bulge = 1 - abs(across - 0.45) * 1.6
            t = 2.6 + light * 1.8 + bulge * 1.8 + rnd.uniform(-0.35, 0.35)
            if strands and int((ang + math.pi) / (2 * math.pi) * strands * 2) % 2 and across > 0.25:
                t -= 1
            img.putpixel((x, y), (*pal[max(0, min(n - 1, round(t)))], 255))


def edge_dark(img, pal):
    """Pixels with an empty neighbour to the right or below drop two tones on the ramp (vanilla item edge)."""
    out = img.copy()
    for y in range(S):
        for x in range(S):
            if img.getpixel((x, y))[3] == 0:
                continue
            for dx, dy in ((1, 0), (0, 1)):
                xx, yy = x + dx, y + dy
                if not (0 <= xx < S and 0 <= yy < S) or img.getpixel((xx, yy))[3] == 0:
                    rgb = img.getpixel((x, y))[:3]
                    i = min(range(len(pal)), key=lambda j: sum((pal[j][c] - rgb[c]) ** 2 for c in range(3)))
                    out.putpixel((x, y), (*pal[max(0, i - 2)], 255))
                    break
    return out


def w5_hank():
    img = blank()
    torus(img, 8, 8.5, 7.2, 5.6, 2.6, CU, strands=9, seed=5)
    for i, (x, y) in enumerate(((11, 12), (12, 13), (13, 14), (14, 15))):
        px(img, x, y, CU[5 - i])
    return edge_dark(img, CU)


def cyl(img, x0, x1, ytop, ybot, ry, body_of, inner_pal, seed=0):
    """Hollow cylinder at 3/4: lit a third from the left, open top with a dark interior."""
    rnd = _r.Random(seed)
    cx, rx = (x0 + x1 + 1) / 2, (x1 - x0 + 1) / 2
    for y in range(S):
        for x in range(x0, x1 + 1):
            dx = (x + 0.5 - cx) / rx
            e = ry * math.sqrt(max(0.0, 1 - dx * dx))
            if not ytop - e <= y + 0.5 <= ybot + e:
                continue
            pal, extra = body_of(y)
            light = 1 - abs((x + 0.5 - x0) / (x1 - x0 + 1) - 0.32) * 2.2
            t = 1 + light * (len(pal) - 2) + extra + rnd.uniform(-0.3, 0.3)
            img.putpixel((x, y), (*pal[max(0, min(len(pal) - 1, round(t)))], 255))
    for y in range(S):
        for x in range(x0, x1 + 1):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - ytop) / ry
            d = math.hypot(dx, dy)
            if d <= 1 and img.getpixel((x, y))[3]:
                if d < 0.72:
                    img.putpixel((x, y), (*inner_pal[min(len(inner_pal) - 1, int((dy + 1) * 1.5))], 255))
                else:
                    pal, _ = body_of(int(ytop))
                    img.putpixel((x, y), (*pal[-1 if dy < 0.2 else -2], 255))


def v7_ribbed():
    img = blank()
    cyl(img, 2, 13, 5.0, 11.0, 2.6, lambda y: (CU, -1.2 if y % 2 else 0.3),
        [(40, 20, 14), (58, 30, 20), (80, 42, 28)], 7)
    for i in range(4):
        px(img, 4, 3 - i, CU[6])
        px(img, 11, 3 - i, CU[4])
    return edge_dark(img, CU)


# ---------------------------------------------------------------- registries read by gen_textures.py
ON_FRAMES = 4


def coil_winder_front_on():
    return [a2_front(k) for k in range(ON_FRAMES)]


# Positions shared with MachineMenu / MachineScreen (Java constants): change both together.
INPUT_CENTER_FRAME = (47, 16)  # frame of the middle input; the used inputs are centred on it
SLOT_STEP = 18
ENERGY_FRAME = (47, 52)
BOLT_POS = (49, 36)  # 14x14 energy bolt between the input and the energy slot, like the furnace flame
ARROW_POS = (89, 34)  # 24x16 progress arrow, the furnace's proportions
OUTPUT_FRAME = (120, 30, 26)
MOLD_FRAME = (25, 16)  # left of the input (MachineMenu.MOLD_X/MOLD_Y minus the frame)
CONTAINER_SIZE = 256


def machine_container(inputs, mold=False):
    """A machine window on a 256x256 sheet: its used inputs (and the mold slot), empty arrow and gauge (filled at
    draw time)."""
    img = gui_base()
    if mold:
        slot(img, *MOLD_FRAME)
    for i in range(inputs):
        slot(img, INPUT_CENTER_FRAME[0] + (2 * i - (inputs - 1)) * SLOT_STEP // 2, INPUT_CENTER_FRAME[1])
    slot(img, *ENERGY_FRAME)
    bolt(img, *BOLT_POS, lit=False)
    thin_arrow(img, *ARROW_POS, SLOT_IN)
    slot(img, OUTPUT_FRAME[0], OUTPUT_FRAME[1], OUTPUT_FRAME[2])
    sheet = Image.new("RGBA", (CONTAINER_SIZE, CONTAINER_SIZE), (0, 0, 0, 0))
    sheet.paste(img, (0, 0))
    return sheet


# The bolt chosen by the user (proposal 2, "fin"): 14 rows, 3-pixel strokes.
BOLT = [
    ".......####...", "......####....", ".....####.....", "....####......", "...####.......",
    "..#########...", "......####....", ".....####.....", ".....###......", "....###.......",
    "....##........", "...##.........", "...#..........", "..#..........."]
BOLT_TONES = [(160, 18, 12), (214, 36, 26), (255, 86, 64)]  # edge, body, lit side


def bolt(img, x0, y0, lit):
    """Lit: redstone tones, light on the left edge, dark on the right and below, a grey drop shadow on the right like
    the furnace flame. Unlit: only the grey outline."""
    pts = {(x, y) for y, row in enumerate(BOLT) for x, ch in enumerate(row) if ch == "#"}
    for (x, y) in pts:
        left, right = (x - 1, y) not in pts, (x + 1, y) not in pts
        below, above = (x, y + 1) not in pts, (x, y - 1) not in pts
        if not lit:
            if left or right or below or above:
                px(img, x0 + x, y0 + y, SLOT_IN)
            continue
        c = BOLT_TONES[2] if left else (BOLT_TONES[0] if right or below else BOLT_TONES[1])
        px(img, x0 + x, y0 + y, c)
        if right and x + 1 < len(BOLT[0]):
            px(img, x0 + x + 1, y0 + y, SLOT_IN)


ARROW_FILL = (255, 255, 255)


def thin_arrow(img, x0, y0, colour):
    """The furnace arrow's shape (24x16), a 3-row shaft and a 15-row head, in one flat colour."""
    for y in range(16):
        for x in range(24):
            shaft = y in (6, 7, 8) and 1 <= x <= (22 if y == 7 else 21)
            head = 15 <= x and x - 15 <= min(y, 15 - y)
            lower_edge = y == 9 and 1 <= x <= 14
            if shaft or head or lower_edge:
                px(img, x0 + x, y0 + y, colour)


def machine_progress():
    """The plain white arrow, drawn over the empty one as the craft advances, tinted by the screen."""
    img = Image.new("RGBA", (24, 16), (0, 0, 0, 0))
    thin_arrow(img, 0, 0, ARROW_FILL)
    return img


def machine_energy():
    """The lit bolt, 14x14, drawn over the outline from below as the energy fills."""
    img = Image.new("RGBA", (14, 14), (0, 0, 0, 0))
    bolt(img, 0, 0, lit=True)
    return img


BLOCKS = {"coil_winder_front": a2_front, "coil_winder_side": a2_side, "coil_winder_top": a2_top}
ANIMATED = {"coil_winder_front_on": (coil_winder_front_on, 2)}
ITEMS = {"copper_wire": w5_hank, "voice_coil": v7_ribbed}
GUI = {"container/machine/progress": (machine_progress, None)}
GUI["container/machine/energy"] = (machine_energy, None)
CONTAINERS = {  # textures/gui/container/<name>.png
    "coil_winder": lambda: machine_container(1),
    "magnetizer": lambda: machine_container(2),
    "etcher": lambda: machine_container(3),
    "extruder": lambda: machine_container(1, mold=True),
}
