# Arcforge Texture Style Guide

This is the house style for every Arcforge block, item and GUI texture. The reference is the
**Distillation Array**, which follows vanilla Minecraft and Mekanism: flat tones, 1 px bevels and
light from the top left.
New textures must follow this guide, and older textures were brought in line with it by the
texture-unification drop.

---

## 1. Core rules

1. **Light from the top left.**
   - Top and left edges get the highlight.
   - Bottom and right edges get the shadow.
   - Recesses invert this: the shadow goes on the top/left lip and the light on the bottom/right lip.
2. **Flat faces.**
   - Use one or two tones per face.
   - Never use gradients, pillow shading, radial glows on metal, or dithering or checkerboards.
3. **No noise.**
   - Texture comes from *shapes*: straps, rivets, seams, slats and panels.
   - Never use random pixels or speckle.
   - Every isolated pixel must be intentional, for example a rivet, a glint or a pore.
4. **Tight palettes.**
   - About 8 greys for casing.
   - 3–5 tones per accent colour.
   - A 16 px machine face should come in around 8–15 colours in total.
5. **1 px bevels.**
   - A bevel is 1 px lit plus 1 px mid on the top/left, and 1 px dark plus 1 px deepest on the
     bottom/right.
6. **Dark machine steel.**
   - Machines sit on the dark ramp below, with a grey median of about 66–68 luminance.
   - Do not drift lighter; the lighter greys used earlier were rejected.
7. **Keep what already works.**
   - Accent and I/O colours, tier colours and animation frame counts stay as they are.
   - UV layouts and texture sizes do not change unless the model changes.

---

## 2. Machine steel ramp (casing palette)

| Role | Hex |
|---|---|
| Deepest (outline, bottom/right outer bevel, recess shadow, slat shadow) | `#16181B` |
| Dark (bottom/right inner bevel, rivet shadow, recess floor) | `#2B2F34` |
| Shade (bevel corner where lit meets dark) | `#383C42` |
| Face, shade half (x ≥ 10 on a 16 px face) | `#40454C` |
| Face, lit half (x < 10) | `#474C53` |
| Mid (top/left inner bevel, strap lit row, slats) | `#575D65` |
| Lit (top/left outer bevel, recess lower lip, edge beams) | `#727982` |
| Rivet / spec highlight | `#959DA6` |

---

## 3. Building blocks

### Single-block machine casing (16×16)

- **Face:** `#474C53` for x < 10 and `#40454C` for x ≥ 10. The split gives a soft cylindrical read.
- **Top-left bevel:** row and column 0 are `#727982`; row and column 1 are `#575D65`.
- **Bottom-right bevel:** row and column 15 are `#16181B`; row and column 14 are `#2B2F34`.
- **Bevel corners:** (0,15), (15,0), (1,14) and (14,1) are `#383C42`.
- **Rivets:** `#959DA6` at (2,2), (13,2), (2,13) and (13,13). Each has a `#2B2F34` shadow 1 px
  diagonally *inward*.
- **Centre art:** goes in the middle and is framed by a recess.

### Recess (windows, vents, sight glasses, ports)

- **Top and left lip:** `#16181B`.
- **Bottom and right lip:** `#727982`.
- **Lip corners:** the top-right and bottom-left corners are `#383C42`.
- **Floor:** `#2B2F34`, or content.

### Vent / grille

- Inside a recess, draw slats of `#575D65` with a `#16181B` shadow row directly below each slat.
- Use three slats in a 10 px recess.

### Riveted strap (3 px, tiles horizontally)

| Row | Colour |
|---|---|
| 1 | `#575D65` (lit) |
| 2 | Pattern repeating every 4 px: `#959DA6` rivet at x%4 = 1, `#2B2F34` shadow at x%4 = 2, `#383C42` elsewhere |
| 3 | `#16181B` |

### Wall plate (multiblocks)

- Use the same split face as the machine casing (`#474C53` / `#40454C` at x = 10).
- Add one strap per block on rows 13–15.

