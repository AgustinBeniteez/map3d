package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.systems.RenderSystem;
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
    private double panX = 0;
    private double panY = 0;
    private float zoom = 3.0f; // Start closer
    private float cameraYaw = 45.0f;
    private final float cameraPitch = 45.0f; // Fixed pitch

    // UI Components
    private boolean showWaypointModal = false;
    private boolean isCreatingMode = false; // Toggle between List and Create mode
    private ClientSettings.Waypoint editingWaypoint = null;
    
    private EditBox waypointNameField;
    private EditBox wpX, wpY, wpZ;
    private Button createWaypointBtn;
    private Button openCreateModeBtn; // Button in List mode to open Create mode
    // private Button cancelCreateBtn; // Removed, merged into X button
    
    private int selectedColor = 0xFFFF00; // Default Yellow
    private String selectedIcon = "icon1"; // Default Icon
    private final int[] COLORS = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF, 0xFFFFFF, 0xFFA500};
    private final List<Button> colorButtons = new ArrayList<>();
    private final List<Button> iconButtons = new ArrayList<>();
    
    // Toggles
    private Button toggleVillagers;
    private Button toggleAnimals;
    private Button toggleEnemies;
    private Button togglePlayers;
    private Button waypointsBtn;
    private Button closeModalBtn;

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
        waypointsBtn = addRenderableWidget(Button.builder(Component.literal("Waypoints"), b -> {
            toggleModal();
        }).bounds(x, buttonY, 70, 20).build());
        
        // Modal Widgets (hidden by default)
        initModalWidgets();
        
        updateModalVisibility();
    }
    
    private void initModalWidgets() {
        // Dynamic Full Screen Modal
        int margin = 10;
        int modalX = margin;
        int modalY = margin;
        int modalW = this.width - (margin * 2);
        int modalH = this.height - (margin * 2);
        
        // Center of the modal for Create Mode
        int centerX = this.width / 2;
        
        // --- Create Mode Widgets ---
        
        // Name
        waypointNameField = new EditBox(this.font, centerX - 100, modalY + 30, 200, 20, Component.literal("Name"));
        addRenderableWidget(waypointNameField);
        
        // Coords
        int coordW = 60;
        int coordGap = 10;
        int totalCoordW = (coordW * 3) + (coordGap * 2);
        int startCoordX = centerX - (totalCoordW / 2);
        
        int coordsY = modalY + 65;
        wpX = new EditBox(this.font, startCoordX, coordsY, coordW, 20, Component.literal("X"));
        wpY = new EditBox(this.font, startCoordX + coordW + coordGap, coordsY, coordW, 20, Component.literal("Y"));
        wpZ = new EditBox(this.font, startCoordX + (coordW + coordGap) * 2, coordsY, coordW, 20, Component.literal("Z"));
        addRenderableWidget(wpX);
        addRenderableWidget(wpY);
        addRenderableWidget(wpZ);
        
        // Color Buttons
        colorButtons.clear();
        int colSize = 20;
        int colorsW = (COLORS.length * (colSize + 4)) - 4;
        int startX = centerX - (colorsW / 2);
        int startY = modalY + 105;
        
        for (int i = 0; i < COLORS.length; i++) {
            final int color = COLORS[i];
            Button btn = Button.builder(Component.literal(""), b -> {
                selectedColor = color;
            }).bounds(startX + (i * (colSize + 4)), startY, colSize, colSize).build();
            addRenderableWidget(btn);
            colorButtons.add(btn);
        }
        
        // Icon Buttons
        iconButtons.clear();
        int iconSize = 20;
        int numIcons = 10;
        int iconsW = (numIcons * (iconSize + 2)) - 2;
        int iconStartX = centerX - (iconsW / 2);
        int iconStartY = modalY + 145;
        
        for (int i = 1; i <= 10; i++) {
            final String iconName = "icon" + i;
            Button btn = Button.builder(Component.empty(), b -> {
                selectedIcon = iconName;
            }).bounds(iconStartX + ((i - 1) * (iconSize + 2)), iconStartY, iconSize, iconSize).build();
            addRenderableWidget(btn);
            iconButtons.add(btn);
        }
        
        // Create Button (Create Mode)
        // Ensure it doesn't overlap with icons (icons end at iconStartY + 20)
        int minButtonY = iconStartY + 30;
        int desiredButtonY = modalY + modalH - 30;
        int finalButtonY = Math.max(minButtonY, desiredButtonY);
        
        createWaypointBtn = addRenderableWidget(Button.builder(Component.literal("Save Waypoint"), b -> {
            createWaypoint();
        }).bounds(centerX - 100, finalButtonY, 200, 20).build());
        
        // --- List Mode Widgets ---
        
        // Open Create Mode Button (in List Mode)
        openCreateModeBtn = addRenderableWidget(Button.builder(Component.literal("Create New Waypoint"), b -> {
            isCreatingMode = true;
            editingWaypoint = null;
            // Pre-fill coordinates when entering create mode
            if (minecraft.player != null) {
                wpX.setValue(String.valueOf(minecraft.player.getBlockX()));
                wpY.setValue(String.valueOf(minecraft.player.getBlockY()));
                wpZ.setValue(String.valueOf(minecraft.player.getBlockZ()));
            }
            waypointNameField.setValue("");
            selectedIcon = "icon1"; 
            updateModalVisibility();
        }).bounds(centerX - 100, modalY + modalH - 40, 200, 20).build());
        
        // Close Button (Small X in top right of the whole modal)
        closeModalBtn = addRenderableWidget(Button.builder(Component.literal("X"), b -> {
            if (isCreatingMode) {
                isCreatingMode = false;
                editingWaypoint = null;
                updateModalVisibility();
            } else {
                showWaypointModal = false;
                updateModalVisibility();
            }
        }).bounds(modalX + modalW - 25, modalY + 5, 20, 20).build());
    }
    
    private void toggleModal() {
        showWaypointModal = !showWaypointModal;
        isCreatingMode = false; // Always start in List mode
        editingWaypoint = null;
        updateModalVisibility();
    }
    
    private void updateModalVisibility() {
        // Modal controls
        if (closeModalBtn != null) closeModalBtn.visible = showWaypointModal;
        
        // Create Mode Widgets
        boolean showCreate = showWaypointModal && isCreatingMode;
        
        if (waypointNameField != null) waypointNameField.visible = showCreate;
        if (wpX != null) wpX.visible = showCreate;
        if (wpY != null) wpY.visible = showCreate;
        if (wpZ != null) wpZ.visible = showCreate;
        if (createWaypointBtn != null) {
            createWaypointBtn.visible = showCreate;
            createWaypointBtn.setMessage(Component.literal(editingWaypoint != null ? "Save Changes" : "Create Waypoint"));
        }
        // if (cancelCreateBtn != null) cancelCreateBtn.visible = showCreate;
        
        for (Button b : colorButtons) {
            b.visible = showCreate;
        }
        
        for (Button b : iconButtons) {
            b.visible = showCreate;
        }
        
        // List Mode Widgets
        boolean showList = showWaypointModal && !isCreatingMode;
        if (openCreateModeBtn != null) openCreateModeBtn.visible = showList;
        
        // Bottom Menu controls (Hide when modal is open to avoid overlap/bleed through)
        boolean showBottom = !showWaypointModal;
        if (toggleVillagers != null) toggleVillagers.visible = showBottom;
        if (toggleAnimals != null) toggleAnimals.visible = showBottom;
        if (toggleEnemies != null) toggleEnemies.visible = showBottom;
        if (togglePlayers != null) togglePlayers.visible = showBottom;
        if (waypointsBtn != null) waypointsBtn.visible = showBottom;
    }
    
    private void createWaypoint() {
        String name = waypointNameField.getValue();
        if (name != null && !name.isEmpty()) {
            try {
                int x = Integer.parseInt(wpX.getValue());
                int y = Integer.parseInt(wpY.getValue());
                int z = Integer.parseInt(wpZ.getValue());
                
                if (editingWaypoint != null) {
                    // Update existing
                    editingWaypoint.name = name;
                    editingWaypoint.x = x;
                    editingWaypoint.y = y;
                    editingWaypoint.z = z;
                    editingWaypoint.color = selectedColor;
                    editingWaypoint.iconName = selectedIcon;
                } else {
                    // Create new
                    ClientSettings.waypoints.add(new ClientSettings.Waypoint(name, x, y, z, selectedColor, selectedIcon));
                }
                
                // Save Waypoints
                WaypointManager.saveWaypoints();
                
                // Clear and go back to list
                waypointNameField.setValue("");
                editingWaypoint = null;
                isCreatingMode = false;
                updateModalVisibility();
            } catch (NumberFormatException e) {
                // Ignore invalid numbers
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        
        if (showWaypointModal && !isCreatingMode) {
            // Check clicks on the Waypoint List (Full Width)
            int margin = 10;
            int modalW = this.width - (margin * 2);
            int modalH = this.height - (margin * 2);
            int modalX = margin;
            int modalY = margin;
            
            // List Area
            int listX = modalX + 10;
            int listY = modalY + 35; // Title bar space
            int itemHeight = 30; // Bigger items
            int listWidth = modalW - 20; 
            
            // Calculate visible items based on available height minus title (35) and bottom button space (50)
            int visibleItems = (modalH - 85) / itemHeight;
            
            int clickedIndex = (int)((mouseY - listY) / itemHeight);
            
            if (clickedIndex >= 0 && clickedIndex < visibleItems && clickedIndex < ClientSettings.waypoints.size() && mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY < listY + (visibleItems * itemHeight)) {
                 ClientSettings.Waypoint wp = ClientSettings.waypoints.get(clickedIndex);
                 
                 // Buttons positions relative to list right edge
                 int rightEdge = listX + listWidth;
                 
                 // Buttons are roughly 20px wide
                 // Delete: Right - 30
                 // TP: Right - 60
                 // Visible: Right - 90
                 // Edit: Right - 120
                 
                 if (mouseX >= rightEdge - 120 && mouseX < rightEdge - 100) {
                     // Edit
                     isCreatingMode = true;
                     editingWaypoint = wp;
                     
                     // Populate fields
                     waypointNameField.setValue(wp.name);
                     wpX.setValue(String.valueOf(wp.x));
                     wpY.setValue(String.valueOf(wp.y));
                     wpZ.setValue(String.valueOf(wp.z));
                     selectedColor = wp.color;
                     selectedIcon = wp.iconName;
                     
                     updateModalVisibility();
                     return true;
                 }
                 
                 if (mouseX >= rightEdge - 90 && mouseX < rightEdge - 70) {
                     // Toggle Visibility
                     wp.visible = !wp.visible;
                     WaypointManager.saveWaypoints();
                     return true;
                 }
                 
                 if (mouseX >= rightEdge - 60 && mouseX < rightEdge - 40) {
                     // TP
                     if (minecraft.player != null) {
                         minecraft.player.connection.sendCommand("tp " + wp.x + " " + wp.y + " " + wp.z);
                     }
                     return true;
                 }
                 
                 if (mouseX >= rightEdge - 30 && mouseX < rightEdge - 10) {
                     // Delete
                     ClientSettings.waypoints.remove(clickedIndex);
                     WaypointManager.saveWaypoints();
                     return true;
                 }
            }
        }
        
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        
        // Center on screen
        poseStack.translate(this.width / 2.0 + panX, this.height / 2.0 + panY, 600);
        
        // Use shared renderer
        VoxelMapRenderer.renderMap(poseStack, zoom, cameraPitch, cameraYaw, false, 10);
        
        // Clear depth buffer to ensure UI draws cleanly on top of the 3D map
        RenderSystem.depthMask(true);
        RenderSystem.clear(256, Minecraft.ON_OSX);
        
        poseStack.popPose();
        
        // Start UI Layer (Higher Z-index to sit above the map)
        poseStack.pushPose();
        poseStack.translate(0, 0, 800);
        
        // Draw Bottom Menu Background
        int menuHeight = 35;
        int menuY = this.height - menuHeight;
        if (!showWaypointModal) { 
             guiGraphics.fill(0, menuY, this.width, this.height, 0x80000000); // Semi-transparent black
        }
        
        // Draw Zoom Level
        if (!showWaypointModal) {
            String zoomText = String.format("Zoom: %.1f", zoom);
            guiGraphics.drawString(this.font, zoomText, this.width - 80, menuY + 10, 0xFFFFFFFF);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !showWaypointModal) {
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
        
        // Render Modal if open
        if (showWaypointModal) {
            renderWaypointModal(guiGraphics, mouseX, mouseY);
        }
        
        // Render Widgets (Buttons, etc.)
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        
        // Render Modal Overlays (Icons, Selection Borders) if open
        if (showWaypointModal && isCreatingMode) {
            renderModalOverlays(guiGraphics, mouseX, mouseY);
        }
        
        poseStack.popPose();
    }
    
    private void renderModalOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
        
        // Draw icon selection border and icons
        for (int i = 0; i < iconButtons.size(); i++) {
            Button btn = iconButtons.get(i);
            String iconName = "icon" + (i + 1);
            
            // Draw Icon
            ResourceLocation iconLoc = new ResourceLocation("voxelview3d", "textures/waypoints/" + iconName + ".png");
            guiGraphics.blit(iconLoc, btn.getX() + 2, btn.getY() + 2, 0, 0, 16, 16, 16, 16);
            
            if (selectedIcon.equals(iconName)) {
                guiGraphics.renderOutline(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0xFFFFFFFF);
            }
        }
    }
    
    private void renderWaypointModal(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Dynamic Full Screen Modal
        int margin = 10;
        int modalX = margin;
        int modalY = margin;
        int modalW = this.width - (margin * 2);
        int modalH = this.height - (margin * 2);
        
        // Background (Almost solid dark)
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF0101010);
        
        // Title bar (Solid black)
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 30, 0xFF000000); 
        
        // Border
        guiGraphics.renderOutline(modalX, modalY, modalW, modalH, 0xFF404040);
        
        String title = isCreatingMode ? (editingWaypoint != null ? "Edit Waypoint" : "Create New Waypoint") : "Saved Waypoints";
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, modalY + 11, 0xFFFFFFFF);
        
        if (isCreatingMode) {
            // Render labels for create mode
            int centerX = this.width / 2;
            int coordW = 60;
            int coordGap = 10;
            int totalCoordW = (coordW * 3) + (coordGap * 2);
            int startCoordX = centerX - (totalCoordW / 2);
            
            guiGraphics.drawCenteredString(this.font, "X", startCoordX + (coordW/2), modalY + 55, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(this.font, "Y", startCoordX + coordW + coordGap + (coordW/2), modalY + 55, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(this.font, "Z", startCoordX + (coordW + coordGap) * 2 + (coordW/2), modalY + 55, 0xFFAAAAAA);
            
            guiGraphics.drawCenteredString(this.font, "Select Color", centerX, modalY + 95, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(this.font, "Select Icon", centerX, modalY + 135, 0xFFAAAAAA);
            
        } else {
            // Render List
            int listX = modalX + 10;
            int listY = modalY + 35;
            int itemHeight = 30;
            int visibleItems = (modalH - 85) / itemHeight; // Reserve space for button at bottom
            
            int listWidth = modalW - 20;
            
            for (int i = 0; i < ClientSettings.waypoints.size(); i++) {
                 if (i >= visibleItems) break; // Limit
                 
                 ClientSettings.Waypoint wp = ClientSettings.waypoints.get(i);
                 int rowY = listY + (i * itemHeight);
                 
                 // Row Background (alternate)
                 if (i % 2 == 0) {
                     guiGraphics.fill(listX, rowY, listX + listWidth, rowY + itemHeight, 0x20FFFFFF);
                 }
                 
                 // Hover effect
                 if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= rowY && mouseY < rowY + itemHeight) {
                     guiGraphics.fill(listX, rowY, listX + listWidth, rowY + itemHeight, 0x10FFFFFF);
                 }
                 
                 // Icon
                 ResourceLocation iconLoc = new ResourceLocation("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
                 RenderSystem.enableBlend();
                 guiGraphics.blit(iconLoc, listX + 5, rowY + 5, 0, 0, 20, 20, 20, 20);
                 RenderSystem.disableBlend();
                 
                 // Name
                 guiGraphics.drawString(this.font, wp.name, listX + 35, rowY + 11, wp.color);
                 
                 // Buttons: [Edit] [Eye] [TP] [Trash] (Aligned Right)
                 int rightEdge = listX + listWidth;
                 
                 // Coords (Before buttons)
                 String coords = String.format("[%d, %d, %d]", wp.x, wp.y, wp.z);
                 int coordsW = this.font.width(coords);
                 guiGraphics.drawString(this.font, coords, rightEdge - 140 - coordsW, rowY + 11, 0xFFAAAAAA);
                 
                 // Edit
                 boolean editHover = mouseX >= rightEdge - 120 && mouseX < rightEdge - 100 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 guiGraphics.fill(rightEdge - 120, rowY + 5, rightEdge - 100, rowY + 25, editHover ? 0xFF606060 : 0xFF404040);
                 guiGraphics.drawCenteredString(this.font, "E", rightEdge - 110, rowY + 11, 0xFFFFFF00);
                 
                 // Eye (Visibility)
                 boolean eyeHover = mouseX >= rightEdge - 90 && mouseX < rightEdge - 70 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 String eyeText = wp.visible ? "O" : "-";
                 int eyeColor = wp.visible ? 0xFF00FF00 : 0xFFFF0000;
                 guiGraphics.fill(rightEdge - 90, rowY + 5, rightEdge - 70, rowY + 25, eyeHover ? 0xFF606060 : 0xFF404040);
                 guiGraphics.drawCenteredString(this.font, eyeText, rightEdge - 80, rowY + 11, eyeColor);
                 
                 // TP
                 boolean tpHover = mouseX >= rightEdge - 60 && mouseX < rightEdge - 40 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 guiGraphics.fill(rightEdge - 60, rowY + 5, rightEdge - 40, rowY + 25, tpHover ? 0xFF606060 : 0xFF404040);
                 guiGraphics.drawCenteredString(this.font, "T", rightEdge - 50, rowY + 11, 0xFF00FFFF);
                 
                 // Trash
                 boolean trashHover = mouseX >= rightEdge - 30 && mouseX < rightEdge - 10 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 guiGraphics.fill(rightEdge - 30, rowY + 5, rightEdge - 10, rowY + 25, trashHover ? 0xFF606060 : 0xFF404040);
                 guiGraphics.drawCenteredString(this.font, "X", rightEdge - 20, rowY + 11, 0xFFFF0000);
            }
        }
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        
        if (isHoveringUI(mouseX, mouseY)) return false;
        
        // Map interaction
        if (button == 0) { // Left Click: Pan
            this.panX += dragX;
            this.panY += dragY;
            return true;
        } else if (button == 1) { // Right Click: Rotate
            this.cameraYaw += (float)dragX;
            return true;
        }
        
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (super.mouseScrolled(mouseX, mouseY, delta)) return true;
        
        if (isHoveringUI(mouseX, mouseY)) return false;

        // Map interaction: Zoom
        this.zoom += (float)delta * 0.5f;
        if (this.zoom < 0.5f) this.zoom = 0.5f;
        if (this.zoom > 20.0f) this.zoom = 20.0f;
        return true;
    }
    
    private boolean isHoveringUI(double mouseX, double mouseY) {
        // Bottom Menu
        if (mouseY >= this.height - 35) return true;
        
        // Modal
        if (showWaypointModal) {
            int margin = 20;
            int modalX = margin;
            int modalY = margin;
            int modalW = this.width - (margin * 2);
            int modalH = this.height - (margin * 2);
            
            if (mouseX >= modalX && mouseX <= modalX + modalW && mouseY >= modalY && mouseY <= modalY + modalH) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}