
package com.aarav.chunkminer.network;

import com.aarav.chunkminer.ChunkMinerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StartChunkMiningPayload(BlockPos origin)
        implements CustomPacketPayload {

    public static final Type<StartChunkMiningPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    ChunkMinerMod.MOD_ID,
                    "start_chunk_mining"
            ));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartChunkMiningPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    StartChunkMiningPayload::origin,
                    StartChunkMiningPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
