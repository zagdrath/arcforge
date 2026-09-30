"""Reshade every Arcforge texture with Create's shading technique (experiment/create-shading).

Usage: python tools/create_reshade/reshade_all.py [--dry-run] [--only GLOB] [--review DIR] [--base REF]

Writes in place under src/main/resources/assets/arcforge/textures/. Sources are always read from git at
--base (the last commit before the reshade), so re-running never reshades a reshaded texture twice.
Afterwards run tools/conduit_dye_gen.py to regenerate the _dyed conduits from the reshaded bases.
See NOTES.md for the routing and the judgment calls.
"""
import argparse, collections, fnmatch, glob, hashlib, io, json, os, re, subprocess, sys
from PIL import Image, ImageDraw

sys.dont_write_bytecode = True   # no __pycache__ in tools/
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
from reshade import reshade, reshade_generic, _generic_tile, generic_pivot, is_outline, is_grey, sat, lum, FACE, LIT, X   # noqa: E402
import conduit_dye_gen                                                             # noqa: E402

ROOT = subprocess.run(['git', 'rev-parse', '--show-toplevel'], cwd=HERE, capture_output=True, text=True,
                      check=True).stdout.strip()
ASSETS = 'src/main/resources/assets/arcforge'
TEX = f'{ASSETS}/textures'
BASE = '0c78357'   # last commit with the original (flat) shading

SINGLE_MACHINES = ('arc_crusher', 'arc_melter', 'assembler', 'block_breaker', 'block_placer', 'chemical_reactor',
                   'combustion_plant', 'electrolyzer', 'fiberizer', 'firebox', 'fuel_burner', 'geothermal_plant',
                   'induction_furnace', 'infuser', 'metal_press', 'thermoelectric_plant', 'vacuum_collector')
MACHINE_FOLDERS = ('electric_pump', 'fermenter', 'security_terminal', 'meter', 'chargepad')
MULTIBLOCKS = ('arc_crushing_array', 'arc_quarry', 'carbonizer', 'condenser_array', 'distillation_array',
               'gas_turbine_array', 'induction_furnace_array', 'metal_pressing_array', 'solar_thermal_array',
               'steam_boiler_array', 'steam_turbine_array', 'superheater_array')
STRUCTURE = ('ctm', 'port', 'port_nozzle')
STORAGE = ('energy_cell', 'heat_cell', 'crate', 'vault', 'throttle_lever', 'fluid_tank')
RUSTIC = ('planter', 'harvester', 'fertilizer_spreader', 'copper_sprinkler', 'scarecrow')
TOP = ('roof_', 'formed_top', 'formed_bottom', '/plate', 'tray_plate', 'underside', 'hopper_inner', 'lid', '/top')
CTM_VARIANT = re.compile(r'_\d+\.png$')
PLANT = re.compile(r'_stage\d|hops_vine|^wild_')

# Textures tinted in code, not through a model's tintindex (see ArcforgeClient.registerFluidModels and
# SolarThermalArrayRenderer).
CODE_TINTED = {
    'block/fluid/slurry_still.png': 'greyscale fluid, tinted per metal (FluidTintSources.constant)',
    'block/fluid/slurry_flow.png': 'greyscale fluid, tinted per metal (FluidTintSources.constant)',
    'block/fluid/steam_still.png': 'greyscale fluid, tinted per steam grade, exhaust, hydrogen, oxygen',
    'block/fluid/steam_flow.png': 'greyscale fluid, tinted per steam grade, exhaust, hydrogen, oxygen',
    'block/solar_thermal_array/model_receiver.png': 'greyscale, tinted by temperature in SolarThermalArrayRenderer',
}
# Faces with a tintindex whose tint is always white: ConduitTints returns WHITE unless the conduit is lit,
# and only the _glow layer is on lit conduits' models, so the _off stripes render in their own colours.
TINT_ALWAYS_WHITE = re.compile(r'^block/conduit/\w+_thermal_off\.png$')
EMISSIVE = ('arc_quarry/beam', 'arc_quarry/emitter', 'chargepad/pad_glow', 'gas_turbine_array/combustor_glow',
            'security_terminal/screen')
RENDERER_CONTENTS = ('distillation_array/vapour', 'fermenter/mash')
# Treated wood keeps its original shading (asked for after the first in-game look): the logs, planks, door,
# trapdoor, the Compost Bin's wood (its compost heap is still reshaded) and the Trellis.
TREATED_WOOD = re.compile(r'^(block|item)/(stripped_)?treated_|^block/compost_bin_(bottom|inside|side|top)\.png$'
                          r'|^(block|item)/trellis\.png$')

