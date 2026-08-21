package com.agustinbenitez.voxelview3d.client;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("vv3d_add_wp")
                .then(ClientCommandManager.argument("data", StringArgumentType.greedyString())
                    .executes(context -> {
                        String encoded = StringArgumentType.getString(context, "data");
                        addWaypointFromShare(encoded);
                        return 1;
                    })
                )
            )
        );

        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
                replaceShareTag(message));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender,
                                                         params, receptionTimestamp) -> {
            Component modified = replaceShareTag(message);
            if (modified == message) return true;

            Minecraft client = Minecraft.getInstance();
            client.execute(() -> client.gui.getChat().addMessage(modified));
            return false;
        });
    }

    private static Component replaceShareTag(Component message) {
        String msg = message.getString();
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
                        .withClickEvent(new ClickEvent.SuggestCommand("/vv3d_add_wp " + encoded))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("voxelview3d.chat.share_hover")))
                    );
                
                // Replace the tag in the message with the clickable component
                String before = msg.substring(0, matcher.start());
                String after = msg.substring(matcher.end());
                
                return Component.literal(before).append(clickable).append(after);
                
            } catch (Exception e) {
                // Invalid format, ignore
            }
        }
        return message;
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
