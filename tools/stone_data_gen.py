#!/usr/bin/env python3
# Copyright (c) 2026 Zagdrath
# SPDX-License-Identifier: MIT
"""Writes the natural stones' resources (see ModStones): blockstates, block and item models, item definitions, loot
tables, tags, lang lines, recipes, worldgen (features, placed features, biome modifiers) and the Engineer's Handbook
entry. Run from the repository root after changing a stone:

    python3 tools/stone_data_gen.py

Tag files and the lang file are merged (existing lines are kept); everything else is overwritten."""

import json
import os
from collections import OrderedDict

RES = "src/main/resources"
ASSETS = f"{RES}/assets/arcforge"
DATA = f"{RES}/data"
NS = "arcforge"

STONES = ["limestone", "dolomite", "slate", "serpentine", "sylvinite", "kimberlite", "pumice", "scoria", "travertine"]
TITLE = {s: s.capitalize() for s in STONES}

# What each stone is for, shown on the natural and cobbled blocks' info tooltips.
USES = {
    "limestone": "A steel flux in the Arcforge Furnace. Calcined in the Chemical Reactor it gives Quicklime and Carbon Dioxide; "
                 "with Brine and Ammonia, Soda Ash. Crushed, it gives Agricultural Lime.",
    "dolomite": "A steel flux. Calcined in the Chemical Reactor it gives Magnesia, Quicklime and Carbon Dioxide. Its bricks make "
                "Arcforge Furnace Bricks.",
    "slate": "A dark building stone that splits in thin sheets. Crushed, it gives gravel and sometimes Silica Dust; the Sifter "
             "finds Mica in Cobbled Slate.",
    "serpentine": "Crushed, it gives gravel and sometimes Nickel Dust; sifted, Chromite. It locks Carbon Dioxide into Magnesite. "
                  "Polished Serpentine casts Concrete Shielding without concrete.",
    "sylvinite": "Potash salt rock: crushed, it gives two Potash and often Salt.",
    "kimberlite": "The rock diamonds come up in. The Sifter finds the odd Diamond, and Garnet and Olivine, in Cobbled Kimberlite; "
                  "crushed, it gives gravel and now and then a Diamond.",
    "pumice": "A light volcanic glass full of holes. The Fiberizer spins it into Rock Wool; crushed, it gives sand and Silica Dust.",
    "scoria": "The Nether's cinder rock. The Fiberizer spins it into Rock Wool; crushed, it gives Volcanic Ash and sometimes Iron Dust.",
    "travertine": "A spring limestone: a steel flux, calcined into Quicklime and Carbon Dioxide, and crushed into Agricultural "
                  "Lime, like Limestone.",
}

# Worldgen per stone: kind (blob/layer/pipe), biome tag, step dimensions, the JSON fallbacks, rarity and targets.
OVERWORLD_TARGET = "minecraft:base_stone_overworld"
NETHER_TARGET = "minecraft:base_stone_nether"
WORLD = {
    "limestone": dict(kind="blob", biomes="#minecraft:is_overworld", rarity=2, size=64, min=0, max=112, shape="uniform"),
    "dolomite": dict(kind="blob", biomes="#minecraft:is_overworld", rarity=4, size=56, min=-16, max=80, shape="uniform"),
    "slate": dict(kind="layer", biomes="#minecraft:is_overworld", rarity=8, size=16, min=-24, max=72, shape="uniform",
                  radius=11, layers=3, tilt=0.25),
    "serpentine": dict(kind="blob", biomes="#minecraft:is_overworld", rarity=4, size=48, min=-64, max=16, shape="trapezoid"),
    "sylvinite": dict(kind="layer", biomes="#minecraft:is_overworld", rarity=10, size=14, min=-48, max=0, shape="uniform",
                      radius=11, layers=2, tilt=0.08),
    "kimberlite": dict(kind="pipe", biomes="#minecraft:is_overworld", rarity=48, size=24, min=-60, max=-44, shape="uniform",
                       height=56),
    "pumice": dict(kind="blob", biomes="#minecraft:is_mountain", rarity=3, size=48, min=60, max=200, shape="trapezoid"),
    "scoria": dict(kind="blob", biomes="#minecraft:is_nether", rarity=1, size=48, min=10, max=117, shape="uniform",
                   target=NETHER_TARGET),
    "travertine": dict(kind="layer", biomes="#c:is_dry/overworld", rarity=6, size=14, min=50, max=100, shape="uniform",
                       radius=10, layers=2, tilt=0.1),
}

