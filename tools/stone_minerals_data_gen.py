#!/usr/bin/env python3
# Copyright (c) 2026 Zagdrath
# SPDX-License-Identifier: MIT
"""Data for stone chemistry (ModMinerals): the minerals and products made from the natural stones.

Writes the item and block assets, loot, tags, recipes, lang, data maps and the Handbook entry "Stone Chemistry". Run from
the repo root after tools/stone_data_gen.py (it owns the stones' own recipes and, since this work, leaves Dolomite out of
the general calcining recipe, crushes Scoria to Volcanic Ash and sifts Garnet and Olivine from Kimberlite):

    python3 tools/stone_data_gen.py && python3 tools/stone_minerals_data_gen.py

Every file it writes is rewritten whole, except tags, lang and the shared data maps, which it merges.
"""

import json
import os
from collections import OrderedDict

from stone_data_gen import (ASSETS, DATA, NS, STONES, ident, merge_tag, stairs_blockstate, wall_blockstate, write)

R = f"{DATA}/{NS}/recipe"

# id, display name, info tooltip. Materials-tab order (ModMinerals.items()).
ITEMS = [
    ("soda_ash", "Soda Ash",
     "Sodium carbonate, from the Solvay process: a calcium carbonate stone, Brine and a little Ammonia in a heated Chemical "
     "Reactor. Fluxes sand into glass in the Arcforge Furnace (four sand, six glass) and dissolves in water into Lye."),
    ("calcium_chloride", "Calcium Chloride",
     "The Solvay process's by-product. A desiccant: it pulls water out of the air. Kept for drying recipes to come."),
    ("calcium_carbide", "Calcium Carbide",
     "Quicklime and Coal Coke fused at the Arcforge Furnace's hottest (1,550 °C). Dropped in water in the Chemical Reactor "
     "it gives Acetylene and Slaked Lime."),
    ("slaked_lime", "Slaked Lime",
     "Quicklime and water. Scrubs Sulfur Dioxide into Gypsum in the Chemical Reactor, and with Volcanic Ash, sand and "
     "gravel makes Pozzolan concrete."),
    ("gypsum", "Gypsum",
     "Slaked Lime that has scrubbed Sulfur Dioxide. A fertilizer on Loam Farmland, and with water it sets into Plaster."),
    ("magnesia", "Magnesia",
     "Magnesium oxide, calcined from Dolomite or Magnesite. Reduced with Ferrosilicon in the Arcforge Furnace (the Pidgeon "
     "process) it gives Magnesium, and with bricks it makes Magnesia Bricks."),
    ("magnesium_dust", "Magnesium Dust", "Ground Magnesium. Smelts back into an ingot."),
    ("magnesium_ingot", "Magnesium Ingot",
     "A light metal from the Pidgeon process: Magnesia and Ferrosilicon in the Arcforge Furnace. It can stand in for the "
     "steel in a Tempered Jetpack and the gear in Tempered Arc Tools."),
    ("magnesium_nugget", "Magnesium Nugget", "A ninth of a Magnesium Ingot."),
    ("potassium_hydroxide", "Potassium Hydroxide",
     "Caustic potash, dried out of Potassium Hydroxide Solution in the Thermal Evaporator Array. The solution serves "
     "wherever Lye does."),
    ("magnesite", "Magnesite",
     "Magnesium carbonate, made by locking Carbon Dioxide into Serpentine or Olivine in the Chemical Reactor. The CO2 stays "
     "locked away, unless you calcine it back out for Magnesia."),
    ("garnet", "Garnet", "A hard red gem sifted from Cobbled Kimberlite. Two on Paper make four Sandpaper."),
    ("sandpaper", "Sandpaper",
     "Garnet grit on Paper. Crafted with a natural stone it polishes it: one stone, one Polished stone, without the "
     "Stonecutter or four blocks of stone."),
    ("olivine", "Olivine",
     "A green gem sifted from Cobbled Kimberlite. Locks Carbon Dioxide into Magnesite, and with Clay makes Foundry Sand."),
    ("foundry_sand", "Foundry Sand",
     "Olivine sand bound with Clay. It moulds the Rod, Plate, Gear and Pellet Dies with less steel."),
    ("mica", "Mica",
     "Thin heat-proof sheets sifted from Cobbled Slate and Cobbled Kimberlite. An insulator: it can stand in for the "
     "bottom tungsten plate of a Tungsten Heating Coil."),
    ("chromite", "Chromite",
     "Chromium iron oxide, sifted from Cobbled Serpentine and Cobbled Kimberlite. Chromium for stainless steel, in time."),
    ("volcanic_ash", "Volcanic Ash",
     "Crushed Scoria. A weak fertilizer, and with Slaked Lime, sand and gravel it makes Pozzolan concrete."),
    ("agricultural_lime", "Agricultural Lime",
     "Crushed Limestone or Travertine. It sweetens sour soil: a fertilizer on Loam Farmland."),
]
FERTILIZERS = ["gypsum", "volcanic_ash", "agricultural_lime"]
# c:dusts/<name> for each dust.
DUSTS = OrderedDict([("soda_ash", "soda_ash"), ("calcium_chloride", "calcium_chloride"), ("slaked_lime", "slaked_lime"),
                     ("gypsum", "gypsum"), ("magnesia", "magnesia"), ("magnesium_dust", "magnesium"),
                     ("potassium_hydroxide", "potassium_hydroxide"), ("magnesite", "magnesite"), ("chromite", "chromite"),
                     ("volcanic_ash", "volcanic_ash"), ("agricultural_lime", "agricultural_lime")])

