"""Export Arcforge's quest chapters as Hibiscus Quests chapters shipped as data.

    python tools/hibiscusquests/export_hibiscus.py <arcforge repo> [--check-only]

Reads the chapter modules (ch_*.py, written with dsl.py), validates every item id against the repo's
assets/arcforge/items/ and every dependency and chapter gate, lays the nodes out, and writes:
    src/main/resources/data/arcforge/hibiscusquests/chapter_groups.snbt
    src/main/resources/data/arcforge/hibiscusquests/chapters/arcforge_<chapter>.snbt
    docs/QUESTLINE.md                every quest, for review
The file format is Hibiscus Quests' (docs/API.md in its repo); every key this writes comes from chapter_snbt,
quest_snbt, task_snbt and reward_snbt. Chapter and group ids are shared by every mod in the book, so they get the
arcforge_ prefix here; the modules use the short ids ("steelmaking/graphite").
"""
import importlib, json, os, sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dsl import rid  # noqa: E402

PREFIX = "arcforge_"
# Chapter order, its group, the gate and its one-line subtitle. The gate isn't a chapter requires (Hibiscus Quests
# doesn't show a gated chapter's map at all): it's added to the dependencies of the chapter's first quests (those with
# no dependency in the chapter), so every chapter opens and shows what's to come, greyed out until it unlocks.
CHAPTERS = [
    # module,            group,         requires,                                 subtitle
    ("ch_start",         "foundations", [],                                       "From bare hands to steel and a working grid"),
    ("ch_steel",         "foundations", ["getting_started/steel_ingot"],          "The four tier alloys and Graphite"),
    ("ch_processing",    "foundations", ["getting_started/steel_parts"],          "New ores, crushing, sifting, arrays and parts"),
    ("ch_heat",          "foundations", ["getting_started/thermoelectric_plant"], "Fireboxes, heat conduits and generators"),
    ("ch_logistics",     "foundations", ["getting_started/wrought_alloy"],        "Conduits, cells, tanks and storage"),
    ("ch_gear",          "foundations", ["getting_started/steel_ingot"],          "Steel tools, Arc Drills, armour and jetpacks"),
    ("ch_automation",    "foundations", ["getting_started/powered_up"],           "Machines that run themselves"),
    ("ch_steam",         "power",       ["steelmaking/tempered_alloy"],           "Boilers, superheaters, turbines and condensers"),
    ("ch_fuelpower",     "power",       ["steelmaking/hardened_alloy"],           "Gas turbines and diesel generators"),
    ("ch_oil",           "petroleum",   ["steelmaking/tempered_alloy"],           "Surveys, pumpjacks, derricks and gas wells"),
    ("ch_refinery",      "petroleum",   ["steelmaking/tempered_alloy"],           "Distillation, cracking and refinery units"),
    ("ch_chemistry",     "chemistry",   ["steelmaking/hardened_alloy"],           "The Chemical Reactor, acids and plastics"),
    ("ch_salt",          "chemistry",   ["steelmaking/tempered_alloy"],           "Brine, chlorine and lithium"),
    ("ch_farming",       "agriculture", [],                                       "Loam, fertilizer, new crops and mills"),
    ("ch_bio",           "agriculture", ["steelmaking/tempered_alloy"],           "Fermenting, biofuels, greenhouses and rubber"),
    ("ch_nuclear",       "nuclear",     ["chemistry/sulfuric_acid"],              "Uranium to enriched fuel, and staying safe"),
    ("ch_nuclear_power", "nuclear",     ["nuclear/enriched"],                     "Fuel rods, the Fission Reactor and its steam"),
    ("ch_reactor_safety", "nuclear",    ["nuclear_power/fission_reactor"],        "Containment, emergency cooling and fallout"),
    ("ch_fuel_cycle",    "nuclear",     ["nuclear_power/fission_reactor"],        "Spent fuel, PUREX, MOX, thorium and waste"),
    ("ch_advanced_cores", "nuclear",    ["nuclear_power/fission_reactor"],        "Lead-bismuth fast reactors and molten salt"),
]
# Every group nests under one "Arcforge" heading (parent, from Hibiscus Quests' nested groups). A version without them
# ignores parent and leaves the empty heading out, so the six groups show at the top level there.
ROOT_GROUP = ("arcforge", "Arcforge", "arcforge:wrench")
GROUPS = [
    ("foundations", "Foundations", "arcforge:steel_ingot"),
    ("power", "Power Generation", "arcforge:steam_turbine_array_casing"),
    ("petroleum", "Petroleum", "arcforge:pumpjack_controller"),
    ("chemistry", "Chemistry", "arcforge:chemical_reactor"),
    ("agriculture", "Agriculture", "arcforge:compost_bin"),
    ("nuclear", "Nuclear", "arcforge:reactor_controller"),
]
CREATIVE_ONLY = {"creative_fluid_tank", "creative_pressurized_cylinder", "creative_energy_cell", "creative_vault"}
# Node shape and size per frame; circle at 1.0 is the chapter's default, so it's left out of the files.
SHAPE = {"task": ("circle", 1.0), "goal": ("hexagon", 1.5), "challenge": ("gear", 1.5)}
GRID_X, GRID_Y = 1.5, 1.25


