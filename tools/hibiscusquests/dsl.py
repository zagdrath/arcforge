"""Tiny DSL for authoring Arcforge's Hibiscus Quests chapters (one ch_*.py module per chapter).

Each criterion helper returns an advancement criterion ({trigger, conditions}), written into the pack as a Hibiscus
Quests task by export_hibiscus.py: a single-item have() becomes an item task, everything else a criterion task.
Keys starting with _ (label, icon) are the task's title and icon, not part of the criterion.
"""

from dataclasses import dataclass, field

NS = "arcforge"


def rid(s):
    return s if ":" in s or s.startswith("#") else f"{NS}:{s}"


# ---------- criteria ----------

def have(*items, tag=None, label=None):
    """inventory_changed: any one of the items (or the tag)."""
    ids = [rid(i) for i in items]
    pred = {"items": ids[0] if len(ids) == 1 else ids} if ids else {"items": tag}
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [pred]}, "_label": label,
            "_icon": items[0] if items else None}


def placed(*blocks, label=None):
    ids = [rid(b) for b in blocks]
    return {"trigger": "minecraft:placed_block", "conditions": {"location": {"type": "minecraft:location_check",
            "predicate": {"block": {"blocks": ids[0] if len(ids) == 1 else ids}}}}, "_label": label, "_icon": blocks[0]}


def formed(multiblock, min_length=None, icon=None, label=None):
    cond = {"multiblock": rid(multiblock)}
    if min_length is not None:
        cond["min_length"] = min_length
    return {"trigger": "arcforge:multiblock_formed", "conditions": cond, "_label": label, "_icon": icon}


def produced(item=None, fluid=None, category=None, icon=None, label=None):
    cond = {}
    if item:
        cond["item"] = {"items": rid(item)}
    if fluid:
        cond["fluid"] = rid(fluid)
    if category:
        cond["recipe_category"] = category
    return {"trigger": "arcforge:machine_produced", "conditions": cond, "_label": label, "_icon": icon or item}


def used_on(tool, *blocks, label=None, icon=None):
    terms = [{"type": "minecraft:match_tool", "predicate": {"items": rid(tool)}}]
    if blocks:
        ids = [rid(b) for b in blocks]
        terms.insert(0, {"type": "minecraft:location_check", "predicate": {"block": {"blocks": ids[0] if len(ids) == 1 else ids}}})
    loc = terms[0] if len(terms) == 1 else {"type": "minecraft:all_of", "terms": terms}
    return {"trigger": "minecraft:item_used_on_block", "conditions": {"location": loc}, "_label": label,
            "_icon": icon or (tool if not tool.startswith("#") else None)}


def enter(block, label=None, icon=None):
    return {"trigger": "minecraft:enter_block", "conditions": {"block": rid(block)}, "_label": label, "_icon": icon}


def turbine(multiblock, min_length=None, min_signal=None, icon=None, label=None):
    cond = {"multiblock": rid(multiblock)}
    if min_length is not None:
        cond["min_length"] = min_length
    if min_signal is not None:
        cond["min_signal"] = min_signal
    return {"trigger": "arcforge:turbine_full_speed", "conditions": cond, "_label": label, "_icon": icon}


def oxy(min_temperature, icon="firebox", label=None):
    return {"trigger": "arcforge:oxy_fuel", "conditions": {"min_temperature": min_temperature}, "_label": label, "_icon": icon}


def linked(label=None, icon="wired_machine_link"):
    return {"trigger": "arcforge:machine_linked", "conditions": {}, "_label": label, "_icon": icon}


# ---------- rewards ----------

def xp(levels):
    return {"type": "xp_levels", "levels": levels}


def item(id, count=1):
    return {"type": "item", "item": rid(id), "count": count}


# ---------- quests ----------

@dataclass
class Quest:
    id: str
    title: str
    icon: str
    text: str
    criteria: list                 # list of criterion dicts (from the helpers above)
    deps: list = field(default_factory=list)
    rewards: list = field(default_factory=list)
    frame: str = "task"            # task | goal | challenge
    all_of: bool = True            # True: every criterion; False: any one (merged into one task: same trigger only)
    items: list = field(default_factory=list)   # items this quest covers (listed at the end of its description)
    x: int | None = None
    y: int | None = None


@dataclass
class Chapter:
    id: str
    title: str
    icon: str
    text: str
    quests: list = field(default_factory=list)

    def q(self, *args, **kwargs):
        quest = Quest(*args, **kwargs)
        self.quests.append(quest)
        return quest
