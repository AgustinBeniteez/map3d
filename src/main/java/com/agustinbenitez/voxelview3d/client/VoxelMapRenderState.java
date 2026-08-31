package com.agustinbenitez.voxelview3d.client;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public record VoxelMapRenderState(
        float zoom,
        float cameraPitch,
        float cameraYaw,
        double panX,
        double panY,
        int renderRadius,
        @Nullable BlockPos selectedBlock,
        int x0,
        int y0,
        int x1,
        int y1,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public VoxelMapRenderState(
            float zoom,
            float cameraPitch,
            float cameraYaw,
            double panX,
            double panY,
            int renderRadius,
            @Nullable BlockPos selectedBlock,
            int x0,
            int y0,
            int x1,
            int y1,
            @Nullable ScreenRectangle scissorArea
    ) {
        this(zoom, cameraPitch, cameraYaw, panX, panY, renderRadius, selectedBlock,
                x0, y0, x1, y1, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }

    @Override
    public float scale() {
        return 1.0F;
    }
}
