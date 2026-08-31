package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.*;


public class WorldWaypointRenderer {
    public static void registerEvents() {
        net.minecraftforge.client.event.AddFramePassEvent.BUS.addListener(WorldWaypointRenderer::onAddFramePasses);
    }


    
    public static void onAddFramePasses(AddFramePassEvent event) {
        event.addPass(Identifier.fromNamespaceAndPath("voxelview3d", "waypoint_pass"), new FramePassManager.PassDefinition() {
            @Override
            public void extracts(net.minecraft.client.renderer.LevelTargetBundle bundle, com.mojang.blaze3d.framegraph.FramePass pass) {
                pass.readsAndWrites(bundle.main);
            }

            @Override
            public void executes(LevelRenderState state) {
                renderWaypoints(state);
            }
        });
    }

    private static void renderWaypoints(LevelRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientSettings.waypoints.isEmpty()) return;

        Vec3 cameraPos = state.cameraRenderState.pos;
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        
        BufferBuilder buf;

        List<List<ClientSettings.Waypoint>> clusters = new ArrayList<>();
        double angleThreshold = Math.toRadians(2.5); 

        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;
            if (mc.player.level() != null && !wp.getDimension().equals(mc.player.level().dimension().identifier().toString())) continue;
            
            double dx = wp.x + 0.5 - cameraPos.x;
            double dy = wp.y - cameraPos.y;
            double dz = wp.z + 0.5 - cameraPos.z;
            
            double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (len < 0.001) continue;
            
            double nx = dx / len;
            double ny = dy / len;
            double nz = dz / len;
            
            boolean added = false;
            for (List<ClientSettings.Waypoint> cluster : clusters) {
                ClientSettings.Waypoint cWp = cluster.get(0);
                double cDx = cWp.x + 0.5 - cameraPos.x;
                double cDy = cWp.y - cameraPos.y;
                double cDz = cWp.z + 0.5 - cameraPos.z;
                double cLen = Math.sqrt(cDx*cDx + cDy*cDy + cDz*cDz);
                
                double cNx = cDx / cLen;
                double cNy = cDy / cLen;
                double cNz = cDz / cLen;
                
                double dot = nx*cNx + ny*cNy + nz*cNz;
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

            group.sort(Comparator.comparingInt(w -> w.y));

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
                
                buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                
                int color = wp.color;
                if ("dead".equals(wp.iconName)) {
                    color = 0x555555;
                }
                
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;
                
                float wInner = 0.2f;
                RenderSystem.getModelViewStack().pushMatrix();
                RenderSystem.getModelViewStack().mul(state.cameraRenderState.viewRotationMatrix);
                VoxelMapRenderer.renderBox(buf, new Matrix4f(), rx, bottomY, rz, wInner, (float)beamHeight, wInner, r, g, b, alpha);
                RenderBufferUtil.drawIfNotEmpty(buf);
                RenderSystem.getModelViewStack().popMatrix();
            }

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
            
            double rx = anchorRx;
            double rz = anchorRz;

            double distSq = closestDistSq;
            float maxFadeDist = 3.0f;
            float alpha = 0.2f;
            
            if (distSq < maxFadeDist * maxFadeDist) {
                double dist = Math.sqrt(distSq);
                alpha = (float) (dist / maxFadeDist) * 0.2f;
                if (alpha < 0.05f) alpha = 0.05f;
            }

            if (alpha >= 0.05f) {
                double lastLabelTopY = -Double.MAX_VALUE;

                for (ClientSettings.Waypoint wp : group) {
                    double targetY = wp.y + 2.5;
                    
                    double dy = targetY - cameraPos.y;
                    double dist = Math.sqrt(rx*rx + dy*dy + rz*rz);
                    double scaleFactor = Math.max(1.0, dist / 10.0);
                    double labelHeightSpace = 0.8 * scaleFactor;

                    if (targetY < lastLabelTopY + labelHeightSpace) {
                        targetY = lastLabelTopY + labelHeightSpace;
                    }
                    
                    double renderY = targetY - cameraPos.y;
                    renderNameTag(poseStack, state, mc.font, wp, rx, renderY, rz);
                    
                    lastLabelTopY = targetY;
                }
            }
        }

        poseStack.popPose();
    }

    private static void renderNameTag(PoseStack poseStack, LevelRenderState state, Font font, ClientSettings.Waypoint wp, double x, double y, double z) {
        double dist = Math.sqrt(x*x + y*y + z*z);

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(state.cameraRenderState.orientation);
        
        float baseScale = 0.025f;
        float distFactor = (float) Math.max(1.0, dist / 10.0);
        float scale = baseScale * distFactor;
        
        poseStack.scale(-scale, -scale, 1.0f); 

        Matrix4f matrix4f = poseStack.last().pose();
        float f1 = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
        int j = (int)(f1 * 255.0F) << 24;
        
        String text = wp.name;
        float hOffset = -font.width(text) / 2.0f;
        
        Identifier iconLoc = Identifier.fromNamespaceAndPath("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
        
        BufferBuilder buf;
        
        float iconSize = 16.0f; 
        float iconY = -10.0f;
        
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.addVertex(matrix4f, -iconSize/2, iconY - iconSize, 0).setUv(0, 0);
        buf.addVertex(matrix4f, -iconSize/2, iconY, 0).setUv(0, 1);
        buf.addVertex(matrix4f, iconSize/2, iconY, 0).setUv(1, 1);
        buf.addVertex(matrix4f, iconSize/2, iconY - iconSize, 0).setUv(1, 0);
        RenderBufferUtil.drawIfNotEmpty(buf);
        
        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        
        font.drawInBatch(text, hOffset, 0, 0xFFFFFFFF, false, matrix4f, bufferSource, Font.DisplayMode.SEE_THROUGH, j, 15728880);
        
        String distText = String.format("%.0fm", dist);
        float distOffset = -font.width(distText) / 2.0f;
        font.drawInBatch(distText, distOffset, 10, 0xFFAAAAAA, false, matrix4f, bufferSource, Font.DisplayMode.SEE_THROUGH, j, 15728880);
        
        bufferSource.endBatch();
        
        poseStack.popPose();
    }
}
