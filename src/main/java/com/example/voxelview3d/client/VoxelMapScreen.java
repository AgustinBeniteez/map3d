package com.example.voxelview3d.client;

import com.example.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class VoxelMapScreen extends Screen {
    
    // Camera controls
    private double camX = 0;
    private double camZ = 0;
    private float zoom = 3.0f; // Start closer
    private float cameraYaw = 45.0f;
    private final float cameraPitch = 45.0f; // Fixed pitch

    public VoxelMapScreen() {
        super(Component.literal("Voxel Map"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        int cutY = ClientMapData.getInstance().getCutY();
        
        // Add Slider for Cut Y
        this.addRenderableWidget(new AbstractSliderButton(10, 10, 120, 20, Component.literal("Cut Y: " + cutY), (double)cutY / 320.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Cut Y: " + ClientMapData.getInstance().getCutY()));
            }

            @Override
            protected void applyValue() {
                int newCutY = (int)(this.value * 320);
                ClientMapData.getInstance().setCutY(newCutY);
            }
        });
        
        // Add Button for Minimap Position
        this.addRenderableWidget(Button.builder(Component.literal("Minimap: " + VoxelMapHud.currentPosition), (btn) -> {
            VoxelMapHud.currentPosition = VoxelMapHud.currentPosition.next();
            btn.setMessage(Component.literal("Minimap: " + VoxelMapHud.currentPosition));
        }).bounds(10, 35, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        
        // Debug info on screen
        guiGraphics.drawString(this.font, "Chunks loaded: " + ChunkScanner.getData().size(), 10, 60, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Meshes cached: " + ClientMapData.getInstance().getMeshCache().size(), 10, 70, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Player Y: " + (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getBlockY() : "?"), 10, 80, 0xFFFFFF);

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        
        // Center on screen
        poseStack.translate(this.width / 2.0, this.height / 2.0, 600);
        
        // Use shared renderer
        // Note: VoxelMapRenderer handles the scaling and rotation setup
        // Radius 10 means render reasonable area (reduced from 24 to avoid lag)
        VoxelMapRenderer.renderMap(poseStack, zoom, cameraPitch, cameraYaw, false, 10);
        
        poseStack.popPose();
        
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) { // Left click
            cameraYaw += dragX;
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
        // Camera movement is currently disabled/locked to player in renderer
        // But if we wanted to enable it, we'd pass camX/camZ to renderer.
        // For now, renderer uses player position strictly.
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
