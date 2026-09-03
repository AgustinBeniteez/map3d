package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.VoxelView3D;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = VoxelView3D.MODID, value = Dist.CLIENT)
public class InputHandler {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (KeyBindings.OPEN_MAP_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new VoxelMapScreen());
        }
        while (KeyBindings.OPEN_WAYPOINTS_LIST_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new VoxelMapScreen(true));
        }
        while (KeyBindings.CREATE_WAYPOINT_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new VoxelMapScreen(true, true));
        }
    }
}
