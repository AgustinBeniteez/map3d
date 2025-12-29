package com.agustinbenitez.voxelview3d.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.CaveVines;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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
    public static final int RENDER_FLOWER = 12;
    public static final int RENDER_TALL_FLOWER = 13;
    public static final int RENDER_MUSHROOM = 14;
    public static final int RENDER_GLOW_LICHEN = 15;
    public static final int RENDER_VINE = 16;
    public static final int RENDER_FIRE = 17;
    public static final int RENDER_REDSTONE_LAMP = 18;
    public static final int RENDER_DOOR = 19;
    public static final int RENDER_BUTTON = 20;
    public static final int RENDER_LEVER = 21;
    public static final int RENDER_REDSTONE_WIRE = 22;
    public static final int RENDER_IRON_BARS = 23;
    public static final int RENDER_FENCE = 24;
    public static final int RENDER_STAIRS = 25;
    public static final int RENDER_SLAB = 26;
    public static final int RENDER_TRAPDOOR = 27;
    public static final int RENDER_GLASS_PANE = 28;
    public static final int RENDER_GLASS_BLOCK = 29;
    
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
                                } else if (state.getBlock() instanceof FlowerBlock) {
                                    renderType = RENDER_FLOWER;
                                } else if (state.getBlock() instanceof TallFlowerBlock) {
                                    renderType = RENDER_TALL_FLOWER;
                                } else if (state.getBlock() == Blocks.BROWN_MUSHROOM || state.getBlock() == Blocks.RED_MUSHROOM) {
                                    renderType = RENDER_MUSHROOM;
                                } else if (state.getBlock() == Blocks.GRASS 
                                        || state.getBlock() == Blocks.TALL_GRASS 
                                        || state.getBlock() == Blocks.FERN 
                                        || state.getBlock() == Blocks.LARGE_FERN) {
                                    renderType = RENDER_GRASS;
                                } else if (state.getBlock() instanceof GlowLichenBlock) {
                                     renderType = RENDER_GLOW_LICHEN;
                                     // Override exposedFaces with actual attachment faces
                                     // GlowLichen uses PipeBlock properties (MultifaceBlock)
                                     exposedFaces = 0;
                                     if (state.getValue(PipeBlock.WEST)) exposedFaces |= 1;
                                     if (state.getValue(PipeBlock.EAST)) exposedFaces |= 2;
                                     if (state.getValue(PipeBlock.DOWN)) exposedFaces |= 4;
                                     if (state.getValue(PipeBlock.UP)) exposedFaces |= 8;
                                     if (state.getValue(PipeBlock.NORTH)) exposedFaces |= 16;
                                     if (state.getValue(PipeBlock.SOUTH)) exposedFaces |= 32;
                                 } else if (state.getBlock() instanceof VineBlock) {
                                    renderType = RENDER_VINE;
                                    exposedFaces = 0;
                                    if (state.getValue(VineBlock.WEST)) exposedFaces |= 1;
                                    if (state.getValue(VineBlock.EAST)) exposedFaces |= 2;
                                    if (state.getValue(VineBlock.UP)) exposedFaces |= 8;
                                    if (state.getValue(VineBlock.NORTH)) exposedFaces |= 16;
                                    if (state.getValue(VineBlock.SOUTH)) exposedFaces |= 32;
                                } else if (state.getBlock() instanceof BaseFireBlock) {
                                    renderType = RENDER_FIRE;
                                } else if (state.getBlock() == Blocks.REDSTONE_LAMP) {
                                    renderType = RENDER_REDSTONE_LAMP;
                                } else if (state.getBlock() instanceof DoorBlock) {
                                    renderType = RENDER_DOOR;
                                    // Pack Door Data into exposedFaces (6 bits available)
                                    // Bit 0-1: Facing (0:S, 1:W, 2:N, 3:E) - Horizontal ordinal?
                                    // Direction.ordinal: DOWN, UP, NORTH(2), SOUTH(3), WEST(4), EAST(5)
                                    // We can use 2D index: 0=South, 1=West, 2=North, 3=East
                                    exposedFaces = 0;
                                    
                                    int facing = 0;
                                    switch(state.getValue(DoorBlock.FACING)) {
                                        case SOUTH: facing = 0; break;
                                        case WEST: facing = 1; break;
                                        case NORTH: facing = 2; break;
                                        case EAST: facing = 3; break;
                                    }
                                    exposedFaces |= (facing & 3); // Bits 0-1
                                    
                                    if (state.getValue(DoorBlock.OPEN)) exposedFaces |= 4; // Bit 2
                                    if (state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT) exposedFaces |= 8; // Bit 3
                                    if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) exposedFaces |= 16; // Bit 4
                                } else if (state.getBlock() instanceof ButtonBlock) {
                                    renderType = RENDER_BUTTON;
                                    exposedFaces = 0;
                                    // Pack Face: Floor(0), Wall(1), Ceiling(2) -> 2 bits
                                    int face = 0;
                                    AttachFace af = state.getValue(ButtonBlock.FACE);
                                    if (af == AttachFace.WALL) face = 1;
                                    else if (af == AttachFace.CEILING) face = 2;
                                    exposedFaces |= (face & 3); // Bits 0-1
                                    
                                    // Pack Facing: S(0), W(1), N(2), E(3) -> 2 bits
                                    int facing = 0;
                                    switch(state.getValue(ButtonBlock.FACING)) {
                                        case SOUTH: facing = 0; break;
                                        case WEST: facing = 1; break;
                                        case NORTH: facing = 2; break;
                                        case EAST: facing = 3; break;
                                    }
                                    exposedFaces |= ((facing & 3) << 2); // Bits 2-3
                                    
                                    if (state.getValue(ButtonBlock.POWERED)) exposedFaces |= 16; // Bit 4
                                    
                                } else if (state.getBlock() instanceof LeverBlock) {
                                    renderType = RENDER_LEVER;
                                    exposedFaces = 0;
                                    // Same as Button
                                    int face = 0;
                                    AttachFace af = state.getValue(LeverBlock.FACE);
                                    if (af == AttachFace.WALL) face = 1;
                                    else if (af == AttachFace.CEILING) face = 2;
                                    exposedFaces |= (face & 3); // Bits 0-1
                                    
                                    int facing = 0;
                                    switch(state.getValue(LeverBlock.FACING)) {
                                        case SOUTH: facing = 0; break;
                                        case WEST: facing = 1; break;
                                        case NORTH: facing = 2; break;
                                        case EAST: facing = 3; break;
                                    }
                                    exposedFaces |= ((facing & 3) << 2); // Bits 2-3
                                    
                                    if (state.getValue(LeverBlock.POWERED)) exposedFaces |= 16; // Bit 4
                                    
                                } else if (state.getBlock() instanceof RedStoneWireBlock) {
                                    renderType = RENDER_REDSTONE_WIRE;
                                    exposedFaces = 0;
                                    // Pack Connections
                                    // Bit 0: North
                                    // Bit 1: South
                                    // Bit 2: East
                                    // Bit 3: West
                                    
                                    if (state.getValue(RedStoneWireBlock.NORTH) != RedstoneSide.NONE) exposedFaces |= 1;
                                    if (state.getValue(RedStoneWireBlock.SOUTH) != RedstoneSide.NONE) exposedFaces |= 2;
                                    if (state.getValue(RedStoneWireBlock.EAST) != RedstoneSide.NONE) exposedFaces |= 4;
                                    if (state.getValue(RedStoneWireBlock.WEST) != RedstoneSide.NONE) exposedFaces |= 8;
                                } else if (state.getBlock() instanceof IronBarsBlock) {
                                    renderType = RENDER_IRON_BARS;
                                    exposedFaces = 0;
                                    if (state.getValue(IronBarsBlock.NORTH)) exposedFaces |= 1;
                                    if (state.getValue(IronBarsBlock.SOUTH)) exposedFaces |= 2;
                                    if (state.getValue(IronBarsBlock.EAST)) exposedFaces |= 4;
                                    if (state.getValue(IronBarsBlock.WEST)) exposedFaces |= 8;
                                } else if (state.getBlock() instanceof FenceBlock) {
                                    renderType = RENDER_FENCE;
                                    exposedFaces = 0;
                                    if (state.getValue(FenceBlock.NORTH)) exposedFaces |= 1;
                                    if (state.getValue(FenceBlock.SOUTH)) exposedFaces |= 2;
                                    if (state.getValue(FenceBlock.EAST)) exposedFaces |= 4;
                                    if (state.getValue(FenceBlock.WEST)) exposedFaces |= 8;
                                } else if (state.getBlock() instanceof StairBlock) {
                                    renderType = RENDER_STAIRS;
                                    exposedFaces = 0;
                                    
                                    // Pack Facing: S(0), W(1), N(2), E(3)
                                    int facing = 0;
                                    switch(state.getValue(StairBlock.FACING)) {
                                        case SOUTH: facing = 0; break;
                                        case WEST: facing = 1; break;
                                        case NORTH: facing = 2; break;
                                        case EAST: facing = 3; break;
                                    }
                                    exposedFaces |= (facing & 3); // Bits 0-1
                                    
                                    // Pack Half: Bottom(0), Top(1)
                                    if (state.getValue(StairBlock.HALF) == Half.TOP) exposedFaces |= 4; // Bit 2
                                    
                                    // Pack Shape: Straight(0), InnerL(1), InnerR(2), OuterL(3), OuterR(4)
                                    int shape = 0;
                                    switch(state.getValue(StairBlock.SHAPE)) {
                                        case STRAIGHT: shape = 0; break;
                                        case INNER_LEFT: shape = 1; break;
                                        case INNER_RIGHT: shape = 2; break;
                                        case OUTER_LEFT: shape = 3; break;
                                        case OUTER_RIGHT: shape = 4; break;
                                    }
                                    exposedFaces |= ((shape & 7) << 3); // Bits 3-5
                                    
                                } else if (state.getBlock() instanceof SlabBlock) {
                                    renderType = RENDER_SLAB;
                                    exposedFaces = 0;
                                    
                                    // Pack Type: Bottom(0), Top(1), Double(2)
                                    int type = 0;
                                    SlabType st = state.getValue(SlabBlock.TYPE);
                                    if (st == SlabType.TOP) type = 1;
                                    else if (st == SlabType.DOUBLE) type = 2;
                                    
                                    exposedFaces |= (type & 3); // Bits 0-1
                                } else if (state.getBlock() instanceof TrapDoorBlock) {
                                    renderType = RENDER_TRAPDOOR;
                                    exposedFaces = 0;
                                    
                                    // Pack Facing: S(0), W(1), N(2), E(3)
                                    int facing = 0;
                                    switch(state.getValue(TrapDoorBlock.FACING)) {
                                        case SOUTH: facing = 0; break;
                                        case WEST: facing = 1; break;
                                        case NORTH: facing = 2; break;
                                        case EAST: facing = 3; break;
                                    }
                                    exposedFaces |= (facing & 3); // Bits 0-1
                                    
                                    // Pack Half: Bottom(0), Top(1)
                                    if (state.getValue(TrapDoorBlock.HALF) == Half.TOP) exposedFaces |= 4; // Bit 2
                                    
                                    // Pack Open: False(0), True(1)
                                    if (state.getValue(TrapDoorBlock.OPEN)) exposedFaces |= 8; // Bit 3
                                } else if (state.getBlock().getDescriptionId().contains("glass_pane")) {
                                    renderType = RENDER_GLASS_PANE;
                                    exposedFaces = 0;
                                    if (state.getValue(BlockStateProperties.NORTH)) exposedFaces |= 1;
                                    if (state.getValue(BlockStateProperties.SOUTH)) exposedFaces |= 2;
                                    if (state.getValue(BlockStateProperties.EAST)) exposedFaces |= 4;
                                    if (state.getValue(BlockStateProperties.WEST)) exposedFaces |= 8;
                                } else if (state.getBlock() instanceof AbstractGlassBlock) {
                                    renderType = RENDER_GLASS_BLOCK;
                                    // exposedFaces is already calculated based on transparency
                                }


                                // Check for Lava
                                boolean isLava = (state.getBlock() == Blocks.LAVA);

                                // Pack Position: x (0-15), z (0-15), y (absolute), renderType, exposedFaces
                                // We store Y relative to minBuildHeight to save bits? 
                                // Standard world height: -64 to 320. Range 384. Fits in 9 bits (512).
                                int relY = worldY - minBuildHeight;
                                // Expand renderType to 5 bits (0-31) and shift exposedFaces to 22
                                int packed = (x & 0xF) | ((z & 0xF) << 4) | ((relY & 0x1FF) << 8) | ((renderType & 0x1F) << 17) | ((exposedFaces & 0x3F) << 22);
                                
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
                                } else if (renderType == RENDER_FLOWER || renderType == RENDER_TALL_FLOWER || renderType == RENDER_MUSHROOM) {
                                    color = getPottedPlantColor(state.getBlock());
                                } else if (renderType == RENDER_FIRE) {
                                    if (state.getBlock() == Blocks.SOUL_FIRE) {
                                        color = 0x33FFFF; // Cyan/Blue for Soul Fire
                                    } else {
                                        color = 0xFF6600; // Orange for regular Fire
                                    }
                                } else if (renderType == RENDER_REDSTONE_LAMP) {
                                    boolean lit = state.getValue(RedstoneLampBlock.LIT);
                                    if (lit) {
                                        color = 0xFFFF99; // Light Yellow (Amarillo Claro)
                                    } else {
                                        color = 0x4A2B2B; // Dark Brown/Red
                                    }
                                } else if (state.getBlock() == Blocks.SHROOMLIGHT) {
                                    color = 0xFF9933; // Bright Orange (More orange than default)
                                } else if (renderType == RENDER_DOOR) {
                                     // Use block map color
                                     try {
                                        color = state.getMapColor(chunk.getLevel(), new BlockPos(pos.getMinBlockX() + x, worldY, pos.getMinBlockZ() + z)).col;
                                     } catch (Exception e) { color = 0; }
                                     
                                     // Iron door override if needed (Map color is Iron, which is fine)
                                     // Spruce door is Spruce color, etc.
                                } else if (renderType == RENDER_REDSTONE_WIRE) {
                                    if (state.getValue(RedStoneWireBlock.POWER) > 0) {
                                        color = 0xFF0000; // Bright Red
                                    } else {
                                        color = 0x550000; // Dark Red
                                    }
                                } else if (renderType == RENDER_BUTTON) {
                                    // Try map color first
                                    try {
                                        color = state.getMapColor(chunk.getLevel(), new BlockPos(pos.getMinBlockX() + x, worldY, pos.getMinBlockZ() + z)).col;
                                    } catch (Exception e) { color = 0; }
                                    
                                    if (color == 0) {
                                        if (state.getBlock().getDescriptionId().contains("stone") || state.getBlock().getDescriptionId().contains("blackstone")) {
                                            color = 0x808080; // Grey
                                        } else {
                                            color = 0x8F7748; // Wood Brown
                                        }
                                    }
                                } else if (renderType == RENDER_LEVER) {
                                    color = 0x808080; // Grey Base
                                } else if (renderType == RENDER_IRON_BARS) {
                                    color = 0xA0A0A0; // Iron Grey
                                } else if (renderType == RENDER_FENCE) {
                                    try {
                                        color = state.getMapColor(chunk.getLevel(), new BlockPos(pos.getMinBlockX() + x, worldY, pos.getMinBlockZ() + z)).col;
                                    } catch (Exception e) { color = 0; }
                                    if (color == 0) color = 0x8F7748; // Default Wood
                                } else if (renderType == RENDER_TRAPDOOR) {
                                    try {
                                        color = state.getMapColor(chunk.getLevel(), new BlockPos(pos.getMinBlockX() + x, worldY, pos.getMinBlockZ() + z)).col;
                                    } catch (Exception e) { color = 0; }
                                    if (color == 0) color = 0x8F7748; // Default Wood
                                } else if (renderType == RENDER_GLASS_PANE) {
                                    try {
                                        color = state.getMapColor(chunk.getLevel(), new BlockPos(pos.getMinBlockX() + x, worldY, pos.getMinBlockZ() + z)).col;
                                    } catch (Exception e) { color = 0; }
                                    if (color == 0) color = 0x88CCFF; // Light Blue/Cyan for clear glass
                                } else if (renderType == RENDER_GLASS_BLOCK) {
                                    if (state.getBlock() == Blocks.TINTED_GLASS) {
                                        color = 0x2A2A2A; // Dark Grey/Black
                                    } else {
                                        try {
                                            color = state.getMapColor(chunk.getLevel(), new BlockPos(pos.getMinBlockX() + x, worldY, pos.getMinBlockZ() + z)).col;
                                        } catch (Exception e) { color = 0; }
                                        if (color == 0) color = 0x88CCFF; // Light Blue/Cyan for clear glass
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
                                        // Also for Lava
                                        if (state.getLightEmission() > 0) {
                                            light = Math.max(light, state.getLightEmission());
                                        }
                                        // Force full brightness for Lit Redstone Lamp to ensure glow
                                        if (renderType == RENDER_REDSTONE_LAMP && state.getValue(RedstoneLampBlock.LIT)) {
                                            light = 15;
                                        }
                                        // Force brightness for Shroomlight
                                        if (state.getBlock() == Blocks.SHROOMLIGHT) {
                                            light = 15;
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
        if (block == Blocks.TORCHFLOWER) return 0xFFA500; // Orange

        if (block == Blocks.BROWN_MUSHROOM) return 0x967241; // Brown Mushroom
        if (block == Blocks.RED_MUSHROOM) return 0xFF0000; // Red Mushroom

        // Tall Flowers
        if (block == Blocks.SUNFLOWER) return 0xFFFF00; // Yellow
        if (block == Blocks.LILAC) return 0xC8A2C8; // Lilac
        if (block == Blocks.ROSE_BUSH) return 0xFF0000; // Red
        if (block == Blocks.PEONY) return 0xFFC0CB; // Pink
        if (block == Blocks.PITCHER_PLANT) return 0x9370DB; // Medium Purple

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
