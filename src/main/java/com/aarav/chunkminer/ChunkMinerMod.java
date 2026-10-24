package com.aarav.chunkminer;

import com.aarav.chunkminer.network.StartChunkMiningPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ChunkMinerMod implements ModInitializer {
    public static final String MOD_ID = "chunkminer";

    private static final int DURABILITY_RESERVE = 10;

    // Fast mining breaks a fixed number of blocks per server tick, while scan limits bound CPU work.
    private static final int BLOCKS_PER_JOB_PER_TICK = 8;
    private static final int MAX_SCANS_PER_JOB_PER_TICK = 1_024;
    private static final int MAX_SCANS_GLOBAL_PER_TICK = 4_096;
    private static final int MAX_ACTIVE_JOBS = 4;
    private static final int POSITIONS_PER_LAYER = 16 * 16;

    private static final Map<UUID, MiningJob> JOBS = new HashMap<>();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(
                StartChunkMiningPayload.TYPE,
                StartChunkMiningPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                StartChunkMiningPayload.TYPE,
                (payload, context) -> context.server().execute(() -> startJob(context.player()))
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> tickJobs());
    }

    private static void startJob(ServerPlayer player) {
        if (player.isRemoved() || player.isSpectator()) {
            return;
        }

        UUID playerId = player.getUUID();
        if (JOBS.remove(playerId) != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Chunk Miner: mining cancelled."
            ));
            return;
        }

        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty() || !tool.isDamageableItem()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Chunk Miner: hold a damageable tool in your main hand."
            ));
            return;
        }

        if (JOBS.size() >= MAX_ACTIVE_JOBS) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Chunk Miner: the server already has the maximum number of active mining jobs."
            ));
            return;
        }

        // Always derive the target chunk from the server-side player position.
        BlockPos origin = player.blockPosition();
        int chunkX = Math.floorDiv(origin.getX(), 16);
        int chunkZ = Math.floorDiv(origin.getZ(), 16);

        ServerLevel level = player.level();
        int minY = level.getMinY();
        int maxY = level.getMaxY() - 1;
        int startY = Math.max(minY, Math.min(maxY, origin.getY()));

        MiningJob job = new MiningJob(level, chunkX, chunkZ, minY, maxY, startY, tool);
        JOBS.put(playerId, job);

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Chunk Miner: started chunk [" + chunkX + ", " + chunkZ
                        + "]. Fast mining enabled (up to 8 blocks per tick). Press G again to cancel."
        ));
    }

    private static void tickJobs() {
        int globalScanBudget = MAX_SCANS_GLOBAL_PER_TICK;
        Iterator<Map.Entry<UUID, MiningJob>> iterator = JOBS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, MiningJob> entry = iterator.next();
            UUID playerId = entry.getKey();
            MiningJob job = entry.getValue();
            ServerPlayer player = job.level.getServer().getPlayerList().getPlayer(playerId);

            if (player == null || player.isRemoved() || player.level() != job.level) {
                iterator.remove();
                continue;
            }

            ItemStack heldTool = player.getMainHandItem();
            if (heldTool != job.tool) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Chunk Miner: stopped because you changed your held tool."
                ));
                iterator.remove();
                continue;
            }

            if (heldTool.isEmpty() || !heldTool.isDamageableItem()
                    || remainingDurability(heldTool) <= DURABILITY_RESERVE) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Chunk Miner: stopped to preserve the last 10 durability."
                ));
                iterator.remove();
                continue;
            }

            if (globalScanBudget > 0) {
                int scanBudget = Math.min(MAX_SCANS_PER_JOB_PER_TICK, globalScanBudget);
                int scanned = processFastMiningTick(player, job, heldTool, scanBudget);
                globalScanBudget -= scanned;
            }

            if (remainingDurability(heldTool) <= DURABILITY_RESERVE) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Chunk Miner: stopped to preserve the last 10 durability."
                ));
                iterator.remove();
                continue;
            }

            if (job.isComplete()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Chunk Miner: chunk finished."
                ));
                iterator.remove();
            }
        }
    }

    /**
     * Fast mode: immediately destroys up to eight eligible blocks per server tick.
     * Position scanning is incremental and bounded, so the mod does not allocate a queue
     * containing every block position in the chunk when G is pressed.
     *
     * @return number of positions scanned, used to enforce the global per-tick scan budget
     */
    private static int processFastMiningTick(ServerPlayer player, MiningJob job,
                                             ItemStack tool, int scanBudget) {
        int scanned = 0;
        int destroyed = 0;

        while (scanned < scanBudget
                && destroyed < BLOCKS_PER_JOB_PER_TICK
                && job.nextIndex < job.totalPositions
                && remainingDurability(tool) > DURABILITY_RESERVE) {
            BlockPos pos = job.positionAt(job.nextIndex++);
            scanned++;

            BlockState state = job.level.getBlockState(pos);
            if (!isEligibleTarget(job, state, pos)) {
                continue;
            }

            // Use the server's normal block-destruction pathway for drops, harvest checks,
            // enchantments, durability, and block-break hooks. This deliberately skips
            // vanilla mining-time accumulation to preserve the original fast behavior.
            if (player.gameMode.destroyBlock(pos)) {
                destroyed++;
            }
        }

        return scanned;
    }

    private static boolean isEligibleTarget(MiningJob job, BlockState state, BlockPos pos) {
        if (state.isAir() || state.getDestroySpeed(job.level, pos) < 0.0F) {
            return false;
        }
        if (state.is(Blocks.BEDROCK)) {
            return false;
        }
        // Do not silently destroy containers or blocks that store data.
        return job.level.getBlockEntity(pos) == null;
    }

    private static int remainingDurability(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    private static final class MiningJob {
        private final ServerLevel level;
        private final int chunkX;
        private final int chunkZ;
        private final int minY;
        private final int maxY;
        private final int startY;
        private final int firstPhaseLayers;
        private final long totalPositions;
        private final ItemStack tool;

        private long nextIndex;

        private MiningJob(ServerLevel level, int chunkX, int chunkZ,
                          int minY, int maxY, int startY, ItemStack tool) {
            this.level = level;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minY = minY;
            this.maxY = maxY;
            this.startY = startY;
            this.firstPhaseLayers = startY - minY + 1;
            this.totalPositions = (long) (maxY - minY + 1) * POSITIONS_PER_LAYER;
            this.tool = tool;
        }

        /**
         * Visit the player's current height down to minY first, then the remaining upper layers.
         * This reaches nearby terrain quickly while still visiting every position in the chunk.
         */
        private BlockPos positionAt(long index) {
            int layer = (int) (index / POSITIONS_PER_LAYER);
            int withinLayer = (int) (index % POSITIONS_PER_LAYER);
            int xOffset = withinLayer % 16;
            int zOffset = withinLayer / 16;

            int y = layer < firstPhaseLayers
                    ? startY - layer
                    : maxY - (layer - firstPhaseLayers);

            return new BlockPos(chunkX * 16 + xOffset, y, chunkZ * 16 + zOffset);
        }

        private boolean isComplete() {
            return nextIndex >= totalPositions;
        }
    }
}