# Block families: (base id, stem, display name, texture under block/).
FAMILIES = [("plaster", "plaster", "Plaster", "stone/plaster"),
            ("magnesia_bricks", "magnesia_brick", "Magnesia Brick", "stone/magnesia_bricks")]
FAMILY_TIPS = {
    "plaster": "Gypsum set with water: a smooth white building block, soft to mine.",
    "magnesia_bricks": "Magnesia fired with clay bricks: a pale refractory brick.",
}

FLUIDS = [  # id, name, info for the bucket (None: a gas, no bucket)
    ("acetylene", "Acetylene", None),
    ("potash_brine", "Potash Brine",
     "Potash dissolved in water. The Electrolyzer splits it into Hydrogen, Chlorine and Potassium Hydroxide Solution."),
    ("potassium_hydroxide_solution", "Potassium Hydroxide Solution",
     "Caustic potash, from electrolysing Potash Brine. Serves wherever Lye does (#arcforge:alkalis). Burns anything that "
     "wades in."),
]


def block_list():
    """(block id, shape, texture, base block)"""
    out = [("magnesium_block", "cube", "magnesium_block", None)]
    for base, stem, _, t in FAMILIES:
        out += [(base, "cube", t, None), (f"{stem}_stairs", "stairs", t, base), (f"{stem}_slab", "slab", t, base),
                (f"{stem}_wall", "wall", t, base)]
    return out


def block_names():
    names = OrderedDict([("magnesium_block", "Block of Magnesium")])
    for base, stem, name, _ in FAMILIES:
        names[base] = {"magnesia_bricks": "Magnesia Bricks"}.get(base, name)
        for shape in ("stairs", "slab", "wall"):
            names[f"{stem}_{shape}"] = f"{name} {shape.capitalize()}"
    return names


def btex(t):
    return f"{NS}:block/{t}"


# --- Assets ---------------------------------------------------------------------------------------------------------

def assets():
    for i, _, _ in ITEMS:
        write(f"{ASSETS}/models/item/{i}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": ident(f"item/{i}")}})
        write(f"{ASSETS}/items/{i}.json", {"model": {"type": "minecraft:model", "model": ident(f"item/{i}")}})
    models = f"{ASSETS}/models/block"
    for block_id, shape, t, base in block_list():
        bs = f"{ASSETS}/blockstates/{block_id}.json"
        item_model = ident(f"block/{block_id}")
        three = {"bottom": btex(t), "side": btex(t), "top": btex(t)}
        if shape == "cube":
            write(bs, {"variants": {"": {"model": ident(f"block/{block_id}")}}})
            write(f"{models}/{block_id}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": btex(t)}})
        elif shape == "stairs":
            write(bs, stairs_blockstate(block_id))
            write(f"{models}/{block_id}.json", {"parent": "minecraft:block/stairs", "textures": three})
            write(f"{models}/{block_id}_inner.json", {"parent": "minecraft:block/inner_stairs", "textures": three})
            write(f"{models}/{block_id}_outer.json", {"parent": "minecraft:block/outer_stairs", "textures": three})
        elif shape == "slab":
            write(bs, {"variants": {"type=bottom": {"model": ident(f"block/{block_id}")},
                                    "type=double": {"model": ident(f"block/{base}")},
                                    "type=top": {"model": ident(f"block/{block_id}_top")}}})
            write(f"{models}/{block_id}.json", {"parent": "minecraft:block/slab", "textures": three})
            write(f"{models}/{block_id}_top.json", {"parent": "minecraft:block/slab_top", "textures": three})
        elif shape == "wall":
            write(bs, wall_blockstate(block_id))
            for suffix, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"),
                                   ("_side_tall", "template_wall_side_tall"), ("_inventory", "wall_inventory")):
                write(f"{models}/{block_id}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": {"wall": btex(t)}})
            item_model = ident(f"block/{block_id}_inventory")
        write(f"{ASSETS}/items/{block_id}.json", {"model": {"type": "minecraft:model", "model": item_model}})
    # The two liquids: blockstate, particle model, bucket.
    for f, _, info in FLUIDS:
        if info is None:
            continue
        write(f"{ASSETS}/blockstates/{f}.json", {"variants": {"": {"model": ident(f"block/{f}")}}})
        write(f"{models}/{f}.json", {"textures": {"particle": ident(f"block/fluid/{f}_still")}})
        write(f"{ASSETS}/items/{f}_bucket.json", {"model": {"type": "neoforge:fluid_container", "fluid": ident(f), "textures": {
            "base": "minecraft:item/bucket", "fluid": "neoforge:item/mask/bucket_fluid_drip"}}})