# ---------- SNBT ----------
class Long(int):
    pass


def snbt(v, ind=0):
    pad, pad1 = "    " * ind, "    " * (ind + 1)
    if isinstance(v, bool):
        return "1b" if v else "0b"
    if isinstance(v, Long):
        return f"{int(v)}L"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, float):
        return f"{v:g}d"
    if isinstance(v, str):
        return '"' + v.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'
    if isinstance(v, list):
        if not v:
            return "[]"
        if all(isinstance(x, (str, int, float)) and not isinstance(x, bool) for x in v) and len(snbt_inline(v)) < 100:
            return snbt_inline(v)
        return "[\n" + ",\n".join(pad1 + snbt(x, ind + 1) for x in v) + "\n" + pad + "]"
    if isinstance(v, dict):
        if not v:
            return "{}"
        return "{\n" + ",\n".join(f"{pad1}{k}: {snbt(x, ind + 1)}" for k, x in v.items()) + "\n" + pad + "}"
    raise TypeError(v)


def snbt_inline(v):
    return "[" + ", ".join(snbt(x) for x in v) + "]"


# ---------- names ----------
NAMES = {}
REGISTRY = set()


def name(i):
    i = rid(i)
    ns, path = i.split(":", 1)
    return NAMES.get(f"block.{ns}.{path}") or NAMES.get(f"item.{ns}.{path}") or NAMES.get(f"fluid_type.{ns}.{path}") \
        or path.replace("_", " ").title()


def listed(ids):
    return [ids] if isinstance(ids, str) else list(ids)


def default_label(c):
    t, cond = c["trigger"], c["conditions"]
    if t == "minecraft:placed_block":
        return "Place " + " or ".join(name(b) for b in listed(cond["location"]["predicate"]["block"]["blocks"]))
    if t == "arcforge:multiblock_formed":
        path = cond["multiblock"].split(":")[1]
        n = name(path + "_controller") if path + "_controller" in REGISTRY else path.replace("_", " ").title()
        n = n.replace(" Controller", "")
        return f"Form the {n}" + (f" (size {cond['min_length']}+)" if "min_length" in cond else "")
    if t == "arcforge:machine_produced":
        what = cond.get("fluid") or (cond.get("item") or {}).get("items") or cond.get("recipe_category", "something")
        return "Make " + (name(what) if ":" in str(what) else str(what).replace("_", " "))
    if t == "arcforge:turbine_full_speed":
        return "Spin a " + cond["multiblock"].split(":")[1].replace("_", " ").title() + " to full speed" + \
            (f" ({cond['min_length']}+ long)" if "min_length" in cond else "")
    if t == "minecraft:inventory_changed":
        return "Obtain " + " or ".join(name(i) for i in listed(cond["items"][0].get("items")))
    if t == "arcforge:oxy_fuel":
        return f"Burn oxy-fuel at {cond['min_temperature']}°C"
    if t == "arcforge:machine_linked":
        return "Link a machine with a Wired Machine Link"
    if t == "minecraft:enter_block":
        return "Step into " + name(cond["block"])
    if t == "minecraft:item_used_on_block":
        return "Use the item on a block"
    return None


# ---------- "any one of" ----------
def merge_any(criteria, where):
    """One criterion that passes when any of these does. Hibiscus Quests completes a quest when all its required tasks
    are done, so alternatives become one task: items in one inventory_changed, blocks in one placed_block."""
    triggers = {c["trigger"] for c in criteria}
    if triggers == {"minecraft:inventory_changed"} and all(len(c["conditions"]["items"]) == 1 for c in criteria):
        ids = [i for c in criteria for i in listed(c["conditions"]["items"][0]["items"])]
        if not any(i.startswith("#") for i in ids):
            return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": ids}]},
                    "_icon": criteria[0].get("_icon")}
    if triggers == {"minecraft:placed_block"}:
        ids = [b for c in criteria for b in listed(c["conditions"]["location"]["predicate"]["block"]["blocks"])]
        return {"trigger": "minecraft:placed_block", "conditions": {"location": {"type": "minecraft:location_check",
                "predicate": {"block": {"blocks": ids}}}}, "_icon": criteria[0].get("_icon")}
    raise ValueError(f"{where}: all_of=False needs criteria that merge into one (have() or placed() alternatives), "
                     f"not {sorted(triggers)}")