### Large plates (48 px array views)

- Snap the plate's tone to the ramp.
- Use the one-step-darker tone on the right ~38% of the plate.
- Give it a 1 px bevel and rivets along the edges.
- Never use pillow shading.

### Connected textures (CTM)

- **Outer edges only:** rims are drawn only on the outer edges of the structure; inner seams stay
  flat.
- **Carbonizer-style mask bits:** 1 = top, 2 = right, 4 = bottom, 8 = left.
  - `top_0` is a fully interior tile.
  - `top_15` is a lone block.
- **Rim colours:**
  - A top or left rim is `#727982`, then `#575D65`.
  - A bottom or right rim is `#16181B` (outer), then `#2B2F34`.
- **Rivets:** a rivet (with its shadow) sits where two outer rims meet.
- **Edge beams:** the Distillation edge beam is a lit row, a mid row, then a 1 px soft shadow
  (alpha about 70).
- **Unformed casings:** these show the beam on all four sides, so they read as single blocks.

---

## 4. Accent colours (unchanged across the mod)

| Use | Tones (light → dark) |
|---|---|
| FE port / power (red) | `#FF8577`, `#E5483C`, `#8E231C` |
| Fluid/heat output (orange) | `#F5B574`, `#E8913A`, `#C8782A`, `#9E5A1C` |
| Input (blue) | `#7FB3F0`, `#4A8FE0`, `#3A76C0`, `#22497E` |
| Status LED / arc (cyan) | `#B8F2EA`, `#5FD4C4`, `#2F6E68` |
| Hazard stripe | `#E0B020` / `#1A1A1A` |

**Tier colours.** Each tier palette runs outline, end face, front, bevel, top, rim highlight.

| Tier | Palette |
|---|---|
| Wrought (gold) | `#3A2A0C #8A661E #AE8430 #C99C44 #E0BA60 #F6DE98` |
| Tempered (copper) | `#3C1C0C #8A4822 #AE6632 #C98244 #E0A062 #F6C898` |
| Hardened (purple) | `#1E0E36 #52307E #7042A4 #8A55C2 #A476DA #CEAAF2` |
| Arcforged (cyan) | `#0A2A38 #1E6A88 #2E96B6 #40B6D8 #6AD2EE #B4F2FF` |

---

## 5. Family-specific rules

### Items

- **Outline:** vanilla-style dark outline `#0C0D0F`.
- **Tones:** 4–6 flat tones per item.
- **Shape:** straight, clean edges. No pointy tips, jagged steps, tails or stray pixels.
- **Tier alloys:** use Cody's reference chunk shape exactly: the `CHUNK` light map, 12×12 placed at
  (2,2), coloured through a 9-tone ramp built from the tier palette.
- **Portables** (Battery, Canister, Gas Cartridge, Thermal Capsule):
  - 5-tone steel in vertical column bands.
  - Adjacent columns differ by at most one step.
  - The tier colour appears as a flat band.
- **Porous or fibrous materials** (coke, slag, wools):
  - Use 4 flat tone clusters.
  - Show porosity with a few deliberate 2 px pits: a dark pit with a lit lower lip.
- **Ingots:** a flat top face, one glint, a darker front face and a darkest end face, with no
  stripes.

### Cylinders / round parts

- Use vertical column bands with the highlight just left of centre.
- Profile for columns 0–15: `0 1 1 2 3 4 5 4 3 3 2 2 1 1 0 0`.
- Pressurized Cylinder steel: `#3E444B #4E545C #646A72 #7E848C #9AA0A8 #B4BAC0 #D6DADE`.
- Round caps are flat concentric rings, lit on the top-left half and one step darker on the
  bottom-right half.

### Cells (Energy/Heat)

- Flat bevelled rim.
- The core window keeps its level art (levels 0–4).
- Frame rails are flat: a lit row `#727982` and a shade row `#383C42`, with tier accents at the
  ends and middle.

### Fluids

- Use a few flat tones, with sheen streaks drawn as shapes rather than noise.
- Animation strips are 16 frames.

