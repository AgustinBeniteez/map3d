package com.agustinbenitez.voxelview3d.world;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.agustinbenitez.voxelview3d.VoxelView3D;

@Mod.EventBusSubscriber(modid = VoxelView3D.MODID, value = Dist.CLIENT)
public class WorldHandler {
    private static int tickCounter = 40; // Start immediately
    private static final Queue<ChunkPos> scanQueue = new LinkedList<>();
    
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension;
    private static ChunkPos lastPos;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && mc.level != null && mc.player != null) {
            
            // Check for dimension change or large movement to force refresh
            var currentDimension = mc.level.dimension();
            ChunkPos currentPos = mc.player.chunkPosition();
            
            boolean dimensionChanged = lastDimension != null && !lastDimension.equals(currentDimension);
            // If moved more than 2 chunks, refresh immediately (responsive)
            boolean movedSignificantly = lastPos != null && (Math.abs(currentPos.x - lastPos.x) > 2 || Math.abs(currentPos.z - lastPos.z) > 2);
            
            if (dimensionChanged) {
                ChunkScanner.clear();
                scanQueue.clear();
                tickCounter = 20; // Force refresh immediately
            } else if (movedSignificantly) {
                 tickCounter = 20; // Force refresh immediately
                 // If moved VERY far (teleport), clear cache to avoid artifacts/memory usage
                 if (Math.abs(currentPos.x - lastPos.x) > 10 || Math.abs(currentPos.z - lastPos.z) > 10) {
                     ChunkScanner.clear();
                     scanQueue.clear();
                 }
            }
            
            lastDimension = currentDimension;
            lastPos = currentPos;
            
            tickCounter++;
            
            // Periodically refresh the scan queue (every 1 second = 20 ticks)
            // Faster refresh to respond to movement
            if (tickCounter >= 20) {
                tickCounter = 0;
                refreshScanQueue();
            }
            
            // Process scan queue (scan 5 chunks per tick for faster updates)
            processScanQueue(5);
        }
    }

    private static void refreshScanQueue() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ChunkPos playerPos = mc.player.chunkPosition();
        int radius = 16; // Increased radius
        
        // Prune old chunks first
        ChunkScanner.prune(playerPos, radius + 2); // Keep a bit more than scan radius

        // Clear existing queue to prioritize new position
        scanQueue.clear();
        
        // Collect chunks in range
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                chunks.add(new ChunkPos(playerPos.x + x, playerPos.z + z));
            }
        }
        
        // Sort by distance to player (closest first)
        chunks.sort((c1, c2) -> {
            double d1 = distSq(c1, playerPos);
            double d2 = distSq(c2, playerPos);
            return Double.compare(d1, d2);
        });
        
        scanQueue.addAll(chunks);
    }
    
    private static double distSq(ChunkPos c1, ChunkPos c2) {
        double dx = c1.x - c2.x;
        double dz = c1.z - c2.z;
        return dx * dx + dz * dz;
    }
    
    private static void processScanQueue(int chunksToScan) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        for (int i = 0; i < chunksToScan; i++) {
            ChunkPos pos = scanQueue.poll();
            if (pos == null) break;
            
            LevelChunk chunk = (LevelChunk) mc.level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (chunk != null) {
                ChunkScanner.scanChunk(chunk);
            }
        }
    }
}