# The dye regions conduit_dye_gen.py cuts out by exact colour: left as they are, so it still finds them.
CONDUIT_KEEP = frozenset(c for r in conduit_dye_gen.REGIONS.values() for c in r) | frozenset(conduit_dye_gen.GLASS)


# ---- sources

def git_blobs(ref, paths):
    """{path: bytes} for the paths that exist at ref, read with one git cat-file --batch."""
    p = subprocess.run(['git', 'cat-file', '--batch'], cwd=ROOT, capture_output=True,
                       input=''.join(f'{ref}:{q}\n' for q in paths).encode(), check=True)
    out, buf, i = {}, p.stdout, 0
    for q in paths:
        nl = buf.index(b'\n', i); head = buf[i:nl].split(); i = nl + 1
        if head[-1] == b'missing':
            continue
        size = int(head[2]); out[q] = buf[i:i + size]; i += size + 1
    return out


def tinted_textures():
    """Textures on a model face with a tintindex -> the models, following parents and #references."""
    M = f'{ROOT}/{ASSETS}/models'

    def load(ref):
        ns, p = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
        f = f'{M}/{p}.json'
        return json.load(open(f)) if ns == 'arcforge' and os.path.exists(f) else None
    found = collections.defaultdict(set)
    for f in glob.glob(f'{M}/**/*.json', recursive=True):
        j = json.load(open(f)); tex = {}; els = None
        while j:
            for k, v in j.get('textures', {}).items(): tex.setdefault(k, v)
            if els is None and 'elements' in j: els = j['elements']
            j = load(j['parent']) if 'parent' in j else None
        for e in els or []:
            for face in e.get('faces', {}).values():
                if 'tintindex' not in face: continue
                t = face['texture']
                for _ in range(10):
                    if not t.startswith('#'): break
                    t = tex.get(t[1:], t)
                if t.startswith('arcforge:'):
                    found[t.split(':', 1)[1] + '.png'].add(os.path.relpath(f, M))
    return found


# ---- routing

def route(rel, tinted):
    """(mode, options) with mode 'skip' (options = reason), 'machine' or 'generic'. First match wins."""
    b = '/' + rel[len('block/'):] if rel.startswith('block/') else '/' + rel
    name = os.path.basename(rel)
    folder = rel.split('/')[1] if rel.startswith('block/') and rel.count('/') > 1 else None
    # 1. skip
    if rel.startswith('block/conduit/dye/'):
        return 'skip', 'greyscale dye mask, tint index 1 (ConduitDyeModel); regenerated by conduit_dye_gen.py'
    if rel in CODE_TINTED:
        return 'skip', CODE_TINTED[rel]
    if rel in tinted and not TINT_ALWAYS_WHITE.match(rel):
        return 'skip', 'greyscale tint target: lit thermal conduit glow (tint index 0, ConduitTints)'
    if '_glow' in name or any(k in rel for k in EMISSIVE):
        return 'skip', 'emissive / glow layer'
    if any(k in rel for k in RENDERER_CONTENTS):
        return 'skip', 'contents drawn by a block entity renderer'
    if rel.startswith('block/conduit/') and name.endswith('_dyed.png'):
        return 'skip', 'regenerated by conduit_dye_gen.py from the reshaded base'
    if TREATED_WOOD.search(rel):
        return 'skip', 'treated wood: kept as originally shaded'
    if rel.startswith('particle/'):
        return 'skip', 'particle'
    if rel.startswith('gui/'):
        if not rel.startswith('gui/sprites/'):
            return 'skip', 'GUI panel background (greys matched by colours drawn in code)'
        meta = f'{ROOT}/{TEX}/{rel}.mcmeta'
        if os.path.exists(meta) and 'scaling' in json.load(open(meta)).get('gui', {}):
            return 'skip', 'nine-slice GUI widget (greys matched by colours drawn in code)'
        if name.startswith('tab'):
            return 'skip', 'side tab widget (tab colours matched by SideTabPanel panels)'
        w, h = Image.open(f'{ROOT}/{TEX}/{rel}').size
        if w > 32 or h > 32:
            return 'skip', 'GUI bar / gauge / panel over 32 px, not an icon (sits on code-drawn panels)'
        if min(w, h) <= 6 and not name.startswith('led_'):   # 6x6 status LEDs shade well as pillows
            return 'skip', 'GUI marker / tick / segment / pip / badge, 6 px or thinner: too small to shade'
        return 'generic', {}
    # 2. machine mode
    if rel.startswith('block/'):
        top_level = folder is None
        machine = (top_level and (name.startswith(tuple(m + '_' for m in SINGLE_MACHINES))
                                  or name.startswith('arcforge_furnace_'))) \
            or folder in MACHINE_FOLDERS + MULTIBLOCKS + STRUCTURE + STORAGE
        if machine and rel != 'block/fluid_tank/glass.png' and not name.startswith(RUSTIC):
            return 'machine', {'top': any(k in b for k in TOP), 'grain': not CTM_VARIANT.search(rel)}
    # 3. general mode. Plants get no edge emboss: on 1-2 px leaves and stems every chunk would get its own
    #    lit and dark edge, which reads as speckle (see NOTES.md).
    if rel.startswith('block/conduit/'):
        return 'generic', {'keep': CONDUIT_KEEP}
    if PLANT.search(name):
        return 'generic', {'emboss': False}
    return 'generic', {}