# ---------- mapping to the Hibiscus Quests schema ----------
def qref(ref):
    """'chapter/quest' in a module -> 'arcforge_chapter/quest'; a same-chapter 'quest' stays as it is."""
    c, _, q = ref.rpartition("/")
    return f"{PREFIX}{c}/{q}" if c else q


def task_snbt(c):
    label, icon = c.get("_label") or default_label(c), c.get("_icon")
    crit = {k: v for k, v in c.items() if not k.startswith("_")}
    items = crit["conditions"].get("items") if crit["trigger"] == "minecraft:inventory_changed" else None
    if items and len(items) == 1 and isinstance(items[0].get("items"), str) and not items[0]["items"].startswith("#"):
        # The book names an item task after its item, so only a written label is kept.
        t = {"type": "item", "item": items[0]["items"]}
        icon = None if icon and rid(icon) == items[0]["items"] else icon
        label = c.get("_label")
    else:
        t = {"type": "criterion", "criterion": crit}
    if label:
        t["title"] = label
    if icon:
        t["icon"] = rid(icon)
    return t


def reward_snbt(r):
    if r["type"] == "item":
        out = {"type": "item", "item": r["item"]}
        if r["count"] != 1:
            out["count"] = r["count"]
        return out
    if r["type"] == "xp_levels":
        return {"type": "xp_levels", "levels": r["levels"]}
    raise ValueError(r)


def description(q):
    lines = [q.text]
    if q.items:
        lines += ["", "**Covers:** " + ", ".join("{item:" + rid(i) + "}" for i in q.items)]
    return lines


def dependencies(q, gate):
    """The quest's dependencies, plus the chapter's gate for a quest with none in its own chapter."""
    deps = list(q.deps)
    if not any("/" not in d for d in deps):
        deps += [g for g in gate if g not in deps]
    return [qref(d) for d in deps]


def quest_snbt(q, chapter_id, gate):
    shape, size = SHAPE[q.frame]
    out = {"id": q.id, "title": q.title, "icon": rid(q.icon), "description": description(q),
           "x": float(q.x * GRID_X), "y": float(q.y * GRID_Y)}
    if shape != "circle":
        out["shape"] = shape
    if size != 1.0:
        out["size"] = size
    deps = dependencies(q, gate)
    if deps:
        out["dependencies"] = deps
    criteria = q.criteria if q.all_of or len(q.criteria) < 2 else [merge_any(q.criteria, f"{chapter_id}/{q.id}")]
    out["tasks"] = [task_snbt(c) for c in criteria]
    if q.rewards:
        out["rewards"] = [reward_snbt(r) for r in q.rewards]
    return out


def chapter_snbt(ch, group, order, gate, subtitle):
    out = {"id": PREFIX + ch.id, "group": PREFIX + group, "order": order, "title": ch.title, "subtitle": subtitle,
           "icon": rid(ch.icon), "default_shape": "circle"}
    out["quests"] = [quest_snbt(q, ch.id, gate) for q in ch.quests]
    return out


# ---------- layout (x = dependency depth within the chapter, y packed near its dependencies) ----------
def layout(ch):
    local = {q.id: [d for d in q.deps if "/" not in d] for q in ch.quests}
    depth = {}

    def d(qid):
        if qid not in depth:
            depth[qid] = 0 if not local[qid] else 1 + max(d(p) for p in local[qid])
        return depth[qid]
    cols = defaultdict(list)
    for q in ch.quests:
        cols[d(q.id)].append(q)
    ys = {}
    for c in sorted(cols):
        taken = set()

        def want(q):
            ps = local[q.id]
            return sum(ys[p] for p in ps) / len(ps) if ps else 0
        for q in sorted(cols[c], key=want):
            y = round(want(q))
            step = 0
            while y in taken:
                step += 1
                y = round(want(q)) + (step + 1) // 2 * (1 if step % 2 else -1)
            taken.add(y)
            ys[q.id] = y
    miny = min(ys.values())
    for q in ch.quests:
        q.x, q.y = depth[q.id], ys[q.id] - miny


