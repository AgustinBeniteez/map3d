package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public class ChunkMesh implements AutoCloseable {
    private final VertexBuffer vertexBuffer;
    private boolean uploaded = false;

    public ChunkMesh() {
        this.vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
    }

    public void upload(BufferBuilder.RenderedBuffer buffer) {
        this.vertexBuffer.bind();
        this.vertexBuffer.upload(buffer);
        VertexBuffer.unbind();
        this.uploaded = true;
    }

    public void draw(Matrix4f matrix, Matrix4f projectionMatrix) {
        if (uploaded) {
            this.vertexBuffer.bind();
            this.vertexBuffer.drawWithShader(matrix, projectionMatrix, GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();
        }
    }

    @Override
    public void close() {
        this.vertexBuffer.close();
    }
}
