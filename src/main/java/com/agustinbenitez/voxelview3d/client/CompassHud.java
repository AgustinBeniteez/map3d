package com.agustinbenitez.voxelview3d.client;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CompassHud implements IGuiOverlay {
    
    public static final CompassHud INSTANCE = new CompassHud();
    
    private static final int COMPASS_WIDTH = 150;
    private static final int COMPASS_HEIGHT = 12;
    private static final int VISIBLE_ANGLE = 100; // Degrees visible in the bar
    private static final double MAX_ENTITY_DISTANCE = 50.0; // Blocks

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientSettings.showCompass) return;

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
        List<VisibleWaypoint> visibleWaypoints = new ArrayList<>();
        
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;
            
            double dx = wp.x - player.getX();
            double dz = wp.z - player.getZ();
            
            double angleRad = Math.atan2(dz, dx);
            double angleDeg = Math.toDegrees(angleRad);
            double wpYaw = angleDeg - 90;
            
            float delta = Mth.wrapDegrees((float)wpYaw - playerYaw);
            
            if (Math.abs(delta) < VISIBLE_ANGLE / 2.0f) {
                float offset = (delta / (VISIBLE_ANGLE / 2.0f)) * (COMPASS_WIDTH / 2.0f);
                int x = (int)(centerX + offset);
                visibleWaypoints.add(new VisibleWaypoint(wp, x));
            }
        }
        
        // Sort by X position
        visibleWaypoints.sort(Comparator.comparingInt(w -> w.x));
        
        // Group overlapping waypoints
        List<List<VisibleWaypoint>> groups = new ArrayList<>();
        if (!visibleWaypoints.isEmpty()) {
            List<VisibleWaypoint> currentGroup = new ArrayList<>();
            currentGroup.add(visibleWaypoints.get(0));
            groups.add(currentGroup);
            
            for (int i = 1; i < visibleWaypoints.size(); i++) {
                VisibleWaypoint current = visibleWaypoints.get(i);
                VisibleWaypoint prev = currentGroup.get(currentGroup.size() - 1);
                
                // Overlap threshold (e.g. 15 pixels)
                if (current.x - prev.x < 15) {
                    currentGroup.add(current);
                } else {
                    currentGroup = new ArrayList<>();
                    currentGroup.add(current);
                    groups.add(currentGroup);
                }
            }
        }
        
        // Render groups
        for (List<VisibleWaypoint> group : groups) {
            if (group.size() == 1) {
                // Render single
                VisibleWaypoint vwp = group.get(0);
                drawWaypointMarker(guiGraphics, vwp.x, topY, vwp.wp, false, true);
            } else {
                // Render stacked
                // Draw from last to first (back to front)? 
                // Actually, if we want them "stacked", we usually draw the bottom one first.
                // Let's verify: "Apilados" -> Stacked.
                // If I draw index 0 first, then index 1 on top...
                
                // Use average X for the stack to center it
                int avgX = (int) group.stream().mapToInt(w -> w.x).average().orElse(0);
                
                for (int i = 0; i < group.size(); i++) {
                    // Draw with offset and smaller size
                    // i=0 is the "bottom" of the stack (first one found, usually left-most)
                    // But visually, the "top" of the stack should be the last one drawn.
                    // Let's make the last item in the group the "top" one.
                    
                    int stackIndex = i; // 0 is bottom, size-1 is top
                    boolean isTop = (i == group.size() - 1);
                    
                    // Offset: shift up for items behind?
                    // Let's shift the items behind UPwards (-y)
                    // Top item (last) is at y. Item before is at y - 3.
                    // So yOffset = (group.size() - 1 - i) * -3
                    
                    int reverseIndex = group.size() - 1 - i;
                    int yOffset = reverseIndex * -3; 
                    
                    drawWaypointMarker(guiGraphics, avgX, topY + yOffset, group.get(i).wp, true, isTop);
                }
            }
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
    
    private void drawWaypointMarker(GuiGraphics guiGraphics, int x, int topY, ClientSettings.Waypoint wp, boolean isStacked, boolean isTop) {
        int y = topY + 1; // Slightly higher
        
        // Draw Icon instead of colored rect
        ResourceLocation iconLoc = new ResourceLocation("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
        RenderSystem.setShaderTexture(0, iconLoc);
        
        int iconSize = isStacked ? 7 : 10; // Smaller if stacked
        
        // Draw icon centered at x
        // If stacked, we might want to shift it visually or just use the y offset provided in the loop
        // The y passed here already includes the stack offset.
        
        guiGraphics.blit(iconLoc, x - iconSize/2, y, iconSize, iconSize, 0, 0, 16, 16, 16, 16);
        
        // Draw text BELOW the marker (only if not stacked, or only for the top one?)
        // If stacked, maybe only show text for the top one? Or none?
        // User didn't specify, but text for all would be messy.
        // Let's show text only if !isStacked. Or if it's the top of the stack?
        // But the method doesn't know if it's the top. 
        // Actually, the loop logic: stackIndex is passed. But we don't know total size here easily.
        // Let's just assume if isStacked, we skip text to avoid clutter, or draw it very small?
        // "apilados" -> usually implies only top one is fully interactable/visible details.
        // Let's hide text for stacked items to keep it clean, unless user complains.
        // Wait, if I have 2 important waypoints, I want to see both names?
        // But they overlap.
        // Let's just draw text for all but with the same y offset logic so they stack too?
        // If I draw text for all, it will be unreadable.
        // Let's only draw text for the *front-most* item?
        // In the loop, I draw from back to front.
        // So the last one drawn (front) will be on top.
        // But if I draw text for back ones, the front icon will cover it? No, text is below icon.
        // Text will stack messy.
        // Let's disable text for stacked items for now, except maybe the top one?
        // Implementation: Pass `isTop` boolean?
        // I'll stick to: if stacked, no text. Simpler and cleaner. 
        // Or better: Show text only if NOT stacked.
        
        if (!isStacked || isTop) {
            PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            float scale = 0.5f;
            // Move text down (y + 12) to be below icon (icon is 10px)
            // Adjust for smaller icon if stacked
            pose.translate(x, y + (isStacked ? 9 : 12), 0);
            pose.scale(scale, scale, 1.0f);
            
            int textWidth = Minecraft.getInstance().font.width(wp.name);
            guiGraphics.drawString(Minecraft.getInstance().font, wp.name, -textWidth / 2, 0, 0xFFFFFFFF, false);
            
            pose.popPose();
        }
    }
    
    private static class VisibleWaypoint {
        ClientSettings.Waypoint wp;
        int x;
        
        public VisibleWaypoint(ClientSettings.Waypoint wp, int x) {
            this.wp = wp;
            this.x = x;
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
                
                // Draw 8x8 face scaled to 8x8 on screen (smaller, aligned with other markers)
                int headSize = 8;
                int drawY = y + 4; // Align with generic markers
                
                // Center the head on x
                // Draw face
                guiGraphics.blit(skin, x - headSize/2, drawY, headSize, headSize, 8, 8, 8, 8, 64, 64);
                // Draw hat/outer layer
                RenderSystem.enableBlend();
                guiGraphics.blit(skin, x - headSize/2, drawY, headSize, headSize, 40, 8, 8, 8, 64, 64);
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