# ---------- docs/QUESTLINE.md ----------
def questline(chapters):
    group_titles = {g: t for g, t, _ in GROUPS}
    out = ["# Arcforge questline (Hibiscus Quests)", "",
           "Generated by `tools/hibiscusquests/export_hibiscus.py`; edit the chapter modules there, not this file. Each line: "
           "title, id, tasks, dependencies, rewards. ★ goal, ◆ challenge, a chapter's first quests also wait for its gate.", ""]
    for n, (ch, group, req, subtitle) in enumerate(chapters, 1):
        out += [f"## {n}. {ch.title} (`{PREFIX}{ch.id}`)", "",
                f"{group_titles[group]} · {subtitle} · gate: {', '.join(qref(r) for r in req) or 'none'}", "",
                ch.text, ""]
        for q in ch.quests:
            mark = {"goal": "★ ", "challenge": "◆ "}.get(q.frame, "")
            criteria = q.criteria if q.all_of or len(q.criteria) < 2 else [merge_any(q.criteria, q.id)]
            tasks = "; ".join(c.get("_label") or default_label(c) or c["trigger"] for c in criteria)
            rewards = ", ".join(f"{r['count']}× {name(r['item'])}" if r["type"] == "item" else f"{r['levels']} lvl"
                                for r in q.rewards)
            out.append(f"- {mark}**{q.title}** `{q.id}`: {tasks}. After: "
                       f"{', '.join(dependencies(q, req)) or 'none'}. Rewards: {rewards or 'none'}")
        out.append("")
    return "\n".join(out)


# ---------- main ----------
def main():
    repo = sys.argv[1]
    check_only = "--check-only" in sys.argv
    items_dir = os.path.join(repo, "src/main/resources/assets/arcforge/items")
    registry = {f[:-5] for f in os.listdir(items_dir) if f.endswith(".json")}
    REGISTRY.update(registry)
    lang = os.path.join(repo, "src/main/resources/assets/arcforge/lang/en_us.json")
    NAMES.update(json.load(open(lang, encoding="utf-8")))
    chapters = [(importlib.import_module(m).ch, g, r, s) for m, g, r, s in CHAPTERS]
    ids = {(ch.id, q.id) for ch, _, _, _ in chapters for q in ch.quests}
    errors, covered, total = [], set(), 0

    def check_item(i, where):
        if i.startswith("minecraft:") or i.startswith("#"):
            return
        if i.split(":")[-1] not in registry:
            errors.append(f"{where}: unknown item {i}")

    def check_ref(ref, chap, where):
        c, _, qid = ref.rpartition("/")
        if (c or chap, qid) not in ids:
            errors.append(f"{where}: unknown quest {ref}")

    for ch, _, req, _ in chapters:
        check_item(ch.icon, f"chapter {ch.id}")
        for r in req:
            check_ref(r, ch.id, f"chapter {ch.id} gate")
        seen = set()
        for q in ch.quests:
            total += 1
            where = f"{ch.id}/{q.id}"
            if q.id in seen:
                errors.append(f"{where}: duplicate id")
            seen.add(q.id)
            check_item(q.icon, where)
            for dep in q.deps:
                check_ref(dep, ch.id, where)
            for i in q.items:
                check_item(i, where)
                covered.add(i.split(":")[-1])
            for r in q.rewards:
                if r["type"] == "item":
                    check_item(r["item"], where)
            if not q.all_of and len(q.criteria) > 1:
                try:
                    merge_any(q.criteria, where)
                except ValueError as e:
                    errors.append(str(e))
            for c in q.criteria:
                if c.get("_icon"):
                    check_item(c["_icon"], where)
                for i in json.dumps(c).split('"'):
                    if i.startswith("arcforge:") and c["trigger"] in ("minecraft:inventory_changed", "minecraft:placed_block"):
                        check_item(i, where)
                        covered.add(i.split(":")[-1])
    missing = sorted(registry - covered - CREATIVE_ONLY)
    print(f"chapters: {len(chapters)}  quests: {total}  items covered: {len(registry - CREATIVE_ONLY) - len(missing)}/"
          f"{len(registry - CREATIVE_ONLY)}")
    if missing:
        errors.append("not covered by any quest: " + ", ".join(missing))
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    if check_only:
        return
    base = os.path.join(repo, "src/main/resources/data/arcforge/hibiscusquests")
    os.makedirs(os.path.join(base, "chapters"), exist_ok=True)
    for f in os.listdir(os.path.join(base, "chapters")):
        if f.endswith(".snbt"):
            os.remove(os.path.join(base, "chapters", f))
    root, root_title, root_icon = ROOT_GROUP
    groups = {"groups": [{"id": root, "title": root_title, "icon": root_icon, "order": 0}]
              + [{"id": PREFIX + g, "title": t, "icon": i, "order": n, "parent": root} for n, (g, t, i) in enumerate(GROUPS)]}
    write(os.path.join(base, "chapter_groups.snbt"), snbt(groups) + "\n")
    for order, (ch, group, req, subtitle) in enumerate(chapters):
        layout(ch)
        write(os.path.join(base, "chapters", PREFIX + ch.id + ".snbt"), snbt(chapter_snbt(ch, group, order, req, subtitle)) + "\n")
    write(os.path.join(repo, "docs/QUESTLINE.md"), questline(chapters))
    print(f"wrote {len(chapters)} chapters and {total} quests under {base}, and docs/QUESTLINE.md")


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\r\n") as f:
        f.write(text)


if __name__ == "__main__":
    main()
