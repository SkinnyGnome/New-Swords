package com.skinnygnome.newswords;

import com.skinnygnome.newswords.item.KatanaItem;
import java.util.HashMap;
import java.util.Iterator;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewSwordsMod implements ModInitializer {
    public static final String MOD_ID = "newswords";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int SPECTATOR_DURATION_TICKS = 10 * 20;
    private static final float KATANA_SPECIAL_DAMAGE = 5.0f;
    public static final Identifier KATANA_DASH_PACKET_ID = new Identifier(MOD_ID, "katana_dash");

    private static final Map<UUID, SpectatorState> SPECTATOR_PLAYERS = new HashMap<>();

    public static final Item KATANA = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "katana"),
            new KatanaItem(ToolMaterials.IRON, 3, -2.2f, new Item.Settings().maxCount(1))
    );

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(KATANA));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != Hand.MAIN_HAND || player.getMainHandStack().getItem() != KATANA) {
                return ActionResult.PASS;
            }

            if (!world.isClient && entity instanceof LivingEntity livingEntity) {
                livingEntity.damage(player.getDamageSources().playerAttack(player), KATANA_SPECIAL_DAMAGE);
                player.swingHand(hand, true);
            }

            return ActionResult.SUCCESS;
        });

        ServerPlayNetworking.registerGlobalReceiver(KATANA_DASH_PACKET_ID, (server, player, handler, buf, responseSender) ->
                server.execute(() -> {
                    if (player.getMainHandStack().getItem() == KATANA) {
                        performKatanaDash(player);
                    }
                }));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
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
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SpectatorState state = SPECTATOR_PLAYERS.remove(handler.player.getUuid());
            if (state != null && handler.player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
                handler.player.interactionManager.changeGameMode(state.previousGameMode);
            }
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
}
