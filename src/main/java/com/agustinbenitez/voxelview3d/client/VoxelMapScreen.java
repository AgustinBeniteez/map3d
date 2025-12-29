package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class VoxelMapScreen extends Screen {
    
    // Camera controls
    private double panX = 0;
    private double panY = 0;
    private float zoom = 3.0f; // Start closer
    private float cameraYaw = 45.0f;
    private final float cameraPitch = 45.0f; // Fixed pitch

    // UI Components
    private boolean showWaypointModal = false;
    private boolean showSettingsModal = false;
    private boolean isCreatingMode = false; // Toggle between List and Create mode
    private float scrollOffset = 0; // Scroll offset for waypoint list
    private boolean isDraggingMap = false;
    private ClientSettings.Waypoint editingWaypoint = null;
    
    private EditBox waypointNameField;
    private EditBox wpX, wpY, wpZ;
    private Button createWaypointBtn;
    private Button openCreateModeBtn; // Button in List mode to open Create mode
    private Button deleteAllBtn;
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
    
    // Settings Widgets
    private Button toggleCompassBtn;
    private Button toggleCoordsBtn;
    private Button renderDistanceBtn;
    private Button closeSettingsBtn;

    public VoxelMapScreen() {
        super(Component.translatable("voxelview3d.title"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonY = this.height - 25;
        int btnWidth = 20; // Icons are small
        int x = 10;
        
        // Toggle Buttons
        toggleVillagers = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            new ResourceLocation("voxelview3d", "textures/types/villager.png"),
            new ResourceLocation("voxelview3d", "textures/types/villager_hide.png"),
            () -> ClientSettings.showVillagers,
            b -> ClientSettings.showVillagers = !ClientSettings.showVillagers));
        x += btnWidth + 5;
        
        toggleAnimals = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            new ResourceLocation("voxelview3d", "textures/types/animal.png"),
            new ResourceLocation("voxelview3d", "textures/types/animal_hide.png"),
            () -> ClientSettings.showAnimals,
            b -> ClientSettings.showAnimals = !ClientSettings.showAnimals));
        x += btnWidth + 5;
        
        toggleEnemies = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            new ResourceLocation("voxelview3d", "textures/types/enemie.png"),
            new ResourceLocation("voxelview3d", "textures/types/enemie_hide.png"),
            () -> ClientSettings.showEnemies,
            b -> ClientSettings.showEnemies = !ClientSettings.showEnemies));
        x += btnWidth + 5;
        
        togglePlayers = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            new ResourceLocation("voxelview3d", "textures/types/player.png"),
            new ResourceLocation("voxelview3d", "textures/types/player_hide.png"),
            () -> ClientSettings.showPlayers,
            b -> ClientSettings.showPlayers = !ClientSettings.showPlayers));
        x += btnWidth + 5;
        
        // Waypoints Button
        waypointsBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.waypoints"), b -> {
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
        waypointNameField = new EditBox(this.font, centerX - 100, modalY + 30, 200, 20, Component.translatable("voxelview3d.waypoint.name"));
        addRenderableWidget(waypointNameField);
        
        // Coords
        int coordW = 60;
        int coordGap = 10;
        int totalCoordW = (coordW * 3) + (coordGap * 2);
        int startCoordX = centerX - (totalCoordW / 2);
        
        int coordsY = modalY + 65;
        wpX = new EditBox(this.font, startCoordX, coordsY, coordW, 20, Component.translatable("voxelview3d.waypoint.x"));
        wpY = new EditBox(this.font, startCoordX + coordW + coordGap, coordsY, coordW, 20, Component.translatable("voxelview3d.waypoint.y"));
        wpZ = new EditBox(this.font, startCoordX + (coordW + coordGap) * 2, coordsY, coordW, 20, Component.translatable("voxelview3d.waypoint.z"));
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
        
        createWaypointBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.waypoint.save"), b -> {
            createWaypoint();
        }).bounds(centerX - 100, finalButtonY, 200, 20).build());
        
        // --- List Mode Widgets ---
        
        // Open Create Mode Button (in List Mode)
        openCreateModeBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.waypoint.create_new"), b -> {
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
        }).bounds(centerX - 105, modalY + modalH - 40, 100, 20).build());
        
        // Delete All Button
        deleteAllBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.waypoint.delete_all"), b -> {
            ClientSettings.waypoints.clear();
            WaypointManager.saveWaypoints();
        }).bounds(centerX + 5, modalY + modalH - 40, 100, 20).build());
        
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
        
        // --- Settings Modal Widgets ---
        int settingsW = 200;
        int settingsH = 150;
        int settingsX = (this.width - settingsW) / 2;
        int settingsY = (this.height - settingsH) / 2;
        
        toggleCompassBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.compass").append(": ").append(Component.translatable(ClientSettings.showCompass ? "voxelview3d.settings.compass.on" : "voxelview3d.settings.compass.off")), b -> {
            ClientSettings.showCompass = !ClientSettings.showCompass;
            b.setMessage(Component.translatable("voxelview3d.settings.compass").append(": ").append(Component.translatable(ClientSettings.showCompass ? "voxelview3d.settings.compass.on" : "voxelview3d.settings.compass.off")));
        }).bounds(settingsX + 10, settingsY + 30, settingsW - 20, 20).build());
        
        toggleCoordsBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.coords").append(Component.translatable(ClientSettings.showCoords ? "voxelview3d.on" : "voxelview3d.off")), b -> {
            ClientSettings.showCoords = !ClientSettings.showCoords;
            b.setMessage(Component.translatable("voxelview3d.settings.coords").append(Component.translatable(ClientSettings.showCoords ? "voxelview3d.on" : "voxelview3d.off")));
        }).bounds(settingsX + 10, settingsY + 55, settingsW - 20, 20).build());
        
        renderDistanceBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.render_dist").append(String.valueOf(ClientSettings.renderDistance)), b -> {
            ClientSettings.renderDistance += 5;
            if (ClientSettings.renderDistance > 15) ClientSettings.renderDistance = 5;
            b.setMessage(Component.translatable("voxelview3d.settings.render_dist").append(String.valueOf(ClientSettings.renderDistance)));
        }).bounds(settingsX + 10, settingsY + 80, settingsW - 20, 20).build());
        
        closeSettingsBtn = addRenderableWidget(Button.builder(Component.literal("X"), b -> {
            showSettingsModal = false;
            updateModalVisibility();
        }).bounds(settingsX + settingsW - 25, settingsY + 5, 20, 20).build());
    }
    
    private void toggleModal() {
        showWaypointModal = !showWaypointModal;
        showSettingsModal = false; // Close settings if opening waypoints
        isCreatingMode = false; // Always start in List mode
        editingWaypoint = null;
        updateModalVisibility();
    }
    
    private void updateModalVisibility() {
        // Waypoint Modal controls
        if (closeModalBtn != null) closeModalBtn.visible = showWaypointModal;
        
        // Create Mode Widgets
        boolean showCreate = showWaypointModal && isCreatingMode;
        
        if (waypointNameField != null) waypointNameField.visible = showCreate;
        if (wpX != null) wpX.visible = showCreate;
        if (wpY != null) wpY.visible = showCreate;
        if (wpZ != null) wpZ.visible = showCreate;
        if (createWaypointBtn != null) {
            createWaypointBtn.visible = showCreate;
            createWaypointBtn.setMessage(editingWaypoint != null ? Component.translatable("voxelview3d.waypoint.save_changes") : Component.translatable("voxelview3d.waypoint.create"));
        }
        
        for (Button b : colorButtons) {
            b.visible = showCreate;
        }
        
        for (Button b : iconButtons) {
            b.visible = showCreate;
        }
        
        // List Mode Widgets
        boolean showList = showWaypointModal && !isCreatingMode;
        if (openCreateModeBtn != null) openCreateModeBtn.visible = showList;
        if (deleteAllBtn != null) deleteAllBtn.visible = showList;
        
        // Settings Modal Widgets
        boolean showSettings = showSettingsModal;
        if (toggleCompassBtn != null) toggleCompassBtn.visible = showSettings;
        if (toggleCoordsBtn != null) toggleCoordsBtn.visible = showSettings;
        if (renderDistanceBtn != null) renderDistanceBtn.visible = showSettings;
        if (closeSettingsBtn != null) closeSettingsBtn.visible = showSettings;
        
        // Bottom Menu controls (Hide when any modal is open)
        boolean showBottom = !showWaypointModal && !showSettingsModal;
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.OPEN_MAP_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        if (keyCode == 256) { // ESC
             if (showWaypointModal) {
                 if (isCreatingMode) {
                     // Go back to List
                     isCreatingMode = false;
                     editingWaypoint = null;
                     updateModalVisibility();
                     return true;
                 } else {
                     // Close Modal
                     showWaypointModal = false;
                     updateModalVisibility();
                     return true;
                 }
             }
             
             if (showSettingsModal) {
                 showSettingsModal = false;
                 updateModalVisibility();
                 return true;
             }
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        
        // Settings Button Click (Top Left)
        if (!showWaypointModal && !showSettingsModal) {
            int btnX = 10;
            int btnY = 10;
            int btnSize = 20;
            if (mouseX >= btnX && mouseX <= btnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                showSettingsModal = true;
                updateModalVisibility();
                return true;
            }
        }
        
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
            int listHeight = modalH - 85;
            
            // Adjust clicked index based on scroll
            int clickedIndex = (int)((mouseY - listY + scrollOffset) / itemHeight);
            
            // Check if click is within list bounds visually
            if (mouseY >= listY && mouseY <= listY + listHeight && 
                clickedIndex >= 0 && clickedIndex < ClientSettings.waypoints.size() && 
                mouseX >= listX && mouseX <= listX + listWidth) {
                
                 ClientSettings.Waypoint wp = ClientSettings.waypoints.get(clickedIndex);
                 int rowY = (int)(listY + (clickedIndex * itemHeight) - scrollOffset);
                 
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
        
        // If not hovering any UI element, start dragging map
        if (!isHoveringUI(mouseX, mouseY)) {
            isDraggingMap = true;
            return true;
        }
        
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingMap = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        
        // Center on screen
        poseStack.translate(this.width / 2.0 + panX, this.height / 2.0 + panY, 600);
        
        // Use shared renderer
        if (!showWaypointModal && !showSettingsModal) {
            VoxelMapRenderer.renderMap(poseStack, zoom, cameraPitch, cameraYaw, false, ClientSettings.renderDistance);
        }
        
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
        if (!showWaypointModal && !showSettingsModal) { 
             guiGraphics.fill(0, menuY, this.width, this.height, 0x80000000); // Semi-transparent black
        }
        
        // Draw Settings Button (Top Left)
        if (!showWaypointModal && !showSettingsModal) {
            int btnX = 10;
            int btnY = 10;
            int btnSize = 20;
            
            // Hover effect
            boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize;
            int renderSize = isHovered ? 24 : 20;
            int renderX = btnX - (renderSize - btnSize) / 2;
            int renderY = btnY - (renderSize - btnSize) / 2;
            
            ResourceLocation settingsIcon = new ResourceLocation("voxelview3d", "textures/settings.png");
            RenderSystem.setShaderTexture(0, settingsIcon);
            guiGraphics.blit(settingsIcon, renderX, renderY, 0, 0, renderSize, renderSize, renderSize, renderSize);
        }
        
        // Draw Zoom Level
        if (!showWaypointModal && !showSettingsModal) {
            Component zoomText = Component.translatable("voxelview3d.zoom", String.format("%.1f", zoom));
            guiGraphics.drawString(this.font, zoomText, this.width - 80, menuY + 10, 0xFFFFFFFF);
            
            // Draw Compass
            renderCompass(guiGraphics);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !showWaypointModal && !showSettingsModal && ClientSettings.showCoords) {
            Player player = mc.player;
            int x = player.getBlockX();
            int y = player.getBlockY();
            int z = player.getBlockZ();
            
            Component coords = Component.translatable("voxelview3d.coords", x, y, z);
            
            // Get Biome
            String biomeName = Component.translatable("voxelview3d.biome.unknown").getString();
            if (mc.level != null) {
                Holder<Biome> biomeHolder = mc.level.getBiome(player.blockPosition());
                biomeName = biomeHolder.unwrapKey()
                        .map(ResourceKey::location)
                        .map(ResourceLocation::getPath)
                        .orElse(Component.translatable("voxelview3d.unknown").getString());
                
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
        
        if (showSettingsModal) {
            renderSettingsModal(guiGraphics, mouseX, mouseY);
        }
        
        // Render Widgets (Buttons, etc.)
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        
        // Draw icon on waypoints button
        if (waypointsBtn != null && waypointsBtn.visible) {
            ResourceLocation iconLoc = new ResourceLocation("voxelview3d", "textures/iconpoints.png");
            RenderSystem.setShaderTexture(0, iconLoc);
            RenderSystem.enableBlend();
            // Draw icon slightly to the left of text or centered if no text
            // Button bounds: waypointsBtn.getX(), waypointsBtn.getY(), width 70, height 20
            // Let's place it at x+5, y+2 (size 16x16)
            guiGraphics.blit(iconLoc, waypointsBtn.getX() + 5, waypointsBtn.getY() + 2, 0, 0, 16, 16, 16, 16);
            RenderSystem.disableBlend();
        }
        
        // Render Modal Overlays (Icons, Selection Borders) if open
        if (showWaypointModal && isCreatingMode) {
            renderModalOverlays(guiGraphics, mouseX, mouseY);
        }
        
        poseStack.popPose();
    }
    
    private void renderCompass(GuiGraphics guiGraphics) {
        // Compass configuration
        int cx = this.width - 40;
        int cy = 40;
        float radius = 20.0f;
        
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(cx, cy, 0);
        
        // Scale and Rotate (Flip Y for screen coords)
        poseStack.scale(radius, -radius, radius); 
        poseStack.mulPose(Axis.XP.rotationDegrees(cameraPitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(cameraYaw));
        
        // Draw Axis Lines
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.disableDepthTest(); // Draw on top
        RenderSystem.lineWidth(4.0f); // Make lines thicker
        
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f pose = poseStack.last().pose();
        
        // Axis Lines (Length 1.5 for visibility)
        float len = 1.5f;
        // N (-Z) - Red
        buf.vertex(pose, 0, 0, 0).color(255, 100, 100, 255).endVertex();
        buf.vertex(pose, 0, 0, -len).color(255, 0, 0, 255).endVertex();
        // S (+Z) - Dark Red
        buf.vertex(pose, 0, 0, 0).color(200, 200, 200, 255).endVertex();
        buf.vertex(pose, 0, 0, len).color(150, 50, 50, 255).endVertex();
        // E (+X) - Blue
        buf.vertex(pose, 0, 0, 0).color(100, 100, 255, 255).endVertex();
        buf.vertex(pose, len, 0, 0).color(0, 0, 255, 255).endVertex();
        // W (-X) - Dark Blue
        buf.vertex(pose, 0, 0, 0).color(200, 200, 200, 255).endVertex();
        buf.vertex(pose, -len, 0, 0).color(50, 50, 150, 255).endVertex();

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.lineWidth(1.0f); // Reset line width
        
        poseStack.popPose();
        
        // Draw labels N, S, E, W
        // N (-Z), S (+Z), E (+X), W (-X)
        drawCompassLabel(guiGraphics, cx, cy, radius * 1.8f, 0, 0, -1, "N", 0xFFFF0000);
        drawCompassLabel(guiGraphics, cx, cy, radius * 1.8f, 0, 0, 1, "S", 0xFFAAAAAA);
        drawCompassLabel(guiGraphics, cx, cy, radius * 1.8f, 1, 0, 0, "E", 0xFF0000FF);
        drawCompassLabel(guiGraphics, cx, cy, radius * 1.8f, -1, 0, 0, "W", 0xFFAAAAAA);
    }

    private void drawCompassLabel(GuiGraphics guiGraphics, int cx, int cy, float radius, float x, float y, float z, String text, int color) {
        Vector3f v = new Vector3f(x, y, z);
        
        // Apply rotations in order: Yaw then Pitch
        v.rotateY((float)Math.toRadians(cameraYaw));
        v.rotateX((float)Math.toRadians(cameraPitch));
        
        // Project to screen
        // Screen X = v.x
        // Screen Y = -v.y (Up in 3D is Down in 2D screen coords)
        
        int sx = cx + (int)(v.x * radius);
        int sy = cy + (int)(-v.y * radius);
        
        // Center text
        int w = this.font.width(text);
        guiGraphics.drawString(this.font, text, sx - w / 2, sy - 4, color, false);
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
    
    private void renderSettingsModal(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int settingsW = 200;
        int settingsH = 150;
        int settingsX = (this.width - settingsW) / 2;
        int settingsY = (this.height - settingsH) / 2;
        
        // Background
        guiGraphics.fill(settingsX, settingsY, settingsX + settingsW, settingsY + settingsH, 0xF0101010);
        
        // Title Bar
        guiGraphics.fill(settingsX, settingsY, settingsX + settingsW, settingsY + 25, 0xFF000000);
        
        // Border
        guiGraphics.renderOutline(settingsX, settingsY, settingsW, settingsH, 0xFF404040);
        
        // Title
        guiGraphics.drawCenteredString(this.font, Component.translatable("voxelview3d.settings.title"), settingsX + settingsW / 2, settingsY + 8, 0xFFFFFFFF);
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
        
        Component title = isCreatingMode ? (editingWaypoint != null ? Component.translatable("voxelview3d.waypoint.edit_title") : Component.translatable("voxelview3d.waypoint.create_title")) : Component.translatable("voxelview3d.waypoint.list_title");
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, modalY + 11, 0xFFFFFFFF);
        
        if (isCreatingMode) {
            // Render labels for create mode
            int centerX = this.width / 2;
            int coordW = 60;
            int coordGap = 10;
            int totalCoordW = (coordW * 3) + (coordGap * 2);
            int startCoordX = centerX - (totalCoordW / 2);
            
            guiGraphics.drawCenteredString(this.font, Component.translatable("voxelview3d.waypoint.x"), startCoordX + (coordW/2), modalY + 55, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(this.font, Component.translatable("voxelview3d.waypoint.y"), startCoordX + coordW + coordGap + (coordW/2), modalY + 55, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(this.font, Component.translatable("voxelview3d.waypoint.z"), startCoordX + (coordW + coordGap) * 2 + (coordW/2), modalY + 55, 0xFFAAAAAA);
            
            guiGraphics.drawCenteredString(this.font, Component.translatable("voxelview3d.waypoint.color"), centerX, modalY + 95, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(this.font, Component.translatable("voxelview3d.waypoint.icon"), centerX, modalY + 135, 0xFFAAAAAA);
            
        } else {
            // Render List
            int listX = modalX + 10;
            int listY = modalY + 35;
            int itemHeight = 30;
            int listHeight = modalH - 85; // Reserve space for button at bottom
            
            int listWidth = modalW - 20;
            
            // Enable Scissor to clip content
            guiGraphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
            
            for (int i = 0; i < ClientSettings.waypoints.size(); i++) {
                 ClientSettings.Waypoint wp = ClientSettings.waypoints.get(i);
                 int rowY = (int)(listY + (i * itemHeight) - scrollOffset);
                 
                 // Skip if out of view
                 if (rowY + itemHeight < listY || rowY > listY + listHeight) continue;
                 
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
                 ResourceLocation editIcon = new ResourceLocation("voxelview3d", "textures/edit.png");
                 RenderSystem.setShaderTexture(0, editIcon);
                 RenderSystem.enableBlend();
                 guiGraphics.blit(editIcon, rightEdge - 118, rowY + 7, 0, 0, 16, 16, 16, 16);
                 RenderSystem.disableBlend();
                 
                 // Eye (Visibility)
                 boolean eyeHover = mouseX >= rightEdge - 90 && mouseX < rightEdge - 70 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 guiGraphics.fill(rightEdge - 90, rowY + 5, rightEdge - 70, rowY + 25, eyeHover ? 0xFF606060 : 0xFF404040);
                 ResourceLocation eyeIcon = new ResourceLocation("voxelview3d", "textures/" + (wp.visible ? "nothide.png" : "hide.png"));
                 RenderSystem.setShaderTexture(0, eyeIcon);
                 RenderSystem.enableBlend();
                 guiGraphics.blit(eyeIcon, rightEdge - 88, rowY + 7, 0, 0, 16, 16, 16, 16);
                 RenderSystem.disableBlend();
                 
                 // TP
                 boolean tpHover = mouseX >= rightEdge - 60 && mouseX < rightEdge - 40 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 guiGraphics.fill(rightEdge - 60, rowY + 5, rightEdge - 40, rowY + 25, tpHover ? 0xFF606060 : 0xFF404040);
                 guiGraphics.drawCenteredString(this.font, "/TP", rightEdge - 50, rowY + 11, 0xFFFFFFFF);
                 
                 // Trash
                 boolean trashHover = mouseX >= rightEdge - 30 && mouseX < rightEdge - 10 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 guiGraphics.fill(rightEdge - 30, rowY + 5, rightEdge - 10, rowY + 25, trashHover ? 0xFF606060 : 0xFF404040);
                 ResourceLocation deleteIcon = new ResourceLocation("voxelview3d", trashHover ? "textures/deletehover.png" : "textures/delete.png");
                 RenderSystem.setShaderTexture(0, deleteIcon);
                 RenderSystem.enableBlend();
                 guiGraphics.blit(deleteIcon, rightEdge - 28, rowY + 7, 0, 0, 16, 16, 16, 16);
                 RenderSystem.disableBlend();
            }
            
            guiGraphics.disableScissor();
            
            // Scrollbar
            int totalContentHeight = ClientSettings.waypoints.size() * itemHeight;
            if (totalContentHeight > listHeight) {
                int scrollBarW = 6;
                int scrollBarX = listX + listWidth - scrollBarW - 2;
                int scrollBarH = (int)((float)listHeight * ((float)listHeight / totalContentHeight));
                if (scrollBarH < 20) scrollBarH = 20;
                
                int maxScroll = totalContentHeight - listHeight;
                int scrollY = listY + (int)((scrollOffset / maxScroll) * (listHeight - scrollBarH));
                
                guiGraphics.fill(scrollBarX, listY, scrollBarX + scrollBarW, listY + listHeight, 0x80000000); // Track
                guiGraphics.fill(scrollBarX, scrollY, scrollBarX + scrollBarW, scrollY + scrollBarH, 0xFF808080); // Thumb
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
        
        if (isDraggingMap) {
            // Map interaction
            if (button == 0) { // Left Click: Pan
                this.panX += dragX;
                this.panY += dragY;
                return true;
            } else if (button == 1) { // Right Click: Rotate
                this.cameraYaw += (float)dragX;
                return true;
            }
        }
        
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (showWaypointModal && !isCreatingMode) {
             // Check if hovering list
             int margin = 10;
             int modalW = this.width - (margin * 2);
             int modalH = this.height - (margin * 2);
             int modalX = margin;
             int modalY = margin;
             
             int listX = modalX + 10;
             int listY = modalY + 35;
             int listWidth = modalW - 20;
             int listHeight = modalH - 85; // Matches render logic
             
             if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                 this.scrollOffset -= delta * 20; // Scroll speed
                 if (this.scrollOffset < 0) this.scrollOffset = 0;
                 
                 // Calculate max scroll
                 int itemHeight = 30;
                 int totalHeight = ClientSettings.waypoints.size() * itemHeight;
                 int maxScroll = Math.max(0, totalHeight - listHeight);
                 
                 if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;
                 
                 return true;
             }
        }

        if (super.mouseScrolled(mouseX, mouseY, delta)) return true;
        
        if (isHoveringUI(mouseX, mouseY)) return false;

        // Map interaction: Zoom
        this.zoom += (float)delta * 0.5f;
        if (this.zoom < 0.5f) this.zoom = 0.5f;
        if (this.zoom > 15.0f) this.zoom = 15.0f;
        return true;
    }
    
    private boolean isHoveringUI(double mouseX, double mouseY) {
        // Bottom Menu
        if (mouseY >= this.height - 35) return true;
        
        // Modal
        if (showWaypointModal) {
            int margin = 10;
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

    private static class ImageToggleButton extends Button {
        private final ResourceLocation textureOn;
        private final ResourceLocation textureOff;
        private final Supplier<Boolean> stateSupplier;

        public ImageToggleButton(int x, int y, int width, int height, ResourceLocation textureOn, ResourceLocation textureOff, Supplier<Boolean> stateSupplier, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.textureOn = textureOn;
            this.textureOff = textureOff;
            this.stateSupplier = stateSupplier;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean isOn = stateSupplier.get();
            ResourceLocation texture = isOn ? textureOn : textureOff;
            
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.enableBlend();
            
            // Draw background if hovered
            if (this.isHovered) {
                 guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0x40FFFFFF);
            }
            
            // Draw icon centered
            int iconSize = width - 4;
            guiGraphics.blit(texture, getX() + 2, getY() + 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
            RenderSystem.disableBlend();
        }
    }
}