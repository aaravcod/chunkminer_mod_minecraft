package com.aarav.chunkminer.client;

import com.aarav.chunkminer.network.StartChunkMiningPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class ChunkMinerClient implements ClientModInitializer {
    private static KeyMapping activateKey;

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("chunkminer", "main")
            );

    @Override
    public void onInitializeClient() {
        activateKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.chunkminer.activate",
                        InputConstants.Type.KEYBOARD,
                        InputConstants.KEY_G,
                        CATEGORY
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (activateKey.consumeClick()) {
                if (client.player == null
                        || client.level == null
                        || client.getConnection() == null) {
                    continue;
                }

                BlockPos origin = client.player.blockPosition();

                ClientPlayNetworking.send(
                        new StartChunkMiningPayload(origin)
                );
            }
        });
    }
}
