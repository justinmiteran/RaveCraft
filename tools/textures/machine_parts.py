"""Machine products and molds of the 7c machines (magnetizer, etcher, extruder): iron and neodymium magnets, plastic
sheet and casing, circuit board, simple circuit, chip, reinforced membrane, and the four molds. Imported by
machines.py. All original: the user picked each one from rounds of Pillow proposals (molds A, plain discs N3,
circuits A, chip, membrane cone, sheet S2c first rework, casing O2 N3), then the 7d components (membrane A,
speaker driver and woofer E, amplifier board B); the chosen pixels are kept here as one
palette per item (darkest tone first) and a 16x16 grid, one letter per palette index, '.' for transparent."""
from __future__ import annotations

import string

from PIL import Image

S = 16
KEYS = string.ascii_lowercase + string.ascii_uppercase
SILHOUETTE = (85, 85, 85)  # vanilla empty-slot icons: one grey, outlines only


def grid(item):
    """The 16x16 image of a (palette, rows) item."""
    palette, rows = item
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != ".":
                img.putpixel((x, y), (*palette[KEYS.index(key)], 255))
    return img


IRON_MAGNET = ([
    (26, 26, 30), (32, 32, 37), (38, 38, 44), (45, 45, 52), (52, 52, 60), (60, 60, 69), (68, 68, 78),
    (78, 78, 89), (88, 88, 100), (101, 101, 114), (114, 114, 128), (132, 132, 146), (150, 150, 164)], [
    "................",
    "................",
    "................",
    "...lllllllllc...",
    "..mlkkkkkkkkkc..",
    ".mkkkkkkkjkjjjc.",
    ".lkkkkkkjjkkjjc.",
    ".hlkkjkjjjkkjia.",
    ".hgliihhhiihhca.",
    ".hgfggeddddbbba.",
    ".hgggheddddbbba.",
    ".chgggeeeffcbba.",
    "..cgggeeeeebba..",
    "...cccbccbbaa...",
    "................",
    "................",
])

PLASTIC_SHEET = ([
    (7, 7, 9), (14, 14, 18), (22, 22, 27), (31, 31, 37), (41, 41, 49), (53, 53, 63), (95, 95, 110),
    (140, 140, 156)], [
    "................",
    "................",
    ".........ggb....",
    ".......ggeeeb...",
    "......geeeeedb..",
    "....hgfeeeeeeeb.",
    "..hhfffeeeeeeeda",
    ".heeffeeeeedeeda",
    ".gefffeeedddedca",
    ".geefffeeddedca.",
    ".egfeeeeeeddaa..",
    ".beeeeeeedba....",
    "..adeeecca......",
    "...addcaa.......",
    "....aaa.........",
    "................",
])

PLASTIC_CASING = ([
    (14, 14, 17), (19, 19, 22), (24, 24, 28), (30, 30, 35), (36, 36, 42), (46, 46, 53), (56, 56, 64),
    (76, 76, 86), (96, 96, 108), (133, 133, 146), (170, 170, 184)], [
    "................",
    ".......ja.......",
    ".....kjgcja.....",
    "...kjggfcccja...",
    ".kkggggfcccbbja.",
    ".gfjjgaaaabhiba.",
    ".gfffjjabihbcca.",
    ".fffeeejjcbccca.",
    ".feeffffdcbccca.",
    ".feefffhccbbbca.",
    ".fdeeeegccbbaba.",
    ".fededefbbcbbba.",
    ".aaedeegbbbbbaa.",
    "...aaeegbbcaa...",
    ".....aafbaa.....",
    ".......aa.......",
])

CIRCUIT_BOARD = ([
    (10, 36, 20), (27, 62, 37), (42, 84, 51), (57, 105, 65), (70, 125, 78), (84, 145, 91), (97, 165, 104),
    (110, 184, 116)], [
    "................",
    "................",
    ".hhhhhhhhhhhhha.",
    ".hhbhhbehaggaga.",
    ".hghgggeeggegga.",
    ".hhghgheeegeeea.",
    ".hgbeeaggaeeaea.",
    ".hhgheeggeegeea.",
    ".hgegegggeeedea.",
    ".fhaeeaegaeeada.",
    ".heggeeeeededea.",
    ".hggeegeeeededa.",
    ".feaeeaedaddada.",
    ".acabaaaaaaaaaa.",
    "................",
    "................",
])

