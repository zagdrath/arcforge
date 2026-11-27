"""Paints the fusion tier's block and item textures (docs/TEXTURE_STYLE.md, "Fusion tier") and writes the port modules'
UV-unwrapped block models with their 64x64 sheets. Run from the repo root: python tools/fusion_texture_gen.py

  - The tokamak's blocks (textures/block/fusion/): the Cryostat Casing (a riveted wall plate with a flange strap and a
    plasma-violet name plate), the Vessel Port (a bolted octagonal flange round a dark bore), the coils (their winding
    packs of copper-gold superconducting tape in a recessed steel case: the Central Solenoid's turns run round it, the
    Toroidal Field Coil's up it, the Poloidal Field Coil's along it between clamps, the Stellarator Coil's twist), the
    First Wall (castellated tungsten armour tiles), the Divertor Cassette (tungsten monoblocks over the steel cassette
    and its cooling pipe), the Breeding Blanket (lead-lithium channels in a recess) and the controller (the plasma's
    cross-section on its screen).
  - The plants: Cryogenic Plant and Cryogenic Distillation Column casings in frost-white insulated cladding with a pale
    cyan cold line, the Tritium Plant's lead-grey glovebox wall, and their controllers' screens.
  - The single blocks: the Quench Protection Unit, the Lithium Isotope Separator and the Tritium Extraction Unit.
  - The items: the lithium fluorides, Lead-Lithium, the wire, tape and coil, the Lithium Target Rod, the Tritium Canister
    and the Activated Component.
  - The six port modules: element models on 64x64 sheets, every face its own painted rectangle at 1 texel per px.
"""

import json
import math
import os

from PIL import Image

ROOT = "src/main/resources/assets/arcforge/"
TEX = ROOT + "textures/"
BLOCK = TEX + "block/fusion/"
ITEM = TEX + "item/"

# --- Palettes ---


def c(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255)


LIT, MID, SHADE, FACE_L, FACE_D, DARK, DEEP, RIVET = (c(h) for h in ("#727982", "#575D65", "#383C42", "#474C53", "#40454C", "#2B2F34",
                                                                      "#16181B", "#959DA6"))
CLEAR = (0, 0, 0, 0)
OUTLINE = c("#0C0D0F")
SCREEN = c("#1A1E22")
CYAN, CYAN_LIT, CYAN_DIM = c("#5FD4C4"), c("#B8F2EA"), c("#2F6E68")
RED, RED_DARK = c("#E04A30"), c("#A02A18")
GREEN, GREEN_LIT = c("#3AA058"), c("#8AE09A")
YELLOW, BLACK = c("#E0B020"), c("#1A1A1A")
# Plasma violet (the fusion tier's accent).
VIOLET = [c(h) for h in ("#2A1A4A", "#4A2E80", "#7A52C8", "#A884EC", "#D8C4FA")]
# Superconducting tape: copper-gold.
TAPE = [c(h) for h in ("#3E2610", "#6A4420", "#9A6A30", "#C69048", "#E8BC70")]
# Tungsten: a warm pale grey.
TUNGSTEN = [c(h) for h in ("#3A3936", "#5A5852", "#7A7770", "#9C988F", "#BEB9AE")]
# Lead-lithium: lilac silver.
PBLI = [c(h) for h in ("#2E2A40", "#4C4664", "#6E6690", "#958CB6", "#C0B8DA")]
# Frost-white insulated cladding and its cold line.
FROST = [c(h) for h in ("#5E6870", "#7C8890", "#96A2AA", "#AEB9C0", "#C6D0D6", "#DCE4E8")]
COLD = [c(h) for h in ("#2E5A66", "#4E8EA0", "#7CC0D2", "#B4E6F0")]
# Lead (the Tritium Plant's walls).
LEAD = [c(h) for h in ("#2E3440", "#3D4451", "#4C5462", "#5E6776", "#717A8A", "#A2AAB8")]
# Tritium: its radioluminescent green.
TRITIUM = [c(h) for h in ("#1C4424", "#2E7A3C", "#4CB860", "#9AF0A8")]
HOT = [c(h) for h in ("#5A1C0A", "#A03A12", "#E0701C", "#F8B048")]


class Canvas:
    def __init__(self, w=16, h=16, fill=CLEAR):
        self.img = Image.new("RGBA", (w, h), fill)
        self.w, self.h = w, h

    def px(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.putpixel((x, y), colour)

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, colour)

    def get(self, x, y):
        return self.img.getpixel((x, y))

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)


# --- The 16 px building blocks (section 3) ---


def frame(cv, face=(FACE_L, FACE_D), rivets=True):
    """The single-block machine casing: the split face, the 2 px bevels, the corner rivets."""
    for y in range(16):
        for x in range(16):
            cv.px(x, y, face[0] if x < 10 else face[1])
    for i in range(16):
        cv.px(i, 0, LIT)
        cv.px(0, i, LIT)
        cv.px(i, 15, DEEP)
        cv.px(15, i, DEEP)
    for i in range(1, 15):
        cv.px(i, 1, MID)
        cv.px(1, i, MID)
        cv.px(i, 14, DARK)
        cv.px(14, i, DARK)
    for x, y in ((0, 15), (15, 0), (1, 14), (14, 1)):
        cv.px(x, y, SHADE)
    if rivets:
        for x, y, sx, sy in ((2, 2, 3, 3), (13, 2, 12, 3), (2, 13, 3, 12), (13, 13, 12, 12)):
            cv.px(x, y, RIVET)
            cv.px(sx, sy, DARK)


def recess(cv, x0, y0, x1, y1, floor=DARK):
    """A recess: the top/left lip deepest, the bottom/right lip lit, its corners shade."""
    cv.rect(x0, y0, x1, y1, floor)
    for x in range(x0, x1 + 1):
        cv.px(x, y0, DEEP)
        cv.px(x, y1, LIT)
    for y in range(y0, y1 + 1):
        cv.px(x0, y, DEEP)
        cv.px(x1, y, LIT)
    cv.px(x1, y0, SHADE)
    cv.px(x0, y1, SHADE)


def strap(cv, y, x0=0, x1=15):
    """The 3 px riveted strap."""
    for x in range(x0, x1 + 1):
        cv.px(x, y, MID)
        cv.px(x, y + 1, RIVET if x % 4 == 1 else DARK if x % 4 == 2 else SHADE)
        cv.px(x, y + 2, DEEP)


def slats(cv, x0, y0, x1, y1):
    """A vent inside a recess (x0..x1, y0..y1 its inside): slats with a shadow row under each."""
    y = y0
    while y + 1 <= y1:
        for x in range(x0, x1 + 1):
            cv.px(x, y, MID)
            cv.px(x, y + 1, DEEP)
        y += 3


def plain_top(cv, face=(FACE_L, FACE_D)):
    """A casing's top: the frame round a recessed panel (the nuclear casings' top)."""
    frame(cv, face)
    recess(cv, 4, 4, 11, 11)


# --- Tokamak blocks ---


def cryostat_casing():
    cv = Canvas()
    frame(cv)
    # The flange strap where two of the cryostat's rings bolt together.
    strap(cv, 6, 2, 13)
    # A plasma-violet name plate.
    recess(cv, 4, 10, 11, 12, VIOLET[2])
    for x in range(5, 11):
        cv.px(x, 11, VIOLET[3] if x < 8 else VIOLET[2])
    cv.px(5, 11, VIOLET[4])
    cv.save(BLOCK + "cryostat_casing.png")
    cv = Canvas()
    frame(cv)
    # The lid: a bolt circle round a recessed blind flange.
    recess(cv, 4, 4, 11, 11)
    for x, y in ((7, 3), (3, 7), (12, 8), (8, 12)):
        cv.px(x, y, RIVET)
    cv.save(BLOCK + "cryostat_casing_top.png")


