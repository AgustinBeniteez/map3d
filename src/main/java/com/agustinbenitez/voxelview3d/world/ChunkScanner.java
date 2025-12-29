package com.agustinbenitez.voxelview3d.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChunkScanner {
    
    // Data structures for storage
    public static class ScannedChunk {
        public final int[] positions;
        public final int[] colors;
        public final byte[] lights;
        
        public ScannedChunk(int[] positions, int[] colors, byte[] lights) {
            this.positions = positions;
            this.colors = colors;
            this.lights = lights;
        }
    }
    
    // Render Types
    public static final int RENDER_BLOCK = 0;
    public static final int RENDER_TORCH = 1;
    public static final int RENDER_LANTERN = 2;
    
    private static final Map<ChunkPos, ScannedChunk> CHUNK_DATA = new HashMap<>();

    public static void scanChunk(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        
        List<Integer> positions = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<Byte> lights = new ArrayList<>();
        
        LevelChunkSection[] sections = chunk.getSections();
        int minBuildHeight = chunk.getMinBuildHeight();
        
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;
            
            int sectionY = chunk.getMinBuildHeight() + i * 16;
            
            for (int y = 0; y < 16; y++) {
                int worldY = sectionY + y;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.isAir()) { // Capture everything including fluids
                            // Check for exposure (culling hidden blocks)
                            // We check neighbors in the chunk. Cross-chunk checking is harder, so we might have artifacts at edges.
                            // But for map, it's okay.
                            
                            boolean exposed = false;
                            
                            // Check local 6 neighbors
                            // Optimized: only check if we are not at chunk boundary for simplicity, 
                            // or if we are, assume exposed if neighbor is unloaded (safe bet)
                            
                            if (isTransparent(section, x + 1, y, z) ||
                                isTransparent(section, x - 1, y, z) ||
                                isTransparent(section, x, y + 1, z) ||
                                isTransparent(section, x, y - 1, z) ||
                                isTransparent(section, x, y, z + 1) ||
                                isTransparent(section, x, y, z - 1)) {
                                exposed = true;
                            }
                            
                            // Also check if it's the top surface block (Heightmap check)
                            // This ensures top soil is always drawn even if surrounded by other blocks locally
                            int surfaceHeight = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                            if (worldY == surfaceHeight - 1) exposed = true;

                            // DEBUG: Always expose non-air blocks for testing if exposure check is failing
                            exposed = true; 

                            if (exposed) {
                                // Determine Render Type and Special Color
                                int renderType = RENDER_BLOCK;
                                boolean isTorch = false;
                                boolean isLantern = false;
                                
                                if (state.getBlock() == Blocks.TORCH || state.getBlock() == Blocks.WALL_TORCH) {
                                    renderType = RENDER_TORCH;
                                    isTorch = true;
                                } else if (state.getBlock() == Blocks.SOUL_TORCH || state.getBlock() == Blocks.SOUL_WALL_TORCH) {
                                    renderType = RENDER_TORCH;
                                    isTorch = true;
                                } else if (state.getBlock() == Blocks.REDSTONE_TORCH || state.getBlock() == Blocks.REDSTONE_WALL_TORCH) {
                                    renderType = RENDER_TORCH;
                                    isTorch = true;
                                } else if (state.getBlock() == Blocks.LANTERN || state.getBlock() == Blocks.SOUL_LANTERN) {
                                    renderType = RENDER_LANTERN;
                                    isLantern = true;
                                }

                                // Pack Position: x (0-15), z (0-15), y (absolute), renderType
                                // We store Y relative to minBuildHeight to save bits? 
                                // Standard world height: -64 to 320. Range 384. Fits in 9 bits (512).
                                int relY = worldY - minBuildHeight;
                                int packed = (x & 0xF) | ((z & 0xF) << 4) | ((relY & 0x1FF) << 8) | ((renderType & 0xF) << 17);
                                
                                positions.add(packed);
                                
                                // Color
                                int color = 0;
                                
                                // Fix Torch/Lantern Colors manually because map color is often 0
                                if (isTorch) {
                                    if (state.getBlock() == Blocks.SOUL_TORCH || state.getBlock() == Blocks.SOUL_WALL_TORCH) {
                                        color = 0x00FFFF; // Cyan
                                    } else if (state.getBlock() == Blocks.REDSTONE_TORCH || state.getBlock() == Blocks.REDSTONE_WALL_TORCH) {
                                        color = 0xFF0000; // Red
                                    } else {
                                        color = 0xFFD966; // Orange/Yellow default torch
                                    }
                                } else if (isLantern) {
                                    if (state.getBlock() == Blocks.SOUL_LANTERN) {
                                        color = 0x00FFFF; // Cyan
                                    } else {
                                        color = 0xFFD966; // Orange/Yellow default
                                    }
                                } else {
                                    try {
                                        color = state.getMapColor(chunk.getLevel(), new BlockPos(pos.getMinBlockX() + x, worldY, pos.getMinBlockZ() + z)).col;
                                    } catch (Exception e) {
                                        color = 0; // Fallback
                                    }
                                }
                                
                                // If color is 0 (transparent/black), try to get from block default
                                if (color == 0) {
                                     // Fallback color based on block hash or hardcoded
                                     // color = state.getBlock().defaultMapColor().col; // Not available easily
                                     color = 0xFF00FF; // Magenta debug
                                }
                                
                                colors.add(color);
                                
                                // Light Emission (Block Light Brightness)
                                // We store the actual light level at this position (0-15) to simulate lighting in night mode
                                // Using LightLayer.BLOCK to get torch light etc.
                                int light = 0;
                                try {
                                    BlockPos blockPos = new BlockPos(pos.getMinBlockX() + x, worldY, pos.getMinBlockZ() + z);
                                    // If block is solid opaque, its internal light is 0. get light from above or adjacent.
                                    if (state.canOcclude()) {
                                        // Get light from above for ground blocks
                                        light = chunk.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, blockPos.above());
                                    } else {
                                        // For non-occluding blocks (like torches, glass), get light at the block itself
                                        light = chunk.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, blockPos);
                                        // If it's an emitter (like torch), ensure it's bright
                                        if (state.getLightEmission() > 0) {
                                            light = Math.max(light, state.getLightEmission());
                                        }
                                    }
                                } catch (Exception e) {
                                    light = state.getLightEmission(); // Fallback
                                }
                                lights.add((byte)light);
                            }
                        }
                    }
                }
            }
        }
        
        // Convert to arrays
        int[] posArray = positions.stream().mapToInt(i -> i).toArray();
        int[] colArray = colors.stream().mapToInt(i -> i).toArray();
        byte[] lightArray = new byte[lights.size()];
        for(int i=0; i<lights.size(); i++) lightArray[i] = lights.get(i);
        
        CHUNK_DATA.put(pos, new ScannedChunk(posArray, colArray, lightArray));
    }
    
    private static boolean isTransparent(LevelChunkSection section, int x, int y, int z) {
        if (x < 0 || x > 15 || y < 0 || y > 15 || z < 0 || z > 15) {
            // Boundary: Check adjacent sections? 
            // For speed, let's assume boundary is exposed (draws faces between chunks, good for debug/map)
            return true;
        }
        BlockState state = section.getBlockState(x, y, z);
        return !state.canOcclude();
    }
    
    public static Map<ChunkPos, ScannedChunk> getData() {
        return CHUNK_DATA;
    }
    
    public static void clear() {
        CHUNK_DATA.clear();
    }
    
    public static void prune(ChunkPos center, int radius) {
        CHUNK_DATA.entrySet().removeIf(entry -> {
            ChunkPos pos = entry.getKey();
            int dx = Math.abs(pos.x - center.x);
            int dz = Math.abs(pos.z - center.z);
            return dx > radius || dz > radius;
        });
    }
}
