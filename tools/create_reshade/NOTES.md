# Create-style reshade: notes

Experimental branch `experiment/create-shading` only. This deliberately breaks TEXTURE_STYLE.md rules 1.2
and 1.6 and the 8-tone ramp, and suspends CLAUDE.md's "never add gradients" rule for this branch.

```
python tools/create_reshade/reshade_all.py [--dry-run] [--only "block/ore/*"] [--review build/reshade_review]
python tools/conduit_dye_gen.py .        # then regenerate the _dyed conduits
git checkout -- src/main/resources/assets/arcforge/textures/block/conduit/dye   # masks: pixel-identical, re-encoded
```

Sources are read from git at `--base` (default `0c78357`, the last commit with the flat shading), so a
rerun never reshades twice. Running the whole pipeline twice gives byte-identical files. On a real run,
any skipped texture that differs from `--base` is put back. A texture added after that commit is read from
disk and logged, and would compound if run twice, so pass a newer `--base` then.

## Result (last run)

| | files |
|---|---|
| machine mode | 392 (46 `top`, 61 without grain) |
| general mode | 553 |
| skipped | 262 |
| `_dyed` conduits regenerated | 32 |

Skipped, by reason:
- **GUI, 187 files:** 95 bars, gauges and panels over 32 px; 45 panel backgrounds; 35 markers, ticks,
  segments, pips and badges of 6 px or less; 6 nine-slice widgets; 6 side tabs.
- **Regenerated or tint targets, 50 files:** 32 `_dyed` conduits (regenerated), 7 dye masks, 4 thermal
  conduit glows, 4 greyscale fluids (slurry, steam) and the solar receiver.
- **Other, 27 files:** 15 treated wood, 5 emissive layers, 4 particles, 3 renderer contents.

## Revisions after the first in-game look

- **Treated wood is kept as originally shaded:**
  - the logs, stripped logs, planks, door and trapdoor (the fence, stairs, slab and button use the planks);
  - the Compost Bin's wood faces (the compost heap inside is still reshaded);
  - the Trellis block and item, and the Treated Door item.
- **Toned-down highlights.**
  - The bright white machine edges came from `#959DA6` → `#B4BEC3` and the `#DDE5E8` glints.
  - Arcforge `#727982` now maps to ramp step 9 and `#959DA6` to step 10, close to their original brightness
    (they were 10 and 11).
  - Spec rows use step 10 and glints step 11. The near-white step 12 is no longer used.
- **Softer face roll.** The cylinder profile runs over ramp steps 5–8 instead of 4–9, so each 2 px column
  pair changes by one step at most. The jump from the lit left side to the dark edge was too abrupt on
  machine tops.
- **Model UV atlases roll per patch.**
  - Only 16 px tiles roll across the full tile width, which is what connected textures need.
  - The 48 px `formed_*` sheets of the Arc Crushing, Induction Furnace and Metal Pressing Arrays are UV
    atlases. Rolling the whole sheet gave each patch (press heads, body, top cap) an arbitrary slice of one
    big profile, so neighbouring faces and the three press heads shaded inconsistently.
  - The same applies to the other 24–32 px model sheets (rollers, press window, chamber, control panel).

## Tint targets found

- Model `tintindex` (resolved through parents and `#refs`, checked on every run):
  - Index 1: `block/conduit/dye/*` (ConduitDyeModel).
  - Index 0: the 57 faces are the thermal conduits' `#glow` and `#conduit` faces. They resolve to
    `*_thermal_glow` (greyscale, skipped) and `*_thermal_off`.
  - `ConduitTints` returns white unless the conduit is lit, and lit models carry the `_glow` layer, so
    `_thermal_off` always renders in its own colours. It is reshaded (the `TINT_ALWAYS_WHITE` exemption).
- Tinted in code:
  - The slurry and steam fluids (`FluidTintSources.constant`; steam is shared by the grades, Exhaust Steam,
    Hydrogen and Oxygen).
  - `solar_thermal_array/model_receiver` (`SolarThermalArrayRenderer`, tinted by temperature).
  - The gas turbine flame is `combustor_glow`, already skipped as emissive.
  - Boiler and turbine contents use fluid sprites.
  - Conduit item `tints` are index 0 = constant white and index 1 = dye.

## Changes to the prototype algorithm

reshade.py is the prototype's algorithm, tidied (pixel access, no dead code), with these deliberate
changes (on top of the revisions above). Each one was needed for an invariant or came out of the review.

1. **Outline pixels are fixed in machine mode too.** The grey remap moved `#0C0D0F` to `#0F1217`.
2. **Machine accent emboss: the tile edge counts as the same shape.** The prototype embossed every
   coloured shape touching the tile edge, which gave lit lines on tiling faces.
3. **Spec rows look at the tile's bottom row above row 0** (what tiling puts there). Otherwise a lit band
   split across the seam (distillation and solar casings, carbonizer tops) got a spec line down the seam.
