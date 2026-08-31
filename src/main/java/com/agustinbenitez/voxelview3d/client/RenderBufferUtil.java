package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

final class RenderBufferUtil {
    private RenderBufferUtil() {
    }

    static void drawIfNotEmpty(BufferBuilder buffer) {
        drawIfNotEmpty(buffer, RenderTypes.translucentMovingBlock());
    }

    static void drawIfNotEmpty(BufferBuilder buffer, RenderType type) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            type.draw(mesh);
        }
    }
}