def octagon_flange(cv, x0, y0, size, bore_floor=DEEP):
    """A bolted octagonal flange (size px across) round a dark bore, lit top-left."""
    x1, y1 = x0 + size - 1, y0 + size - 1
    cut = size // 4
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            dx = min(x - x0, x1 - x)
            dy = min(y - y0, y1 - y)
            if dx + dy < cut:
                continue
            edge = dx == 0 or dy == 0 or dx + dy == cut
            if edge:
                # Lit on the top/left half, dark on the bottom/right.
                cv.px(x, y, LIT if (x - x0) + (y - y0) < size - 1 else DEEP)
            else:
                cv.px(x, y, MID if (x - x0) + (y - y0) < size else SHADE)
    # The bore.
    b0, b1 = x0 + 3, x1 - 3
    recess(cv, b0, y0 + 3, b1, y1 - 3, bore_floor)
    return x0, y0, x1, y1


def vessel_port():
    cv = Canvas()
    frame(cv, rivets=False)
    octagon_flange(cv, 2, 2, 12)
    # Eight bolts on the flange.
    for x, y in ((7, 3), (3, 8), (12, 7), (8, 12), (5, 4), (10, 4), (4, 10), (11, 11)):
        cv.px(x, y, RIVET)
    cv.save(BLOCK + "vessel_port.png")


def turns_rows(cv, x0, y0, x1, y1):
    """Horizontal turns of tape: a lit row, a shade row and an insulation line, repeating."""
    for y in range(y0, y1 + 1):
        k = (y - y0) % 3
        tone = TAPE[3] if k == 0 else TAPE[2] if k == 1 else TAPE[0]
        for x in range(x0, x1 + 1):
            cv.px(x, y, tone)
        if k != 2:
            cv.px(x0, y, TAPE[4] if k == 0 else TAPE[3])
            cv.px(x1, y, TAPE[1])


def turns_cols(cv, x0, y0, x1, y1):
    """Vertical turns of tape."""
    for x in range(x0, x1 + 1):
        k = (x - x0) % 3
        tone = TAPE[3] if k == 0 else TAPE[2] if k == 1 else TAPE[0]
        for y in range(y0, y1 + 1):
            cv.px(x, y, tone)
        if k != 2:
            cv.px(x, y0, TAPE[4] if k == 0 else TAPE[3])
            cv.px(x, y1, TAPE[1])


def central_solenoid():
    cv = Canvas()
    frame(cv, rivets=False)
    # The turns run round the stack between its steel collars.
    turns_rows(cv, 2, 3, 13, 12)
    for x in range(2, 14):
        cv.px(x, 2, DEEP)
        cv.px(x, 13, LIT)
    cv.save(BLOCK + "central_solenoid.png")
    cv = Canvas()
    frame(cv)
    # Its end: the turns seen end-on as rings round the bore.
    for y in range(3, 13):
        for x in range(3, 13):
            r = max(abs(x - 7.5), abs(y - 7.5))
            if r <= 4.5:
                cv.px(x, y, TAPE[3] if int(r) % 2 == 0 else TAPE[1])
    recess(cv, 6, 6, 9, 9, DEEP)
    cv.save(BLOCK + "central_solenoid_top.png")


def coil_case(name, fill):
    cv = Canvas()
    frame(cv)
    recess(cv, 3, 3, 12, 12)
    fill(cv)
    cv.save(BLOCK + name + ".png")


def toroidal_field_coil():
    coil_case("toroidal_field_coil", lambda cv: turns_cols(cv, 4, 4, 11, 11))
    cv = Canvas()
    plain_top(cv)
    turns_rows(cv, 5, 5, 10, 10)
    cv.save(BLOCK + "toroidal_field_coil_top.png")


def poloidal_field_coil():
    def fill(cv):
        turns_rows(cv, 4, 4, 11, 11)
        # Two clamps across the winding pack.
        for x in (6, 9):
            for y in range(4, 12):
                cv.px(x, y, MID if x == 6 else SHADE)
            cv.px(x, 4, LIT)
            cv.px(x, 11, DEEP)
    coil_case("poloidal_field_coil", fill)
    cv = Canvas()
    plain_top(cv)
    turns_rows(cv, 5, 5, 10, 10)
    cv.save(BLOCK + "poloidal_field_coil_top.png")


def stellarator_coil():
    def fill(cv):
        # The modular coil's twist: turns running on the diagonal.
        for y in range(4, 12):
            for x in range(4, 12):
                k = (x + y) % 3
                cv.px(x, y, TAPE[3] if k == 0 else TAPE[2] if k == 1 else TAPE[0])
    coil_case("stellarator_coil", fill)
    cv = Canvas()
    plain_top(cv)
    for y in range(5, 11):
        for x in range(5, 11):
            k = (x - y) % 3
            cv.px(x, y, TAPE[3] if k == 0 else TAPE[2] if k == 1 else TAPE[0])
    cv.save(BLOCK + "stellarator_coil_top.png")


def first_wall():
    """Castellated tungsten armour: 4 px tiles, each lit top-left, offset by 2 px row to row."""
    cv = Canvas()
    for row in range(4):
        off = 2 if row % 2 else 0
        for col in range(-1, 5):
            x0, y0 = col * 4 + off, row * 4
            for y in range(y0, y0 + 4):
                for x in range(x0, x0 + 4):
                    dx, dy = x - x0, y - y0
                    tone = TUNGSTEN[4] if dy == 0 or dx == 0 else TUNGSTEN[0] if dy == 3 or dx == 3 else TUNGSTEN[2] if dx < 2 else TUNGSTEN[1]
                    if (dx == 0 and dy == 3) or (dx == 3 and dy == 0):
                        tone = TUNGSTEN[1]
                    cv.px(x, y, tone)
    # The block's own edges: lit top and left, dark bottom and right.
    for i in range(16):
        cv.px(i, 15, DEEP)
        cv.px(15, i, DEEP)
    cv.save(BLOCK + "first_wall.png")


def divertor_cassette():
    cv = Canvas()
    frame(cv, rivets=False)
    # The target: four tungsten monoblocks, each bored for its cooling tube.
    for i in range(4):
        x0 = 2 + i * 3
        for y in range(2, 9):
            for x in range(x0, x0 + 3):
                dx = x - x0
                tone = TUNGSTEN[4] if y == 2 or dx == 0 else TUNGSTEN[0] if dx == 2 or y == 8 else TUNGSTEN[2]
                cv.px(x, y, tone)
        cv.px(x0 + 1, 5, DEEP)
        cv.px(x0 + 1, 6, TUNGSTEN[3])
    # The cassette body under it: its cooling pipe in clamps, and the strap.
    for x in range(2, 14):
        cv.px(x, 9, DEEP)
        cv.px(x, 10, LIT)
        cv.px(x, 11, MID)
        cv.px(x, 12, DARK)
    for x in (4, 11):
        cv.px(x, 10, RIVET)
        cv.px(x, 11, SHADE)
        cv.px(x, 12, DEEP)
    for x in range(2, 14):
        cv.px(x, 13, DARK)
    cv.save(BLOCK + "divertor_cassette.png")
    cv = Canvas()
    for y in range(16):
        for x in range(16):
            dx = x % 4
            cv.px(x, y, TUNGSTEN[4] if dx == 0 else TUNGSTEN[0] if dx == 3 else TUNGSTEN[2])
    for i in range(16):
        cv.px(i, 0, TUNGSTEN[4])
        cv.px(i, 15, DEEP)
    cv.save(BLOCK + "divertor_cassette_top.png")