# --- Loot -----------------------------------------------------------------------------------------------------------

def loot():
    for block_id, shape, _, _ in block_list():
        path = f"{DATA}/{NS}/loot_table/blocks/{block_id}.json"
        if shape == "slab":
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

def tags():
    blocks = block_list()
    merge_tag(f"{DATA}/minecraft/tags/block/mineable/pickaxe.json", [ident(b[0]) for b in blocks])
    merge_tag(f"{DATA}/minecraft/tags/block/needs_stone_tool.json", [ident("magnesium_block")])
    merge_tag(f"{DATA}/minecraft/tags/block/beacon_base_blocks.json", [ident("magnesium_block")])
    merge_tag(f"{DATA}/minecraft/tags/item/beacon_payment_items.json", [ident("magnesium_ingot")])
    for kind, shape in (("stairs", "stairs"), ("slabs", "slab"), ("walls", "wall")):
        vals = [ident(b[0]) for b in blocks if b[1] == shape]
        merge_tag(f"{DATA}/minecraft/tags/block/{kind}.json", vals)
        merge_tag(f"{DATA}/minecraft/tags/item/{kind}.json", vals)
    C = f"{DATA}/c/tags/item"
    for item, name in DUSTS.items():
        write(f"{C}/dusts/{name}.json", {"values": [ident(item)]})
    merge_tag(f"{C}/dusts.json", [f"#c:dusts/{n}" for n in DUSTS.values()])
    write(f"{C}/ingots/magnesium.json", {"values": [ident("magnesium_ingot")]})
    merge_tag(f"{C}/ingots.json", ["#c:ingots/magnesium"])
    write(f"{C}/nuggets/magnesium.json", {"values": [ident("magnesium_nugget")]})
    merge_tag(f"{C}/nuggets.json", ["#c:nuggets/magnesium"])
    for kind in ("item", "block"):
        write(f"{DATA}/c/tags/{kind}/storage_blocks/magnesium.json", {"values": [ident("magnesium_block")]})
        merge_tag(f"{DATA}/c/tags/{kind}/storage_blocks.json", ["#c:storage_blocks/magnesium"])
    for gem in ("garnet", "olivine"):
        write(f"{C}/gems/{gem}.json", {"values": [ident(gem)]})
    merge_tag(f"{C}/gems.json", ["#c:gems/garnet", "#c:gems/olivine"])
    write(f"{C}/mica.json", {"values": [ident("mica")]})
    merge_tag(f"{DATA}/{NS}/tags/item/insulators.json", ["#c:mica"])
    merge_tag(f"{DATA}/{NS}/tags/item/fertilizers.json", [ident(f) for f in FERTILIZERS])
    for f, _, info in FLUIDS:
        write(f"{DATA}/c/tags/fluid/{f}.json", {"values": [ident(f), ident(f"flowing_{f}")]})
        if info is not None:
            write(f"{C}/buckets/{f}.json", {"values": [ident(f"{f}_bucket")]})
    merge_tag(f"{C}/buckets.json", [f"#c:buckets/{f}" for f, _, info in FLUIDS if info is not None])
    # Lye and caustic potash, interchangeable in every recipe that wants an alkali.
    write(f"{DATA}/{NS}/tags/fluid/alkalis.json", {"values": ["#c:lye", "#c:potassium_hydroxide_solution"]})
    merge_tag(f"{DATA}/{NS}/tags/fluid/gases.json", [ident("acetylene")])


