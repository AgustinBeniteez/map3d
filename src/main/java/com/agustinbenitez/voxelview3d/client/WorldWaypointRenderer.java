package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;
import org.joml.Matrix4f;

public class WorldWaypointRenderer {
    public static void registerEvents() {
        net.minecraftforge.client.event.AddFramePassEvent.BUS.addListener(WorldWaypointRenderer::onAddFramePasses);
    }


    
    public static void onAddFramePasses(AddFramePassEvent event) {
        event.addPass(Identifier.fromNamespaceAndPath("voxelview3d", "waypoint_pass"), new FramePassManager.PassDefinition() {
            @Override
            public void extracts(net.minecraft.client.renderer.LevelTargetBundle bundle, com.mojang.blaze3d.framegraph.FramePass pass) {
                pass.readsAndWrites(bundle.main);
            }

            @Override
            public void executes(LevelRenderState state) {
                renderWaypoints(state);
            }
        });
    }

    private static void renderWaypoints(LevelRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientSettings.waypoints.isEmpty()) return;

        Vec3 cameraPos = state.cameraRenderState.pos;

        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;
            if (mc.player.level() != null && !wp.getDimension().equals(mc.player.level().dimension().identifier().toString())) continue;
            double rx = wp.x + 0.5 - cameraPos.x;
            double rz = wp.z + 0.5 - cameraPos.z;
            double bottomY = wp.y - cameraPos.y;
            double distSq = rx * rx + rz * rz;
            float alpha = 0.2f;

            if (distSq < 9.0) {
                alpha = (float)(Math.sqrt(distSq) / 3.0) * 0.2f;
                if (alpha < 0.05f) alpha = 0.05f;
            }

            int color = "dead".equals(wp.iconName) ? 0x555555 : wp.color;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            VoxelMapRenderer.renderBox(buf, new Matrix4f(), rx, bottomY, rz,
                    0.2f, 2048.0f, 0.2f, r, g, b, alpha);
            // The level frame graph already installed the camera model-view
            // matrix. Multiplying it again makes every beam fan out from the camera.
            RenderBufferUtil.drawBeam(buf);
        }
    }
}
