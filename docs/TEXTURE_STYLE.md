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
   - **Exception: broad shading on big UV-unwrapped faces.** On the large faces of a formed multiblock model (about
     16 px or more each way), a two-tone split reads as a flat fill. Those faces use *broad shading* instead: eight
     flat steel tones (`#5C626A` `#555B63` `#4F555D` `#494E56` `#43484F` `#3E4349` `#393D43` `#34383D`) in wide
     diagonal bands, lightest at the top left and darkest at the bottom right (tone index from
     0.55·x/w + 0.45·y/h). Each band is several px wide, with hard steps and no dithering. The face's bevel,
     rivets, straps and louvres sit on top unchanged. This is not pillow shading, which darkens every edge towards
     a lit middle. First used on the Industrial Water Intake. On a model whose faces are sized by its structure (the
     Steam Turbine Array) the bands are tone polygons laid over each big face, its corners and bevels from a panel round
     them; long round facets step along their length instead (see "Steam Turbine Array model").
   - **Large models no longer use it:** formed multiblock models and other large renderer models follow "Large model
     texturing (IE / Mekanism / Create style)" in section 5, which replaces this exception and the single light from
     the top left for them. The Pumpjack is the first; the models still on broad shading are listed there.
3. **No noise.**
   - Texture comes from *shapes*: straps, rivets, seams, slats and panels.
   - Never use random pixels or speckle.
   - Every isolated pixel must be intentional, for example a rivet, a glint or a pore.
   - Large models (section 5, "Large model texturing") may carry a controlled material grain in their plate
     modules: a few neighbouring steel tones in a deliberate brushed or mottled pattern that repeats with the tile.
     That isn't speckle; random isolated pixels are still out.
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

Block textures never use near-blacks or greys off this ramp (`#0C0D0F`, the item outline, is for items
only).

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
| Heat (hot orange): heat ports, flames, hot thermal conduit cores | `#FFB02E`, `#F26A16`, `#C73A0E`, `#7A1B0A` |
| Input (blue) | `#7FB3F0`, `#4A8FE0`, `#3A76C0`, `#22497E` |
| Status LED / arc (cyan) | `#B8F2EA`, `#5FD4C4`, `#2F6E68` |
| Link (purple, from the Iolite ramp): the Wired Machine Link port, the Link Conduit core, Wired Machine Link accents | `#636CD0`, `#4A3CB4`, `#3C298A`, `#2E1660` (glow highlight `#7C9CEC`, unlit glass `#1E0E3E`) |
| Hazard stripe | `#E0B020` / `#1A1A1A` |
| Furnace brick (firebox and furnace interiors) | `#553F34`, `#3A2C23`, `#2B211B` |

**Tier colours.** Each tier palette runs outline, end face, front, bevel, top, rim highlight.

| Tier | Palette |
|---|---|
| Wrought (gold) | `#3A2A0C #8A661E #AE8430 #C99C44 #E0BA60 #F6DE98` |
| Tempered (copper) | `#3C1C0C #8A4822 #AE6632 #C98244 #E0A062 #F6C898` |
| Hardened (purple) | `#1E0E36 #52307E #7042A4 #8A55C2 #A476DA #CEAAF2` |
| Arcforged (cyan) | `#0A2A38 #1E6A88 #2E96B6 #40B6D8 #6AD2EE #B4F2FF` |
| Creative (pink; creative-only blocks) | `#3A0A2E #882068 #B42E8C #D442AA #EC70C8 #FFB8EC` |

The creative storage blocks are their Arcforged textures with every cyan tier pixel moved to the same step of the
Creative ramp (`tools/creative_storage_gen.py`).

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
- **Upgrade cards and tool modules:** a flat card body, `#474C53` left of x = 10 and `#40454C` right of
  it, with a 1 px bevel, the accent strip and one glyph. No grain in the body.
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
- The core window keeps its level art (levels 0–4): flat bars of 3 tones from the FE or Heat row where it's
  charged, and the same bars in dim tones where it isn't (FE: `#8E231C` / `#5A1712` bars, `#3A100C` gaps;
  Heat: the cold `#7A1B0A` / `#3C1C0C`). The window's left lip stays `#16181B` at every level.
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
- In a 3D model the glass follows the part it's set in: on a round casing, one pane per facet, just inside the
  casing's outer surface (never a flat pane across several facets), with nothing (a shaft, a blade tip, a bolt, a pipe)
  passing through it. Check every part against the windows.

### Distillation column band grammar

The unformed Distillation Array blocks keep this grammar; formed, the column is the refinery tower model (see
"Refinery tower model" below), so the old formed faces (`plate`, the `roof_*` corners, `tray_glass`, the formed
controller) are gone. Every face shares the same row layout, so lines run unbroken all the way round:

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

### Ports: one design, two sizes

- **Layers, outside in:**
  - a steel collar with a 1 px bevel;
  - a 1 px coloured ring:
    - blue = in;
    - orange = out;
    - red = FE;
    - hot orange = heat (the Heat row);
    - purple = the Wired Machine Link (the Link row);
  - a dark bore;
  - a resource-colour chip.
  Everything outside the collar is transparent.
- **Multiblocks: 10×10 (`block/port/<mode>`).**
  - Collar at x3..12, bore 6×6 (x5..10), chip 4×4.
  - Drawn by PortOverlays on the faces set with the Wrench.
  - New structures start with **no ports**, except the Steam Turbine Array, which gets steam, energy and Exhaust ports
    on its model's fitted spots.
  - Nozzles use the same 10 px collar.
- **Single-block machines: 8×8, perfectly centred at x/y 4..11.**
  - This is the 10×10 plate with its chip shrunk to 2×2: rows and columns 0,1,2,3,6,7,8,9 of the 10×10.
  - The collar is the size of a conduit's output flange, and the 4×4 bore is the conduit input nub.
  - It's painted into the face textures at every face that the default side configuration
    (`new SideConfig(top, bottom, left, right, back, front)`) gives a mode.
  - For a north-facing block, LEFT is east.
  - Plain port faces (backs, bottoms, port tops) are clean casing with the four rivets and the plate.
  - Hopper and intake tops keep their art behind the port. Only the collar and ring are painted, and
    the hopper shows through the 4×4 bore.
  - When left and right have different defaults, the machine has separate `_left` / `_right` textures.
    It also gets its own top and bottom textures when those differ from the sides.
  - Faces set to NONE show no port marks.
- **3D machine models:** the 8×8 plate is centred on the face. When the body is inset, the port sits
  on an 8×8 socket that reaches the block edge, so a conduit meets it flush (Electric Pump back).
