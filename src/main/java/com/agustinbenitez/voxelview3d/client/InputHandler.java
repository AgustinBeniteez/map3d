package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.VoxelView3D;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


public class InputHandler {
    public static void registerEvents() {
        net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(InputHandler::onClientTick);
    }

    
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
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