SIMPLE_CIRCUIT = ([
    (10, 10, 14), (10, 36, 20), (27, 27, 34), (42, 42, 51), (27, 62, 37), (57, 57, 67), (42, 84, 51),
    (151, 35, 23), (70, 70, 83), (178, 44, 30), (57, 105, 65), (97, 97, 113), (70, 125, 78), (230, 61, 44),
    (110, 110, 128), (84, 145, 91), (188, 102, 68), (97, 165, 104), (214, 126, 86), (110, 184, 116),
    (213, 197, 136), (234, 222, 158), (255, 246, 180)], [
    "................",
    "................",
    ".tttttttttttttb.",
    ".ttttttmtmvrmrb.",
    ".trwsqssmrsmrrb.",
    ".ttrtrtqmmqmmmb.",
    ".trpnhrsrmvmrmb.",
    ".ttrjvmsrmmrmmb.",
    ".trmrsrqssqsumb.",
    ".ptmmsrmroolckb.",
    ".tmrrqmmifffamb.",
    ".trvqsrmiffdmkb.",
    ".pmmmmmmkvqqukb.",
    ".bgbebbbbbbbbbb.",
    "................",
    "................",
])

NEODYMIUM_MAGNET = ([
    (69, 57, 78), (85, 71, 95), (100, 84, 111), (116, 98, 127), (131, 111, 143), (147, 125, 160),
    (176, 154, 188), (188, 169, 199), (201, 185, 210), (214, 200, 221), (227, 215, 233), (239, 230, 244),
    (252, 246, 255)], [
    "................",
    "................",
    "................",
    "...mmlllllllb...",
    "..mlkjkkkllkkb..",
    ".mlkkkkkkkklkkb.",
    ".llkkkkkkkkkkjb.",
    ".illkklkkkjkjha.",
    ".ihljiihihihhca.",
    ".ihhhgfeeeedcdb.",
    ".ihhhhfeeffddcb.",
    ".bgghhfffffddca.",
    "..bgggfffffdca..",
    "...bbbbbbbbaa...",
    "................",
    "................",
])

REINFORCED_MEMBRANE = ([
    (14, 14, 17), (24, 24, 28), (36, 36, 42), (56, 56, 64), (96, 96, 108), (170, 170, 184)], [
    "................",
    ".....dddeeb.....",
    "...dddddcccea...",
    "..dcedeeddccdb..",
    "..ddedddcccdbb..",
    ".ddddddcdccbbbb.",
    ".ddeddccdebbcbb.",
    ".ddedccceeebcbb.",
    ".ecdcddefffabbb.",
    ".ecdcceeffaabab.",
    ".bccccbefaaaaab.",
    "..eccbbbaaabac..",
    "..adbbccbbaaec..",
    "...bbbbbbaacc...",
    ".....bbbbbb.....",
    "................",
])

CHIP = ([
    (10, 10, 14), (42, 42, 51), (57, 57, 67), (60, 63, 70), (70, 70, 83), (84, 84, 98), (84, 88, 100),
    (92, 95, 101), (97, 97, 113), (110, 110, 128), (133, 144, 176), (154, 138, 168), (146, 148, 154),
    (126, 159, 164), (171, 174, 179)], [
    "................",
    "................",
    "....o.o.o.m.....",
    "...jjjjjjjijja..",
    "..jfejeeeceeca..",
    "..ijfefeceeeea..",
    "..ffiieeecccca..",
    "..jejecenkceca..",
    "..feeecclgccba..",
    "..ieeeccccccca..",
    "..fcceecccbcaa..",
    "..aaeacacacaa...",
    "....h.h.h.h.....",
    "....d.d.d.d.....",
    "................",
    "................",
])

SHEET_MOLD = ([
    (53, 53, 53), (78, 78, 80), (100, 100, 102), (122, 122, 124), (146, 146, 148), (176, 176, 178),
    (216, 216, 216), (250, 250, 250)], [
    "................",
    ".hhhhhhhhhhhhha.",
    ".hfgfgffffffffa.",
    ".hgfgfgffefeffa.",
    ".hfgfffffeffffa.",
    ".hgabaaaaaaaaga.",
    ".hfabbcbbbcbbga.",
    ".hgabcbbbcbbbga.",
    ".hfacbbbcbbbbga.",
    ".hfabbbcbbbcbfa.",
    ".heabbcbbbbbaga.",
    ".heggggggggfgda.",
    ".hfefeefdfdedda.",
    ".heffffeedededa.",
    ".faaaaaaaaaaaaa.",
    "................",
])

