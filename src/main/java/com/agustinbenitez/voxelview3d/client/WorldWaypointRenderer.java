package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;

            double rx = wp.x + 0.5 - cameraPos.x;
            double rz = wp.z + 0.5 - cameraPos.z;
            
            // Beam from waypoint Y upwards
            double bottomY = wp.y - cameraPos.y;
            double beamHeight = 2048.0; 
            
            // Calculate distance for fading
            double distSq = rx*rx + rz*rz;
            float maxFadeDist = 3.0f;
            float alpha = 0.8f;

            if (distSq < maxFadeDist * maxFadeDist) {
                double dist = Math.sqrt(distSq);
                alpha = (float) (dist / maxFadeDist) * 0.8f;
                if (alpha < 0.1f) alpha = 0.05f; 
            }
            
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            
            int color = wp.color;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            
            float wInner = 0.2f;
            float wOuter = 0.6f;
            
            // Use renderBox from VoxelMapRenderer
            VoxelMapRenderer.renderBox(buf, poseStack.last().pose(), rx, bottomY, rz, wInner, (float)beamHeight, wInner, r, g, b, alpha);
            VoxelMapRenderer.renderBox(buf, poseStack.last().pose(), rx, bottomY, rz, wOuter, (float)beamHeight, wOuter, r, g, b, alpha * 0.3f);
            
            BufferUploader.drawWithShader(buf.end());
            
            // Render Name Tag and Icon
            if (alpha > 0.2f) {
                renderNameTag(poseStack, mc.font, wp, rx, bottomY + 2.5, rz, event.getCamera().getYRot());
            }
        }

        poseStack.popPose();
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void renderNameTag(PoseStack poseStack, Font font, ClientSettings.Waypoint wp, double x, double y, double z, float cameraYaw) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-cameraYaw));
        poseStack.scale(-0.025f, -0.025f, 0.025f); // Standard name tag scale

        Matrix4f matrix4f = poseStack.last().pose();
        float f1 = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
        int j = (int)(f1 * 255.0F) << 24;
        
        String text = wp.name;
        float hOffset = -font.width(text) / 2.0f;
        
        // Disable depth test to ensure text/icon is always visible
        RenderSystem.disableDepthTest();
        
        // 1. Draw Icon
        ResourceLocation iconLoc = new ResourceLocation("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, iconLoc);
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        // Icon size in local units (after scaling)
        // Scale is 0.025, so 1 local unit = 0.025 world units
        // We want icon to be maybe 0.5 world units wide -> 20 local units
        float iconSize = 16.0f; 
        float iconY = -12.0f; // Above text (text is at 0), moved closer
        
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(matrix4f, -iconSize/2, iconY - iconSize, 0).uv(0, 0).endVertex();
        buf.vertex(matrix4f, -iconSize/2, iconY, 0).uv(0, 1).endVertex();
        buf.vertex(matrix4f, iconSize/2, iconY, 0).uv(1, 1).endVertex();
        buf.vertex(matrix4f, iconSize/2, iconY - iconSize, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(buf.end());
        
        // 2. Draw Text
        // Use immediate buffer to ensure it draws with disabled depth test right now
        var bufferSource = net.minecraft.client.renderer.MultiBufferSource.immediate(tess.getBuilder());
        
        // Use SEE_THROUGH to ensure it renders on top of everything (like the beam)
        font.drawInBatch(text, hOffset, 0, 0xFFFFFFFF, false, matrix4f, bufferSource, Font.DisplayMode.SEE_THROUGH, j, 15728880);
        
        bufferSource.endBatch();
        
        RenderSystem.enableDepthTest();
        
        poseStack.popPose();
    }
}