# --- Data maps ------------------------------------------------------------------------------------------------------

def merge_map(path, key, value):
    with open(path) as f:
        d = json.load(f, object_pairs_hook=OrderedDict)
    d["values"][key] = value
    with open(path, "w") as f:
        json.dump(d, f, indent=2, ensure_ascii=False)
        f.write("\n")


def data_maps():
    maps = f"{DATA}/{NS}/data_maps/fluid"
    # The hottest fuel there is, just above Hydrogen (60 HU/mB at 1,400 °C). Config refineryFuels.acetylene* overrides.
    merge_map(f"{maps}/burner_fuels.json", ident("acetylene"),
              OrderedDict(hu_per_mb=70, mb_per_tick=1.0, burn_temperature=1450))
    merge_map(f"{maps}/gas_properties.json", ident("acetylene"), {"color": "#F2EEE4"})


# --- Recipes --------------------------------------------------------------------------------------------------------

def reacting(name, **fields):
    r = OrderedDict(type=f"{NS}:chemical_reacting")
    r.update(fields)
    write(f"{R}/chemical_reacting/{name}.json", r)


def inp(ingredient, count=1):
    return {"ingredient": ingredient, "count": count}


def fl(fluid, amount):
    return {"tag": fluid[1:], "amount": amount} if fluid.startswith("#") else {"fluid": fluid, "amount": amount}


def out(item, count=1):
    return {"id": item if ":" in item else ident(item), "count": count}


def fout(fluid, amount):
    return {"id": fluid if ":" in fluid else ident(fluid), "amount": amount}


def furnace(name, metal, metal_count, result, count, *, additive=None, coke=1, byproduct=None, time=400, min_temp=1200, xp=0.5):
    r = OrderedDict(type=f"{NS}:arcforge_smelting", metal=inp(metal, metal_count))
    if additive:
        r["additive"] = inp(*additive)
    r["coke"] = coke
    r["result"] = out(result, count)
    if byproduct:
        r["byproduct"] = out(*byproduct)
    r["time"] = time
    r["min_temp"] = min_temp
    r["experience"] = xp
    write(f"{R}/arcforge_smelting/{name}.json", r)


def crafting(name, obj):
    write(f"{R}/crafting/{name}.json", obj)


def shaped(name, pattern, key, result, count=1, category="misc"):
    crafting(name, OrderedDict(type="minecraft:crafting_shaped", category=category, pattern=pattern, key=key,
                               result=out(result, count)))


def shapeless(name, ingredients, result, count=1, category="misc"):
    crafting(name, OrderedDict(type="minecraft:crafting_shapeless", category=category, ingredients=ingredients,
                               result=out(result, count)))


def crushing(name, ingredient, result, count, bonus=None, chance=None):
    r = OrderedDict(type=f"{NS}:crushing", ingredient=ingredient, result={"count": count, "id": ident(result)})
    if bonus:
        r["bonus"] = {"count": 1, "id": bonus}
        r["bonus_chance"] = chance
    r["time"] = 200
    r["ore"] = False
    write(f"{R}/crushing/{name}.json", r)


def sifting(name, ingredient, outputs):
    write(f"{R}/sifting/{name}.json", OrderedDict(type=f"{NS}:sifting", ingredient=ident(ingredient),
                                                  outputs=[{"item": {"id": i}, "chance": c} for i, c in outputs]))


WATER = "#minecraft:water"


