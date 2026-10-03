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
| Hazard stripe | `#E0B020` / `#1A1A1A` |
| Furnace brick (firebox and furnace interiors) | `#553F34`, `#3A2C23`, `#2B211B` |

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

### Ports: one design, two sizes

- **Layers, outside in:**
  - a steel collar with a 1 px bevel;
  - a 1 px coloured ring:
    - blue = in;
    - orange = out;
    - red = FE;
    - hot orange = heat (the Heat row);
  - a dark bore;
  - a resource-colour chip.
  Everything outside the collar is transparent.
- **Multiblocks: 10×10 (`block/port/<mode>`).**
  - Collar at x3..12, bore 6×6 (x5..10), chip 4×4.
  - Drawn by PortOverlays on the faces set with the Wrench.
  - New structures start with **no ports**.
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
    (alternate clusters purple and green); iridescent → bismuth (rainbow ramp); cream → halite (pinkish rock salt).
  - Use the same method for any future ore.
- **Raw items:** Cody's own raw item textures, recoloured per ore by brightness onto the ore's 7-tone ramp,
  keeping every pixel and his outline shading.
  - Sources: blue → silver and arcite; light → nickel and bismuth (rainbow ramp); dark → tungsten
    (wolframite); green → fluorite (purple, with the lower-right lobe green).
  - Use the same method for any future raw item.
- **Raw blocks:** Cody's own raw-block textures, recoloured per ore by brightness onto the ore's 7-tone ramp,
  keeping every pixel, lump and crease of his art. Sources: bluegrey → silver, grey → nickel and tungsten,
  green → fluorite, teal → bismuth (rainbow ramp) and arcite (dimmed cyan). Use the same method for any
  future raw block.
- **Crystals and crystal blocks:** Cody's own crystal item and crystal block textures, recoloured per ore by
  brightness onto the ore's 7-tone ramp. Fluorite is purple with a green crystal / green patches; arcite is
  cyan. Use the same method for any future crystal.
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

## Meters, Chargepad, Conduit Cover, oxy-fuel, advancements
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
- Conduit Cover (default look): a translucent pane.
  - Fill: `#DCE8EE` at alpha 40, with a `#DCE8EE` / `#A9BCC6` inner ring at alpha 140.
  - A crisp opaque 1 px outline: `#727982` on the top and left, `#2B2F34` on the bottom and right.
  - Two glass glints.
  - Rendered translucent; a copied look uses that block's own model.
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
- Advancement tab background: the wall plate one step darker (`#40454C` / `#383C42` split at x = 10) with
  the riveted strap on rows 13–15.

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

## Reservoir, Liquid Experience, Quantum Tunnel, Chunk Loader
- **Reservoir** (`block/reservoir/`): clear glass (alpha 0) with small glints like vanilla glass (a 3 px `#DCE8EE` streak
  and a 2 px `#A9BCC6` one beside it near the top left, a 2 px `#A9BCC6` one near the bottom right), inside a 2 px
  machine-steel frame round the face's edges, kept light so the corners don't go black: `#959DA6` then `#727982` on the top
  and left, `#575D65` then `#474C53` on the bottom and right, `#727982` where lit meets shaded. Connected like the other
  CTM blocks, by quadrant (ConnectedModel "reservoir", ReservoirModel): each 8×8 quadrant of a face comes from one of five
  textures by which of its two edges carry the frame: `frame` (both: an outer corner), `frame_h` (top or bottom only),
  `frame_v` (left or right only), `glass` (neither) or `frame_corners` (neither, but the frame turns there: the 2×2 corner
  of `frame`, closing an inner corner). An edge where two Reservoirs join drops its frame; a concave edge keeps it. Faces
  between Reservoirs aren't drawn. The liquid is ReservoirRenderer's: one body at the shared level, run to the block edge
  toward a neighbour with the same liquid, full-bright for glowing fluids. Nothing is painted into the glass.
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
- **Bigger Steam Boiler and Turbine Arrays** use the existing shell textures; nothing new is painted. A wall casing leaves
  out its face toward the hollow inside however big the shell is, so the liner and reveal (`ctm/shell_liner`,
  `ctm/shell_jamb`) still line the inside. The turbine's rotor model is scaled to the cross-section's shorter side.

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