4. **Generic emboss: past the tile edge the texture continues**, for the "other side is inside" test.
   Before, a boundary one pixel in from the edge lost its emboss only at the edge.
5. **Shape membership vs embossing (machine mode).** Any non-grey pixel belongs to a shape, but only
   saturated (≥ 0.45) pixels are embossed. The prototype used saturation for both, so the pale hot pixels
   inside glow panels (Geothermal, Induction Furnace, Fuel Burner) counted as outside, and every
   neighbour got a ring: speckle. Muted brick still keeps its art.
6. **Neutral hue.** Generic mode pulls only true neutrals (chroma < 6) to the steel hue. Faintly tinted
   highlights keep their hue: the Bismuth block's near-white pinks were turning blue-grey.
7. **One contrast pivot per animation.** Generic mode's median is taken over the whole strip; each frame
   is still processed on its own. The fluids' medians vary 7–18 luminance between frames, which would
   have shimmered.
8. **`keep` colours.** Conduits leave the exact colours `conduit_dye_gen.py` cuts out untouched. Those are
   its `REGIONS` (dye stripes) and `GLASS` tones. Otherwise the dyed conduits would keep their stripe and
   the dye masks would come out empty. The regenerated masks are pixel-identical to the originals.

## Routing judgment calls

- **Plants get no edge emboss** (`*_stage*`, `hops_vine*`, `wild_*`). On 1–2 px leaves and stems, every
  chunk got its own lit and dark edge (Flax read as noise), and the pale hops cones clipped to white.
  They get the hue-shifted ramp only.
- **GUI sprites 6 px or thinner are skipped**, except the 6×6 status LEDs:
  - These are heat markers, threshold/temperature ticks, throttle segments, insulation pips, module dots,
    jetpack fill strips, sun/moon/collector pips and the 8×6 filter match badges.
  - The pure-white markers turned grey, the badges' `#3C3C3C` plate speckled, and several are exact
    colours drawn in code (`#5FD4C4`, `#E0E0E0`).
  - The LEDs shade well as pillows and are reshaded.
- **GUI sprites over 32 px are skipped** (energy/heat bars, tank gauges, fills, HUD panels). They're
  bars sitting on code-drawn panels, not icons.
- **Side tabs** (`widget/arcforge/tab*`) are skipped as the tab colours matched by SideTabPanel.
  The `face_*` side-config widgets are reshaded: their colours only coincide with tooltip text colours.
- **`arcforge_furnace_*` goes to machine mode** with the multiblocks. The bricks come out unchanged
  (muted material, no steel greys), and only the port's opening and fire are shaded.
- **Numbered fill-level sets** (energy/heat cell `side_N`/`top_N`, cylinder `gauge_N`, chargepad
  `housing_front_N`) match the spec's `_\d+.png` rule. So they lose grain like the real connected sets
  (carbonizer `casing_N`/`top_N`). They are too small for grain anyway.
- **`top` flag**: used exactly as specified. `chargepad/housing_top` doesn't match it (no `/top`), so it
  rolls like a side. See below.

## Invariant checks (reshade_all.py fails on any break)

- Every file: same size; alpha byte-identical, in memory and re-read after saving; outline pixels unchanged;
  strips reshaded frame by frame (each frame alone gives the same output).
- `.mcmeta`: hashed before and after the run.
- Opaque blocks, 2×2 tiling:
  - Reshade the original tiled 2×2 and compare its seams with the reshaded texture tiled 2×2. Where the
    pixels across a seam are the same class (both plain, both coloured, both lit steel…) they must match,
    so the edge adds no emboss, lit line or shadow.
  - Across a real boundary the edge is "neither" by design.
  - Machine face regions are left out: their cylinder profile spans one tile by design.
- Connected/numbered sets: every variant shares its face columns, and no variant gains a new lit line
  on an edge.
- Last run: 945 files, 365 tiling checks, 9 sets, all hold.

## Look worse, or at least not better

- **Infuser sides** (`infuser_side*`): the dense straps become alternating spec rows. Still straps, but
  busier than the flat original.
- **Tiled machine walls:** the cylinder profile is dark at both tile edges, so machines placed side by
  side show a darker double column at every seam. It's the technique working as specified (softer since
  the 5–8 range), and the biggest change from TEXTURE_STYLE rule 1.2.
- **Chargepad `housing_side`/`housing_top`, gas turbine `bearing_face`, `rotor_shaft`:** big plain faces
  become vertical cylinders with grain. On the housing top it reads like a pipe lying on the pad.
  Worth adding to `top` if the experiment continues.
- **Energy/Heat Cell fill checkers:** the emboss puts a pale row on top of the checker, so it's
  slightly muddier.
- **Raw Bismuth, bismuth ore:** already multi-colour noise; the extra contrast makes it a little louder.
- **Sorghum heads:** their top-left edges go pale peach, so they're a bit less rust-red.
