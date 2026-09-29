# Arcforge GUI review (all screens)

Scope: every `AbstractContainerScreen` in `client/screen/**` (40 registered screens, 43 background PNGs), the shared
helpers in `client/gui/**`, the menus' slot constants, all `textures/gui/container/*.png`, all
`textures/gui/sprites/container/**` and `widget/arcforge/**` sprites, `en_us.json`, and `docs/TEXTURE_STYLE.md`.

Method: layouts were read from the code constants; recesses, slots, tracks and arrows were detected pixel-exactly from the
PNGs (a recess is a 1 px `#0E0E0E` top/left + 1 px `#5C5C5C` bottom/right, or the blue/orange/green/steel pairs). Text was
measured with the real 26.1 `ascii.png` (advance = rightmost ink column + 2, space = 4). Ink of a string drawn at `x`
spans `x .. x+advance-2`; `textRight(right)` ink ends at `right-2`. Glyphs that are not in `ascii.png` were estimated:
`·`=3, `×`=6, `…`=6, `→`=8 (their exact widths do not change any verdict below). Worst cases use default config values.

Note: every background shares an identical outer frame and palette (verified on all 43 PNGs), and no stray pixels were
found outside recesses, tracks, arrows and dials. The problems are text fit, cross-screen consistency, and sprites and code.

---

## 1. House GUI spec (what most screens actually do)

**Panel**
- 176 px wide. Heights: 166 (standard), 206 (18-slot buffer: Assembler, Vacuum Collector), 214 (27-slot buffer: Arc
  Quarry), 222 (Crate), 236 (Quarry settings), 184 (Security Terminal). The scrolling crate is 194 wide.
- Frame: a 1 px `#000000` outline with transparent corner pixels, a 2 px `#505050` highlight (rows and cols 1–2), a 2 px
  `#1A1A1A` shadow (rows and cols W-3..W-2). The face is `#313131` from (3,3) to (W-4,H-4).
- Title: centred (`(176 - w)/2`), y 6, TEXT `#E0E0E0`, no shadow.
- Inventory label: x 8, y = height − 94 (72 on 166), LABEL `#A0A0A0`.
- Player inventory: slots at (8, height − 82) (84 on 166), hotbar +58.

**Recess (any window)**
- 1 px `#0E0E0E` top/left lip and 1 px `#5C5C5C` bottom/right lip.
- The top-right and bottom-left corner pixels take the floor colour.
- Floor `#1F1F1F`.

**Slots**
- An 18×18 recess; the item sits at +1.
- Input: `#1E3F6E`/`#5E9BE8` lips, `#172230` floor. Output: `#7A4210`/`#F0A860` lips, `#2A1F16` floor.
- Neutral grey is for player inventory, ghost and pattern cells, and display cells.
- Main output: a 26×26 orange recess at y 30; the item sits at +5 (x = arrow end + 5).
- Bucket pairs are stacked at y 18 and y 52 (frames), with a 6×6 `#5C5C5C` down-arrow centred in the 16 px gap (y 41).

**Vertical gauges (FE and HU buffers)**
- A 12×52 frame at (8,18) (left) or (156,18) (right). The sprite is 10×50 at (9,19) or (157,19), cropped from the bottom.
- Convention (majority): inputs on the left, outputs on the right.

**Tanks**
- A 14×52 frame at y 18; fill and `tank_gauge` overlay 12×50 at +1.
- Tank-to-tank gap 2 px; gap to a neighbouring gauge 4 px (see L-series for deviations).
- Lubricant: 8×52 frame, 6×50 fill at (25,19).
- Storage gauges are the storage family's own: 56 high at y 16.

**Processing row (machines without a screen)**
- Input slot frame at y 34 (item y 35).
- Arrow 21×15 sprite at y 35 over a baked `#1A1A1A` arrow (rows 36–48); the sprite is ACCENT cyan.
- Output 26×26 at y 30.
- Status line at y 58: LED at the input slot's frame x, text at LED + 8.

**Screen recess (generators, heat and multiblocks)**
- y 18, h 52 (inner 19–68). The x position varies with the gauges, tanks and slots beside it (gaps are 2–6 px, see L8).
- Text rows:
  - Row 1 at y 23: label at x = recess x + 5 (inner + 4) in LABEL; value right-aligned at `right = inner right − 3` in TEXT.
  - Bar at y 34: a 4 px flat `#0E0E0E` track baked in, the same size as the bar sprite; a 2×6 marker at y 33.
  - Row 2 at y 42.
  - Status at y 56.
- Value colours: heat amounts and rates HEAT `#FF9A3C`; FE rates ACCENT `#5FD4C4`; plain values TEXT.

**Status line**
- LED 6×6 (`led_running`/`idle`/`blocked`/`off`), text at LED x + 8, LED y = text y. Status text in TEXT.

