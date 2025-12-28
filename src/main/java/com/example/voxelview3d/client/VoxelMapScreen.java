package com.example.voxelview3d.client;

import com.example.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;
import org.joml.Matrix4f;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class VoxelMapScreen extends Screen {
    private final Map<ChunkPos, ChunkMesh> meshCache = new HashMap<>();
    
    // Camera controls
    private double camX = 0;
    private double camZ = 0;
    private float zoom = 3.0f; // Start closer
    private float cameraYaw = 45.0f;
    private final float cameraPitch = 45.0f; // Fixed pitch
    private int cutY = 320; // Max height

    public VoxelMapScreen() {
        super(Component.literal("Voxel Map"));
    }
    
    @Override
    protected void init() {
        super.init();
        // Add Slider for Cut Y
        this.addRenderableWidget(new AbstractSliderButton(10, 10, 120, 20, Component.literal("Cut Y: " + cutY), (double)cutY / 320.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Cut Y: " + cutY));
            }

            @Override
            protected void applyValue() {
                int newCutY = (int)(this.value * 320);
                if (newCutY != cutY) {
                    cutY = newCutY;
                    // Clear cache to rebuild meshes with new cut height
                    // Note: We cannot access VoxelMapScreen.this.meshCache directly here easily if we use lambda, 
                    // but we are in anonymous inner class.
                    // We need to access the outer class method or field.
                    clearMeshCache();
                }
            }
        });
    }

    private void clearMeshCache() {
        meshCache.values().forEach(ChunkMesh::close);
        meshCache.clear();
    }

    private int lastPlayerY = 0;

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (Minecraft.getInstance().player != null) {
            int currentPlayerY = Minecraft.getInstance().player.getBlockY();
            if (Math.abs(currentPlayerY - lastPlayerY) > 1) {
                lastPlayerY = currentPlayerY;
                clearMeshCache();
            }
        }
        
        this.renderBackground(guiGraphics);
        
        // Debug info on screen
        guiGraphics.drawString(this.font, "Chunks loaded: " + ChunkScanner.getData().size(), 10, 40, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Meshes cached: " + meshCache.size(), 10, 50, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Cam: " + String.format("%.1f, %.1f", camX, camZ), 10, 60, 0xFFFFFF);

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        
        // Center on screen
        poseStack.translate(this.width / 2.0, this.height / 2.0, 600);
        
        // Fix coordinate system: GUI Y is down, World Y is up.
        poseStack.scale(zoom, -zoom, zoom);
        
        poseStack.mulPose(Axis.XP.rotationDegrees(cameraPitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(cameraYaw));
        
        // Lock camera to player (no manual panning)
        // camX and camZ are ignored or reset to 0 effectively by not applying them
        // or we can keep them if we want to allow temporary panning, but user said "tu en el centro"
        
        // Enable Depth Test for 3D rendering
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL11.GL_LEQUAL = 515
        
        // Disable culling to ensure all faces are visible
        RenderSystem.disableCull();
        
        // IMPORTANT: Set shader color to white to avoid tinting
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        // Draw a debug axis marker at the center (Player position)
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        // X Axis
        buf.vertex(poseStack.last().pose(), 0, 0, 0).color(255, 0, 0, 255).endVertex();
        buf.vertex(poseStack.last().pose(), 5, 0, 0).color(255, 0, 0, 255).endVertex();
        // Y Axis
        buf.vertex(poseStack.last().pose(), 0, 0, 0).color(0, 255, 0, 255).endVertex();
        buf.vertex(poseStack.last().pose(), 0, 5, 0).color(0, 255, 0, 255).endVertex();
        // Z Axis
        buf.vertex(poseStack.last().pose(), 0, 0, 0).color(0, 0, 255, 255).endVertex();
        buf.vertex(poseStack.last().pose(), 0, 0, 5).color(0, 0, 255, 255).endVertex();
        BufferBuilder.RenderedBuffer renderedBuffer = buf.end();
        BufferUploader.drawWithShader(renderedBuffer);

        if (Minecraft.getInstance().player != null) {
            Player player = Minecraft.getInstance().player;
            ChunkPos playerChunk = player.chunkPosition();
            
            // Center strictly on player exact position
            double centerX = player.getX();
            double centerZ = player.getZ();
            double centerY = player.getY();

            Map<ChunkPos, ChunkScanner.ScannedChunk> data = ChunkScanner.getData();

            for (Map.Entry<ChunkPos, ChunkScanner.ScannedChunk> entry : data.entrySet()) {
                ChunkPos cp = entry.getKey();
                
                // Horizontal Distance Check: Only show chunks within ~2 chunks radius (32 blocks)
                // "Solo la parte cercana a ti"
                if (Math.abs(cp.x - playerChunk.x) > 2 || Math.abs(cp.z - playerChunk.z) > 2) {
                    continue;
                }
                
                if (!meshCache.containsKey(cp)) {
                    buildMesh(cp, entry.getValue());
                }

                ChunkMesh mesh = meshCache.get(cp);
                if (mesh != null) {
                    poseStack.pushPose();
                    // Translate to chunk relative position
                    // We want (px, py, pz) to be at (0,0,0) in view
                    // Chunk starts at cp.x*16, cp.z*16
                    // Block at cx, cy, cz in world:
                    // ViewPos = (cx - playerX), (cy - playerY), (cz - playerZ)
                    
                    float rx = (float)(cp.x * 16 - centerX);
                    float rz = (float)(cp.z * 16 - centerZ);
                    float ry = (float)(-centerY); 
                    
                    poseStack.translate(rx, ry, rz);
                    
                    mesh.draw(poseStack.last().pose(), RenderSystem.getProjectionMatrix());
                    poseStack.popPose();
                }
            }
            
            // Render entities relative to player (which is 0,0,0)
            renderEntities(poseStack, centerX, centerZ, centerY);
        }
        
        poseStack.popPose();
        
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) { // Left click
            cameraYaw += dragX;
            // cameraPitch += dragY; // Pitch locked
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        zoom += delta * 0.5f;
        if (zoom < 0.1f) zoom = 0.1f;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        float speed = 16.0f / zoom; 
        if (keyCode == GLFW.GLFW_KEY_W) camZ -= speed;
        if (keyCode == GLFW.GLFW_KEY_S) camZ += speed;
        if (keyCode == GLFW.GLFW_KEY_A) camX -= speed;
        if (keyCode == GLFW.GLFW_KEY_D) camX += speed;
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderEntities(PoseStack poseStack, double centerX, double centerZ, double centerY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        Iterable<Entity> entities = mc.level.entitiesForRendering();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        // Use position color shader
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        for (Entity e : entities) {
            double rx = e.getX() - centerX;
            double ry = e.getY() - centerY;
            double rz = e.getZ() - centerZ;
            
            // Optimization: Skip if too far? 
            // For now render all for visibility
            
            if (e instanceof Player) {
                // Player marker: Two white squares (cubes) stacked
                // Body
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 0.8f, 0.6f, 1.0f, 1.0f, 1.0f, 1.0f);
                // Head
                renderBox(buf, poseStack.last().pose(), rx, ry + 0.8f, rz, 0.4f, 0.4f, 0.4f, 1.0f, 1.0f, 1.0f, 1.0f);
            } else if (e instanceof Villager) {
                // Villager: Brownish
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.6f, 1.8f, 0.6f, 0.6f, 0.4f, 0.3f, 1.0f);
            } else if (e instanceof Animal) {
                // Animal: Greenish? Or generic
                renderBox(buf, poseStack.last().pose(), rx, ry, rz, 0.5f, 0.5f, 0.5f, 0.4f, 0.8f, 0.4f, 1.0f);
            }
        }
        
        BufferBuilder.RenderedBuffer renderedBuffer = buf.end();
        BufferUploader.drawWithShader(renderedBuffer);
    }

    private void renderBox(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float w, float h, float d, float r, float g, float b, float a) {
        float minX = (float)(x - w/2);
        float maxX = (float)(x + w/2);
        float minY = (float)y;
        float maxY = (float)(y + h);
        float minZ = (float)(z - d/2);
        float maxZ = (float)(z + d/2);
        
        int red = (int)(r * 255);
        int green = (int)(g * 255);
        int blue = (int)(b * 255);
        int alpha = (int)(a * 255);
        
        // Top
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Bottom
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Front
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Back
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        
        // Left
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        
        // Right
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
    }

    private void buildMesh(ChunkPos cp, ChunkScanner.ScannedChunk data) {
        ChunkMesh mesh = new ChunkMesh();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        // Use a slightly smaller size to create a "miniblock" effect with gaps
        float min = 0.1f;
        float max = 0.9f;
        
        int[] packedPositions = data.packedPositions;
        int[] colors = data.colors;
        
        int playerY = 0;
        if (Minecraft.getInstance().player != null) {
            playerY = Minecraft.getInstance().player.getBlockY();
        }
        
        if (packedPositions != null) {
            for (int i = 0; i < packedPositions.length; i++) {
                int packed = packedPositions[i];
                int color = colors[i];
                
                // Unpack: x(4) | z(4) | y(9)
                int x = packed & 0xF;
                int z = (packed >> 4) & 0xF;
                int relY = (packed >> 8) & 0x1FF;
                
                // Assume standard overworld for now (-64).
                int h = relY - 64; 
                
                // Vertical Range Check (PlayerY +/- 10)
                // "Que pase lo mismo con la altura" -> Hide blocks far from player vertically
                if (h < playerY - 10 || h > playerY + 10) continue;
                
                if (h > cutY) continue; 
                
                // Extract RGB
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                
                // Top Face (Brightest)
                builder.vertex(x + min, h + max, z + min).color(r, g, b, 255).endVertex();
                builder.vertex(x + min, h + max, z + max).color(r, g, b, 255).endVertex();
                builder.vertex(x + max, h + max, z + max).color(r, g, b, 255).endVertex();
                builder.vertex(x + max, h + max, z + min).color(r, g, b, 255).endVertex();

                // Darken side faces for pseudo-lighting
                int rSide = (int)(r * 0.8f);
                int gSide = (int)(g * 0.8f);
                int bSide = (int)(b * 0.8f);

                // Bottom Face
                builder.vertex(x + max, h + min, z + min).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + max, h + min, z + max).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + min, h + min, z + max).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + min, h + min, z + min).color(rSide, gSide, bSide, 255).endVertex();

                // North Face (z + min)
                builder.vertex(x + max, h + max, z + min).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + max, h + min, z + min).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + min, h + min, z + min).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + min, h + max, z + min).color(rSide, gSide, bSide, 255).endVertex();

                // South Face (z + max)
                builder.vertex(x + min, h + max, z + max).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + min, h + min, z + max).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + max, h + min, z + max).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + max, h + max, z + max).color(rSide, gSide, bSide, 255).endVertex();

                // West Face (x + min)
                builder.vertex(x + min, h + max, z + min).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + min, h + min, z + min).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + min, h + min, z + max).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + min, h + max, z + max).color(rSide, gSide, bSide, 255).endVertex();

                // East Face (x + max)
                builder.vertex(x + max, h + max, z + max).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + max, h + min, z + max).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + max, h + min, z + min).color(rSide, gSide, bSide, 255).endVertex();
                builder.vertex(x + max, h + max, z + min).color(rSide, gSide, bSide, 255).endVertex();
            }
        }
        
        BufferBuilder.RenderedBuffer renderedBuffer = builder.end();
        mesh.upload(renderedBuffer);
        
        meshCache.put(cp, mesh);
    }
    
    @Override
    public void onClose() {
        clearMeshCache();
        super.onClose();
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
