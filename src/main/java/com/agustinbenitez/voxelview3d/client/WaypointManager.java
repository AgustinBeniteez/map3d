package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.client.ClientSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class WaypointManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path BASE_DIR = Path.of("voxelview3d", "waypoints");

    public static void saveWaypoints() {
        String worldId = getCurrentWorldId();
        if (worldId == null) return;

        try {
            if (!Files.exists(BASE_DIR)) {
                Files.createDirectories(BASE_DIR);
            }

            Path file = BASE_DIR.resolve(sanitize(worldId) + ".json");
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(ClientSettings.waypoints, writer);
            }
            LOGGER.info("Saved {} waypoints for world '{}'", ClientSettings.waypoints.size(), worldId);
        } catch (IOException e) {
            LOGGER.error("Failed to save waypoints", e);
        }
    }

    public static void loadWaypoints() {
        ClientSettings.waypoints.clear();
        String worldId = getCurrentWorldId();
        if (worldId == null) return;

        try {
            if (!Files.exists(BASE_DIR)) {
                Files.createDirectories(BASE_DIR);
            }
            
            Path file = BASE_DIR.resolve(sanitize(worldId) + ".json");
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    List<ClientSettings.Waypoint> loaded = GSON.fromJson(reader, new TypeToken<List<ClientSettings.Waypoint>>(){}.getType());
                    if (loaded != null) {
                        ClientSettings.waypoints.addAll(loaded);
                    }
                    LOGGER.info("Loaded {} waypoints for world '{}'", ClientSettings.waypoints.size(), worldId);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load waypoints", e);
        }
    }

    private static String getCurrentWorldId() {
        Minecraft mc = Minecraft.getInstance();
        
        // Check for Singleplayer (Integrated Server) first
        if (mc.hasSingleplayerServer()) {
             // Use reflection to get storageSource (protected)
             try {
                 java.lang.reflect.Field f = net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");
                 f.setAccessible(true);
                 net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess access = 
                     (net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess) f.get(mc.getSingleplayerServer());
                 return "sp_" + access.getLevelId();
             } catch (Exception e) {
                 LOGGER.error("Failed to get world ID via reflection", e);
                 // Fallback to display name (might not be unique but better than crash)
                 return "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
             }
        }
        
        // Check for Multiplayer
        if (mc.getCurrentServer() != null) {
            ServerData server = mc.getCurrentServer();
            return "mp_" + server.ip;
        }
        
        return null;
    }
    
    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