CASING_MOLD = ([
    (53, 53, 53), (78, 78, 80), (100, 100, 102), (122, 122, 124), (146, 146, 148), (176, 176, 178),
    (216, 216, 216), (250, 250, 250)], [
    "................",
    ".hhhhhhhhhhhhha.",
    ".hfgfgffffffffa.",
    ".hgfgfgffefeffa.",
    ".hfgaaaaaaaagfa.",
    ".hgfbbbcbbbcgea.",
    ".hffabggggabgea.",
    ".hgfagfeefgagea.",
    ".hffagfffefafea.",
    ".hfeaageefacgda.",
    ".hefabaaaabbfea.",
    ".hefacbbbcbagda.",
    ".hfeggggfgfgdda.",
    ".heffffeedededa.",
    ".faaaaaaaaaaaaa.",
    "................",
])

MEMBRANE_MOLD = ([
    (53, 53, 53), (78, 78, 80), (100, 100, 102), (122, 122, 124), (146, 146, 148), (176, 176, 178),
    (216, 216, 216), (250, 250, 250)], [
    "................",
    ".hhhhhhhhhhhhha.",
    ".hfgfgffffffffa.",
    ".hgfgfbaaageffa.",
    ".hfgaabbcbaagfa.",
    ".hgfbbggggacgea.",
    ".hfabgaaaagaaga.",
    ".hgabgaggagabga.",
    ".hfacgagfagabga.",
    ".hfabgaaabgabfa.",
    ".hegaaggggabfea.",
    ".hefacaaaabagda.",
    ".hfeggabbbfgdda.",
    ".hefffgggfededa.",
    ".faaaaaaaaaaaaa.",
    "................",
])

BOARD_MOLD = ([
    (53, 53, 53), (78, 78, 80), (100, 100, 102), (122, 122, 124), (146, 146, 148), (176, 176, 178),
    (216, 216, 216), (250, 250, 250)], [
    "................",
    ".hhhhhhhhhhhhha.",
    ".hfgfgffffffffa.",
    ".hgfgfgffefeffa.",
    ".hfbgagagagagfa.",
    ".hgabbacabacaga.",
    ".hfabbcbbbcbbga.",
    ".hgabcbbbcbbbga.",
    ".hfacbbbcbbbbga.",
    ".hfabbbcbbbcbfa.",
    ".heabbcbbbbbaga.",
    ".heagagagagagda.",
    ".hfgfgegdgdgdda.",
    ".heffffeedededa.",
    ".faaaaaaaaaaaaa.",
    "................",
])


MEMBRANE = ([
    (116, 99, 76), (141, 125, 100), (165, 150, 124), (189, 175, 148), (214, 201, 172), (238, 226, 196)], [
    "................",
    ".....dddeeb.....",
    "...dddddcccea...",
    "..dcedeeddccdb..",
    "..ddedddcccdbb..",
    ".ddddddcdccbbbb.",
    ".ddeddccdebbcbb.",
    ".ddedccceeebcbb.",
    ".ecdcddefffabbb.",
    ".ecdcceeffaabab.",
    ".bccccbefaaaaab.",
    "..eccbbbaaabac..",
    "..adbbccbbaaec..",
    "...bbbbbbaacc...",
    ".....bbbbbb.....",
    "................",
])


SPEAKER_DRIVER = ([
    (31, 31, 36), (38, 40, 46), (46, 46, 53), (64, 67, 73), (91, 93, 100), (116, 99, 76), (117, 120, 127),
    (141, 125, 100), (143, 147, 154), (165, 150, 124), (170, 173, 181), (189, 175, 148), (214, 201, 172),
    (238, 226, 196)], [
    "..kkkkkkkiiiid..",
    ".kiiiiiccgggkgd.",
    "kibicclljjccgbgd",
    "kiicmlmmlljjcggd",
    "kiclmllljjjlhagd",
    "kiclllljljjhhagd",
    "kilmlljjlmhhjhgd",
    "kclmljjjmmmhjhad",
    "icjljllmnnnfhhad",
    "igjljjmmnnffhfed",
    "igcjjjhmnffffaed",
    "igcjjhhhfffhfaed",
    "ikgchhjjhhffaeed",
    "dgbgaahhhfaaebed",
    ".dgggggaaeeeeed.",
    "..dddddddddddd..",
])


