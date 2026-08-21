package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector4f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Immediate mesh bridge for Minecraft 1.21.8's GPU render pipelines. */
final class RenderBufferUtil {
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private static final ByteBufferBuilder SORT_ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Map<PipelineKey, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<PipelineKey, MappableRingBuffer> VERTEX_BUFFERS = new HashMap<>();

    private static ResourceLocation texture = TextureAtlas.LOCATION_BLOCKS;
    private static boolean depthTest = true;
    private static boolean cull = true;
    private static boolean additiveBlend;
    private static boolean screenSpace;

    private RenderBufferUtil() {
    }

    static void setTexture(ResourceLocation location) {
        if (location != null) texture = location;
    }

    static void setDepthTest(boolean enabled) {
        depthTest = enabled;
    }

    static void setCull(boolean enabled) {
        cull = enabled;
    }

    static void setAdditiveBlend(boolean enabled) {
        additiveBlend = enabled;
    }

    static void setColor(float red, float green, float blue, float alpha) {
        COLOR_MODULATOR.set(red, green, blue, alpha);
    }

    static void setScreenSpace(boolean enabled) {
        screenSpace = enabled;
    }

    static void clearDepth() {
        Minecraft minecraft = Minecraft.getInstance();
        RenderSystem.getDevice().createCommandEncoder()
                .clearDepthTexture(minecraft.getMainRenderTarget().getDepthTexture(), 1.0);
    }

    static void resetState() {
        texture = TextureAtlas.LOCATION_BLOCKS;
        depthTest = true;
        cull = true;
        additiveBlend = false;
        COLOR_MODULATOR.set(1.0f, 1.0f, 1.0f, 1.0f);
    }

    static void drawIfNotEmpty(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh == null) return;

        MeshData.DrawState drawState = mesh.drawState();
        VertexFormat format = drawState.format();
        boolean textured = format == DefaultVertexFormat.POSITION_TEX
                || format == DefaultVertexFormat.POSITION_TEX_COLOR;
        PipelineKey key = new PipelineKey(format, drawState.mode(), textured,
                depthTest, cull, additiveBlend);
        RenderPipeline pipeline = PIPELINES.computeIfAbsent(key, RenderBufferUtil::createPipeline);

        int vertexBytes = drawState.vertexCount() * format.getVertexSize();
        MappableRingBuffer vertexBuffer = VERTEX_BUFFERS.get(key);
        if (vertexBuffer == null || vertexBuffer.size() < vertexBytes) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(
                    () -> "voxelview3d " + pipeline.getLocation(),
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    Math.max(vertexBytes, 256));
            VERTEX_BUFFERS.put(key, vertexBuffer);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mapped = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), mapped.data());
        }

        GpuBuffer indices;
        VertexFormat.IndexType indexType;
        if (drawState.mode() == VertexFormat.Mode.QUADS) {
            mesh.sortQuads(SORT_ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = format.uploadImmediateIndexBuffer(mesh.indexBuffer());
            indexType = mesh.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(drawState.mode());
            indices = sequential.getBuffer(drawState.indexCount());
            indexType = sequential.type();
        }

        Minecraft minecraft = Minecraft.getInstance();
        GpuTextureView textureView = textured
                ? minecraft.getTextureManager().getTexture(texture).getTextureView()
                : null;
        // Minecraft 1.21.8 renders GUI geometry in an orthographic depth range
        // using a -11000 Z model-view translation. Our immediate 3D map must
        // use the same GUI space or half of the pitched map is clipped away.
        Matrix4f modelView = screenSpace
                ? new Matrix4f().setTranslation(0.0f, 0.0f, -11000.0f)
                : RenderSystem.getModelViewMatrix();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView, COLOR_MODULATOR, RenderSystem.getModelOffset(),
                RenderSystem.getTextureMatrix(), 1.0f);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "voxelview3d immediate map",
                minecraft.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                minecraft.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            if (textured) {
                pass.bindSampler("Sampler0", textureView);
            }
            pass.setVertexBuffer(0, vertexBuffer.currentBuffer());
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        } finally {
            mesh.close();
            vertexBuffer.rotate();
        }
    }

    private static RenderPipeline createPipeline(PipelineKey key) {
        String shader;
        if (key.format == DefaultVertexFormat.POSITION_COLOR) {
            shader = "core/position_color";
        } else if (key.format == DefaultVertexFormat.POSITION_TEX_COLOR) {
            shader = "core/position_tex_color";
        } else if (key.format == DefaultVertexFormat.POSITION_TEX) {
            shader = "core/position_tex";
        } else {
            throw new IllegalArgumentException("Unsupported VoxelView vertex format: " + key.format);
        }

        String id = "pipeline/immediate_" + Integer.toUnsignedString(key.hashCode(), 36);
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(ResourceLocation.fromNamespaceAndPath("voxelview3d", id))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(key.format, key.mode)
                .withDepthTestFunction(key.depthTest
                        ? DepthTestFunction.LEQUAL_DEPTH_TEST
                        : DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(key.depthTest)
                .withCull(key.cull)
                .withBlend(key.additive ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT);
        if (key.textured) builder.withSampler("Sampler0");
        return builder.build();
    }

    private record PipelineKey(VertexFormat format, VertexFormat.Mode mode, boolean textured,
                               boolean depthTest, boolean cull, boolean additive) {
    }
}