**Other elements**
- Flame: 14×14 (`flame_off`, then `flame_on` cropped from the bottom).
- Ghost sprites: 16×16 white alpha silhouettes drawn over the empty slot.

**Buttons**
- 20×20 `widget/arcforge/button*` nine-slice, with a 16 px icon at +2 (+3 when pressed).
- Selected = pressed + a 1 px ACCENT outline outside.

**Side tabs**
- Size: 22×20 collapsed, 24×20 selected. Stacks start at y 6 with a 1 px gap; right tabs at x 172, left tabs at `4 − width`.
- Panels (nine-slice `tab_panel`, border 4):
  - Energy, Heat, Sides, Upgrades and Redstone: 100 wide (Redstone widens to 104 or 128 for more buttons).
  - Pressure and Security: 108 wide. Ports: 128 wide.
- Panel title at (+22, +6); content from y +24, x +6 (text) or +8 (buttons and slots).
- Left = Energy, Heat, Pressure. Right = Redstone, Sides/Ports, Upgrades, Security, in that order.
- Upgrade slots at (181,73), pitch 20. This works because exactly two right tabs precede Upgrades.

---

## 2. Findings

Severity: **High** = visible collision or overflow in normal play. **Med** = visible defect in plausible cases, or a clear
style-guide or consistency break. **Low** = polish, consistency or code smell.

### High

**H1. Distillation Array: status text overflows the screen into the Naphtha tank.**
- Files: `DistillationArrayScreen.java` (STATUS_X 54), `distillation_array.png`.
- Evidence:
  - The screen recess is (42,18,50,52), inner x 43–90; the Naphtha tank frame starts at x 96.
  - NOT_FORMED "Incomplete" (w 53) has ink 54–105: **15 px past the inner edge**, over the lip, the gap and the tank frame.
  - NO_FEED "No feed" (w 39) ink ends at 91, **1 px on the lip**. "Running" ends at exactly 90.
- Fix: clip with `clipped(status, 90-54+1=37)` plus the tooltip (the Assembler pattern), or shorten the strings
  ("Unformed" = 45 still overflows, so clip). Better: widen the screen by moving LED/status to x 46/54 with a 2-line
  layout.

**H2. Security Terminal: the feedback line runs off the panel.**
- Files: `SecurityTerminalScreen.java` (FEEDBACK_Y 170, x 8).
- Evidence (panel inner right is about 172):
  - "Security is turned off on this server" (w 190) ink 8–196: **24 px outside the panel**.
  - "Notch_TheBuilder is already trusted" (w 184) ends at 190; "No player named Notch_TheBuilder" (w 174) ends at 180.
- Fix: wrap with `font.split(text, 160)` over two lines (y 166 and 175 fit in 184), or clip with an ellipsis and a tooltip.
  Shorter lang: "Security is off on this server" = 150.

**H3. Heat Cell: the In and Out values collide.**
- Files: `HeatCellScreen.java` (IN_OUT_Y 50: In left at 55, Out right at 163).
- Evidence (the rate is `ConduitTier.heatCellRate` = 50/200/800/3,200 HU/t):
  - Arcforged at full rate: "In +3200" ink 55–97 and "Out -3200 HU/t" ink 87–161, **11 px overlap**.
  - At 800 the gap is 1 px.
  - The numbers are also ungrouped (no `grouped()`), unlike everywhere else.
- Fix: put In and Out on separate rows (for example In at 50, Out at 61, and move Leak onto the pip row), or use
  `compact()` with shorter strings ("+3.2k" / "−3.2k HU/t").

**H4. Combustion Plant: the fuel name overlaps the "Fuel" label.**
- Files: `CombustionPlantScreen.java` (label at 57, value right at 141, y 23).
- Evidence:
  - `combustion_fuel` contains `minecraft:coal_block`. "Block of Coal" (w 66) starts at x 75, while the "Fuel" label ink
    ends at 76: **2 px overlap**.
  - "Charcoal" is fine.
- Fix: clip the value to `141 − (57 + w("Fuel") + 4)` = 58 px with an ellipsis and a tooltip, or show the item icon
  instead of the name.

