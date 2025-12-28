package com.example.voxelview3d.client;

import com.example.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;

import java.util.Map;

public class VoxelMapRenderer {

    public static void renderMap(PoseStack poseStack, float zoom, float cameraPitch, float cameraYaw, boolean isHud, int renderRadius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        Player player = mc.player;
        
        // Setup 3D Rendering State
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        poseStack.pushPose();
        
        // Scale and Rotate
        poseStack.scale(zoom, -zoom, zoom);
        poseStack.mulPose(Axis.XP.rotationDegrees(cameraPitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(cameraYaw));
        
        // Draw Axis Marker (Center)
        drawAxis(poseStack);
        
        // Draw Debug Cube
        drawDebugCube(poseStack);
        
        ClientMapData clientData = ClientMapData.getInstance();
        int cutY = clientData.getCutY();
        
        int minBuildHeight = mc.level != null ? mc.level.getMinBuildHeight() : -64;
        
        // Calculate Vertical Culling Bounds
        int renderMinY = minBuildHeight;
        int renderMaxY = cutY;
        
        boolean isUnderground = false;
        
        if (mc.level != null) {
            int playerY = player.getBlockY();
            boolean canSeeSky = mc.level.canSeeSky(player.blockPosition());
            
            isUnderground = !canSeeSky;
            
            if (isUnderground) {
                // Cave Mode: Strict vertical limits to see player inside
                // Show floor slightly below
                renderMinY = Math.max(minBuildHeight, playerY - 16); 
                // Cut ceiling closer to head (playerY + 1 instead of +4) as requested
                renderMaxY = Math.min(cutY, playerY + 1);
            } else {
                // Surface Mode: Show deeper context
                // Ensure we see down to sea level (60) when flying high, but keep culling when low
                renderMinY = Math.max(minBuildHeight, Math.min(playerY - 32, 60));
                // renderMaxY remains cutY (or sky)
            }
        }
        
        // Render Chunks
        renderChunks(poseStack, player, renderRadius, minBuildHeight, renderMinY, renderMaxY, isUnderground);
        
        // Render Entities
        renderEntities(poseStack, player, renderMinY, renderMaxY);
        
        poseStack.popPose();
        
        // Reset state
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
    }

    public static void renderMinimap2D(PoseStack poseStack, float zoom, float cameraYaw, int renderRadius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        Player player = mc.player;
        
        // Setup 2D Rendering State
        // Use Depth Test to allow higher blocks to cover lower blocks
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        poseStack.pushPose();
        
        // Scale
        poseStack.scale(zoom, zoom, 1.0f);
        // Rotate (Map rotation)
        poseStack.mulPose(Axis.ZP.rotationDegrees(cameraYaw));
        
        ClientMapData clientData = ClientMapData.getInstance();
        int cutY = clientData.getCutY();
        int minBuildHeight = mc.level != null ? mc.level.getMinBuildHeight() : -64;
        
        // Calculate Vertical Culling Bounds (Same logic as 3D)
        int renderMinY = minBuildHeight;
        int renderMaxY = cutY;
        boolean isUnderground = false;
        
        if (mc.level != null) {
            int playerY = player.getBlockY();
            boolean canSeeSky = mc.level.canSeeSky(player.blockPosition());
            
            isUnderground = !canSeeSky;
            
            if (isUnderground) {
                renderMinY = Math.max(minBuildHeight, playerY - 16); 
                renderMaxY = Math.min(cutY, playerY + 1);
            } else {
                renderMinY = Math.max(minBuildHeight, Math.min(playerY - 32, 60));
            }
        }
        
        // Render Chunks 2D
        renderChunks2D(poseStack, player, renderRadius, minBuildHeight, renderMinY, renderMaxY, isUnderground);
        
        // Render Player Marker (2D Arrow)
        renderPlayerMarker2D(poseStack);
        
        poseStack.popPose();
        
        RenderSystem.disableDepthTest();
    }
    
    private static void renderChunks2D(PoseStack poseStack, Player player, int radius, int minBuildHeight, int renderMinY, int renderMaxY, boolean isUnderground) {
        double centerX = player.getX();
        double centerZ = player.getZ();
        double centerY = player.getY();
        
        ChunkPos playerChunk = player.chunkPosition();
        Map<ChunkPos, ChunkScanner.ScannedChunk> data = ChunkScanner.getData();
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Matrix4f pose = poseStack.last().pose();
        
        // Optimized loop: only iterate nearby chunks
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                 ChunkPos cp = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                 ChunkScanner.ScannedChunk chunkData = data.get(cp);
                 if (chunkData != null) {
                     buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                     renderChunkBlocks2D(buf, pose, cp, chunkData, centerX, centerZ, centerY, minBuildHeight, renderMinY, renderMaxY, isUnderground);
                     BufferUploader.drawWithShader(buf.end());
                 }
            }
        }
    }
    
    private static void renderChunkBlocks2D(BufferBuilder buf, Matrix4f pose, ChunkPos cp, ChunkScanner.ScannedChunk chunkData, double centerX, double centerZ, double centerY, int minBuildHeight, int minY, int maxY, boolean isUnderground) {
        int[] packedPositions = chunkData.packedPositions;
        int[] colors = chunkData.colors;
        
        if (packedPositions == null) return;
        
        for (int i = 0; i < packedPositions.length; i++) {
            int packed = packedPositions[i];
            int color = colors[i];
            
            int x = packed & 0xF;
            int z = (packed >> 4) & 0xF;
            int relY = (packed >> 8) & 0x1FF;
            
            int h = relY + minBuildHeight;
            
            if (h < minY || h > maxY) continue;
            
            // 2D Projection: Map World (X, Z) to Screen (X, Y)
            double rx = (cp.x * 16 + x) + 0.5 - centerX;
            double rz = (cp.z * 16 + z) + 0.5 - centerZ;
            
            // Use relative height for Z-layering (so higher blocks cover lower ones)
            // Scale height difference to small Z range to fit in depth buffer
            double heightDiff = h - centerY;
            double zLayer = heightDiff * 0.01; // Scale down
            
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            
            // Dimming Logic
            float brightness = 1.0f;
            if (isUnderground) {
                double distSq = rx * rx + rz * rz; // 2D distance
                double maxDist = 16.0;
                if (distSq > 36.0) { 
                    double dist = Math.sqrt(distSq);
                    double fade = 1.0 - ((dist - 6.0) / (maxDist - 6.0));
                    brightness = (float) Math.max(0.3, Math.min(1.0, fade));
                }
            }
            
            // Draw 2D Quad (1x1 unit)
            // X -> rx, Y -> rz
            renderQuad2D(buf, pose, rx, rz, zLayer, 1.0f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, 1.0f);
        }
    }

    private static void renderQuad2D(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float size, float r, float g, float b, float a) {
        float minX = (float)(x - size/2);
        float maxX = (float)(x + size/2);
        float minY = (float)(y - size/2);
        float maxY = (float)(y + size/2);
        float depth = (float)z;
        
        int red = (int)(r * 255);
        int green = (int)(g * 255);
        int blue = (int)(b * 255);
        int alpha = (int)(a * 255);
        
        // Flat Quad
        buf.vertex(pose, minX, maxY, depth).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, depth).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, depth).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, depth).color(red, green, blue, alpha).endVertex();
    }
    
    private static void renderPlayerMarker2D(PoseStack poseStack) {
        // Draw a simple red arrow or triangle in center
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f pose = poseStack.last().pose();
        
        // Z-layer above map
        float z = 1.0f;
        
        // Triangle pointing UP (since map rotates, player is always UP relative to map if we rotate map)
        // BUT wait:
        // If we rotate map by -playerYaw, then the world rotates, and player stays fixed UP.
        // Yes, that's how minimaps usually work.
        
        // Size 2.0
        buf.vertex(pose, 0, -2, z).color(255, 0, 0, 255).endVertex(); // Top (Forward in screen Y is -Y? No, standard GUI Y is Down. So -Y is Up.)
        buf.vertex(pose, -1.5f, 2, z).color(255, 0, 0, 255).endVertex(); // Bottom Left
        buf.vertex(pose, 1.5f, 2, z).color(255, 0, 0, 255).endVertex(); // Bottom Right
        
        BufferUploader.drawWithShader(buf.end());
    }
    
    private static void drawAxis(PoseStack poseStack) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f pose = poseStack.last().pose();
        
        // X Axis (Red)
        buf.vertex(pose, 0, 0, 0).color(255, 0, 0, 255).endVertex();
        buf.vertex(pose, 5, 0, 0).color(255, 0, 0, 255).endVertex();
        // Y Axis (Green)
        buf.vertex(pose, 0, 0, 0).color(0, 255, 0, 255).endVertex();
        buf.vertex(pose, 0, 5, 0).color(0, 255, 0, 255).endVertex();
        // Z Axis (Blue)
        buf.vertex(pose, 0, 0, 0).color(0, 0, 255, 255).endVertex();
        buf.vertex(pose, 0, 0, 5).color(0, 0, 255, 255).endVertex();
        
        BufferUploader.drawWithShader(buf.end());
    }
    
    private static void drawDebugCube(PoseStack poseStack) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderBox(buf, poseStack.last().pose(), 0, 0, 0, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f);
        BufferUploader.drawWithShader(buf.end());
    }
    
    private static void renderChunks(PoseStack poseStack, Player player, int radius, int minBuildHeight, int renderMinY, int renderMaxY, boolean isUnderground) {
        double centerX = player.getX();
        double centerZ = player.getZ();
        double centerY = player.getY();
        
        ChunkPos playerChunk = player.chunkPosition();
        Map<ChunkPos, ChunkScanner.ScannedChunk> data = ChunkScanner.getData();
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Matrix4f pose = poseStack.last().pose();
        
        if (radius > 0) {
            // Optimized loop: only iterate nearby chunks (coordinate loop)
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                     ChunkPos cp = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                     ChunkScanner.ScannedChunk chunkData = data.get(cp);
                     if (chunkData != null) {
                         // Flush per chunk to avoid massive buffers (OutOfMemory)
                         buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                         renderChunkBlocks(buf, pose, cp, chunkData, centerX, centerZ, centerY, minBuildHeight, renderMinY, renderMaxY, isUnderground);
                         BufferUploader.drawWithShader(buf.end());
                     }
                }
            }
        } else {
            // Render all scanned chunks (for full map)
            for (Map.Entry<ChunkPos, ChunkScanner.ScannedChunk> entry : data.entrySet()) {
                buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                renderChunkBlocks(buf, pose, entry.getKey(), entry.getValue(), centerX, centerZ, centerY, minBuildHeight, renderMinY, renderMaxY, isUnderground);
                BufferUploader.drawWithShader(buf.end());
            }
        }
    }
    
    private static void renderChunkBlocks(BufferBuilder buf, Matrix4f pose, ChunkPos cp, ChunkScanner.ScannedChunk chunkData, double centerX, double centerZ, double centerY, int minBuildHeight, int minY, int maxY, boolean isUnderground) {
        int[] packedPositions = chunkData.packedPositions;
        int[] colors = chunkData.colors;
        
        if (packedPositions == null) return;
        
        for (int i = 0; i < packedPositions.length; i++) {
            int packed = packedPositions[i];
            int color = colors[i];
            
            int x = packed & 0xF;
            int z = (packed >> 4) & 0xF;
            int relY = (packed >> 8) & 0x1FF;
            
            int h = relY + minBuildHeight;
            
            if (h < minY || h > maxY) continue;
            
            // Calculate absolute position relative to player (camera)
            // Center the block (blocks are 0-1, so center is +0.5)
            double rx = (cp.x * 16 + x) + 0.5 - centerX;
            double ry = h - centerY;
            double rz = (cp.z * 16 + z) + 0.5 - centerZ;
            
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            
            // "Exterior Darker" Logic:
            // If underground, apply distance-based fog/dimming to simulate lighting
            float brightness = 1.0f;
            if (isUnderground) {
                // Distance squared
                double distSq = rx * rx + ry * ry + rz * rz;
                // Fade start at 6 blocks, fully dark at 16 blocks?
                double maxDist = 16.0;
                if (distSq > 36.0) { // Start fading after 6 blocks
                    double dist = Math.sqrt(distSq);
                    double fade = 1.0 - ((dist - 6.0) / (maxDist - 6.0));
                    brightness = (float) Math.max(0.3, Math.min(1.0, fade)); // Min brightness 0.3
                }
            }
            
            // Render block as a box (1.0 size for solid terrain)
            renderBox(buf, pose, rx, ry, rz, 1.0f, 1.0f, 1.0f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, 1.0f);
        }
    }
    
    private static void renderEntities(PoseStack poseStack, Player player, int minY, int maxY) {
        double centerX = player.getX();
        double centerZ = player.getZ();
        double centerY = player.getY();
        
        Minecraft mc = Minecraft.getInstance();
        Iterable<Entity> entities = mc.level.entitiesForRendering();
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        // Pass 1: Boxes
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        for (Entity e : entities) {
            if (e instanceof Player) continue;
            
            // Vertical Culling for Entities
            if (e.getY() < minY || e.getY() > maxY) continue;
            
            double rx = e.getX() - centerX;
            double ry = e.getY() - centerY;
            double rz = e.getZ() - centerZ;
            
            if (e instanceof Monster) {
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 1.8f, 0.6f, 1.0f, 0.0f, 0.0f, 1.0f);
            } else if (e instanceof Villager) {
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 1.8f, 0.6f, 0.6f, 0.4f, 0.3f, 1.0f);
            } else if (e instanceof Animal) {
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 1.0f);
            }
        }
        BufferUploader.drawWithShader(buf.end());
        
        // Pass 2: Player Head
        // Iterate all players to find client player or others
        for (Entity e : entities) {
            if (e instanceof AbstractClientPlayer) {
                AbstractClientPlayer p = (AbstractClientPlayer) e;
                if (p == player) { // Only render self for now as per request "tu personaje"
                     renderPlayerHead(poseStack, p, centerX, centerZ, centerY);
                }
            }
        }
    }
    
    private static void renderPlayerHead(PoseStack poseStack, AbstractClientPlayer player, double cx, double cz, double cy) {
        ResourceLocation skin = player.getSkinTextureLocation();
        RenderSystem.setShaderTexture(0, skin);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        
        double rx = player.getX() - cx;
        double ry = player.getY() - cy;
        double rz = player.getZ() - cz;
        
        renderTexturedHead(buf, poseStack.last().pose(), rx, ry + 1.4, rz, 0.5f);
        
        BufferUploader.drawWithShader(buf.end());
    }

    private static void renderBox(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float w, float h, float d, float r, float g, float b, float a) {
        float minX = (float)(x - w/2);
        float maxX = (float)(x + w/2);
        float minY = (float)y;
        float maxY = (float)(y + h);
        float minZ = (float)(z - d/2);
        float maxZ = (float)(z + d/2);
        
        int red = (int)(r * 255);
        int green = (int)(g * 255);
        int blue = (int)(b * 255);
        int alpha = (int)(a * 255);
        
        // Top
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Bottom
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Front
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Back
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        
        // Left
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        
        // Right
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
    }
    
    private static void renderTexturedHead(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float size) {
        float minX = (float)(x - size/2);
        float maxX = (float)(x + size/2);
        float minY = (float)y;
        float maxY = (float)(y + size);
        float minZ = (float)(z - size/2);
        float maxZ = (float)(z + size/2);
        
        float uScale = 1.0f / 64.0f;
        float vScale = 1.0f / 64.0f;
        
        // Simplified Cube Mapping for Head
        // Top
        float uMin = 8 * uScale; float uMax = 16 * uScale;
        float vMin = 0 * vScale; float vMax = 8 * vScale;
        buf.vertex(pose, minX, maxY, minZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).uv(uMax, vMin).endVertex();
        
        // Bottom
        uMin = 16 * uScale; uMax = 24 * uScale;
        vMin = 0 * vScale; vMax = 8 * vScale;
        buf.vertex(pose, maxX, minY, minZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, minX, minY, maxZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, minX, minY, minZ).uv(uMax, vMin).endVertex();
        
        // Front
        uMin = 8 * uScale; uMax = 16 * uScale;
        vMin = 8 * vScale; vMax = 16 * vScale;
        buf.vertex(pose, maxX, maxY, minZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, maxX, minY, minZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, minX, minY, minZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, minX, maxY, minZ).uv(uMax, vMin).endVertex();
        
        // Back
        uMin = 24 * uScale; uMax = 32 * uScale;
        vMin = 8 * vScale; vMax = 16 * vScale;
        buf.vertex(pose, minX, maxY, maxZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, minX, minY, maxZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).uv(uMax, vMin).endVertex();
        
        // Left
        uMin = 16 * uScale; uMax = 24 * uScale;
        vMin = 8 * vScale; vMax = 16 * vScale;
        buf.vertex(pose, minX, maxY, minZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, minX, minY, minZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, minX, minY, maxZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).uv(uMax, vMin).endVertex();
        
        // Right
        uMin = 0 * uScale; uMax = 8 * uScale;
        vMin = 8 * vScale; vMax = 16 * vScale;
        buf.vertex(pose, maxX, maxY, maxZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, maxX, minY, minZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).uv(uMax, vMin).endVertex();
    }
}