def group(rel):
    """Review sheet a texture belongs to."""
    name = os.path.basename(rel); parts = rel.split('/')
    if rel.startswith('item/'): return 'items'
    if rel.startswith('entity/'): return 'entity'
    if rel.startswith('gui/'): return 'gui_icons'
    folder = parts[1] if len(parts) > 2 else None
    if folder in MULTIBLOCKS: return 'multiblock_' + folder
    if name.startswith('arcforge_furnace_'): return 'multiblock_arcforge_furnace'
    if folder in MACHINE_FOLDERS or folder is None and name.startswith(tuple(m + '_' for m in SINGLE_MACHINES)):
        return 'machines'
    if folder in STRUCTURE: return 'ctm'
    if folder in STORAGE or folder == 'pressurized_cylinder': return 'storage'
    if folder == 'conduit': return 'conduits'
    if folder == 'fluid': return 'fluids'
    if folder == 'ore' or name.startswith('raw_'): return 'ores'
    if PLANT.search(name) or name == 'trellis.png': return 'crops'
    if name.startswith(RUSTIC + ('compost_bin', 'irrigated_', 'loam_farmland')): return 'farm'
    if name.endswith('_block.png'): return 'storage'
    return 'natural'


# ---- invariants

class Broken(Exception):
    pass


def alpha(im): return im.getchannel('A').tobytes()