CALCIUM_CARBONATE = ["limestone", "travertine", "dolomite"]
# The ones that calcine to Quicklime alone: Dolomite gives Magnesia too (tools/stone_minerals_data_gen.py).
LIME_STONES = ["limestone", "travertine"]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def ident(name):
    return f"{NS}:{name}"


def tex(name):
    return f"{NS}:block/stone/{name}"


def variants(s):
    """The stone's eight worked forms: (block id, shape stem, display name, texture: a name or (side, end) for the
    pillar)."""
    t = TITLE[s]
    return [
        (s, s, t, s),
        (f"cobbled_{s}", f"cobbled_{s}", f"Cobbled {t}", f"cobbled_{s}"),
        (f"polished_{s}", f"polished_{s}", f"Polished {t}", f"polished_{s}"),
        (f"{s}_bricks", f"{s}_brick", f"{t} Brick", f"{s}_bricks"),
        (f"small_{s}_bricks", f"small_{s}_brick", f"Small {t} Brick", f"small_{s}_bricks"),
        (f"{s}_pillar", f"{s}_pillar", f"{t} Pillar", (f"{s}_pillar", f"{s}_pillar_top")),
        (f"chiseled_{s}", f"chiseled_{s}", f"Chiseled {t}", f"chiseled_{s}"),
    ]


def family(s):
    """Every block of a stone, (block id, shape, texture, base block for slabs' double): each form, then its stairs,
    slab and wall."""
    b = []
    for block_id, stem, _, texture in variants(s):
        b.append((block_id, "pillar" if block_id.endswith("_pillar") else "cube", texture, None))
        b.append((f"{stem}_stairs", "stairs", texture, block_id))
        b.append((f"{stem}_slab", "slab", texture, block_id))
        b.append((f"{stem}_wall", "wall", texture, block_id))
    return b


def display_name(block_id, s):
    for base_id, stem, name, _ in variants(s):
        if block_id == base_id:
            return {f"{s}_bricks": f"{TITLE[s]} Bricks", f"small_{s}_bricks": f"Small {TITLE[s]} Bricks"}.get(block_id, name)
        for shape in ("stairs", "slab", "wall"):
            if block_id == f"{stem}_{shape}":
                return f"{name} {shape.capitalize()}"
    raise KeyError(block_id)


# --- Assets ---------------------------------------------------------------------------------------------------------

def stairs_blockstate(name):
    """Asphalt Stairs' blockstate (vanilla's stairs rotations) with the names swapped."""
    with open(f"{ASSETS}/blockstates/asphalt_stairs.json") as f:
        text = f.read()
    return json.loads(text.replace("arcforge:block/asphalt_stairs", f"arcforge:block/{name}"), object_pairs_hook=OrderedDict)


def wall_blockstate(name):
    parts = [{"when": {"up": "true"}, "apply": {"model": ident(f"block/{name}_post")}}]
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for height, model in (("low", "_side"), ("tall", "_side_tall")):
            apply = OrderedDict(model=ident(f"block/{name}{model}"), uvlock=True)
            if y:
                apply["y"] = y
            parts.append({"when": {side: height}, "apply": apply})
    return {"multipart": parts}


# A pillar's side textures by join (StonePillarBlock): alone, the capital end of a stack, the base end, the middle.
PILLAR_SIDES = ("", "_upper", "_lower", "_middle")


def pillar_blockstate(name):
    """Twelve variants, axis x joins. The upright model is turned onto the axis (z: x 90, so the texture's top points
    north; x: x 90 then y 90, so it points east). The capital sits at the texture's top, so it shows where the end the
    texture's top points to is open: the positive end for y and x, the negative (north) end for z."""
    variants = OrderedDict()
    for axis, rot in (("x", {"x": 90, "y": 90}), ("y", {}), ("z", {"x": 90})):
        for up in ("false", "true"):
            for down in ("false", "true"):
                top_joined, bottom_joined = (down == "true", up == "true") if axis == "z" else (up == "true", down == "true")
                suffix = {(False, False): "", (False, True): "_upper", (True, False): "_lower", (True, True): "_middle"}[
                    (top_joined, bottom_joined)]
                v = OrderedDict(model=ident(f"block/{name}{suffix}"))
                v.update(rot)
                variants[f"axis={axis},down={down},up={up}"] = v
    return {"variants": variants}


