# Usage: python3 creative_storage_gen.py <repo root>  (needs Pillow). Safe to re-run.
# The creative storage blocks (Creative Vault, Energy Cell, Fluid Tank and Pressurized Cylinder) are the Arcforged ones
# in the creative tier colour. This copies each Arcforged blockstate, model and item definition with "arcforged" swapped
# for "creative", and recolours the Arcforged textures: every cyan tier pixel goes onto the creative ramp at the same
# step of its ramp (by brightness), and the machine steel stays as it is. Run from anywhere; edits the repo in place.
import glob, json, os, sys
from PIL import Image

A = (sys.argv[1] if len(sys.argv) > 1 else '.') + '/src/main/resources/assets/arcforge/'
H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))
# Tier palettes (docs/TEXTURE_STYLE.md): outline, end face, front, bevel, top, rim highlight.
ARCFORGED = [H(c) for c in ['0A2A38', '1E6A88', '2E96B6', '40B6D8', '6AD2EE', 'B4F2FF']]
CREATIVE = [H(c) for c in ['3A0A2E', '882068', 'B42E8C', 'D442AA', 'EC70C8', 'FFB8EC']]

TEXTURES = ['block/vault/arcforged_front', 'block/vault/arcforged_side', 'block/vault/arcforged_top',
            'block/energy_cell/frame_arcforged', 'block/fluid_tank/arcforged', 'block/pressurized_cylinder/arcforged_body']
BLOCKS = ['vault', 'energy_cell', 'fluid_tank', 'pressurized_cylinder']
MODEL_GLOBS = ['models/block/vault/arcforged.json', 'models/block/energy_cell/arcforged_*.json', 'models/block/fluid_tank/arcforged.json',
               'models/block/pressurized_cylinder/arcforged_*.json', 'models/item/arcforged_energy_cell*.json',
               'models/item/arcforged_fluid_tank.json', 'models/item/arcforged_pressurized_cylinder.json']


def brightness(rgb):
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def is_tier(rgb):
    # The cyan tier tones; the machine steel is grey (blue at most ~20 over red).
    return rgb[2] - rgb[0] >= 40


def recolour(rgb):
    step = min(range(len(ARCFORGED)), key=lambda i: abs(brightness(ARCFORGED[i]) - brightness(rgb)))
    return CREATIVE[step]


def texture(name):
    image = Image.open(A + 'textures/' + name + '.png').convert('RGBA')
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if a and is_tier((r, g, b)):
                pixels[x, y] = recolour((r, g, b)) + (a,)
    image.save(A + 'textures/' + name.replace('arcforged', 'creative') + '.png')


def copy_json(path, edit=None):
    with open(A + path, encoding='utf-8') as f:
        text = f.read().replace('arcforged', 'creative')
    data = json.loads(text)
    if edit:
        data = edit(data)
    out = A + path.replace('arcforged', 'creative')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


# A Creative Energy Cell is always full, so its item always shows all four segments lit.
def always_full(data):
    return {'model': {'type': 'minecraft:model', 'model': 'arcforge:item/creative_energy_cell_4'}}


for name in TEXTURES:
    texture(name)
for block in BLOCKS:
    copy_json('blockstates/arcforged_' + block + '.json')
    copy_json('items/arcforged_' + block + '.json', always_full if block == 'energy_cell' else None)
for pattern in MODEL_GLOBS:
    for path in sorted(glob.glob(A + pattern)):
        copy_json(os.path.relpath(path, A).replace('\\', '/'))
print('done')
