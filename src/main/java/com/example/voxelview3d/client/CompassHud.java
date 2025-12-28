package com.example.voxelview3d.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.awt.Color;

public class CompassHud implements IGuiOverlay {
    
    public static final CompassHud INSTANCE = new CompassHud();
    
    private static final int COMPASS_WIDTH = 150;
    private static final int COMPASS_HEIGHT = 12;
    private static final int VISIBLE_ANGLE = 100; // Degrees visible in the bar
    private static final double MAX_ENTITY_DISTANCE = 50.0; // Blocks

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        Player player = mc.player;
        float playerYaw = Mth.wrapDegrees(player.getYRot());

        int centerX = screenWidth / 2;
        int topY = 5; // Margin from top

        // 1. Draw Background
        // Black bar with transparency
        guiGraphics.fill(centerX - COMPASS_WIDTH / 2, topY, centerX + COMPASS_WIDTH / 2, topY + COMPASS_HEIGHT, 0x80000000);
        
        // Use Scissor to clip content to the bar
        // Scissor coords are in window pixels, not GUI pixels. Need scale factor.
        double scale = mc.getWindow().getGuiScale();
        int scissorX = (int)((centerX - COMPASS_WIDTH / 2) * scale);
        int scissorY = (int)((mc.getWindow().getHeight() - (topY + COMPASS_HEIGHT) * scale)); // Bottom-up
        int scissorW = (int)(COMPASS_WIDTH * scale);
        int scissorH = (int)(COMPASS_HEIGHT * scale);
        
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);

        // 2. Draw Entities (Now drawn FIRST so directions appear on top)
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == player) continue;
            if (!(entity instanceof LivingEntity)) continue; // Only living things
            
            // Filter based on settings
            if (entity instanceof Player && !ClientSettings.showPlayers) continue;
            if (entity instanceof Enemy && !ClientSettings.showEnemies) continue;
            if (entity instanceof Villager && !ClientSettings.showVillagers) continue;
            if (entity instanceof Animal && !ClientSettings.showAnimals) continue;
            // If it's none of the above (e.g. Squid, Bat, etc), maybe treat as Animal or ignore?
            // Let's treat others as Animals for now if they are not monsters
            if (!(entity instanceof Player) && !(entity instanceof Enemy) && !(entity instanceof Villager) && !(entity instanceof Animal) && !ClientSettings.showAnimals) continue;

            double distSq = entity.distanceToSqr(player);
            if (distSq > MAX_ENTITY_DISTANCE * MAX_ENTITY_DISTANCE) continue;

            // Calculate angle to entity
            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            
            // Atan2 returns angle from X axis.
            // MC Yaw 0 = +Z (South).
            // We want angle relative to South.
            // Math.atan2(dz, dx) -> 0 is +X (East). 90 is +Z (South).
            // So South (0 yaw) corresponds to 90 math degrees.
            
            double angleRad = Math.atan2(dz, dx); 
            double angleDeg = Math.toDegrees(angleRad); 
            
            // Convert to MC Yaw basis:
            // Math: E=0, S=90, W=180, N=-90
            // MC: S=0, W=90, N=180, E=-90
            // Relation: MC = (Math - 90)
            
            double entityYaw = angleDeg - 90; 
            
            drawEntityMarker(guiGraphics, playerYaw, (float)entityYaw, entity, centerX, topY);
        }

        // 3. Draw Waypoints
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;
            
            double dx = wp.x - player.getX();
            double dz = wp.z - player.getZ();
            
            double angleRad = Math.atan2(dz, dx);
            double angleDeg = Math.toDegrees(angleRad);
            double wpYaw = angleDeg - 90;
            
            drawWaypointMarker(guiGraphics, playerYaw, (float)wpYaw, wp, centerX, topY);
        }

        // 4. Draw Directions (N, S, E, W) - Drawn AFTER entities/waypoints to be on top
        drawDirection(guiGraphics, playerYaw, 0, "S", centerX, topY);
        drawDirection(guiGraphics, playerYaw, 90, "W", centerX, topY);
        drawDirection(guiGraphics, playerYaw, 180, "N", centerX, topY);
        drawDirection(guiGraphics, playerYaw, -90, "E", centerX, topY);
        drawDirection(guiGraphics, playerYaw, 270, "E", centerX, topY); // -90 is same as 270

        RenderSystem.disableScissor();
        
        // Draw center indicator (optional triangle or line)
        guiGraphics.fill(centerX - 1, topY + COMPASS_HEIGHT, centerX + 1, topY + COMPASS_HEIGHT + 5, 0xFFFFFFFF);
    }
    
    private void drawDirection(GuiGraphics guiGraphics, float playerYaw, float targetYaw, String text, int centerX, int topY) {
        float delta = Mth.wrapDegrees(targetYaw - playerYaw);
        
        if (Math.abs(delta) < VISIBLE_ANGLE / 2.0f) {
            float offset = (delta / (VISIBLE_ANGLE / 2.0f)) * (COMPASS_WIDTH / 2.0f);
            int x = (int)(centerX + offset);
            
            // Center the text
            int textWidth = guiGraphics.pose().last().pose().toString().length(); // Dummy, get font width
            int w = Minecraft.getInstance().font.width(text);
            
            guiGraphics.drawString(Minecraft.getInstance().font, text, x - w / 2, topY + 2, 0xFFFFFFFF, false);
        }
    }
    
    private void drawWaypointMarker(GuiGraphics guiGraphics, float playerYaw, float wpYaw, ClientSettings.Waypoint wp, int centerX, int topY) {
        float delta = Mth.wrapDegrees(wpYaw - playerYaw);
        
        if (Math.abs(delta) < VISIBLE_ANGLE / 2.0f) {
            float offset = (delta / (VISIBLE_ANGLE / 2.0f)) * (COMPASS_WIDTH / 2.0f);
            int x = (int)(centerX + offset);
            int y = topY + 1; // Slightly higher
            
            // Draw a diamond or rect with FULL ALPHA
            guiGraphics.fill(x - 2, y, x + 2, y + 4, wp.color | 0xFF000000);
            
            // Optional: Draw beam-like vertical line if desired, but kept simple for HUD
        }
    }
    
    private void drawEntityMarker(GuiGraphics guiGraphics, float playerYaw, float entityYaw, Entity entity, int centerX, int topY) {
        float delta = Mth.wrapDegrees(entityYaw - playerYaw);
        
        if (Math.abs(delta) < VISIBLE_ANGLE / 2.0f) {
            float offset = (delta / (VISIBLE_ANGLE / 2.0f)) * (COMPASS_WIDTH / 2.0f);
            int x = (int)(centerX + offset);
            int y = topY + 1; // Slightly higher
            
            if (entity instanceof AbstractClientPlayer) {
                // Render Player Face
                AbstractClientPlayer p = (AbstractClientPlayer) entity;
                ResourceLocation skin = p.getSkinTextureLocation();
                
                RenderSystem.setShaderTexture(0, skin);
                // Draw 8x8 face scaled to 8x8 on screen (or bigger)
                // blit(texture, x, y, width, height, u, v, uWidth, vHeight, texWidth, texHeight)
                // Head is usually at u=8, v=8, size 8x8. 
                // Texture size 64x64.
                
                int headSize = 16;
                // Center the head on x
                // Draw face
                guiGraphics.blit(skin, x - headSize/2, y, headSize, headSize, 8, 8, 8, 8, 64, 64);
                // Draw hat/outer layer
                RenderSystem.enableBlend();
                guiGraphics.blit(skin, x - headSize/2, y, headSize, headSize, 40, 8, 8, 8, 64, 64);
                RenderSystem.disableBlend();
                
            } else {
                // Render generic dot/icon
                int color = 0xFFFFFFFF; // White default
                if (entity instanceof Enemy) {
                    color = 0xFFFF0000; // Red for enemies
                } else if (entity instanceof Villager) {
                    color = 0xFF00FF00; // Green for villagers
                } else {
                    color = 0xFFAAAAAA; // Gray for others
                }
                
                // Draw a small 4x4 rect
                guiGraphics.fill(x - 2, y + 6, x + 2, y + 10, color);
            }
        }
    }
}
