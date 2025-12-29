package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.VoxelView3D;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VoxelView3D.MODID, value = Dist.CLIENT)
public class InputHandler {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
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
}
