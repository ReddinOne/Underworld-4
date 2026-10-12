package dev.unstable.underworld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Underworld 3.2 for Java 26.3 / Fabric.
 *
 * The vanilla Nether terrain is NEVER generated, modified or replaced here.
 * Nether (X,Z,Y < -10) -> Underworld (same X,Z; Y 296), still falling.
 * V3 terrain sits near Y=67 and is only 30 blocks thick; void expands outward.
 * Underworld falling into the lower void -> vanilla void damage/death.
 * Underworld crossing the upper boundary (Y > 315) -> safe Nether return.
 * Nether remains physically unchanged; bedrock below Y=5 blocks true under-floor emergence.
 *
 * Per-player original gamemode is persisted in the player's vanilla entity
 * tags, surviving save/relog; it is restored upon leaving the Underworld.
 */
public final class UnderworldMod implements ModInitializer {
    public static final String MOD_ID = "underworld";
    public static final ResourceKey<Level> UNDERWORLD = ResourceKey.create(
            Registries.DIMENSION, Identifier.fromNamespaceAndPath(MOD_ID, "underworld"));

    private static final double NETHER_ENTRY_Y = -10.0;
    private static final double UNDERWORLD_DROP_Y = 296.0;
    private static final double UNDERWORLD_UPPER_EXIT_Y = 315.0;
    private static final String MODE_TAG_PREFIX = "underworld.prevmode.";