def faces3(t):
    """Stairs and slab textures: a pillar's shapes show its side round them and its end on top and bottom."""
    if isinstance(t, tuple):
        return {"bottom": tex(t[1]), "side": tex(t[0]), "top": tex(t[1])}
    return {"bottom": tex(t), "side": tex(t), "top": tex(t)}


def assets(s):
    for block_id, shape, t, base in family(s):
        bs = f"{ASSETS}/blockstates/{block_id}.json"
        models = f"{ASSETS}/models/block"
        item_model = ident(f"block/{block_id}")
        if shape == "cube":
            write(bs, {"variants": {"": {"model": ident(f"block/{block_id}")}}})
            write(f"{models}/{block_id}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(t)}})
        elif shape == "stairs":
            write(bs, stairs_blockstate(block_id))
            three = faces3(t)
            write(f"{models}/{block_id}.json", {"parent": "minecraft:block/stairs", "textures": three})
            write(f"{models}/{block_id}_inner.json", {"parent": "minecraft:block/inner_stairs", "textures": three})
            write(f"{models}/{block_id}_outer.json", {"parent": "minecraft:block/outer_stairs", "textures": three})
        elif shape == "slab":
            write(bs, {"variants": {"type=bottom": {"model": ident(f"block/{block_id}")},
                                    "type=double": {"model": ident(f"block/{base}")},
                                    "type=top": {"model": ident(f"block/{block_id}_top")}}})
            three = faces3(t)
            write(f"{models}/{block_id}.json", {"parent": "minecraft:block/slab", "textures": three})
            write(f"{models}/{block_id}_top.json", {"parent": "minecraft:block/slab_top", "textures": three})
        elif shape == "wall":
            write(bs, wall_blockstate(block_id))
            for suffix, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"),
                                   ("_side_tall", "template_wall_side_tall"), ("_inventory", "wall_inventory")):
                write(f"{models}/{block_id}{suffix}.json",
                      {"parent": f"minecraft:block/{parent}", "textures": {"wall": tex(t[0] if isinstance(t, tuple) else t)}})
            item_model = ident(f"block/{block_id}_inventory")
        elif shape == "pillar":
            side, end = t
            write(bs, pillar_blockstate(block_id))
            for suffix in PILLAR_SIDES:
                write(f"{models}/{block_id}{suffix}.json",
                      {"parent": "minecraft:block/cube_column", "textures": {"end": tex(end), "side": tex(side + suffix)}})
            old_horizontal = f"{models}/{block_id}_horizontal.json"
            if os.path.exists(old_horizontal):
                os.remove(old_horizontal)
        write(f"{ASSETS}/items/{block_id}.json", {"model": {"type": "minecraft:model", "model": item_model}})


# --- Loot -----------------------------------------------------------------------------------------------------------

def loot(s):
    for block_id, shape, _, _ in family(s):
        path = f"{DATA}/{NS}/loot_table/blocks/{block_id}.json"
        if block_id == s:
            # As stone: the cobbled form, or the rock itself with Silk Touch.
            write(path, {"type": "minecraft:block", "pools": [{"entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": ident(s)},
                {"type": "minecraft:item", "condition": {"type": "minecraft:survives_explosion"}, "name": ident(f"cobbled_{s}")}]}],
                "rolls": 1}], "random_sequence": f"{NS}:blocks/{block_id}"})
        elif shape == "slab":
            write(path, {"type": "minecraft:block", "pools": [{"entries": [{"type": "minecraft:item", "modifier": [
                {"type": "minecraft:set_count", "condition": {"type": "minecraft:match_block", "blocks": ident(block_id),
                                                              "state": {"type": "double"}}, "count": 2},
                {"type": "minecraft:explosion_decay"}], "name": ident(block_id)}], "rolls": 1}],
                "random_sequence": f"{NS}:blocks/{block_id}"})
        else:
            write(path, {"type": "minecraft:block", "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                                                               "entries": [{"type": "minecraft:item", "name": ident(block_id)}],
                                                               "rolls": 1}], "random_sequence": f"{NS}:blocks/{block_id}"})


# --- Tags -----------------------------------------------------------------------------------------------------------