- **Storage blocks** (tanks, cylinders, crates, vaults, cells) get no port overlays.
- **Wired Machine Link port (`block/port/machine_link`):** the 10×10 plate (the Energy plate's layout) with the Link row:
  ring `#636CD0` lit / `#2E1660` dark, chip `#4A3CB4` round a `#2E1660` centre, and one `#7C9CEC` Iolite glint on the
  chip's lit corner. It is never set with the Wrench: it marks the face a Link Conduit is attached to. The cable carries it
  as the plate at its plugged end (raised 1.5 px off the machine's face, so it covers the port under it), and a Wired
  Machine Link shows it on its own cable face. Ports-tab / Sides-tab icon `face_machine_link`: the Link row as a face
  tile (`#636CD0` lit, `#2E1660` dark, `#4A3CB4` fill) with a `#1E0E3E` padlock (it's locked), its keyhole `#7C9CEC`.

### Conduits

- **UV layout:**
  - straight side (0,0,16,6): row 0 lit edge, rows 1–4 contents, row 5 dark edge;
  - the top and bottom faces use the same strip mirrored across the conduit, so the lit edge is on the north or west
    side. South and west arms use the `_s` models and up arms the `_u` models, which undo their 180° turn;
  - cap (0,6,6,12);
  - arm sides (6,6,14,12).
- **Tier colour:** the end flanges and the cap ring are flat tier tones:
  - dark = end face (tone 2);
  - mid = bevel (tone 4);
  - light = rim highlight (tone 6).
- **Steel:** body `#40454C`, hoops and clamps `#575D65` / `#727982` / `#959DA6`, all from the ramp.
- **Contents by type:**
  - **Energy (solid):** the running core is `#FF8577` / `#E5483C`; the idle core is `#8E231C` in both
    rows.
  - **Item and fluid (translucent):** glass with a `#DCE8EE` sheen row (alpha ~110–120), a `#DCE8EE`
    body (alpha ~40) and an `#A9BCC6` lower row (alpha ~60–70).
  - **Pressurized / gas (translucent):** the same glass, with opaque steel hoops every 4 px. The gas
    is drawn inside by ConduitRenderer across the whole bore, tinted to the gas, and its opacity
    follows how full the conduit is. There's no glow layer.
  - **Thermal (cutout):** a tempered-copper jacket (`#E0A062 #C98244 #AE6632 #8A4822`). Its core is
    `#7A1B0A` / `#3C1C0C` when cold, and the Heat row when hot.
- **Connectors:**
  - The output flange (8×8) is a steel lip round the orange ring with a `#16181B` centre.
  - The input nub (4×4) is blue ring tones with a `#16181B` pip.
- **Filter attachments:** allow is status cyan, deny is FE red, unset is steel.

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
  on each part and read as stripes and noise. (Large models tile *plate modules* instead: tiles drawn as whole plates,
  laid on the block grid. See "Large model texturing" below.)
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
- **Models whose size follows their structure** (the Steam Turbine Array) can't have a fixed unwrap. Their sheet holds
  panels cut into columns and rows: fixed ones (bevels, rivet corners, seam bands) are drawn at 1 texel per px on every
  face, and only bands of one tone stretch, sharing the rest of the face by weight (so a split face keeps its lit / shade
  split at about 10/16). Faces too small for a panel use its plain form, then a flat tone; riveted panels need 9 px.
  Round parts are banded like the 16 px cylinders: each facet gets one band tone (or a banded plate strip) for how it
  faces the light from the top left, stepping round the part, never blended; the game's face shading adds to it. Caps
  are flat concentric rings. A repeating surface (a grating, a strap, a louvre) is a small tile laid at 1:1.

### Large model texturing (IE / Mekanism / Create style)

For formed multiblock models and other large renderer models, after Immersive Engineering's multiblocks
(`multiblock_base`, `diesel_generator`, `crusher`, `excavator`), Mekanism's models (`digital_miner`,
`chemical_crystallizer`, the turbine casing) and Create's blocks (`andesite_casing`, `fluid_tank`,
`mechanical_press_side`, `flywheel`). It replaces rule 2's broad-shading exception and rule 1's single light for these
models; rule 3 allows their material grain. `tools/LargeModelTextureGen.java` paints what the plating samples (the grain
fields, edge strips and rivet heads) and the small parts' tiles into rows 96–233 of each large model's sheet (128×256,
the same layout on all of them); ModelMesh `modules` and `plating` lay them.

1. **No global light.** The texture never paints a light direction across a whole face or part: no broad diagonal
   shading. The game's own face shading lights the model. Every plate shades itself: a 1 px lighter top and left
   edge, a 1 px darker bottom and right edge, on that plate only, and a 1 px darkest seam between neighbouring plates.
   Recessed panels invert it (shadow on the top/left lip, light on the bottom/right).
2. **Never stretch; continuous plates, never a block grid.** Every face is drawn at exactly 16 texels per block; no
   panel, band or tone is ever stretched to fill a face. A large face is painted as **one continuous surface of a few
   big plates sized to the part**: as few as keep each within 4 blocks (64 px), or the whole face when it's smaller.
   Each plate has its own 1 px lit top and left edge and dark bottom and right edge; the darkest seam runs only where
   two of its plates meet: at the ends of a part, at flanges, at the tower's tray seams, at the pumpjack's structural
   joints. **Never a seam on every block line**, and no block-sized tile repeated across a big face. Rivets run in rows
   along the plate edges, a 2×2 head every 4 px, on riveted plates 9 px or more each way. A deck plate has a recessed
   panel inset 3 px with its lips inverted. A curved part's side (a horse head, a counterweight) is one plated surface
   across all its facets; a curved shell (the tower) is plated unrolled, its plates carrying on from facet to facet, as
   tall as each tray section; a domed head, being one pressed piece, is bare grain with no plate edges.
   Block-sized **tiles are only for small parts**, and even they carry no frame or seam on every block line: the
   seamless grating, the girder flange (a row of rivets every 4 px, laid along a face's longer side), the motor's
   ribbed plate, the hazard edge and a burner's fire (8×6, full-bright).
3. **Material grain.** Steel isn't a flat fill. Each plate's field has a low-contrast grain of 3–4 neighbouring tones
   from the machine steel ramp (luminance steps of 2–6) in a brushed pattern: about 60–75% of neighbouring pixels differ
   by a small step. The grain **doesn't repeat across a face**: the sheet holds one large non-repeating grain field per
   steel (116×64 machine, 116×32 generator), and each plate shows its own window of it at 1:1, picked by a seed from
   where the face is, so it's the same every frame and every load. Plate edges come from grained edge strips the same
   way. No harsh speckle, no isolated bright or dark pixel except rivets, glints and holes, and no checkerboards. Keep
   the dark machine steel (field median luminance 66–69; the generator steel sits lighter, at about 85).
4. **Round and curved parts** (rods, legs, the polished rod, bearings, half-round tops, curved faces, curved edges)
   carry a soft stepped ramp across their width: 5–7 close tones in 1–2 px steps, lightest just off centre, darkest at
   the edges, like Create's tanks and shafts and IE's cylinders. The ramp runs across the part, never along it, with
   no dithering. The faceted geometry stays; the ramp is painted per facet strip at 1:1. A facet 3 px or more round
   carries the whole ramp across itself. Narrower facets share one ramp that carries on round the part, repeating so
   it is never wider than 14 px. A band wider round than it is long (a flange, a ring) carries its ramp across its
   length instead, the same all the way round. A tile on a curved edge (the hazard stripe) carries on round the arc.
   The polished rod has its own bright steel ramp.
5. **No flat faces.** No face larger than about 4×4 px is a single flat tone. Faces under that get one or two tones.
   A thin strip in a steel tone longer than that shows a slice of a plate at 1:1; a thin part in a colour of its own (a
   blade, a cooling rib, the FE band) may stay one tone, which has nothing to stretch. Panels with stretching cuts
   (FE_BAND) aren't used on large models: they drop to their flat colour.
6. **Block models on a large model** (the Well Head) can't share its sheet, and a block model's UVs span a whole
   texture, so only 16×16 textures keep 1 texel per px. Each visible face gets its own region painted to its exact size
   (a grain plate with its edges, or the ramp across a round part), packed into 16×16 sheets, and the model's UVs point
   at them: `tools/WellHeadModelGen.java` writes both together.
7. **Glass** on a large model is laid at 1:1 too: the GLASS tile's 1 px frame (lit top and left, dark bottom and
   right) runs round the pane's edge in runs of up to 10 px, its glints sit once in the top-left corner (on a pane 8 px
   or more each way), and the clear middle isn't drawn at all. A window meant to read as a pane over what's behind it
   (the Fission Reactor's) adds one flat light grey tint pane (the rivet tone, about 35% opaque, drawn translucent) a
   hair behind the frame and glints: smoky, still clearly see-through, never a gradient.
8. **Round caps and discs** (pipe ends, flywheel faces, a casing's end rings) are plated as one surface across their
   wedges, never wedge by wedge.

Converted: the **Pumpjack**, the **Oil Derrick**, the **refinery tower** (Distillation Array), the **Steam Turbine
Array**, and the refinery units' models (the **Cracking Unit**, the **Steam Cracker**, the **Diesel Generator** and the
**Flare Stack**'s stack). Their renderers shade
each face as a block face turned its way is, so the models sit in the light like the blocks round them. Models still to
bring into line: the **Gas Turbine Array** and the **Industrial Water Intake**.

### Natural materials: ores, raw items, raw blocks, crystals (VANILLA texture language)

Cody wants these to look like Minecraft's own textures, not like our machine style. The flat, no-noise
rules above are for machines, casings and GUIs. Natural materials follow vanilla's technique instead
(learned from the vanilla textures, never traced or copied).

- **Ore blocks:** Cody's own ore textures, recoloured per ore and used as overlays on the real vanilla
  stone / deepslate.
  - The overlay is every pixel where his texture differs from the stone base: the mineral pixels plus his
    dark stone bevels.
  - Mineral pixels are recoloured by brightness onto the ore's ramp. Bevels are kept on stone and
    darkened for deepslate.
  - Sources: silverish → silver and arcite; cream → nickel and tungsten (wolframite); green → fluorite
    (alternate clusters purple and green) and spodumene (mint); iridescent → bismuth (rainbow ramp); cream → halite
    (pinkish rock salt).
  - Iolite (the End's ore): cream → iolite, on **End Stone**: its grey bevels go to End Stone shadow tones
    (`#9C9D68 #AAAC73 #B8BA7D #C6C888` for source greys 83/93/102/114), as the Nether Sulfur ore's go to netherrack's.
  - Use the same method for any future ore.
- **Raw items:** Cody's own raw item textures, recoloured per ore by brightness onto the ore's 7-tone ramp,
  keeping every pixel and his outline shading.
  - Sources: blue → silver and arcite; light → nickel and bismuth (rainbow ramp); dark → tungsten
    (wolframite); green → fluorite (purple, with the lower-right lobe green) and spodumene (mint).
  - Use the same method for any future raw item.
- **Raw blocks:** Cody's own raw-block textures, recoloured per ore by brightness onto the ore's 7-tone ramp,
  keeping every pixel, lump and crease of his art. Sources: bluegrey → silver, grey → nickel and tungsten, green → spodumene,
  green → fluorite, teal → bismuth (rainbow ramp) and arcite (dimmed cyan). Use the same method for any
  future raw block.
- **Crystals and crystal blocks:** Cody's own crystal item and crystal block textures, recoloured per ore by
  brightness onto the ore's 7-tone ramp. Fluorite is purple with a green crystal / green patches; arcite is
  cyan. Use the same method for any future crystal block.
  - **Iolite Crystal** has its own shape (Cody's pick, after reference art): one long faceted shard leaning to the top
    left out of a chunky, clustered base, with a shorter point to the right and a stub on the left. Flat facets in the
    Iolite tones: the facet facing the top left lit, a pale ridge up each point, the facet facing the bottom right
    shadowed, the darkest tone as the outline. Block of Iolite and Iolite Dust use the usual crystal block and dust.
- **Metal storage blocks:** Cody's own block textures, recoloured per metal, then SMOOTHED.
  - Smoothing: blur the interior brightness (3×3, or horizontal-only for the banded texture), then flatten
    it to 4 tones. The 2 px frame and the corner rivets are kept from the source.
  - Sources: banded → silver, riveted copper-tone → nickel, riveted dark → tungsten and bismuth
    (pink-silver).
- **Dusts (every dust in the mod):** Cody's greyscale dust texture, recoloured by brightness.
  - New dusts use their ore ramp.
  - Existing dusts (iron, copper, gold, carbon, ancient debris, nether quartz) use their current tones,
    so their colours stay the same.
- **Palettes are brighter and more saturated than the machine steel.**
  - Silver `#5E6C7C → #FFFFFF`; nickel `#6E6236 → #FFF9DC`; tungsten metal `#2E3338 → #B4BBC2`.
  - Wolframite (ore/raw) `#16100C → #A09080`.
  - Fluorite purple `#4A2275 → #F0E0FF` with green `#1F6B3A → #DCFFE6`.
  - Bismuth rainbow (blue, magenta, gold, cyan) on the ore/raw; bismuth metal pink-silver
    `#6E5E70 → #FFF4FA`.
  - Arcite `#1B6E8C → #E8FCFF`.
  - Iolite: deep violet through indigo to a pale icy blue, `#2E1660 #4A3CB4 #7C9CEC #E2F2FF` (7 tones
    `#1E0E3E #2E1660 #3C298A #4A3CB4 #636CD0 #7C9CEC #E2F2FF`), so it reads as an End material and apart from
    Arcite's cyan and Fluorite's purple.
- **Ingots:** Cody's reference ingot shape (exact 16×16, 8 light levels, tonal outline) for every ingot,
  including steel.
- **Coal Coke:** his reference lump shape, in its light grey.
- **Treated wood:** Cody's own wood textures (planks, trapdoor, door top/bottom, log side/top, stripped
  log side/top), recoloured by brightness onto the treated ramp `#120C07 → #725033`.
  - Bark sits in the darker part of the ramp and stripped wood one step lighter.
  - Grey hinge and handle pixels map onto the hardware greys `#5A5F66 #6A6F76 #7E848B #9AA0A7`.
  - The door item is redrawn to match the door.
- **Arcforge Furnace bricks:** Cody's own brick texture, its 7 tones recoloured by rank onto the
  fire-brick ramp `#2B211B #33271F #3A2C23 #47352C #553F34 #644B3F #74584A` (the two darkest are the mortar). Port textures are the
  bricks with the port opening laid over them.
- **Steel tools and armour:** Cody's own tool/armour textures and his two worn-armour layers, recoloured by
  brightness onto the steel-ingot ramp `#3C3F45 → #C4C9CF` (outline `#16181B`), topping out below white
  so steel reads darker and bluer than iron. Wooden handles are kept exactly. New tools of the family
  (Hammer, Excavator) are drawn in his style on his handle.

---

## 6. Workflow checklist for any new texture

1. **Draw from geometry.** Use straight edges and explicit tone maps, not filters.
2. **Palette check.**
   - Greys come only from the ramp above; accents come from their tables.
   - Count the colours: aim for 8–15 on a machine face and 4–8 on an item.
3. **Speckle check.** No isolated pixel unless it is a rivet, glint or pore.
4. **Port check.** Every single-machine face with a default side mode shows the 8×8 port; faces
   set to NONE show none.
5. **Preview it.**
   - At 6–8× next to the Distillation Array and an existing machine.
   - As an iso render of the model, to check UVs and that edges join.
6. **Before/after.** When a texture is replaced, keep the same size and UV region, and keep the
   same frame count for animations so the `.mcmeta` still applies.

## Jetpack & arc tools
- Jetpack tanks: column-banded portable steel with the flat tier band (like Gas Cartridges); harness and grips in dark polymer #1C1F24 #2A2E35 #3A3F47 with plastic buckles #F0ECE4 #DCD6CA.
- Arc Drill / Arc Saw: machine-steel body with the tier band, tungsten bit/bar (#1C1F24…#6A717B), a cyan arc glint at the tip and a small cyan charge LED.
- Tool module cards: the upgrade card with its accent recoloured amber (#F5D060 #E0B020 #8A6A10), one glyph each; machine upgrades keep the cyan accent.
- Worn jetpack: 3D pack model (32x32 texture, same tank banding) plus a strap layer on the humanoid equipment layer.

## Settings, security, Foundry Suit, Fermenter, dyed conduits, Gas Turbine
- Card family accents: machine upgrades cyan, tool modules amber, Settings Card violet (#D6B8FF #A47CE8 #5E3E96).
- Security icons: gold padlock (#F5D060 #E0B020 #8A6A10) with steel shackle; people figures cyan (own) / grey (others).
- Foundry Suit: the steel armour recoloured by brightness onto the rock-wool ochre ramp #2A2014 #4A3A22 #6E5630 #8E7040 #AE8C52 #CCAA6A #E2C688 #F2DEAA; gold visor on the worn helmet.
- Ethanol: pale straw, semi-transparent (alpha ~190): #D8C688 → #FCF8E6.
- Dyed conduits: no bands. The dye colours the conduit's own texture. Each tier texture has a `<tier>_<kind>_dyed` copy with the dye region cut out, plus a tier-independent greyscale mask in `block/conduit/dye/`, drawn at tint index 1 by each piece's `_dyed` model.
  - Energy and Thermodynamic: the mask is the core stripe. Idle is flat #90 (thermal #90/#60). Lit is #FF on the top row and #C8 below. A hot thermal conduit brightens from 55% to 100% of the dye between 100 °C and 1,200 °C.
  - Item, Fluid and Pressurized: the mask is the glass pixels. Greys are #FF and #C8, and alpha is the glass's own +0x58, so the glass reads as tinted but stays see-through.
  - Generated by conduit_dye_gen.py (shipped with the drop that added them). Re-run it if a conduit texture changes.
- Gas Turbine Array (Hardened): machine steel with vertical cooling fins and a purple heat-blued band; compressor blades light steel, turbine blades Hardened purple; combustor glow blue-white core fading to orange.

## GUI icons, tabs and buttons
- Tab/button icons: 16x16, one flat fill per shape with a 1 px hue-matched dark outline (never #0C0D0F black), glyph about 12 px centred; torches are copied from the redstone sprites. Padlock #E8B830 / #5A4410 with steel shackle #B4BAC0 / #3A3F47; people #5FD4C4 / #1F4E48 (own) and #A0A0A0 / #3A3A3A (others).
- Tabs are 22x20 (selected 24x20), cut from the original sprites; left-side tabs are the mirror. Left = machine readouts (Energy, Heat, Pressure), right = settings (Redstone, Sides/Ports, Upgrades, Security).
- Collapsed tabs use the panel's own greys: #313131 face, #505050 highlight, #1A1A1A shadow, #000 outline. Hover is one step lighter (#3B3B3B / #5E5E5E / #1E1E1E).
- Open panels fit their content (`SideTab.fitWidth`). The width is the larger of the header (22 + title + 6) and the widest line + 12, so a left tab never leaves a blank strip on its far side. Button rows keep the same margin on both sides.
- Screen text keeps 2 px inside the recess's inner edge. A value that can run long (a fuel, fluid or block name, or a status) is `clipped()` with an ellipsis, and a tooltip gives it in full.
- Buttons: only the widget/button nine-slice family (normal/hover/pressed/disabled); selected = pressed + 1 px ACCENT (#5FD4C4) outline outside. No custom button sprites.

## Mekanism-style block models
- Single-block machines may use UV-unwrapped element models (64x64 sheet, 1 px bevels per face) instead of cubes; emissive screens are separate animated textures with light_emission 15. First: Security Terminal (desk console, monitor tilted 22.5°).
- Gas Turbine Array rotor: the combustor is a 16-sided can (8 crossed bands at ±22.5°/45°, r 14 px) with flat #3A3F47 end discs. A bearing sits at each end of the shaft on the inside: a 16-sided housing (r 6.5) with a r 9 flange that carries 4 bolt heads.
- Fermenter: the UV-unwrapped vat on a 64×64 sheet with no loose protrusions. Its default ports are part of the model: the input socket on top, the output port under the plinth, and the energy socket on the back.
- Gas Turbine Array skin: the steam turbine's banded plates at double height with a thin Hardened-purple line; 3x3 end caps (48x48) drawn by the BER keep the outer 3 px clear for frame beams.
- Any 3D machine with default ports follows "Ports": 8×8 plates at its default faces, on a socket to the block edge when the body is inset.

## Meters, Chargepad, Conduit Facade, oxy-fuel
- Meters (Energy / Heat / Fluid / Gas): the standard casing on every face.
  - Front: a steel bezel at x2..13, y3..10 framing a 10×6 `#16181B` LCD at x3..12, y4..9. The LCD is centred on
    the block (x 8). The renderer centres the value and unit on it by their ink width (the font's width − 1), in
    status cyan at full brightness.
  - Under the screen: a 3 px type strip (FE red, Heat, input blue, gas `#DDF3FD` / `#7FC8F0`), a 4 px flow
    arrow (→, pointing at the output side) and a 1×2 redstone LED at x12 (`#8E231C` off, `#FF8577` / `#E5483C`
    on).
  - The left and right sides carry the 8×8 port of their mode:
    - Energy: FE red on both sides.
    - Heat: Heat on both sides.
    - Fluid: input blue on the left, output orange on the right.
    - Gas: blue in / orange out with a gas-white chip.
  - The back has a 3-slat vent.
- Chargepad: a 2 px pad (x/z 1..15) with an offset hazard checker on its edge.
  - Top: a recessed coil in `#2F6E68`, drawn emissive cyan (`#5FD4C4` / `#B8F2EA`) while charging.
  - A thin housing at the back (x3..13, y0..14, z13..16), so the standing area runs to z13:
    - a 4-segment charge gauge on its front, using the FE row lit and the cell dims unlit;
    - the 8×8 FE port on its back face at the block edge (the housing is the socket).
- Conduit Facade (blank, `block/conduit/facade_blank`): the single-block machine casing face with no centre art, so a
  wall of blank facades reads as riveted steel panels: the split face, 1 px bevels and four rivets. The 16×16×1 panel's
  edges take the matching strip of it. A dressed facade uses the sprites of its block's own model, side by side.
- Oxy-fuel GUI: no new frames. The flame sprite swaps to `flame_oxy` (orange edge, blue-white core `#BFE0FF` /
  `#F4FAFF`), the status reads "Oxy-fuel", and the oxygen level is a row in the Heat tab.
- Meter GUI (no player inventory, like the Security Terminal): a 176×124 panel with the standard frame.
  - The house screen recess (7,18)–(168,69):
    - Rate label/value on row y 23;
    - a 152×4 rate bar on a baked `#0E0E0E` track at (12,34), flat 3-tone in the kind's colour (FE cyan, Heat,
      input blue, gas), with a 1×6 `#E0E0E0` threshold tick;
    - the Threshold row on y 42;
    - the status LED + text on y 56.
  - Below the recess:
    - "Signal when" with two 20×20 house buttons carrying the `meter_above` / `meter_below` icons (selected =
      pressed + ACCENT outline);
    - a threshold field recess (66,100)–(168,117) holding the EditBox, with the unit right-aligned inside it.

## Farming
- Soil (Loam and its farmland) uses Cody's soil texture (his pink block), recoloured by brightness onto the loam
  browns `#2A1D14 #3B2A1D #4C3625 #5E432D #72533A #8A6A4C`, with every pixel kept.
- Farmland tops cut that texture into four 4-row furrows:
  - each band's top row is the ridge, every pixel two tones lighter on the texture's own palette;
  - its bottom row is the furrow (the darkest tone), bridged by a 2 px clod or two.
  - Moist farmland is the same texture on the darker ramp `#160F0A → #5C432E`. Only moisture 7 shows as moist,
    as in vanilla.
- Irrigated Loam Farmland:
  - The top is the moist top with a copper channel down the middle (x5..10). The channel has a lit lip, walls in shade
    and in light, two water rows (`#4A8FE0` / `#3A76C0`, a `#7FB3F0` glint every half block) and clamp bands at y0/y8.
  - The sides are Loam with a 3-row copper liner band and two rivets.
  - The copper is the Tempered row.
- Compost Bin: vanilla composter geometry, built from the treated-wood textures.
  - The sides are the treated planks with a `TREATED[0]` gap row under each 4 px slat, and a bark post at each edge
    (one step darker on the right).
  - The inside is the planks two steps darker, and the rim is stripped-log end grain.
  - The heap while composting is Cody's green block, recoloured by rank onto `#2E3A1A #42501F #5A6628 #727A30 #8A8A3C`.
    Ready compost is his soil texture on dark humus `#140E0A → #5E432D`.
- Fertilizer items:
  - Wood Ash and Basic Slag are the dust texture recoloured by brightness: ash grey `#242322 → #B3AEA7`, slag slate
    blue `#1A1E24 → #A2ABB5`.
  - Compost is Cody's lump texture, recoloured by rank onto humus `#1C130D → #8A6A4C` (his own tonal outline, no black).
  - Mixed Fertilizer is a tied paper sack (`#6E5634 … #CCAE74`) with a `#0C0D0F` outline. It has a green label
    carrying three 2×2 chips in the compost, ash and slag colours.
- GUI: the Firebox and Combustion Plant have a fuel → flame → ash column that runs down the screen recess's height.
  The input frame is at y18..35, the flame at y37, and the output-orange ash frame at y52..69.

## Crops
- Crops are natural materials, so they use the vanilla plant language and Cody's own crop textures.
  - **Rapeseed:** his 8-stage plant, one texture per age, with its white flowers recoloured rapeseed yellow
    (`#EBC52C` / `#FFE66A`). His greens are `#204C1D #2C5E22 #40712B #5B8838 #779C43`. The last stage keeps its browned
    ripe pods. Rapeseeds are his dark seed cluster, and Wild Rapeseed is his small flowering bush.
  - **Flax:** his 5-stage sheet (spread over ages 0-7 as 0,0,1,1,2,2,3,4), with Flax Seeds, Flax Fibre (the green bundle)
    and Linen (the cloth) from the same sheet. That sheet was recovered from a resized screenshot and quantised to 10
    tones, so if the original PNGs turn up, drop them in over `flax_stage*` / `flax_stage*_top`.
  - Sorghum, Hops and the Trellis items are drawn to match, on his greens.
  - **Soybeans** (no source art) are drawn to match on his greens: 4 stages over ages 0-7 (0,0,1,1,2,2,2,3), one block
    tall throughout.
    - Leaves are broad oval leaflets (a 4×3 stamp: `#779C43` top, `#5B8838` body, `#40712B` underside) in trifoliate
      sets: a centre leaflet upright over one out to each side. Stems are `#2C5E22` / `#40712B`, fanning from one root.
    - Stage 0 is two seedlings with round seed leaves; stage 1 two young plants with their first trifoliate leaf and one
      side leaflet each, pointing outward; stage 2 the full bush with filling pods (pale fuzzy green `#BCCC74 #9AB050
      #6E8A30`, 2–3 px, lit at the top) hanging at the leaf joints.
    - Ripe (stage 3) the plant dries the way soybeans do: the leaves have nearly all dropped (a few yellowed leaflets,
      `#C8BE5C #A8A040 #7E8A34`), the stalk is dry brown (`#5A4424` / `#3E2E18`) and loaded with tan pods (`#E4D3A0
      #C2A86E #8E7444`), so a ripe field reads at a glance.
    - Wild Soybeans are the stage 2 bush with two of its pods already ripe.
    - The Soybeans item is a ripe three-bean pod lying corner to corner: three 6×6 round lobes (lit top-left, shaded
      bottom-right) in a tonal `#4A3820` outline (no black), each lower lobe's outline making the seam against the next,
      with a short dry stalk off the top.
- **Tall crops:** a crop stage 16×32 tall is split into `<crop>_stageN` (bottom) and `<crop>_stageN_top`, drawn by
  `block/tall_crop`. That model is vanilla's crop planes twice, the top set a block higher. Wild Flax uses
  `block/tall_cross` the same way.
  - Flax is tall from age 2 and Rapeseed from age 4. They only grow into those stages with air above them.
- **Trellis:**
  - four 2×2 corner posts and 1 px rails at y 5, 10 and 15, textured with the stripped treated log;
  - the hop vine is four outward planes just outside the frame (`hops_vine1-4`), so it wraps the lattice;
  - stage 4 carries pale green cones (`#D6EC9E #A8CC6A #7FA24A`).

## Rustic farming machines
- The Planter, Harvester, Fertilizer Spreader, Copper Sprinkler and Scarecrow are UV-unwrapped element models: one
  64×64 sheet per model (the Scarecrow's two halves share one), each face painted flat and sized to it, identical faces
  sharing one region. Geometry carries the detail, in the feel of Create and Immersive Engineering.
- Materials:
  - treated wood is cut from Cody's own treated textures, never flat tones: plank faces from the Treated Planks (its
    4 px boards and gap rows, turned to run along the face), posts and legs from the Stripped Treated Log, beams from
    that log on its side, end grain from the log's top. Each face keeps a lit first and shaded last pixel along its
    boards, a steel nail at each board end on faces 9 px or wider, and the grain is offset by the face's size and side,
    so identical faces still share one sheet region;
  - iron on the machine steel ramp (straps, angles, chutes, the reel, the spinner), rivets only on faces 9 px or wider;
  - copper on the Tempered row (seed drums, spouts, the whole Sprinkler);
  - linen on the Linen item's ramp `#4A3934 #9D7860 #D2B192 #EDCFB4 #FDE8D0` (Cody's cloth), with stitched seams and a
    sewn patch; the Scarecrow's sack head is one step darker (`#B89574`), with stitched X eyes and mouth;
  - straw `#5C4318 #8A6A26 #B48E36 #D4AE4A #ECD072` in 3 px strands.
- Planter and Harvester share one body: four log legs, a planks hopper with a stripped-log rim, an iron band and iron
  corner angles. The Planter has copper seed drums with iron hubs and an iron seed chute at the front; the Harvester has
  a reel (iron end plates, log axle, four slats), a toothed iron cutter bar and a copper outlet at the back.
- The Planter, Harvester and Fertilizer Spreader sheets and UVs are hand-cut from the treated textures as above.
  `tools/rustic_texture_gen.py` painted the earlier procedural version and would overwrite them: don't re-run it. The
  Planter's seed tray is the Compost Bin heap texture and the Fertilizer Spreader's top is Loam: their models
  point those faces at `compost_bin_compost` and `loam` themselves, not at copies on the sheets.
- The Sickles are Cody's hook sickle, cleaned (no guard nubs at the handle), with the blade on three tones: iron
  `#E6E6E6 #9A9A9A #5E5E5E`, steel `#C4C9CF #7D8289 #3C3F45`; the handle on the house wood `#291E0B #493615 #684E1E
  #896727` and a `#16181B` end cap.
- The Steel Scythe is Cody's scythe (a long snath with a hand grip), every pixel kept: its wood snapped to the house
  wood and its greys mapped by brightness onto the steel tool ramp (`#16181B … #C4C9CF`, `#DDE1E5` for the brightest
  glint).
- Wild crops use the grown crop's own model and textures (Wild Hops: the bearing vine as a cross); they have no art of
  their own.

## Farm processing
- The Mill, Oil Press, Seed Extractor and Grain Dryer are single-block machine casings (section 3) with the 8x8 ports
  at their default faces: input on top, output underneath, and energy (the Grain Dryer: heat) at the back. The sides are
  the casing with a 3-slat vent. The front is a recessed window (x3..12) showing the machine at work, with a 4-frame
  `_on` animation:
  - Mill: a buhrstone runner edge-on over its bed stone on a steel spindle; the dressing grooves move and flour falls.
  - Oil Press: a steel ram and platen over a seed bed in a trough, an amber oil pan; the platen presses down and drips.
  - Seed Extractor: a threshing drum with rasp bars over a sieve; the bars turn and seeds fall.
  - Grain Dryer: three slatted racks of cones and grain over a heating element, `#3A2C23` off and heat orange on.
- Buhrstone (millstones) is a warm grit stone, not machine steel: `#4A4436 #6A614E #8A7F66 #A89C80 #C2B79B`.
- Millstone: a rustic UV-unwrapped model (see "Rustic farming machines") on one 64x64 sheet. A treated-wood table on
  log legs with an iron band and a spout at the front, a bed stone one step darker, and the runner stone (its top cut
  with four dressing furrows) with an iron rynd and a log handle peg. The runner is its own model, stepped a quarter
  turn per TURN by the blockstate.
- Items: Flour and Seed Meal are the dust texture recoloured (flour `#6E6454 -> #FBF8EE`, meal `#3E2E1A -> #D8BE88`);
  Dried Hops, Dried Sorghum and Dried Grain are Hop Cones, Sorghum Stalks and the Rapeseeds cluster recoloured to straw
  and wheat gold; Press Cake is drawn: a round cake with weave marks and a `#0C0D0F` outline.
- Seed Oil: Ethanol's fluid frames recoloured onto amber `#7A5010 -> #FFE08A`, a little more opaque. Carbon Dioxide
  uses the steam texture tinted `#B4BCC4`.
- The Gas Output port is the output ring round a gas-white chip; its Sides-tab face is the output face's orange bevel
  round `#DCE6F0`.

## Farm chemistry
- The Air Separator and Haber Reactor are single-block machine casings (section 3) with the 8x8 ports at their default
  faces and a recessed front window (x3..12) with a 4-frame `_on` animation. Left and right differ, so each has `_left`
  and `_right` textures (for a north-facing block LEFT is east).
  - Air Separator: an intake grille on top (four slats in a recess), a plain bottom, FE at the back, the Oxygen port on
    the left and the Gas Output port on the right. The window is a cold box: two slim steel columns joined by a pipe,
    a frost row on their caps, liquid Oxygen (`#9FD4F2`) and liquid Nitrogen (`#C4C0EC`) pooled at their feet; running,
    a bubble rises through each pool.
  - Haber Reactor: gas input ports (the input ring round a gas-white chip) on top and left, heat underneath, FE at the
    back, Gas Output on the right. The window is a sight glass onto the catalyst bed: 2x2 pellets in furnace brick
    (`#553F34 #3A2C23 #2B211B`) when cold, on the Heat row when running, with the lit pellets shifting frame to frame.
- Biogas Digester (connected, `ConnectedModel` "digester"): the side plate is the wall plate (the split face, the
  riveted strap on rows 13-15) with a Tempered-copper hoop band on rows 5-7 (`#E0A062 #C98244 #8A4822`); the lid is the
  plain split face with a seam round each block and four rivets. Edges use the Distillation Array's `ctm/column_beam`.
  The loose casing is the plate with that beam on all four sides. The controller is the plate without the band, with a
  recessed sight glass (x3..12, y2..9) onto the slurry (`#8A8A3C #727A30 #5A6628 #42501F`, gas space dark above) and a
  small cyan readout below it; running, bubbles rise through the slurry and the readout fills.
- Items: NPK Fertilizer is the Mixed Fertilizer sack as a white poly sack (`#F0ECE4 #DCD6CA #B8B2A6 #8E887E`, the
  plastic tones) with a blue tie and label (the Input row) and N/P/K chips (ammonia `#C8DC78`, slag, ash). Digestate is
  Cody's red lump, sampled off its screenshot's 9 px grid and quantised into 7 brightness levels, recoloured by level
  onto a wet olive-brown `#161408 #24200E #363018 #4A4424 #5E5830 #746E3E #8C8650` (his own tonal outline, no black).
- Fluids: Nutrient Solution (`#1E4A18 -> #B2E284`) and Biodiesel (a pale lemon-gold `#8C7E1E -> #F6F2B0`, paler than
  Seed Oil) are Ethanol's frames recoloured by brightness. The gases are the steam texture tinted: Nitrogen `#C4C0EC`,
  Ammonia `#E2F0A0`, Biogas `#A8B478`.

## Automated farms
- Glass Cloche, Grow Chamber and Hydroponic Cell are UV-unwrapped element models (one 64×64 sheet each, cutout). What
  grows inside is not in the model: `ClocheRenderer` draws the soil's own block, squashed into the bed, and the plant's
  own block model (its crop stage, or grown in size for saplings, flowers and cane), shrunk to fit under the glass.
- Their glass follows "Glass": each pane is a face painted fully transparent with two opaque 1 px diagonal glints
  (`#DCE8EE`, `#A9BCC6`) near its top left, sized to the face.
- **Glass Cloche** (rustic, see "Rustic farming machines"): a treated-planks planter (floor and 1 px board walls to
  y 3.5) with a stripped-log post at each corner, a glass bell (x/z 2.5..13.5, to y 13.5) with thin iron corner bars (standing on the planter floor, y 1.5), an
  iron lid and a log knob. Its faces are fixed and it has no port plates, like the other rustic machines; its underside
  has an iron drain grate (a recess with three slats) where the harvest drops out. Bed: x/z 2..14, y 1.5..3.
- **Grow Chamber / Hydroponic Cell:** machine steel. A 2 px plinth, a solid back wall, a 3 px cap and two front corner
  posts frame clear glass on the front and both sides. The default ports are painted on whole 16×16 casing faces split
  across the parts that make up the face: input on top, output underneath, FE on the back (left, right and front are
  NONE, so the glass stays clear). The cap's three open sides carry the tier band on their middle row: Wrought gold on
  the Grow Chamber, Tempered copper on the Hydroponic Cell. Inside, a 1 px steel tray rim (x/z 1.5..14.5, y 2..4).
  - Grow lamp: a 10×10 housing under the cap with its own texture (`lamp` / `lamp_on`): three tubes along x on a steel
    reflector, off `#5E4868 / #4E3A56`, on `#F7B8FF / #E27AF0` (with `#B04CC8`), `light_emission` 15 in the `_on` model.
  - Grow Chamber bed: the soil at x/z 2.5..13.5, y 2..3.5. Hydroponic Cell: a flat Nutrient Solution surface at y 3
    (`#44802E` with a `#2F6424` lit row and `#6FA850` sheen streaks) under a plastic raft (x/z 4..12, the NPK sack's
    plastic tones) with a dark 4×4 net pot in the middle, where the plant stands.
- GUIs (176×166): cut from the existing backgrounds. The tank on the left (x 8), the seed slot (29,19), soil (29,39) and
  fertilizer (49,39) in input blue (the Hydroponic Cell's fertilizer slot takes the soil's place), the arrow at (72,30),
  a 2×2 of output-orange slots from (99,20), and on the powered two the FE gauge at x 156 (the Hydroponic Cell's Carbon
  Dioxide tank at x 138). The status LED and text sit under the slots (y 60). Ghosts are the Infuser's style: white at
  alpha 55 with a 105 rim, a seed, a soil block and a tied sack.

## Greenhouse Array
- The walls and roof are ordinary Pressure Glass (its connected, see-through window look); the crops inside are drawn by
  `PlantingBedRenderer` from each bed (the soil's own block squashed into the bed's recess, the crop's own block model
  over it), never painted into a texture.
- Greenhouse Frame (connected, `ConnectedModel` "greenhouse"): the wall plate (the split face, a panel joint at rows
  7-8 in `#2B2F34` / `#575D65`, two top rivets, the riveted strap on rows 13-15) with the digester's plain lid on top.
  Formed, frames and the controller join into one surface with the Distillation `ctm/column_beam` along the outer edges;
  the glass counts the frames as part of the structure, so it draws no beam against them. Loose, the beam runs round
  every edge. Any frame on the outside can take a port plate.
- Greenhouse Controller: the plate with a recessed climate panel (x3..12, y2..9, `#16181B` floor): a leaf on the left
  (`#2C5E22 … #779C43`), a heat-row thermometer on the right, and a strip of three system LEDs below (water blue,
  nutrient green, lamp cyan), dim when idle. `controller_on` is 4 frames: the thermometer rises and the readout blinks.
- Planting Bed: a UV-unwrapped steel planter (64×64 sheet), a 12 px body and a 4 px rim 2 px thick. Its outer faces are
  whole-block faces split between body and rim (lit rim rows, the split face, the strap at the bottom; the north and
  south rims run the full width, so their ends are the corners of the east and west faces); inside is
  `#2B2F34`. The recess (x/z 2..14, y 12..15) is where the renderer puts the soil.
- Grow Lamp: a UV-unwrapped steel fixture hanging from the roof (a 6×6 mount, a 12×12 housing, every face drawn,
  undersides too, since it's seen from below) with the Grow Chamber's grow tubes underneath (`grow_lamp_tubes` / `_on`, `light_emission` 15 lit).
- GUI (176×206): the FE gauge and the water, Nutrient Solution and Carbon Dioxide tanks at x 8/24/40/56, the fertilizer
  slot at (75,19) in input blue, beds / speed / temperature text under it (x 76), a 3×3 of output-orange slots from
  (115,19), and the house screen recess (7,76)-(168,109) with the status line, a 1 px `#2A2A2A` divider at y 87, and two
  rows of system LEDs with labels in three columns (x 10, 60, 118).

## Salt and chlor-alkali
- Salt is the dust texture recoloured by brightness onto a cool white (`#6E747C #A4AAB2 #CDD2D8 #E9EDF0 #FFFFFF`),
  cooler than Flour. The Salt Block is the raw-block method: Cody's grey raw block (as used for Raw Nickel) mapped tone for
  tone onto `#7A828C #A8AFB8 #C8CED5 #DEE3E8 #EFF2F5 #FFFFFF`.
- Halite follows the ore method: Cody's cream ore (as for nickel) and light raw item, and his grey raw block (as for
  Raw Nickel), all mapped onto a pinkish rock-salt ramp `#7E5E62 #C29EA0 #E8CFCD #FFF7F4`. Halite
  Ore and Deepslate Halite Ore are overlays on vanilla stone and deepslate; Rock Salt is the raw item; Block of Rock Salt
  the raw block.
- Thermal Evaporator Array (`block/thermal_evaporator/`), on the machine steel ramp:
  - `plate`: the formed wall plate, the split face with one riveted strap on rows 13–15, so the tower reads as nine
    banded rings. Its outer edges get the shared `ctm/column_beam` (outer rims only, like the Distillation Array).
  - `top`: the riveted cap (the same as the Biogas Digester's). `inner`: the tower's interior skin, one step darker
    (`#3E4249` / `#383C42`) with a darker strap; the renderer lines the inside of the walls with it, 1/16 in.
  - `casing` (loose): the digester's beam frame on all four sides, with a riveted strap across rows 5–7 instead of the
    copper band. `casing_top` is the digester's.
  - `controller`: a sight-glass recess (x 3..12, y 2..9) holding a sea-blue level (`#7FC4D6 #3E8FA8 #2A6C84 #1F5468`)
    beside a thin red thermometer (`#F06A3C #B83A22`), a lamp recess under it, and the strap. `controller_on` is 4
    frames (frametime 5): the surface ripples, a bubble drifts, the thermometer reads high and the lamp glows amber.
    `controller_unformed` is the front inside the loose casing's beam frame.
  - Windows are done as on the steam and gas turbine arrays: the panes are plain Pressure Glass, and each casing face
    shows an 8×8 window quadrant (`ctm/pressure_glass_base`) wherever glass touches that corner of the face, so every
    window reaches half a block into the corners, the cap and the base, with a 1 px `ctm/window_lip` where a window
    quadrant meets plate. On the controller's side the base stays solid, so the controller's display is never cut. The
    formed models are translucent (the glass base is tinted), and the tower is a thin skin: no faces toward the glass or
    into the core.
  - The renderer draws the inside: the `inner` lining just inside the walls with `ctm/shell_jamb` reveals round each
    opening (cut away behind every window tile, as TiledBoxes.lining does for the steam arrays), the liquid from the
    fluid's own sprite and tint across the whole interior, and the salt bed from the Salt Block texture. Nothing is
    painted into glass.
- Ports `block/port/brine`, `salt` and `water`: the 10×10 output plate (orange ring), chip recoloured to pale teal
  (`#9FD9CF` / `#3F8C86`), salt white (`#F2F0EA` / `#B8B2A6`) and water blue (`#6E9EF0` / `#2C4FA6`).
- Seawater has no texture of its own: it uses vanilla water's `block/water_still`, `water_flow` and `water_overlay`,
  tinted a deep sea green-blue `#2A8C9E` (`ArcforgeClient.SEAWATER_TINT`; vanilla's default water is `#3F76E4`), so it
  moves and reads like water but is clearly greener. Its fog is the same colour.
- Fluids: Brine (`#5E7A8A -> #EEF6F8`), Lye (`#7C8078 -> #F6F8F2`) and Hydrochloric Acid (a pale yellow `#9C9450 ->
  #FAF8DA`) are Ethanol's frames recoloured by brightness, a little more opaque. Chlorine is the steam texture tinted
  `#BCD24A`, greener and deeper than Ammonia.
- The Electrolyzer's bottom is now an Output face by default, so it carries the 8×8 output port (cut from the Chemical
  Reactor's bottom).
- GUIs: the Chemical Reactor's is re-cut for three input tanks (frames at x 24, 39 and 54), the item slots at x 70, the
  arrow at 90 and the outputs at 112 and 132; the Electrolyzer's gains a liquid tank at x 122 (a copy of the gas tank
  frame) with the arrow moved to 98.

## Rubber and bio-plastics
- **Rubber Dandelion** (no source art) is drawn to match on Cody's greens (`#204C1D #2C5E22 #40712B #5B8838 #779C43`), one
  block tall, 4 stages over ages 0-7 (0,0,1,1,2,2,2,3):
  - a rosette of dandelion leaves fanning out low from the crown on both sides: each blade a lit top edge, a body and a
    shaded underside, its top edge cut into backward-pointing lobes every 3 px (the runcinate teeth), tapering to a tip;
  - stage 0 is a two-leaf seedling, stage 1 a small rosette, stage 2 the full rosette with two yellow heads on stalks
    (`#FFE66A #EBC52C #C8961E`: rays out, darker heart), stage 3 (ripe) the same with two white seed clocks
    (`#FFFFFF #E4E8E0 #B8BEB4`, 5×5 with the corners off, lit top-left), so a ripe field reads at a glance;
  - Wild Rubber Dandelion is the full rosette with one flower and one seed clock.
- Items (vanilla outline `#0C0D0F` unless a natural material keeps its own tonal one):
  - Rubber Dandelion Seeds: three dandelion achenes, each a fan of white pappus rays over a thin beak and a small brown
    seed, in a tonal `#3A2A1D` outline.
  - Rubber Dandelion Roots: two fat forked taproots (`#3A2414 → #C09A64`, lit on the left, root hairs), their cut crowns
    cream with a bead of latex, in their own darkest tone as outline.
  - Pine Resin: Cody's lump (as for Compost), recoloured by rank onto amber `#4A2406 → #FFF0B8`, with one glint.
  - Raw Rubber: a crepe sheet (`#6E4C1E #9A7034 #C09650 #DCB670 #F0D494`) with diagonal crepe ribs and a corner turned up.
  - Rubber: a cured slab on the ingot rule (flat top `#4A4E54` with a `#6A7078` edge and one glint, front `#34373C`, end
    `#26282C`). Rubber Gasket: a flat O-ring, lit top-left and a step darker bottom-right.
  - PVC Sheet: the Plastic Sheet recoloured to PVC grey (`#CED8E0 #A8B4BE #8C99A5 #6E7A86`).
  - Block of Rubber: four pressed 8×8 tiles, each with a 1 px bevel on the rubber tones and one moulded dot.
- Fluids: Latex is Ethanol's frames recoloured by brightness onto milky cream `#A89E88 → #FFFFFF`. Ethylene is the steam
  texture tinted a faint mint `#D2EADA`.
- **Resin Tap** (rustic, see "Rustic farming machines"): a UV-unwrapped element model on one 64×64 sheet. A treated-planks
  back board against the log and a shelf on two iron brackets, a slatted planks cup with an iron hoop and a dark interior
  floor, and a copper spout (the Tempered row) driven into the log with its nozzle turned down over the cup. What's in the
  cup is one face with its own 16×16 texture (`#c`): `resin_tap_empty` (clear, so the floor shows), `resin_tap_latex`
  (`#ECE8DC` with lit ripples and a glint) or `resin_tap_resin` (`#D08C22` the same way).
- **Vulcanizer** (single-block casing, see "Farm processing"): input on top, output underneath, heat at the back, vented
  sides. The front window is a heated press: an upper platen on its ram and a lower platen, each with a heating element
  (furnace brick `#3A2C23` / `#2B211B` off, the Heat row on), a Raw Rubber sheet with a pinch of sulfur on the lower
  platen. `_on` is 4 frames (frametime 3): the platen comes down, the elements glow and the sheet cures dark.
- GUIs (cut from existing backgrounds): the Chemical Reactor's output and by-product slots stack at x 112 (y 24 / 44),
  with the output tank frame copied to x 136 beside the by-product tank at x 154; the Infuser's wood slot moves to y 24
  with the additive slot copied under it at y 44; the Grain Dryer gains the Infuser's tank frame at x 24; the Vulcanizer
  is the Grain Dryer's background with its input slot copied to x 23.

## Reservoirs (Fluid, Gas, Item), Liquid Experience, Quantum Tunnel, Chunk Loader
- **Fluid Reservoir** (`block/fluid_reservoir/`, the Reservoir until it was renamed; unchanged): clear glass (alpha 0) with small glints like vanilla glass (a 3 px `#DCE8EE` streak
  and a 2 px `#A9BCC6` one beside it near the top left, a 2 px `#A9BCC6` one near the bottom right), inside a 2 px
  machine-steel frame round the face's edges, kept light so the corners don't go black: `#959DA6` then `#727982` on the top
  and left, `#575D65` then `#474C53` on the bottom and right, `#727982` where lit meets shaded. Connected like the other
  CTM blocks, by quadrant (ConnectedModel "reservoir", ReservoirModel): each 8×8 quadrant of a face comes from one of five
  textures by which of its two edges carry the frame: `frame` (both: an outer corner), `frame_h` (top or bottom only),
  `frame_v` (left or right only), `glass` (neither) or `frame_corners` (neither, but the frame turns there: the 2×2 corner
  of `frame`, closing an inner corner). An edge where two Fluid Reservoirs join drops its frame; a concave edge keeps it. Faces
  between Reservoirs aren't drawn. The liquid is ReservoirRenderer's: one body at the shared level, run to the block edge
  toward a neighbour with the same liquid, full-bright for glowing fluids. Nothing is painted into the glass.
- **Gas Reservoir** (`block/gas_reservoir/`): a pressure vessel in the Steam Boiler Array's steel, on the same five
  quadrant pieces and ConnectedModel "reservoir" (it joins only Gas Reservoirs). `frame` is a 6×6 heavy corner piece at each
  corner of the face (the boiler casing shrunk: `#727982` / `#575D65` bevel top-left, `#2B2F34` / `#16181B` bottom-right,
  `#383C42` where they meet and as its face, one `#959DA6` rivet at (2,2) with its `#2B2F34` shadow at (3,3)) joined by
  3 px edge beams: top and left beams `#727982`, `#575D65`, `#2B2F34` from the outside in; bottom and right beams
  `#16181B`, `#40454C`, `#575D65` from the outside in (lit on the edge facing the glass). `frame_h` / `frame_v` are the
  beams alone, `frame_corners` the 3×3 elbows where two beams meet at an inner corner. Only a quadrant whose two edges
  are both outer edges of the vessel gets the corner piece, so the pieces land on the vessel's outer corners and merged
  tanks show beams only round the outside. Glass is alpha 0 with the two glints moved off the corners and beams (a 3 px
  `#DCE8EE` streak (8,4)-(6,6) with a 2 px `#A9BCC6` one beside it, and a 2 px `#A9BCC6` one at (12,7)-(11,8)), so no
  piece clips a glint. The gas is ReservoirRenderer's: `block/gas_reservoir/fill` (plain white, tinted) filling each block
  to 0.01 inside its outer faces and to the edge toward the same gas, alpha 0.12 empty to 0.72 full, full-bright for
  glowing gases. Nothing is painted into the glass.
- **Item Reservoir** (`block/item_reservoir/`): fully solid, no glass. ConnectedModel "item_reservoir" (ReservoirModel's
  quadrant scheme on `panel` / `rim` / `rim_h` / `rim_v` / `rim_corners`), joined only between FORMED blocks, so a formed
  store's rims run round its outer edges and a block that isn't part of a box keeps all four. `panel` is the split face
  (`#474C53` x < 10, `#40454C` x ≥ 10) with a `#959DA6` rivet near each corner ((2,2), (13,2), (2,13), (13,13), each with
  its `#2B2F34` shadow 1 px diagonally inward): no seams, so a merged face reads as plates riveted at their corners. Rims
  follow the CTM rule (`#727982` / `#575D65` top and left, `#16181B` / `#2B2F34` bottom and right, `#383C42` where they
  meet). The ends along the store's length (AXIS; vertical for an unformed block) carry the riveted strap (3 px: `#575D65`;
  `#959DA6` rivets every 4 px with `#2B2F34` beside them on `#383C42`; `#16181B`) just inside the rim, as overlays
  0.02 out from the face: `band_top` (rows 2–4), `band_bottom` (rows 11–13), `band_left` (cols 2–4), `band_right` (cols
  11–13), one per edge so the strap stays lit on its top/left. Every face along the length gets a band at each end, so the
  bands wrap the store like an Item Vault's; the end faces are plain panels. The item model is a lone block (rims all
  round, bands top and bottom).
- **Item Reservoir GUI** (`gui/container/item_reservoir.png`): the scrolling Crate's panel (194×222, scroll track at
  x 175) with a search field recessed into the title row (x 96, y 4, 91×12: `#0E0E0E` top/left lip, `#1F1F1F` floor,
  `#5C5C5C` bottom/right lip, like a slot) and a 7×7 magnifier in `#8B8B8B` / `#5C5C5C` at its left; the text box starts
  at x 108.
- **Liquid Experience**: Ethanol's frames recoloured by brightness onto a pale XP green
  `#5E9A2C #7DBA3C #9CD24E #BDE86C #DCF89A #F4FFD2`, more opaque than Ethanol; it glows (light 10), fog `#B6F07A`. The
  Vacuum Collector's GUI gains the steam turbines' slim tank frame at x 160 for it (fallback fill recoloured the same).
- **XP Drain**: a UV-unwrapped grate 2 px tall: a 2 px steel rail round the edge and five 1 px bars over a spine, the
  bars' tops lit (`#727982` / `#575D65`), their sides `#2B2F34`, so it reads dark between the bars.
- **XP Shower**: a UV-unwrapped ceiling fixture: a 10×10 mounting plate whose top (against the ceiling, where conduits
  meet it) carries the 8×8 input port, a collar, a copper riser (the Tempered row), a collar, and a 10×10 shower head with
  a 1 px nozzle grid in `#16181B` on its underside.
- **Quantum Tunnel** (`block/quantum_tunnel`, one 64×64 sheet): a thin dark frame cube with colour only on its corners.
  The 8 corners are 3 px caps in the Arcforged tier palette, since it's made from Arcforged Alloy (`#2E96B6` face, a step
  darker `#1E6A88` right of 62%, `#6AD2EE` lit top/left, `#1E6A88` bottom/right with a `#0A2A38` outer corner) with a
  single `#B4F2FF` stud in the middle of each face. The 12 edge beams between them are 2 px dark steel (a `#40454C` lit
  row over a `#2B2F34` shaded row). On every face an 8×8 port sits on a hollow
  steel socket 2 px deep to the block edge, held by four 2×2 steel braces. A port's plate is the 8×8 plate (rows/columns
  0,1,2,3,6,7,8,9 of the 10×10) with its coloured ring in steel (`#575D65` lit side, `#2B2F34` shaded side) and its 4×4
  bore open through to the core, so an unconfigured face is a blank collar. QuantumTunnelRenderer lays the ring of the
  face's mode over it (`block/quantum_tunnel/ring_input`, blue; `ring_output`, orange; `ring_mixed`, the input ring's top
  and left halves with the output ring's bottom and right, for a face doing both) and draws the core: a 4 px cube
  (`block/quantum_tunnel/core`, a 4×4 face lit top-left in `#B4F2FF #B8F2EA #6AD2EE #5FD4C4 #40B6D8 #2E96B6 #1E6A88`),
  full-bright, turning slowly on two axes, greyed while the tunnel isn't on a frequency. The item model holds the core
  still at 45°.
- **Chunk Loader** (`block/chunk_loader`, `chunk_loader_on`): a slim, tall pedestal like a crystal ball stand: a 12×12
  footing 1 px tall, an 8×8 body 6 px tall, a 10×10 capital and a 3×3 stem, then a copper cradle (the Tempered row): a
  ring the globe sits in and four claws rising round it on the sides. The body's front is a recessed status screen (x 1..6,
  rows 1..4: a `#16181B` top/left lip and a `#727982` right/bottom lip round a 4×2 readout, dark teal `#2F6E68` off, a
  checker of status-cyan cells with one `#B8F2EA` on). The globe is ChunkLoaderRenderer's: a voxel ball 6 cells across,
  centred 12.5 px up, whose every outer face takes one texel of `block/chunk_loader/globe` (a 32×16 longitude × latitude
  map in flat tones: oceans `#3E6EC8`, land `#5A9E3C` and `#C8AE72`, poles `#F2F4F6`), its axis tilted 22.5°, turning only
  while the loader is active. The item model carries the globe still.
- **GUIs**: the Quantum Tunnel (176×230) and Chunk Loader (176×122) screens are the Security Terminal's panel stretched,
  with house recesses: the tunnel's name field, frequency list, buffer row (five `#0E0E0E` 28×4 bar tracks) and face grid
  (the side-config `face_none` / `face_input` / `face_output` tiles at 12×12); the loader's 5×5 chunk map and FE row.

## Firebox Array and the bigger steam arrays
- **Firebox Array** (`block/firebox_array/`), connected like the Thermal Evaporator Array (ConnectedModel
  "firebox_array": a thin skin with window quadrants and the Distillation `ctm/column_beam` on the outer edges only).
  - `plate` (walls): the wall plate (split face, two top rivets, the riveted strap on rows 13–15) with a firebrick band set
    into rows 5–6: a `#16181B` lip on row 4, the furnace brick tones (`#553F34` face row, `#3A2C23` shade row) with a
    `#2B211B` mortar joint every 4 px, offset row to row, and a `#727982` lit lip on row 7. Across a formed wall the bands
    join into one brick course per block.
  - `top`: the plain lid, split face with four rivets. `casing` / `casing_top`: the same with the single-block bevel, for
    loose casings.
  - `controller`: the plate with a recessed fire door (x3..12, y2..11): steel, lit on its left and top, two `#383C42`
    hinge pins, a `#959DA6` handle and a 4×2 sight slot in `#16181B`. `controller_on` is 4 frames (frametime 4) with the
    slot glowing in the Heat row (`#FFB02E #F26A16 #C73A0E`), flickering.
  - Each wall casing leaves out its face toward the inside (the `inward` state); FireboxArrayRenderer draws the inside:
    `lining` (16×16 firebrick, 4 px courses, 7×3 bricks: `#2B211B` mortar, `#553F34` face, `#3A2C23` bottom row), a coal
    bed (`coals`: steel-dark lumps on `#16181B`; `coals_lit`: Heat-row lumps on `#7A1B0A`, each a 3×2 block with a lit top
    row, no loose pixels) and, while it burns, a flame over every floor block (`flame`, 16×16, 8 frames, frametime 2: three
    flat tongues, `#C73A0E` edges, `#F26A16` body, `#FFB02E` core, on a `#7A1B0A` base row), full-bright, as two crossed
    planes.
  - GUI: the Fuel Burner's frame (tank at x 9, the screen recess, the heat buffer at x 157) with the bucket column
    replaced by one input-blue fuel slot at (31,19) and the Firebox's flame under it at (32,44).
- **Superheater and Condenser boxes** (bigger than 3×3×3): one connected skin (ConnectedModel "cube_box": the digester's
  solid skin, joined only with the same block's formed box casings, `ctm/column_beam` on the outer edges).
  - `superheater_array/box_side`: the wall plate (two rivets, strap) with a vent recess (x3..12, y3..11) whose floor is the
    dim coil `#7A1B0A` behind two `#575D65` slats with `#16181B` shadows.
  - `condenser_array/box_side`: the wall plate with a recess holding three Tempered-copper tubes (`#E0A062` lit column,
    `#AE6632` shade column) over the `#2B2F34` floor.
  - `box_top` (both): the lid with four rivets and a panel seam across rows 7–8 (`#2B2F34` / `#575D65`).
  - The 3×3×3 keeps its 48 px model.
- **Bigger Steam Boiler Arrays** use the existing shell textures; nothing new is painted. A wall casing leaves
  out its face toward the hollow inside however big the shell is, so the liner and reveal (`ctm/shell_liner`,
  `ctm/shell_jamb`) still line the inside. The Steam Turbine Array is a model of its own now (see "Steam Turbine Array
  model").

## Steam Turbine Array model
- Formed, the array's casings and Pressure Glass draw nothing (`steam_turbine_array/hidden`, with the port plates);
  SteamTurbineArrayRenderer draws one turbine-generator set from TurbineLayout, built by TurbineMesh on one sheet,
  `block/steam_turbine_array/turbine` (128×256, positions in ModelMesh.Paint; rows 0–95 by its art script, rows 96–233
  the large-model plating by `tools/LargeModelTextureGen.java`). It's a large model: see "Large model texturing".
  - Row 0–3: 4×4 flat tones (the machine steel ramp, the generator steel `#3E444B #4E545C #646A72 #7E848C`, FE red, the
    blade steel `#B4BAC0` with its back `#7E848C`, as the Gas Turbine Array's compressor blades). Row 4: tiles: the
    grating (`#575D65` bar, `#40454C`, two `#16181B` gap rows), the riveted strap (4×3: `#575D65` / `#474C53` with a
    `#959DA6` rivet / `#2B2F34`) and a louvre (`#7E848C` lip, `#646A72` slat, `#3E444B` shadow, `#16181B` gap).
  - 16×16 panels in the single-block casing grammar (2 px bevel, split face at x 10, rivets with inward shadows):
    PLATE, PLAIN, GEN_PLATE / GEN_PLAIN (the generator steel lit half, `#474C53` shade half), FOOTING (`#383C42`),
    DECK (a deck plate with a recessed panel inset 4: `#2B2F34` top/left, `#575D65` lip bottom/right, `#40454C` floor),
    FE_BAND (`#E5483C`, `#FF8577` / `#8E231C` edges), GLASS (12×12: clear, a 1 px frame, the standard glints: two 1 px
    diagonal streaks in the top-left corner, `#DCE8EE` (4 px) and `#A9BCC6` (3 px) apart from each other; cut 7 / 1 / 4
    so the glints keep their size on any pane) and GLASS_SMALL (4×4: the frame alone, for windows under 11 px).
  - CASING_0…5 (8×12 each): the casings' shell plates in the band tones `#2B2F34 #383C42 #40454C #474C53 #575D65 #727982`,
    a seam band at each end (`#16181B` seam, a lip one band lighter / darker, a rivet on the facet's centre column with
    its shadow), so each shell section reads as riveted plates meeting at seams; the window frames sit in those seams.
  - Rows 64–87 hold the old broad-shading tones and panels; nothing draws them any more.
- **Plated faces** (PLATE, PLAIN, DECK, FOOTING, the band tones and the generator's: the plinth deck and footings, the
  pedestals, the steam chest, the generator and exciter housings) are continuous plates, as few as keep each within
  4 blocks, riveted where the paint is riveted; the deck is deck plates with recessed panels, with no seam across it
  every block. The generator steel plates in its own lighter grain.
- **The casings' shells** are riveted plates wrapped round them unrolled, as the refinery tower's shell is, their seams
  at the casing's ends, its tapers and its windows; the tapering ends are bare grain. **Round parts** (pipes, hoops,
  gland bosses, valve bodies, the shaft, couplings, hub drums and rotor discs) carry the ramp across them.
- **The casings**: 16-sided shells, each split by a bolted flange, with hoops where the ends taper, end rings with bolt
  circles and gland bosses; long sets get longer casings and up to three double-flow low-pressure casings, a pair of
  bearing pedestals and a coupling between each two, and an exciter (a smaller ribbed housing) beyond the generator.
  - **Windows** run along both upper sides (two facets each side) between the hoops: one GLASS pane per facet, 0.25 px
    inside the outer surface. Behind them a cavity: the inner wall `#2B2F34` 1.5 px in, `#16181B` end walls, and stage
    rings (`#575D65` lit / `#383C42` shade, 0.5 px) between the blade rows. What enters a casing (the chest, the valve
    pipes, the crossover) ends inside its wall, and a window never runs under one.
  - **Rotors** (drawn with the rotor, turning with it): a 12-sided hub drum and, per row, a disc and a ring of flat
    blades angled 35° to the axis, in the blade steel (front `#B4BAC0`, back `#7E848C`); more and longer blades toward
    low pressure (24 / 30 / 36 blades a row), the tips growing along each casing but always 1 px inside the
    inner wall's flats.
  - Flanges are plate slices; bolt heads `#959DA6`; elbows PLATE; the deck slab's edge the girder flange; louvred vents
    on both generator ends.
  - Port nozzles are PLAIN collars 10 px across (the port plate's collar) from the model to the face.
- **Hitbox** (not a texture, but it follows the model): formed, each block's shape is the model's parts in it,
  voxelized from TurbineMesh itself at 1 px (TurbineShapes), so a block's outline follows the parts a player sees.

## Lithium and the Battery Array
- **Spodumene** follows the ore method: Cody's green ore (as for fluorite, without the alternate purple clusters), his green
  raw item and green raw block, and his dust, all mapped by brightness onto a pale mint "hiddenite" ramp
  `#3E5A48 #76A088 #B4D8C0 #EEFFF4`, so it reads apart from fluorite (purple) and halite (pinkish). Spodumene Ore and
  Deepslate Spodumene Ore are overlays on vanilla stone and deepslate.
- **Lithium Brine:** Ethanol's frames recoloured by brightness onto a pale mint-teal `#4E7C70 → #EAF6F0`, a little more
  opaque (as Brine); fog `#B8DCCC`.
- **Lithium Hydroxide:** the dust texture recoloured onto a lavender-tinted white `#6E6880 #A29CB4 #CAC6D8 #E8E6F0 #FFFFFF`
  (cooler than Salt). **Graphite:** Cody's coke lump recoloured by rank onto graphite grey `#0C0D0F → #BCC2CA`; the
  **Block of Graphite** is the Coal Coke Block mapped tone for tone onto `#2A2D33 #3A3E46 #4E535C #666C76`.
  **Cell Separator:** the Plastic Sheet recoloured onto a milky white `#8A96A2 → #FFFFFF`.
- **LFP Cathode / Graphite Anode:** an electrode sheet (x3..12, y4..14) with a bare-foil top row and a tab (x5..8) up to
  y1, in a `#0C0D0F` outline: the coating lit on its top row and left column, a step darker right of x9, a shade bottom
  row and right column, one glint. Cathode: slate LFP `#7A8496 #566074 #465064 #2E3646` on an aluminium tab; anode:
  graphite `#6A707A #3E434C #30343B #1E2126` on a copper tab (the Tempered row).
- **Battery Array** (`block/battery_array/`), connected (ConnectedModel "battery_array": the digester's solid skin, rims on
  the outer edges only with the Distillation `ctm/column_beam`). It's solid, not a skin: casings keep their faces and
  beams toward their windows, so the glass shows the cells inside and never the inside of a casing; the formed Pressure
  Glass counts the array as its structure and draws no beam against it.
  - `plate` / `casing`: the Firebox Array's wall plate and loose casing with the firebrick band re-tiled as an insulated FE
    band (`#8E231C` face row, `#5A1712` shade row, a `#3A100C` joint every 4 px). `top` / `casing_top`: the Firebox
    Array's lids.
  - `controller_0`…`controller_4`: the plate with a recessed charge display (x3..12, y2..11, `#3A100C` floor) of four
    bars lit from the bottom like the Energy Cell's (`#FF8577` first column, `#E5483C`; unlit `#8E231C` / `#5A1712`);
    picked by the controller's CHARGE. `controller_unformed`: the same display, dark, in the loose casing.
- **Lithium Cells** (`block/lithium_cell/<tier>_<0-4>`, `<tier>_top`): the single-block casing with a 2-row tier band (top /
  front tones, rim highlight at the left end) on rows 2–3, two side rivets, and a recessed window (x5..10, y5..13) of
  four bars lit from the bottom as above, all a formed array's cells showing its fill. The top is a tier plate with two
  3×3 terminals, FE red (+) and steel (−).
- **Power Regulators** (`block/power_regulator/<tier>`, `_on`, `_top`): the casing with a 3-slat vent recess (x3..12,
  y2..9), a 2×2 status lamp under it (dark teal `#2F6E68` loose, status cyan `#B8F2EA` / `#5FD4C4` in a formed array)
  and the tier band on rows 12–13; the top is a riveted casing with a 6×6 tier plate.
- Ports `block/port/energy_input` (the input plate) and `energy_output` (the output plate) carry the FE chip
  (`#E5483C` / `#8E231C`); `lithium_brine` is the output plate with a mint chip (`#B4D8C0` / `#5E8C78`).
- Ports-tab face icons (`widget/arcforge/face_<mode>`, as `face_gas_output`): the output frame (`#F5B574` lit /
  `#9E5A1C` dark) or the input frame (`#7FB3F0` / `#2A5A99`) round the resource's colour: Energy Input (input frame, FE
  red), Energy Output (output frame, FE red), Lithium Brine (mint), and Brine, Salt and Water, which had none.
- GUIs: the Battery Array's is the Energy Cell's panel stretched to 186 tall (the inventory 20 px lower), a 16×72 gauge
  recess at (8,17) holding the Energy Cell's FE bars (`container/battery_array/energy_fill`, 14×70) and the house screen
  recess (30,17)–(168,89). The Thermal Evaporator's gains a 43×4 `#0E0E0E` track at (76,46) for its by-product bar
  (`byproduct_fill`: flat mint `#CEE6DC` / `#8CB8A8` / `#4E7C70`). The Chemical Reactor's two input slots become three
  touching slots at y 16/34/52.

## Renewable coal
- **Bio-Coal:** Cody's coke lump (as Coal Coke) recoloured by rank onto a warm brown-black hydrochar ramp
  `#140E0A #22180F #2F2116 #3E2C1D #4D3726 #5E4430 #70523A #866448` (the ramp's top tone `#9C785A` left out so it stays
  dark and coal-like); the **Block of Bio-Coal** is the Coal Coke Block mapped tone for tone onto
  `#140E0A #22180F #2F2116 #4D3726`. Brown against coal's black and coke's grey.
- **Range Upgrade:** the upgrade card (Speed's) with its accent strip recoloured lime (`#2F6E2A #7CD45F #D4F7C2` for
  Speed's `#1F6E66 #5FD4C4 #B8F2EA`) and the glyph cleared to the card body and redrawn as four corner brackets
  (x4..11, y5..11, lit `#D4F7C2` on the top-left pixels) round a 2 px dot.
- **Tree Cutter** (single-block casing; input on top, output underneath, energy at the back): the Vulcanizer's top and
  bottom, the Block Breaker's energy back and side. The front is a 12×12 recess (x2..13, y2..13: `#16181B` top and left
  edge, `#727982` bottom and right, `#383C42` corners) holding a circular saw: a hub (r 1.2, `#383C42`, Heat-row orange
  `#F26A16` when on) in a rivet ring, a steel disc (r 3.6, `#9AA0A8` lit top-left / `#7E848C`) and 8 teeth to r 4.9
  (`#B4BAC0` / `#959DA6`), over a hazard stripe on row 12 (`#E0B020` / `#1A1A1A` in pairs). `_on` is 4 frames
  (frametime 1) with the blade turned 22.5° each (a tooth pitch is 45°, so it loops) and the hub lit.
- **Hydrothermal Carbonizer** (single-block casing; input on top, output underneath, heat at the back: all the
  Vulcanizer's faces). The front is a riveted pressure-vessel window (frame x2..13, y1..10, `#575D65` lit edge, `#2B2F34` shade,
  four rivets) holding water (`#7FB3F0` surface row, `#4A8FE0` body, `#3A76C0` right column) over a two-row biomass bed
  (`#3E2C1D` / `#2F2116` cold), and a 4×3 gauge recess (x6..9, y11..13, `#16181B`) with a white pivot and a needle.
  `_on` is 4 frames (frametime 5): the bed glows in the Heat row (`#C73A0E` / `#7A1B0A`, shifting a column a frame), two
  bubbles `#DCE8EE` rise through the water, and the needle swings up and turns orange.
- GUIs (cut from existing backgrounds):
  - **Tree Cutter:** the Block Breaker's with its progress track painted out, three touching blue input slots (the
    Chemical Reactor's slot frame) at (29,17), (47,17), (65,17) for saplings and one at (29,39) for Bone Meal; the 3×3
    output grid and energy gauge stay. Its sprites are the Block Breaker's (`energy_bar`, LEDs) plus two ghosts drawn
    like the others (white, alpha 0x37 body / 0x69 edge): `ghost_sapling` (a sapling's crown on a stem) and
    `ghost_bone_meal` (a heap).
  - **Hydrothermal Carbonizer:** the Grain Dryer's with its tank frame copied to x 138 for the returned water and the
    heat track cut short of it; the heat buffer, tank, heat bar, min-temperature tick and LED sprites are the Grain
    Dryer's.

## Carbon capture
- **Faces from parts.** The three machines' plain and port faces are built, not hand-painted: the single-block casing (split
  face, 1 px bevels, four rivets with their inward shadows) with the 8×8 port plate cut from `block/port/<mode>` (rows and
  columns 0,1,2,3,6,7,8,9 of the 10×10) at x/y 4..11 on each face the default side configuration gives a mode, its 2×2
  chip in the resource's tones (lit top-left, mid, mid, dark): Carbon Dioxide `#DCE2E8 #B4BCC4 #7E868E`, Carbon Dust
  `#5A5E64 #3A3D42 #1E2023`, fuel (the item input brown) `#8A7A64 #4E3018 #2B1A0E`, ash `#B3AEA7 #86817C #55524E`, Syngas
  `#F4E6CC #E2CCA4 #A8916A`, oils `#F2D27A #C8902A #7A5214`, FE `#FF8577 #E5483C #8E231C`. Plain sides are the Vulcanizer's
  vented side; heat backs and bottoms are the Vulcanizer's and the Haber Reactor's heat faces.
- **Carbon Reclaimer** (top input, bottom output, back FE, both sides vented): the front is a reaction window (recess
  x3..12, y2..11) of Carbon Dioxide haze (`#7E868E` top two rows, `#6E767E` below) with a zig-zag heating coil across rows
  5–6 (cold `#2B2F34` / `#16181B`) over a carbon bed (`#4A4E54` lit row, `#2A2C30` body, two `#1E2023` pits). `_on` is
  4 frames (frametime 4): the coil in the Heat row (`#F26A16`, a `#FFB02E` glint stepping along) and two motes of carbon
  falling onto the bed.
- **Gasifier** (top input, bottom output, right Syngas, back heat, left vented): a gauge recess (x5..10, y2..5) over a
  grated firebox door (recess x3..12, y6..12): furnace brick inside (`#2B211B` / `#3A2C23`), a cold fuel bed
  (`#3E3A36` / `#2B2826`) on its bottom two rows, and two `#2B2F34` grate bars at x6 and x9. `_on` is 4 frames
  (frametime 3): the box glows (`#7A1B0A` / `#C73A0E`), the bed burns (`#F26A16` with a `#FFB02E` ember stepping along),
  three flame tips rise and fall between the bars, and the gauge needle turns orange.
- **Fischer-Tropsch Reactor** (top input, bottom heat, left output, back FE, right vented): a tall window (recess x3..12,
  y2..12) of warm Syngas haze (`#8A7E68` / `#76694F`) over three strata (Naphtha `#E8D68A`, Light Oil `#C8902A`, Heavy
  Oil `#5A3A18`), with three 1 px catalyst tubes (x5, 8, 11) from a `#727982` cap down into them (`#959DA6` above the
  strata, `#575D65` in them). `_on` is 4 frames (frametime 4): the tubes a step lighter and two Syngas bubbles
  (`#F4E6CC`) rising between them.
- **Syngas** is a gas: the steam texture tinted a warm pale tan `#E2CCA4` (fog the same), set apart from the cool gases and
  Biogas's olive. No new item textures.
- GUIs (cut from existing backgrounds):
  - **Carbon Reclaimer:** the Hydrothermal Carbonizer's arrow and result frame, with the Haber Reactor's FE gauge and two
    tanks on the left and its last tank on the right; the heat track painted out.
  - **Gasifier:** the Hydrothermal Carbonizer's background as it is (steam tank, fuel slot, arrow, ash frame, Syngas tank,
    heat buffer, heat track). Sprites the Hydrothermal Carbonizer's, plus the Combustion Plant's ghost coal.
  - **Fischer-Tropsch Reactor:** 176×186 (inventory 104 down): the Haber Reactor's with its inventory moved 20 down and a
    plain row added; FE gauge and Syngas tank on the left, the catalyst slot (the Chemical Reactor's blue frame) at
    (43,34), the arrow at (63,34), four tank frames at x 88/104/120/136, the buffer frame at 156, and a 158 px heat track
    at (9,88) with ticks at both ends of the window. `ghost_dust`: a heap, drawn like the other ghosts (white, alpha 0x37
    body / 0x69 edge).

## Sifter, Diamond Press and biogas sulfur
- **Faces** are built like the carbon-capture machines' (casing + 8×8 port plates at the default modes, chips in the
  resource's tones): gravel `#9A948E #6E6A66 #45423F`, dust `#C9C4BE #8E8984 #5C5854`, Graphite `#7E858F #4E535C #2A2D33`,
  diamond `#D4FFF8 #6AD2EE #2E96B6`. Plain sides are the Vulcanizer's vented side; the Diamond Press's heat face (left) is
  the Vulcanizer's heat back.
- **Sifter** (top input, bottom output, back FE): a recess (x2..13, y2..12) holding a vibrating screen: a gravel bed of
  2 px pebbles in the three gravel tones (rows 4–6) riding on a steel rail with wire knots every other pixel (row 7,
  `#959DA6` / `#575D65`), and a dust tray on a lit lip (row 11) with two small heaps of finds (dust greys, a copper
  `#C98244` fleck). `_on` is 4 frames (frametime 2): the bed and the rail shake a pixel right, back, left, and dust
  falls through the mesh.
- **Diamond Press** (Hardened; top input, bottom output, left heat, back FE): a recess (x2..13, y2..11) with a tungsten
  ram (a shank and a wide head, `#1C1F24 → #B4BBC2`) over a Graphite puck and a bed anvil (face, waist, foot); under the
  recess the Hardened tier band (row 12 `#A476DA` with a `#CEAAF2` end, row 13 `#7042A4`). `_on` is 4 frames (frametime
  4): the ram comes down a pixel, both anvil faces glow in the Heat row while it presses, and the puck comes out as a
  diamond glint.
- **Sifter Meshes** (items, `#0C0D0F` outline): a 2 px square frame (lit top/left, dark bottom/right, lit corner rivets)
  round a wire grid every 3 px (x/y 5, 8, 11) with lit crossings and open (transparent) gaps, in the metal's ramp:
  Steel the Steel Rod's `#212427 → #A8AFB6`, Invar the Invar Ingot's `#343731 → #BCC2B4`, Tungsten metal
  `#1C1F24 → #B4BBC2`.
- **Deepslate Gravel** (natural, vanilla texture language): wrap-around pebbles (a seeded Voronoi of 18 stones), each a
  flat tone of the deepslate ramp `#1C1C22 #29292F #35353C #42424A #505058 #5E5E67 #6E6E78` with a lit top/left rim two
  steps up and a bottom/right rim a step down, and dark `#1C1C22` gaps between stones. It tiles seamlessly.
- **Sulfur port** (`block/port/sulfur`) and Ports-tab icon (`face_sulfur`): the Salt ones with a sulfur chip
  (`#F2E46A` / `#D2BE3A`; the icon's fill `#E6D24E`).
- GUIs: the **Sifter** is the Metal Press's background with its result frame swapped for two rows of the Block Breaker's
  output grid at (101,25) (the die frame holds the mesh); ghosts `ghost_mesh` (a framed grid) and `ghost_gravel` (a heap).
  The **Diamond Press** is the Hydrothermal Carbonizer's with the Haber Reactor's FE gauge in place of the left tank and
  the right tank painted out; `ghost_graphite`. The **Biogas Digester** gains a sulfur slot (its Digestate frame) at
  (117,43).

## Plates, gears and rods
- Every plate and gear (steel, copper, silver, nickel, tungsten, invar) is Cody's plate or gear, with
  every pixel kept. His screenshots were sampled on their pixel grids (the plate a clean 24x, the gear a resampled ~7x),
  quantised into 6 brightness levels, and recoloured by level onto the metal's 5-tone item ramp, with a sixth tone
  (the darkest ×0.72) for his own tonal outline: no `#0C0D0F` black.
  - Plates are painted soft (the gears and rods keep the full ramp): levels 0-5 are outline, tone 1, tone 2, halfway
    from 2 to 3, tone 3, and 60% of the way from 3 to 4. The centre reads a step lighter than the rim, never the
    highlight tone.
  - Arcite-Tungsten Composite is the tungsten plate with two arcite fibres laid across it (slope 1/2): `#B8F0FF` with
    a `#E8FCFF` glint every third pixel, over a `#2E3338` groove row.
  - Steel `#2E3237 #484C54 #5C646C #747880 #A8AFB6`; copper `#8A4822 #AE6632 #C98244 #E0A062 #F6C898`;
    silver `#4B5663 #5E6C7C #98A8BA #CAD8E6 #FFFFFF`; nickel `#584E2B #6E6236 #AFA26A #DDD29C #FFF9DC`;
    tungsten `#25292D #2E3338 #545B63 #7C848D #B4BBC2`; invar `#4A4E46 #5C6258 #8E9588 #BCC2B4 #E6EADE`.
  - A new metal's plate or gear uses the same level maps (`plate_levels` / `gear_levels`) on its own ramp.
- Rods follow the plate and gear: a 6-level map (`rod_levels`) painted on the same tones, with no `#0C0D0F` black.
  A 4 px diagonal body (bottom-left to top-right) between a tonal outline: the upper-left edge on tone 1, the
  lower-right edge on the darker outline tone. Across the body: highlight (5/4), bright (4/3), mid (3/2), shade (2/1),
  varied row by row for the same grain as the plate. The top end is a lit cut face; the bottom end sits in shadow.
- Every metal with a plate also gets a gear and a rod.
- The Metal Press dies engrave the part they press, in the die's face (x/y 3..12): the plate's silhouette, a 10 px gear
  (eight teeth round a ring, a 2×2 hole) and the rod's diagonal, each a `#2E3237` floor with a `#0C0D0F` shadow along its
  top edge and a `#5C646C` lit lip under its bottom edge. The coloured strip at the bottom stays.

## Heat Pipe and the "Limited by connections" line
- **Heat Pipe** (`block/heat_pipe/`): a UV-unwrapped 32×32 sheet `pipe` (1 texel per px, UVs in half-units) and a
  tinted `core`. The model runs along Z (its negative end north) and the blockstate turns it for X (y 90/270) and Y
  (x 90/270): `body` (a 12×12 jacket, 2..14, and a 6×6 core, 5..11, end to end) and `cap` (a 16×16 plate 3 px thick at
  a pipe end, against the port). Only the ends are flanged: between the blocks of a pipe the jacket runs on unbroken, so
  a pipe reads as one length.
  - Jacket strip (x 0–15, y 0–11), along the pipe: `#727982` lit edge, `#575D65`, two rows of `#474C53` face, the window
    band as a recess (a `#16181B` top lip, 2 clear rows, a `#727982` lower lip), two rows of `#40454C`, then `#2B2F34`
    and `#16181B`. All four long faces share it (up and down turned 90°).
  - Cap ring (x 0–15, y 12–27), the cap's inner face: the house bevel 2 px wide round a hidden `#2B2F34` middle. Cap sides (x 16–18): `#727982`, `#474C53` with a `#959DA6` bolt head
    and its `#2B2F34` shadow every 4 px, `#2B2F34`.
  - Cap face (x 16–31, y 16–31): the single-block casing (bevel, split face at x 10, four rivets with inward shadows)
    with a recessed 4×4 plug in the middle.
  - `core` is neutral grey for the tint (rows 2–3, the ones the window shows: `#FFFFFF` over `#D2D2D2`; the rest
    `#B4B4B4`), full-bright (`block_light` 15) and unshaded. HeatPipeTint colours it by the temperature carried with the
    Thermodynamic Conduits' heat ramp (`#9A2A18` at 100°C, `#E0461A`, `#FF8A2A`, `#FFD35A`, `#FFF4D6` at 1,500°C) and
    `#2B2F34` while it carries nothing. The item shows it at `#E0461A`.
- **"Limited by connections"** (every heat, FE or steam producer's GUI): one line in warning yellow `#FFD24A` in place of
  the inventory label, scaled down to fit the 160 px between the margins (to 2/3 at most; past that the short form
  "Limited: ..." is used). Jade shows the same line in the same colour.

## Industrial Water Intake
- **Casing** (`block/water_intake/casing`): the single-block casing (broad shading, bevel, four rivets) with a recessed
  intake grille: two `#575D65` slats with `#16181B` shadows over water (`#2C4FA6` top, `#3A76C0` bottom) and one
  `#4A8FE0` glint.
- **Formed** (`block/water_intake/formed`, `formed_on`; models `formed` / `formed_active` on the centre block): one
  UV-unwrapped 128×128 sheet, 1 texel per px. Every
  large face (skid top, walls, lid, motor-housing top, junction-box top, and the faces between the housing's
  ribs) uses broad shading (see rule 2). A 48×4 skid (edges the 4-row strip,
  top the broad-shaded wall plate, underside the intake strainer: `#16181B` with a `#383C42` bar every 4 px in a bevel), a 42×28
  pump house set in 3 px (walls: the broad-shaded wall plate with a riveted strap across rows 5–7 and an intake louvre band near the
  bottom: a recess with a slat every 3 rows), a 44×44 lid with a seam, and low, flat roof equipment (nothing tall or
  round sticks up): a 32×6×14 motor housing lying along the front half (long sides broad-shaded between cooling ribs, a
  `#575D65` column and a `#16181B` column every 3 px, under a `#727982` top row; ends the edge strip; top the broad-shaded wall plate),
  a 6×6 discharge header across the full width behind it (lit-to-deepest rows, flange bands at the ends and middle) and
  a small 10×4×6 junction box. The front's sight glass (a 17×7 recess) shows still water
  (`#2C4FA6` / `#1C2E44`) idle and, in `formed_on`, `#4A8FE0` with a `#7FB3F0` top row and diagonal flow streaks on a
  fixed pitch, a `#3A76C0` right column, and a lit status lamp beside it.
- **GUI** (`gui/container/water_intake.png`): the Condenser Array's frame with the Electric Pump's 10-wide FE gauge on
  the left; its sprites are the Electric Pump's.

## Wired Machine Link and Link Conduit
- **Link Conduit** (`block/link_conduit/*`, multipart `link_conduit`): a thin 4×4 cable. A centre node (a 4×4 bevelled steel
  clamp: `#727982` top/left, `#474C53` face, `#16181B` bottom/right), an arm per joined side, and at a plugged end the
  Wired Machine Link port plate (10×10, 1.5 px thick, `#575D65` / `#2B2F34` / `#383C42` edges). The jacket (`jacket`, along the
  cable) is a `#727982` lit row, a 2 px slot open onto the core, and a `#2B2F34` dark row, with a steel clamp band
  (`#959DA6` / `#575D65` / `#383C42` / `#16181B`) every 8 px. The Iolite core (a 3×3 element inside) shows through the slot:
  unlit `#3C298A` / `#2E1660` / `#1E0E3E`; while the link is live (`arm_on`) `#4A3CB4` / `#7C9CEC` / `#3C298A` and emissive.
  Arms point north and are turned by the blockstate (y for sides, x 270 up, x 90 down).
- **Wired Machine Link** (`block/wired_machine_link/*`, models `wired_machine_link` / `_on`, `arcforge:ported`): the machine
  casing (split face, bevel, four rivets) with a Link-row Iolite inlay at the middle of each edge (`#636CD0` over
  `#4A3CB4` top and left, `#3C298A` over `#2E1660` bottom and right), outside the 10×10 port plate, so a set face shows
  the usual port plate for its mode in the middle. The front has a status screen: an 8×6 recess (deepest top/left lip,
  lit bottom/right lip) at x4–11, y4–9; off, `#1E0E3E` glass with a `#2E1660` line; linked, `#3C298A` glass, a `#4A3CB4`
  top row and a flat `#7C9CEC` pulse line, emissive (`screen_on`). A port set on the front draws over the screen.
- **GUI** (`gui/container/wired_machine_link.png`, 176×126): the house frame, one screen recess (7,17)-(168,60) for the
  read-out (machine name, cable face and length, status LED and line), and below it the face grid drawn from the
  `face_<mode>` icons in the Sides tab's cross, with a wrapped hint to its right.
- **Iolite** (`block/ore/iolite_ore`, `block/iolite_block`, `item/iolite_crystal`, `item/iolite_dust`): see Natural
  materials (the ore on End Stone, the crystal's own shape).

## Crude oil and the oilfield
- **Fluids** (`block/fluid/<name>_still` / `_flow`, each keeping its source's `.mcmeta`): re-toned by rank from an
  existing fluid's frames, never repainted, so they move like their source.
  - Crude Oil: Heavy Oil's frames on a warm black with a faint brown-grey sheen (`#0D0C0B #131210 #1A1815 #2E2A25`; the
    flow adds `#0A0908` and the sheen up to `#4C443A`). Thick and slow: frametime 8 still, 5 flowing, since caverns
    and surface seeps show it as a block. Its fog is near-black.
  - Bitumen: Heavy Oil's frames on a cold blue-black (`#060607` to `#141418`; the flow up to `#30303A`).
  - Kerosene: Light Oil's frames (keeping its alpha) in a pale water-white straw (`#C8C6AA` to `#FBFAEE`).
  - Diesel: Light Oil's frames in amber (`#9C8418` to `#F6E6AA`).
  - Refinery Gas: the gas model, tinted `#C2CCDA`.
- **Ports** (`block/port/<mode>`, the Ports tab's `face_<mode>`): the 10×10 output plate (orange ring) with the
  product's chip in two tones: Refinery Gas `#E4E8EE` / `#B4BCC8`, Kerosene `#F2F0DC` / `#D4D2B6`, Diesel
  `#E8D27A` / `#C0A42C`, Bitumen `#3A3A40` / `#141418`. The face icons use the same pair with their mean between.
- **Oilfield blocks** (`block/oilfield/*`), all in the single-block casing grammar (2 px bevel, split face at x 10):
  - Derrick Casing: the wall plate with top rivets and the riveted strap on rows 11–13; its top (`derrick_casing_top`)
    a tread deck: a recessed panel with three lit grating bars, each with its shadow row, and corner rivets.
  - Derrick Frame: a cutout steel lattice: 2 px posts and rails round the edge (lit top/left, dark bottom/right), 1 px X
    bracing behind them with a shadow pixel under, rivets at the corners.
  - Controllers: the casing with a screen recess (`#1A1E22` floor) at x 3–12, y 2–9 and a status LED (`#B8F2EA` /
    `#5FD4C4`, `#2F6E68` when idle). The Derrick's screen adds an amber depth gauge (`#E8913A` / `#C8782A`) with scale
    ticks; the Pumpjack's a cyan nodding-beam glyph. `_on` frames light them.
  - Pumpjack Casing and Controller: the hazard stripe (`#E0B020` / `#1A1A1A`, 2 px diagonal bands) in place of the
    strap, between a `#575D65` lip and a `#16181B` shadow.
  - Well Head (`block/well_head`, its own model): a 64×32 sheet with one rectangle per element face (flange, spool,
    side outlet, gland), each painted to its size: 1 px lit top/left, dark bottom/right, flat between, rivets only on
    faces 9 px or larger.
- **Items:** Steel Pipe, a flanged pipe length on the diagonal (machine steel, a bore at the top end); Seismic Survey
  Tool, a thumper plate, a shaft with a T handle and a readout box with a cyan screen.
- **GUIs** (`gui/container/oil_derrick`, `pumpjack`): the Electric Pump's frame. The Derrick keeps the blue input slot
  (Steel Pipes, with a white Steel Pipe ghost at the pump's ghost alphas `0x69` rim / `0x37` body) and drops the arrow
  and output slot; the Pumpjack keeps both bucket slots. The tank fill falls back to a dark Crude Oil hatch
  (`#1A1613` / `#241E19`, `#2E2620` top row) when the fluid sprite isn't there.

## Oil caverns, sour crude and natural gas
- **Fluids** (re-toned by rank, keeping the source's alpha; see Fluids):
  - Sour Crude Oil: Crude Oil's frames and timing, its blacks pulled toward a yellowish brown
    (`#100E09 #17140D #1F1B11 #36301E`; the flow `#0C0A06` up to a `#58502E` sheen), so a sour cavern reads as
    crude with a sickly cast beside a sweet one. Both have still and flowing block textures; neither burns.
  - Sour Diesel and Sour Kerosene: their clean fuel's ramps on Light Oil's frames, each tone pulled half way toward an
    olive of its own lightness (hue 58°, 0.82 of its lightness, saturation 0.42), so they read as the same fuel gone
    off.
  - Natural Gas (`#C8E4F6`, a clear pale blue) and Hydrogen Sulfide (`#C4CC6A`, a sickly yellow-green): the gas model
    tinted (GasTints, matched by `gas_properties.json`).
- **Port chips:** Sour Diesel `#D4C76E` / `#A59532`, Sour Kerosene `#E5E4BF` / `#CECC98`, Hydrogen Sulfide `#D4DA80` /
  `#A4AC48`, Natural Gas `#DCEEFA` / `#A8C8E2`, Carbon Dioxide `#C8D0D8` / `#94A0AC`, Steam `#E4E8EE` / `#B4BCC8`,
  Sulfur `#F0E070` / `#C8B02C`.
- **Single-block units** (the machine casing, ports at their default faces with each fluid's chip, `_on` animated):
  - Hydrotreater: a reactor window of three catalyst beds (two-tone pellet rows `#B4A884` / `#7E7458` over `#5E5642`)
    between support grids. `_on` bubbles Hydrogen (`#EAF2FA`) up through them.
  - Sulfur Recovery Unit: a furnace with its burner at the back over a pit of molten sulfur (the Sulfur chip's tones
    with a `#FFF4A8` glint). `_on` lights the burner and moves the glint.
  - Steam Methane Reformer: a furnace box lined with firebrick (`#553F34 #3A2C23 #2B211B`), three reformer tubes
    hanging close-packed from a lit inlet header to an outlet collector. `_on` glows the tubes white-hot to orange with
    a bright band moving down each. It must never read as the Catalytic Reformer's tube window: the brick lining down
    both sides, the header and collector across, and no casing gaps between its tubes.
- **Gas Wellhead** (`block/gas_wellhead`, its own model by `tools/GasWellheadModelGen`, on the large-model rules like
  the Well Head): a flanged casing head, a spool, the master valve body, a swab cap with its flange and stem, a wing
  (round along its length) out to the flow line's flange, a choke on the wing with its stem and handwheel, and a
  pressure gauge on a stand-off.
  - Every element face is its own region, packed into 16×16 sheets (`block/gas_wellhead/sheet_N`) and painted to its
    size: round parts carry the steel ramp across them (on a band 2 px high or less, across its height), flat faces 1 px
    lit top/left and dark bottom/right.
  - The handwheel is the only red (`#5E1712` to `#D8573E`): the ramp round its rim on the sides, and from above a red
    rim (lit far edge, dark near edge) round dark gaps, with spokes in to a steel hub, so it reads as a wheel.
  - The flow line's flange carries the output port: the orange ring round a Natural Gas chip. The gauge's east face is
    a white dial in a steel bezel, its needle dark and a red mark low left.
  - Its hitbox is the elements, rotated with its facing.
- **Items:**
  - Sour buckets: the fluid bucket model with the fluid's still frame (`neoforge:fluid_container`).
  - Survey Record: a folded paper chart (`#F6F2E4` lit, `#E4DCC6` body, `#C8BEA2` shaded right leaf, `#A69C80` fold),
    the top right corner dog-eared, a seismograph trace (`#2B2F34`) across both leaves with spikes in the middle, and a
    red mark (`#C0392B`) where the oil lies.
- **Map markers** (`map/decorations/oil_<kind>.png`, 8×8): a small derrick outlined like vanilla's markers: two legs
  (`#8A6236`) splaying from a lit crown (`#C89A5C`) to the ground with girts between, a `#1A120A` outline, standing in
  a black pool. Taller for bigger deposits (Seep shortest, Giant the full height).
- **GUIs** (`gui/container/hydrotreater`, `sulfur_recovery_unit`, `steam_methane_reformer`): the refining machines'
  frame with a second feed tank recess beside the feed (Hydrogen, Oxygen or Steam), its tooltip naming that feed.
- **Surface seeps:** a small pit of the deposit's oil sunk in the ground with a coarse-dirt rim; no texture of its own.

## Steam Cracker, Propylene and Polypropylene
- **Steam Cracker Casing** (`block/refinery/steam_cracker_casing`, in the oilfield's casing grammar: 2 px bevel, split
  face at x 10, top rivets, the riveted strap): a peep hole in its upper half (a steel frame, a firebrick ring, the
  Heat row's fire in it). Its top is the tread deck. **Controller:** the screen recess with the status LED and a glyph
  of three furnace tubes over a flame; `_on` lights them. Formed, both hide their faces (`steam_cracker_hidden`, with
  the port plates).
- **Propylene:** the gas model tinted `#D8CCEC` (a pale lavender; GasTints, matched by `gas_properties.json`).
- **Port chips:** Ethylene `#E2F0E6` / `#A8C8B4`, Propylene `#ECE4F6` / `#B8A8D2`; the face icons use the same pair.
- **Polypropylene Sheet:** the Plastic Sheet's shape and outline, its four tones moved to a creamier off-white with a
  faint warm tint (`#CCBE9E #E2D6BA #F2EAD4 #FFF8E8`), so it reads apart from the Plastic Sheet's cooler white.
- **GUI** (`gui/container/steam_cracker`): the refining machines' frame with the feed and steam tanks on the left,
  four product tanks (x 89 + 16k) and the heat buffer.

- Formed, the Distillation Array's casings, tray levels and controller draw nothing (`distillation_array/hidden`, with
  the port plates); DistillationArrayRenderer draws a refinery column from RefineryLayout, built by RefineryMesh (on
  ModelMesh, as the turbines') on one sheet, `block/distillation_array/refinery` (128×256). Rows 0–95 are laid out as
  the Steam Turbine Array's (flat tones, tiles, panels, CASING strips, broad-shading rows 64–87) with, in rows 48–51, the
  fire glow: GLOW (4×7: `#7A1B0A #C73A0E #F26A16 #FFB02E #F26A16 #C73A0E #7A1B0A`), GLOW_CORE `#FFB02E` and
  GLOW_SMALL (`#C73A0E #FFB02E #C73A0E`), dark red at the ends stepping to an orange core; no blue (it's a fired heater,
  not a combustor). Rows 96–233 hold the large-model layout (`tools/LargeModelTextureGen.java`), as on the Pumpjack's
  sheet: the small parts' tiles, ramps, the burner fire, the grain fields, edge strips and rivet heads.
- **Textured as a large model** (section 5, "Large model texturing"): every face at exactly 1 texel per model px, nothing
  stretched, no broad shading, no face over 4.5×4.5 px in one flat tone; DistillationArrayRenderer shades each face as a
  block face turned its way is (the burners' fire and the vapour stay unshaded, being light).
  - The shell is plated unrolled: riveted plates as tall as each tray section and as few round it as keep each within
    4 blocks (two on a 2×2 or a 3×3), carrying on from facet to facet, rivet rows along their edges and seams only where
    they meet and at the tray seams. The elliptical head, one pressed piece, is bare grain (no plate edges on its facets).
  - The reboiler, headers and the other plated boxes are continuous plates sized to each face (seams only between
    plates, rivet rows along their edges); the decks and the condenser's top the seamless grating; the bundle's sides
    keep the VENT louvre tile at 1:1; discs (the fan shroud tops) are one plate each.
  - The base ring, flanges, fan shrouds and lips carry their ramp across their height, the same all the way round;
    pipes, the vapour line, the stack, the manways and the ladder's stiles, rungs and cage carry it round them.
  - The burner windows show the fire tile (8×6), drawn full-bright at the column's heat.
- **Sizing:** the shell's radius follows the cross-section (11.5 px for 2×2, 17.5 px for 3×3; 16 or 24 sides), and its
  tray sections are 32 px tall with the top section at least 20 px; taller columns get more sections, never stretched ones.
- **Reboiler:** a plated box over the footprint up to 13 px (riveted plates on its sides, deck plates on top), the base
  deck plate on it, its front left corner cut away for the ladder (11.5 px square on a 2×2, 15 px on a 3×3). Burner
  windows on all four sides: a 1 px steel frame half a px proud, a DEEP pane, and the fire drawn full-bright over it,
  its brightness (T − 100) / 500 of the column's temperature; cold, only the dark pane shows. A stack (a DEEP-capped
  lip) rises at the back left.
- **Shell:** riveted plates wrapped round it, a base ring, a bolted flange at each tray seam (1 px RIVET bolt heads on
  every facet) and an elliptical head.
- **Sight glasses** (rule "Glass"): on each section's front a flat boss (PLAIN sides, a 1 px frame lit top/right, shade
  bottom/left) with one 12×12 GLASS pane a quarter px inside its face. Behind it a shallow cavity (DARK back and top,
  DEEP underside, SHADE sides) where the renderer draws the tray's pool in its product's own fluid sprite and tint and
  the rising vapour, as the old tray windows did. Nothing passes in front of a pane: fittings sit at `fittingsY`, clear
  of the platform bands.
- **Manways** on each section's left: a banded nozzle with a bolted blind flange (eight bolt heads) and a davit arm.
- **Platforms** on every other flange: grating fans from the shell out to the footprint's edge, open at the ladder's
  corner; the top deck is grating over the head with a hatch at that corner. Railings on every deck edge: a 2 px kick
  plate, a hand rail, a 1 px knee rail and posts at most 8 px apart.
- **Caged ladder** up the front left corner, as wide as a ladder block: two 1 px stiles 11 px apart (`LADDER_HALF`
  either side of its middle, 3 px out from the shell) and a 1 px rung every 4 px, from the ground to above the top
  deck; from 24 px up, a cage of 1 px hoops every 10 px, bowing out round it a px wider than the ladder, joined by bars.
  The stiles are square bars closed at the top and the rungs thinner, ending inside them; each hoop has a knuckle at
  every bend and a bracket from each end back to its stile (no open pipe end shows anywhere on it).
- **Top:** the condenser bank on legs: the tube bundle in the VENT louvre tile with a GRATE top, PLATE headers at both
  ends, one or two fan shrouds (banded, a DARK inner wall so the opening reads as a well, two MID guard bars) with the
  fan blades in BLADE / BLADE_BACK turning while it runs. The vapour line (banded, PLAIN elbows) rises from the head
  into the left header; the reflux returns from the right header into the top section.
- **Draw-offs:** one banded pipe per liquid product it makes, out of its tray through a MID nozzle collar, then down the
  right side to the base deck. Port nozzles are PLAIN collars 10 px across, as the turbines'.
- **Hitbox:** formed, each block's shape is the model's still parts in it (RefineryHitbox, as the Steam Turbine Array's
  TurbineShapes), with the port plates added.

## Pumpjack model
- Formed, the Pumpjack's casings and controller hide their faces and PumpjackRenderer draws the unit from
  PumpjackLayout, built by PumpjackMesh on `block/pumpjack/pumpjack` (128×256). Rows 0–95 are the shared model sheet
  layout (flat tones and panels). Rows 96–233 hold the large-model layout: the furnace's refractory brick (`M_BRICK`,
  0, 96, 16×8: buff firebrick `#9C7A58 #927052 #8A684A` with a `#AE8C68` lit top row, staggered courses, `#4E3C2E`
  mortar), a control panel's lit screen (`M_SCREEN`, 16, 96, 8×6: cyan bars and readouts on `#10181C` glass, drawn
  full-bright), the small parts' tiles, the hazard edge (`#E0B020` / `#1A1A1A` in 2 px diagonal bands, its edge rows darker), the
  round parts' ramps 2 to 14 px across and the polished rod's bright ramp, the grain fields, edge strips and rivet
  heads. `tools/LargeModelTextureGen.java` paints them (run `java tools/LargeModelTextureGen.java` from the repository
  root).
- **Textured as a large model** (section 5, "Large model texturing"): every face at exactly 1 texel per model px, still
  and moving parts alike, so its pixels match the blocks round it; nothing stretched, no broad shading, no face over
  4.5×4.5 px in one flat tone. The game's face shading lights it.
  - Plated faces are continuous plates sized to the part, seams only where its plates meet: the plinth deck is one
    recessed deck plate, its sides one plate each; the reducer, guard and beam sides one plate per face; the
    reducer's riveted plates have rivet rows along their edges. A moving part's plates move with it.
  - The platform is the seamless grating; the skid rails and the beam's top and bottom flanges the girder flange, laid
    along them; the motor the ribbed motor plate in generator steel; the horse head's side plates one plain plate and
    each counterweight's side one riveted plate, laid out over its whole outline and cut by it; the plinth lip, flange
    edges, stiles and other thin strips a 1:1 slice of a plate. The reducer's and bearings' end caps are each one plate.
  - Round parts carry the ramp across them, per facet strip: the legs, braces, pitman arms, bridle wires, the bearings
    and split ring, the reducer's half-round top, and the horse head's curved face (a 9 px ramp across its width, the
    same down the whole arc); the polished rod its bright ramp, the only bright steel. The counterweights' curved edges
    carry the hazard edge, carried on round the arc.
  - **Well Head** (its own block model, `tools/WellHeadModelGen.java`, rule 6): a flanged casing head on the ground
    (a 12 px flange, the casing spool and its top flange), a short valve tree (a flanged body), a side outlet with its
    flange, a valve bonnet, stem and handwheel, and a stuffing box with a gland nut on top, where the polished rod goes
    in. Its round parts' sides carry the ramp across them (thin flanges across their height); its tops and ends are
    grain plates with their edges. All in the machine steel; the polished rod (the Pumpjack's) is the only bright part.
  - The FE band on the motor is red laid in 2×2 texel cells; the lightening holes are 2 px DEEP squares.
- In the house steel, from the ground up:
  - The skid rails running past the well, open at the well end (a real skid is slid over the well) with one cross
    member just behind the Well Head, never through its base plate.
  - The base: a deep plinth under everything but the well, with a lip round its top.
  - The Samson post: an A-frame of four legs (3.6 px), the front pair splayed wide, braced along both sides and across
    front and back low down (clear of the horse head's swing).
  - A grating platform round the saddle with railings along both sides, and a ladder up the far side from the rail,
    as wide as a ladder block (12 px between 1 px stiles, a 1 px rung every 4 px), the stiles running on past the deck.
  - The saddle with its bearing.
  - The gear reducer: a box with a half-round top about the crankshaft, a split ring and bearing caps at the shaft,
    and a footing lip.
  - The motor (with its FE band) behind the reducer, with a belt guard.
  - Faces nobody sees aren't drawn (the skid's undersides, the plinth lip's, the beam web's top and bottom inside the
    flanges, where they would z-fight).
- **Block shading:** PumpjackRenderer shades each face as a block face turned its way (top 1.0, N/S 0.8, E/W 0.6,
  bottom 0.5) and sends its normal up, so entity lighting doesn't darken it below the blocks beside it.
- Moving parts, drawn each frame at the crank's angle:
  - The walking beam: a 12 px deep girder with flanges top and bottom, running past the equalizer at the back.
  - The horse head: its curved face an arc about the saddle bearing (so the bridle always hangs over the well), 36°
    below the beam and 32° above (covering the beam's end), and its back a flat plate square to the beam's end,
    which runs 2 px into it. Over its bottom 14° the back rounds in to the face, a quarter circle in its depth, to a
    tip 2 px deep under the bridle. **28 facets** over the arc (about 2.4° each), so the face and the plates' outline
    read as one smooth sweep. Four lightening holes in each side plate. The bridle's wires lie over the face from its
    top down to where they leave it.
  - The equalizer: a crossbar out to the pitman arms on its own bearing.
  - The cranks: arms with big counterweights on the pin's side (a ring sector about the shaft, 17 px out, ±38°, in
    **24 facets**). Only the curved edge is hazard-striped; the sides stay unstriped (striping all of it was too loud).
  - The bridle wires, the carrier bar and the polished rod.
- The beam rocks from −0.42 to +0.35 rad, set by the crank geometry. The crank turns at 9° a tick scaled by the pump
  rate (at least a fifth of that), so a draining deposit visibly slows it.
- **Hitbox:** the still parts only (skid, plinth, A-frame steps, platform, ladder, reducer, motor, guard, saddle),
  in the unit's part blocks. The beam, head and cranks move, so they're left out.

## Oil Derrick model
- Formed, the rig's Derrick Casings, Derrick Frames and controller hide their faces (`block/oilfield/derrick_hidden`,
  the casings keeping their port plates) and OilDerrickRenderer draws a classic four-legged lattice derrick from
  DerrickLayout, built by DerrickMesh on the Pumpjack's sheet (it has the house steel and the FE band), in the rig's
  footprint, nine blocks tall. Unformed, the casings and frames keep their own textures.
- **A large model** ("Large model texturing"). The parts, from the ground up:
  - The rig floor: a DECK plate (recessed panels, one continuous surface) on short legs and girders in the girder
    flange, open over the hole, a two-step GRATE stair at its front between stringers, and a railing (1 px posts at
    most 8 px apart, a knee rail and a hand rail) round its edge, open at the stair and the ladder.
  - The rotary table: a round table (the ramp across each facet) on a plain base, a ring top and the master bushing's
    dark square socket.
  - The legs: square bars 3 px across, leaning in from the deck's corners to the crown platform's, their faces square
    to the tower's sides. All four faces are plain plates (each a long plate with its own lit and dark edges, so the
    corners read square); one row of rivet heads, every 8 px, runs down the middle of the face on the tower's front
    or back, and never round a corner.
  - Girts every 16 px up all four sides (2 px bars in the girder flange) and X-bracing in every bay (thin round rods
    with the ramp across them), the front's bottom bay left open over the stair, so the tower reads as an open lattice
    you see through.
  - The crown: a GRATE platform on a flanged frame with a railing (open at the ladder's top), the crown block's four
    sheaves (round, the ramp across their rims) between two beams, and a short gin pole leaning back with a sheave at
    its tip.
  - The drawworks at the back of the floor: a PLATE frame with side plates and bearing housings, the drum (the ramp
    across it, flanges either end with bolt heads, a few wraps of dark line standing proud) and a brake-band guard.
  - The engine house beside it: GEN_PLATE walls (riveted generator steel), a lipped roof, the FE band under the eaves,
    louvres (VENT) in its front and side, and a chain guard to the drum's shaft.
  - The ladder up the front left leg: two stiles on stand-offs and a 1 px rung every 4 px, its stiles running on past
    the crown deck as grab rails.
- **Moving parts**, every frame: the hoist lines (dark) from the crown block to the travelling block, which strokes
  down while it drills (a stroke every two seconds); the hook; the square kelly, turning 18° a tick through the rotary
  table; the fast line from the drum to the crown; and the drawworks drum, turning as the line pays out.
- **Hitbox:** the still parts in the rig's blocks: the deck and stair, the legs (in 4 px steps up their lean), the
  crown platform, the drawworks, the engine house and the ladder. The rig has no blocks between its legs above the
  floor, so the tops of the drawworks and engine house have none. The blocks the ladder runs through are climbable.

## Distillation Array GUI (seven products)
- 216×166, the inventory centred with 20 px of plain panel each side. The screen is shortened to y 18–52 (x 40–133)
  with the Pitch output slot frame under it at (40, 54); right of it, seven narrow product tanks (6 px inside, the
  turbines' lubricant gauge), at x 137 + 10k, lightest product on the left. The heat bar is the Firebox's (84×4).
  A product the column doesn't make at its height shows `product_locked`; the fallback fill is `#4E5258` with a `#6A6E74` top row.

## Refinery units, fuels and generators
- **Fluids** (re-toned by rank from Light Oil's still/flow frames, keeping its alpha and `.mcmeta`):
  - Gasoline: a pale straw-orange (`#B27428 #DDA044 #F2C678 #FBE6B8`). Its fog is `#E8A850`.
  - Lubricant: a clear golden amber (`#7A4E10 #B07418 #D89A2A #F2C25A`). Its fog is `#C88A28`.
- **Items:** Petroleum Coke is Coal Coke's lump re-toned by rank onto a darker blue-black, so the two read as kin. The
  Cracking Catalyst is a small heap of zeolite pellets: flat cream tones, each pellet with a lit top-left pixel and a
  shadow under it, inside the item outline.
- **Single-block units** (the machine casing, with the 8×8 ports at their default faces and each product's chip):
  - Catalytic Reformer: three catalyst tubes in a furnace window on its front. `_on` lights the window up the Heat row,
    with a bright band climbing each tube.
  - Coker: a coke drum's bolted door with a viewing slit. `_on` flickers fire in the slit.
  - Lubricant Blender: a vat of amber Lubricant with its mixer. `_on` turns the mixer.
  - Port chips: Naphtha `#F6E0AA` / `#D4AA5C`, Bitumen `#3A3A40` / `#141418`, Heavy Oil `#6A4A2A` / `#2A1A0C`,
    Refinery Gas `#E4E8EE` / `#B4BCC8`, Petroleum Coke `#5A5E66` / `#22252A`.
- **Plant casings and controllers** (`block/refinery/*`, in the oilfield's casing grammar: 2 px bevel, split face at
  x 10, top rivets, the tread deck on top):
  - Cracking Unit: the riveted strap and a small vent.
  - Diesel Generator: an FE band in place of the strap.
  - Controllers: the screen recess with the status LED and a glyph (a cracking column; a sine wave). `_on` lights them.
  - Formed, both hide their faces (`*_hidden`, with the port plates) and a renderer draws the plant (PlantRenderer, on
    ModelMesh like the turbines, shading each face as a block face, as PumpjackRenderer does).
- The three models below are **large models** ("Large model texturing"): their PLATE, PLAIN, DECK, FOOTING and band
  tone faces are continuous plates, CASING shells riveted plates wrapped round them, domed heads and cones bare grain,
  and their round parts carry the ramp across them. The names below are the paints each part is given.
- **Cracking Unit model** (CrackingUnitLayout and CrackingUnitMesh, on the refinery tower's sheet,
  `block/distillation_array/refinery`): a fluid catalytic cracker in the 3×3×5 box.
  - The base deck: deck plates over the footprint on a FOOTING, with a MID lip.
  - The regenerator, the big vessel at the back: a banded base ring, a riveted CASING shell in two courses with a
    bolted flange between, a banded elliptical head, and a flue stack with a lip rising past the top of the box.
  - The sight port on the regenerator's front: a steel frame standing proud, a DARK pane, and the catalyst bed's GLOW
    over it in the glow list, as bright as (T − 150) / 450 of the unit's temperature.
  - The reactor at the front right: a skirt, the stripper, a stepped cone up to the wider disengager and its head, with
    flanges at the joints.
  - The riser: a wye at its foot, the pipe up the front and its crossover into the disengager's side.
  - The standpipes and the hopper's feed line, each with a slide valve (a body and its actuator) halfway down.
  - The fresh-catalyst hopper: four braced legs, a body with a lid, and a three-step cone to its feed line.
  - The vapour line from the reactor's head across to the right edge and down to the deck, flanged at each turn.
  - Port nozzles are PLAIN collars 10 px across, as the turbines'.
  - **Hitbox:** each block's shape is the model's still parts in it (PlantHitbox), with the port plates added. Empty
    cells on the ground get the floor plate; empty cells above it are empty.
- **Diesel Generator model** (DieselGeneratorLayout and DieselGeneratorMesh, on the Pumpjack's sheet for its
  generator steel, FE band, louvres and lit screen): a large genset (a Cat or Cummins set) 3 wide and 3 tall, its length
  setting the engine (5 long a V8, 7 a V12, 9 a V16). Front to back:
  - The base: a sub-base fuel tank (plain plates, a seam band round its top, a filler neck and a level gauge) and the
    base frame's two rails in the girder flange, with the engine's mounts and the alternator's feet.
  - The radiator: the core's fins (GRATE, laid at 1:1) facing forward between plain side channels, header tanks across
    its top and bottom (round, the ramp across them), a filler cap; behind it the fan's shroud (plated outside, SHADE
    inside) and the guard: three rings and eight spokes of thin rod, so the fan shows through; stays to the engine.
  - The V engine: crankcase and oil pan with a pan-rail flange, a front cover, and two banks leaning 30° out of the V,
    each a plain plated box with a flange line at the head and a rocker cover per cylinder (4, 6 or 8 a side) with two
    bolts; the intake manifold down the V; an exhaust manifold along the outside of each bank (round) with a runner to
    each cylinder; the turbochargers over the banks at the flywheel end (two, four on the V16: a turbine scroll and a
    smaller compressor, round), piped into the aftercooler (a PLATE box across the V) and up through risers into the
    silencer (round, flanged bands near its ends) along the top, its outlet capped by a round rain flap hinged at its
    back, which flaps up and down on the exhaust pulses while it runs.
  - The flywheel housing (round, a tapered front, a bolt circle) and the coupling.
  - The alternator in the generator steel (GEN_PLATE, GEN_PLAIN) with two banks of louvres down each side, the FE
    band round its middle and lips round its ends, its end bell and bearing cap, a terminal box on top with three FE red
    cable glands, and at its right side the control panel on two arms: a generator-steel box with its door's frame,
    the lit screen (`M_SCREEN`, in the glow list, always lit) and three buttons.
  - Moving parts, drawn each frame about the shaft: the fan's six blades and hub (and the shaft to the engine's nose),
    and the coupling with its bolt heads. They turn at 30° a tick times the engine's speed, so spin-up and coast-down
    show.
  - **Hitbox:** the still parts only, as for the Pumpjack.
- **Steam Cracker model** (SteamCrackerLayout and SteamCrackerMesh, on the refinery tower's sheet), in its 3×3×3 box:
  - The deck: DECK plates on a FOOTING.
  - The furnace box: welded plain wall plates with a plinth lip and a cornice, buckstays (2 px columns in the girder
    flange, 1 px proud) up its walls, a tie band round them; peep holes along its front and left side (a steel frame
    standing proud, the refractory brick `M_BRICK` round the reveal, and the fire `M_FIRE` inside in the glow list,
    as bright as (T − 300) / 600 of the furnace's temperature); the burners' housings and fuel line along its foot.
  - On the roof: the radiant tubes (thin round rods with collars) rising into two banded headers along it, joined by a
    collector across their front ends, and at the back the convection hood (PLATE) with a short stack (round, banded,
    a lip at its top).
  - The transfer line from the collector across to the quench drum: a round pipe, each turn an elbow with a flanged
    ring either side.
  - The quench drum at the right: a skirt, a riveted CASING shell in two courses with a flange between, an elliptical
    head, and a level gauge on stand-offs.
  - **Hitbox:** the still parts only.
- **Flare Stack** (`block/flare_stack/side`, `top`, `bottom`): the base block, with Input ports (Refinery Gas chip) on
  its sides and bottom and the stack's seat (a recessed ring) on top.
  - FlareStackMesh (on the refinery sheet) draws the stack above it: a square base flange over the socket (bolted at
    its corners, covering the socket's square recess), a round seat on it, a banded pipe in three flanged lengths up to
    74 px, and the tip up to 80 px in a wind shield of plates.
  - The pilot line runs up the north side. A GRATE service platform at 62 px has a kick plate, railings and struts to
    its corners, with a ladder up the south side on stand-offs: two 0.8 px square stiles 9 px apart, closed at the top,
    and a thinner rung every 4 px, the railing open above it.
- **Flare flame** (`block/flare_stack/flame`, 8 frames): a tongue of fire in the Heat row with a pale core, on black.
  - It is drawn additively and full-bright on two crossed sheets (RenderTypes.eyes), from a 5 px pilot up to 32 px
    as the stack burns more.
  - Never use blue: it's a hydrocarbon flare.
- **GUIs:**
  - Refining machines (`gui/container/cracking_unit`, `catalytic_reformer`, `coker`, `lubricant_blender`): the
    Fischer-Tropsch Reactor's 176×186 frame, with the band above the inventory redrawn to hold only the parts each
    machine has:
    - the FE gauge (Blender only);
    - the feed tank;
    - the catalyst slot (Cracking Unit; a white pellet ghost at the ghost alphas `0x69` rim / `0x37` body);
    - the arrow;
    - one tank per product;
    - the Coker's coke slot (orange, an output);
    - the heat buffer and heat bar on the heated ones, with a tick at the recipe's least temperature.
  - Diesel Generator (`gui/container/diesel_generator`): the Pumpjack's frame without its bucket slots. The screen runs
    across to the fuel tank, with a speed bar under it in the accent colour. The tank fill falls back to an amber
    hatch (`fuel_fill`) when the fluid sprite isn't there.

## Hydrocracking, Synthetic Rubber and the Coker's Heavy Oil
- **Synthetic Rubber** (`item/synthetic_rubber`): Raw Rubber's crepe sheet (its diagonal ribs and turned-up corner) with
  its four tones moved by rank onto a cool slate (`#8C93A3 #636A79 #4E5462 #3A3F4B`) in the item outline, so it reads as
  the same uncured sheet, made rather than tapped, and apart from cured Rubber's grey slab.
- **Cracking Unit Mode tab** (GUI icons, 16×16: flat fills with a hue-matched 1 px outline, drawn on the widget/button
  nine-slice as the Redstone tab's, the selected one pressed with the ACCENT outline):
  - `icon_cracking_mode` (the tab): a steel cracking column (the Pressure icon's steel: `#9AA0A8` lit, `#646A72` body,
    `#3E444B` shade, `#16181B` outline) with a sight window in the Heat row (`#FFB02E` / `#F26A16`) and two draw-offs.
  - `cracking_catalytic`: three zeolite pellets as on the Cracking Catalyst (`#FFF6D8 #E8DDB8 #B8A878`, outline
    `#4A3E22`), each lit top-left with a shade step bottom-right.
  - `cracking_hydrocracking`: a hydrogen molecule, two 6×6 atoms (`#E2EEFF #A8CCF2 #6A9AD2`, outline `#1E3A66`) joined
    by a 2 px bond (`#4A78B2`).
- **GUIs** (cut from existing backgrounds): the Cracking Unit gains the Steam Cracker's second feed tank frame at x 25
  (Hydrogen) and a fourth product tank at x 137; the Coker gains a second product tank (Heavy Oil) at x 105, copied from
  its first, and its coke slot moves 16 px right to x 125 (a refining machine's item slot sits 4 px right of its last
  product tank).

## Nuclear tier
- **Natural materials** (the ore method, "Natural materials" above: Cody's ore, raw item, raw block, dust, crystal,
  ingot, plate and block textures recoloured by brightness onto each ramp). New ramps:
  - Uraninite (pitchblende): pitch black with a greasy dark green sheen, `#090B08 #151C12 #23301C #506440`.
    Keep it dark and green: browner, it reads olive; brighter, it reads as moss.
  - Galena: metallic blue-grey cubes, `#2A303A #5A6474 #94A0B2 #D8E0EC`.
  - Lead (metal): a dull blue-grey, `#2E3440 #4C5462 #717A8A #A2AAB8`; its block is the banded block smoothed.
  - Monazite honey-brown (`#5A2E10 … #F8D890`) and Zircon pale gold-brown (`#6A4A2A … #FFF0D0`). Their crystal items
    are the Iolite Crystal's own shape (`iolite_crystal.py`: the long shard, the short point and the clustered base),
    its seven tones on each one's ramp; not the shared crystal item.
  - Depleted Uranium: a dark green-tinted gunmetal, `#1C2620 #324236 #4E6854 #82A088`.
  - The uranium line's powders as dusts: Ammonium Diuranate orange-yellow, Yellowcake a green-leaning yellow
    (`#6A6008 … #F6FAA6`), Uranium Dioxide green-black (`#12160E … #687C52`), Uranium Tetrafluoride (green salt)
    green. Everything uranium leans green.
  - **Heavy Mineral Sand**: the ore method on sand. Cody's iridescent ore's clumps, the stone bevels dropped, each clump
    black, garnet-red or honey, laid over the real vanilla sand. Its particle is a dark sand of flat blotches.
- **Fluids**: Uranyl Sulfate Solution (a bright uranium yellow) and Hydrofluoric Acid (water-clear with a cold grey
  tint) are Hydrochloric Acid's frames re-toned by rank, keeping its alpha and `.mcmeta`. The gases are tints
  (GasTints): Fluorine a pale yellow, UF6 an off-white, depleted UF6 a grey.
- **Building blocks**: Lead Bricks are the Arcforge Furnace bricks recoloured by rank onto the lead ramp (the mortar
  its two darkest tones). Concrete Shielding is concrete in three close light greys as flat blotches, with the rebar
  ends showing on an 8 px grid (a rust square, lit top-left).
- **Items** (outline `#0C0D0F`, 4–6 flat tones): the Geiger Counter (a hazard-yellow case, its dial, the probe on its
  cord), the Dosimeter (a steel badge with a clip and a cyan readout), the Lead Box (a lead case with latches and a
  hazard label) and the Gas Centrifuge (a slim column on its foot with three connections).
- **Hazmat Suit**: Cody's steel armour items and worn layers recoloured onto a safety-orange rubber ramp of close,
  even steps (`#7A3E10 #94501A #AC6220 #C27426 #D4862E #E29A3C #EEAE52 #F6C470`), each colour placed by its
  brightness (not its rank), so the steel's soft shading carries over and no step jumps. No near-black: items take a
  dark-orange outline (`#4A2408`) and the worn layers the ramp from its second tone up. Never yellow-gold, or it reads
  as gold armour. The helmet item has a dark visor; the worn head layer is a full hood (top and four sides closed,
  flat, its lowest row a step darker) with the visor across the face.
- **Gas Centrifuge Cascade blocks**: the casing is the wall plate with a recessed louvre band (the plenum under the
  raised floor), its top the tread deck with perforations. The controller is the casing with a screen recess: the
  status LED and three cyan centrifuge columns. The loose Gas Centrifuge is a 16×16 sheet for its block model at 1:1
  (the casing's column bands in the round-part profile, a dark band near its foot, the cap, the foot's deck).
- **Port: Tails**: the output ring with the depleted gas's grey chip, and its Ports-tab face icon.
- **Gas Centrifuge Cascade model** (a large model, "Large model texturing", on the refinery sheet; CentrifugeCascadeMesh
  from CentrifugeCascadeLayout). Formed, the hall's blocks hide and the renderer draws:
  - the raised floor: a deep FOOTING plinth under a 2 px DECK, with a perforated grating tile under each centrifuge;
  - each centrifuge, a slim column: a square foot, the motor housing, a POLISHED rotor casing (radius 4 px) up to its
    cap, and a band round it low down (y 25–27) that shows a spinning glow mark while the cascade runs (45° a tick,
    each column a little out of step with the next);
  - header pipework along the top of each row: three pipes (feed, product, tails) at y 46 running across the row with
    capped ends, a riser from each cap up into each;
  - the control panel on the controller's block at the front edge: a GEN_PLAIN cabinet on a sill, VENT louvres low on
    its front and a lit screen (M_SCREEN, in the glow list) facing out of the hall;
  - the port nozzles on the casings set as ports.
  - Its sound is a faint, low hum while it runs.
- **GUIs**:
  - Gas Centrifuge Cascade (`gui/container/centrifuge_cascade`, 216×186, the inventory 104 down, centred with 20 px of
    plain panel each side): the Battery Array's frame, widened. The FE gauge on the left, the house screen in the
    middle (status, feed, target and tails with − / + buttons, the feed and SWU a mB takes), and the feed, product and
    tails tanks (12×72) on the right; the FE gauge is as tall as the tanks. Sprites: the Diesel Generator's energy bar
    carried up to 10×72 (its stripes repeated) and LEDs, a 72-tall tank gauge, a gas fill, and the 7×7 − / + buttons
    (hovered: a teal face with a cyan glyph).
  - Lead Box (`gui/container/lead_box`, 176×166): a 3×3 grid of slots in a lead-grey tray (`#3D4451`) over the player
    inventory.

## Fuel fabrication and the Fission Reactor
- **Natural materials** (the ore method): Zirconium Dust and Zirconium Tetrachloride as Cody's dust, Zirconium and Zircaloy
  ingots as his ingot. Zirconium a silvery grey with a faint warm cast (`#3E3C3A #6E6C68 #AAA8A2 #ECEAE4`); Zircaloy a
  step cooler (`#30363C #5A646C #929CA4 #D6DEE4`); the tetrachloride a white powder (`#8C8C86 … #FAFAF4`).
- **Rods** use the rod level map (`rod_levels`): the Zircaloy Tube on the Zircaloy item ramp (`#3A4046 #4E565E #6E7880
  #98A2AA #C8D0D6`) with a dark bore at its cut end; the Fuel Rod the same with a uranium-green band near its top and
  a dark end plug collar; the Control Rod on the silver ramp with two dark absorber bands.
- **Pellets**: three small cylinders (two standing, one in front), each lit on its top face and down its left, outline
  `#0C0D0F`. Green (pressed) pellets a dull green-grey (`#3E4A3A … #A8B6A0`); sintered Fuel Pellets a dark glossy
  green-black (`#121A12 … #7A9A62`). The **Pellet Die** is the plate die with four round pellet cavities engraved in its
  face (the die engraving rules) and its strip uranium green.
- **Blocks** (machine steel ramp, `oil.casing`): Reactor Casing has a recessed centre panel with a hazard-yellow plate and
  the black trefoil; its top is the plain recessed panel. Reactor Glass: the casing's 2 px bevel frame round a translucent
  cold blue-grey pane (alpha ~92) with an inner lip and glints top-left. Controllers are the casing with a screen recess
  (the cyan LED, then a k-meter bar, a power trace and a red SCRAM button; a drum level glass and a pressure dial; a
  tray column and assay bars) over a louvre band. Fuel and Control Rod Channels are an open lattice frame (2 px posts and
  rails, a spacer grid across the middle) round what stands in it: a Zircaloy tube, or a dark absorber rod with a band in
  its bank's colour (A red `#C83A2A`, B amber `#E0A020`, C green `#3AA058`, D blue `#3A78D0`); their tops a tube-sheet
  ring round the bore. Graphite Moderator: stacked bricks in three close near-black matte greys (`#26292C … #42474C`),
  each lit top-left; its top bored through. Steel Reflector: a machined face turned in concentric rings. Steam Generator
  Tubes: close pale Inconel tubes behind a support plate; their top a tube sheet of holes. Exchange Tray: a tray deck
  recess with its weir and downcomer slot; its top the perforated deck.
- **Fluids**: Heavy Water (a deeper, cooler blue than water), Hot Coolant (a pale steamy blue) and Hot Heavy Water are
  Hydrochloric Acid's frames re-toned by rank. Contaminated Steam is a gas tint, `#DCF0EC` (a faint green cast on white).
- **Port: Hot Leg**: the output ring with an orange-red chip (`#F49A6A` / `#C04A2A`) and its face icon.
- **Large models** ("Large model texturing", on the refinery sheet; VesselMesh). Formed, the vessels' walls and the blocks
  inside them hide and the renderer draws:
  - every vessel stands on a plinth (FOOTING under a 2 px DECK); the reactor and Steam Generator stand in a casing frame of
    4 px plain posts up the corners and beams along the top edges. Round shells are CASING (plated unrolled, riveted),
    tapering heads bare grain, flanges banded with a plated ring top. The controller's block carries a GEN_PLAIN control
    cabinet with VENT louvres and a lit M_SCREEN.
  - **Fission Reactor**: a skirt, the bottom head, the plated shell, a bolted flange pair, a 2:1 elliptical closure head
    and control-rod drive housings in a 6 px grid on it (slim banded housings, POLISHED latch housings with flat caps), a
    seismic support ring round them. Each pane of Reactor Glass in the side walls is a viewport in the shell facing it: a
    bolted boss with the core seen dark through it (M_CORE_DARK, 40..55×96: blue-black water between two fuel channels),
    and while the reactor is critical the Cherenkov glow over it (M_CORE_LIT, 24..39×96, full-bright: the water stepping
    up from the channels' edges to a pale blue middle, the channels a deeper blue, spacer grids a step lighter; both tiles
    seamless and never dithered). Roof and floor glass have no viewport.
  - **Steam Generator**: a skirt, a hemispherical channel head, the tubesheet flange, the tube-bundle shell, a conical
    transition out to the wider steam drum (with a manway boss facing front), a girth band, the elliptical top head and
    the steam outlet standing on its crown.
  - **Heavy Water Tower**: squat, filling most of its footprint: a skirt, the bottom head, the shell in plated sections a
    block tall with a flange at each tray seam, the top head and gas outlet, and the hydrogen sulfide loop pipe down its
    back left (seen from the front), in at the lowest and highest sections.
- **GUIs**: Fission Reactor (`gui/container/fission_reactor`, 176×254, the inventory 172 down): one big screen
  (6..169×15..119) and the nine rod input and nine output slots under it. Sprites: the bank fill (a steel bar with dark
  absorber lines) and target marker (cyan), the − / + buttons, SCRAM (red), RESET and the page arrow (each hovered, the
  cascade's button style), toggles on (teal) and off. Steam Generator (176×166): four 12×52 tank recesses and the screen
  between. Heavy Water Tower (176×166): the FE gauge, three input tanks, the screen with a progress track, the output
  tank. Tank gauges 52 tall, a water and a gas fill, the Diesel Generator's LEDs and energy bar.

## Containment, waste and reprocessing
- **Concrete**: Containment Concrete is poured concrete a step darker and cooler than Concrete Shielding (`#8E908C …
  #AEB0AC`, flat blotches), with the formwork's tie holes on an 8 px grid (a dark socket lit on its bottom-right lip) and a
  faint board-form joint across the middle; no rebar. The Containment Dome is stairs on it whose step is a quarter round
  (four 4 px bands rising 6, 11, 14, 16 px), plain geometry, no painted curve.
- **Steel faces** (`oil.casing`, the machine steel ramp). The Steel Liner is one stainless plate with a weld bead across
  it (a run of lit scallops over its shadow) and two leak-chase test points. The Airlock is a thick door slab: the lower
  half has a handwheel and a hazard band at its foot, the upper a bolted round porthole of amber lead glass with its glint.
  The Penetration's face is the sleeve plate with a flanged pipe through it (bolt circle, flange ring lit top-left, dark
  bore); its sides the liner with a raised collar band. Drain Tank and Geological Repository tops take the hazard casing.
- **Shaped blocks** paint each face to the region their element shows, so the block UVs lay it at 1:1 (Hydrogen
  Recombiner: a housing with catalyst plates in pale platinum greys over inlet louvres, its top an outlet grille; Relief
  Valve: a steel body under a red spring bonnet `#5A1A12 … #EC8A62` with its lever; ECCS Accumulator: a plated tank with
  a water sight glass and the ECCS green band `#1E5A34 #2E7A48 #46A060`; Decontamination Shower: a box with a green and
  white decon band, its underside a grid of nozzles; Low-Level Waste Drum: a painted drum on the round-part profile in
  `#6A5208 … #F0CC50` with two hoops and a label; Dry Cask: concrete with a steel lid band and inlet vents).
- **Core Catcher**: the furnace bricks re-toned by rank to a refractory cream (`#6A4A30 … #EED8BA`).
- **Corium and fallout** (natural materials, vanilla's language). Corium the fluid is Cody's lava texture pixel for pixel
  (the 16x16 under his reference), each pixel recoloured by a gradient map, not a tint: its brightness (normalised
  over the tile) picks a colour on a brown ramp warming as it brightens (`#4A2410 #663218 #80431E #9A5726 #B46E30
  #CE8C40 #EAB660`), so the pattern and its shading are exactly his. It
  animates by scrolling that exact tile down a px a frame (still: 16 frames, frametime 4, interpolated; flow: the tile
  2x2 at 32x32, 16 frames, frametime 2); the block's light falls as it cools. Solidified Corium is a dark glassy slag (`#16120F … #4A3A2C`) with dark
  cracks, three holding a dull ember. Fallout is grey ash in vanilla sand's grain (`#5C5C58 … #A3A39C`: a mid-grey field
  of single grains and small clumps, a few lighter and darker specks), laid as snow layers (2 px a layer, the full block
  at 8), the same texture on every face.
- **Radiation Poisoning icon** (18x18): Cody's pixel trefoil (a disc, three blades round a diamond hub, black outline) in
  green, the disc lit top-left (`#96E848`) and a step darker bottom-right (`#6CC832`), flat. The air over contaminated
  ground shows vanilla dust particles tinted radiation green (`#7CFF4A`, `#5FE03A`, `#A8FF7A`), and vanilla ash.
- **Spent Fuel Rack** model: a 2 px base plate and 1 px stainless walls round four cells, the walls' faces holding two
  dark neutron-absorber panel slots. The rods and their glow are SpentFuelRackRenderer's, laid at 1:1: `rack_rod` (columns
  0-2 the Zircaloy ramp across the rod, a darker end-plug collar and a spacer band; its top the plug) and `cherenkov`
  (columns 0-5 blue `#123A78 #2A64C0 #4C8EEA` brightest in the middle, its ends a step dimmer, drawn with the eyes type so
  black adds nothing).
- **Machines** (the Metal Press's front casing, its window repainted): the Waste Compactor's ram over a crushed yellow
  drum (running, the ram strokes down and up over 8 frames); the Vitrification Plant's refractory melter over a steel
  canister, its pour stream glowing (running, flickering). Their other faces are the Metal Press's and the Chemical
  Reactor's.
- **PUREX blocks**: Hot Cell Casing is the containment concrete in the casing's 2 px bevel frame with corner rivets; the
  Hot Cell Window a deep stepped steel frame (3 px) round a warm amber lead-glass pane (`#B08A3A`, alpha ~120) with its
  inner lip and glints; the controller the casing's screen recess with four cyan contactor columns and the split bars.
- **Salt reactor blocks**: the Freeze Plug is a pipe section wound with blue cooling coils (dark and its seam glowing
  orange when melted), its end the bore holding pale green frozen salt (`#5E7A5A … #E6F2DC`) or glowing orange; the
  Salt Processing Unit the strapped casing with a small amber viewing port; the Drain Tank the hazard casing with the
  trefoil.
- **Fluids** (Hydrochloric Acid's frames re-toned by rank): Nitric Acid a pale fuming yellow; High-Level Liquid Waste a
  dark brown liquor; Lead-Bismuth a molten blue-grey and hot, a dull red-orange; Fuel Salt a pale green and hot, a warm
  amber; the metals and the waste opaque. Nitrogen Dioxide is a gas tint, `#C8743C` (brown-orange).
- **Items** (outline `#0C0D0F`): MOX, Thorium Blanket and U-233 rods are the Fuel Rod with an olive-khaki band, two pale
  bands, or a teal band; pellets are the Fuel Pellet's shape in green-then-sintered pairs (MOX olive to near-black,
  thorium pale grey to ivory, U-233 grey-teal to dark teal). The powders are Cody's dust by the ore method (Recovered
  Uranium olive, Plutonium Oxide olive-khaki, Depleted UO2 dark gunmetal, MOX Blend olive, Thorium Dioxide and the
  fluorides near-white, Rare Earth Dust a pinkish grey, U-233 Oxide teal-green), Lead-Bismuth his ingot (`#2E3038 …
  #C4C2CE`). The HEPA Filter is pleated white media in a steel frame (used: grey-brown with a yellow tag); the Waste Drum a
  steel drum; the Vitrified Waste Log a tall stainless canister with a yellow label band and its dark glass at the fill
  port; the Airlock and Equipment Hatch flat icons of their doors.
- **Large models** ("Large model texturing"):
  - **Equipment Hatch** (on the refinery sheet, 3x3 across a block-thick wall): the plated steel sleeve round the
    opening, a banded flange ring proud of each face with a bolt circle outside, the hinge's knuckles on brackets; the
    door a thick round plate (PLATE face, banded edge) with a handwheel on its boss, swung open about its hinge.
  - **PUREX Plant**: its walls stay blocks; through its windows the cell is drawn full-bright: a deck floor, a lamp over
    every block of ceiling (M_LAMP, 16x8 at 96,96, in the glow list: two warm-white tubes, a bright core row over a
    softer one, between a light reflector, dark rims and tube holders, laid from the face's top-left), the plated
    dissolver with its domed head, a bank of banded contactors and three pipework lines on brackets along the back wall.
    At each window a master-slave manipulator: a banded tube through the wall above it, inside a polished slave arm with
    its tongs, outside the master arm and a generator-steel grip with a red trigger.
  - **Spent Fuel Pool racks**: the rack model above, its rods' Cherenkov glow full-bright while hot.
  - **Corium**: the fluid above, glowing orange-red with its dark crust.
- **GUIs**: Waste Compactor and Vitrification Plant (176x166) from the Metal Press's pieces (its FE recess, blue input and
  grey slots, the arrow and orange output); the Vitrification Plant adds a 12x50 tank. PUREX Plant (176x204, inventory 122
  down): FE gauge, acid and solvent tanks, the screen with its mode button (42x12, the reactor's button style) and progress
  track, the waste tank, 2x2 blue rod inputs and 3x2 orange outputs. Geological Repository (176x166): a screen and one
  blue slot. Ghost sprites (a drum, a glass pane) in the Metal Press's ghost tone; the reactor's Silence button amber.
