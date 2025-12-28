package com.example.voxelview3d.client;

import com.example.voxelview3d.VoxelView3D;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

@Mod.EventBusSubscriber(modid = VoxelView3D.MODID, value = Dist.CLIENT)
public class VoxelMapHud {

    public enum MinimapPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, OFF;
        
        public MinimapPosition next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    public static MinimapPosition currentPosition = MinimapPosition.TOP_LEFT;
    
    private static final int MAP_SIZE = 100; // Size in pixels
    private static final int MARGIN = 10;

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        // We render after everything or specific layer? 
        // Post ALL might cover chat etc. usually okay for minimap.
        // Or check event.getOverlay()
        
        if (currentPosition == MinimapPosition.OFF) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof VoxelMapScreen) return; // Don't show HUD when map screen is open
        
        GuiGraphics guiGraphics = event.getGuiGraphics();
        PoseStack poseStack = guiGraphics.pose();
        
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        
        int x = MARGIN;
        int y = MARGIN;
        
        switch (currentPosition) {
            case TOP_LEFT:
                x = MARGIN;
                y = MARGIN;
                break;
            case TOP_RIGHT:
                x = width - MAP_SIZE - MARGIN;
                y = MARGIN;
                break;
            case BOTTOM_LEFT:
                x = MARGIN;
                y = height - MAP_SIZE - MARGIN;
                break;
            case BOTTOM_RIGHT:
                x = width - MAP_SIZE - MARGIN;
                y = height - MAP_SIZE - MARGIN;
                break;
            default:
                break;
        }
        
        // Draw Background
        // guiGraphics.fill(x - 2, y - 2, x + MAP_SIZE + 2, y + MAP_SIZE + 2, 0x80000000); // Removed as requested
        
        // Scissor Test to clip map to box
        // Needs proper scaling for retina displays etc.
        double scale = mc.getWindow().getGuiScale();
        int sx = (int)(x * scale);
        int sy = (int)((height - (y + MAP_SIZE)) * scale); // OpenGL Y is bottom-up
        int sw = (int)(MAP_SIZE * scale);
        int sh = (int)(MAP_SIZE * scale);
        
        RenderSystem.enableScissor(sx, sy, sw, sh);
        
        poseStack.pushPose();
        
        // Translate to center of minimap box
        poseStack.translate(x + MAP_SIZE / 2.0, y + MAP_SIZE / 2.0, 100);
        
        // 3D Rendering
        // Rotate map based on player yaw so "Up" is always "Forward"
        // Player Yaw: 0 = South, 90 = West, 180 = North, 270 = East (approx)
        // Actually: 0 = South (+Z), 90 = West (-X), 180 = North (-Z), -90 = East (+X)
        // We want the map to rotate so player forward is UP.
        // If player yaw is 0 (South), map should be rotated 180?
        // Let's just apply -playerYaw.
        
        float playerYaw = mc.player.getYRot();
        
        // Minimap Zoom (smaller view)
        float zoom = 5.0f; 
        
        // Use VoxelMapRenderer
        // Call renderMinimap2D for optimized top-down 2D view
        
        // Let's try matching player rotation.
        float camYaw = -playerYaw + 180;
        
        // Radius 2 chunks for optimization (very fast, less lag)
        // Zoom out more (was 5.0, now 3.0) as requested "mas alejado"
        VoxelMapRenderer.renderMinimap2D(poseStack, 3.0f, camYaw, 2);
        
        poseStack.popPose();
        RenderSystem.disableScissor();
        
        // Draw border - REMOVED
        // guiGraphics.renderOutline(x - 1, y - 1, MAP_SIZE + 2, MAP_SIZE + 2, 0xFFFFFFFF);
    }
}
