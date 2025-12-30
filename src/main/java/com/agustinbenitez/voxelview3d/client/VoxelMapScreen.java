package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
    private EditBox searchField; // Waypoint search
    private Button createWaypointBtn;
    private Button openCreateModeBtn; // Button in List mode to open Create mode
    private Button deleteAllBtn;
    private Button dimensionFilterBtn; // Filter waypoints by dimension
    private Button hideAllBtn; // Hide all waypoints in dimension
    // private Button cancelCreateBtn; // Removed, merged into X button
    
    private int selectedColor = 0xFFFF00; // Default Yellow
    private String selectedIcon = "icon1"; // Default Icon
    private String currentDimensionFilter = "minecraft:overworld"; // Default dimension filter
    private final int[] COLORS = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF, 0xFFFFFF, 0xFFA500};
    private final List<Button> colorButtons = new ArrayList<>();
    private final List<Button> iconButtons = new ArrayList<>();
    
    // Toggles
    private Button toggleVillagers;
    private Button toggleAnimals;
    private Button toggleEnemies;
    private Button togglePlayers;
    private Button toggleNightMode;
    private Button toggleChunkGrid;
    private Button togglePerspective;
    private Button waypointsBtn;
    private Button closeModalBtn;
    private Button closeMapBtn;
    
    // Settings Widgets
    private Button toggleCompassBtn;
    private Button toggleCoordsBtn;
    private Button renderDistanceBtn;
    private Button autoDeathPointsBtn;
    private Button fullBrightMapBtn;
    // private Button showChunkGridBtn; // Moved to main bar
    private Button closeSettingsBtn;

    public VoxelMapScreen() {
        this(false);
    }

    public VoxelMapScreen(boolean openWaypoints) {
        this(openWaypoints, false);
    }

    public VoxelMapScreen(boolean openWaypoints, boolean createMode) {
        super(Component.translatable("voxelview3d.title"));
        this.showWaypointModal = openWaypoints;
        this.isCreatingMode = createMode;
        
        // Auto-select current dimension when opening the screen
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            this.currentDimensionFilter = mc.level.dimension().location().toString();
        }
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
        x += btnWidth + 10; // Extra gap for separator
        
        // Night Mode Toggle
        if (this.minecraft.level != null) {
            // Auto-sync only on init if we want to follow game time initially
            // Or we can let it be persistent if static variable.
            // User request: "si es de noche se cambie solo a este estado noche"
            // So we sync it.
            long time = this.minecraft.level.getDayTime() % 24000;
            ClientSettings.isNightMode = time >= 13000 && time <= 23000;
        }
        
        toggleNightMode = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            new ResourceLocation("voxelview3d", "textures/night.png"), // On (Night)
            new ResourceLocation("voxelview3d", "textures/day.png"),   // Off (Day)
            () -> ClientSettings.isNightMode,
            b -> ClientSettings.isNightMode = !ClientSettings.isNightMode));
        x += btnWidth + 5;

        // Chunk Grid Toggle
        toggleChunkGrid = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            new ResourceLocation("voxelview3d", "textures/chunks.png"),
            new ResourceLocation("voxelview3d", "textures/chunkshide.png"),
            () -> ClientSettings.showChunkGrid,
            b -> ClientSettings.showChunkGrid = !ClientSettings.showChunkGrid));
        x += btnWidth + 5;

        // Perspective Toggle
        togglePerspective = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            new ResourceLocation("voxelview3d", "textures/side.png"),
            new ResourceLocation("voxelview3d", "textures/up.png"),
            () -> !ClientSettings.isTopDownView,
            b -> ClientSettings.isTopDownView = !ClientSettings.isTopDownView));
        x += btnWidth + 5;
        
        // Waypoints Button
        waypointsBtn = addRenderableWidget(new IconTextButton(x, buttonY, 120, 20, 
            Component.translatable("voxelview3d.waypoints"),
            new ResourceLocation("voxelview3d", "textures/iconpoints.png"),
            b -> toggleModal()));
        
        // Close Map Button (Top Right)
        closeMapBtn = addRenderableWidget(new Button(this.width - 25, 5, 20, 20, Component.literal("X"), b -> this.onClose(), Supplier::get) {
             @Override
             public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                 // Custom dark style
                 int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                 int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                 int textColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
                 
                 guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                 guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);
                 guiGraphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
             }
        });
        
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
            Button btn = new Button(startX + (i * (colSize + 4)), startY, colSize, colSize, Component.empty(), b -> {
                selectedColor = color;
            }, Supplier::get) {
                @Override
                public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                    // Custom dark style
                    int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                    int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                    
                    guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                    guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);
                }
            };
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
            Button btn = new Button(iconStartX + ((i - 1) * (iconSize + 2)), iconStartY, iconSize, iconSize, Component.empty(), b -> {
                selectedIcon = iconName;
            }, Supplier::get) {
                @Override
                public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                    // Custom dark style
                    int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                    int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                    
                    guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                    guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);
                }
            };
            addRenderableWidget(btn);
            iconButtons.add(btn);
        }
        
        // Create Button (Create Mode)
        // Ensure it doesn't overlap with icons (icons end at iconStartY + 20)
        int minButtonY = iconStartY + 30;
        int desiredButtonY = modalY + modalH - 30;
        int finalButtonY = Math.max(minButtonY, desiredButtonY);
        
        createWaypointBtn = addRenderableWidget(new Button(centerX - 100, finalButtonY, 200, 20, Component.translatable("voxelview3d.waypoint.save"), b -> {
            createWaypoint();
        }, Supplier::get) {
             @Override
             public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                 // Custom dark style
                 int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                 int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                 int textColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
                 
                 guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                 guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);
                 guiGraphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
             }
        });
        
        // --- List Mode Widgets ---
        
        // Search Field
        // Reduced width to fit dimension button (20px) and hideAll button (20px) + gaps (5px each)
        // Total reserved = 20 + 5 + 20 + 5 = 50px
        int searchW = modalW - 20 - 50;
        searchField = new EditBox(this.font, modalX + 10, modalY + 35, searchW, 20, Component.translatable("voxelview3d.waypoint.search"));
        searchField.setResponder(text -> scrollOffset = 0);
        addRenderableWidget(searchField);
        
        // Hide All Button
        hideAllBtn = addRenderableWidget(new ImageToggleButton(modalX + 10 + searchW + 5, modalY + 35, 20, 20,
            new ResourceLocation("voxelview3d", "textures/hideall.png"), // On (All Hidden)
            new ResourceLocation("voxelview3d", "textures/nothideall.png"), // Off (Visible/Mixed)
            () -> areAllHidden(currentDimensionFilter),
            b -> {
                boolean allHidden = areAllHidden(currentDimensionFilter);
                boolean newState = allHidden; // If hidden (true), set to visible (true). If visible (false), set to hidden (false).
                // Wait, if allHidden is true (hidden), clicking means "Show All" -> visible = true. Correct.
                // If allHidden is false (visible), clicking means "Hide All" -> visible = false. Correct.
                
                for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
                    if (wp.getDimension().equals(currentDimensionFilter)) {
                        wp.visible = newState;
                    }
                }
            }));
        
        // Dimension Filter Button
        dimensionFilterBtn = addRenderableWidget(new Button(modalX + 10 + searchW + 5 + 20 + 5, modalY + 35, 20, 20, Component.empty(), b -> {
            // Cycle Dimensions: Overworld -> Nether -> End -> Overworld
            List<String> dims = getAvailableDimensions();
            if (dims.isEmpty()) {
                currentDimensionFilter = "minecraft:overworld";
            } else {
                int index = dims.indexOf(currentDimensionFilter);
                if (index == -1) index = 0;
                index = (index + 1) % dims.size();
                currentDimensionFilter = dims.get(index);
            }
            // Reset scroll when changing dimension
            scrollOffset = 0;
        }, Supplier::get) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                // Draw Background
                super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
                
                // Draw Icon based on dimension
                ResourceLocation texture = getDimensionIcon(currentDimensionFilter);
                
                RenderSystem.setShaderTexture(0, texture);
                // Draw 16x16 icon centered in 20x20 button
                guiGraphics.blit(texture, getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
                
                // Check for duplicates of same type
                List<String> dims = getAvailableDimensions();
                List<String> sameTypeDims = new ArrayList<>();
                ResourceLocation myIcon = getDimensionIcon(currentDimensionFilter);
                
                for (String d : dims) {
                    if (getDimensionIcon(d).equals(myIcon)) {
                        sameTypeDims.add(d);
                    }
                }
                
                if (sameTypeDims.size() > 1) {
                    int idx = sameTypeDims.indexOf(currentDimensionFilter);
                    if (idx >= 0) {
                        String num = String.valueOf(idx + 1);
                        PoseStack poseStack = guiGraphics.pose();
                        poseStack.pushPose();
                        // Position in bottom right corner
                        // Button is 20x20. Text is small.
                        // Translate to x+14, y+12
                        poseStack.translate(getX() + 12, getY() + 12, 200); 
                        poseStack.scale(0.7f, 0.7f, 1f);
                        guiGraphics.drawString(font, num, 0, 0, 0xFFFFFF, true);
                        poseStack.popPose();
                    }
                }
            }
        });

        // Open Create Mode Button (in List Mode)
        openCreateModeBtn = addRenderableWidget(new IconTextButton(centerX - 100, modalY + modalH - 40, 200, 20, 
            Component.translatable("voxelview3d.waypoint.create_new"), 
            new ResourceLocation("voxelview3d", "textures/newpoint.png"),
            b -> {
                isCreatingMode = true;
                editingWaypoint = null;
                
                // Do NOT auto-switch filter. Keep current filter so user can create waypoints in other dimensions.
                // If user is in Nether tab, create waypoint in Nether.
                // The actual dimension string is picked up in createWaypoint() from currentDimensionFilter.
                
                // Pre-fill coordinates when entering create mode
                if (minecraft.player != null) {
                    wpX.setValue(String.valueOf(minecraft.player.getBlockX()));
                    wpY.setValue(String.valueOf(minecraft.player.getBlockY()));
                    wpZ.setValue(String.valueOf(minecraft.player.getBlockZ()));
                }
                waypointNameField.setValue("");
                selectedIcon = "icon1"; 
                updateModalVisibility();
            }));
        
        // Close Button (Small X in top right of the whole modal)
        closeModalBtn = addRenderableWidget(new Button(modalX + modalW - 25, modalY + 5, 20, 20, Component.literal("X"), b -> {
            if (isCreatingMode) {
                isCreatingMode = false;
                editingWaypoint = null;
                updateModalVisibility();
            } else {
                showWaypointModal = false;
                updateModalVisibility();
            }
        }, Supplier::get) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                // Custom dark style
                int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                int textColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
                
                guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);
                guiGraphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
            }
        });
        
        // --- Settings Modal Widgets ---
        int settingsW = 200;
        int settingsH = 245; // Reduced height to fit content better
        int settingsX = (this.width - settingsW) / 2;
        int settingsY = (this.height - settingsH) / 2;
        
        toggleCompassBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.compass").append(": ").append(Component.translatable(ClientSettings.showCompass ? "voxelview3d.settings.compass.on" : "voxelview3d.settings.compass.off")), b -> {
            ClientSettings.showCompass = !ClientSettings.showCompass;
            b.setMessage(Component.translatable("voxelview3d.settings.compass").append(": ").append(Component.translatable(ClientSettings.showCompass ? "voxelview3d.settings.compass.on" : "voxelview3d.settings.compass.off")));
        }).bounds(settingsX + 10, settingsY + 50, settingsW - 20, 20).build());
        
        toggleCoordsBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.coords").append(Component.translatable(ClientSettings.showCoords ? "voxelview3d.on" : "voxelview3d.off")), b -> {
            ClientSettings.showCoords = !ClientSettings.showCoords;
            b.setMessage(Component.translatable("voxelview3d.settings.coords").append(Component.translatable(ClientSettings.showCoords ? "voxelview3d.on" : "voxelview3d.off")));
        }).bounds(settingsX + 10, settingsY + 75, settingsW - 20, 20).build());
        
        renderDistanceBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.render_dist").append(String.valueOf(ClientSettings.renderDistance)), b -> {
            ClientSettings.renderDistance += 5;
            if (ClientSettings.renderDistance > 25) ClientSettings.renderDistance = 5;
            b.setMessage(Component.translatable("voxelview3d.settings.render_dist").append(String.valueOf(ClientSettings.renderDistance)));
        }).bounds(settingsX + 10, settingsY + 100, settingsW - 20, 20).build());

        autoDeathPointsBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.auto_death").append(Component.translatable(ClientSettings.autoDeathPoints ? "voxelview3d.on" : "voxelview3d.off")), b -> {
            ClientSettings.autoDeathPoints = !ClientSettings.autoDeathPoints;
            b.setMessage(Component.translatable("voxelview3d.settings.auto_death").append(Component.translatable(ClientSettings.autoDeathPoints ? "voxelview3d.on" : "voxelview3d.off")));
        }).bounds(settingsX + 10, settingsY + 125, settingsW - 20, 20).build());

        fullBrightMapBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.full_bright").append(Component.translatable(ClientSettings.fullBrightMap ? "voxelview3d.on" : "voxelview3d.off")), b -> {
            ClientSettings.fullBrightMap = !ClientSettings.fullBrightMap;
            b.setMessage(Component.translatable("voxelview3d.settings.full_bright").append(Component.translatable(ClientSettings.fullBrightMap ? "voxelview3d.on" : "voxelview3d.off")));
        }).bounds(settingsX + 10, settingsY + 150, settingsW - 20, 20).build());

        // Delete All Button (Settings)
        deleteAllBtn = addRenderableWidget(new Button(settingsX + 10, settingsY + 180, settingsW - 20, 20, Component.translatable("voxelview3d.waypoint.delete_all"), b -> {
             ClientSettings.waypoints.clear();
             WaypointManager.saveWaypoints();
        }, Supplier::get) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                // Draw background (Reddish?)
                int color = isHovered ? 0xFFFF0000 : 0xFFAA0000;
                guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80000000); // Black background
                guiGraphics.renderOutline(getX(), getY(), width, height, color); // Red border
                
                // Draw Icon
                ResourceLocation icon = new ResourceLocation("voxelview3d", "textures/deletehover.png");
                RenderSystem.setShaderTexture(0, icon);
                RenderSystem.enableBlend();
                guiGraphics.blit(icon, getX() + 5, getY() + 2, 0, 0, 16, 16, 16, 16);
                RenderSystem.disableBlend();
                
                // Draw Text (Red)
                guiGraphics.drawCenteredString(font, getMessage(), getX() + width / 2 + 10, getY() + (height - 8) / 2, 0xFFFF5555);
            }
        });

        closeSettingsBtn = addRenderableWidget(new Button(settingsX + settingsW - 25, settingsY + 10, 20, 20, Component.literal("X"), b -> {
            showSettingsModal = false;
            updateModalVisibility();
        }, Supplier::get) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                // Custom dark style
                int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                int textColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
                
                guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);
                guiGraphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
            }
        });
    }
    
    private List<ClientSettings.Waypoint> getFilteredWaypoints() {
        if (searchField == null) {
            return ClientSettings.waypoints;
        }
        
        String term = searchField.getValue().toLowerCase();
        List<ClientSettings.Waypoint> filtered = new ArrayList<>();
        
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            // Filter by Dimension
            if (!wp.getDimension().equals(currentDimensionFilter)) {
                continue;
            }
            
            // Filter by Search Term
            if (term.isEmpty() || wp.name.toLowerCase().contains(term)) {
                filtered.add(wp);
            }
        }
        
        // Sort by distance
        if (minecraft.player != null) {
            Player p = minecraft.player;
            filtered.sort((w1, w2) -> {
                double d1 = p.distanceToSqr(w1.x + 0.5, w1.y + 0.5, w1.z + 0.5);
                double d2 = p.distanceToSqr(w2.x + 0.5, w2.y + 0.5, w2.z + 0.5);
                return Double.compare(d1, d2);
            });
        }
        
        return filtered;
    }

    private void toggleModal() {
        showWaypointModal = !showWaypointModal;
        showSettingsModal = false; // Close settings if opening waypoints
        if (!isCreatingMode) {
             isCreatingMode = false; // Keep create mode if it was already set (via constructor)
        }
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
        
        // Auto-fill coords if opening create mode for new waypoint
        if (showCreate && editingWaypoint == null && Minecraft.getInstance().player != null) {
            // Only fill if empty to avoid overwriting user input or existing edits
            if (wpX.getValue().isEmpty() && wpY.getValue().isEmpty() && wpZ.getValue().isEmpty()) {
                 Player p = Minecraft.getInstance().player;
                 wpX.setValue(String.valueOf(p.getBlockX()));
                 wpY.setValue(String.valueOf(p.getBlockY()));
                 wpZ.setValue(String.valueOf(p.getBlockZ()));
            }
        }
        
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
        if (searchField != null) searchField.visible = showList;
        if (dimensionFilterBtn != null) dimensionFilterBtn.visible = showList;
        if (hideAllBtn != null) hideAllBtn.visible = showList;
        if (openCreateModeBtn != null) openCreateModeBtn.visible = showList;
        // if (deleteAllBtn != null) deleteAllBtn.visible = showList; // Removed from here
        
        // Settings Modal Widgets
        boolean showSettings = showSettingsModal;
        if (toggleCompassBtn != null) toggleCompassBtn.visible = showSettings;
        if (toggleCoordsBtn != null) toggleCoordsBtn.visible = showSettings;
        if (renderDistanceBtn != null) renderDistanceBtn.visible = showSettings;
        if (autoDeathPointsBtn != null) autoDeathPointsBtn.visible = showSettings;
        if (fullBrightMapBtn != null) fullBrightMapBtn.visible = showSettings;
        if (closeSettingsBtn != null) closeSettingsBtn.visible = showSettings;
        if (deleteAllBtn != null) deleteAllBtn.visible = showSettings;
        
        // Bottom Menu controls (Hide when any modal is open)
        boolean showBottom = !showWaypointModal && !showSettingsModal;
        if (toggleVillagers != null) toggleVillagers.visible = showBottom;
        if (toggleAnimals != null) toggleAnimals.visible = showBottom;
        if (toggleEnemies != null) toggleEnemies.visible = showBottom;
        if (togglePlayers != null) togglePlayers.visible = showBottom;
        if (toggleNightMode != null) toggleNightMode.visible = showBottom;
        if (toggleChunkGrid != null) toggleChunkGrid.visible = showBottom;
        if (togglePerspective != null) togglePerspective.visible = showBottom;
        if (waypointsBtn != null) waypointsBtn.visible = showBottom;
        if (closeMapBtn != null) closeMapBtn.visible = showBottom;
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
                    // Keep existing dimension or update? Let's keep existing to avoid moving it accidentally
                } else {
                    // Create new
                    // Use current dimension filter if creating from list, or player dimension if creating via keybind
                    // But keybind sets currentDimensionFilter to player dimension anyway.
                    // So we can just use currentDimensionFilter.
                    
                    // Actually, if we are in Nether list and create, we want it to be Nether.
                    String dim = currentDimensionFilter;
                    
                    ClientSettings.waypoints.add(new ClientSettings.Waypoint(name, x, y, z, selectedColor, selectedIcon, dim));
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
            // If user is typing in an input field, don't close the map
            if (this.getFocused() instanceof EditBox) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            this.onClose();
            return true;
        }

        if (KeyBindings.CREATE_WAYPOINT_KEY.matches(keyCode, scanCode)) {
            showWaypointModal = true;
            isCreatingMode = true;
            showSettingsModal = false; // Ensure settings are closed
            editingWaypoint = null;
            
            // Auto-switch filter to current dimension so the new waypoint will be visible after creation
            if (minecraft.level != null) {
                currentDimensionFilter = minecraft.level.dimension().location().toString();
            }
            
            // Pre-fill coordinates
            if (minecraft.player != null) {
                wpX.setValue(String.valueOf(minecraft.player.getBlockX()));
                wpY.setValue(String.valueOf(minecraft.player.getBlockY()));
                wpZ.setValue(String.valueOf(minecraft.player.getBlockZ()));
            }
            waypointNameField.setValue("");
            selectedIcon = "icon1";
            
            updateModalVisibility();
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
            int listY = modalY + 65; // Pushed down for search bar
            int itemHeight = 30; // Bigger items
            int listWidth = modalW - 20; 
            
            // Calculate visible items based on available height minus title (35) and bottom button space (50)
            int listHeight = modalH - 115;
            
            // Adjust clicked index based on scroll
            int clickedIndex = (int)((mouseY - listY + scrollOffset) / itemHeight);
            
            List<ClientSettings.Waypoint> waypoints = getFilteredWaypoints();
            
            // Check if click is within list bounds visually
            if (mouseY >= listY && mouseY <= listY + listHeight && 
                clickedIndex >= 0 && clickedIndex < waypoints.size() && 
                mouseX >= listX && mouseX <= listX + listWidth) {
                
                 ClientSettings.Waypoint wp = waypoints.get(clickedIndex);
                 int rowY = (int)(listY + (clickedIndex * itemHeight) - scrollOffset);
                 
                 // Buttons positions relative to list right edge
                 int rightEdge = listX + listWidth;
                 
                 // Buttons are roughly 20px wide
                 // Delete: Right - 30
                 // TP: Right - 60
                 // Visible: Right - 90
                 // Edit: Right - 120
                 // Share: Right - 150
                 
                 if (mouseX >= rightEdge - 150 && mouseX < rightEdge - 130) {
                     // Share
                     String msg = WaypointSharingHandler.createShareMessage(wp);
                     this.minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen(msg));
                     return true;
                 }
                 
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
                         this.onClose(); // Close the map after TP
                     }
                     return true;
                 }
                 
                 if (mouseX >= rightEdge - 30 && mouseX < rightEdge - 10) {
                     // Delete
                     ClientSettings.waypoints.remove(wp);
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
            float pitch = ClientSettings.isTopDownView ? 90.0f : cameraPitch;
            VoxelMapRenderer.renderMap(poseStack, zoom, pitch, cameraYaw, false, ClientSettings.renderDistance);
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
             
             // Draw Separator Pipe between Type toggles and Night Mode toggle
             // We added 4 toggle buttons (Villager, Animal, Enemy, Player)
             // Each is 20 wide + 5 gap. Starts at x=10.
             // 10 + 20+5 + 20+5 + 20+5 + 20 = 10 + 25 + 25 + 25 + 20 = 105
             // The next button (NightMode) is at x + 10 = 115.
             // Separator should be around 110.
             // Let's calculate it dynamically or hardcode it since button sizes are fixed.
             // x start = 10
             // 4 buttons * (20 width + 5 gap) = 100 width covered. Last gap is 5.
             // The position after last button is 10 + 25*3 + 20 = 105.
             // We added extra 5 gap (total 10 gap) before NightMode.
             // So separator at 105 + 5 = 110.
             
             int separatorX = 10 + (25 * 4); // 110
             int sepY = menuY + 5;
             int sepH = 25; // height of separator
             
             // Draw Pipe
             guiGraphics.fill(separatorX, sepY, separatorX + 1, sepY + sepH, 0xFF888888); // Gray vertical line
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
            // Draw Zoom Icon
            ResourceLocation zoomIcon = new ResourceLocation("voxelview3d", "textures/zoom.png");
            RenderSystem.setShaderTexture(0, zoomIcon);
            RenderSystem.enableBlend();
            guiGraphics.blit(zoomIcon, this.width - 100, menuY + 6, 0, 0, 16, 16, 16, 16);
            RenderSystem.disableBlend();

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
        
        // Draw icon on waypoints button handled by IconTextButton
        
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
        
        float effectivePitch = ClientSettings.isTopDownView ? 90.0f : cameraPitch;
        
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(cx, cy, 0);
        
        // Scale and Rotate (Flip Y for screen coords)
        poseStack.scale(radius, -radius, radius); 
        poseStack.mulPose(Axis.XP.rotationDegrees(effectivePitch));
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
        drawCompassLabel(guiGraphics, cx, cy, radius * 1.8f, 0, 0, -1, "N", 0xFFFF0000, effectivePitch);
        drawCompassLabel(guiGraphics, cx, cy, radius * 1.8f, 0, 0, 1, "S", 0xFFAAAAAA, effectivePitch);
        drawCompassLabel(guiGraphics, cx, cy, radius * 1.8f, 1, 0, 0, "E", 0xFF0000FF, effectivePitch);
        drawCompassLabel(guiGraphics, cx, cy, radius * 1.8f, -1, 0, 0, "W", 0xFFAAAAAA, effectivePitch);
    }

    private void drawCompassLabel(GuiGraphics guiGraphics, int cx, int cy, float radius, float x, float y, float z, String text, int color, float pitch) {
        Vector3f v = new Vector3f(x, y, z);
        
        // Apply rotations in order: Yaw then Pitch
        v.rotateY((float)Math.toRadians(cameraYaw));
        v.rotateX((float)Math.toRadians(pitch));
        
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
            
            float r = ((selectedColor >> 16) & 0xFF) / 255.0f;
            float g = ((selectedColor >> 8) & 0xFF) / 255.0f;
            float b = (selectedColor & 0xFF) / 255.0f;
            
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(r, g, b, 1.0F);
            guiGraphics.blit(iconLoc, btn.getX() + 2, btn.getY() + 2, 0, 0, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            
            if (selectedIcon.equals(iconName)) {
                guiGraphics.renderOutline(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0xFFFFFFFF);
            }
        }
    }
    
    private void renderSettingsModal(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int settingsW = 200;
        int settingsH = 245;
        int settingsX = (this.width - settingsW) / 2;
        int settingsY = (this.height - settingsH) / 2;
        
        // Background
        guiGraphics.fill(settingsX, settingsY, settingsX + settingsW, settingsY + settingsH, 0xF0101010);
        
        // Title Bar
        guiGraphics.fill(settingsX, settingsY, settingsX + settingsW, settingsY + 40, 0xFF000000);
        
        // Border
        guiGraphics.renderOutline(settingsX, settingsY, settingsW, settingsH, 0xFF404040);
        
        // Title
        guiGraphics.drawCenteredString(this.font, Component.translatable("voxelview3d.settings.title"), settingsX + settingsW / 2, settingsY + 15, 0xFFFFFFFF);
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
            int listY = modalY + 65; // Pushed down for search bar
            int itemHeight = 30;
            int listHeight = modalH - 115; // Reserve space for search bar (30) and button at bottom (85) -> total 115
            
            int listWidth = modalW - 20;
            
            // Enable Scissor to clip content
            guiGraphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
            
            List<ClientSettings.Waypoint> waypoints = getFilteredWaypoints();
            
            for (int i = 0; i < waypoints.size(); i++) {
                 ClientSettings.Waypoint wp = waypoints.get(i);
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
                 
                 float r = ((wp.color >> 16) & 0xFF) / 255.0f;
                 float g = ((wp.color >> 8) & 0xFF) / 255.0f;
                 float b = (wp.color & 0xFF) / 255.0f;
                 
                 RenderSystem.enableBlend();
                 RenderSystem.setShaderColor(r, g, b, 1.0F);
                 guiGraphics.blit(iconLoc, listX + 5, rowY + 5, 0, 0, 20, 20, 20, 20);
                 RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                 RenderSystem.disableBlend();
                 
                 // Name
                 guiGraphics.drawString(this.font, wp.name, listX + 35, rowY + 5, wp.color);
                 
                 // Distance
                 if (this.minecraft.player != null) {
                     double d = Math.sqrt(this.minecraft.player.distanceToSqr(wp.x + 0.5, wp.y + 0.5, wp.z + 0.5));
                     String distStr = String.format("%.1fm", d);
                     guiGraphics.drawString(this.font, distStr, listX + 35, rowY + 15, 0xFF888888);
                 }
                 
                 // Buttons: [Edit] [Eye] [TP] [Trash] (Aligned Right)
                 int rightEdge = listX + listWidth;
                 
                 // Coords (Before buttons)
                 String coords = String.format("[%d, %d, %d]", wp.x, wp.y, wp.z);
                 int coordsW = this.font.width(coords);
                 guiGraphics.drawString(this.font, coords, rightEdge - 170 - coordsW, rowY + 11, 0xFFAAAAAA);
                 
                 // Share
                 boolean shareHover = mouseX >= rightEdge - 150 && mouseX < rightEdge - 130 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 guiGraphics.fill(rightEdge - 150, rowY + 5, rightEdge - 130, rowY + 25, shareHover ? 0xFF606060 : 0xFF404040);
                 ResourceLocation shareIcon = new ResourceLocation("voxelview3d", "textures/share.png");
                 RenderSystem.setShaderTexture(0, shareIcon);
                 RenderSystem.enableBlend();
                 guiGraphics.blit(shareIcon, rightEdge - 148, rowY + 7, 0, 0, 16, 16, 16, 16);
                 RenderSystem.disableBlend();
                 
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
            int totalContentHeight = waypoints.size() * itemHeight;
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
             int listY = modalY + 65;
             int listWidth = modalW - 20;
             int listHeight = modalH - 115; // Matches render logic
             
             if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                 this.scrollOffset -= delta * 20; // Scroll speed
                 if (this.scrollOffset < 0) this.scrollOffset = 0;
                 
                 // Calculate max scroll
                 int itemHeight = 30;
                 int totalHeight = getFilteredWaypoints().size() * itemHeight;
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

    private static class IconTextButton extends Button {
        private final ResourceLocation icon;

        public IconTextButton(int x, int y, int width, int height, Component message, ResourceLocation icon, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.icon = icon;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Draw background
            int color = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80000000); // Semi-transparent black
            guiGraphics.renderOutline(getX(), getY(), width, height, color); // Border
            
            // Calculate positions to center content (Icon + Text)
            Font font = Minecraft.getInstance().font;
            int textWidth = font.width(getMessage());
            int iconWidth = 16;
            int gap = 3; // Small gap
            int totalContentWidth = iconWidth + gap + textWidth;
            
            int contentX = getX() + (width - totalContentWidth) / 2;
            int iconY = getY() + (height - 16) / 2;
            int textY = getY() + (height - 8) / 2;
            
            // Draw Icon
            RenderSystem.setShaderTexture(0, icon);
            RenderSystem.enableBlend();
            guiGraphics.blit(icon, contentX, iconY, 0, 0, 16, 16, 16, 16);
            RenderSystem.disableBlend();
            
            // Draw Text
            guiGraphics.drawString(font, getMessage(), contentX + iconWidth + gap, textY, color);
        }
    }
    
    private boolean areAllHidden(String dimension) {
        boolean hasWaypoints = false;
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (wp.getDimension().equals(dimension)) {
                hasWaypoints = true;
                if (wp.visible) return false;
            }
        }
        // If no waypoints, treat as "All Hidden" (so button shows hidden icon)? 
        // Or false?
        // If hasWaypoints is false, return true (default state).
        return true;
    }
    
    private List<String> getAvailableDimensions() {
        List<String> dims = new ArrayList<>();
        
        // Add default dimensions (Overworld, Nether, End)
        dims.add("minecraft:overworld");
        dims.add("minecraft:the_nether");
        dims.add("minecraft:the_end");
        
        // Always include current dimension if available and not already added
        if (minecraft.level != null) {
            String current = minecraft.level.dimension().location().toString();
            if (!dims.contains(current)) {
                dims.add(current);
            }
        }
        
        // Add dimensions from waypoints
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            String d = wp.getDimension();
            if (!dims.contains(d)) {
                dims.add(d);
            }
        }
        
        // Sort: Overworld, Nether, End, others
        dims.sort((a, b) -> {
            int sA = getDimensionScore(a);
            int sB = getDimensionScore(b);
            if (sA != sB) return Integer.compare(sA, sB);
            return a.compareTo(b);
        });
        return dims;
    }

    private int getDimensionScore(String dim) {
        if (dim.equals("minecraft:overworld")) return 0;
        if (dim.equals("minecraft:the_nether")) return 1;
        if (dim.equals("minecraft:the_end")) return 2;
        return 3;
    }
    
    private ResourceLocation getDimensionIcon(String dim) {
        if (dim.contains("nether")) return new ResourceLocation("minecraft", "textures/block/netherrack.png");
        if (dim.contains("end") && !dim.contains("render")) return new ResourceLocation("minecraft", "textures/block/end_stone.png");
        return new ResourceLocation("minecraft", "textures/block/grass_block_side.png");
    }
}