    @Override
    public void onInitialize() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk, generated) -> {
            if (world.dimension().equals(UNDERWORLD)) {
                UnderworldTerrain.decorate(world, chunk);
            }
        });

        // Additional protection in case another mod alters Adventure permissions.
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !world.dimension().equals(UNDERWORLD));

        ServerTickEvents.END_SERVER_TICK.register(UnderworldMod::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("underworld")
                    .then(Commands.literal("enter")
                            .executes(ctx -> commandTeleport(ctx.getSource().getPlayerOrException(), true)))
                    .then(Commands.literal("return")
                            .executes(ctx -> commandTeleport(ctx.getSource().getPlayerOrException(), false))));
        });
    }

    private static int commandTeleport(ServerPlayer player, boolean enter) {
        MinecraftServer server = player.level().getServer();
        ServerLevel target = server == null ? null : server.getLevel(enter ? UNDERWORLD : Level.NETHER);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Underworld dimension unavailable. Check datapack logs."));
            return 0;
        }
        boolean success = enter ? enterUnderworld(player, target) : returnToNether(player, target);
        if (success) player.sendSystemMessage(Component.literal(enter
                ? "The void opens beneath you..." : "Returned to the Nether."));
        return success ? 1 : 0;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        ServerLevel underworld = server.getLevel(UNDERWORLD);
        if (nether == null || underworld == null) return;

        List<Entity> entering = new ArrayList<>();
        for (Entity e : nether.getAllEntities()) {
            if (!e.isRemoved() && !e.isPassenger() && e.getY() < NETHER_ENTRY_Y) entering.add(e);
        }
        List<Entity> leaving = new ArrayList<>();
        for (Entity e : underworld.getAllEntities()) {
            if (!e.isRemoved() && !e.isPassenger() && e.getY() > UNDERWORLD_UPPER_EXIT_Y) {
                leaving.add(e);
            }
        }

        for (Entity e : entering) enterUnderworld(e, underworld);
        for (Entity e : leaving) returnToNether(e, nether);

        // Never rescue downward-falling entities: the Underworld has no lower portal.
        // Minecraft handles normal void damage/death below the dimension floor.
        // Repair mode on entry/relog and after external dimension changes/death.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(UNDERWORLD)) {
                setAdventureMode(player);
            } else {
                restoreOriginalMode(player);
            }
        }
    }

    private static boolean enterUnderworld(Entity entity, ServerLevel dest) {
        if (entity.level().dimension().equals(UNDERWORLD)) return false;
        double x = entity.getX();
        double z = entity.getZ();
        Vec3 motion = entity.getDeltaMovement();
        // Chunk load triggers the independent Underworld geology. No landing search,
        // no fake spawn platform, no teleport to the origin or another X/Z position.
        dest.getChunk(BlockPos.containing(x, 0, z).getX() >> 4,
                      BlockPos.containing(x, 0, z).getZ() >> 4);
        double y = UNDERWORLD_DROP_Y;
        if (entity instanceof ServerPlayer player) {
            saveOriginalMode(player);
        }
        // The final bool uses the same native 26.3 teleport API as version 1.
        boolean moved = entity.teleportTo(dest, x, y, z,
                Set.of(), entity.getYRot(), entity.getXRot(), true);
        if (moved) {
            // Dimension teleports may otherwise zero out movement.
            entity.setDeltaMovement(new Vec3(motion.x, Math.min(-1.0, motion.y), motion.z));
            // Avoid inheriting a huge fall-distance counter from the Nether.
            entity.resetFallDistance();
            if (entity instanceof ServerPlayer player) {
                setAdventureMode(player);
            }
            // Resistance does not slow or interrupt the cinematic fall. Covers the
            // long vertical drop and the first landing without altering gravity.
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,
                        280, 4, true, false, false));
            }
        } else if (entity instanceof ServerPlayer player) {
            restoreOriginalMode(player);
        }
        return moved;
    }

    private static boolean returnToNether(Entity entity, ServerLevel nether) {
        int x = (int) Math.floor(entity.getX());
        int z = (int) Math.floor(entity.getZ());
        BlockPos destination = safeNetherLanding(nether, x, z);
        if (destination == null) return false;
        boolean moved = entity.teleportTo(nether,
                destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                Set.of(), entity.getYRot(), entity.getXRot(), true);
        if (moved) {
            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();
            if (entity instanceof ServerPlayer player) restoreOriginalMode(player);
        }
        return moved;
    }

    private static void saveOriginalMode(ServerPlayer player) {
        if (previousModeTag(player) != null) return;
        player.addTag(MODE_TAG_PREFIX + player.gameMode().getName());
    }

    private static void setAdventureMode(ServerPlayer player) {
        saveOriginalMode(player);
        if (player.gameMode() != GameType.ADVENTURE) player.setGameMode(GameType.ADVENTURE);
    }

    private static void restoreOriginalMode(ServerPlayer player) {
        String tag = previousModeTag(player);
        if (tag == null) return;
        String saved = tag.substring(MODE_TAG_PREFIX.length());
        GameType original = switch (saved) {
            case "creative" -> GameType.CREATIVE;
            case "spectator" -> GameType.SPECTATOR;
            case "adventure" -> GameType.ADVENTURE;
            default -> GameType.SURVIVAL;
        };
        player.setGameMode(original);
        player.removeTag(tag);
    }

    private static String previousModeTag(ServerPlayer player) {
        for (String tag : player.entityTags()) {
            if (tag.startsWith(MODE_TAG_PREFIX)) return tag;
        }
        return null;
    }

    /**
     * Exit close to the Nether's BEDROCK FLOOR, not half way up the dimension.
     * Search low cavities first, directly under the entry X/Z, then spiral out.
     * Bedrock and netherrack are not modified. If no safe lower pocket exists,
     * expand vertically as an explicit last resort instead of suffocating.
     *
     * Below Nether's floor bedrock (Y<0), vanilla has only void. Spawning
     * literally underneath would cause immediate re-entry; so the correct
     * survivable position is just ABOVE its lower bedrock layer.
     */
    private static BlockPos safeNetherLanding(ServerLevel world, int x, int z) {
        for (int upperY : new int[]{21, 31, 45, 65, 100}) {
            for (int radius = 0; radius <= 56; radius += 4) {
                for (int dx = -radius; dx <= radius; dx += 4) {
                    for (int dz = -radius; dz <= radius; dz += 4) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        for (int y = 5; y <= upperY; y++) {
                            BlockPos floor = new BlockPos(x + dx, y, z + dz);
                            if (safeFloor(world, floor)) return floor.above();
                        }
                    }
                }
            }
        }
        return null; // Never place entities inside blocks or below the floor.
    }

    private static boolean safeFloor(ServerLevel world, BlockPos floor) {
        BlockState state = world.getBlockState(floor);
        return state.isSolid() && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.BEDROCK)
                && world.getBlockState(floor.above()).isAir()
                && world.getBlockState(floor.above(2)).isAir();
    }
}
