package com.example.voxelview3d.world;

import com.example.voxelview3d.VoxelView3D;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedList;
import java.util.Queue;

@Mod.EventBusSubscriber(modid = VoxelView3D.MODID, value = Dist.CLIENT)
public class WorldHandler {
    private static int tickCounter = 0;
    private static final Queue<ChunkPos> scanQueue = new LinkedList<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level != null) {
            tickCounter++;
            
            // Periodically refresh the scan queue (every 2 seconds)
            if (tickCounter >= 40) {
                tickCounter = 0;
                refreshScanQueue();
            }
            
            // Process scan queue (scan 2 chunks per tick to avoid lag)
            processScanQueue(2);
        }
    }

    private static void refreshScanQueue() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ChunkPos playerPos = mc.player.chunkPosition();
        int radius = 10; // Scan radius

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                scanQueue.offer(new ChunkPos(playerPos.x + x, playerPos.z + z));
            }
        }
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
