package com.agustinbenitez.voxelview3d.client;

import net.minecraft.client.Minecraft;

public class InputHandler {
    private InputHandler() {
    }

    public static void onClientTick(Minecraft client) {
        while (KeyBindings.OPEN_MAP_KEY.consumeClick()) {
            client.setScreen(new VoxelMapScreen());
        }
        while (KeyBindings.OPEN_WAYPOINTS_LIST_KEY.consumeClick()) {
            client.setScreen(new VoxelMapScreen(true));
        }
        while (KeyBindings.CREATE_WAYPOINT_KEY.consumeClick()) {
            client.setScreen(new VoxelMapScreen(true, true));
        }
    }
}