**H5. Fuel Burner Heat tab: the extra row overflows the 100 px panel.**
- Files: `FuelBurnerScreen.java` (HeatTab extra `fuel_burner.fuel_value`), `HeatTab.java` (x +6, width 100).
- Evidence (the panel's content area ends at about x +96, nine-slice border 4):
  - "Heavy Oil · 0.25 mB/t" (w 102) ends at x +106.
  - "Creosote · 0.5 mB/t" (w 97) ends at x +101.
  - Because this is a left tab, the overflow lands on the GUI frame.
- Fix: let HeatTab/EnergyTab size their width to their content (max of their lines + 12), or split the fuel name and
  rate into two rows.

### Med

**M1. Ports tab (every multiblock screen): row text overflows the 128 px panel.**
- Files: `PortsTab.java` (TEXT_X 28, WIDTH 128).
- Evidence:
  - The location line: "Bottom · x-1 y-2 z+1" (w 105) ends at +131; "Front · the controller" (w 111) ends at +137.
  - The panel content ends at about +124.
- Fix: widen to about 150, or clip with `plainSubstrByWidth(…, WIDTH-TEXT_X-6)` plus an ellipsis. `ports.more` also
  shares the row pitch.

**M2. Ports tab labels use the generic SideMode text, not the machine's names.**
- Files: `PortsTab.java` (`port.mode().getDescription()`), `MachineScreen` (the Ports tab gets no `sideModeName`).
- Evidence: `side_mode.energy` = "Energy output", so the Arc Crushing, Induction Furnace and Metal Pressing Arrays (which
  consume FE) list their energy port as "Energy output". The Sides tab gets it right via `sideModeName`.
- Fix: pass `this::sideModeName` into `PortsTab` as `SideConfigTab` does.

**M3. Electric Pump: the Source value overlaps its label.**
- Files: `ElectricPumpScreen.java` (label at 31, value right at 121).
- Evidence:
  - The pump takes any source with `BucketPickup` (`ElectricPumpBlockEntity:139`).
  - "Sulfuric Acid" (w 64) starts at 57, while "Source" ink ends at 65: **9 px overlap**. "Heavy Oil" fits with 8 px spare.
- Fix: clip to `121 − 69 = 52` px with an ellipsis, and put the full name in the existing tooltip.

**M4. Arc Quarry: status truncated with no ellipsis.**
- Files: `ArcQuarryScreen.java:drawText` (`plainSubstrByWidth(…, TEXT_W-8=82)`).
- Evidence:
  - "Waiting for chunk" (w 86) renders as "Waiting for chun".
  - "Out of Cobblestone" (w 96) and "Out of Deepslate Tiles" (w 111) are cut mid-word.
  - The screen has room to x 119: 84 px from 35.
- Fix: use `clipped(statusLine(), 84)` with a status tooltip (Assembler/Breaker pattern), or shorten to
  "Waiting: chunk".

**M5. Steam and Gas Turbine Arrays: the status touches or overflows the screen.**
- Files: `SteamTurbineArrayScreen`/`GasTurbineArrayScreen` (STATUS_X 96, VALUE_RIGHT 145), `*_turbine_array.png`.
- Evidence:
  - The screen is (34,18,118,52), inner 35–150.
  - Gas turbine "Intake shut" (w 57) ink 96–151: **1 px on the lip**. Steam turbine "Spinning up" ends at exactly 150.
  - VALUE_RIGHT 145 was copied from the boiler (inner 148) and leaves 7 px on this screen, while the status has 0.
- Fix: clip the status to `150 − 96 − 2`, or move LED/status to x 84/92 (the label column moves with it). Set
  VALUE_RIGHT = 147 (inner − 3).

**M6. Die slot bevel is inverted (reads as raised).**
- Files: `metal_press.png` (28,34,18,18), `metal_pressing_array.png` (26,18,18,54: three cells).
- Evidence: the top/left lip is `#8C939B` (lit) and the bottom/right lip `#3A3F45` (dark). Every other slot and recess is
  dark top/left and lit bottom/right, as the style guide requires for recesses. The colours are also machine-steel ramp,
  not the GUI greys.
- Fix: swap the lips (`#3A3F45` top/left, `#8C939B` bottom/right, as a "tool" slot variant), or use the grey slot. The
  ghost_die already marks the slot.

**M7. Solar Thermal Array heat bar does not follow HeatScale.**
- Files: `SolarThermalArrayScreen.java:barWidth`, `solar_thermal_array/heat_bar.png`.
- Evidence:
  - Every other heat bar maps 20→1,600 °C with the HeatScale colours (`#3C699A…#FFF0BE`), "so the same colour always means
    the same heat".
  - The solar bar maps 20→`SOLAR_MAX_TEMPERATURE` (1,100 °C) and its sprite runs `#4A4E5E…#FBA52C`. A full solar bar
    shows ~1,200 °C colours at 1,100 °C, and its width is not comparable to the boiler's.
  - The class comment still says "20-550°C".
- Fix: use `drawHeatBar()` (`HeatScale.fillWidth`) with a sprite rendered from HeatScale at width 88. Keep the 500 °C tick.

**M8. Status line position and LED alignment are inconsistent.**
- Files: all screens with `drawLed`.
- Evidence:
  - Status y varies: 58 (the processing row), 56 (screens, Fiberizer), 60 (Block Breaker), 61 (turbines, Solar),
    63 (Assembler), 22 (Vacuum), 20 (Quarry).
  - LED y ≠ text y on the Assembler (LED 64 / text 63) and the Arc Quarry (21 / 20); all others use LED y = text y.
  - The arrays (Crushing, Induction, Pressing) show an LED at (161,23) with no text.
  - The Block Placer, Carbonizer and Arcforge Furnace have no status line at all.
- Fix: set LED_Y = STATUS_Y everywhere. Adopt y 58 (row layout) or y 56 (screen layout). Give the Placer, Carbonizer and
  Furnace an LED + status. Give the arrays a clipped status line under Power (y 61 fits: inner to 68).

**M9. Colour semantics differ between screens, tabs and storage.**
- Files: `EnergyTab`, `HeatTab`, `UpgradesTab`, storage screens, `ArcCrushingArray`/`InductionFurnaceArray`/`MetalPressingArray`,
  turbine screens.
- Evidence:
  - Tabs draw labels in TOOLTIP_GRAY `#AAAAAA` and values in WHITE `#FFFFFF`. Screens use LABEL `#A0A0A0` and TEXT `#E0E0E0`.
  - FE output is ACCENT on the Combustion, Thermoelectric and turbine screens, but TOOLTIP_GREEN `#55FF55` in the Energy tab
    for the same number.
  - The array "Power" value uses a local `SAVING_COLOR 0xFF55FF55` (copied three times).
  - Storage amounts and turbine RPM use WHITE.
  - `0xFF707070` "dim" is redeclared five times (NO_STEAM, NOT_BOILING, PASSING, EMPTY ×2).
- Fix: add `ArcforgeGui.GOOD`/`BAD`/`DIM` and use LABEL/TEXT in the tabs. Pick one FE-output colour (ACCENT) for tab and
  screen.

**M10. Custom button sprites, which the guide forbids.**
- Files: `widget/arcforge/auto_eject_on|off.png` (SideConfigTab/PortsTab), `electrolyzer/vent_on|off.png`
  (`ElectrolyzerScreen.vent`).
- Evidence:
  - The guide says "only the widget/button nine-slice family … No custom button sprites."
  - Both are self-bevelled button images. The vents use a `0x30FFFFFF` fill for hover instead of `button_hover`.
  - The vents also sit at y 71–82 with **0 px gap** to the player-inventory recess at y 83, on bare panel with no recess.
- Fix:
  - Draw the auto-eject button as `button`/`button_pressed` (16 px nine-slice) + icon, with pressed + ACCENT when on.
  - Draw the vents as 14×12 `button` + icon, and move them up 1 px (y 70), or put them below the tank inside the frame
    area.

**M11. Button and tab icons break the icon rule (outline).**
- Files: `arc_quarry/{back,reset,start,stop,config,scan,silk_*}`, `conduit_filter/{dir_*,filter_allow,components_ignore}`,
  `vacuum_collector/{minus,show_range,hide_range}`, `vault/{lock_*,void_*}`, `widget/face_*`, `widget/auto_eject_*`.
- Evidence:
  - The guide asks for one flat fill per shape with a 1 px hue-matched dark outline. These are single-colour silhouettes
    without an outline (for example start = solid `#55FF55`, stop = solid `#FF5555`, back and reset = `#E0E0E0`).
  - `vault/void_on` uses the forbidden `#0C0D0F`.
  - All `face_*` tiles and `auto_eject_*` use a pure `#000000` border.
- Fix: redraw with hue-matched outlines (as in `icon_energy` `#5A140F` and `icon_upgrades` `#2E1A52`), and use dark tones
  of the face colour for the face tiles.

**M12. Heat and burn bars are smooth gradients, in eight width-specific copies.**
- Files: `*/heat_bar.png` (42, 84, 88, 92, 105, 108, 114, 152 px: 14–29 colours each), `combustion_plant/burn_bar.png`
  (14 colours).
- Evidence:
  - CLAUDE.md and TEXTURE_STYLE say "Never add gradients" for GUIs.
  - Each new bar width needs a new pre-rendered sprite; `drawHeatBar` fetches `sprite("heat_bar")` per machine.
- Fix: draw the bar in code as 9 flat bands from `HeatScale.COLORS` (one colour per band, stepped), or use one shared
  stepped 152 px sprite cropped to width.

### Low

**L1. Temperature and number formatting is inconsistent.**
- Files: Firebox, Thermoelectric, Heat Cell (screen and tooltips), Fiberizer (`celsius_needs`), `ArcforgeGui`.
- Evidence:
  - Grouping: "1400°C" (ungrouped `menu.getTemperature()`) versus "1,400°C" on the Fuel Burner, Geothermal, Boiler and
    others.
  - Spacing: the Arcforge Furnace tooltips use "%s / %s °C" with a space.
  - Case: Heat Cell `compact()` is upper-cased ("124K") while the Energy Cell shows "12.4k".
- Fix: always use `grouped()`, one `celsius` key, and lower-case k/M.

**L2. The Energy tab shows "Usage −0 FE/t" in red for machines with no per-tick usage.**
- Files: `BlockPlacerScreen`, `VacuumCollectorScreen`, `ArcQuarryScreen` (`EnergyTab.usage(…, () -> 0)`).
- Fix: add an `EnergyTab.storedOnly(...)` that omits the usage row (tab height 50), or pass the real per-operation cost.

**L3. The Superheater Array has a heat buffer but no Heat tab.**
- Files: `SuperheaterArrayScreen.java`.
- Evidence: the Boiler (same family) gets Heat + Pressure; the Superheater gets Pressure only (heat only in the bar
  tooltip).
- Fix: add a `HeatTab(menu::getHeat, "usage", …)` before the PressureTab.

**L4. Flame placement differs within the burner family.**
- Files: Firebox and Combustion flame at (21,46) under the fuel slot, outside the screen. Fuel Burner and Geothermal flame
  at (127,52) inside the screen.
- Fix: pick one. Inside the screen at (127,52) keeps it clear of the slots on all four.

**L5. Storage-family geometry drifts.**
- Files: `StorageMenu` (SLOT_X 27 versus SLOT_X_LEFT 9), `VaultMenu` (8,18), `vault.png`, `PressurizedCylinderScreen` (TEXT_X 58),
  `energy_cell.png`.
- Evidence:
  - Slot columns sit at item x 27 (Energy Cell, Fluid Tank), x 9 (Heat Cell, Cylinder) and x 8,y 18 (Vault: frames at 7,17,
    1 px up and left).
  - The Vault screen recess is at y 17 (others y 16); the Vault down-arrow sits 1 px high in its gap (39 versus a centred 40).
  - Cylinder text is at inner + 3 (58 on inner 55); others use inner + 4.
  - The Energy Cell charge slot is **green** (`#2F6A1E`/`#9AE878`), the only green slot in the mod (others use blue for
    input).
- Fix:
  - Put Vault slots at (9,19) and (9,53), matching the storage siblings, and move the screen to y 16.
  - Set Cylinder TEXT_X to 59.
  - Make the Energy Cell slot blue, or document green = charge.

**L6. Heat-threshold tick styles differ on every bar.**
- Evidence: Boiler grade ticks 1×4 `#5C5C5C`; Superheater 1×4 `#FFFFFF`; Fiberizer `min_temp_tick` 3×7 sprite (white);
  Solar 1×4 `#8CC8FF`.
- Fix: one tick style (for example a 1×6 TEXT-coloured line from y 33), as a shared helper in `MachineScreen`.

**L7. Screen padding and row positions drift.**
- Evidence:
  - Left pad is 4 everywhere except the Distillation Array (SCREEN_X 46 on inner 43 = 3) and the Arcforge Furnace bar
    (12 on inner 9).
  - Right value edge = inner − 3 everywhere except Solar (RIGHT 164 on inner 166 = −2) and the turbines (145 on 150 = −5).
  - Row 1 is at y 23 except on the turbines and Solar (22); row 2 is at y 42 except on Distillation (43).
- Fix: derive these values from the recess (`SCREEN_X + 5`, `SCREEN_X + W − 4`) rather than hard-coding them.

**L8. Tank and gauge spacing drifts.**
- Evidence:
  - The gap between adjacent tanks is 2 px on the Chemical Reactor (24/40), Electrolyzer (138/154) and Distillation (8/24),
    but 4 px on Distillation's outputs (96/114/132).
  - Gauge-to-screen gap: 6 (Thermoelectric, Pump), 4 (Solar), 2 (turbines' lube → screen).
  - The Carbonizer tank is 16×54 in a 18×56 frame (storage style) on a machine GUI.
  - The Arc Quarry energy gauge is 46 high at y 16.
  - The Solar heat buffer (an output) is on the left, although producers put it on the right (Firebox, Geothermal,
    Fuel Burner at 157).
- Fix: normalise when the backgrounds are next touched; the minimum is a note in the spec.

**L9. Crate and Arc Tool title placement.**
- Files: `CrateScreen` (no `titleLabelX` override, so the default x = 8, left-aligned), `ArcToolScreen` (titleLabelY 5).
- Evidence: every other GUI centres its title at y 6.
- Fix: centre the Crate title over the 176 px part (`(176 − w)/2`) and set the Arc Tool title to y 6.

**L10. Ghost treatments differ.**
- Evidence:
  - Sprites are used on 12 screens, but the Arc Quarry uses `fakeItem(cobblestone)` + a `0xB08B8B8B` wash, the Assembler
    uses the item + a `0x808B8B8B` wash, and the Vault uses `fakeItem` + `0xA0101214`.
  - `ghost_bucket` marks the **input** slot on the Pump and Carbonizer but the **output** slot on the Geothermal, Fuel
    Burner, Infuser and Boiler.
- Fix: one wash constant in ArcforgeGui, and one rule for the bucket ghost (for example "where the player puts the
  empty bucket").

**L11. Tooltip hover areas are inconsistent.**
- Evidence:
  - The Fluid Tank gauge hover is the inner 36×54 (misses the 1 px frame); other storages use the recess.
  - The Carbonizer tank hover is inner; machine tanks use frame ±1.
  - Gas Turbine exhaust: centred on `min(52, w)` but drawn at full width, so wider text drifts right.
  - Electrolyzer cost text is not clipped to TEXT_W 66 (default "600 FE/mB H2" ends at 109; ≥ 4 digits would hit the
    arrow at 114).
  - The Fiberizer heat-bar hover (43,63,105,7) overlaps the status row.
- Fix: use a `hoverFrame(x, y, w, h)` helper, and clip the electrolyzer line.

**L12. Duplicated sprites (the same file per machine).**
- Evidence (identical by md5):
  - `led_*` ×30, `energy_bar` ×24, `progress` ×17, `tank_gauge` ×14, `heat_marker` ×12, `ghost_bucket` ×7,
    `heat_buffer` ×6, `flame_on`/`flame_off` ×4, `steam_fill` ×4, `heat_bar`(84) ×3, `ghost_coal` ×3, `water_fill` ×3,
    `lava_fill`, `lube_*` and `ghost_die` ×2 each.
  - `infuser/fluid_fill` == `fuel_burner/fuel_fill`.
  - `distillation_array/steam_fill` differs from the other four steam fills.
- Fix: move these to `container/common/` (for example `ArcforgeGui.common("led_running")`); keep per-machine overrides only
  where the art differs.

**L13. Unused, mis-sized and cross-referenced sprites.**
- Evidence:
  - Unused: `block_breaker/progress`, `vacuum_collector/progress`, `geothermal_plant/energy_bar`, `widget/face_locked`.
  - `pressurized_cylinder/gas_fill` is 36×54 but blitted as a 20×54 fallback, so it is squashed.
  - `ArcQuarryConfigScreen` borrows `conduit_filter/*` and `vacuum_collector/{show,hide}_range`.
  - `ConduitFilterScreen` uses `icon_clear` (a red ×) as its fluid fallback.
- Fix: delete the unused sprites; resize `gas_fill` to 20×54; move the shared icons to `widget/arcforge`.

**L14. The Fiberizer status line abuts the output recess.**
- Evidence: the output frame (96,30,26,26) ends at y 55 and the status is at y 56. "Output full" ink runs 51–102, so
  x 96–102 sits directly under the lip with 0 px gap. Siblings use y 58.
- Fix: the heat bar at y 66 forces 56; move the bar and tick to y 67/64 and the status to 57, or narrow the status with
  `clipped(…, 43)`.

**L15. Text that touches recess edges (0 px padding).**
- Evidence:
  - Energy Cell Arcforged "Out -65.5k FE/t" ends at 166 (inner edge).
  - Condenser "Air 120 · Water 280" ends at 148 (inner edge).
  - Geothermal Heat tab "24 lava · 64 magma" ends at x +98 in a 100 px tab.
  - Arc Crushing Array "x2 +25%" ends at 163 (5 px).
- Fix: clip at inner − 4, or shorten ("W 280").

**L16. Inventory label and bottom margin on tall GUIs.**
- Evidence:
  - MachineScreen uses `height − 93`, which puts the label 11 px above the inventory on 206 GUIs but 12 on 214 and 166.
  - The Arc Quarry label is at y 121, only 1 px under the buffer (last frame row 119).
  - The Quarry's bottom margin is 5 px versus 6.
- Fix: derive it from the menu's `INVENTORY_Y − 12` (as ArcQuarryConfig does with −11), and move the quarry inventory to
  y 132.

**L17. Toggle buttons do not show the selected state as the guide says.**
- Files: Vault lock/void, Quarry silk/replace/show-area, Vacuum show-range.
- Evidence: they swap only the icon; the guide's selected state is pressed + ACCENT outline (used by the Redstone and
  Security tabs).
- Fix: draw `button_pressed` + outline when on.

**L18. Redstone tab panel width.**
- Files: `RedstoneTab` (`max(100, 8 + n·24)`).
- Evidence: with 4 modes (Breaker, Placer, Gas Turbine) the panel is 104 wide and the last button ends at 100, so the
  margins are 8 left and 4 right.
- Fix: use `max(100, 2·8 + n·24 − 4)`.

**L19. Block Breaker target name is truncated with no ellipsis.**
- Files: `BlockBreakerScreen.drawText` (`plainSubstrByWidth(…, 60)`).
- Evidence: "Deepslate Wolframite Ore" (w 125) is cut mid-word, while the status on the same screen uses `clipped()`.
- Fix: use `clipped(target(), 60)`; the tooltip already exists.

**L20. Status text and wording ambiguities.**
- Evidence:
  - DISABLED shows "Idle" (same as IDLE, but a different LED), and SUPERHEATING shows "Heating" (same as HEATING).
  - The Fermenter's crop tooltip reuses `arc_melter.melts_into` ("Melts into … of Ethanol").
  - `gui.arcforge.gas_turbine.status.*` and `fermenter.status.fermenting` are unused.
  - The Solar buffer tooltip uses `gui.arcforge.heat` ("Heat: x / y HU") where every other buffer uses `hu_stored`.
  - FE stored tooltips are GRAY while HU stored lines are default white.
- Fix: use "Disabled" (w 42) and "Superheating"; add a `fermenter.ferments_into` key; unify the buffer tooltips.

**L21. The Crate scroller uses the vanilla light palette.**
- Files: `crate/scroller.png` (`#C6C6C6`/`#555555`) on the dark GUI.
- Fix: redraw in `#505050`/`#1A1A1A`/`#313131`, like a raised panel.

**L22. The Carbonizer bucket slots break the pair spacing.**
- Files: `CarbonizerMenu` (BUCKET_OUT_Y 51), `carbonizer.png`.
- Evidence: the frames are at y 18 and 50 (a 14 px gap) versus 18 and 52 (16 px) on every other bucket pair.
- Fix: move the out slot to 53 if the screen recess allows (it does: the screen is at x 8–121).

---

## 3. Tabs per screen (L = left, R = right; Sec = Security, shown only when security is on and owned)

- **Energy (usage) | Redstone, Sides+Auto-eject, Upgrades, Sec:** Arc Crusher, Induction Furnace, Metal Press, Chemical
  Reactor, Fermenter, Infuser, Assembler, Electric Pump.
- **Energy (usage) | Redstone, Sides (no auto-eject), Upgrades, Sec:** Arc Melter, Electrolyzer.
- **Energy (usage, 0) | Redstone(+Pulse), Sides, Upgrades, Sec:** Block Placer (no auto-eject). The Block Breaker is the
  same with real usage and auto-eject.
- **Energy (usage, 0) | Redstone, Sides+AE, Upgrades, Sec:** Vacuum Collector, Arc Quarry.
- **Energy + Heat | Redstone, Sides, Upgrades, Sec:** Fiberizer (+AE), Thermoelectric Plant.
- **Heat | Redstone, Sides, Upgrades, Sec:** Firebox, Geothermal Plant (+nearby row), Fuel Burner (+fuel row).
- **Energy (output) | Redstone, Sides, Upgrades, Sec:** Combustion Plant.
- **Energy | Redstone, Ports+AE, Upgrades, Sec:** Arc Crushing, Induction Furnace and Metal Pressing Arrays.
- **Heat + Pressure | Redstone, Ports+AE, Sec:** Steam Boiler Array.
- **Pressure | Redstone, Ports, Sec:** Superheater Array (no Heat: L3).
- **— | Redstone, Ports, Sec:** Condenser Array.
- **Heat | Redstone, Ports(+AE on Distillation), Sec:** Distillation Array, Solar Thermal Array.
- **Energy | Redstone(+Throttle on Gas), Ports, Sec:** Steam and Gas Turbine Arrays.
- **— | Redstone, Ports+AE, Sec (MultiblockScreen):** Carbonizer, Arcforge Furnace.
- **Storage:**
  - Energy Cell and Cylinder(+AE): Redstone, Sides, Sec.
  - Heat Cell: Redstone, Sides, Upgrades(+leak info), Sec.
  - Fluid Tank: Sides+AE, Sec (no redstone control in the menu).
  - Crate and Vault: Sides, Sec.
- **No tabs:** Conduit Filter, Arc Tool, Quarry Settings, Security Terminal.

Guide compliance: all readouts are on the left and all settings on the right; the order Redstone → Sides/Ports →
Upgrades → Security holds everywhere. Two remarks:
- The Pressure tab is interactive (it selects a setting) yet sits on the left, because the guide lists Pressure as a
  readout. Consider moving it right and renaming it a setting.
- The Heat tab for the Superheater is missing (L3).

---

## 4. Per-screen notes (layout facts, and where the findings apply)

**Processing machines (no screen)**
- **Arc Crusher:** gauge (8,18). Input (43,34), arrow 67, out 26² at 95, bonus (127,34). LED 43 / status 51,58. Fits:
  worst "Output full" ends at 102.
- **Induction Furnace / Metal Press:** input 52, arrow 78, out 26² at 108. Status 60,58, fine. The Press die slot at
  (28,34) has the inverted bevel (M6).
- **Arc Melter:** tank frame (100,18). Status 51,58; worst "No power" ends at 95, 4 px before the tank. No auto-eject by
  design.
- **Fermenter:** tanks at 24 and 112. LED 43 is not aligned with the input slot at 47 (the others align to the slot frame).
  "Fermenting" ends at 103 (tank at 112).
- **Chemical Reactor:** tanks 24/40 (2 px gap)/154. Slots at 59, 107, 129; arrow 81. Status 67,58 is fine.
- **Electrolyzer:** tanks 24/138/154. Ratio and cost text on bare panel at 43,24/36. Vents (M10).
- **Infuser:** tank 24, buckets 42, input 72, arrow 96, out 124. Status 80,58.
- **Fiberizer:** energy left and heat buffer right. Heat bar (43,66,105) with min tick. Status at y 56 (L14).
- **Assembler (206):** 3×3 ghost pattern (grey), arrow 88, out 26² at 118, remainders at 149. Status clipped to 72 at
  96,63 with the LED 1 px low (M8).
- **Block Breaker / Block Placer:** 3×3 grids at 97 (orange) and 61 (blue).
  - Breaker: target text at 30,22 (L19); bar (31,42,56,2) in a 58×4 track.
  - Placer: no status line (M8).
- **Vacuum Collector (206):** filter slot at (29,17). Status 60,22. Buttons at y 44 (minus 30, plus 96, show 122); range
  text centred at 73.
- **Arc Quarry (214):** gauge (8,16,12,46). Screen (23,16,98,46). Replace slot (123,16). Buttons Start (146,16),
  Settings (123,40), Reset (146,40). Status clip (M4). LED 1 px low (M8).
- **Quarry Settings (236):** fields and grid match their recesses exactly. The scan strip clips line 1 with no ellipsis
  (line 2 has one). The inventory label is intentionally hidden.

**Burner and heat family (screen 52,18,94,52 → inner 53–144; text 57, values 141; bar 57,34,84; status 65,56)**
- **Firebox, Combustion, Geothermal, Fuel Burner:** geometry identical and correct.
  - The Firebox temperature is ungrouped (L1); the Combustion fuel name collides (H4).
  - The flame placement split (L4).
- **Thermoelectric:** screen (26,18,124,52). Bar (31,34,114). Status + FE/t share row 56; the gap is ≥ 11 px even at
  "+1,000 FE/t", so fine.
- **Electric Pump:** screen (26,18,100,52). Its progress track is filled in code (`#0E0E0E`) rather than baked, unlike every
  other bar. Source overlap (M3).

**Steam family (screen 48,18,102,52 → inner 49–148; text 53, values 145; bar 53,34,92; status 61,56)**
- **Boiler Array, Superheater, Condenser:** geometry consistent. The Boiler hides its rate when it doesn't fit (good
  pattern). Condenser parts line touches the edge (L15).
- **Distillation:** screen (42,18,50,52) is too narrow for the status (H1). Padding 3 (L7).
- **Solar Thermal:** screen (24,18,144,52). Dial (29..70,24..44) matches the DIAL hover (28,21,44,26). Bar scale (M7).
  Heat buffer on the left (L8).
- **Steam and Gas Turbines:** screen (34,18,118,52). The dial is baked; pivot (60,45.5) sits on the 4×2 grey hub at
  58..61,44..45. Status overflow (M5).

**Arrays**
- **Crushing / Induction / Pressing:** lanes at y 18/36/54 with arrows at FIRST_PROGRESS_Y 20 (+18). Screens at 120, 108 and
  114 (inner right 168). An LED only (M8). The Pressing die column (26,18,18,54) is inverted (M6).
- **Arcforge Furnace / Carbonizer (MultiblockScreen):** screen (8,46,160|114,24).
  - Furnace oxygen and boost fit (O2 4,000 mB leaves a 3 px gap to "Heat").
  - Carbonizer title "Carbonizer · 8 chambers · 3×3" is 154 px, which fits.
  - Neither screen has a status LED (M8).

**Storage**
- **Energy Cell:** gauge (64,16,16,56), screen (86,16,82,56).
- **Fluid Tank:** gauge (62,16,38,56), screen (106,16,62,56).
- **Heat Cell:** gauge (30,16,16,56), screen (50,16,118,56). Bar (55,41,108); pips at 140–163 fit. H3 applies.
- **Cylinder:** gauge (30,16,22,56), screen (54,16,114,56).
- **Vault:** screen (30,17,118,52), item cell (35,22). Bar (37,61,104,2) in a 106×4 track. Buttons at x 152. L5 applies.
- **Crate:** 9×6 at (8,18) and the 194-wide scroll variant with its track (174,17,14,108). The title is not centred (L9).

**Tools and utility**
- **Conduit Filter:** 9 ghost cells at (8,18) pitch 18; chips at y 38; buttons at 8/30/52, y 46; summary at 76,52 fits.
- **Arc Tool:** module strip (23,54,146,12) with names clipped by `entries()`. Title at y 5 (L9).
- **Security Terminal:** list (7,68,162,72) and field (7,146,118,18) match the code. The feedback line overflows (H2).