def merge_tag(path, values):
    existing = []
    if os.path.exists(path):
        with open(path) as f:
            existing = json.load(f).get("values", [])
    out = list(existing)
    for v in values:
        if v not in out:
            out.append(v)
    write(path, {"values": out})


def tags():
    blocks = [b for s in STONES for b in family(s)]
    ids = [ident(b[0]) for b in blocks]
    stairs = [ident(b[0]) for b in blocks if b[1] == "stairs"]
    slabs = [ident(b[0]) for b in blocks if b[1] == "slab"]
    walls = [ident(b[0]) for b in blocks if b[1] == "wall"]
    merge_tag(f"{DATA}/minecraft/tags/block/mineable/pickaxe.json", ids)
    for kind, vals in (("stairs", stairs), ("slabs", slabs), ("walls", walls)):
        merge_tag(f"{DATA}/minecraft/tags/block/{kind}.json", vals)
        merge_tag(f"{DATA}/minecraft/tags/item/{kind}.json", vals)
    overworld = [ident(s) for s in STONES if s != "scoria"]
    merge_tag(f"{DATA}/minecraft/tags/block/base_stone_overworld.json", overworld)
    merge_tag(f"{DATA}/minecraft/tags/block/base_stone_nether.json", [ident("scoria")])
    cobbled = [ident(f"cobbled_{s}") for s in STONES]
    for kind in ("item",):
        merge_tag(f"{DATA}/minecraft/tags/{kind}/stone_tool_materials.json", cobbled)
        merge_tag(f"{DATA}/minecraft/tags/{kind}/stone_crafting_materials.json", cobbled)
    # c: tags, one per stone and the parents, for blocks and items.
    for kind in ("block", "item"):
        for s in STONES:
            write(f"{DATA}/c/tags/{kind}/stones/{s}.json", {"values": [ident(s)]})
            write(f"{DATA}/c/tags/{kind}/cobblestones/{s}.json", {"values": [ident(f"cobbled_{s}")]})
        merge_tag(f"{DATA}/c/tags/{kind}/stones.json", [f"#c:stones/{s}" for s in STONES])
        merge_tag(f"{DATA}/c/tags/{kind}/cobblestones.json", [f"#c:cobblestones/{s}" for s in STONES])
    # Arcforge's own: the stones that burn to quicklime, and what fluxes steel.
    carbonates = []
    for s in CALCIUM_CARBONATE:
        carbonates += [f"#c:stones/{s}", f"#c:cobblestones/{s}"]
    write(f"{DATA}/{NS}/tags/item/calcium_carbonate_stones.json", {"values": carbonates})
    lime = []
    for s in LIME_STONES:
        lime += [f"#c:stones/{s}", f"#c:cobblestones/{s}"]
    write(f"{DATA}/{NS}/tags/item/lime_stones.json", {"values": lime})
    merge_tag(f"{DATA}/{NS}/tags/item/steel_fluxes.json", ["#arcforge:calcium_carbonate_stones", ident("quicklime")])


# --- Lang -----------------------------------------------------------------------------------------------------------

def lang():
    path = f"{ASSETS}/lang/en_us.json"
    with open(path) as f:
        d = json.load(f, object_pairs_hook=OrderedDict)
    new_blocks = OrderedDict()
    new_tips = OrderedDict()
    for s in STONES:
        for block_id, _, _, _ in family(s):
            new_blocks[f"block.{NS}.{block_id}"] = display_name(block_id, s)
        new_tips[f"tooltip.{NS}.info.{s}"] = USES[s]
        new_tips[f"tooltip.{NS}.info.cobbled_{s}"] = USES[s]
    item = OrderedDict([(f"item.{NS}.quicklime", "Quicklime")])
    new_tips[f"tooltip.{NS}.info.quicklime"] = ("Burnt lime: Limestone or Travertine calcined in the Chemical Reactor (Dolomite "
                                               "gives one, with its Magnesia). A steel flux; with sand and gravel it makes "
                                               "Concrete Powder, with water Slaked Lime, and with Coal Coke Calcium Carbide.")

    def insert_after(anchor, entries):
        out = OrderedDict()
        done = False
        for k, v in d.items():
            if k in entries:
                continue
            out[k] = v
            if k == anchor:
                out.update(entries)
                done = True
        if not done:
            out.update(entries)
        return out

    d = insert_after(f"block.{NS}.asphalt_stairs", new_blocks)
    d = insert_after(f"item.{NS}.slag", item)
    d = insert_after(f"tooltip.{NS}.info.asphalt_stairs", new_tips)
    with open(path, "w") as f:
        json.dump(d, f, indent=2, ensure_ascii=False)
        f.write("\n")


