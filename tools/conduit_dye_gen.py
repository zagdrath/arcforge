# Usage: python3 conduit_dye_gen.py <repo root>  (needs Pillow). Safe to re-run.
# Dyed conduits: per-piece "_dyed" models, dyed textures (the dye region cut out) and tier-independent dye masks,
# and blockstates/item models switched to arcforge:dyeable. Run from anywhere; edits the repo in place.
import json, glob, os, copy
from PIL import Image

import sys
A = (sys.argv[1] if len(sys.argv) > 1 else '.') + '/src/main/resources/assets/arcforge/'  # the repo root
T = A + 'textures/block/conduit/'
M = A + 'models/block/conduit/'
TIERS = ['wrought', 'tempered', 'hardened', 'arcforged']
H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))

# Which pixels are the dye region, and the mask grey (and alpha) each becomes. Top row of a stripe = the lit tone.
REGIONS = {
    'energy_off': {H('8E231C'): (0x90, 255)},
    'energy_on': {H('FF8577'): (0xFF, 255), H('E5483C'): (0xC8, 255)},
    'thermal_off': {H('7A1B0A'): (0x90, 255), H('3C1C0C'): (0x60, 255)},
    'thermal_on': {H('FFB02E'): (0xFF, 255), H('F26A16'): (0xC8, 255)},
}
GLASS = {H('DCE8EE'): 0xFF, H('A9BCC6'): 0xC8}  # glass tones -> mask grey; alpha is the glass's own + 0x58


def variants():
    for tier in TIERS:
        for kind in ['energy_off', 'energy_on', 'thermal_off', 'thermal_on', 'item', 'fluid', 'gas_off', 'gas_on']:
            yield tier, kind


def mask_name(kind):
    return {'gas_off': 'glass_gas', 'gas_on': 'glass_gas', 'item': 'glass_item', 'fluid': 'glass_fluid'}.get(kind, kind)


def build_textures():
    os.makedirs(T + 'dye', exist_ok=True)
    masks = {}
    for tier, kind in variants():
        src = Image.open(T + f'{tier}_{kind}.png').convert('RGBA')
        cut = src.copy()
        mask = Image.new('RGBA', src.size, (0, 0, 0, 0))
        for y in range(src.height):
            for x in range(src.width):
                r, g, b, a = src.getpixel((x, y))
                if a == 0:
                    continue
                if kind in REGIONS:
                    hit = REGIONS[kind].get((r, g, b))
                    if hit:
                        mask.putpixel((x, y), (hit[0],) * 3 + (hit[1],))
                        cut.putpixel((x, y), (0, 0, 0, 0))
                elif a < 255 and (r, g, b) in GLASS:
                    mask.putpixel((x, y), (GLASS[(r, g, b)],) * 3 + (min(255, a + 0x58),))
                    cut.putpixel((x, y), (0, 0, 0, 0))
        cut.save(T + f'{tier}_{kind}_dyed.png')
        name = mask_name(kind)
        if name in masks:
            assert list(masks[name].getdata()) == list(mask.getdata()), f'{tier}_{kind}: dye region differs by tier'
        else:
            masks[name] = mask
    for name, mask in masks.items():
        mask.save(T + f'dye/{name}.png')
    return masks


def dyed_base(path):
    d = json.load(open(path))
    kind = os.path.basename(path).split('_')[1]
    out = copy.deepcopy(d)
    if kind in ('energy', 'thermal'):
        out['render_type'] = 'minecraft:cutout'
    elements = []
    for e in d['elements']:
        faces = e['faces']
        if any(f['texture'] == '#glow' for f in faces.values()):
            continue  # the heat glow: a dyed conduit's own colour lights up instead (tint index 1)
        elements.append(e)
        dye_faces = {k: dict(f, texture='#dye', tintindex=1) for k, f in faces.items() if f['texture'] == '#conduit'}
        if dye_faces:
            elements.append(dict(copy.deepcopy(e), faces=dye_faces))
    out['elements'] = elements
    out['textures'] = {k: v for k, v in d['textures'].items() if k != 'glow'}
    return out


def build_models():
    bases = sorted(glob.glob(M + 'base_*.json'))
    for b in bases:
        if b.endswith('_dyed.json'):
            continue
        json.dump(dyed_base(b), open(b[:-5] + '_dyed.json', 'w'), indent=2)
    n = 0
    for f in sorted(glob.glob(M + '*.json')):
        name = os.path.basename(f)[:-5]
        if name.startswith('base_') or name.endswith('_dyed') or name.split('_')[0] not in TIERS:
            continue
        d = json.load(open(f))
        tex = d['textures']
        conduit = tex['conduit'].split('/')[-1]            # e.g. hardened_energy_on
        kind = conduit.split('_', 1)[1]
        out = {
            'parent': d['parent'] + '_dyed',
            'textures': {k: v for k, v in tex.items() if k != 'glow'},
        }
        out['textures']['conduit'] = tex['conduit'] + '_dyed'
        out['textures']['dye'] = 'arcforge:block/conduit/dye/' + mask_name(kind)
        json.dump(out, open(M + name + '_dyed.json', 'w'), indent=2)
        n += 1
    return n


def build_blockstates():
    for f in sorted(glob.glob(A + 'blockstates/*_conduit.json')):
        d = json.load(open(f))
        for part in d['multipart']:
            a = part['apply']
            if a.get('type') == 'arcforge:dyeable':
                continue
            part['apply'] = {'type': 'arcforge:dyeable', **a, 'dyed': a['model'] + '_dyed'}
        json.dump(d, open(f, 'w'), indent=2)


def build_items():
    for f in sorted(glob.glob(A + 'items/*_conduit.json')):
        name = os.path.basename(f)[:-5]
        if name.split('_')[0] not in TIERS:
            continue
        item = f'arcforge:item/{name}'
        m = json.load(open(A + f'models/item/{name}.json'))
        dyed = dict(m, parent=m['parent'] + '_dyed')
        json.dump(dyed, open(A + f'models/item/{name}_dyed.json', 'w'), indent=2)
        d = {'model': {
            'type': 'minecraft:condition', 'property': 'minecraft:has_component', 'component': 'arcforge:conduit_color',
            'on_true': {'type': 'minecraft:model', 'model': item + '_dyed', 'tints': [
                {'type': 'minecraft:constant', 'value': -1},
                {'type': 'arcforge:conduit_color', 'default': -1}]},
            'on_false': {'type': 'minecraft:model', 'model': item}}}
        json.dump(d, open(f, 'w'), indent=2)


if __name__ == '__main__':
    build_textures()
    print('tier models', build_models())
    build_blockstates()
    build_items()
