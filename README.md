# Unstable Underworld v3.2.0 — Minecraft Java 26.3

A Fabric mod that adds a separate Underworld linked to the vanilla Nether. Inspired by the atmosphere of scripted Unstable SMP videos; all generated ruins are original. The vanilla Nether dimension and its terrain are never rewritten.

## Changes in 3.2.0

- **Void is lethal again:** falling below the Underworld does NOT return you to the Nether. Minecraft's normal void damage applies once you fall far enough below the world's minimum build height. The explicit `/underworld return` command and upward exit remain available.
- **Removed the terrain pit grid:** no fixed 680-block crater-cell lattice; broad void basins and branching faults now use continuous, strongly warped, seed-based noise. Hills remain about Y=51–98, and solid ground is 30 blocks thick.
- **Upgrades existing Underworld chunks:** a new per-chunk v3.2 marker causes v3.1 chunks to be cleared and regenerated when next loaded. This **destroys old Underworld terrain, structures and player builds**. Back up your world before upgrading.
- **Other retained features:** soul sand, soul soil, blackstone and basalt provinces; progressively expanding fissures to complete void outside radius 6,400; detailed ruined structures; Nether-like sky without sun or clouds; falling preserves X/Z and motion; players enter Adventure mode and their previous mode is restored after leaving.

## Coordinates and transitions

- Nether: falling below Y=-10 enters the Underworld, at the same X and Z and high above its terrain. **The Nether's blocks are not changed**; a hole through the normal bedrock is still needed for natural entry.
- Underworld: falling into an open pit is **not an exit**. Falling sufficiently far down causes normal void damage and death. The dimension height is 512 blocks from Y=-192 to Y=319.
- Underworld: crossing above Y=315 still returns you to a safe cave in the Nether, as does `/underworld return`.
- `/underworld enter` provides a test entry without having to dig through Nether bedrock.

## Requirements

- Minecraft Java 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API compatible with Minecraft 26.3 (Gradle version `0.162.0+26.3`)
- Java 25 to build

## Build on GitHub

Upload the **contents** of this ZIP to the root of a GitHub repository. Open **Actions → Build Minecraft 26.3 mod → Run workflow**. After a successful build, download the `unstable-underworld-26.3` artifact and extract `unstable-underworld-3.2.0.jar`. Install it and the matching Fabric API, removing any earlier Underworld JARs.

The workflow runs a standalone terrain test and JSON/source checks before the full Gradle build. Local standalone checks do not prove compilation or in-game visuals.

## Source layout

- `UnderworldMod.java`: teleport transitions, commands and game modes; **no lower-void rescue**.
- `UnderworldMath.java`: geography, shallow hills, noise-driven fractures and regional materials.
- `UnderworldTerrain.java`: per-chunk painting and regeneration/migration of earlier Underworld chunks.
- `UnderworldStructures.java`: four styles of procedural ruins with foundations.
- `src/main/resources/data/underworld/`: separate dimension, biome and Nether-style dimension type.
- `scripts/`: tests and resource validation.

## Backup and migration notes

Version 3.2 identifies previous v3.1 Underworld chunks using the hidden lodestone marker at Y=-185, erases their terrain and structures, and generates the new version. v3.0 uses crying obsidian at Y=-184; v2 used gilded blackstone at Y=-182. Do not use the same world without a backup if you care about buildings in the Underworld. Existing Nether and Overworld chunks remain unchanged.

**Verification:** JSON resource checks and standalone Java terrain tests passed. This package has **not** been compiled against the full Minecraft/Fabric 26.3 libraries or visually tested in-game in this environment. The exact source of every possible visual artifact cannot be proven without screenshots/gameplay testing.

MIT license. Unofficial community project, not affiliated with Mojang or the Unstable SMP creators.
