package com.agustinbenitez.voxelview3d.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SETTINGS_FILE = Path.of("voxelview3d", "settings.json");

    public static void saveSettings() {
        try {
            if (!Files.exists(SETTINGS_FILE.getParent())) {
                Files.createDirectories(SETTINGS_FILE.getParent());
            }

            SettingsData data = new SettingsData();
            data.renderDistance = ClientSettings.renderDistance;
            data.showCoords = ClientSettings.showCoords;
            data.autoDeathPoints = ClientSettings.autoDeathPoints;
            data.showChunkGrid = ClientSettings.showChunkGrid;
            data.showCompass = ClientSettings.showCompass;
            data.showVillagers = ClientSettings.showVillagers;
            data.showAnimals = ClientSettings.showAnimals;
            data.showEnemies = ClientSettings.showEnemies;
            data.showPlayers = ClientSettings.showPlayers;
            data.isTopDownView = ClientSettings.isTopDownView;
            data.hudSize = ClientSettings.hudSize;
            
            try (Writer writer = Files.newBufferedWriter(SETTINGS_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save settings", e);
        }
    }

    public static void loadSettings() {
        if (!Files.exists(SETTINGS_FILE)) return;

        try (Reader reader = Files.newBufferedReader(SETTINGS_FILE)) {
            SettingsData data = GSON.fromJson(reader, SettingsData.class);
            if (data != null) {
                ClientSettings.renderDistance = data.renderDistance;
                ClientSettings.showCoords = data.showCoords;
                ClientSettings.autoDeathPoints = data.autoDeathPoints;
                ClientSettings.showChunkGrid = data.showChunkGrid;
                ClientSettings.showCompass = data.showCompass;
                ClientSettings.showVillagers = data.showVillagers;
                ClientSettings.showAnimals = data.showAnimals;
                ClientSettings.showEnemies = data.showEnemies;
                ClientSettings.showPlayers = data.showPlayers;
                ClientSettings.isTopDownView = data.isTopDownView;
                ClientSettings.hudSize = data.hudSize != null ? data.hudSize : ClientSettings.HudSize.MEDIUM;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load settings", e);
        }
    }

    private static class SettingsData {
        public int renderDistance = 7;
        public boolean showCoords = true;
        public boolean autoDeathPoints = true;
        public boolean showChunkGrid = false;
        public boolean showCompass = true;
        public boolean showVillagers = true;
        public boolean showAnimals = true;
        public boolean showEnemies = true;
        public boolean showPlayers = true;
        public boolean isTopDownView = false;
        public ClientSettings.HudSize hudSize = ClientSettings.HudSize.MEDIUM;
    }
}