AMPLIFIER_BOARD = ([
    (24, 24, 28), (10, 36, 20), (36, 36, 42), (27, 62, 37), (38, 52, 100), (56, 56, 64), (42, 84, 51),
    (57, 105, 65), (96, 96, 108), (70, 125, 78), (92, 118, 190), (84, 145, 91), (187, 119, 82), (97, 165, 104),
    (145, 149, 156), (110, 184, 116), (212, 140, 97), (195, 199, 205), (220, 224, 230)], [
    "................",
    "................",
    ".pppppppppppppb.",
    ".pprprdrpbseseb.",
    ".pniiffajnkekeb.",
    ".ppifccajjeeeeb.",
    ".pnicccanbjjbjb.",
    ".ppaaaaanjjnjjb.",
    ".pnonononjsehjb.",
    ".lpbjjbjnbkebhb.",
    ".pjqmqmqmjeehjb.",
    ".pnnjjnjqmjhjhb.",
    ".ljbjjbjhbhhbhb.",
    ".bgbdbbbbbbbbbb.",
    "................",
    "................",
])


WOOFER = ([
    (14, 14, 17), (24, 24, 28), (31, 31, 36), (36, 36, 42), (38, 40, 46), (46, 46, 53), (56, 56, 64), (64, 67, 73),
    (91, 93, 100), (96, 96, 108), (117, 120, 127), (143, 147, 154), (170, 170, 184), (170, 173, 181)], [
    "..nnnnnnnllllh..",
    ".nlllllffkkknkh.",
    "nlelffggddffkekh",
    "nllfjgjjggddfkkh",
    "nlfgjgggdddgbckh",
    "nlfggggdgddbbckh",
    "nlgjggddgjbbdbkh",
    "nfgjgdddjjjbdbch",
    "lfdgdggjmmmabbch",
    "lkdgddjjmmaabaih",
    "lkfdddbjmaaaacih",
    "lkfddbbbaaabacih",
    "lnkfbbddbbaaciih",
    "hkekccbbbaccieih",
    ".hkkkkkcciiiiih.",
    "..hhhhhhhhhhhh..",
])



def _blank_mold():
    """The blank mold (7d choice A): the sheet mold's frame around a flat grained steel plate."""
    palette, rows = SHEET_MOLD
    plate = [list(row) for row in rows]
    for y in range(5, 11):
        for x in range(3, 13):
            plate[y][x] = "g" if (x * 3 + y * 5) % 7 == 0 else "f"
    return palette, ["".join(row) for row in plate]


BLANK_MOLD = _blank_mold()


def outline(mask):
    """The cells of a mask that touch a cell outside it."""
    return {(x, y) for x, y in mask
            if any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def mold_silhouette():
    """The empty mold slot icon, like the vanilla empty-slot silhouettes: the outline of the mold body and of its
    imprint (the sheet mold's darkest tones, inside the rim)."""
    rows = SHEET_MOLD[1]
    body = {(x, y) for y in range(S) for x in range(S) if rows[y][x] != "."}
    rim = outline(body)
    imprint = {(x, y) for x, y in body - rim if rows[y][x] in "abc"}
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for x, y in rim | outline(imprint):
        img.putpixel((x, y), (*SILHOUETTE, 255))
    return img


def _item(item):
    return lambda: grid(item)


ITEMS = {name.lower(): _item(item) for name, item in {
    "IRON_MAGNET": IRON_MAGNET, "PLASTIC_SHEET": PLASTIC_SHEET, "PLASTIC_CASING": PLASTIC_CASING,
    "CIRCUIT_BOARD": CIRCUIT_BOARD, "SIMPLE_CIRCUIT": SIMPLE_CIRCUIT, "NEODYMIUM_MAGNET": NEODYMIUM_MAGNET,
    "REINFORCED_MEMBRANE": REINFORCED_MEMBRANE, "CHIP": CHIP, "SHEET_MOLD": SHEET_MOLD, "CASING_MOLD": CASING_MOLD,
    "MEMBRANE_MOLD": MEMBRANE_MOLD, "BOARD_MOLD": BOARD_MOLD, "MEMBRANE": MEMBRANE,
    "SPEAKER_DRIVER": SPEAKER_DRIVER, "AMPLIFIER_BOARD": AMPLIFIER_BOARD, "WOOFER": WOOFER,
    "BLANK_MOLD": BLANK_MOLD}.items()}