def breeding_blanket():
    cv = Canvas()
    frame(cv)
    recess(cv, 3, 3, 12, 12)
    # Lead-lithium running in its channels.
    for y in range(4, 12):
        k = (y - 4) % 3
        for x in range(4, 12):
            cv.px(x, y, PBLI[3] if k == 0 else PBLI[2] if k == 1 else DEEP)
        if k == 0:
            cv.px(4, y, PBLI[4])
    cv.save(BLOCK + "breeding_blanket.png")
    cv = Canvas()
    plain_top(cv)
    for y in range(5, 11):
        for x in range(5, 11):
            cv.px(x, y, PBLI[3] if x < 8 else PBLI[2])
    cv.px(5, 5, PBLI[4])
    cv.save(BLOCK + "breeding_blanket_top.png")


def controller_frame(cv, screen=(2, 2, 13, 10)):
    """A nuclear controller's face: the frame, a screen recess and the button strip under it."""
    for y in range(16):
        for x in range(16):
            cv.px(x, y, MID)
    for i in range(16):
        cv.px(i, 0, LIT)
        cv.px(0, i, LIT)
        cv.px(i, 15, DEEP)
        cv.px(15, i, DEEP)
        cv.px(i, 14, DARK)
        cv.px(14, i, DARK)
    cv.px(15, 0, SHADE)
    cv.px(0, 15, SHADE)
    for i in range(1, 15):
        cv.px(1, i, MID)
    x0, y0, x1, y1 = screen
    recess(cv, x0, y0, x1 + 1, y1 + 1, SCREEN)
    for x in range(x0 + 1, x1 + 1):
        for y in range(y0 + 1, y1 + 1):
            cv.px(x, y, SCREEN)
    # The button strip.
    for x in range(2, 14):
        cv.px(x, 12, DEEP)
        cv.px(x, 13, MID if x % 2 == 0 else SHADE)


def fusion_reactor_controller():
    cv = Canvas()
    controller_frame(cv)
    # The plasma's D-shaped cross-section, glowing violet.
    d = ["..XX.", ".XOOX", "XOOOX", "XOOOX", ".XOOX", "..XX."]
    for dy, row in enumerate(d):
        for dx, ch in enumerate(row):
            if ch == "X":
                cv.px(3 + dx, 4 + dy, VIOLET[3])
            elif ch == "O":
                cv.px(3 + dx, 4 + dy, VIOLET[2])
    cv.px(5, 5, VIOLET[4])
    # Readouts: power and density bars, and the status LEDs.
    for i, h in enumerate((4, 2, 3)):
        for y in range(10 - h, 10):
            cv.px(9 + i, y, CYAN if i != 1 else CYAN_DIM)
    cv.px(11, 3, GREEN_LIT)
    cv.px(12, 3, RED)
    cv.px(9, 3, CYAN_LIT)
    # Run and Fast Shutdown on the strip.
    cv.px(3, 13, GREEN)
    cv.px(12, 13, RED)
    cv.px(12, 12, RED_DARK)
    cv.save(BLOCK + "fusion_reactor_controller.png")


# --- The plants ---


