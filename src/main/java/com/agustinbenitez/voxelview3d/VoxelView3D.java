package com.agustinbenitez.voxelview3d;

import com.agustinbenitez.voxelview3d.client.CompassHud;
import com.agustinbenitez.voxelview3d.client.InputHandler;
import com.agustinbenitez.voxelview3d.client.KeyBindings;
import com.agustinbenitez.voxelview3d.client.WaypointEventHandler;
import com.agustinbenitez.voxelview3d.client.WaypointSharingHandler;
import com.agustinbenitez.voxelview3d.client.WorldWaypointRenderer;
import com.agustinbenitez.voxelview3d.world.WorldHandler;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.slf4j.Logger;

public final class VoxelView3D implements ClientModInitializer {
    public static final String MODID = "voxelview3d";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(KeyBindings.OPEN_MAP_KEY);
        KeyBindingHelper.registerKeyBinding(KeyBindings.OPEN_WAYPOINTS_LIST_KEY);
        KeyBindingHelper.registerKeyBinding(KeyBindings.CREATE_WAYPOINT_KEY);

        HudRenderCallback.EVENT.register(CompassHud::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldWaypointRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            InputHandler.onClientTick(client);
            WaypointEventHandler.onClientTick(client);
            WorldHandler.onClientTick(client);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                WaypointEventHandler.onPlayerLogIn());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                WaypointEventHandler.onPlayerLogOut());

        WaypointSharingHandler.register();
        LOGGER.info("Map 3d usseewa initialized on Fabric");
    }
}
