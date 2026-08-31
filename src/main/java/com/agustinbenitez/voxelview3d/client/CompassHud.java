package com.agustinbenitez.voxelview3d.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CompassHud {

    private CompassHud() {
    }
    
    private static final int COMPASS_WIDTH = 150;
    private static final int COMPASS_HEIGHT = 12;
    private static final int VISIBLE_ANGLE = 100; // Degrees visible in the bar
    private static final double MAX_ENTITY_DISTANCE = 50.0; // Blocks

    public static void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        if (!ClientSettings.showCompass) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        Player player = mc.player;
        float playerYaw = Mth.wrapDegrees(player.getYRot());

        int screenWidth = mc.getWindow().getGuiScaledWidth();

        int centerX = screenWidth / 2;
        int topY = 5; // Margin from top
        
        float hudScale = 1.0f;
        switch (ClientSettings.hudSize) {
            case SMALL -> hudScale = 0.5f;
            case MEDIUM -> hudScale = 0.75f;
            case LARGE -> hudScale = 1.0f;
        }
        
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(centerX, topY);
        guiGraphics.pose().scale(hudScale, hudScale);
        guiGraphics.pose().translate(-centerX, -topY);

        // 1. Draw Background
        guiGraphics.fill(centerX - COMPASS_WIDTH / 2, topY, centerX + COMPASS_WIDTH / 2, topY + COMPASS_HEIGHT, 0x80000000);
        
        double scale = mc.getWindow().getGuiScale();
        int scissorX = (int)((centerX - (COMPASS_WIDTH / 2.0 * hudScale)) * scale);
        int scissorY = (int)((mc.getWindow().getHeight() - ((topY + (COMPASS_HEIGHT * hudScale))) * scale));
        int scissorW = (int)((COMPASS_WIDTH * hudScale) * scale);
        int scissorH = (int)((COMPASS_HEIGHT * hudScale) * scale);
        
        guiGraphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);

        // 2. Draw Entities
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == player) continue;
            if (!(entity instanceof LivingEntity)) continue;
            
            if (entity instanceof Player && !ClientSettings.showPlayers) continue;
            if (entity instanceof Enemy && !ClientSettings.showEnemies) continue;
            if (entity instanceof Villager && !ClientSettings.showVillagers) continue;
            if (entity instanceof Animal && !ClientSettings.showAnimals) continue;
            if (!(entity instanceof Player) && !(entity instanceof Enemy) && !(entity instanceof Villager) && !(entity instanceof Animal) && !ClientSettings.showAnimals) continue;

            double distSq = entity.distanceToSqr(player);
            if (distSq > MAX_ENTITY_DISTANCE * MAX_ENTITY_DISTANCE) continue;

            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            
            double angleRad = Math.atan2(dz, dx); 
            double angleDeg = Math.toDegrees(angleRad); 
            double entityYaw = angleDeg - 90; 
            
            drawEntityMarker(guiGraphics, playerYaw, (float)entityYaw, entity, centerX, topY);
        }

        // 3. Draw Waypoints
        List<VisibleWaypoint> visibleWaypoints = new ArrayList<>();
        
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;
            
            if (mc.level != null && !wp.getDimension().equals(mc.level.dimension().identifier().toString())) continue;
            
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
        
        visibleWaypoints.sort(Comparator.comparingInt(w -> w.x));
        
        List<List<VisibleWaypoint>> groups = new ArrayList<>();
        if (!visibleWaypoints.isEmpty()) {
            List<VisibleWaypoint> currentGroup = new ArrayList<>();
            currentGroup.add(visibleWaypoints.get(0));
            groups.add(currentGroup);
            
            for (int i = 1; i < visibleWaypoints.size(); i++) {
                VisibleWaypoint current = visibleWaypoints.get(i);
                VisibleWaypoint prev = currentGroup.get(currentGroup.size() - 1);
                
                if (current.x - prev.x < 15) {
                    currentGroup.add(current);
                } else {
                    currentGroup = new ArrayList<>();
                    currentGroup.add(current);
                    groups.add(currentGroup);
                }
            }
        }
        
        for (List<VisibleWaypoint> group : groups) {
            if (group.size() == 1) {
                VisibleWaypoint vwp = group.get(0);
                drawWaypointMarker(guiGraphics, vwp.x, topY, vwp.wp, false);
            } else {
                int avgX = (int) group.stream().mapToInt(w -> w.x).average().orElse(0);
                for (int i = 0; i < group.size(); i++) {
                    int reverseIndex = group.size() - 1 - i;
                    int yOffset = reverseIndex * -3; 
                    drawWaypointMarker(guiGraphics, avgX, topY + yOffset, group.get(i).wp, true);
                }
            }
        }

        // 4. Draw Directions
        drawDirection(guiGraphics, playerYaw, 0, "S", centerX, topY);
        drawDirection(guiGraphics, playerYaw, 90, "W", centerX, topY);
        drawDirection(guiGraphics, playerYaw, 180, "N", centerX, topY);
        drawDirection(guiGraphics, playerYaw, -90, "E", centerX, topY);
        drawDirection(guiGraphics, playerYaw, 270, "E", centerX, topY);

        guiGraphics.disableScissor();
        
        guiGraphics.fill(centerX - 1, topY + COMPASS_HEIGHT, centerX + 1, topY + COMPASS_HEIGHT + 5, 0xFFFFFFFF);
        
        guiGraphics.pose().popMatrix();
    }
    
    private static void drawDirection(GuiGraphicsExtractor guiGraphics, float playerYaw, float targetYaw, String text, int centerX, int topY) {
        float delta = Mth.wrapDegrees(targetYaw - playerYaw);
        
        if (Math.abs(delta) < VISIBLE_ANGLE / 2.0f) {
            float offset = (delta / (VISIBLE_ANGLE / 2.0f)) * (COMPASS_WIDTH / 2.0f);
            int x = (int)(centerX + offset);
            int w = Minecraft.getInstance().font.width(text);
            guiGraphics.text(Minecraft.getInstance().font, text, x - w / 2, topY + 2, 0xFFFFFFFF, false);
        }
    }
    
    private static void drawWaypointMarker(GuiGraphicsExtractor guiGraphics, int x, int topY, ClientSettings.Waypoint wp, boolean isStacked) {
        int y = topY + 1;
        Identifier iconLoc = Identifier.fromNamespaceAndPath("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
        int iconSize = isStacked ? 7 : 10;
        guiGraphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, iconLoc, x - iconSize/2, y, iconSize, iconSize);
    }
    
    private static class VisibleWaypoint {
        ClientSettings.Waypoint wp;
        int x;
        
        public VisibleWaypoint(ClientSettings.Waypoint wp, int x) {
            this.wp = wp;
            this.x = x;
        }
    }
    
    private static void drawEntityMarker(GuiGraphicsExtractor guiGraphics, float playerYaw, float entityYaw, Entity entity, int centerX, int topY) {
        float delta = Mth.wrapDegrees(entityYaw - playerYaw);
        
        if (Math.abs(delta) < VISIBLE_ANGLE / 2.0f) {
            float offset = (delta / (VISIBLE_ANGLE / 2.0f)) * (COMPASS_WIDTH / 2.0f);
            int x = (int)(centerX + offset);
            int y = topY + 1;
            
            if (entity instanceof AbstractClientPlayer p) {
                Identifier skin = p.getSkin().body().texturePath();
                int headSize = 8;
                int drawY = y + 4;
                guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, x - headSize/2, drawY, 8, 8, headSize, headSize, 64, 64);
            } else {
                int color = 0xFFFFFFFF;
                if (entity instanceof Enemy) {
                    color = 0xFFFF0000;
                } else if (entity instanceof Villager) {
                    color = 0xFF00FF00;
                } else {
                    color = 0xFFAAAAAA;
                }
                guiGraphics.fill(x - 2, y + 6, x + 2, y + 10, color);
            }
        }
    }
}
