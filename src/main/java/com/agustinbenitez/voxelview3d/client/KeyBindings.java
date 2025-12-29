package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String CATEGORY = "key.categories.voxelview3d";
    public static final String OPEN_MAP = "key.voxelview3d.open_map";
    public static final String OPEN_WAYPOINTS_LIST = "key.voxelview3d.open_waypoints_list";
    public static final String CREATE_WAYPOINT = "key.voxelview3d.create_waypoint";

    public static final KeyMapping OPEN_MAP_KEY = new KeyMapping(
            OPEN_MAP,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
    );

    public static final KeyMapping OPEN_WAYPOINTS_LIST_KEY = new KeyMapping(
            OPEN_WAYPOINTS_LIST,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY
    );

    public static final KeyMapping CREATE_WAYPOINT_KEY = new KeyMapping(
            CREATE_WAYPOINT,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_ADD, // Numpad +
            CATEGORY
    );
}
