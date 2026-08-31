package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

final class RenderBufferUtil {
    private static final RenderPipeline COLOR_DEPTH_PIPELINE = colorPipeline(
            "position_color_depth", BlendFunction.TRANSLUCENT,
            DepthStencilState.DEFAULT, true, VertexFormat.Mode.QUADS);
    private static final RenderPipeline COLOR_DEPTH_NO_CULL_PIPELINE = colorPipeline(
            "position_color_depth_no_cull", BlendFunction.TRANSLUCENT,
            DepthStencilState.DEFAULT, false, VertexFormat.Mode.QUADS);
    private static final RenderPipeline COLOR_SEE_THROUGH_PIPELINE = colorPipeline(
            "position_color_see_through", BlendFunction.TRANSLUCENT,
            new DepthStencilState(CompareOp.ALWAYS_PASS, false), false, VertexFormat.Mode.QUADS);
    private static final RenderPipeline BEAM_PIPELINE = colorPipeline(
            "waypoint_beam", BlendFunction.LIGHTNING,
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false), false, VertexFormat.Mode.QUADS);
    private static final RenderPipeline LINES_DEPTH_PIPELINE = colorPipeline(
            "position_color_lines_depth", BlendFunction.TRANSLUCENT,
            DepthStencilState.DEFAULT, false, VertexFormat.Mode.DEBUG_LINES);

    private static final RenderPipeline TEXTURED_COLOR_DEPTH_PIPELINE = texturedPipeline(
            "position_tex_color_depth", DefaultVertexFormat.POSITION_TEX_COLOR,
            "core/position_tex_color", DepthStencilState.DEFAULT, true);
    private static final RenderPipeline TEXTURED_COLOR_SEE_THROUGH_PIPELINE = texturedPipeline(
            "position_tex_color_see_through", DefaultVertexFormat.POSITION_TEX_COLOR,
            "core/position_tex_color", new DepthStencilState(CompareOp.ALWAYS_PASS, false), false);
    private static final RenderPipeline TEXTURED_DEPTH_PIPELINE = texturedPipeline(
            "position_tex_depth", DefaultVertexFormat.POSITION_TEX,
            "core/position_tex", DepthStencilState.DEFAULT, true);
    private static final RenderPipeline TEXTURED_SEE_THROUGH_PIPELINE = texturedPipeline(
            "position_tex_see_through", DefaultVertexFormat.POSITION_TEX,
            "core/position_tex", new DepthStencilState(CompareOp.ALWAYS_PASS, false), false);

    private static final RenderType COLOR_DEPTH = renderType("position_color_depth", COLOR_DEPTH_PIPELINE);
    private static final RenderType COLOR_DEPTH_NO_CULL = renderType("position_color_depth_no_cull", COLOR_DEPTH_NO_CULL_PIPELINE);
    private static final RenderType COLOR_SEE_THROUGH = renderType("position_color_see_through", COLOR_SEE_THROUGH_PIPELINE);
    private static final RenderType BEAM = renderType("waypoint_beam", BEAM_PIPELINE);
    private static final RenderType LINES_DEPTH = renderType("position_color_lines_depth", LINES_DEPTH_PIPELINE);

    private static final Map<Identifier, RenderType> TEXTURED_COLOR_DEPTH = new HashMap<>();
    private static final Map<Identifier, RenderType> TEXTURED_COLOR_SEE_THROUGH = new HashMap<>();
    private static final Map<Identifier, RenderType> TEXTURED_DEPTH = new HashMap<>();
    private static final Map<Identifier, RenderType> TEXTURED_SEE_THROUGH = new HashMap<>();

    private RenderBufferUtil() {
    }

    static void drawIfNotEmpty(BufferBuilder buffer) {
        drawIfNotEmpty(buffer, COLOR_DEPTH);
    }

    static void drawColorNoCull(BufferBuilder buffer) {
        drawIfNotEmpty(buffer, COLOR_DEPTH_NO_CULL);
    }

    static void drawColorSeeThrough(BufferBuilder buffer) {
        drawIfNotEmpty(buffer, COLOR_SEE_THROUGH);
    }

    static void drawBeam(BufferBuilder buffer) {
        drawIfNotEmpty(buffer, BEAM);
    }

    static void drawLines(BufferBuilder buffer) {
        drawIfNotEmpty(buffer, LINES_DEPTH);
    }

    static void drawTexturedColor(BufferBuilder buffer, Identifier texture) {
        drawIfNotEmpty(buffer, texturedType(TEXTURED_COLOR_DEPTH, "position_tex_color_depth", TEXTURED_COLOR_DEPTH_PIPELINE, texture));
    }

    static void drawTexturedColorSeeThrough(BufferBuilder buffer, Identifier texture) {
        drawIfNotEmpty(buffer, texturedType(TEXTURED_COLOR_SEE_THROUGH, "position_tex_color_see_through", TEXTURED_COLOR_SEE_THROUGH_PIPELINE, texture));
    }

    static void drawTextured(BufferBuilder buffer, Identifier texture) {
        drawIfNotEmpty(buffer, texturedType(TEXTURED_DEPTH, "position_tex_depth", TEXTURED_DEPTH_PIPELINE, texture));
    }

    static void drawTexturedSeeThrough(BufferBuilder buffer, Identifier texture) {
        drawIfNotEmpty(buffer, texturedType(TEXTURED_SEE_THROUGH, "position_tex_see_through", TEXTURED_SEE_THROUGH_PIPELINE, texture));
    }

    private static void drawIfNotEmpty(BufferBuilder buffer, RenderType type) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            type.draw(mesh);
        }
    }

    private static RenderPipeline colorPipeline(String path, BlendFunction blend,
                                                DepthStencilState depth, boolean cull,
                                                VertexFormat.Mode mode) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("voxelview3d", "pipeline/" + path))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(new ColorTargetState(blend))
                .withCull(cull)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                .withDepthStencilState(depth)
                .build();
    }

    private static RenderPipeline texturedPipeline(String path, VertexFormat format,
                                                   String shader, DepthStencilState depth,
                                                   boolean cull) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("voxelview3d", "pipeline/" + path))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(cull)
                .withVertexFormat(format, VertexFormat.Mode.QUADS)
                .withDepthStencilState(depth)
                .build();
    }

    private static RenderType renderType(String name, RenderPipeline pipeline) {
        return RenderType.create(name, RenderSetup.builder(pipeline).createRenderSetup());
    }

    private static RenderType texturedType(Map<Identifier, RenderType> cache, String name,
                                           RenderPipeline pipeline, Identifier texture) {
        return cache.computeIfAbsent(texture, key -> RenderType.create(
                name,
                RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", key)
                        .createRenderSetup()
        ));
    }
}