def frost_panel(cv, x0, y0, x1, y1):
    """Insulated cold-box cladding: frost-white, lit top-left, split like a casing face."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            cv.px(x, y, FROST[4] if x < x0 + (x1 - x0 + 1) * 10 // 16 else FROST[3])
    for x in range(x0, x1 + 1):
        cv.px(x, y0, FROST[5])
        cv.px(x, y1, FROST[1])
    for y in range(y0, y1 + 1):
        cv.px(x0, y, FROST[5])
        cv.px(x1, y, FROST[1])
    cv.px(x1, y0, FROST[2])
    cv.px(x0, y1, FROST[2])


def cold_line(cv, y, x0=2, x1=13):
    for x in range(x0, x1 + 1):
        cv.px(x, y, COLD[2] if x < 10 else COLD[1])
    cv.px(x0, y, COLD[3])


def cryogenic_plant():
    cv = Canvas()
    frame(cv, rivets=False)
    frost_panel(cv, 2, 2, 13, 10)
    cold_line(cv, 11)
    for x in range(2, 14):
        cv.px(x, 12, DEEP)
    for x, y in ((2, 13), (13, 13)):
        cv.px(x, y, RIVET)
    cv.save(BLOCK + "cryogenic_plant_casing.png")
    cv = Canvas()
    frame(cv)
    recess(cv, 3, 3, 12, 12)
    slats(cv, 4, 4, 11, 11)
    cv.save(BLOCK + "cryogenic_plant_casing_top.png")
    cv = Canvas()
    controller_frame(cv)
    # A thermometer down to the cold end and the liquefier's readouts.
    for y in range(3, 10):
        cv.px(4, y, COLD[2] if y > 5 else CYAN_DIM)
    cv.px(4, 10, COLD[3])
    for i, h in enumerate((5, 3, 4, 2)):
        for y in range(10 - h, 10):
            cv.px(7 + i * 2, y, CYAN if i % 2 == 0 else COLD[2])
    cv.px(12, 3, CYAN_LIT)
    cv.px(3, 13, GREEN)
    cv.save(BLOCK + "cryogenic_plant_controller.png")


def cryogenic_column():
    """The distillation column band grammar: content rows 0-9, the tray sill 10-12, the strap 13-15."""
    cv = Canvas()
    for y in range(16):
        for x in range(16):
            cv.px(x, y, FACE_L if x < 10 else FACE_D)
    # Content: frost cladding sheets, a seam between them, the cold line under them.
    frost_panel(cv, 0, 0, 7, 8)
    frost_panel(cv, 8, 0, 15, 8)
    cold_line(cv, 9, 0, 15)
    for x in range(16):
        cv.px(x, 10, LIT)
    strap(cv, 13)
    cv.save(BLOCK + "cryogenic_column_casing.png")
    cv = Canvas()
    frame(cv)
    octagon_flange(cv, 3, 3, 10)
    cv.save(BLOCK + "cryogenic_column_casing_top.png")
    cv = Canvas()
    for y in range(16):
        for x in range(16):
            cv.px(x, y, FACE_L if x < 10 else FACE_D)
    # The screen pane inside the content band: tray temperatures falling down the column.
    recess(cv, 0, 0, 15, 9, SCREEN)
    for i, w in enumerate((9, 7, 5, 3)):
        for x in range(2, 2 + w):
            cv.px(x, 2 + 2 * i, CYAN if i % 2 == 0 else COLD[2])
    cv.px(13, 2, CYAN_LIT)
    cv.px(13, 4, GREEN)
    for x in range(16):
        cv.px(x, 10, LIT)
    strap(cv, 13)
    cv.save(BLOCK + "cryogenic_column_controller.png")


def lead_face(cv):
    frame(cv, face=(LEAD[3], LEAD[2]))


def tritium_plant():
    cv = Canvas()
    lead_face(cv)
    # The glovebox: a dark window with its glints, two glove ports under it.
    recess(cv, 3, 3, 12, 8, c("#1E2A2E"))
    cv.px(4, 4, c("#DCE8EE"))
    cv.px(5, 4, c("#A9BCC6"))
    cv.px(4, 5, c("#A9BCC6"))
    for gx in (3, 9):
        for y in range(10, 13):
            for x in range(gx, gx + 4):
                cv.px(x, y, LIT if (x == gx or y == 10) else DEEP if (x == gx + 3 or y == 12) else LEAD[1])
        cv.px(gx + 1, 11, BLACK)
        cv.px(gx + 2, 11, BLACK)
    cv.px(13, 10, TRITIUM[2])
    cv.save(BLOCK + "tritium_plant_casing.png")
    cv = Canvas()
    lead_face(cv)
    recess(cv, 4, 4, 11, 11, LEAD[1])
    cv.save(BLOCK + "tritium_plant_casing_top.png")
    cv = Canvas()
    controller_frame(cv)
    # Four product bars (D, T, He-3, He), the tritium inventory glowing green.
    for i, (h, colour) in enumerate(((4, CYAN), (6, TRITIUM[2]), (2, VIOLET[3]), (3, CYAN_DIM))):
        for y in range(10 - h, 10):
            cv.px(4 + i * 2, y, colour)
    cv.px(12, 3, TRITIUM[3])
    cv.px(12, 5, RED)
    cv.px(3, 13, GREEN)
    cv.save(BLOCK + "tritium_plant_controller.png")


# --- Single blocks ---


def quench_protection_unit():
    cv = Canvas()
    frame(cv)
    recess(cv, 3, 3, 12, 9)
    slats(cv, 4, 4, 11, 8)
    # The high-voltage plate: hazard stripes with a black bolt.
    for y in range(10, 13):
        for x in range(4, 12):
            cv.px(x, y, YELLOW if (x + y) % 4 < 2 else BLACK)
    for x in range(4, 12):
        cv.px(x, 10, YELLOW)
        cv.px(x, 12, YELLOW)
    for x, y in ((8, 10), (7, 11), (8, 11), (7, 12)):
        cv.px(x, y, BLACK)
    for x in range(4, 12):
        cv.px(x, 11, YELLOW)
    cv.px(8, 11, BLACK)
    cv.px(7, 11, BLACK)
    cv.save(BLOCK + "quench_protection_unit.png")
    cv = Canvas()
    frame(cv)
    recess(cv, 3, 3, 12, 12)
    slats(cv, 4, 4, 11, 11)
    cv.save(BLOCK + "quench_protection_unit_top.png")


def lithium_isotope_separator():
    for on in (False, True):
        cv = Canvas()
        frame(cv)
        # The vapour cell: a window with the lithium vapour, the laser across it when working.
        recess(cv, 3, 3, 12, 10, c("#1E2228"))
        for y in range(4, 10):
            for x in range(4, 12):
                cv.px(x, y, c("#262C34") if not on else (c("#3A2E52") if y in (6, 7) else c("#2A2638")))
        if on:
            for x in range(4, 12):
                cv.px(x, 6, VIOLET[3])
                cv.px(x, 7, VIOLET[2])
            cv.px(4, 6, VIOLET[4])
        cv.px(4, 4, c("#DCE8EE"))
        cv.px(5, 4, c("#A9BCC6"))
        # The magnet's band under it.
        for x in range(3, 13):
            cv.px(x, 11, TAPE[3] if x < 10 else TAPE[2])
            cv.px(x, 12, TAPE[1])
        cv.save(BLOCK + ("lithium_isotope_separator_front_on.png" if on else "lithium_isotope_separator_front.png"))
    cv = Canvas()
    frame(cv)
    recess(cv, 3, 5, 12, 10)
    turns_rows(cv, 4, 6, 11, 9)
    cv.save(BLOCK + "lithium_isotope_separator_side.png")
    cv = Canvas()
    frame(cv)
    recess(cv, 3, 3, 12, 12)
    slats(cv, 4, 4, 11, 11)
    cv.save(BLOCK + "lithium_isotope_separator_top.png")
    cv = Canvas()
    plain_top(cv)
    cv.save(BLOCK + "lithium_isotope_separator_bottom.png")


def tritium_extraction_unit():
    for on in (False, True):
        cv = Canvas()
        frame(cv)
        # The bake-out oven's door window: a target rod standing inside, glowing hot when baking.
        recess(cv, 4, 2, 11, 12, c("#1E2228"))
        for y in range(3, 12):
            for x in range(5, 11):
                cv.px(x, y, HOT[1] if on and 4 <= y <= 10 and x in (5, 10) else c("#262C34"))
        for y in range(3, 12):
            cv.px(7, y, c("#C8D0D6") if not on else HOT[3])
            cv.px(8, y, c("#6E7880") if not on else HOT[2])
        cv.px(7, 5, PBLI[3])
        cv.px(8, 5, PBLI[2])
        cv.px(13, 4, TRITIUM[2] if on else TRITIUM[0])
        cv.save(BLOCK + ("tritium_extraction_unit_front_on.png" if on else "tritium_extraction_unit_front.png"))
    cv = Canvas()
    frame(cv)
    # The heat line in and the tritium line out.
    recess(cv, 3, 4, 12, 6)
    for x in range(4, 12):
        cv.px(x, 5, HOT[2] if x < 10 else HOT[1])
    recess(cv, 3, 9, 12, 11)
    for x in range(4, 12):
        cv.px(x, 10, TRITIUM[2] if x < 10 else TRITIUM[1])
    cv.save(BLOCK + "tritium_extraction_unit_side.png")
    cv = Canvas()
    plain_top(cv)
    cv.px(7, 7, TRITIUM[2])
    cv.px(8, 7, TRITIUM[1])
    cv.save(BLOCK + "tritium_extraction_unit_top.png")


# --- Items ---


def recolour(src, dst, ramp, keep=lambda p: False):
    """Cody's method: each pixel onto the ramp by its brightness (the outline and kept pixels stay)."""
    im = Image.open(src).convert("RGBA")
    pixels = [im.getpixel((x, y)) for y in range(im.height) for x in range(im.width)]
    lums = sorted({lum(p) for p in pixels if p[3] and p[:3] != OUTLINE[:3] and not keep(p)})
    out = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            p = im.getpixel((x, y))
            if not p[3] or p[:3] == OUTLINE[:3] or keep(p):
                continue
            rank = lums.index(lum(p)) / max(1, len(lums) - 1)
            out.putpixel((x, y), ramp[min(len(ramp) - 1, int(round(rank * (len(ramp) - 1))))])
    out.save(dst)


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def soft_dust_ramp(ramp):
    """A dust ramp with its darkest tone split as tools/dust_shading_gen.py lifts it: outline 30%, inside 65%."""
    lift = lambda f: tuple(round(x + (y - x) * f) for x, y in zip(ramp[0][:3], ramp[1][:3])) + (255,)
    return [lift(0.30), lift(0.65)] + ramp[1:]


def items():
    # The fluorides: lithium fluoride's powder with a faint teal (enriched) or violet (Li-6) cast. Lithium fluoride has
    # the dusts' softened shadow side (tools/dust_shading_gen.py), so its darkest tone is two: the outline's, lifted 30%
    # toward the next tone, and the inside's, lifted 65%. The ramps carry the same two lifts.
    recolour(ITEM + "lithium_fluoride.png", ITEM + "enriched_lithium_fluoride.png",
             soft_dust_ramp([c(h) for h in ("#7C8A8A", "#A8B8B6", "#BCCCCA", "#D2E0DE", "#E6F2F0")]))
    recolour(ITEM + "lithium_fluoride.png", ITEM + "lithium_6_fluoride.png",
             soft_dust_ramp([c(h) for h in ("#847C90", "#ACA4BC", "#C0B8D0", "#D6D0E4", "#ECE8F4")]))
    # Lead-Lithium: the lead ingot, silvery lilac.
    recolour(ITEM + "lead_ingot.png", ITEM + "lead_lithium_ingot.png",
             [c(h) for h in ("#1C1A26", "#2E2A40", "#3E3854", "#4C4664", "#6E6690", "#958CB6", "#C0B8DA")])
    # The Lithium Target Rod: the fuel rod with its band in Li-6 violet.
    rod = Image.open(ITEM + "fuel_rod.png").convert("RGBA")
    band = {c("#1E4A24"): VIOLET[0], c("#2E6A30"): VIOLET[1], c("#3A8040"): VIOLET[2], c("#4C9A4C"): VIOLET[2], c("#6CBA60"): VIOLET[3]}
    for y in range(16):
        for x in range(16):
            p = rod.getpixel((x, y))
            if p in band:
                rod.putpixel((x, y), band[p])
    rod.save(ITEM + "lithium_target_rod.png")
    wire()
    tape()
    coil()
    canister()
    activated_component()


