package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.VoxelView3D;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = VoxelView3D.MODID, value = Dist.CLIENT)
public class WaypointSharingHandler {
    private static final Gson GSON = new Gson();
    private static final String PREFIX = "[VV3D-WP:";
    private static final String SUFFIX = "]";
    private static final Pattern PATTERN = Pattern.compile("\\[VV3D-WP:(.*?)\\]");

    public static String createShareMessage(ClientSettings.Waypoint wp) {
        String json = GSON.toJson(wp);
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded + SUFFIX;
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("vv3d_add_wp")
                .then(Commands.argument("data", StringArgumentType.greedyString())
                    .executes(context -> {
                        String encoded = StringArgumentType.getString(context, "data");
                        addWaypointFromShare(encoded);
                        return 1;
                    })
                )
        );
    }

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String msg = event.getMessage();
        if (msg.startsWith("/vv3d_add_wp ")) {
            event.setCanceled(true);
            String encoded = msg.substring(13);
            addWaypointFromShare(encoded);
            // Optionally add to history so user can recall it if needed
            Minecraft.getInstance().gui.getChat().addRecentChat(msg);
        }
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();
        Matcher matcher = PATTERN.matcher(msg);
        if (matcher.find()) {
            String encoded = matcher.group(1);
            try {
                String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                ClientSettings.Waypoint wp = GSON.fromJson(json, ClientSettings.Waypoint.class);
                
                Component clickable = Component.translatable("voxelview3d.chat.share_text", wp.name)
                    .setStyle(Style.EMPTY
                        .withColor(wp.color)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/vv3d_add_wp " + encoded))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("voxelview3d.chat.share_hover")))
                    );
                
                // Replace the tag in the message with the clickable component
                String before = msg.substring(0, matcher.start());
                
                Component newMsg = Component.literal(before).append(clickable);
                event.setMessage(newMsg);
                
            } catch (Exception e) {
                // Invalid format, ignore
            }
        }
    }

    private static void addWaypointFromShare(String encoded) {
        try {
            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            ClientSettings.Waypoint wp = GSON.fromJson(json, ClientSettings.Waypoint.class);
            
            boolean exists = ClientSettings.waypoints.stream().anyMatch(w -> 
                w.x == wp.x && w.y == wp.y && w.z == wp.z && w.name.equals(wp.name));
            
            if (!exists) {
                ClientSettings.waypoints.add(wp);
                WaypointManager.saveWaypoints();
                Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("voxelview3d.chat.waypoint_added", wp.name), false);
            } else {
                 Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("voxelview3d.chat.waypoint_exists", wp.name), false);
            }
                
        } catch (Exception e) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("Error adding waypoint").withStyle(net.minecraft.ChatFormatting.RED), false);
            }
        }
    }
}
