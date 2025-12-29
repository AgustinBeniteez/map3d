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
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;

import java.util.HashMap;
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
        
        // Render Player Marker (Textured Cube)
        renderPlayerMarker(poseStack, player);
        
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
        renderEntities(poseStack, player, renderMinY, renderMaxY, renderRadius);

        // Render Chunk Grid
        renderChunkGrid(poseStack, player, renderRadius);

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
        
        // Track waypoint counts at each location to stack them
        Map<String, Integer> waypointCounts = new HashMap<>();
        
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;
            
            double rx = wp.x + 0.5 - centerX;
            double rz = wp.z + 0.5 - centerZ;
            
            // Calculate distance for fading
            double distSq = rx*rx + rz*rz;
            float maxFadeDist = 3.0f; // blocks
            float alpha = 0.8f;
            
            if (distSq < maxFadeDist * maxFadeDist) {
                double dist = Math.sqrt(distSq);
                alpha = (float) (dist / maxFadeDist) * 0.8f;
                if (alpha < 0.1f) alpha = 0.05f; // almost invisible
            }
            
            // Calculate stacking offset
            String locKey = wp.x + "_" + wp.y + "_" + wp.z;
            int stackIndex = waypointCounts.getOrDefault(locKey, 0);
            waypointCounts.put(locKey, stackIndex + 1);
            
            // Draw Name Tag & Icon
            // Position name tag slightly above the waypoint's actual Y coordinate
            // Stack overlapping waypoints vertically (4.0 blocks per index)
            double ry = wp.y - centerY; 
            double stackOffset = stackIndex * 4.0; 
            
            if (alpha > 0.2f) { // Only show name if somewhat visible (not right on top of player)
                poseStack.pushPose();
                poseStack.translate(rx, ry + 2.5 + stackOffset, rz);
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
    
    private static void renderPlayerMarker(PoseStack poseStack, Player player) {
        if (!(player instanceof AbstractClientPlayer)) return;
        
        AbstractClientPlayer abstractClientPlayer = (AbstractClientPlayer) player;
        ResourceLocation skin = abstractClientPlayer.getSkinTextureLocation();
        
        RenderSystem.setShaderTexture(0, skin);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        
        poseStack.pushPose();
        // Rotate to match player facing
        // Model faces North (-Z). Minecraft Yaw: S=0, W=90, N=180, E=270.
        // We need to rotate by (180 - playerYaw) to align model with player direction.
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - player.getYRot()));
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        float headSize = 2.0f; // Increased size (was 1.0f)
        float borderSize = 2.3f; // Slightly larger for border
        
        // 1. Render Textured Head
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        renderTexturedHead(buf, poseStack.last().pose(), 0, 0, 0, headSize);
        BufferUploader.drawWithShader(buf.end());
        
        // 2. Render White Border (Inverted Hull)
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderInvertedColorBox(buf, poseStack.last().pose(), 0, 0, 0, borderSize, 1.0f, 1.0f, 1.0f, 1.0f);
        BufferUploader.drawWithShader(buf.end());
        
        poseStack.popPose();
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
                         // Check Version?
                         // If version mismatch, we could request rescan, but scan is server/world thread based.
                         // For now, assume if data exists it is valid or will be replaced eventually.
                         
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
        int[] packedPositions = chunkData.positions;
        int[] colors = chunkData.colors;
        byte[] lights = chunkData.lights;
        
        if (packedPositions == null) return;
        
        for (int i = 0; i < packedPositions.length; i++) {
            int packed = packedPositions[i];
            int color = colors[i];
            
            int x = packed & 0xF;
            int z = (packed >> 4) & 0xF;
            int relY = (packed >> 8) & 0x1FF;
            int renderType = (packed >> 17) & 0xF;
            int exposedFaces = (packed >> 21) & 0x3F;
            
            // If exposedFaces is 0, it might be old data OR a block with no exposed faces (fully buried).
            // But fully buried blocks shouldn't be in the list?
            // Actually, buried blocks are culled during scan.
            // So if it's in the list, it MUST have some exposure.
            // If exposedFaces is 0, it means it's old data format (where bits were 0).
            // So we default to ALL exposed to be safe (and ugly grid) or just assume Top?
            // Let's assume old data has Top exposed at least.
            if (exposedFaces == 0) exposedFaces = 0x3F; // Default to all faces for old data
            
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

            if (isUnderground && !ClientSettings.fullBrightMap) {
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
            
            // Night Mode Lighting Logic (or Underground Mode)
            boolean useNightMode = ClientSettings.isNightMode || isUnderground;
            
            if (useNightMode && lights != null && i < lights.length) {
                int lightLevel = lights[i];
                
                // Add light level contribution (up to 1.0)
                float nightBase = 0.25f; 
                float lightContrib = (lightLevel / 15.0f);
                float nightFactor = nightBase + (lightContrib * (1.0f - nightBase));
                
                brightness *= nightFactor;
            }

            // Render based on Type
            if (renderType == 1) { // RENDER_TORCH
                // Small box, centered at bottom of block space
                // Visual size: 0.2 x 0.6 x 0.2
                renderBox(buf, pose, rx, ry - 0.2, rz, 0.2f, 0.6f, 0.2f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
            } else if (renderType == 2) { // RENDER_LANTERN
                // Lantern: Metal Top, Color Bottom
                
                // 1. Bottom Light Part (Main Body)
                // More rectangular and taller as requested.
                // Old: ry - 0.3, Height 0.4.
                // New: Start lower (ry - 0.4), Height 0.5. Width 0.35.
                renderBox(buf, pose, rx, ry - 0.4, rz, 0.35f, 0.5f, 0.35f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                
                // Metal Color: Dark Gray
                float metalR = 68 / 255.0f;
                float metalG = 68 / 255.0f;
                float metalB = 68 / 255.0f;

                // 2. Middle Metal Ring (Top Cap)
                // Pos: ry + 0.1 (Above body). Height 0.05. Slightly wider.
                renderBox(buf, pose, rx, ry + 0.1, rz, 0.4f, 0.05f, 0.4f, metalR * brightness, metalG * brightness, metalB * brightness, alpha);

                // 3. Top Metal Handle/Box (Extra Grey Part)
                // Pos: ry + 0.15 (Above ring). Height 0.1. Narrower.
                // This adds the "extra grey part on top" requested.
                renderBox(buf, pose, rx, ry + 0.15, rz, 0.2f, 0.1f, 0.2f, metalR * brightness, metalG * brightness, metalB * brightness, alpha);
                
            } else if (renderType == 3 || renderType == 4) { // RENDER_CAVE_VINE or RENDER_CAVE_VINE_WITH_BERRIES
                // Cave Vine Logic
                // 1. Brown Stem/Root (Inner Core)
                // Thin, full height to connect blocks.
                float stemR = 89 / 255.0f;
                float stemG = 61 / 255.0f;
                float stemB = 41 / 255.0f;
                renderBox(buf, pose, rx, ry, rz, 0.15f, 1.0f, 0.15f, stemR * brightness, stemG * brightness, stemB * brightness, alpha);

                // 2. Green Foliage (Outer Layer)
                // Slightly shorter than full block to reveal stem at joints, giving a "connected" look.
                // Size: 0.45 width, 0.8 height.
                float vineR = 92 / 255.0f;
                float vineG = 124 / 255.0f;
                float vineB = 53 / 255.0f;
                renderBox(buf, pose, rx, ry + 0.1, rz, 0.45f, 0.8f, 0.45f, vineR * brightness, vineG * brightness, vineB * brightness, alpha);
                
                // If it has berries (Type 4), render small orange cubes
                if (renderType == 4) {
                    // Berries: Small orange glowing boxes.
                    float berryR = 1.0f; // Bright Orange/Yellow
                    float berryG = 0.6f;
                    float berryB = 0.0f;
                    
                    // Boost brightness for glow
                    float berryBrightness = Math.max(brightness, 0.9f); 
                    float bSize = 0.15f;
                    
                    // Berry 1 (Lower, Right-ish)
                    // Offset randomly to look organic.
                    // X + 0.25, Y - 0.1, Z + 0.1
                    renderBox(buf, pose, rx + 0.25, ry - 0.1, rz + 0.1, bSize, bSize, bSize, berryR * berryBrightness, berryG * berryBrightness, berryB * berryBrightness, alpha);
                    
                    // Berry 2 (Higher, Left-ish)
                    // X - 0.25, Y + 0.3, Z - 0.1
                    // Staggered height and opposite side
                    renderBox(buf, pose, rx - 0.25, ry + 0.3, rz - 0.1, bSize, bSize, bSize, berryR * berryBrightness, berryG * berryBrightness, berryB * berryBrightness, alpha);
                }

            } else if (renderType == 5) { // RENDER_SUGAR_CANE
                  // Sugar Cane: 3 Green Tubes with Borders
                  float caneR = 150 / 255.0f;
                  float caneG = 210 / 255.0f;
                  float caneB = 100 / 255.0f;
                  
                  // Dark Green Border Color (Lighter now)
                  float bR = 60 / 255.0f;
                  float bG = 100 / 255.0f;
                  float bB = 40 / 255.0f;
                  
                  float tubeSize = 0.25f;
                  float bThick = 0.03f; // Border thickness
                  
                  // Tube Offsets: {x, z}
                  float[][] offsets = {
                      {-0.2f, -0.1f}, // Left-Back
                      {0.2f, -0.1f},  // Right-Back
                      {0.0f, 0.2f}    // Front-Center
                  };
                  
                  for (float[] off : offsets) {
                      double tx = rx + off[0];
                      double tz = rz + off[1];
                      
                      // Main Tube
                      renderBox(buf, pose, tx, ry, tz, tubeSize, 1.0f, tubeSize, caneR * brightness, caneG * brightness, caneB * brightness, alpha);
                      
                      // Borders
                      // West
                      renderBox(buf, pose, tx - tubeSize/2 - bThick/2, ry, tz, bThick, 1.0f, tubeSize + 2*bThick, bR * brightness, bG * brightness, bB * brightness, alpha);
                      // East
                      renderBox(buf, pose, tx + tubeSize/2 + bThick/2, ry, tz, bThick, 1.0f, tubeSize + 2*bThick, bR * brightness, bG * brightness, bB * brightness, alpha);
                      // North (Between W/E)
                      renderBox(buf, pose, tx, ry, tz - tubeSize/2 - bThick/2, tubeSize, 1.0f, bThick, bR * brightness, bG * brightness, bB * brightness, alpha);
                      // South (Between W/E)
                      renderBox(buf, pose, tx, ry, tz + tubeSize/2 + bThick/2, tubeSize, 1.0f, bThick, bR * brightness, bG * brightness, bB * brightness, alpha);
                  }
                  
             } else if (renderType == 6) { // RENDER_CACTUS
                 // Cactus: Thinner body + Spines + Vertical Stripes
                 // Body Size: 14/16 = 0.875
                 float bodySize = 0.875f;
                 
                 // Use block color (usually green)
                 float baseR = (r * brightness) / 255.0f;
                 float baseG = (g * brightness) / 255.0f;
                 float baseB = (b * brightness) / 255.0f;
                 
                 renderBox(buf, pose, rx, ry, rz, bodySize, 1.0f, bodySize, baseR, baseG, baseB, alpha);
                 
                 // Vertical Dark Stripes (Ribs)
                 // Darker green: Multiply by 0.6
                 float stripeR = baseR * 0.6f;
                 float stripeG = baseG * 0.6f;
                 float stripeB = baseB * 0.6f;
                 
                 float sW = 0.1f; // Stripe Width
                 float sD = 0.02f; // Stripe Depth (Protrusion)
                 float sOff = bodySize / 2.0f + (sD / 2.0f); // Slightly outside
                 
                 // Z- Face
                 renderBox(buf, pose, rx - 0.2, ry, rz - sOff, sW, 1.0f, sD, stripeR, stripeG, stripeB, alpha);
                 renderBox(buf, pose, rx + 0.2, ry, rz - sOff, sW, 1.0f, sD, stripeR, stripeG, stripeB, alpha);
                 
                 // Z+ Face
                 renderBox(buf, pose, rx - 0.2, ry, rz + sOff, sW, 1.0f, sD, stripeR, stripeG, stripeB, alpha);
                 renderBox(buf, pose, rx + 0.2, ry, rz + sOff, sW, 1.0f, sD, stripeR, stripeG, stripeB, alpha);
                 
                 // X- Face (Rotated)
                 renderBox(buf, pose, rx - sOff, ry, rz - 0.2, sD, 1.0f, sW, stripeR, stripeG, stripeB, alpha);
                 renderBox(buf, pose, rx - sOff, ry, rz + 0.2, sD, 1.0f, sW, stripeR, stripeG, stripeB, alpha);
                 
                 // X+ Face (Rotated)
                 renderBox(buf, pose, rx + sOff, ry, rz - 0.2, sD, 1.0f, sW, stripeR, stripeG, stripeB, alpha);
                 renderBox(buf, pose, rx + sOff, ry, rz + 0.2, sD, 1.0f, sW, stripeR, stripeG, stripeB, alpha);

                 // Spines (Thorns)
                 float spineLen = 0.1f;
                 float spineThick = 0.05f;
                 float spineR = 0.9f; 
                 float spineG = 0.9f;
                 float spineB = 0.8f;
                 
                 float offset = bodySize / 2.0f; 
                 
                 // Face 1 (Z-): 2 Spines (Centered between stripes)
                 renderBox(buf, pose, rx, ry + 0.25, rz - offset, spineThick, spineThick, spineLen, spineR, spineG, spineB, alpha);
                 renderBox(buf, pose, rx, ry - 0.25, rz - offset, spineThick, spineThick, spineLen, spineR, spineG, spineB, alpha);

                 // Face 2 (Z+): 2 Spines
                 renderBox(buf, pose, rx, ry + 0.25, rz + offset, spineThick, spineThick, spineLen, spineR, spineG, spineB, alpha);
                 renderBox(buf, pose, rx, ry - 0.25, rz + offset, spineThick, spineThick, spineLen, spineR, spineG, spineB, alpha);

                 // Face 3 (X-): 2 Spines
                 renderBox(buf, pose, rx - offset, ry + 0.25, rz, spineLen, spineThick, spineThick, spineR, spineG, spineB, alpha);
                 renderBox(buf, pose, rx - offset, ry - 0.25, rz, spineLen, spineThick, spineThick, spineR, spineG, spineB, alpha);

                 // Face 4 (X+): 2 Spines
                 renderBox(buf, pose, rx + offset, ry + 0.25, rz, spineLen, spineThick, spineThick, spineR, spineG, spineB, alpha);
                  renderBox(buf, pose, rx + offset, ry - 0.25, rz, spineLen, spineThick, spineThick, spineR, spineG, spineB, alpha);
 
             } else if (renderType == 7) { // RENDER_SAPLING
                // Sapling: Small Brown Block (0.4x0.4x0.4)
                float sSize = 0.4f;
                // Brown Wood Color
                float woodR = 120 / 255.0f;
                float woodG = 80 / 255.0f;
                float woodB = 40 / 255.0f;
                
                // Position: Bottom Center
                // rx, ry, rz is block center.
                // To put on floor (y-0.5), we need center at (y-0.5) + (sSize/2) = y - 0.5 + 0.2 = y - 0.3
                 renderBox(buf, pose, rx, ry - 0.3, rz, sSize, sSize, sSize, woodR * brightness, woodG * brightness, woodB * brightness, alpha);
                  
              } else if (renderType == 8) { // RENDER_BAMBOO
                 // Bamboo: Single Green Tube, varying thickness but usually thin
                 float bambooSize = 0.25f; // Thin stalk
                 
                 // Bamboo Green
                 float bamR = 100 / 255.0f;
                 float bamG = 180 / 255.0f;
                 float bamB = 60 / 255.0f;
                 
                 // Render Stalk (Full Height)
                 renderBox(buf, pose, rx, ry, rz, bambooSize, 1.0f, bambooSize, bamR * brightness, bamG * brightness, bamB * brightness, alpha);
                 
                 // Add small leaves? (Optional, maybe later if requested)
                 // For now just the "tube upwards" as requested.
                 
              } else if (renderType == 9) { // RENDER_POTTED_PLANT
                 // 1. Flower Pot: Brown Square at bottom
                 float potR = 180 / 255.0f; // Terracotta-ish
                 float potG = 100 / 255.0f;
                 float potB = 80 / 255.0f;
                 
                 float potSize = 0.35f;
                 float potHeight = 0.3f;
                 
                 // Center Y for Pot: Bottom (-0.5) + Half Height (0.15) = -0.35
                 renderBox(buf, pose, rx, ry - 0.35, rz, potSize, potHeight, potSize, potR * brightness, potG * brightness, potB * brightness, alpha);
                 
                 // 2. Plant Inside: Small Block with Specific Color
                 // Use the passed r, g, b values which come from ChunkScanner's getPottedPlantColor
                 float plantSize = 0.25f;
                 
                 // Center Y for Plant: Top of Pot (-0.2) + Half Plant (0.125) = -0.075
                renderBox(buf, pose, rx, ry - 0.075, rz, plantSize, plantSize, plantSize, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);

             } else if (renderType == 10) { // RENDER_FLOWER_POT
                 // Empty Flower Pot
                 float potR = 180 / 255.0f; // Terracotta-ish
                 float potG = 100 / 255.0f;
                 float potB = 80 / 255.0f;
                 
                 float potSize = 0.35f;
                 float potHeight = 0.3f;
                 
                 renderBox(buf, pose, rx, ry - 0.35, rz, potSize, potHeight, potSize, potR * brightness, potG * brightness, potB * brightness, alpha);
                 
              } else if (renderType == 11) { // RENDER_GRASS
                 // Grass/Fern: Render as multiple small blades/tufts to give "relief"
                 // Use the block color (usually biome green) passed in r,g,b
                 
                 float bladeW = 0.1f;
                 float bladeH = 0.45f; // Short grass height
                 
                 // Blade 1 (Left-Back)
                renderBox(buf, pose, rx - 0.2, ry, rz - 0.2, bladeW, bladeH, bladeW, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                // Blade 2 (Right-Front)
                renderBox(buf, pose, rx + 0.2, ry, rz + 0.2, bladeW, bladeH * 0.8f, bladeW, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                // Blade 3 (Left-Front)
                renderBox(buf, pose, rx - 0.15, ry, rz + 0.15, bladeW, bladeH * 0.9f, bladeW, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                // Blade 4 (Right-Back)
                renderBox(buf, pose, rx + 0.1, ry, rz - 0.1, bladeW, bladeH * 1.1f, bladeW, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);

              } else {
                 // Render block as a box (1.0 size for solid terrain)
                renderBlockWithBorders(buf, pose, rx, ry, rz, 1.0f, 1.0f, 1.0f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha, exposedFaces);
            }
        }
    }
    
    private static void renderEntities(PoseStack poseStack, Player player, int minY, int maxY, int radius) {
        double centerX = player.getX();
        double centerZ = player.getZ();
        double centerY = player.getY();
        
        Minecraft mc = Minecraft.getInstance();
        Iterable<Entity> entities = mc.level.entitiesForRendering();
        Map<ChunkPos, ChunkScanner.ScannedChunk> scannedData = ChunkScanner.getData();
        ChunkPos playerChunk = player.chunkPosition();
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        // Pass 1: Boxes
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        for (Entity e : entities) {
            if (e instanceof Player) continue;
            
            // Vertical Culling for Entities
            if (e.getY() < minY || e.getY() > maxY) continue;
            
            // Horizontal Culling (Radius & Scanned Check)
            ChunkPos entityChunk = new ChunkPos(e.blockPosition());
            
            // Check if chunk is scanned (only render entities on visible map chunks)
            if (!scannedData.containsKey(entityChunk)) continue;
            
            // Check radius
            if (radius > 0) {
                 int dx = Math.abs(entityChunk.x - playerChunk.x);
                 int dz = Math.abs(entityChunk.z - playerChunk.z);
                 if (dx > radius || dz > radius) continue;
            }
            
            double rx = e.getX() - centerX;
            double ry = e.getY() - centerY;
            double rz = e.getZ() - centerZ;
            
            if (e instanceof Monster) {
                if (!ClientSettings.showEnemies) continue;
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 1.8f, 0.6f, 1.0f, 0.0f, 0.0f, 1.0f);
            } else if (e instanceof Villager) {
                if (!ClientSettings.showVillagers) continue;
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 1.8f, 0.6f, 0.6f, 0.4f, 0.3f, 1.0f);
            } else if (e instanceof Squid) {
                if (!ClientSettings.showAnimals) continue;
                
                // Determine if it's a Glow Squid
                boolean isGlowSquid = (e instanceof GlowSquid);
                
                if (isGlowSquid) {
                    // Glow Squid: Bright Cyan/Aqua
                    // Emphasize luminosity with bright colors
                    renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 0.6f, 0.6f, 0.0f, 1.0f, 1.0f, 1.0f);
                } else {
                    // Normal Squid: Dark Blue
                    renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 0.6f, 0.6f, 0.2f, 0.2f, 0.6f, 1.0f);
                }
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
                if (p != player) { // Render other players, but NOT self (self is rendered as marker at center)
                     renderPlayerHead(poseStack, p, centerX, centerZ, centerY);
                }
            }
        }
    }
    
    private static void renderPlayerHead(PoseStack poseStack, AbstractClientPlayer player, double cx, double cz, double cy) {
        ResourceLocation skin = player.getSkinTextureLocation();
        
        double rx = player.getX() - cx;
        double ry = player.getY() - cy;
        double rz = player.getZ() - cz;
        
        poseStack.pushPose();
        poseStack.translate(rx, ry, rz);
        // Rotate to match player facing
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - player.getYRot()));
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        float headSize = 2.0f;
        float borderSize = 2.3f;
        
        // 1. Render Textured Head
        RenderSystem.setShaderTexture(0, skin);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // Render at 0,0,0 local to the translated position
        renderTexturedHead(buf, poseStack.last().pose(), 0, 0, 0, headSize);
        BufferUploader.drawWithShader(buf.end());
        
        // 2. Render White Border (Inverted Hull)
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderInvertedColorBox(buf, poseStack.last().pose(), 0, 0, 0, borderSize, 1.0f, 1.0f, 1.0f, 1.0f);
        BufferUploader.drawWithShader(buf.end());
        
        poseStack.popPose();
    }

    private static void renderBlockWithBorders(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float w, float h, float d, float r, float g, float b, float a, int exposedFaces) {
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
        
        // Bit 3: Up (+y) - Top Face
        if ((exposedFaces & 8) != 0) {
            buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
            
            // Render Borders on Top Face
            // Border color: Black Opaque (for visibility)
            int br = 0, bg = 0, bb = 0, ba = 255;
            float bSize = 0.125f; // 1/8th of a block
            float yOff = 0.02f; // Increased offset to prevent z-fighting
            
            // West Edge (Bit 0)
            if ((exposedFaces & 1) != 0) {
                 // CCW Order: Top-Left -> Bottom-Left -> Bottom-Right -> Top-Right
                 buf.vertex(pose, minX, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, minX, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, minX + bSize, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, minX + bSize, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
            }
            
            // East Edge (Bit 1)
            if ((exposedFaces & 2) != 0) {
                 // CCW Order
                 buf.vertex(pose, maxX - bSize, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, maxX - bSize, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, maxX, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, maxX, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
            }
            
            // North Edge (Bit 4)
            if ((exposedFaces & 16) != 0) {
                  // CCW Order
                  buf.vertex(pose, minX, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, minX, maxY + yOff, minZ + bSize).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, maxX, maxY + yOff, minZ + bSize).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, maxX, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
            }
            
            // South Edge (Bit 5)
            if ((exposedFaces & 32) != 0) {
                  // CCW Order
                  buf.vertex(pose, minX, maxY + yOff, maxZ - bSize).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, minX, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, maxX, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, maxX, maxY + yOff, maxZ - bSize).color(br, bg, bb, ba).endVertex();
            }
        }
        
        // Bit 2: Down (-y) - Bottom Face
        if ((exposedFaces & 4) != 0) {
            buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        }
        
        // Bit 4: North (-z) - Front/Back? 
        // Logic says North is -z.
        // In renderBox: "Front" was minZ. "Back" was maxZ. 
        // Wait, Front usually means +z or -z depending on convention.
        // In renderBox:
        // Front: minZ face? (lines 520-523 uses minZ for Z coords). Yes.
        // Back: maxZ face? (lines 526-529 uses maxZ). Yes.
        
        // So North (-z) corresponds to "Front" in renderBox code?
        // Let's check renderBox "Front":
        // buf.vertex(pose, maxX, maxY, minZ)...
        // Yes, all Z are minZ. So "Front" is North face (-z).
        
        if ((exposedFaces & 16) != 0) { // North
            buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        }
        
        // Bit 5: South (+z) - Back
        if ((exposedFaces & 32) != 0) { // South
            buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        }
        
        // Bit 0: West (-x) - Left?
        // In renderBox "Left": all X are minX. Yes, West.
        if ((exposedFaces & 1) != 0) { // West
            buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        }
        
        // Bit 1: East (+x) - Right?
        // In renderBox "Right": all X are maxX. Yes, East.
        if ((exposedFaces & 2) != 0) { // East
            buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        }
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

    private static void renderInvertedColorBox(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float size, float red, float green, float blue, float alpha) {
        float minX = (float)(x - size/2);
        float maxX = (float)(x + size/2);
        float minY = (float)y;
        float maxY = (float)(y + size);
        float minZ = (float)(z - size/2);
        float maxZ = (float)(z + size/2);
        
        // Reverse winding order (3, 2, 1, 0) for inverted hull effect
        
        // Top
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Bottom
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Front
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Back
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        
        // Left
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Right
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
    }

    private static void renderChunkGrid(PoseStack poseStack, Player player, int radius) {
        if (!ClientSettings.showChunkGrid) return;

        double cx = player.getX();
        double cz = player.getZ();
        
        ChunkPos centerChunk = player.chunkPosition();
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f);
        
        // Use LINES mode
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f pose = poseStack.last().pose();
        
        int minChunkX = centerChunk.x - radius;
        int maxChunkX = centerChunk.x + radius;
        int minChunkZ = centerChunk.z - radius;
        int maxChunkZ = centerChunk.z + radius;
        
        // Yellow color for grid, semi-transparent
        int r = 255, g = 255, b = 0, a = 128;
        
        // Vertical lines (Z-axis lines, varying X)
        for (int x = minChunkX; x <= maxChunkX + 1; x++) {
             double worldX = x * 16.0;
             double rx = worldX - cx;
             
             double worldZMin = minChunkZ * 16.0;
             double worldZMax = (maxChunkZ + 1) * 16.0;
             
             double rzMin = worldZMin - cz;
             double rzMax = worldZMax - cz;
             
             buf.vertex(pose, (float)rx, 0, (float)rzMin).color(r, g, b, a).endVertex();
             buf.vertex(pose, (float)rx, 0, (float)rzMax).color(r, g, b, a).endVertex();
        }
        
        // Horizontal lines (X-axis lines, varying Z)
        for (int z = minChunkZ; z <= maxChunkZ + 1; z++) {
             double worldZ = z * 16.0;
             double rz = worldZ - cz;
             
             double worldXMin = minChunkX * 16.0;
             double worldXMax = (maxChunkX + 1) * 16.0;
             
             double rxMin = worldXMin - cx;
             double rxMax = worldXMax - cx;
             
             buf.vertex(pose, (float)rxMin, 0, (float)rz).color(r, g, b, a).endVertex();
             buf.vertex(pose, (float)rxMax, 0, (float)rz).color(r, g, b, a).endVertex();
        }
        
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.lineWidth(1.0f);
    }
}