def outline(cv):
    """Draws the 1 px outline round every opaque pixel."""
    src = cv.img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] and src.getpixel((nx, ny)) != OUTLINE:
                    cv.px(x, y, OUTLINE)
                    break


def ring_item(cv, cx, cy, rx, ry, thick, tone_at):
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
            inner = ((x + 0.5 - cx) / max(0.1, rx - thick)) ** 2 + ((y + 0.5 - cy) / max(0.1, ry - thick)) ** 2
            if d <= 1.0 and inner > 1.0:
                cv.px(x, y, tone_at(x, y))


# The spool of tools/wire_texture_gen.py in Arcforge: Transmission (its wire coils), so the fusion tier's wire reads as
# one of that family: two black plastic flanges seen a little from above, the conductor wound between them.
SPOOL_PLASTIC = [c(h) for h in ("#0E0F10", "#1C1E20", "#2B2E31", "#3D4145", "#53585D")]
# Arcite-tungsten: the dark tungsten winding (dark to light), its arcite core glinting cyan on every other turn.
ARCITE_TUNGSTEN = [c(h) for h in ("#22211F", "#33322F", "#4A4844", "#64615B", "#7E7A72")]
ARCITE_CORE = c("#7FDCEE")


def spool(ramp, core=None):
    cv = Canvas()
    cx, rx, ry = 7.5, 7.0, 2.0
    top, bottom, body_rx = 3.5, 12.5, 4.8

    def inside(x, y, cy, sx, sy):
        return ((x + 0.5 - cx) / sx) ** 2 + ((y + 0.5 - cy) / sy) ** 2 <= 0.92

    def disc(cy, upper):
        for y in range(16):
            for x in range(16):
                if inside(x, y, cy + 1.0, rx, ry) and not inside(x, y, cy, rx, ry):
                    cv.px(x, y, SPOOL_PLASTIC[0])
                elif inside(x, y, cy, rx, ry):
                    rim = not inside(x, y, cy, rx - 1.2, ry - 0.7)
                    cv.px(x, y, SPOOL_PLASTIC[2] if rim else SPOOL_PLASTIC[4] if x + 0.5 < cx - 1 else SPOOL_PLASTIC[3])
                    if upper and inside(x, y, cy, 1.6, 0.7):
                        cv.px(x, y, SPOOL_PLASTIC[0])

    disc(bottom, False)
    for y in range(16):
        for x in range(16):
            if abs(x + 0.5 - cx) <= body_rx and top + 0.5 <= y + 0.5 <= bottom + 0.4:
                d = (x + 0.5 - (cx - 1.2)) / body_rx
                k = 4 if abs(d) < 0.3 else 3 if abs(d) < 0.7 else 2 if d < 1.2 else 1
                if x + 0.5 > cx + body_rx - 1:
                    k = 1
                if y % 2 == 1:
                    k = max(0, k - 1)
                cv.px(x, y, core if core and y % 2 == 0 and k == 4 else ramp[k])
    disc(top, True)
    # The loose end, off the winding's right side and out past the flanges.
    for i, (x, y) in enumerate(((13, 8), (14, 8), (15, 7))):
        cv.px(x, y, ramp[3] if i else ramp[2])
    outline(cv)
    return cv


def wire():
    """Arcite-Tungsten Wire: Arcforge: Transmission's wire coil, wound with the dark tungsten and its cyan arcite core."""
    spool(ARCITE_TUNGSTEN, ARCITE_CORE).save(ITEM + "arcite_tungsten_wire.png")


def lit_side(dx, dy, r):
    """How far a point on a round part faces the top-left light: 1 straight at it, -1 away."""
    return -(dx + dy) / max(0.5, r) / math.sqrt(2)


def tape():
    """A reel of superconducting tape: a steel flange rim, the copper-gold tape wound round in layers, a steel hub ring
    round an open hole. Shaded as one round part lit from the top-left: each layer's tone steps down round it from the lit
    side to the shaded side, the layers parting with a thin darker line every other turn."""
    cv = Canvas()
    steel = [c(h) for h in ("#3E444B", "#535A62", "#6A727A", "#8A929A", "#A8B0B8")]
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            if r > 6.9 or r < 3.1:
                continue  # outside the reel, or the hole through it
            lit = lit_side(dx, dy, r)
            if r > 5.9:
                # The flange rim: lit top-left, shaded bottom-right.
                tone = steel[4] if lit > 0.5 else steel[3] if lit > 0.0 else steel[2] if lit > -0.5 else steel[1]
            elif r < 4.1:
                # The hub ring round the hole: its inner edge the other way round (the hole's far wall catches the light).
                tone = steel[3] if lit < -0.3 else steel[1] if lit > 0.3 else steel[2]
            else:
                k = 4 if lit > 0.6 else 3 if lit > 0.15 else 2 if lit > -0.4 else 1
                if 4.9 < r < 5.3:
                    k = max(0, k - 1)  # the one line where the outer layers start
                tone = TAPE[k]
            cv.px(x, y, tone)
    outline(cv)
    cv.save(ITEM + "superconducting_tape.png")


def coil():
    """A superconducting coil: a ring wound all round with copper-gold turns of tape over a dark former (a toroidal
    winding), with the round hole through its middle. The turns run across the ring (radial stripes, a lit and a shade
    tone each), the whole ring lit from the top-left; two steel clamps hold it at the bottom."""
    cv = Canvas()
    turns = 12
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            if r > 6.9 or r < 3.0:
                continue
            lit = lit_side(dx, dy, r)
            # The ring's own roundness: its outer and inner edges turn away from you, darker.
            edge = r > 6.0 or r < 3.8
            a = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            gap = (a * turns) % 1.0 < 0.28  # the dark line between two turns
            k = 4 if lit > 0.45 else 3 if lit > -0.1 else 2 if lit > -0.6 else 1
            if edge:
                k -= 1
            if gap:
                k = min(k, 1) if lit < 0 else k - 2
            cv.px(x, y, TAPE[max(0, min(4, k))])
    outline(cv)
    cv.save(ITEM + "superconducting_coil.png")


def canister():
    """The Tritium Canister: the Hardened Canister's double-walled steel with a tritium-green glowing label."""
    im = Image.open(ITEM + "hardened_canister.png").convert("RGBA")
    swap = {c("#C9A2F2"): TRITIUM[3], c("#9A5CD6"): TRITIUM[2], c("#5E3290"): TRITIUM[1],
            c("#5A94EC"): TRITIUM[3], c("#4E86E0"): TRITIUM[2], c("#4478D0"): TRITIUM[2], c("#3868BE"): TRITIUM[1], c("#2E5CA6"): TRITIUM[0]}
    for y in range(16):
        for x in range(16):
            p = im.getpixel((x, y))
            if p in swap:
                im.putpixel((x, y), swap[p])
    # The second wall: a dark seam just inside the outline down both sides.
    for y in range(5, 15):
        for x in (3, 12):
            if im.getpixel((x, y)) not in (OUTLINE,) and im.getpixel((x, y))[3]:
                im.putpixel((x, y), c("#3E444B"))
    im.save(ITEM + "tritium_canister.png")


