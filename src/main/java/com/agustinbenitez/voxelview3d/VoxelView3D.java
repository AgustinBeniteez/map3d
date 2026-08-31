package com.agustinbenitez.voxelview3d;

import com.agustinbenitez.voxelview3d.client.*;
import com.agustinbenitez.voxelview3d.world.WorldHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(VoxelView3D.MODID)
public class VoxelView3D {
    public static final String MODID = "voxelview3d";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VoxelView3D(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RegisterKeyMappingsEvent.BUS.addListener(this::registerKeys);
            AddGuiOverlayLayersEvent.BUS.addListener(this::registerOverlays);
            RegisterPictureInPictureRendererEvent.BUS.addListener(this::registerPictureInPictureRenderers);

            // Register client tick & event handlers
            InputHandler.registerEvents();
            WaypointEventHandler.registerEvents();
            WorldHandler.registerEvents();
            WaypointSharingHandler.registerEvents();
            WorldWaypointRenderer.registerEvents();
        }
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_MAP_KEY);
        event.register(KeyBindings.OPEN_WAYPOINTS_LIST_KEY);
        event.register(KeyBindings.CREATE_WAYPOINT_KEY);
    }

    private void registerOverlays(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                ForgeLayeredDraw.POST_SLEEP_STACK,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(MODID, "compass_hud"),
                CompassHud::render);
    }

    private void registerPictureInPictureRenderers(RegisterPictureInPictureRendererEvent event) {
        event.register(new VoxelMapPictureInPictureRenderer(event.getBufferSource()));
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("voxelview3d Client Setup initialized");
        }
    }
}
