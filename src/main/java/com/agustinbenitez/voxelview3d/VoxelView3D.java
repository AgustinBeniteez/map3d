package com.agustinbenitez.voxelview3d;

import com.agustinbenitez.voxelview3d.client.CompassHud;
import com.agustinbenitez.voxelview3d.client.KeyBindings;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.slf4j.Logger;

@Mod(VoxelView3D.MODID)
public class VoxelView3D {
    public static final String MODID = "voxelview3d";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VoxelView3D(ModContainer container) {
        // Static subscribers below handle all client and mod-bus registration.
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.OPEN_MAP_KEY);
            event.register(KeyBindings.OPEN_WAYPOINTS_LIST_KEY);
            event.register(KeyBindings.CREATE_WAYPOINT_KEY);
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiLayersEvent event) {
            event.registerAbove(
                    VanillaGuiLayers.SLEEP_OVERLAY,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "compass_hud"),
                    CompassHud::render);
        }
    }
}
