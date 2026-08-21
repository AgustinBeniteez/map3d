package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.world.WorldHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class WaypointEventHandler {
    private static boolean wasDead;

    private WaypointEventHandler() {
    }

    public static void onPlayerLogOut() {
        WaypointManager.saveWaypoints();
        ClientSettings.waypoints.clear();
        wasDead = false;
        ClientMapData.getInstance().clearCache();
        WorldHandler.reset();
    }

    public static void onPlayerLogIn() {
        ClientSettings.waypoints.clear();
        WaypointManager.loadWaypoints();
        wasDead = false;
        ClientMapData.getInstance().clearCache();
        WorldHandler.reset();
    }

    public static void onClientTick(Minecraft client) {
        if (ClientSettings.autoDeathPoints
                && client.screen instanceof DeathScreen
                && !wasDead
                && client.player != null) {
            createDeathWaypoint(client);
        }

        if (wasDead && client.player != null && client.player.isAlive()
                && !(client.screen instanceof DeathScreen)) {
            wasDead = false;
        }
    }

    private static void createDeathWaypoint(Minecraft client) {
        wasDead = true;

        int x = client.player.getBlockX();
        int y = client.player.getBlockY();
        int z = client.player.getBlockZ();
        String dimension = client.player.level().dimension().location().toString();
        String lastDeathName = Component.translatable("voxelview3d.waypoint.last_death").getString();

        boolean alreadyExistsAtLocation = false;
        for (ClientSettings.Waypoint waypoint : ClientSettings.waypoints) {
            if (!waypoint.name.equals(lastDeathName)) continue;

            if (waypoint.x == x && waypoint.y == y && waypoint.z == z
                    && waypoint.getDimension().equals(dimension)) {
                alreadyExistsAtLocation = true;
                break;
            }

            String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(LocalDateTime.now());
            String deathPrefix = Component.translatable("voxelview3d.waypoint.death").getString();
            waypoint.name = deathPrefix + " " + time;
            break;
        }

        if (alreadyExistsAtLocation) return;

        ClientSettings.waypoints.add(new ClientSettings.Waypoint(
                lastDeathName, x, y, z, 0x555555, "dead", dimension));
        WaypointManager.saveWaypoints();
        client.player.displayClientMessage(
                Component.translatable("voxelview3d.waypoint.death_created", x, y, z), false);
    }
}
