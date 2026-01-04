package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

@Mod.EventBusSubscriber(modid = "voxelview3d", value = Dist.CLIENT)
public class WorldWaypointRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientSettings.waypoints.isEmpty()) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        
        // We render relative to camera, so we subtract cameraPos from waypoint pos
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        // Render System Setup
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Disable Depth Mask for see-through beams
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Group waypoints by Angle (Visual Cluster)
        // This groups waypoints that overlap on screen
        List<List<ClientSettings.Waypoint>> clusters = new ArrayList<>();
        
        // Threshold for clustering (radians). 
        // 2 degrees = ~0.035 rad.
        // If we want stricter clustering, lower it.
        // If we want to group more aggressively when far away, increase it.
        double angleThreshold = Math.toRadians(2.5); 

        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;
            // Only render waypoints in the current dimension
            if (mc.player.level() != null && !wp.getDimension().equals(mc.player.level().dimension().location().toString())) continue;
            
            // Calculate direction from camera to waypoint
            double dx = wp.x + 0.5 - cameraPos.x;
            double dy = wp.y - cameraPos.y; // Use base Y
            double dz = wp.z + 0.5 - cameraPos.z;
            
            // Normalize
            double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (len < 0.001) continue; // On top of player
            
            double nx = dx / len;
            double ny = dy / len;
            double nz = dz / len;
            
            boolean added = false;
            for (List<ClientSettings.Waypoint> cluster : clusters) {
                // Check angle against first element of cluster
                ClientSettings.Waypoint cWp = cluster.get(0);
                double cDx = cWp.x + 0.5 - cameraPos.x;
                double cDy = cWp.y - cameraPos.y;
                double cDz = cWp.z + 0.5 - cameraPos.z;
                double cLen = Math.sqrt(cDx*cDx + cDy*cDy + cDz*cDz);
                
                double cNx = cDx / cLen;
                double cNy = cDy / cLen;
                double cNz = cDz / cLen;
                
                // Dot product
                double dot = nx*cNx + ny*cNy + nz*cNz;
                // Clamp dot
                if (dot > 1.0) dot = 1.0;
                if (dot < -1.0) dot = -1.0;
                
                double angle = Math.acos(dot);
                
                if (angle < angleThreshold) {
                    cluster.add(wp);
                    added = true;
                    break;
                }
            }
            
            if (!added) {
                List<ClientSettings.Waypoint> newCluster = new ArrayList<>();
                newCluster.add(wp);
                clusters.add(newCluster);
            }
        }

        for (List<ClientSettings.Waypoint> group : clusters) {
            if (group.isEmpty()) continue;

            // Sort by distance to camera (render far to near or near to far?)
            // For stacking, we might want to sort by Y?
            // Existing logic sorted by Y. Let's keep that for consistent stacking order.
            group.sort(Comparator.comparingInt(w -> w.y));

            // 1. Render Beams for ALL waypoints in the group
            // We iterate all to draw their beams at their REAL positions
            // But we only draw the beam if it's the "lowest" of its vertical stack (if they are truly at same x,z)
            // Wait, previous logic only drew one beam per X,Z group.
            // Now we have a visual cluster. They might be at different X,Z.
            // We should probably draw beams for all of them, or maybe just one if they are super close?
            // Let's draw beams for all of them for now, but fade them correctly.
            
            // To avoid Z-fighting or clutter, maybe we can skip beams if they are too close?
            // Let's stick to drawing all beams for accuracy.
            
            for (ClientSettings.Waypoint wp : group) {
                double rx = wp.x + 0.5 - cameraPos.x;
                double rz = wp.z + 0.5 - cameraPos.z;
                double bottomY = wp.y - cameraPos.y;
                double beamHeight = 2048.0; 
                
                double distSq = rx*rx + rz*rz;
                float maxFadeDist = 3.0f;
                float baseAlpha = 0.2f;
                float alpha = baseAlpha;
    
                if (distSq < maxFadeDist * maxFadeDist) {
                    double dist = Math.sqrt(distSq);
                    alpha = (float) (dist / maxFadeDist) * baseAlpha;
                    if (alpha < 0.05f) alpha = 0.05f; 
                }
                
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                
                int color = wp.color;
                if ("dead".equals(wp.iconName)) {
                    color = 0x555555;
                }
                
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;
                
                float wInner = 0.2f;
                VoxelMapRenderer.renderBox(buf, poseStack.last().pose(), rx, bottomY, rz, wInner, (float)beamHeight, wInner, r, g, b, alpha);
                BufferUploader.drawWithShader(buf.end());
                RenderSystem.defaultBlendFunc();
            }

            // 2. Render Name Tags (Stacked)
            // We calculate a shared render position for the stack based on the CLOSEST waypoint
            // This prevents occlusion (labels appearing behind walls because they were averaged with a far waypoint)
            
            double closestDistSq = Double.MAX_VALUE;
            double anchorRx = 0;
            double anchorRz = 0;
            
            for (ClientSettings.Waypoint wp : group) {
                 double rx = wp.x + 0.5 - cameraPos.x;
                 double rz = wp.z + 0.5 - cameraPos.z;
                 double dSq = rx*rx + rz*rz;
                 if (dSq < closestDistSq) {
                     closestDistSq = dSq;
                     anchorRx = rx;
                     anchorRz = rz;
                 }
            }
            
            // Render relative to camera using ANCHOR X,Z (Closest)
            double rx = anchorRx;
            double rz = anchorRz;

            // Use the alpha of the closest one
            // Re-calculate generic alpha for the group based on closest distance
            double distSq = closestDistSq;
            float maxFadeDist = 3.0f;
            float alpha = 0.2f; // Base alpha when far
            
            // If we are close, we fade out but KEEP IT VISIBLE (clamp)
            if (distSq < maxFadeDist * maxFadeDist) {
                double dist = Math.sqrt(distSq);
                alpha = (float) (dist / maxFadeDist) * 0.2f;
                if (alpha < 0.05f) alpha = 0.05f; // Clamp to ensure visibility even when standing on it
            }

            if (alpha >= 0.05f) {
                double lastLabelTopY = -Double.MAX_VALUE; // Track top of last label in world Y

                for (ClientSettings.Waypoint wp : group) {
                    // Start from the waypoint's REAL Y, but clamped/adjusted to stack?
                    // If we use real Y, they might be far apart vertically.
                    // If we want to stack them neatly, we should probably ignore their real Y difference if it causes overlap?
                    // BUT if we ignore real Y, the distance text might be wrong.
                    
                    // Strategy: Start at Real Y. If it overlaps with previous (lower) label, push it up.
                    // Since we sorted by Y, we go from bottom up.
                    
                    double targetY = wp.y + 2.5; // Natural position
                    
                    // Calculate dynamic height space based on distance
                    // Distance approximation for spacing using GROUP position (Anchor)
                    double dy = targetY - cameraPos.y;
                    double dist = Math.sqrt(rx*rx + dy*dy + rz*rz);
                    double scaleFactor = Math.max(1.0, dist / 10.0);
                    double labelHeightSpace = 0.8 * scaleFactor; // Compact spacing

                    // If overlaps with last label, push up
                    if (targetY < lastLabelTopY + labelHeightSpace) {
                        targetY = lastLabelTopY + labelHeightSpace;
                    }
                    
                    // Render relative to camera at ANCHOR X,Z but STACKED Y
                    double renderY = targetY - cameraPos.y;
                    
                    // Note: renderNameTag uses (x,y,z) to calculate distance for scaling.
                    // We are passing (rx, renderY, rz). rx/rz are anchor.
                    // This is good. It means they all scale roughly the same.
                    renderNameTag(poseStack, mc.font, wp, rx, renderY, rz);
                    
                    // Update last top
                    lastLabelTopY = targetY;
                }
            }
        }

        poseStack.popPose();
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true); // Restore depth mask
    }

    private static void renderNameTag(PoseStack poseStack, Font font, ClientSettings.Waypoint wp, double x, double y, double z) {
        double dist = Math.sqrt(x*x + y*y + z*z);

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        // Use standard camera orientation for perfect billboard
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        
        // Scale based on distance to maintain visibility
        // Base scale 0.025f corresponds to standard Minecraft tag size at close range
        // We scale up linearly with distance after 10 blocks to keep constant screen size
        float baseScale = 0.025f;
        float distFactor = (float) Math.max(1.0, dist / 10.0);
        float scale = baseScale * distFactor;
        
        poseStack.scale(-scale, -scale, scale); 

        Matrix4f matrix4f = poseStack.last().pose();
        float f1 = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
        int j = (int)(f1 * 255.0F) << 24;
        
        String text = wp.name;
        float hOffset = -font.width(text) / 2.0f;
        
        // Disable depth test to ensure text/icon is always visible
        RenderSystem.disableDepthTest();
        // Disable culling to prevent backface culling when rotating
        RenderSystem.disableCull();
        // Disable depth mask to prevent writing to depth buffer (just in case)
        RenderSystem.depthMask(false);
        
        // 1. Draw Icon
        ResourceLocation iconLoc = new ResourceLocation("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, iconLoc);
        
        // Use white color for icon in world view (no tint)
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        // Icon size in local units (after scaling)
        // Scale is variable now, but local coords remain same relative to text
        float iconSize = 16.0f; 
        float iconY = -10.0f; // Above text (text is at 0), moved closer
        
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(matrix4f, -iconSize/2, iconY - iconSize, 0).uv(0, 0).endVertex();
        buf.vertex(matrix4f, -iconSize/2, iconY, 0).uv(0, 1).endVertex();
        buf.vertex(matrix4f, iconSize/2, iconY, 0).uv(1, 1).endVertex();
        buf.vertex(matrix4f, iconSize/2, iconY - iconSize, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(buf.end());
        
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        // 2. Draw Text
        // Use immediate buffer to ensure it draws with disabled depth test right now
        var bufferSource = net.minecraft.client.renderer.MultiBufferSource.immediate(tess.getBuilder());
        
        // Use SEE_THROUGH to ensure it renders on top of everything (like the beam)
        font.drawInBatch(text, hOffset, 0, 0xFFFFFFFF, false, matrix4f, bufferSource, Font.DisplayMode.SEE_THROUGH, j, 15728880);
        
        // Draw Distance below name
        String distText = String.format("%.0fm", dist);
        float distOffset = -font.width(distText) / 2.0f;
        font.drawInBatch(distText, distOffset, 10, 0xFFAAAAAA, false, matrix4f, bufferSource, Font.DisplayMode.SEE_THROUGH, j, 15728880);
        
        bufferSource.endBatch();
        
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        
        poseStack.popPose();
    }
}
