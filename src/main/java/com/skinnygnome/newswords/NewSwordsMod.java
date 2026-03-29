package com.skinnygnome.newswords;

import com.skinnygnome.newswords.item.KatanaItem;
import com.skinnygnome.newswords.item.LightblueDimensionSwordItem;
import com.skinnygnome.newswords.item.RageSwordItem;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewSwordsMod implements ModInitializer {
    public static final String MOD_ID = "newswords";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int SPECTATOR_DURATION_TICKS = 10 * 20;
    private static final float KATANA_SPECIAL_DAMAGE = 5.0f;
    private static final int RAGE_POINT_DURATION_TICKS = 10 * 20;
    private static final int RAGE_MAX_POINTS = 10;
    private static final float RAGE_BASE_DAMAGE = 5.0f;
    private static final float RAGE_DAMAGE_PER_POINT = 1.0f;
    private static final float RAGE_EXPLOSION_DAMAGE_PER_POINT = 2.0f;
    private static final float RAGE_BASE_EXPLOSION_SIZE = 1.0f;
    private static final int RAGE_POINTS_PER_SPEED_LEVEL = 2;
    private static final float LIGHTBLUE_DIMENSION_SWORD_DAMAGE = 10.0f;
    private static final int LIGHTBLUE_PHASE_DURATION_TICKS = 5 * 20;
    public static final Identifier KATANA_DASH_PACKET_ID = new Identifier(MOD_ID, "katana_dash");
    public static final Identifier LIGHTBLUE_PHASE_PACKET_ID = new Identifier(MOD_ID, "lightblue_phase");

    private static final Map<UUID, SpectatorState> SPECTATOR_PLAYERS = new HashMap<>();
    private static final Map<UUID, RageState> RAGE_STATES = new HashMap<>();
    private static final Map<UUID, PhaseState> PHASE_STATES = new HashMap<>();
    private static long serverTickCounter = 0L;

    public static final Item KATANA = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "katana"),
            new KatanaItem(ToolMaterials.IRON, 3, -2.2f, new Item.Settings().maxCount(1))
    );

    public static final Item RAGE_BLADE = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "rage_blade"),
            new RageSwordItem(ToolMaterials.NETHERITE, 3, -2.4f, new Item.Settings().maxCount(1).fireproof())
    );

    public static final Item LIGHTBLUE_DIMENSION_SWORD = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "lightblue_dimension_sword"),
            new LightblueDimensionSwordItem(ToolMaterials.DIAMOND, 3, -2.4f, new Item.Settings().maxCount(1))
    );

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(KATANA);
            entries.add(RAGE_BLADE);
            entries.add(LIGHTBLUE_DIMENSION_SWORD);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }

            Item mainHandItem = player.getMainHandStack().getItem();

            if (mainHandItem == KATANA) {
                if (!world.isClient && entity instanceof LivingEntity livingEntity) {
                    livingEntity.damage(player.getDamageSources().playerAttack(player), KATANA_SPECIAL_DAMAGE);
                    player.swingHand(hand, true);
                }

                return ActionResult.SUCCESS;
            }

            if (mainHandItem == LIGHTBLUE_DIMENSION_SWORD) {
                if (!world.isClient && entity instanceof LivingEntity livingEntity) {
                    livingEntity.damage(player.getDamageSources().playerAttack(player), LIGHTBLUE_DIMENSION_SWORD_DAMAGE);
                    player.swingHand(hand, true);
                }

                return ActionResult.SUCCESS;
            }

            if (mainHandItem != RAGE_BLADE) {
                return ActionResult.PASS;
            }

            if (!world.isClient && entity instanceof LivingEntity livingEntity) {
                int points = getRagePoints(player.getUuid());
                float damage = RAGE_BASE_DAMAGE + (points * RAGE_DAMAGE_PER_POINT);

                livingEntity.damage(player.getDamageSources().playerAttack(player), damage);
                player.swingHand(hand, true);

                if (!livingEntity.isAlive()) {
                    addRagePoint(player.getUuid());
                    player.sendMessage(Text.literal("Rage +1 (" + getRagePoints(player.getUuid()) + "/" + RAGE_MAX_POINTS + ")"), true);
                }
            }

            return ActionResult.SUCCESS;
        });

        ServerPlayNetworking.registerGlobalReceiver(KATANA_DASH_PACKET_ID, (server, player, handler, buf, responseSender) ->
                server.execute(() -> {
                    if (player.getMainHandStack().getItem() == KATANA) {
                        performKatanaDash(player);
                    }
                }));

        ServerPlayNetworking.registerGlobalReceiver(LIGHTBLUE_PHASE_PACKET_ID, (server, player, handler, buf, responseSender) ->
                server.execute(() -> {
                    if (player.getMainHandStack().getItem() == LIGHTBLUE_DIMENSION_SWORD) {
                        activateLightbluePhase(player);
                    }
                }));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            serverTickCounter++;

            Iterator<Map.Entry<UUID, SpectatorState>> iterator = SPECTATOR_PLAYERS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, SpectatorState> entry = iterator.next();
                SpectatorState state = entry.getValue();
                state.ticksRemaining--;

                if (state.ticksRemaining > 0) {
                    continue;
                }

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null && player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
                    player.interactionManager.changeGameMode(state.previousGameMode);
                    if (player.getWorld() instanceof ServerWorld serverWorld) {
                        serverWorld.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.35, 0.6, 0.35, 0.05);
                        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.0f);
                    }
                    player.sendMessage(Text.literal("Katana spectate ended."), true);
                }

                iterator.remove();
            }

            Iterator<Map.Entry<UUID, RageState>> rageIterator = RAGE_STATES.entrySet().iterator();
            while (rageIterator.hasNext()) {
                Map.Entry<UUID, RageState> entry = rageIterator.next();
                RageState state = entry.getValue();
                state.pruneExpired(serverTickCounter);

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    int points = state.getPoints(serverTickCounter);
                    if (points > 0 && player.getMainHandStack().getItem() == RAGE_BLADE) {
                        int speedLevels = points / RAGE_POINTS_PER_SPEED_LEVEL;
                        if (speedLevels > 0) {
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 25, speedLevels - 1, true, false, true));
                        }

                        if (serverTickCounter % 10 == 0) {
                            int speedBonus = points / RAGE_POINTS_PER_SPEED_LEVEL;
                            player.sendMessage(Text.literal("Rage: " + points + "/" + RAGE_MAX_POINTS + " | +" + points + " dmg | Speed " + speedBonus), true);
                        }
                    }
                }

                if (state.isEmpty(serverTickCounter)) {
                    rageIterator.remove();
                }
            }

            Iterator<Map.Entry<UUID, PhaseState>> phaseIterator = PHASE_STATES.entrySet().iterator();
            while (phaseIterator.hasNext()) {
                Map.Entry<UUID, PhaseState> entry = phaseIterator.next();
                PhaseState state = entry.getValue();
                state.ticksRemaining--;

                if (state.ticksRemaining > 0) {
                    continue;
                }

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    boolean invulnerableByGamemode = player.interactionManager.getGameMode() == GameMode.CREATIVE
                            || player.interactionManager.getGameMode() == GameMode.SPECTATOR;
                    player.getAbilities().invulnerable = state.originalInvulnerable || invulnerableByGamemode;
                    player.sendAbilitiesUpdate();
                    player.sendMessage(Text.literal("Dimension phase ended."), true);
                }

                phaseIterator.remove();
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SpectatorState state = SPECTATOR_PLAYERS.remove(handler.player.getUuid());
            if (state != null && handler.player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
                handler.player.interactionManager.changeGameMode(state.previousGameMode);
            }

            RAGE_STATES.remove(handler.player.getUuid());
            PHASE_STATES.remove(handler.player.getUuid());
        });

        LOGGER.info("New Swords initialized.");
    }

    public static boolean activateKatanaSpectator(ServerPlayerEntity player) {
        GameMode currentGameMode = player.interactionManager.getGameMode();
        if (currentGameMode == GameMode.SPECTATOR) {
            return false;
        }

        player.interactionManager.changeGameMode(GameMode.SPECTATOR);
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.35, 0.6, 0.35, 0.05);
            serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.1f);
        }
        SPECTATOR_PLAYERS.put(player.getUuid(), new SpectatorState(currentGameMode, SPECTATOR_DURATION_TICKS));
        player.sendMessage(Text.literal("Katana spectate active for 10 seconds."), true);
        return true;
    }

    public static boolean triggerRageExplosion(ServerPlayerEntity player) {
        int points = getRagePoints(player.getUuid());
        if (points <= 0) {
            player.sendMessage(Text.literal("No rage points to detonate."), true);
            return false;
        }

        float explosionSize = RAGE_BASE_EXPLOSION_SIZE + (points / 2.0f);
        float explosionDamage = points * RAGE_EXPLOSION_DAMAGE_PER_POINT;

        ServerWorld world = player.getServerWorld();
        Vec3d center = player.getPos().add(0.0, 1.0, 0.0);
        world.createExplosion(player, center.x, center.y, center.z, explosionSize, true, World.ExplosionSourceType.NONE);

        double radius = Math.max(2.5, explosionSize * 2.0);
        Box damageBox = new Box(
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius
        );

        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class,
                damageBox,
                target -> target.isAlive() && target != player && target.squaredDistanceTo(center) <= radius * radius
        );

        for (LivingEntity target : targets) {
            target.damage(player.getDamageSources().playerAttack(player), explosionDamage);
        }

        clearRagePoints(player.getUuid());
        player.sendMessage(Text.literal("Rage detonation!"), true);
        return true;
    }

    public static boolean teleportLightblueSwordUser(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d rayTarget = player.getEyePos().add(look.multiply(48.0));
        Vec3d targetPos = player.getWorld()
                .raycast(new RaycastContext(
                        player.getEyePos(),
                        rayTarget,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        player
                ))
                .getPos();

        BlockPos base = BlockPos.ofFloored(targetPos);
        BlockPos safe = findSafeTeleportSpot(player.getServerWorld(), base.up());
        if (safe == null) {
            player.sendMessage(Text.literal("No safe teleport location found."), true);
            return false;
        }

        Vec3d destination = Vec3d.ofBottomCenter(safe);
        player.requestTeleport(destination.x, destination.y, destination.z);
        player.velocityModified = true;

        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, destination.x, destination.y + 1.0, destination.z, 30, 0.35, 0.45, 0.35, 0.01);
        world.playSound(null, safe, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.25f);
        return true;
    }

    private static void activateLightbluePhase(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PhaseState existing = PHASE_STATES.get(playerId);
        boolean originalInvulnerable = existing != null ? existing.originalInvulnerable : player.getAbilities().invulnerable;

        player.getAbilities().invulnerable = true;
        player.sendAbilitiesUpdate();
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, LIGHTBLUE_PHASE_DURATION_TICKS, 0, true, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, LIGHTBLUE_PHASE_DURATION_TICKS, 2, true, false, true));
        PHASE_STATES.put(playerId, new PhaseState(originalInvulnerable, LIGHTBLUE_PHASE_DURATION_TICKS));
        player.sendMessage(Text.literal("Dimension phase active for 5 seconds."), true);
    }

    private static BlockPos findSafeTeleportSpot(ServerWorld world, BlockPos start) {
        int minY = world.getBottomY() + 1;
        int maxY = world.getTopY() - 2;
        int x = start.getX();
        int z = start.getZ();

        int clampedStartY = Math.max(minY, Math.min(start.getY(), maxY));
        for (int y = clampedStartY; y <= Math.min(clampedStartY + 8, maxY); y++) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.up();
            BlockPos ground = feet.down();
            boolean hasSpace = world.getBlockState(feet).isAir() && world.getBlockState(head).isAir();
            boolean hasGround = !world.getBlockState(ground).isAir();

            if (hasSpace && hasGround) {
                return feet;
            }
        }

        return null;
    }

    private static int getRagePoints(UUID playerId) {
        RageState state = RAGE_STATES.get(playerId);
        if (state == null) {
            return 0;
        }

        return state.getPoints(serverTickCounter);
    }

    private static void addRagePoint(UUID playerId) {
        RageState state = RAGE_STATES.computeIfAbsent(playerId, uuid -> new RageState());
        state.addPoint(serverTickCounter + RAGE_POINT_DURATION_TICKS);
    }

    private static void clearRagePoints(UUID playerId) {
        RageState state = RAGE_STATES.get(playerId);
        if (state != null) {
            state.clear();
        }
    }

    private static void performKatanaDash(PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d horizontal = new Vec3d(look.x, 0.0, look.z);
        if (horizontal.lengthSquared() > 0.0001) {
            horizontal = horizontal.normalize().multiply(0.85);
        }

        player.addVelocity(horizontal.x, 0.1, horizontal.z);
        player.velocityModified = true;

        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.25, 0.15, 0.25, 0.04);
            serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9f, 1.15f);
        }
    }

    private static final class SpectatorState {
        private final GameMode previousGameMode;
        private int ticksRemaining;

        private SpectatorState(GameMode previousGameMode, int ticksRemaining) {
            this.previousGameMode = previousGameMode;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static final class RageState {
        private final ArrayDeque<Long> pointExpiryTicks = new ArrayDeque<>();

        private int getPoints(long currentTick) {
            pruneExpired(currentTick);
            return pointExpiryTicks.size();
        }

        private boolean isEmpty(long currentTick) {
            return getPoints(currentTick) == 0;
        }

        private void addPoint(long expiryTick) {
            if (pointExpiryTicks.size() >= RAGE_MAX_POINTS) {
                return;
            }

            pointExpiryTicks.addLast(expiryTick);
        }

        private void pruneExpired(long currentTick) {
            while (!pointExpiryTicks.isEmpty() && pointExpiryTicks.peekFirst() <= currentTick) {
                pointExpiryTicks.removeFirst();
            }
        }

        private void clear() {
            pointExpiryTicks.clear();
        }
    }

    private static final class PhaseState {
        private final boolean originalInvulnerable;
        private int ticksRemaining;

        private PhaseState(boolean originalInvulnerable, int ticksRemaining) {
            this.originalInvulnerable = originalInvulnerable;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