### GUIs

- Dark panels: `#313131` face with a `#505050` highlight and a `#1A1A1A` shadow bevel. Use vanilla-style slots and dark machine screens.
- Keep text on screens short so it never overlaps.

### Glass (see-through multiblock windows)

- Glass is clear like vanilla glass: the pane is fully transparent (alpha 0) with two opaque 1 px
  diagonal glint streaks (`#DCE8EE`, `#A9BCC6`), so it stays cutout-safe.
- Whatever is behind the glass (fluids, vapour, rotors) is drawn by a renderer, not painted into the
  texture.

### Distillation column band grammar

Every face in the 2-wide column shares the same row layout, so lines run unbroken all the way round:

| Rows | Content |
|---|---|
| 0–9 | Content: glass, a screen or plain face |
| 10–12 | Tray sill: `#727982` on row 10, then face |
| 13–15 | The riveted strap |

The controller is a screen pane inside that band, not a boxed bezel.

### Interior skins

- Inside skins (`ctm/shell_liner`) are one step darker than the outside, with one riveted rib per block.
- Window openings get a 1/16 reveal (`ctm/shell_jamb`).
- Never put an interior plane in the same plane as an outer face.

### Multiblock port overlays (`block/port/<mode>`)

- A 10×10 steel collar, 1 px bevel, sitting at x3..12.
- A coloured ring inside it:
  - blue = in;
  - orange = out;
  - red = FE;
  - hot orange = heat.
- A dark bore with a 4×4 resource-colour chip.
- The overlay is transparent outside the collar.

### Tool modes

- A mode is shown by recolouring the item's grip with three tones:
  - Configure: cyan;
  - Rotate: green;
  - Port: orange;
  - Dismantle: red.
- The head stays unchanged.

### Natural blocks (asphalt, stone-like)

- 3 close dark tones in flat blotches 2–4 px across, plus a few 1–2 px lighter chips.
- No single-pixel noise, and the texture tiles seamlessly.

### 3D models (formed multiblocks, renderer models)

- **UV-unwrap every model** like Mekanism and Blockbench: one texture sheet per model, with every element
  face pointing at its own painted rectangle at 1 texel per px.
- **Never tile a 16 px block texture across model faces.** The seams, straps and bands then land at random
  on each part and read as stripes and noise.
- **Paint each face flat, sized to that face:**
  - a 1 px lit top/left edge and a dark bottom/right edge;
  - rivets only on faces 9 px or larger;
  - tiny faces (1–2 px) get one or two flat tones.
- **Let geometry carry the detail:** legs, girders, recessed cores, stepped footings, collars. The
  game's face shading does the rest. Don't make a base one plain box.
- **Solar and mirror facets:** a solar panel face is deep blue cells (`#3E6A92`). Each cell has:
  - a lit top/left edge (`#6F9CC2`);
  - a shaded bottom/right edge (`#2F5478`);
  - dark grid lines (`#1C2E44`) on a fixed pitch, so the lines stay straight across neighbouring
    facets;
  - at most one glint per facet.

  Curvature comes from the facets' angles, never from painted gradients. Facet joints are sealed (they
  overlap under the neighbour) and draw no edge faces.
- **Parts tinted in code** (for example glowing receivers) use neutral greys so the tint multiplies
  cleanly.

---

## 6. Workflow checklist for any new texture

1. **Draw from geometry.** Use straight edges and explicit tone maps, not filters.
2. **Palette check.**
   - Greys come only from the ramp above; accents come from their tables.
   - Count the colours: aim for 8–15 on a machine face and 4–8 on an item.
3. **Speckle check.** No isolated pixel unless it is a rivet, glint or pore.
4. **Preview it.**
   - At 6–8× next to the Distillation Array and an existing machine.
   - As an iso render of the model, to check UVs and that edges join.
5. **Before/after.** When a texture is replaced, keep the same size and UV region, and keep the
   same frame count for animations so the `.mcmeta` still applies.