def check_file(rel, before, after, mode, opts):
    if before.size != after.size:
        raise Broken(f'{rel}: size {before.size} -> {after.size}')
    if alpha(before) != alpha(after):
        raise Broken(f'{rel}: alpha channel changed')
    w, h = before.size
    if h > w and h % w == 0:   # frame by frame: each frame alone (with the strip's pivot) must give the same result
        m = generic_pivot(before)
        fn = (lambda t: reshade(t, opts['top'], opts['grain'])) if mode == 'machine' else \
            (lambda t: _generic_tile(t, pivot=m, **opts))
        for f in range(h // w):
            box = (0, f * w, w, (f + 1) * w)
            if fn(before.crop(box)).tobytes() != after.crop(box).tobytes():
                raise Broken(f'{rel}: frame {f} not processed on its own')
    pb, pa = before.load(), after.load()
    bad = [(x, y) for y in range(h) for x in range(w) if is_outline(pb[x, y]) and pb[x, y] != pa[x, y]]
    if bad:
        raise Broken(f'{rel}: {len(bad)} outline pixels changed, first {bad[0]}')


def tile2(im):
    w, h = im.size; t = Image.new('RGBA', (2 * w, 2 * h))
    for dx in (0, w):
        for dy in (0, h): t.paste(im, (dx, dy))
    return t


def seam_class(c, mode):
    """What decides a pixel's emboss: a seam between two pixels of one class must shade like the interior."""
    if mode == 'generic':
        return c[3] > 0 and not is_outline(c)
    return ('outline' if is_outline(c) else 'coloured' if not is_grey(c) else
            'face' if c[:3] in FACE else 'lit' if c[:3] == LIT else 'grey')


def check_tiling(rel, before, after, mode, opts):
    """Full-opaque blocks: tile the original 2x2, reshade that, and compare its seam rows and columns with the
    reshaded texture tiled 2x2. Where both pixels across a seam are the same class (so the interior would put
    no emboss between them) they must match: the tile edge adds no emboss, lit line or shadow. Across a real
    boundary the edge is neither inside nor outside by design, and machine face regions are left out, as
    their cylinder profile depends on the tile width by design."""
    w, h = before.size
    if h > w and h % w == 0:
        before, after, h = before.crop((0, 0, w, w)), after.crop((0, 0, w, w)), w
    fn = (lambda t: reshade(t, opts['top'], opts['grain'])) if mode == 'machine' else \
        (lambda t: reshade_generic(t, **opts))
    a, b, src = fn(tile2(before)).load(), tile2(after).load(), tile2(before).load()
    across = collections.defaultdict(list)   # seam pixel -> its neighbours on the other side of a seam
    for y in range(2 * h):
        for x, d in ((w - 1, 1), (w, -1)): across[(x, y)].append((x + d, y))
    for x in range(2 * w):
        for y, d in ((h - 1, 1), (h, -1)): across[(x, y)].append((x, y + d))
    diff = [p for p, qs in across.items() if a[p] != b[p]
            and all(seam_class(src[p], mode) == seam_class(src[q], mode) for q in qs)
            and not (mode == 'machine' and src[p][:3] in FACE)]
    if diff:
        raise Broken(f'{rel}: {len(diff)} seam pixels shade differently from the interior, first {diff[0]}')


def check_ctm_set(stem, members):
    """Every variant of one connected face shares the same shading columns, and no variant gains a lit line
    on a tile edge (an interior seam when connected)."""
    problems = []
    if not any(k in '/' + stem for k in TOP):
        cols = collections.defaultdict(set)
        for rel, before, after in members:
            pb, pa = before.load(), after.load(); w, h = before.size
            for y in range(1, h - 1):
                for x in range(w):
                    if all(pb[x, yy][:3] in FACE for yy in (y - 1, y, y + 1)):
                        cols[x].add(pa[x, y][:3])
        problems += [f'{stem}: column {x} has {len(c)} face tones across variants' for x, c in cols.items() if len(c) > 1]
    lit = lum(X[10])
    for rel, before, after in members:
        pb, pa = before.load(), after.load(); w, h = before.size
        for name, line in (('top', [(x, 0) for x in range(w)]), ('bottom', [(x, h - 1) for x in range(w)]),
                           ('left', [(0, y) for y in range(h)]), ('right', [(w - 1, y) for y in range(h)])):
            run = 0
            for p in line:
                new = pa[p][3] and lum(pa[p]) >= lit and lum(pb[p]) < lum(LIT)
                run = run + 1 if new else 0
                if run >= 3:
                    problems.append(f'{rel}: new lit line on the {name} edge'); break
    return problems


# ---- review sheets

def contact_sheet(cells, path):
    """cells: [(label, before, after)] of the first frame; before above after, each at the largest integer
    scale that fits a BOX px cell (1x for anything bigger)."""
    BOX = 96
    cw = max(BOX, max(b.width for _, b, _ in cells)) + 12
    ch = max(BOX, max(b.height for _, b, _ in cells))
    per = max(1, 1500 // cw); rows = (len(cells) + per - 1) // per
    sheet = Image.new('RGBA', (per * cw + 12, rows * (2 * ch + 30) + 12), (60, 62, 68, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, a, b) in enumerate(cells):
        x = 12 + (i % per) * cw; y = 12 + (i // per) * (2 * ch + 30)
        s = max(1, BOX // max(a.size))
        d.text((x, y), label[:cw // 6], fill=(230, 230, 230))
        for k, im in enumerate((a, b)):
            sheet.alpha_composite(im.resize((im.width * s, im.height * s), Image.NEAREST), (x, y + 13 + k * (ch + 3)))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)


def first_frame(im):
    w, h = im.size
    return im.crop((0, 0, w, w)) if h > w and h % w == 0 else im


# ---- main

def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--dry-run', action='store_true', help='reshade and check everything, write no textures')
    ap.add_argument('--only', metavar='GLOB', help='only paths under textures/ matching this glob, e.g. "block/ore/*"')
    ap.add_argument('--review', metavar='DIR', help='write before/after contact sheets per group here')
    ap.add_argument('--base', default=BASE, help=f'git ref holding the original textures (default {BASE})')
    args = ap.parse_args()

    rels = sorted(os.path.relpath(f, f'{ROOT}/{TEX}').replace(os.sep, '/')
                  for f in glob.glob(f'{ROOT}/{TEX}/**/*.png', recursive=True))
    if args.only:
        rels = [r for r in rels if fnmatch.fnmatch(r, args.only)]
    tinted = tinted_textures()
    for t in tinted:   # every tinted texture must be accounted for
        if t.startswith('block/') and route(t, tinted)[0] != 'skip' and not TINT_ALWAYS_WHITE.match(t):
            raise Broken(f'{t} is tinted but not skipped')

    blobs = git_blobs(args.base, [f'{TEX}/{r}' for r in rels])
    mcmeta = {f: hashlib.sha256(open(f, 'rb').read()).hexdigest()
              for f in glob.glob(f'{ROOT}/{TEX}/**/*.mcmeta', recursive=True)}

    counts = collections.Counter(); skipped = collections.defaultdict(list); flags = collections.Counter()
    sheets = collections.defaultdict(list); sets = collections.defaultdict(list); tiled = 0; errors = []
    routing = []; restored = []
    for rel in rels:
        mode, opts = route(rel, tinted)
        routing.append((rel, mode, opts if mode == 'skip' else ' '.join(k for k in ('top', 'grain') if opts.get(k))
                        + (' keep-dye-colours' if 'keep' in opts else '') + (' no-emboss' if opts.get('emboss') is False else '')))
        if mode == 'skip':
            skipped[opts].append(rel)
            # a skipped texture is the original: put it back if an earlier run reshaded it
            key = f'{TEX}/{rel}'
            if not args.dry_run and key in blobs and open(f'{ROOT}/{key}', 'rb').read() != blobs[key]:
                open(f'{ROOT}/{key}', 'wb').write(blobs[key]); restored.append(rel)
            continue
        key = f'{TEX}/{rel}'
        if key not in blobs:
            print(f'  new since {args.base}, reading from disk: {rel}')
        before = Image.open(io.BytesIO(blobs[key]) if key in blobs else f'{ROOT}/{key}').convert('RGBA')
        after = reshade(before, opts['top'], opts['grain']) if mode == 'machine' else reshade_generic(before, **opts)
        try:
            check_file(rel, before, after, mode, opts)
            if rel.startswith('block/') and min(alpha(before)) == 255:
                check_tiling(rel, before, after, mode, opts); tiled += 1
        except Broken as e:
            errors.append(str(e)); continue
        counts[mode] += 1
        if mode == 'machine':
            flags['top'] += opts['top']; flags['no grain'] += not opts['grain']
        if CTM_VARIANT.search(rel):
            sets[CTM_VARIANT.sub('', rel)].append((rel, before, after))
        sheets[group(rel)].append((rel.rsplit('/', 1)[-1][:-4], first_frame(before), first_frame(after)))
        if not args.dry_run:
            after.save(f'{ROOT}/{key}')
            if alpha(Image.open(f'{ROOT}/{key}').convert('RGBA')) != alpha(before):
                errors.append(f'{rel}: alpha changed on save')

    for stem, members in sorted(sets.items()):
        errors += check_ctm_set(stem, members)
    after_meta = {f: hashlib.sha256(open(f, 'rb').read()).hexdigest()
                  for f in glob.glob(f'{ROOT}/{TEX}/**/*.mcmeta', recursive=True)}
    if after_meta != mcmeta:
        errors.append('.mcmeta files changed')

    if args.review:
        out = args.review if os.path.isabs(args.review) else f'{ROOT}/{args.review}'
        for g, cells in sorted(sheets.items()):
            contact_sheet(cells, f'{out}/{g}.png')
        with open(f'{out}/routing.tsv', 'w') as f:
            f.writelines(f'{r}\t{m}\t{o}\n' for r, m, o in routing)
        print(f'review: {len(sheets)} sheets in {out}')

    print(f'machine {counts["machine"]} (top {flags["top"]}, no grain {flags["no grain"]}), '
          f'generic {counts["generic"]}, skipped {sum(map(len, skipped.values()))}')
    for reason, fs in sorted(skipped.items(), key=lambda kv: -len(kv[1])):
        print(f'  skip {len(fs):4}  {reason}')
    print(f'invariants: size, alpha, outlines, frames on {sum(counts.values())} files; 2x2 tiling on {tiled}; '
          f'{len(sets)} connected/numbered sets; .mcmeta {"unchanged" if after_meta == mcmeta else "CHANGED"}')
    if restored:
        print(f'restored {len(restored)} skipped textures to the original (_dyed ones: rerun conduit_dye_gen.py)')
    if errors:
        print('\n'.join('BROKEN ' + e for e in errors))
        sys.exit(1)
    print('all invariants hold' + (' (dry run, nothing written)' if args.dry_run else ''))


if __name__ == '__main__':
    main()
