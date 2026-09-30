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
    (alternate clusters purple and green); iridescent → bismuth (rainbow ramp).
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
