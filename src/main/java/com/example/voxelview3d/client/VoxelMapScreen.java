package com.example.voxelview3d.client;

import com.example.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public class VoxelMapScreen extends Screen {
    
    // Camera controls
    private double camX = 0;
    private double camZ = 0;
    private float zoom = 3.0f; // Start closer
    private float cameraYaw = 45.0f;
    private final float cameraPitch = 45.0f; // Fixed pitch

    // UI Components
    private boolean showWaypointModal = false;
    private EditBox waypointNameField;
    private EditBox wpX, wpY, wpZ;
    private Button createWaypointBtn;
    
    private int selectedColor = 0xFFFF00; // Default Yellow
    private final int[] COLORS = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF, 0xFFFFFF, 0xFFA500};
    private final List<Button> colorButtons = new ArrayList<>();
    
    // Toggles
    private Button toggleVillagers;
    private Button toggleAnimals;
    private Button toggleEnemies;
    private Button togglePlayers;

    public VoxelMapScreen() {
        super(Component.literal("Voxel Map"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonY = this.height - 25;
        int btnWidth = 50;
        int x = 10;
        
        // Toggle Buttons
        toggleVillagers = addRenderableWidget(Button.builder(Component.literal("Vil: " + (ClientSettings.showVillagers ? "ON" : "OFF")), b -> {
            ClientSettings.showVillagers = !ClientSettings.showVillagers;
            b.setMessage(Component.literal("Vil: " + (ClientSettings.showVillagers ? "ON" : "OFF")));
        }).bounds(x, buttonY, btnWidth, 20).build());
        x += btnWidth + 5;
        
        toggleAnimals = addRenderableWidget(Button.builder(Component.literal("Ani: " + (ClientSettings.showAnimals ? "ON" : "OFF")), b -> {
            ClientSettings.showAnimals = !ClientSettings.showAnimals;
            b.setMessage(Component.literal("Ani: " + (ClientSettings.showAnimals ? "ON" : "OFF")));
        }).bounds(x, buttonY, btnWidth, 20).build());
        x += btnWidth + 5;
        
        toggleEnemies = addRenderableWidget(Button.builder(Component.literal("Ene: " + (ClientSettings.showEnemies ? "ON" : "OFF")), b -> {
            ClientSettings.showEnemies = !ClientSettings.showEnemies;
            b.setMessage(Component.literal("Ene: " + (ClientSettings.showEnemies ? "ON" : "OFF")));
        }).bounds(x, buttonY, btnWidth, 20).build());
        x += btnWidth + 5;
        
        togglePlayers = addRenderableWidget(Button.builder(Component.literal("Pla: " + (ClientSettings.showPlayers ? "ON" : "OFF")), b -> {
            ClientSettings.showPlayers = !ClientSettings.showPlayers;
            b.setMessage(Component.literal("Pla: " + (ClientSettings.showPlayers ? "ON" : "OFF")));
        }).bounds(x, buttonY, btnWidth, 20).build());
        x += btnWidth + 5;
        
        // Waypoints Button
        addRenderableWidget(Button.builder(Component.literal("Waypoints"), b -> {
            toggleModal();
        }).bounds(x, buttonY, 70, 20).build());
        
        // Modal Widgets (hidden by default)
        initModalWidgets();
        
        updateModalVisibility();
    }
    
    private void initModalWidgets() {
        int modalW = 220;
        int modalH = 220;
        int modalX = this.width / 2 - modalW / 2;
        int modalY = this.height / 2 - 100;
        
        // Name
        waypointNameField = new EditBox(this.font, modalX + 10, modalY + 30, 200, 20, Component.literal("Name"));
        addRenderableWidget(waypointNameField);
        
        // Coords
        int coordW = 60;
        wpX = new EditBox(this.font, modalX + 10, modalY + 65, coordW, 20, Component.literal("X"));
        wpY = new EditBox(this.font, modalX + 80, modalY + 65, coordW, 20, Component.literal("Y"));
        wpZ = new EditBox(this.font, modalX + 150, modalY + 65, coordW, 20, Component.literal("Z"));
        addRenderableWidget(wpX);
        addRenderableWidget(wpY);
        addRenderableWidget(wpZ);
        
        // Color Buttons
        colorButtons.clear();
        int colSize = 20;
        int startX = modalX + 10;
        int startY = modalY + 100;
        
        for (int i = 0; i < COLORS.length; i++) {
            final int color = COLORS[i];
            Button btn = Button.builder(Component.literal(""), b -> {
                selectedColor = color;
            }).bounds(startX + (i * (colSize + 5)), startY, colSize, colSize).build();
            addRenderableWidget(btn);
            colorButtons.add(btn);
        }
        
        // Create Button
        createWaypointBtn = addRenderableWidget(Button.builder(Component.literal("Create WP"), b -> {
            createWaypoint();
        }).bounds(modalX + 10, modalY + 130, 200, 20).build());
    }
    
    private void toggleModal() {
        showWaypointModal = !showWaypointModal;
        if (showWaypointModal && minecraft.player != null) {
            // Populate fields
            wpX.setValue(String.valueOf(minecraft.player.getBlockX()));
            wpY.setValue(String.valueOf(minecraft.player.getBlockY()));
            wpZ.setValue(String.valueOf(minecraft.player.getBlockZ()));
            waypointNameField.setValue("");
        }
        updateModalVisibility();
    }
    
    private void updateModalVisibility() {
        if (waypointNameField != null) waypointNameField.visible = showWaypointModal;
        if (wpX != null) wpX.visible = showWaypointModal;
        if (wpY != null) wpY.visible = showWaypointModal;
        if (wpZ != null) wpZ.visible = showWaypointModal;
        if (createWaypointBtn != null) createWaypointBtn.visible = showWaypointModal;
        
        for (Button b : colorButtons) {
            b.visible = showWaypointModal;
        }
    }
    
    private void createWaypoint() {
        String name = waypointNameField.getValue();
        if (name != null && !name.isEmpty()) {
            try {
                int x = Integer.parseInt(wpX.getValue());
                int y = Integer.parseInt(wpY.getValue());
                int z = Integer.parseInt(wpZ.getValue());
                
                ClientSettings.waypoints.add(new ClientSettings.Waypoint(name, x, y, z, selectedColor));
                
                showWaypointModal = false;
                updateModalVisibility();
            } catch (NumberFormatException e) {
                // Ignore invalid numbers
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        
        // Center on screen
        poseStack.translate(this.width / 2.0, this.height / 2.0, 600);
        
        // Use shared renderer
        VoxelMapRenderer.renderMap(poseStack, zoom, cameraPitch, cameraYaw, false, 10);
        
        poseStack.popPose();
        
        // Start UI Layer (Higher Z-index to sit above the map)
        poseStack.pushPose();
        poseStack.translate(0, 0, 800);
        
        // Draw Bottom Menu Background
        int menuHeight = 35;
        int menuY = this.height - menuHeight;
        guiGraphics.fill(0, menuY, this.width, this.height, 0x80000000); // Semi-transparent black
        
        // Draw Zoom Level
        String zoomText = String.format("Zoom: %.1f", zoom);
        guiGraphics.drawString(this.font, zoomText, this.width - 80, menuY + 10, 0xFFFFFFFF);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Player player = mc.player;
            int x = player.getBlockX();
            int y = player.getBlockY();
            int z = player.getBlockZ();
            
            String coords = String.format("X: %d Y: %d Z: %d", x, y, z);
            
            // Get Biome
            String biomeName = "Unknown Biome";
            if (mc.level != null) {
                Holder<Biome> biomeHolder = mc.level.getBiome(player.blockPosition());
                biomeName = biomeHolder.unwrapKey()
                        .map(ResourceKey::location)
                        .map(ResourceLocation::getPath)
                        .orElse("Unknown");
                
                // Capitalize
                biomeName = capitalize(biomeName.replace('_', ' '));
            }
            
            int centerX = this.width / 2;
            int topMargin = 10;
            
            guiGraphics.drawCenteredString(this.font, coords, centerX, topMargin, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font, biomeName, centerX, topMargin + 12, 0xFFFFFF);
        }
        
        // Render Widgets (Buttons, etc.)
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        
        // Render Modal if open
        if (showWaypointModal) {
            renderWaypointModal(guiGraphics, mouseX, mouseY);
        }
        
        poseStack.popPose();
    }
    
    private void renderWaypointModal(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int w = 220;
        int h = 260; 
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 100;
        
        // Background
        guiGraphics.fill(x, y, x + w, y + h, 0xFF202020);
        guiGraphics.fill(x, y, x + w, y + 20, 0xFF404040); // Title bar
        guiGraphics.drawCenteredString(this.font, "Waypoints Manager", this.width / 2, y + 6, 0xFFFFFF);
        
        // Labels for Coords
        guiGraphics.drawString(this.font, "X", x + 10, y + 55, 0xFFAAAAAA);
        guiGraphics.drawString(this.font, "Y", x + 80, y + 55, 0xFFAAAAAA);
        guiGraphics.drawString(this.font, "Z", x + 150, y + 55, 0xFFAAAAAA);
        
        // Draw colors over buttons
        for (int i = 0; i < colorButtons.size(); i++) {
            Button btn = colorButtons.get(i);
            int color = COLORS[i];
            
            // Fill inside button
            guiGraphics.fill(btn.getX() + 2, btn.getY() + 2, btn.getX() + btn.getWidth() - 2, btn.getY() + btn.getHeight() - 2, color | 0xFF000000);
            
            // Selection border
            if (selectedColor == color) {
                guiGraphics.renderOutline(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0xFFFFFFFF);
            }
        }
        
        // List of Waypoints
        int listY = y + 160;
        int index = 0;
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (index > 6) break; 
            String s = wp.name + " [" + wp.x + "," + wp.y + "," + wp.z + "]";
            guiGraphics.drawString(this.font, s, x + 10, listY + index * 12, wp.color);
            index++;
        }
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