# --- Recipes --------------------------------------------------------------------------------------------------------

REC = f"{DATA}/{NS}/recipe/stone"


def shaped(name, pattern, key, result, count):
    write(f"{REC}/{name}.json", {"type": "minecraft:crafting_shaped", "category": "building", "key": key, "pattern": pattern,
                                 "result": {"count": count, "id": ident(result)}})


def cutting(src, result, count=1):
    write(f"{REC}/{result}_from_{src}_stonecutting.json",
          {"type": "minecraft:stonecutting", "ingredient": ident(src), "result": {"count": count, "id": ident(result)}})


def recipes(s):
    c, p, br = f"cobbled_{s}", f"polished_{s}", f"{s}_bricks"
    # Every form's stairs (4), slab (6) and wall (6), by crafting or the Stonecutter, as vanilla's stone families.
    for base, stem, _, _ in variants(s):
        stairs, slab, wall = f"{stem}_stairs", f"{stem}_slab", f"{stem}_wall"
        shaped(stairs, ["#  ", "## ", "###"], {"#": ident(base)}, stairs, 4)
        shaped(slab, ["###"], {"#": ident(base)}, slab, 6)
        shaped(wall, ["###", "###"], {"#": ident(base)}, wall, 6)
        cutting(base, stairs)
        cutting(base, slab, 2)
        cutting(base, wall)
    shaped(p, ["##", "##"], {"#": ident(s)}, p, 4)
    shaped(br, ["##", "##"], {"#": ident(p)}, br, 4)
    shaped(f"small_{s}_bricks", ["##", "##"], {"#": ident(br)}, f"small_{s}_bricks", 4)
    shaped(f"{s}_pillar", ["#", "#"], {"#": ident(p)}, f"{s}_pillar", 2)
    shaped(f"chiseled_{s}", ["#", "#"], {"#": ident(f"{p}_slab")}, f"chiseled_{s}", 1)
    # The Stonecutter takes the natural rock to every worked form and its shapes, and polished and bricks on down.
    worked = [v for v in variants(s) if v[0] not in (s, c)]
    for base, stem, _, _ in worked:
        cutting(s, base)
        cutting(s, f"{stem}_stairs")
        cutting(s, f"{stem}_slab", 2)
        cutting(s, f"{stem}_wall")
    for base, stem, _, _ in worked:
        if base in (p,):
            continue
        cutting(p, base)
        cutting(p, f"{stem}_stairs")
        cutting(p, f"{stem}_slab", 2)
        cutting(p, f"{stem}_wall")
    cutting(br, f"small_{s}_bricks")
    cutting(br, f"small_{s}_brick_stairs")
    cutting(br, f"small_{s}_brick_slab", 2)
    cutting(br, f"small_{s}_brick_wall")
    # Cobbled smelts back to the rock, as cobblestone to stone.
    write(f"{REC}/{s}_from_smelting_{c}.json", {"type": "minecraft:smelting", "category": "blocks", "cookingtime": 200,
                                                 "experience": 0.1, "ingredient": ident(c), "result": {"id": ident(s)}})


def crushing(name, stone, result, count, bonus=None, chance=None):
    r = OrderedDict(type=f"{NS}:crushing", ingredient=[ident(stone), ident(f"cobbled_{stone}")],
                    result={"count": count, "id": result})
    if bonus:
        r["bonus"] = {"count": 1, "id": bonus}
        r["bonus_chance"] = chance
    r["time"] = 200
    r["ore"] = False
    write(f"{DATA}/{NS}/recipe/crushing/{name}.json", r)


