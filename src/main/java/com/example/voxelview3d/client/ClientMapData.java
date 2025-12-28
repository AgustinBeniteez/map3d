package com.example.voxelview3d.client;

import com.example.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientMapData {
    private static final ClientMapData INSTANCE = new ClientMapData();

    public static ClientMapData getInstance() {
        return INSTANCE;
    }

    private final Map<ChunkPos, ChunkMesh> meshCache = new HashMap<>();
    private final Set<Long> visibleBlocks = new HashSet<>();
    private int cutY = 320;

    public Map<ChunkPos, ChunkMesh> getMeshCache() {
        return meshCache;
    }

    public void clearCache() {
        meshCache.values().forEach(ChunkMesh::close);
        meshCache.clear();
    }
    
    public void setCutY(int cutY) {
        if (this.cutY != cutY) {
            this.cutY = cutY;
            clearCache();
        }
    }
    
    public int getCutY() {
        return cutY;
    }

    public void buildMeshIfNeeded(ChunkPos cp, ChunkScanner.ScannedChunk data) {
        // Deprecated: Rendering is now done immediately in VoxelMapRenderer
        // keeping method stub to avoid compile errors if called from old code, but empty body
    }
}
