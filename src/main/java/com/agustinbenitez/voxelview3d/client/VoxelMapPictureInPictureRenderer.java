package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