def uses():
    R = f"{DATA}/{NS}/recipe"
    # Calcining: CaCO3 -> CaO + CO2, heated in the Chemical Reactor. Limestone and Travertine; Dolomite has its own.
    write(f"{R}/chemical_reacting/quicklime_from_calcining.json", OrderedDict([
        ("type", f"{NS}:chemical_reacting"),
        ("item_input", {"ingredient": "#arcforge:lime_stones", "count": 1}),
        ("item_output", {"id": ident("quicklime"), "count": 2}),
        ("fluid_output", {"id": ident("carbon_dioxide"), "amount": 250}),
        ("time", 120), ("energy_per_tick", 40), ("heat_per_tick", 20), ("min_temperature", 900)]))
    # Concrete Powder from lime, sand and gravel: eight Light Gray, as cement is grey.
    write(f"{R}/crafting/light_gray_concrete_powder_from_quicklime.json", {
        "type": "minecraft:crafting_shapeless", "category": "building",
        "ingredients": [ident("quicklime"), "#minecraft:sand", "#minecraft:sand", "#minecraft:sand", "#minecraft:sand",
                        "#c:gravels", "#c:gravels", "#c:gravels", "#c:gravels"],
        "result": {"id": "minecraft:light_gray_concrete_powder", "count": 8}})
    # Dolomite refractory: Arcforge Furnace Bricks from dolomite bricks instead of nether bricks.
    write(f"{R}/crafting/arcforge_furnace_bricks_from_dolomite.json", {
        "type": "minecraft:crafting_shaped", "category": "building", "key": {"B": "minecraft:bricks", "D": ident("dolomite_bricks")},
        "pattern": ["BD", "DB"], "result": {"count": 4, "id": ident("arcforge_furnace_bricks")}})
    # Serpentine concrete: high bound water, the classic neutron shield.
    write(f"{R}/crafting/concrete_shielding_from_serpentine.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["CRC", "RCR", "CRC"],
        "key": {"C": ident("polished_serpentine"), "R": "#c:rods/steel"}, "result": {"id": ident("concrete_shielding"), "count": 5}})
    # Crushing.
    crushing("gravel_from_slate", "slate", "minecraft:gravel", 1, ident("silica_dust"), 0.25)
    crushing("gravel_from_serpentine", "serpentine", "minecraft:gravel", 1, ident("nickel_dust"), 0.15)
    crushing("potash_from_sylvinite", "sylvinite", ident("potash"), 2, ident("salt"), 0.5)
    crushing("gravel_from_kimberlite", "kimberlite", "minecraft:gravel", 1, "minecraft:diamond", 0.02)
    crushing("sand_from_pumice", "pumice", "minecraft:sand", 1, ident("silica_dust"), 0.3)
    # Scoria crushes to Volcanic Ash (was gravel), keeping its Iron Dust.
    old = f"{DATA}/{NS}/recipe/crushing/gravel_from_scoria.json"
    if os.path.exists(old):
        os.remove(old)
    crushing("volcanic_ash_from_scoria", "scoria", ident("volcanic_ash"), 2, ident("iron_dust"), 0.12)
    # Sifting Cobbled Kimberlite.
    write(f"{R}/sifting/cobbled_kimberlite.json", {"type": f"{NS}:sifting", "ingredient": ident("cobbled_kimberlite"), "outputs": [
        {"item": {"id": "minecraft:diamond"}, "chance": 0.03},
        {"item": {"id": "minecraft:flint"}, "chance": 0.15},
        {"item": {"id": ident("iron_dust")}, "chance": 0.08},
        {"item": {"id": ident("nickel_dust")}, "chance": 0.06},
        {"item": {"id": ident("garnet")}, "chance": 0.20},
        {"item": {"id": ident("olivine")}, "chance": 0.25}]})
    # Rock Wool from the volcanic stones.
    write(f"{R}/fiberizing/rock_wool_from_volcanic_stone.json", OrderedDict([
        ("type", f"{NS}:fiberizing"),
        ("ingredient", [ident("pumice"), ident("cobbled_pumice"), ident("scoria"), ident("cobbled_scoria")]),
        ("result", {"count": 2, "id": ident("rock_wool")}), ("time", 160), ("fe_per_tick", 30), ("hu_per_tick", 20),
        ("min_temp", 800)]))


# --- Worldgen -------------------------------------------------------------------------------------------------------

