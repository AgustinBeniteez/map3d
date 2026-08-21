package com.agustinbenitez.voxelview3d.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

public class InputHandler {
    private static boolean autoTestOpened;
    private static boolean autoTestScreenshot;
    private static int autoTestTicks;

    private InputHandler() {
    }

    public static void onClientTick(Minecraft client) {
        if (!autoTestOpened && Boolean.getBoolean("voxelview3d.autotest")
                && client.player != null && client.level != null && client.screen == null) {
            autoTestOpened = true;
            client.setScreen(new VoxelMapScreen());
        }
        if (autoTestOpened && !autoTestScreenshot && client.screen instanceof VoxelMapScreen
                && ++autoTestTicks >= 120) {
            autoTestScreenshot = true;
            Screenshot.grab(client.gameDirectory, client.getMainRenderTarget(), component -> { });
        }
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
