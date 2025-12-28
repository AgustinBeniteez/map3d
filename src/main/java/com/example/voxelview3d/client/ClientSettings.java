package com.example.voxelview3d.client;

import java.util.ArrayList;
import java.util.List;

public class ClientSettings {
    public static boolean showVillagers = true;
    public static boolean showAnimals = true;
    public static boolean showEnemies = true;
    public static boolean showPlayers = true;

    public static final List<Waypoint> waypoints = new ArrayList<>();

    public static class Waypoint {
        public String name;
        public int x, y, z;
        public int color;
        public boolean visible = true;

        public Waypoint(String name, int x, int y, int z, int color) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }
    }
}