def recipes():
    # §1 Solvay: CaCO3 + 2 NaCl (brine) -> Na2CO3 + CaCl2, the ammonia carried round (only a little lost).
    reacting("soda_ash_from_solvay", item_input=inp("#arcforge:calcium_carbonate_stones"),
             fluid_inputs=[fl("#c:brine", 1000), fl("arcforge:ammonia", 100)],
             item_output=out("soda_ash", 2), byproduct=out("calcium_chloride"),
             time=120, energy_per_tick=40, heat_per_tick=10, min_temperature=200)
    furnace("glass_from_soda_ash", "#c:sands", 4, "minecraft:glass", 6, additive=(ident("soda_ash"), 1), coke=0,
            time=200, min_temp=1200, xp=0.1)
    reacting("lye_from_soda_ash", item_input=inp(ident("soda_ash")), fluid_inputs=[fl(WATER, 500)],
             fluid_output=fout("lye", 500), time=80, energy_per_tick=20)
    # §2 Calcium Carbide: CaO + 3 C -> CaC2 + CO, the furnace's hottest, longest job. CaC2 + 2 H2O -> C2H2 + Ca(OH)2.
    furnace("calcium_carbide", ident("quicklime"), 2, "calcium_carbide", 1, coke=2, time=800, min_temp=1550, xp=0.7)
    reacting("acetylene_from_calcium_carbide", item_input=inp(ident("calcium_carbide")), fluid_inputs=[fl(WATER, 500)],
             fluid_output=fout("acetylene", 500), byproduct=out("slaked_lime"), time=80, energy_per_tick=20)
    # §3 Slaking and scrubbing.
    reacting("slaked_lime", item_input=inp(ident("quicklime")), fluid_inputs=[fl(WATER, 250)],
             item_output=out("slaked_lime"), time=60, energy_per_tick=20)
    reacting("gypsum_from_scrubbing", item_input=inp(ident("slaked_lime")), fluid_inputs=[fl("#c:sulfur_dioxide", 250)],
             item_output=out("gypsum"), time=80, energy_per_tick=30)
    shaped("plaster", ["GG", "GG", "W "], {"G": ident("gypsum"), "W": "minecraft:water_bucket"}, "plaster", 4, "building")
    # §4 Dolomite: CaMg(CO3)2 -> CaO + MgO + CO2. Pidgeon: 2 MgO + Si (ferrosilicon) -> 2 Mg + SiO2 (slag).
    reacting("magnesia_from_calcining_dolomite", item_input=inp("#arcforge:dolomite_stones"),
             item_output=out("magnesia", 2), byproduct=out("quicklime"), fluid_output=fout("carbon_dioxide", 250),
             time=120, energy_per_tick=40, heat_per_tick=20, min_temperature=900)
    furnace("magnesium_from_pidgeon", ident("magnesia"), 2, "magnesium_ingot", 2, additive=(ident("ferrosilicon"), 1),
            coke=1, byproduct=(ident("slag"), 1), time=600, min_temp=1500, xp=0.7)
    shaped("magnesium_block", ["###", "###", "###"], {"#": "#c:ingots/magnesium"}, "magnesium_block", 1, "building")
    shapeless("magnesium_ingot_from_magnesium_block", ["#c:storage_blocks/magnesium"], "magnesium_ingot", 9)
    shaped("magnesium_ingot_from_nuggets", ["###", "###", "###"], {"#": "#c:nuggets/magnesium"}, "magnesium_ingot")
    shapeless("magnesium_nugget", ["#c:ingots/magnesium"], "magnesium_nugget", 9)
    for kind, time in (("smelting", 200), ("blasting", 100)):
        write(f"{R}/magnesium_ingot_from_{kind}_dust.json", OrderedDict(
            type=f"minecraft:{kind}", category="misc", cookingtime=time, experience=0.7, ingredient="#c:dusts/magnesium",
            result={"id": ident("magnesium_ingot")}))
    crushing("magnesium_dust_from_magnesium_ingot", "#c:ingots/magnesium", "magnesium_dust", 1)
    jetpack = json.load(open(f"{R}/crafting/tempered_jetpack.json"), object_pairs_hook=OrderedDict)
    jetpack["key"]["X"] = "#c:ingots/magnesium"
    crafting("tempered_jetpack_from_magnesium", jetpack)
    for tool in ("tempered_arc_drill", "tempered_arc_saw"):
        r = json.load(open(f"{R}/crafting/{tool}.json"), object_pairs_hook=OrderedDict)
        r["key"]["G"] = "#c:ingots/magnesium"
        crafting(f"{tool}_from_magnesium", r)
    # 4 Magnesia + 4 Bricks, chequered round an empty middle.
    shaped("magnesia_bricks", ["MBM", "B B", "MBM"], {"M": ident("magnesia"), "B": "minecraft:brick"}, "magnesia_bricks", 8,
           "building")
    # §5 Caustic potash: KCl brine through the chlor-alkali cell, as Brine for Lye.
    reacting("potash_brine", item_input=inp(ident("potash")), fluid_inputs=[fl(WATER, 250)],
             fluid_output=fout("potash_brine", 250), time=60)
    write(f"{R}/electrolyzing/chlorine_hydrogen_and_potassium_hydroxide_from_potash_brine.json", OrderedDict(
        type=f"{NS}:electrolyzing", input={"fluid": ident("potash_brine"), "amount": 100},
        primary=fout("hydrogen", 50), secondary=fout("chlorine", 50), tertiary=fout("potassium_hydroxide_solution", 100),
        energy=60000))
    write(f"{R}/evaporating/potassium_hydroxide.json", OrderedDict(
        type=f"{NS}:evaporating", input={"tag": "c:potassium_hydroxide_solution", "amount": 250},
        item_result={"id": ident("potassium_hydroxide")}, water=200, heat=1800))
    # Every Lye recipe takes either alkali.
    for name in ("sodium_silicate", "biodiesel_with_lye", "thorium_dioxide", "lithium_hydroxide"):
        path = f"{R}/chemical_reacting/{name}.json"
        with open(path) as f:
            r = json.load(f, object_pairs_hook=OrderedDict)
        r["fluid_inputs"] = [OrderedDict([("tag", "arcforge:alkalis"), ("amount", x["amount"])])
                             if x.get("fluid") == ident("lye") else x for x in r["fluid_inputs"]]
        write(path, r)
    # §6 Mineral carbonation: Mg3Si2O5(OH)4 + 3 CO2 -> 3 MgCO3 + 2 SiO2; Mg2SiO4 + 2 CO2 -> 2 MgCO3 + SiO2.
    reacting("magnesite_from_serpentine", item_input=inp("#arcforge:serpentine_stones"), fluid_inputs=[fl("arcforge:carbon_dioxide", 500)],
             item_output=out("magnesite", 2), byproduct=out("silica_dust"),
             time=160, energy_per_tick=40, heat_per_tick=10, min_temperature=150)
    reacting("magnesite_from_olivine", item_input=inp("#c:gems/olivine"), fluid_inputs=[fl("arcforge:carbon_dioxide", 500)],
             item_output=out("magnesite", 2), time=160, energy_per_tick=40, heat_per_tick=10, min_temperature=150)
    reacting("magnesia_from_calcining_magnesite", item_input=inp("#c:dusts/magnesite"), item_output=out("magnesia"),
             fluid_output=fout("carbon_dioxide", 250), time=100, energy_per_tick=40, heat_per_tick=20, min_temperature=700)
    # §7-10 Sifting (Kimberlite's in stone_data_gen.py: Garnet .20 and Olivine .25 join its four).
    sifting("cobbled_slate", "cobbled_slate", [(ident("mica"), 0.30), (ident("silica_dust"), 0.20)])
    sifting("cobbled_serpentine", "cobbled_serpentine", [(ident("chromite"), 0.12), (ident("nickel_dust"), 0.05)])
    # Kimberlite's Mica (10%) and Chromite (8%) would make eight outputs; the Sifter has six. They come from Slate and
    # Serpentine above instead.
    shapeless("sandpaper", ["minecraft:paper", "#c:gems/garnet", "#c:gems/garnet"], "sandpaper", 4)
    for s in STONES:
        shapeless(f"polished_{s}_from_sandpaper", [ident(s), ident("sandpaper")], f"polished_{s}", 1, "building")
    shapeless("foundry_sand", ["#c:gems/olivine"] * 4 + ["minecraft:clay_ball"], "foundry_sand", 4)
    for die in ("rod_die", "plate_die", "gear_die", "pellet_die"):
        path = f"{R}/{die}.json" if os.path.exists(f"{R}/{die}.json") else f"{R}/crafting/{die}.json"
        with open(path) as f:
            r = json.load(f, object_pairs_hook=OrderedDict)
        key = OrderedDict(r["key"])
        key["F"] = ident("foundry_sand")
        pattern = list(r["pattern"])
        # SSS / SXS / BBB -> FSF / FXF / BBB: Foundry Sand moulds where four of the five ingots were.
        pattern[0] = "FSF"
        pattern[1] = pattern[1].replace("S", "F", 2)
        r["key"] = key
        r["pattern"] = pattern
        crafting(f"{die}_from_foundry_sand", r)
    shaped("tungsten_heating_coil_from_mica", [" T ", "TRT", " M "],
           {"T": "#c:plates/tungsten", "R": "#c:rods/copper", "M": "#c:mica"}, "tungsten_heating_coil", 2)
    # §11 Volcanic Ash (crushing Scoria, in stone_data_gen.py), Pozzolan and Agricultural Lime.
    shapeless("light_gray_concrete_powder_from_pozzolan",
              ["#c:dusts/volcanic_ash", "#c:dusts/slaked_lime", "#minecraft:sand", "#minecraft:sand", "#minecraft:sand",
               "#c:gravels", "#c:gravels", "#c:gravels"], "minecraft:light_gray_concrete_powder", 8, "building")
    crushing("agricultural_lime_from_limestone", "#arcforge:limestone_stones", "agricultural_lime", 2)
    crushing("agricultural_lime_from_travertine", "#arcforge:travertine_stones", "agricultural_lime", 2)
    family_recipes()


