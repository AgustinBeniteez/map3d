package com.agustinbenitez.voxelview3d.world;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.agustinbenitez.voxelview3d.VoxelView3D;

import com.agustinbenitez.voxelview3d.client.ClientSettings;
import com.agustinbenitez.voxelview3d.client.VoxelMapScreen;


public class WorldHandler {
    public static void registerEvents() {
        net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(WorldHandler::onClientTick);
    }

    private static final int QUEUE_REFRESH_INTERVAL_TICKS = 20;
    private static final int SCAN_INTERVAL_TICKS = 2;
    private static final int MAX_STALE_CHUNKS_PER_REFRESH = 2;
    private static final long CHUNK_REFRESH_AGE_TICKS = 600L;

    private static int refreshTickCounter = QUEUE_REFRESH_INTERVAL_TICKS;
    private static int scanTickCounter = SCAN_INTERVAL_TICKS;
    private static final Queue<ChunkPos> scanQueue = new ArrayDeque<>();
    private static final Set<Long> queuedChunks = new HashSet<>();
    private static final Map<Long, Long> lastScanTicks = new HashMap<>();
    private static boolean mapWasOpen;
    
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension;
    private static ChunkPos lastPos;

    public static void reset() {
        ChunkScanner.clear();
        clearScanQueue();
        lastScanTicks.clear();
        lastDimension = null;
        lastPos = null;
        mapWasOpen = false;
        refreshTickCounter = QUEUE_REFRESH_INTERVAL_TICKS;
        scanTickCounter = SCAN_INTERVAL_TICKS;
    }

    
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean mapIsOpen = mc.screen instanceof VoxelMapScreen;

        // The 3D scan is expensive and is useful only while its screen is visible.
        if (!mapIsOpen || mc.level == null || mc.player == null) {
            if (mapWasOpen) clearScanQueue();
            mapWasOpen = false;
            return;
        }

        if (!mapWasOpen) {
            mapWasOpen = true;
            refreshTickCounter = QUEUE_REFRESH_INTERVAL_TICKS;
            scanTickCounter = SCAN_INTERVAL_TICKS;
        }

        var currentDimension = mc.level.dimension();
        ChunkPos currentPos = mc.player.chunkPosition();

        boolean dimensionChanged = lastDimension != null && !lastDimension.equals(currentDimension);
        boolean movedSignificantly = lastPos != null
                && (Math.abs(currentPos.x() - lastPos.x()) > 2 || Math.abs(currentPos.z() - lastPos.z()) > 2);

        if (dimensionChanged) {
            ChunkScanner.clear();
            clearScanQueue();
            lastScanTicks.clear();
            refreshTickCounter = QUEUE_REFRESH_INTERVAL_TICKS;
        } else if (movedSignificantly) {
            // Drop queued work from the old position so nearby chunks have priority.
            clearScanQueue();
            refreshTickCounter = QUEUE_REFRESH_INTERVAL_TICKS;

            if (Math.abs(currentPos.x() - lastPos.x()) > 10 || Math.abs(currentPos.z() - lastPos.z()) > 10) {
                ChunkScanner.clear();
                lastScanTicks.clear();
            }
        }

        lastDimension = currentDimension;
        lastPos = currentPos;

        if (++refreshTickCounter >= QUEUE_REFRESH_INTERVAL_TICKS) {
            refreshTickCounter = 0;
            refreshScanQueue();
        }

        // Scan one chunk at a time to spread the work across frames.
        if (++scanTickCounter >= SCAN_INTERVAL_TICKS) {
            scanTickCounter = 0;
            processScanQueue(1);
        }
    }

    private static void refreshScanQueue() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ChunkPos playerPos = mc.player.chunkPosition();
        int radius = ClientSettings.renderDistance;
        long currentTick = mc.level.getGameTime();
        
        ChunkScanner.prune(playerPos, radius + 2);
        lastScanTicks.keySet().removeIf(key -> isOutsideRadius(ChunkPos.unpack(key), playerPos, radius + 2));
        
        List<ChunkPos> missingChunks = new ArrayList<>();
        List<ChunkPos> staleChunks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos pos = new ChunkPos(playerPos.x() + x, playerPos.z() + z);
                long key = ChunkPos.pack(pos.x(), pos.z());
                if (queuedChunks.contains(key)) continue;

                Long lastScanTick = lastScanTicks.get(key);
                if (!ChunkScanner.contains(pos)) {
                    missingChunks.add(pos);
                } else if (lastScanTick == null || currentTick - lastScanTick >= CHUNK_REFRESH_AGE_TICKS) {
                    staleChunks.add(pos);
                }
            }
        }
        
        missingChunks.sort(Comparator.comparingInt(pos -> distSq(pos, playerPos)));
        for (ChunkPos pos : missingChunks) enqueue(pos);

        // Refresh only a small number of old chunks per second. This keeps block
        // changes visible without continuously rescanning the entire map.
        staleChunks.sort(Comparator
                .comparingLong((ChunkPos pos) -> lastScanTicks.getOrDefault(ChunkPos.pack(pos.x(), pos.z()), Long.MIN_VALUE))
                .thenComparingInt(pos -> distSq(pos, playerPos)));
        for (int i = 0; i < Math.min(MAX_STALE_CHUNKS_PER_REFRESH, staleChunks.size()); i++) {
            enqueue(staleChunks.get(i));
        }
    }
    
    private static int distSq(ChunkPos c1, ChunkPos c2) {
        int dx = c1.x() - c2.x();
        int dz = c1.z() - c2.z();
        return dx * dx + dz * dz;
    }

    private static boolean isOutsideRadius(ChunkPos pos, ChunkPos center, int radius) {
        return Math.abs(pos.x() - center.x()) > radius || Math.abs(pos.z() - center.z()) > radius;
    }

    private static void enqueue(ChunkPos pos) {
        if (queuedChunks.add(ChunkPos.pack(pos.x(), pos.z()))) scanQueue.add(pos);
    }

    private static void clearScanQueue() {
        scanQueue.clear();
        queuedChunks.clear();
    }
    
    private static void processScanQueue(int chunksToScan) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        for (int i = 0; i < chunksToScan; i++) {
            ChunkPos pos = scanQueue.poll();
            if (pos == null) break;
            queuedChunks.remove(ChunkPos.pack(pos.x(), pos.z()));
            
            LevelChunk chunk = (LevelChunk) mc.level.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
            if (chunk != null) {
                ChunkScanner.scanChunk(chunk);
                lastScanTicks.put(ChunkPos.pack(pos.x(), pos.z()), mc.level.getGameTime());
            }
        }
    }
}
