package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.VoxelView3D;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Mod.EventBusSubscriber(modid = VoxelView3D.MODID, value = Dist.CLIENT)
public class WaypointEventHandler {

    private static boolean wasDead = false;

    @SubscribeEvent
    public static void onPlayerLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WaypointManager.saveWaypoints();
        // Clear waypoints on logout to avoid mixing servers
        ClientSettings.waypoints.clear();
        wasDead = false;
    }
    
    @SubscribeEvent
    public static void onPlayerLogIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // Clear previous waypoints before loading new ones
        ClientSettings.waypoints.clear();
        WaypointManager.loadWaypoints();
        wasDead = false;

        // Clear map cache and world scanner state to prevent data bleeding between worlds
        ClientMapData.getInstance().clearCache();
        com.agustinbenitez.voxelview3d.world.WorldHandler.reset();
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (!ClientSettings.autoDeathPoints) return;
        
        if (event.getScreen() instanceof DeathScreen && !wasDead) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                wasDead = true;
                int x = mc.player.getBlockX();
                int y = mc.player.getBlockY();
                int z = mc.player.getBlockZ();
                String dimension = mc.player.level().dimension().location().toString();
                
                String lastDeathName = Component.translatable("voxelview3d.waypoint.last_death").getString();
                
                // Check for existing "Last Death" and handle it
                boolean alreadyExistsAtLocation = false;
                for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
                    if (wp.name.equals(lastDeathName)) {
                        // If location matches, assume it's the same death event (e.g. relogin)
                        if (wp.x == x && wp.y == y && wp.z == z && wp.getDimension().equals(dimension)) {
                            alreadyExistsAtLocation = true;
                            break;
                        }
                        
                        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now());
                        String deathPrefix = Component.translatable("voxelview3d.waypoint.death").getString();
                        wp.name = deathPrefix + " " + time;
                        break; // Only one should exist
                    }
                }
                
                if (!alreadyExistsAtLocation) {
                    // Create new "Last Death" waypoint (Gray color: 0x555555)
                    // Icon "dead" maps to dead.png
                    ClientSettings.waypoints.add(new ClientSettings.Waypoint(lastDeathName, x, y, z, 0x555555, "dead", dimension)); 
                    WaypointManager.saveWaypoints();
                    
                    mc.player.displayClientMessage(Component.translatable("voxelview3d.waypoint.death_created", x, y, z), false);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            // Reset wasDead flag when player respawns (is alive and no longer in DeathScreen)
            if (wasDead && mc.player != null && mc.player.isAlive() && !(mc.screen instanceof DeathScreen)) {
                wasDead = false;
            }
        }
    }
}
