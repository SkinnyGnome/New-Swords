package com.skinnygnome.newswords;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class NewSwordsClient implements ClientModInitializer {
    private boolean wasAttackPressed;
    private KeyBinding dimensionPhaseKey;

    @Override
    public void onInitializeClient() {
        dimensionPhaseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.newswords.dimension_phase",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.newswords.abilities"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.interactionManager == null) {
                wasAttackPressed = false;
                return;
            }

            boolean isAttackPressed = client.options.attackKey.isPressed();
            if (isAttackPressed && !wasAttackPressed && client.player.getMainHandStack().getItem() == NewSwordsMod.KATANA) {
                ClientPlayNetworking.send(NewSwordsMod.KATANA_DASH_PACKET_ID, PacketByteBufs.empty());
            }

            while (dimensionPhaseKey.wasPressed()) {
                if (client.player.getMainHandStack().getItem() == NewSwordsMod.LIGHTBLUE_DIMENSION_SWORD) {
                    ClientPlayNetworking.send(NewSwordsMod.LIGHTBLUE_PHASE_PACKET_ID, PacketByteBufs.empty());
                }
            }

            wasAttackPressed = isAttackPressed;
        });
    }
}