def worldgen(s):
    w = WORLD[s]
    target = w.get("target", OVERWORLD_TARGET)
    targets = [{"target": {"predicate_type": "minecraft:tag_match", "tag": target}, "state": ident(s)}]
    feature = OrderedDict()
    if w["kind"] == "blob":
        feature["type"] = f"{NS}:config_ore"
    elif w["kind"] == "layer":
        feature["type"] = f"{NS}:config_layer"
    else:
        feature["type"] = f"{NS}:config_pipe"
    feature["ore"] = s
    feature["size"] = w["size"]
    for k in ("radius", "layers", "tilt", "height"):
        if k in w:
            feature[k] = w[k]
    if w["kind"] == "blob":
        feature["discard_chance_on_air_exposure"] = 0.0
    feature["targets"] = targets
    write(f"{DATA}/{NS}/worldgen/feature/stone_{s}.json", feature)
    placement = []
    if w["rarity"] > 1:
        placement.append({"type": "minecraft:rarity_filter", "chance": w["rarity"]})
    placement += [{"type": f"{NS}:config_count", "ore": s, "default": 1 if s != "scoria" else 2},
                  {"type": "minecraft:in_square"},
                  {"type": f"{NS}:config_height", "ore": s, "shape": w["shape"], "default_min": w["min"], "default_max": w["max"]},
                  {"type": "minecraft:biome"}]
    write(f"{DATA}/{NS}/worldgen/placed_feature/stone_{s}.json", {"feature": ident(f"stone_{s}"), "placement": placement})
    write(f"{DATA}/{NS}/neoforge/biome_modifier/stone_{s}.json", OrderedDict([
        ("type", "neoforge:add_features"), ("biomes", w["biomes"]), ("features", ident(f"stone_{s}")),
        ("step", "underground_ores"), ("neoforge:conditions", [{"type": f"{NS}:ore_enabled", "ore": s}])]))


# --- Handbook -------------------------------------------------------------------------------------------------------

def handbook():
    entry = OrderedDict(title="Natural Stones", icon=ident("limestone_bricks"),
                        items=[ident(s) for s in STONES] + [ident("quicklime")], pages=[
        {"type": "text", "text": "Nine rocks lie in the world beside granite and diorite. Mined, each drops its cobbled form "
                                 "(Silk Touch keeps the rock), which smelts back. Every one comes polished, as bricks, small bricks, "
                                 "a pillar and a chiseled block, and every form in stairs, slabs and walls, by crafting or the "
                                 "Stonecutter. "
                                 "Their cobbles also make stone tools and furnaces."},
        {"type": "text", "text": "# Where they lie\nLimestone: big blobs, common, from Y 0 to 112. Dolomite: blobs from below "
                                 "sea level into the hills. Slate: wide tilted layers. Serpentine: deep, in and above the "
                                 "deepslate. Sylvinite: deep salt layers. Kimberlite: rare pipes rising from the bottom of the "
                                 "world. Pumice: high in mountains. Scoria: through the Nether. Travertine: layers near the "
                                 "surface in hot, dry biomes."},
        {"type": "text", "text": "# Lime\nLimestone, Travertine and Dolomite flux steel in the Arcforge Furnace. Calcined in a "
                                 "heated Chemical Reactor (900 °C) Limestone and Travertine give two Quicklime and 250 mB of "
                                 "Carbon Dioxide for your Greenhouse or Hydroponic Cells; Dolomite gives two Magnesia and one "
                                 "Quicklime (see Stone Chemistry). Quicklime fluxes steel too, and with four sand and four "
                                 "gravel makes eight Light Gray Concrete Powder."},
        {"type": "text", "text": "# The others\nSylvinite crushes into Potash (and Salt). Kimberlite hides diamonds: sift "
                                 "Cobbled Kimberlite, with Garnet and Olivine. Pumice and Scoria spin into Rock Wool; Scoria crushes to "
                                 "Volcanic Ash. Slate, Serpentine, Pumice and "
                                 "Scoria crush with a chance of Silica, Nickel or Iron Dust. Polished Serpentine casts "
                                 "Concrete Shielding, and Dolomite Bricks make Arcforge Furnace Bricks."},
    ])
    write(f"{ASSETS}/engineers_handbook/entries/natural_stones.json", entry)
    path = f"{ASSETS}/engineers_handbook/book.json"
    with open(path) as f:
        book = json.load(f, object_pairs_hook=OrderedDict)
    for chapter in book["chapters"]:
        if chapter["id"] == "materials" and "natural_stones" not in chapter["entries"]:
            i = chapter["entries"].index("ores") + 1 if "ores" in chapter["entries"] else len(chapter["entries"])
            chapter["entries"].insert(i, "natural_stones")
    with open(path, "w") as f:
        json.dump(book, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    for s in STONES:
        assets(s)
        loot(s)
        recipes(s)
        worldgen(s)
    tags()
    uses()
    lang()
    handbook()
    print("done")


if __name__ == "__main__":
    main()
