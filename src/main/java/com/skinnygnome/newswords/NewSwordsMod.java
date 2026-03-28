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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
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

    private static final int SPECTATOR_DURATION_TICKS = 5 * 20;
    private static final float KATANA_SPECIAL_DAMAGE = 5.0f;

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
                performKatanaDash(player);
                livingEntity.damage(player.getDamageSources().playerAttack(player), KATANA_SPECIAL_DAMAGE);
                player.swingHand(hand, true);
            }

            return ActionResult.SUCCESS;
        });

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
        SPECTATOR_PLAYERS.put(player.getUuid(), new SpectatorState(currentGameMode, SPECTATOR_DURATION_TICKS));
        player.sendMessage(Text.literal("Katana spectate active for 5 seconds."), true);
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