def activated_component():
    """A worn First Wall tile block (four castellated tungsten tiles), scorched where the plasma touched it, a radiation tag
    tied on. Each tile is bevelled (lit top-left, dark bottom-right, its face split lit/shade), and the tiles step darker
    from the top-left one to the bottom-right one, so the piece reads as a lit solid, not a flat grid."""
    cv = Canvas()
    T = TUNGSTEN
    shade = [c(h) for h in ("#2A2926", "#3A3936", "#4A4844", "#5A5852", "#6A6760", "#7A7770", "#8C8880", "#9C988F",
                            "#ACA89E", "#BEB9AE")]
    x0, y0, n = 1, 2, 5
    for ty in range(2):
        for tx in range(2):
            base = 6 - 2 * (tx + ty)  # the top-left tile lightest, the bottom-right darkest
            for y in range(n):
                for x in range(n):
                    if x == 0 or y == 0:
                        k = base + 3
                    elif x == n - 1 or y == n - 1:
                        k = base - 3
                    else:
                        k = base + 1 if x + y <= 3 else base
                    cv.px(x0 + tx * n + x, y0 + ty * n + y, shade[max(0, min(9, k))])
    # The scorch: a soot streak where the plasma touched, its lower edge burnt brown.
    for x, y in ((3, 5), (4, 5), (4, 6), (5, 6), (6, 7), (7, 7), (7, 8)):
        cv.px(x, y, c("#2A2622"))
    for x, y in ((3, 6), (5, 7), (6, 8), (7, 9)):
        cv.px(x, y, c("#5A4636"))
    # The tag on its string, the tag shaded like a plate (lit top-left, dark bottom-right).
    for x, y in ((10, 8), (11, 9)):
        cv.px(x, y, c("#B8B0A0"))
    tag = [c(h) for h in ("#8A6A14", "#B8901A", "#E0B020", "#F0CC50")]
    for y in range(10, 15):
        for x in range(10, 15):
            k = 3 if x == 10 or y == 10 else 0 if x == 14 or y == 14 else 2 if x + y < 25 else 1
            cv.px(x, y, tag[k])
    for x, y in ((12, 11), (11, 12), (12, 12), (13, 12), (11, 13), (13, 13)):
        cv.px(x, y, BLACK)
    outline(cv)
    cv.save(ITEM + "activated_component.png")


# --- Port module models (Mekanism-style element models on a 64x64 sheet) ---


class Sheet:
    """Packs faces into a 64x64 sheet row by row; each face is painted to its exact size at 1 texel per px."""

    def __init__(self, size=64):
        self.cv = Canvas(size, size)
        self.size = size
        self.x = self.y = self.row = 0

    def place(self, w, h):
        if self.x + w > self.size:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + h > self.size:
            raise ValueError("sheet full")
        at = (self.x, self.y)
        self.x += w
        self.row = max(self.row, h)
        return at


