"""Static v3.2 checks; use the real GitHub build for Minecraft API compilation."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
res = root / 'src/main/resources'
dim = json.loads((res / 'data/underworld/dimension/underworld.json').read_text())
type_ = json.loads((res / 'data/underworld/dimension_type/underworld.json').read_text())
biome = json.loads((res / 'data/underworld/worldgen/biome/wasteland.json').read_text())
java = root / 'src/main/java/dev/unstable/underworld'
mod = (java / 'UnderworldMod.java').read_text()
terrain = (java / 'UnderworldTerrain.java').read_text()
math = (java / 'UnderworldMath.java').read_text()
structures = (java / 'UnderworldStructures.java').read_text()
properties = (root / 'gradle.properties').read_text()
workflow = (root / '.github/workflows/build.yml').read_text()

assert dim['type'] == 'underworld:underworld'
gen = dim['generator']
assert gen['type'] == 'minecraft:flat' and gen['settings']['layers'] == []
assert gen['settings']['biome'] == 'underworld:wasteland'
assert gen['settings']['features'] is False and gen['settings']['lakes'] is False
assert gen['settings']['structure_overrides'] == []
assert type_['min_y'] == -192 and type_['height'] == 512
assert type_['skybox'] == 'none' and type_['has_skylight'] is False
assert type_['cardinal_light'] == 'nether'
assert 'minecraft:visual/sky_color' not in biome['attributes']
assert len(biome['features']) == 11
assert 'UNDERWORLD_DROP_Y = 296.0' in mod
assert 'entity.getX()' in mod and 'entity.getZ()' in mod
assert 'GameType.ADVENTURE' in mod and 'restoreOriginalMode(player)' in mod
assert 'UNDERWORLD_LOWER_EXIT_Y' not in mod
assert 'e.getY() > UNDERWORLD_UPPER_EXIT_Y' in mod
assert 'PIT_GRID' not in math
assert 'VERSION_MARKER_Y = -186' in terrain
assert 'UnderworldMath.hasLand(x, z, seed)' in terrain
assert 'UnderworldStructures.generate(chunk, seed)' in terrain
assert 'VERTICAL_THICKNESS = 30' in math
assert 'COMPLETE_VOID = 6400' in math
assert 'UnderworldMath.surfaceY(cx, cz, seed)' in structures
assert 'UnderworldMath.structureSite(centerX, centerZ, seed, radius)' in structures
for name in ('gate(', 'shrine(', 'tower(', 'citadel('):
    assert name in structures
assert 'mod_version=3.2.0' in properties
assert 'unstable-underworld-3.2.0.jar' in workflow
assert not (res / 'data/minecraft/dimension/the_nether.json').exists()
assert not (res / 'data/minecraft/dimension_type/the_nether.json').exists()
for path in root.rglob('*.json'):
    json.loads(path.read_text())
print('PASS: empty dimension, Nether sky, 30-block shallow terrain, natural fractures, true void death, ruins, migrations and packaging')