def family_recipes():
    base_dir = f"{R}/stone"
    for base, stem, _, _ in FAMILIES:
        stairs, slab, wall = f"{stem}_stairs", f"{stem}_slab", f"{stem}_wall"
        for name, pattern, result, count in ((stairs, ["#  ", "## ", "###"], stairs, 4), (slab, ["###"], slab, 6),
                                             (wall, ["###", "###"], wall, 6)):
            write(f"{base_dir}/{name}.json", OrderedDict(type="minecraft:crafting_shaped", category="building",
                                                         key={"#": ident(base)}, pattern=pattern,
                                                         result={"count": count, "id": ident(result)}))
        for result, count in ((stairs, 1), (slab, 2), (wall, 1)):
            write(f"{base_dir}/{result}_from_{base}_stonecutting.json",
                  {"type": "minecraft:stonecutting", "ingredient": ident(base), "result": {"count": count, "id": ident(result)}})


def stone_tags():
    """Per-stone natural+cobbled tags the recipes above use."""
    for s in ("limestone", "travertine", "dolomite", "serpentine"):
        write(f"{DATA}/{NS}/tags/item/{s}_stones.json", {"values": [f"#c:stones/{s}", f"#c:cobblestones/{s}"]})


# --- Lang -----------------------------------------------------------------------------------------------------------

