package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

public final class VoxelMapPictureInPictureRenderer extends PictureInPictureRenderer<VoxelMapRenderState> {
    public VoxelMapPictureInPictureRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<VoxelMapRenderState> getRenderStateClass() {
        return VoxelMapRenderState.class;
    }

    @Override
    protected void renderToTexture(VoxelMapRenderState state, PoseStack poseStack) {
        // The vanilla PIP base flips Z for entity previews. This renderer has
        // its own map camera, so cancel that inherited flip or lower terrain
        // wins the depth test over trees, buildings and mountains above it.
        // Also compress depth as zoom grows: the PIP projection has a fixed
        // +/-1000 Z range, while map zoom can reach 15x.
        Minecraft minecraft = Minecraft.getInstance();
        int guiScale = Math.max(1, minecraft.gameRenderer.getGameRenderState().windowRenderState.guiScale);
        int worldHeight = minecraft.level != null ? minecraft.level.getHeight() : 384;
        float horizontalDepth = (state.renderRadius() + 1) * 24.0F;
        float estimatedDepth = (horizontalDepth + worldHeight) * state.zoom() * guiScale;
        float depthCompression = Math.min(1.0F, 800.0F / Math.max(1.0F, estimatedDepth));
        poseStack.scale(1.0F, 1.0F, -depthCompression);
        poseStack.translate(state.panX(), state.panY(), 0.0);
        VoxelMapRenderer.renderMap(
                poseStack,
                state.zoom(),
                state.cameraPitch(),
                state.cameraYaw(),
                false,
                state.renderRadius(),
                state.selectedBlock()
        );
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "voxel_map_3d";
    }
}
