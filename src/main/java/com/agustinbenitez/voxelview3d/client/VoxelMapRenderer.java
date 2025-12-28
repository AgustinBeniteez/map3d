package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
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
        RenderSystem.enableCull(); // Enable Cull to avoid seeing backfaces and reduce visual noise
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        // Enable Blending for transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
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
            
            // Underground if can't see sky OR if deep in a hole (surface is significantly higher)
            isUnderground = !canSeeSky;
            
            if (!isUnderground) {
                 int x = player.getBlockX();
                 int z = player.getBlockZ();
                 
                 // Check surrounding height (3x3 area around player)
                 // If average surrounding height is significantly higher than playerY, we are in a hole/trench
                 int h1 = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + 2, z);
                 int h2 = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x - 2, z);
                 int h3 = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z + 2);
                 int h4 = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z - 2);
                 
                 int avgSurrounding = (h1 + h2 + h3 + h4) / 4;
                 
                 // If player is > 3 blocks below the surrounding terrain, treat as underground (transparency)
                 if (playerY < avgSurrounding - 3) {
                     isUnderground = true;
                 }
            }
            
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

        // Render Waypoints
        renderWaypoints(poseStack, player, cameraYaw, cameraPitch);
        
        poseStack.popPose();
        
        // Reset state
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
    }

    private static void renderWaypoints(PoseStack poseStack, Player player, float cameraYaw, float cameraPitch) {
        if (ClientSettings.waypoints.isEmpty()) return;
        
        double centerX = player.getX();
        double centerZ = player.getZ();
        double centerY = player.getY();
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;
            
            double rx = wp.x + 0.5 - centerX;
            double rz = wp.z + 0.5 - centerZ;
            
            // Draw Beam (Infinite Vertical)
            // We draw a very tall box centered vertically around the player (y=0 relative)
            // This ensures it looks like it goes from bottom to top of the world
            double beamHeight = 2048.0; 
            double beamY = 0; // Relative to camera (which is 0)
            
            // Calculate distance for fading
            double distSq = rx*rx + rz*rz;
            float maxFadeDist = 3.0f; // blocks
            float alpha = 0.8f;
            
            if (distSq < maxFadeDist * maxFadeDist) {
                double dist = Math.sqrt(distSq);
                alpha = (float) (dist / maxFadeDist) * 0.8f;
                if (alpha < 0.1f) alpha = 0.05f; // almost invisible
            }
            
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            
            int color = wp.color;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            
            // Inner beam (thinner, more opaque)
            float wInner = 0.2f; 
            renderBox(buf, poseStack.last().pose(), rx, beamY, rz, wInner, (float)beamHeight, wInner, r, g, b, alpha);
            
            // Outer beam (wider, more transparent)
            float wOuter = 0.6f;
            renderBox(buf, poseStack.last().pose(), rx, beamY, rz, wOuter, (float)beamHeight, wOuter, r, g, b, alpha * 0.3f);
            
            BufferUploader.drawWithShader(buf.end());
            
            // Draw Name Tag
            // Position name tag slightly above the waypoint's actual Y coordinate, or above player if far?
            // Let's keep it at the waypoint's Y coordinate so you know its altitude
            double ry = wp.y - centerY; 
            
            if (alpha > 0.2f) { // Only show name if beam is somewhat visible
                poseStack.pushPose();
                poseStack.translate(rx, ry + 2.5, rz);
                // Billboard effect
                poseStack.mulPose(Axis.YP.rotationDegrees(-cameraYaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(-cameraPitch));
                
                float scale = 0.15f; // Slightly larger text
                poseStack.scale(scale, -scale, scale);
                
                // Disable depth test to ensure text/icon is always visible (on top of beam and blocks)
                RenderSystem.disableDepthTest();

                // --- Draw Icon ---
                ResourceLocation iconLoc = new ResourceLocation("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, iconLoc);
                
                // Draw Icon Quad
                float iconSize = 16.0f; // Size in local scaled units
                float iconY = -2.0f; // Slightly above text
                
                // We need a new buffer for the icon
                Tesselator tessIcon = Tesselator.getInstance();
                BufferBuilder bufIcon = tessIcon.getBuilder();
                bufIcon.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                Matrix4f matrix = poseStack.last().pose();
                
                bufIcon.vertex(matrix, -iconSize/2, iconY - iconSize, 0).uv(0, 0).endVertex();
                bufIcon.vertex(matrix, -iconSize/2, iconY, 0).uv(0, 1).endVertex();
                bufIcon.vertex(matrix, iconSize/2, iconY, 0).uv(1, 1).endVertex();
                bufIcon.vertex(matrix, iconSize/2, iconY - iconSize, 0).uv(1, 0).endVertex();
                
                BufferUploader.drawWithShader(bufIcon.end());
                // -----------------

                int textWidth = Minecraft.getInstance().font.width(wp.name);
                int halfWidth = textWidth / 2;
                
                // Draw background for text for better readability
                // Use fill (we need to access Tesselator or just use standard GuiGraphics fill? 
                // We are in 3D context, so drawInBatch handles it, but background requires a separate quad or font options.
                // drawInBatch has backgroundColor param (the last int).
                // Use SEE_THROUGH to match depth disabled state
                
                Minecraft.getInstance().font.drawInBatch(wp.name, -halfWidth, 0, 0xFFFFFFFF, false, poseStack.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(), Font.DisplayMode.SEE_THROUGH, 0x40000000, 15728880);
                
                // Re-enable depth test
                RenderSystem.enableDepthTest();
                
                poseStack.popPose();
            }
        }
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
            float alpha = 1.0f;

            if (isUnderground) {
                // Distance squared
                double distSq = rx * rx + ry * ry + rz * rz;
                double dist = Math.sqrt(distSq);

                // Brightness (Fog): Darken blocks far away
                // Fade start at 6 blocks, fully dark at 16 blocks?
                double maxDist = 16.0;
                if (distSq > 36.0) { // Start fading after 6 blocks
                    double fade = 1.0 - ((dist - 6.0) / (maxDist - 6.0));
                    brightness = (float) Math.max(0.3, Math.min(1.0, fade)); // Min brightness 0.3
                }

                // Transparency: Make nearby blocks transparent so we can see through walls
                // User request: "transparenté lo que este cerca no todo"
                double transStart = 2.0;
                double transEnd = 8.0;

                if (dist < transStart) {
                    alpha = 0.3f;
                } else if (dist > transEnd) {
                    alpha = 1.0f;
                } else {
                    // Interpolate from 0.3 to 1.0
                    double t = (dist - transStart) / (transEnd - transStart);
                    alpha = (float) (0.3 + t * 0.7);
                }
            }

            // Render block as a box (1.0 size for solid terrain)
            renderBox(buf, pose, rx, ry, rz, 1.0f, 1.0f, 1.0f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
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
                if (!ClientSettings.showEnemies) continue;
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 1.8f, 0.6f, 1.0f, 0.0f, 0.0f, 1.0f);
            } else if (e instanceof Villager) {
                if (!ClientSettings.showVillagers) continue;
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 1.8f, 0.6f, 0.6f, 0.4f, 0.3f, 1.0f);
            } else if (e instanceof Animal) {
                if (!ClientSettings.showAnimals) continue;
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 1.0f);
            }
        }
        BufferUploader.drawWithShader(buf.end());
        
        // Pass 2: Player Head
        // Iterate all players to find client player or others
        for (Entity e : entities) {
            if (e instanceof AbstractClientPlayer) {
                if (!ClientSettings.showPlayers) continue;
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

    public static void renderBox(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float w, float h, float d, float r, float g, float b, float a) {
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
