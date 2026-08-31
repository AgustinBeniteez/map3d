package com.agustinbenitez.voxelview3d.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
import org.joml.Vector4f;

public final class CompassHud {

    private CompassHud() {
    }
    
    private static final int COMPASS_WIDTH = 150;
    private static final int COMPASS_HEIGHT = 12;
    private static final int VISIBLE_ANGLE = 100; // Degrees visible in the bar
    private static final double MAX_ENTITY_DISTANCE = 50.0; // Blocks

    public static void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        renderWorldWaypointLabels(guiGraphics, mc);
        if (!ClientSettings.showCompass) return;

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
        
        // In MC 26.1, enableScissor expects GUI coordinates, not raw pixel window coordinates!
        int scissorX = centerX - COMPASS_WIDTH / 2;
        int scissorY = topY;
        int scissorW = COMPASS_WIDTH;
        int scissorH = COMPASS_HEIGHT;
        
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
        // Waypoint icons are standalone PNGs. blitSprite() looks them up in the
        // GUI sprite atlas and produced the magenta/black missing-sprite marker.
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, iconLoc, x - iconSize / 2, y,
                0, 0, iconSize, iconSize, 16, 16);
    }

    /**
     * World labels are extracted as normal GUI elements in 26.1. Rendering Font
     * buffers directly from the late level frame pass is no longer reliable.
     */
    private static void renderWorldWaypointLabels(GuiGraphicsExtractor graphics, Minecraft mc) {
        CameraRenderState camera = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        if (!camera.initialized) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        List<ProjectedWaypoint> projectedWaypoints = new ArrayList<>();

        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible || !wp.getDimension().equals(mc.level.dimension().identifier().toString())) continue;

            double worldX = wp.x + 0.5;
            double worldY = wp.y + 2.5;
            double worldZ = wp.z + 0.5;

            Vector4f projected = new Vector4f(
                    (float)(worldX - camera.pos.x),
                    (float)(worldY - camera.pos.y),
                    (float)(worldZ - camera.pos.z),
                    1.0f);
            camera.viewRotationMatrix.transform(projected);
            camera.projectionMatrix.transform(projected);

            if (projected.w <= 0.001f) continue;

            float ndcX = projected.x / projected.w;
            float ndcY = projected.y / projected.w;
            if (ndcX < -1.05f || ndcX > 1.05f || ndcY < -1.05f || ndcY > 1.05f) continue;

            int screenX = Math.round((ndcX * 0.5f + 0.5f) * screenWidth);
            int screenY = Math.round((0.5f - ndcY * 0.5f) * screenHeight);
            double dx = worldX - camera.pos.x;
            double dy = wp.y - camera.pos.y;
            double dz = worldZ - camera.pos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            projectedWaypoints.add(new ProjectedWaypoint(wp, screenX, screenY, distance));
        }

        // Draw distant labels first. A nearby waypoint remains readable when two
        // projected positions overlap.
        projectedWaypoints.sort(Comparator.comparingDouble((ProjectedWaypoint p) -> p.distance).reversed());
        for (ProjectedWaypoint projected : projectedWaypoints) {
            drawWorldWaypointLabel(graphics, mc, projected);
        }
    }

    private static void drawWorldWaypointLabel(GuiGraphicsExtractor graphics, Minecraft mc, ProjectedWaypoint projected) {
        ClientSettings.Waypoint wp = projected.waypoint;
        Identifier icon = Identifier.fromNamespaceAndPath("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
        String distanceText = String.format("%.0fm", projected.distance);

        int iconSize = 16;
        int iconY = projected.y - 29;
        int nameY = projected.y - 11;
        int distanceY = projected.y;
        int nameWidth = mc.font.width(wp.name);
        int distanceWidth = mc.font.width(distanceText);

        graphics.fill(projected.x - nameWidth / 2 - 2, nameY - 1,
                projected.x + (nameWidth + 1) / 2 + 2, nameY + 9, 0x70000000);
        graphics.fill(projected.x - distanceWidth / 2 - 2, distanceY - 1,
                projected.x + (distanceWidth + 1) / 2 + 2, distanceY + 9, 0x70000000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, projected.x - iconSize / 2, iconY,
                0, 0, iconSize, iconSize, 16, 16);
        graphics.text(mc.font, wp.name, projected.x - nameWidth / 2, nameY, 0xFFFFFFFF, true);
        graphics.text(mc.font, distanceText, projected.x - distanceWidth / 2, distanceY, 0xFFAAAAAA, true);
    }

    private static final class ProjectedWaypoint {
        private final ClientSettings.Waypoint waypoint;
        private final int x;
        private final int y;
        private final double distance;

        private ProjectedWaypoint(ClientSettings.Waypoint waypoint, int x, int y, double distance) {
            this.waypoint = waypoint;
            this.x = x;
            this.y = y;
            this.distance = distance;
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
