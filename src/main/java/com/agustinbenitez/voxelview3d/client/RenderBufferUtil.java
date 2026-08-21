package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;

final class RenderBufferUtil {
    private RenderBufferUtil() {
    }

    static void drawIfNotEmpty(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}
