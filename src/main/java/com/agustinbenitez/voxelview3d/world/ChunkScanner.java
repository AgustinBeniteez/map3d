package com.agustinbenitez.voxelview3d.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.block.CaveVines;

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
    public static final int RENDER_CAVE_VINE = 3;
    public static final int RENDER_CAVE_VINE_WITH_BERRIES = 4;
    public static final int RENDER_SUGAR_CANE = 5;
    public static final int RENDER_CACTUS = 6;
    public static final int RENDER_SAPLING = 7;
    public static final int RENDER_BAMBOO = 8;
    public static final int RENDER_POTTED_PLANT = 9;
    public static final int RENDER_FLOWER_POT = 10;
    public static final int RENDER_GRASS = 11;
    
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
                            // Calculate exposed faces bitmask
                            // Bit 0: West (-x)
                            // Bit 1: East (+x)
                            // Bit 2: Down (-y)
                            // Bit 3: Up (+y)
                            // Bit 4: North (-z)
                            // Bit 5: South (+z)
                            
                            int exposedFaces = 0;
                            if (isTransparent(chunk, x - 1, worldY, z, state)) exposedFaces |= 1;
                            if (isTransparent(chunk, x + 1, worldY, z, state)) exposedFaces |= 2;
                            if (isTransparent(chunk, x, worldY - 1, z, state)) exposedFaces |= 4;
                            if (isTransparent(chunk, x, worldY + 1, z, state)) exposedFaces |= 8;
                            if (isTransparent(chunk, x, worldY, z - 1, state)) exposedFaces |= 16;
                            if (isTransparent(chunk, x, worldY, z + 1, state)) exposedFaces |= 32;

                            // Also check if it's the top surface block (Heightmap check)
                            // This ensures top soil is always drawn even if surrounded by other blocks locally
                            int surfaceHeight = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                            if (worldY == surfaceHeight - 1) exposedFaces |= 8; // Force UP face exposed

                            // If any face is exposed, or if we force it (for debugging/safety)
                            if (exposedFaces != 0) {
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
                                } else if (state.getBlock() == Blocks.CAVE_VINES || state.getBlock() == Blocks.CAVE_VINES_PLANT) {
                                    boolean hasBerries = false;
                                    try {
                                        hasBerries = state.getValue(CaveVines.BERRIES);
                                    } catch (Exception ignored) {}
                                    
                                    if (hasBerries) {
                                        renderType = RENDER_CAVE_VINE_WITH_BERRIES;
                                    } else {
                                        renderType = RENDER_CAVE_VINE;
                                    }
                                } else if (state.getBlock() == Blocks.SUGAR_CANE) {
                                    renderType = RENDER_SUGAR_CANE;
                                } else if (state.getBlock() == Blocks.CACTUS) {
                                    renderType = RENDER_CACTUS;
                                } else if (state.getBlock() == Blocks.OAK_SAPLING 
                                        || state.getBlock() == Blocks.SPRUCE_SAPLING 
                                        || state.getBlock() == Blocks.BIRCH_SAPLING 
                                        || state.getBlock() == Blocks.JUNGLE_SAPLING 
                                        || state.getBlock() == Blocks.ACACIA_SAPLING 
                                        || state.getBlock() == Blocks.DARK_OAK_SAPLING
                                        || state.getBlock() == Blocks.MANGROVE_PROPAGULE
                                        || state.getBlock() == Blocks.CHERRY_SAPLING
                                        || state.getBlock() == Blocks.BAMBOO_SAPLING
                                        || state.getBlock() == Blocks.DEAD_BUSH) {
                                    renderType = RENDER_SAPLING;
                                } else if (state.getBlock() == Blocks.BAMBOO) {
                                    renderType = RENDER_BAMBOO;
                                } else if (state.getBlock() instanceof FlowerPotBlock) {
                                    FlowerPotBlock pot = (FlowerPotBlock) state.getBlock();
                                    if (pot.getContent() == Blocks.AIR) {
                                        renderType = RENDER_FLOWER_POT;
                                    } else {
                                        renderType = RENDER_POTTED_PLANT;
                                    }
                                } else if (state.getBlock() == Blocks.GRASS 
                                        || state.getBlock() == Blocks.TALL_GRASS 
                                        || state.getBlock() == Blocks.FERN 
                                        || state.getBlock() == Blocks.LARGE_FERN) {
                                    renderType = RENDER_GRASS;
                                }

                                // Check for Lava
                                boolean isLava = (state.getBlock() == Blocks.LAVA);

                                // Pack Position: x (0-15), z (0-15), y (absolute), renderType, exposedFaces
                                // We store Y relative to minBuildHeight to save bits? 
                                // Standard world height: -64 to 320. Range 384. Fits in 9 bits (512).
                                int relY = worldY - minBuildHeight;
                                int packed = (x & 0xF) | ((z & 0xF) << 4) | ((relY & 0x1FF) << 8) | ((renderType & 0xF) << 17) | ((exposedFaces & 0x3F) << 21);
                                
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
                                } else if (isLava) {
                                    // Hardcoded Bright Orange for Lava
                                    // Use a vibrant orange: 0xFF8000 (RGB) -> 255, 128, 0
                                    // Map colors are usually packed int.
                                    color = 0xFF6600; // Strong Orange
                                } else if (renderType == RENDER_POTTED_PLANT) {
                                    // Get color from the potted content
                                    FlowerPotBlock pot = (FlowerPotBlock) state.getBlock();
                                    color = getPottedPlantColor(pot.getContent());
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
                                        // Also for Lava
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
    
    private static boolean isTransparent(LevelChunk chunk, int x, int y, int z, BlockState selfState) {
        BlockState neighborState;
        
        // Handle Chunk Boundaries
        if (x < 0 || x > 15 || z < 0 || z > 15) {
             ChunkPos pos = chunk.getPos();
             int worldX = pos.getMinBlockX() + x;
             int worldZ = pos.getMinBlockZ() + z;
             
             try {
                 neighborState = chunk.getLevel().getBlockState(new BlockPos(worldX, y, worldZ));
             } catch (Exception e) {
                 return true; 
             }
        } else if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
             return true; 
        } else {
             // Local Section Lookup
             int secIdx = chunk.getSectionIndex(y);
             LevelChunkSection[] sections = chunk.getSections();
             if (secIdx < 0 || secIdx >= sections.length) return true;
             LevelChunkSection s = sections[secIdx];
             if (s == null || s.hasOnlyAir()) {
                 neighborState = Blocks.AIR.defaultBlockState();
             } else {
                 neighborState = s.getBlockState(x, y & 15, z);
             }
        }
        
        // Fluid Logic: If both are fluids, treat as occluded (no border/face)
        if (!selfState.getFluidState().isEmpty()) {
             if (!neighborState.getFluidState().isEmpty()) {
                 return false;
             }
        }
        
        return !neighborState.canOcclude();
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

    private static int getPottedPlantColor(net.minecraft.world.level.block.Block block) {
        if (block == Blocks.POPPY) return 0xFF0000; // Red
        if (block == Blocks.DANDELION) return 0xFFFF00; // Yellow
        if (block == Blocks.BLUE_ORCHID) return 0x00BFFF; // Deep Sky Blue
        if (block == Blocks.ALLIUM) return 0xFF00FF; // Magenta
        if (block == Blocks.AZURE_BLUET) return 0xE0E0E0; // Light Gray/White
        if (block == Blocks.RED_TULIP) return 0xFF0000;
        if (block == Blocks.ORANGE_TULIP) return 0xFFA500;
        if (block == Blocks.WHITE_TULIP) return 0xFFFFFF;
        if (block == Blocks.PINK_TULIP) return 0xFFC0CB;
        if (block == Blocks.OXEYE_DAISY) return 0xFFFFFF;
        if (block == Blocks.CORNFLOWER) return 0x6495ED; // Cornflower Blue
        if (block == Blocks.LILY_OF_THE_VALLEY) return 0xFFFFFF;
        if (block == Blocks.WITHER_ROSE) return 0x303030; // Dark Grey/Black
        if (block == Blocks.CRIMSON_FUNGUS) return 0x8B0000; // Dark Red
        if (block == Blocks.WARPED_FUNGUS) return 0x008B8B; // Cyan/Teal
        if (block == Blocks.FERN) return 0x008000; // Green
        if (block == Blocks.CACTUS) return 0x008000;
        if (block == Blocks.BAMBOO) return 0x008000;
        if (block == Blocks.DEAD_BUSH) return 0x6B4F28; // Dead Bush Brown

        // Saplings (default brown/wood)
        if (block == Blocks.OAK_SAPLING 
            || block == Blocks.SPRUCE_SAPLING
            || block == Blocks.BIRCH_SAPLING
            || block == Blocks.JUNGLE_SAPLING
            || block == Blocks.ACACIA_SAPLING
            || block == Blocks.DARK_OAK_SAPLING
            || block == Blocks.MANGROVE_PROPAGULE
            || block == Blocks.CHERRY_SAPLING) {
            return 0x785028; // Wood Brown
        }
        
        return 0x785028; // Default Brown
    }
}