def paint_plate(cv, x0, y0, w, h, ramp, riveted=True, seam=None):
    """A plate: its field split lit/shade at 10/16, a 1 px lit top-left edge, a dark bottom-right edge, rivets on 9 px+."""
    lit_l, face_l, face_d, dark = ramp
    split = max(1, w * 10 // 16)
    for y in range(h):
        for x in range(w):
            tone = face_l if x < split else face_d
            if w > 2 and h > 2:
                if y == 0 or x == 0:
                    tone = lit_l
                elif y == h - 1 or x == w - 1:
                    tone = dark
            elif w <= 2 or h <= 2:
                tone = face_l if (x + y) == 0 else face_d
            cv.px(x0 + x, y0 + y, tone)
    if riveted and w >= 9 and h >= 9:
        for x, y in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            cv.px(x0 + x, y0 + y, RIVET)


STEEL = (LIT, FACE_L, FACE_D, DEEP)
STEEL_DARK = (MID, SHADE, DARK, DEEP)


def paint_round(cv, x0, y0, w, h, ramp7, along_x=True):
    """A round part's facet strip: a stepped ramp across its width (lightest just off centre, darkest at the edges)."""
    n = w if along_x else h
    for y in range(h):
        for x in range(w):
            t = (x if along_x else y) / max(1, n - 1)
            # Profile: dark edges, light just left of / above centre.
            k = 1.0 - abs(t - 0.4) / 0.6
            cv.px(x0 + x, y0 + y, ramp7[max(0, min(len(ramp7) - 1, int(k * (len(ramp7) - 1) + 0.5)))])


ROUND_STEEL = [c(h) for h in ("#2B2F34", "#383C42", "#40454C", "#474C53", "#575D65", "#646A72", "#727982")]
ROUND_TAPE = [TAPE[0], TAPE[1], TAPE[2], TAPE[2], TAPE[3], TAPE[3], TAPE[4]]
ROUND_COLD = [c(h) for h in ("#5E6870", "#7C8890", "#96A2AA", "#AEB9C0", "#C6D0D6", "#D4DCE0", "#DCE4E8")]


class Model:
    """An element model whose faces are packed (tallest first) into one sheet, 64x64 or, if they don't fit, 128x128."""

    def __init__(self, name):
        self.name = name
        self.elements = []
        self.faces = []

    def box(self, frm, to, paint, faces=("north", "south", "east", "west", "up", "down"), rotation=None):
        """paint(face, cv, x, y, w, h): paints the face's rectangle."""
        (x1, y1, z1), (x2, y2, z2) = frm, to
        dims = {"north": (x2 - x1, y2 - y1), "south": (x2 - x1, y2 - y1), "east": (z2 - z1, y2 - y1), "west": (z2 - z1, y2 - y1),
                "up": (x2 - x1, z2 - z1), "down": (x2 - x1, z2 - z1)}
        out = {}
        for face in faces:
            w, h = (max(1, int(math.ceil(v - 1e-6))) for v in dims[face])
            entry = {"texture": "#t"}
            out[face] = entry
            self.faces.append((w, h, face, paint, entry))
        element = {"from": list(frm), "to": list(to), "faces": out}
        if rotation:
            element["rotation"] = rotation
        self.elements.append(element)

    def pack(self, size):
        sheet = Sheet(size)
        spots = []
        for w, h, face, paint, entry in sorted(self.faces, key=lambda f: (-f[1], -f[0])):
            spots.append((sheet.place(w, h), w, h, face, paint, entry))
        return sheet, spots

    def write(self):
        for size in (64, 128):
            try:
                sheet, spots = self.pack(size)
                break
            except ValueError:
                continue
        s = 16.0 / sheet.size
        for (x, y), w, h, face, paint, entry in spots:
            paint(face, sheet.cv, x, y, w, h)
            entry["uv"] = [round(x * s, 4), round(y * s, 4), round((x + w) * s, 4), round((y + h) * s, 4)]
        tex = "arcforge:block/fusion/module/" + self.name
        sheet.cv.save(BLOCK + "module/" + self.name + ".png")
        model = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": {"t": tex, "particle": tex}, "elements": self.elements}
        path = ROOT + "models/block/fusion/" + self.name + ".json"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump(model, f, indent=2)
            f.write("\n")


def plate(ramp=STEEL, riveted=True):
    return lambda face, cv, x, y, w, h: paint_plate(cv, x, y, w, h, ramp, riveted)


def side_ramp(ramp7, axis):
    """The ramp across a cylinder along axis, on each side face."""
    def paint(face, cv, x, y, w, h):
        ends = {"y": ("up", "down"), "z": ("north", "south"), "x": ("east", "west")}[axis]
        if face in ends:
            # Round caps are one flat surface across the boxes that make them.
            cv.rect(x, y, x + w - 1, y + h - 1, ramp7[2])
            return
        if axis == "z":
            # Along z: on up/down the width runs across x; on east/west it runs across y.
            paint_round(cv, x, y, w, h, ramp7, along_x=face in ("up", "down"))
        elif axis == "x":
            paint_round(cv, x, y, w, h, ramp7, along_x=False)
        else:
            paint_round(cv, x, y, w, h, ramp7, along_x=True)
    return paint


def cylinder(m, cx, cz, y0, y1, r, ramp7, axis="y", c2=None):
    """A regular octagonal prism of apothem r: two crosses of boxes (arms r long, r·tan 22.5° wide), the second turned
    45°, their corners the octagon's vertices. Along y at (cx, cz) over y0..y1; along z at (cx, c2) over z y0..y1."""
    paint = side_ramp(ramp7, axis)
    a = r * math.tan(math.pi / 8)
    k = 0
    for turned in (False, True):
        for long_x in (True, False):
            hx, hz = (r, a) if long_x else (a, r)
            # Each box a hair shorter than the last, so the four end caps never share a plane (no z-fighting); only the
            # box's two outer facets and its caps are drawn (its long sides lie inside the others).
            e0, e1 = y0 + 0.01 * k, y1 - 0.01 * k
            k += 1
            rotation = None
            if axis == "y":
                frm, to = (cx - hx, e0, cz - hz), (cx + hx, e1, cz + hz)
                if turned:
                    rotation = {"angle": 45, "axis": "y", "origin": [cx, (y0 + y1) / 2, cz]}
                faces = (("east", "west") if long_x else ("north", "south")) + ("up", "down")
            else:
                cy = c2
                frm, to = (cx - hx, cy - hz, e0), (cx + hx, cy + hz, e1)
                if turned:
                    rotation = {"angle": 45, "axis": "z", "origin": [cx, cy, (y0 + y1) / 2]}
                faces = (("east", "west") if long_x else ("up", "down")) + ("north", "south")
            m.box(tuple(round(v, 4) for v in frm), tuple(round(v, 4) for v in to), paint, faces=faces, rotation=rotation)


def mount(m):
    """Every module's mount: the flange plate against the Vessel Port (the north face, z 0..2) and its collar."""
    m.box((1, 1, 0), (15, 15, 2), plate())
    m.box((5, 5, 2), (11, 11, 4), plate(STEEL_DARK, False))


def accent_plate(ramp4):
    return lambda face, cv, x, y, w, h: paint_plate(cv, x, y, w, h, ramp4, riveted=False)


def neutral_beam_injector():
    m = Model("neutral_beam_injector")
    mount(m)
    # The beamline box running back from the port, the neutraliser tank on it and the ion source at its end.
    m.box((4, 3, 4), (12, 11, 13), plate())
    m.box((5, 11, 6), (11, 14, 11), plate(STEEL_DARK))
    m.box((3, 2, 13), (13, 12, 16), plate())
    # The ion source's high-voltage bushing: a copper-gold band round the back.
    m.box((2.5, 6, 14), (13.5, 8, 15), accent_plate((TAPE[4], TAPE[3], TAPE[2], TAPE[0])))
    # Its feet.
    m.box((5, 0, 6), (7, 3, 12), plate(STEEL_DARK, False))
    m.box((9, 0, 6), (11, 3, 12), plate(STEEL_DARK, False))
    m.write()


def gyrotron():
    m = Model("gyrotron")
    mount(m)
    # The waveguide from the port up to the tube.
    m.box((6, 6, 4), (10, 10, 9), plate(STEEL_DARK, False))
    # The tube standing in its superconducting magnet: a cold collar round its middle.
    cylinder(m, 8, 11, 0, 16, 3, ROUND_STEEL)
    cylinder(m, 8, 11, 4, 9, 4.5, ROUND_COLD)
    cylinder(m, 8, 11, 9, 10, 4.75, ROUND_TAPE)
    m.write()


def pellet_injector():
    m = Model("pellet_injector")
    mount(m)
    # The gun barrel into the port, the breech and the pellet magazine (a cold box with its frost) above it.
    cylinder(m, 8, 8, 4, 12, 1.5, ROUND_STEEL, axis="z", c2=8)
    m.box((4, 4, 11), (12, 12, 16), plate())
    m.box((5, 12, 11.5), (11, 16, 15.5), lambda f, cv, x, y, w, h: paint_plate(cv, x, y, w, h, (FROST[5], FROST[4], FROST[3], FROST[1]), riveted=False))
    m.box((7, 1, 12), (9, 4, 15), plate(STEEL_DARK, False))
    m.write()


def gas_puff_valve():
    m = Model("gas_puff_valve")
    mount(m)
    # The valve body, the piezo actuator on it, the gas line down from it.
    m.box((5, 4, 4), (11, 11, 10), plate())
    cylinder(m, 8, 7, 10, 14, 2, ROUND_STEEL, axis="y")
    m.box((6.5, 14, 5.5), (9.5, 15, 8.5), accent_plate((CYAN_LIT, CYAN, CYAN_DIM, DEEP)))
    cylinder(m, 8, 8, 10, 16, 1, ROUND_STEEL, axis="z", c2=7.5)
    m.box((7, 0, 13), (9, 7.5, 15), plate(STEEL_DARK, False))
    m.write()


def cryopump():
    m = Model("cryopump")
    mount(m)
    # The pump body behind its gate valve, the cold head on top with its Liquid Helium line.
    m.box((3, 3, 4), (13, 13, 6), plate(STEEL_DARK))
    cylinder(m, 8, 8, 6, 15, 4.5, ROUND_STEEL, axis="z", c2=8)
    cylinder(m, 8, 11, 12.5, 16, 2, ROUND_COLD, axis="y")
    m.box((6, 15, 9), (10, 16, 13), accent_plate((COLD[3], COLD[2], COLD[1], COLD[0])))
    m.write()


def diagnostics_port():
    m = Model("diagnostics_port")
    mount(m)
    # The sensor head: a box with its viewport (a cyan-lit lens) and the cable out of its back.
    m.box((4, 4, 4), (12, 12, 12), plate())
    m.box((5.5, 12, 6), (10.5, 13, 10), accent_plate((CYAN_LIT, CYAN, CYAN_DIM, DEEP)))
    m.box((6, 6, 12), (10, 10, 14), plate(STEEL_DARK, False))
    cylinder(m, 8, 8, 14, 16, 1, ROUND_STEEL, axis="z", c2=8)
    m.write()


def direct_energy_converter():
    m = Model("direct_energy_converter")
    mount(m)
    # The converter's vacuum tank running back from the port, its collector grids standing on top in a row (the
    # decelerating electrodes, copper-gold, each a step taller toward the back), the high-voltage bushing (a white
    # ceramic insulator, banded) on the back, and its feet.
    m.box((3, 3, 4), (13, 12, 14), plate())
    for i, z in enumerate((5, 7.5, 10)):
        m.box((4, 12, z), (12, 13 + i, z + 1.5), accent_plate((TAPE[4], TAPE[3], TAPE[2], TAPE[0])))
    m.box((5, 5, 14), (11, 11, 16), plate(STEEL_DARK, False))
    cylinder(m, 8, 15, 12, 16, 1.6, ROUND_COLD, axis="y")
    m.box((6.25, 13.5, 13.25), (9.75, 14.25, 16.75), accent_plate((VIOLET[4], VIOLET[3], VIOLET[2], VIOLET[1])))
    m.box((4, 0, 6), (6, 3, 12), plate(STEEL_DARK, False))
    m.box((10, 0, 6), (12, 3, 12), plate(STEEL_DARK, False))
    m.write()


# --- Block models, blockstates, item definitions ---


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def block_model(name, data):
    write_json(ROOT + "models/block/fusion/" + name + ".json", data)


def tex(name):
    return "arcforge:block/fusion/" + name


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def models_and_states():
    hidden = ROOT + "models/block/fusion/tokamak_hidden.json"
    write_json(hidden, {"textures": {"particle": tex("cryostat_casing")}, "elements": []})
    for name, top in (("cryostat_casing", "cryostat_casing_top"), ("vessel_port", "cryostat_casing_top"),
                      ("central_solenoid", "central_solenoid_top"), ("toroidal_field_coil", "toroidal_field_coil_top"),
                      ("poloidal_field_coil", "poloidal_field_coil_top"), ("stellarator_coil", "stellarator_coil_top"),
                      ("first_wall", "first_wall"), ("divertor_cassette", "divertor_cassette_top"), ("breeding_blanket", "breeding_blanket_top")):
        block_model(name, {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": tex(top), "bottom": tex(top), "side": tex(name)}})
        ported = name in ("cryostat_casing", "vessel_port")
        formed = {"type": "arcforge:ported", "model": "arcforge:block/fusion/tokamak_hidden"} if ported else {"model": "arcforge:block/fusion/tokamak_hidden"}
        write_json(ROOT + "blockstates/" + name + ".json", {"variants": {"formed=false": {"model": "arcforge:block/fusion/" + name}, "formed=true": formed}})
    block_model("fusion_reactor_controller", {"parent": "minecraft:block/orientable_with_bottom", "textures": {
        "top": tex("cryostat_casing_top"), "bottom": tex("cryostat_casing_top"), "side": tex("cryostat_casing"), "front": tex("fusion_reactor_controller")}})
    controller_states("fusion_reactor_controller", "arcforge:block/fusion/fusion_reactor_controller", "arcforge:block/fusion/tokamak_hidden")
    # The plants.
    for kind in ("cryogenic_plant", "cryogenic_column", "tritium_plant"):
        block_model(kind + "_casing", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": tex(kind + "_casing_top"), "bottom": tex(kind + "_casing_top"), "side": tex(kind + "_casing")}})
        block_model(kind + "_controller", {"parent": "minecraft:block/orientable_with_bottom", "textures": {
            "top": tex(kind + "_casing_top"), "bottom": tex(kind + "_casing_top"), "side": tex(kind + "_casing"), "front": tex(kind + "_controller")}})
        block_model(kind + "_hidden", {"textures": {"particle": tex(kind + "_casing")}, "elements": []})
        write_json(ROOT + "blockstates/" + kind + "_casing.json", {"variants": {
            "formed=false": {"model": "arcforge:block/fusion/" + kind + "_casing"},
            "formed=true": {"type": "arcforge:ported", "model": "arcforge:block/fusion/" + kind + "_hidden"}}})
        controller_states(kind + "_controller", "arcforge:block/fusion/" + kind + "_controller", "arcforge:block/fusion/" + kind + "_hidden")
    # The Quench Protection Unit.
    block_model("quench_protection_unit", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": tex("quench_protection_unit_top"), "bottom": tex("cryostat_casing_top"), "side": tex("quench_protection_unit")}})
    write_json(ROOT + "blockstates/quench_protection_unit.json", {"variants": {"": {"model": "arcforge:block/fusion/quench_protection_unit"}}})
    # The two machines.
    for name, back in (("lithium_isotope_separator", "lithium_isotope_separator_side"), ("tritium_extraction_unit", "tritium_extraction_unit_side")):
        bottom = "lithium_isotope_separator_bottom" if name.startswith("lithium") else "tritium_extraction_unit_top"
        for on in (False, True):
            suffix = "_on" if on else ""
            block_model(name + suffix, {"parent": "minecraft:block/cube", "textures": {
                "north": tex(name + "_front" + suffix), "south": tex(back), "east": tex(name + "_side"), "west": tex(name + "_side"),
                "up": tex(name + "_top"), "down": tex(bottom), "particle": tex(name + "_side")}})
        variants = {}
        for facing, y in FACINGS:
            for lit in ("false", "true"):
                v = {"model": "arcforge:block/fusion/" + name + ("_on" if lit == "true" else "")}
                if y:
                    v["y"] = y
                variants["facing=%s,lit=%s" % (facing, lit)] = v
        write_json(ROOT + "blockstates/" + name + ".json", {"variants": variants})
    # The modules: their models face north (toward the port) when facing=north.
    for name in ("neutral_beam_injector", "gyrotron", "pellet_injector", "gas_puff_valve", "cryopump", "diagnostics_port", "direct_energy_converter"):
        variants = {}
        for facing, y in FACINGS:
            v = {"model": "arcforge:block/fusion/" + name}
            if y:
                v["y"] = y
            variants["facing=" + facing] = v
        write_json(ROOT + "blockstates/" + name + ".json", {"variants": variants})


def controller_states(name, model, hidden):
    variants = {}
    for facing, y in FACINGS:
        for formed in ("false", "true"):
            for lit in ("false", "true"):
                if formed == "true":
                    v = {"type": "arcforge:ported", "model": hidden}
                else:
                    v = {"model": model}
                    if y:
                        v["y"] = y
                variants["facing=%s,formed=%s,lit=%s" % (facing, formed, lit)] = v
    write_json(ROOT + "blockstates/" + name + ".json", {"variants": variants})


BLOCK_ITEMS = ["cryostat_casing", "vessel_port", "central_solenoid", "toroidal_field_coil", "poloidal_field_coil", "stellarator_coil", "first_wall",
               "divertor_cassette", "breeding_blanket", "fusion_reactor_controller", "neutral_beam_injector", "gyrotron", "pellet_injector",
               "gas_puff_valve", "cryopump", "diagnostics_port", "direct_energy_converter", "quench_protection_unit", "lithium_isotope_separator", "tritium_extraction_unit",
               "cryogenic_plant_casing", "cryogenic_plant_controller", "cryogenic_column_casing", "cryogenic_column_controller",
               "tritium_plant_casing", "tritium_plant_controller"]
ITEMS = ["enriched_lithium_fluoride", "lithium_6_fluoride", "lead_lithium_ingot", "arcite_tungsten_wire", "superconducting_tape",
         "superconducting_coil", "lithium_target_rod", "tritium_canister", "activated_component"]
# Items drawn with another item's texture (none at present).
ITEM_TEXTURE = {}


def item_definitions():
    for name in BLOCK_ITEMS:
        write_json(ROOT + "items/" + name + ".json", {"model": {"type": "minecraft:model", "model": "arcforge:block/fusion/" + name}})
    for name in ITEMS:
        texture = ITEM_TEXTURE.get(name, name)
        write_json(ROOT + "models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": "arcforge:item/" + texture}})
        write_json(ROOT + "items/" + name + ".json", {"model": {"type": "minecraft:model", "model": "arcforge:item/" + name}})


def main():
    os.makedirs(BLOCK + "module", exist_ok=True)
    cryostat_casing()
    vessel_port()
    central_solenoid()
    toroidal_field_coil()
    poloidal_field_coil()
    stellarator_coil()
    first_wall()
    divertor_cassette()
    breeding_blanket()
    fusion_reactor_controller()
    cryogenic_plant()
    cryogenic_column()
    tritium_plant()
    quench_protection_unit()
    lithium_isotope_separator()
    tritium_extraction_unit()
    items()
    neutral_beam_injector()
    gyrotron()
    pellet_injector()
    gas_puff_valve()
    cryopump()
    direct_energy_converter()
    diagnostics_port()
    models_and_states()
    item_definitions()


if __name__ == "__main__":
    main()