def lang():
    path = f"{ASSETS}/lang/en_us.json"
    with open(path) as f:
        d = json.load(f, object_pairs_hook=OrderedDict)
    entries = OrderedDict()
    for i, name, info in ITEMS:
        entries[f"item.{NS}.{i}"] = name
        entries[f"tooltip.{NS}.info.{i}"] = info
    for block_id, name in block_names().items():
        entries[f"block.{NS}.{block_id}"] = name
    entries[f"tooltip.{NS}.info.magnesium_block"] = "Nine Magnesium Ingots."
    for base, stem, _, _ in FAMILIES:
        for b in (base, f"{stem}_stairs", f"{stem}_slab", f"{stem}_wall"):
            entries[f"tooltip.{NS}.info.{b}"] = FAMILY_TIPS[base]
    for f, name, info in FLUIDS:
        entries[f"fluid_type.{NS}.{f}"] = name
        if info is not None:
            entries[f"block.{NS}.{f}"] = name
            entries[f"item.{NS}.{f}_bucket"] = f"{name} Bucket"
            entries[f"tooltip.{NS}.info.{f}_bucket"] = info
    # Quicklime's text named Dolomite as a source; Dolomite now calcines to Magnesia (with one Quicklime).
    d[f"tooltip.{NS}.info.quicklime"] = ("Burnt lime: Limestone or Travertine calcined in the Chemical Reactor (Dolomite "
                                        "gives one, with its Magnesia). A steel flux; with sand and gravel it makes "
                                        "Concrete Powder, with water Slaked Lime, and with Coal Coke Calcium Carbide.")
    out_d = OrderedDict()
    for k, v in d.items():
        if k in entries:
            continue
        out_d[k] = v
        if k == f"tooltip.{NS}.info.quicklime":
            out_d.update(entries)
    with open(path, "w") as f:
        json.dump(out_d, f, indent=2, ensure_ascii=False)
        f.write("\n")


# --- Handbook -------------------------------------------------------------------------------------------------------

