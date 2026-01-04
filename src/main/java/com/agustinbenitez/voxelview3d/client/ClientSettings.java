package com.agustinbenitez.voxelview3d.client;

import java.util.ArrayList;
import java.util.List;

public class ClientSettings {
    public static boolean showVillagers = true;
    public static boolean showAnimals = true;
    public static boolean showEnemies = true;
    public static boolean showPlayers = true;
    public static boolean showCompass = true;
    
    // New settings
    public static int renderDistance = 5;
    public static boolean showCoords = true;
    public static boolean autoDeathPoints = true;
    public static boolean fullBrightMap = false;
    public static boolean showChunkGrid = false;
    public static boolean isNightMode = false;
    public static boolean isTopDownView = false;
    
    public enum HudSize {
        SMALL, MEDIUM, LARGE
    }
    public static HudSize hudSize = HudSize.MEDIUM;

    public static final List<Waypoint> waypoints = new ArrayList<>();

    public static class Waypoint {
        public String name;
        public int x, y, z;
        public int color;
        public String iconName; // e.g. "icon1"
        public boolean visible = true;
        public String dimension; // e.g. "minecraft:overworld"

        public Waypoint() {}

        public Waypoint(String name, int x, int y, int z, int color, String iconName, String dimension) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.iconName = (iconName == null || iconName.isEmpty()) ? "icon1" : iconName;
            this.dimension = (dimension == null || dimension.isEmpty()) ? "minecraft:overworld" : dimension;
        }
        
        // Backward compatibility constructor
        public Waypoint(String name, int x, int y, int z, int color, String iconName) {
            this(name, x, y, z, color, iconName, "minecraft:overworld");
        }
        
        public String getDimension() {
            return (dimension == null || dimension.isEmpty()) ? "minecraft:overworld" : dimension;
        }
    }
}
