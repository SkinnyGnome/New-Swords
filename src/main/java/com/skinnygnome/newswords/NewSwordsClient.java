package com.skinnygnome.newswords;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public class NewSwordsClient implements ClientModInitializer {
    private boolean wasAttackPressed;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.interactionManager == null) {
                wasAttackPressed = false;
                return;
            }

            boolean isAttackPressed = client.options.attackKey.isPressed();
            if (isAttackPressed && !wasAttackPressed && client.player.getMainHandStack().getItem() == NewSwordsMod.KATANA) {
                ClientPlayNetworking.send(NewSwordsMod.KATANA_DASH_PACKET_ID, PacketByteBufs.empty());
            }

            wasAttackPressed = isAttackPressed;
        });
    }
}