def handbook():
    pages = [
        "The natural stones are a chemistry set: lime, soda, magnesium and the minerals sifted out of the cobbles, all "
        "with machines you already have. Every page here is one chain. Where a recipe needs heat, it's the Chemical "
        "Reactor with a heat source on its Heat Input, as for calcining.",
        "# Soda Ash (Solvay)\n- 1 Limestone, Travertine or Dolomite + 1,000 mB Brine + 100 mB Ammonia, at 200 °C and "
        "10 HU/t: 2 Soda Ash and 1 Calcium Chloride.\n- Arcforge Furnace: 4 sand + 1 Soda Ash, no coke: 6 Glass.\n- 1 Soda "
        "Ash + 500 mB Water: 500 mB Lye, without the Electrolyzer.\n- Calcium Chloride is a desiccant, kept for drying.",
        "# Carbide and Acetylene\n- Arcforge Furnace at 1,550 °C (its hottest): 2 Quicklime + 2 Coal Coke make 1 Calcium "
        "Carbide over 40 s. It eats power.\n- 1 Calcium Carbide + 500 mB Water: 500 mB Acetylene and 1 Slaked Lime.\n- "
        "Acetylene is a gas and the hottest fuel: 70 HU/mB at 1,450 °C, 1 mB/t, in the Fuel Burner and Firebox Array, and "
        "with Oxygen in oxy-fuel.",
        "# Lime and Gypsum\n- 1 Quicklime + 250 mB Water: 1 Slaked Lime.\n- 1 Slaked Lime + 250 mB Sulfur Dioxide: 1 "
        "Gypsum, scrubbing the gas.\n- Gypsum is a fertilizer (3 nutrients).\n- 4 Gypsum + a Water Bucket: 4 Plaster, with "
        "stairs, slabs and walls.",
        "# Magnesium\n- Dolomite now calcines on its own: 2 Magnesia + 1 Quicklime + 250 mB Carbon Dioxide (900 °C).\n- "
        "Pidgeon process, Arcforge Furnace at 1,500 °C: 2 Magnesia + 1 Ferrosilicon + 1 Coal Coke: 2 Magnesium Ingots and "
        "Slag.\n- Magnesium stands in for the steel plate in a Tempered Jetpack and the steel gear in Tempered Arc Tools.\n"
        "- 4 Magnesia + 4 Bricks: 8 Magnesia Bricks.",
        "# Caustic Potash\n- 1 Potash + 250 mB Water: 250 mB Potash Brine.\n- The Electrolyzer splits 100 mB of it into "
        "50 mB Hydrogen, 50 mB Chlorine and 100 mB Potassium Hydroxide Solution (60,000 FE).\n- The Thermal Evaporator "
        "Array dries 250 mB into 1 Potassium Hydroxide.\n- The solution serves wherever Lye does.",
        "# Carbonation\n- Serpentine (natural or cobbled) + 500 mB Carbon Dioxide at 150 °C: 2 Magnesite and 1 Silica "
        "Dust.\n- 1 Olivine + 500 mB Carbon Dioxide: 2 Magnesite.\n- The CO2 stays locked in the Magnesite. Calcined at "
        "700 °C it comes back out, with 1 Magnesia.",
        "# Sifted minerals\n- Cobbled Kimberlite: Diamond 3%, Flint 15%, Iron Dust 8%, Nickel Dust 6%, Garnet 20%, "
        "Olivine 25%.\n- Cobbled Slate: Mica 30%, Silica Dust 20%.\n- Cobbled Serpentine: Chromite 12%, Nickel Dust 5%.\n"
        "- Paper + 2 Garnet: 4 Sandpaper. A stone + Sandpaper: its Polished form.\n- 4 Olivine + 1 Clay: 4 Foundry Sand, "
        "for dies with less steel.\n- Mica can replace the bottom plate of a Tungsten Heating Coil.",
        "# Ash and lime for the fields\n- Scoria now crushes into 2 Volcanic Ash (still 12% Iron Dust): a fertilizer (1).\n"
        "- Volcanic Ash + Slaked Lime + 3 sand + 3 gravel: 8 Light Gray Concrete Powder (Pozzolan).\n- Limestone or "
        "Travertine crushed: 2 Agricultural Lime, which sweetens the soil (3).",
    ]
    entry = OrderedDict(title="Stone Chemistry", icon=ident("soda_ash"),
                        items=[ident(i) for i, _, _ in ITEMS] + [ident("magnesium_block"), ident("plaster"),
                                                                  ident("magnesia_bricks"), ident("potash_brine_bucket"),
                                                                  ident("potassium_hydroxide_solution_bucket")],
                        pages=[{"type": "text", "text": t} for t in pages])
    write(f"{ASSETS}/engineers_handbook/entries/stone_chemistry.json", entry)
    path = f"{ASSETS}/engineers_handbook/book.json"
    with open(path) as f:
        book = json.load(f, object_pairs_hook=OrderedDict)
    for chapter in book["chapters"]:
        if chapter["id"] == "materials" and "stone_chemistry" not in chapter["entries"]:
            chapter["entries"].insert(chapter["entries"].index("natural_stones") + 1, "stone_chemistry")
    with open(path, "w") as f:
        json.dump(book, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    assets()
    loot()
    tags()
    stone_tags()
    data_maps()
    recipes()
    lang()
    handbook()
    print("done")


if __name__ == "__main__":
    main()
