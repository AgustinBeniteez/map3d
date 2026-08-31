package com.agustinbenitez.voxelview3d.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.util.Mth;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;

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
    private boolean mapDragMoved = false;
    private double mapPressX;
    private double mapPressY;
    private ClientSettings.Waypoint editingWaypoint = null;
    private BlockPos selectedMapBlock;
    private int blockMenuX;
    private int blockMenuY;
    
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
    private LayerSlider layerSlider;
    private Button closeModalBtn;
    private Button closeMapBtn;
    private Button goBtn;
    private Button selectedWaypointBtn;
    private Button selectedTeleportBtn;
    
    // Settings Widgets
    private Button toggleCompassBtn;
    private Button toggleCoordsBtn;
    private Button renderDistanceBtn;
    private Button autoDeathPointsBtn;
    // fullBrightMapBtn removed
    private Button hudSizeBtn;
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
        
        // Load settings
        SettingsManager.loadSettings();
        
        // Auto-select current dimension when opening the screen
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            this.currentDimensionFilter = mc.level.dimension().identifier().toString();
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
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/types/villager.png"),
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/types/villager_hide.png"),
            () -> ClientSettings.showVillagers,
            b -> { ClientSettings.showVillagers = !ClientSettings.showVillagers; SettingsManager.saveSettings(); }));
        x += btnWidth + 5;
        
        toggleAnimals = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/types/animal.png"),
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/types/animal_hide.png"),
            () -> ClientSettings.showAnimals,
            b -> { ClientSettings.showAnimals = !ClientSettings.showAnimals; SettingsManager.saveSettings(); }));
        x += btnWidth + 5;
        
        toggleEnemies = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/types/enemie.png"),
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/types/enemie_hide.png"),
            () -> ClientSettings.showEnemies,
            b -> { ClientSettings.showEnemies = !ClientSettings.showEnemies; SettingsManager.saveSettings(); }));
        x += btnWidth + 5;
        
        togglePlayers = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/types/player.png"),
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/types/player_hide.png"),
            () -> ClientSettings.showPlayers,
            b -> { ClientSettings.showPlayers = !ClientSettings.showPlayers; SettingsManager.saveSettings(); }));
        x += btnWidth + 10; // Extra gap for separator
        
        // Night Mode Toggle
        if (this.minecraft.level != null) {
            // Auto-sync only on init if we want to follow game time initially
            // Or we can let it be persistent if static variable.
            // User request: "si es de noche se cambie solo a este estado noche"
            // So we sync it.
            long time = this.minecraft.level.getGameTime() % 24000;
            ClientSettings.isNightMode = time >= 13000 && time <= 23000;
        }
        
        toggleNightMode = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/night.png"), // On (Night)
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/day.png"),   // Off (Day)
            () -> ClientSettings.isNightMode,
            b -> ClientSettings.isNightMode = !ClientSettings.isNightMode) {
                
                private final Identifier caveIcon = Identifier.fromNamespaceAndPath("voxelview3d", "textures/cave.png");
                
                @Override
                protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                    boolean isOn = stateSupplier.get();
                    
                    // Logic:
                    // If Underground:
                    //   - Show Cave Icon if Night Mode is ON (Default for Cave)
                    //   - Show Day Icon if Night Mode is OFF (User requested bright mode)
                    // If Surface:
                    //   - Show Night Icon if Night Mode is ON
                    //   - Show Day Icon if Night Mode is OFF
                    
                    Identifier texture;
                    if (VoxelMapRenderer.isUndergroundState) {
                         texture = isOn ? caveIcon : textureOff;
                    } else {
                         texture = isOn ? textureOn : textureOff;
                    }
                    
                    // texture texture
                    // enableBlend
                    
                    // Draw background if hovered
                    if (this.isHovered) {
                         graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x40FFFFFF);
                    }
                    
                    // Draw icon centered
                    int iconSize = width - 4;
                    graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, getX() + 2, getY() + 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
                    // disableBlend
                    
                    // Tooltip logic removed from here to fix scaling issue

                }
            });
        x += btnWidth + 5;

        // Chunk Grid Toggle
        toggleChunkGrid = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/chunks.png"),
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/chunkshide.png"),
            () -> ClientSettings.showChunkGrid,
            b -> { ClientSettings.showChunkGrid = !ClientSettings.showChunkGrid; SettingsManager.saveSettings(); }));
        x += btnWidth + 5;

        // Perspective Toggle
        togglePerspective = addRenderableWidget(new ImageToggleButton(x, buttonY, btnWidth, btnWidth,
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/side.png"),
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/up.png"),
            () -> !ClientSettings.isTopDownView,
            b -> {
                ClientSettings.isTopDownView = !ClientSettings.isTopDownView;
                clearBlockSelection();
                SettingsManager.saveSettings();
            }));
        x += btnWidth + 5;
        
        // Waypoints Button
        waypointsBtn = addRenderableWidget(new IconTextButton(x, buttonY, 120, 20, 
            Component.translatable("voxelview3d.waypoints"),
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/iconpoints.png"),
            b -> toggleModal()));
        
        // Layer Slider
        int minY = -64;
        int maxY = 320;
        if (this.minecraft.level != null) {
            minY = this.minecraft.level.getMinY();
            maxY = this.minecraft.level.getMaxY();
        }
        
        // Slider moved to left, below settings button (Settings at 10, 10, size 20)
        // Settings Bottom = 30. Gap 20 (moved from 10). Slider Y = 50.
        // X = 15 (moved from 10) to avoid text clipping.
        // Height 50.
        layerSlider = addRenderableWidget(new LayerSlider(15, 50, 15, 50, minY, maxY));
        
        // "Go" Button (Reset to Player)
        // Just above bottom menu (height - 25). So put at height - 50.
        // User requested to move it up "un poco mas". Let's try height - 65.
        // Width 40, Height 20.
        int goBtnY = this.height - 65;
        goBtn = addRenderableWidget(new Button.Plain(10, goBtnY, 40, 20, Component.literal("Go"), b -> {
            if (layerSlider != null) {
                layerSlider.resetToPlayer();
            }
            // Reset Pan to center on player
            this.panX = 0;
            this.panY = 0;
            clearBlockSelection();
        }, Supplier::get) {
             @Override
             protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                 // Custom render
                 int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                 int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                 int textColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
                 
                 graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                 graphics.outline(getX(), getY(), width, height, borderColor);
                 
                 // Render Player Face
                 if (minecraft.player != null) {
                     Identifier skin = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID()).getSkin().body().texturePath();
                     // texture skin
                     // Head is at u=8, v=8, size=8x8.
                     // Blit: x, y, size, size, u, v, uWidth, vHeight, textureWidth, textureHeight
                     graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, getX() + 2, getY() + 2, 16, 16, 8, 8, 8, 8, 64, 64);
                     
                     // Draw 2nd layer (Hat)
                     graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, getX() + 2, getY() + 2, 16, 16, 40, 8, 8, 8, 64, 64);
                 }
                 
                 // Draw "Go" Text
                 graphics.text(font, getMessage(), getX() + 20, getY() + (height - 8) / 2, textColor);
             }
        });
        
        // Close Map Button (Top Right)
        closeMapBtn = addRenderableWidget(new Button.Plain(this.width - 25, 5, 20, 20, Component.literal("X"), b -> this.onClose(), Supplier::get) {
             @Override
             protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                 // Custom dark style
                 int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                 int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                 int textColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
                 
                 graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                 graphics.outline(getX(), getY(), width, height, borderColor);
                 graphics.centeredText(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
             }
        });

        initBlockSelectionWidgets();
        
        // Modal Widgets (hidden by default)
        initModalWidgets();
        
        updateModalVisibility();
    }

    private void initBlockSelectionWidgets() {
        selectedWaypointBtn = addRenderableWidget(Button.builder(
                Component.translatable("voxelview3d.selection.create_waypoint"),
                b -> createWaypointAtSelectedBlock())
                .bounds(blockMenuX + 5, blockMenuY + 23, 125, 20)
                .build());
        selectedTeleportBtn = addRenderableWidget(Button.builder(
                Component.translatable("voxelview3d.selection.teleport"),
                b -> teleportToSelectedBlock())
                .bounds(blockMenuX + 135, blockMenuY + 23, 50, 20)
                .build());
        selectedWaypointBtn.visible = false;
        selectedTeleportBtn.visible = false;
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
            Button btn = new Button.Plain(startX + (i * (colSize + 4)), startY, colSize, colSize, Component.empty(), b -> {
                selectedColor = color;
            }, Supplier::get) {
                @Override
                protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                    // Custom dark style
                    int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                    int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                    
                    graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                    graphics.outline(getX(), getY(), width, height, borderColor);
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
            Button btn = new Button.Plain(iconStartX + ((i - 1) * (iconSize + 2)), iconStartY, iconSize, iconSize, Component.empty(), b -> {
                selectedIcon = iconName;
            }, Supplier::get) {
                @Override
                protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                    // Custom dark style
                    int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                    int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                    
                    graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                    graphics.outline(getX(), getY(), width, height, borderColor);
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
        
        createWaypointBtn = addRenderableWidget(new Button.Plain(centerX - 100, finalButtonY, 200, 20, Component.translatable("voxelview3d.waypoint.save"), b -> {
            createWaypoint();
        }, Supplier::get) {
             @Override
             protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                 // Custom dark style
                 int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                 int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                 int textColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
                 
                 graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                 graphics.outline(getX(), getY(), width, height, borderColor);
                 graphics.centeredText(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
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
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/hideall.png"), // On (All Hidden)
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/nothideall.png"), // Off (Visible/Mixed)
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
        dimensionFilterBtn = addRenderableWidget(new Button.Plain(modalX + 10 + searchW + 5 + 20 + 5, modalY + 35, 20, 20, Component.empty(), b -> {
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
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                // Draw Background
                super.extractContents(graphics, mouseX, mouseY, a);
                
                // Draw Icon based on dimension
                Identifier texture = getDimensionIcon(currentDimensionFilter);
                
                // texture texture
                // Draw 16x16 icon centered in 20x20 button
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
                
                // Check for duplicates of same type
                List<String> dims = getAvailableDimensions();
                List<String> sameTypeDims = new ArrayList<>();
                Identifier myIcon = getDimensionIcon(currentDimensionFilter);
                
                for (String d : dims) {
                    if (getDimensionIcon(d).equals(myIcon)) {
                        sameTypeDims.add(d);
                    }
                }
                
                if (sameTypeDims.size() > 1) {
                    int idx = sameTypeDims.indexOf(currentDimensionFilter);
                    if (idx >= 0) {
                        String num = String.valueOf(idx + 1);
                        PoseStack poseStack = new PoseStack();
                        poseStack.pushPose();
                        // Position in bottom right corner
                        // Button is 20x20. Text is small.
                        // Translate to x+14, y+12
                        poseStack.translate(getX() + 12, getY() + 12, 200); 
                        poseStack.scale(0.7f, 0.7f, 1f);
                        graphics.text(font, num, 0, 0, 0xFFFFFF, true);
                        poseStack.popPose();
                    }
                }
            }
        });

        // Open Create Mode Button (in List Mode)
        openCreateModeBtn = addRenderableWidget(new IconTextButton(centerX - 100, modalY + modalH - 40, 200, 20, 
            Component.translatable("voxelview3d.waypoint.create_new"), 
            Identifier.fromNamespaceAndPath("voxelview3d", "textures/newpoint.png"),
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
        closeModalBtn = addRenderableWidget(new Button.Plain(modalX + modalW - 25, modalY + 5, 20, 20, Component.literal("X"), b -> {
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
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                // Custom dark style
                int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                int textColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
                
                graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                graphics.outline(getX(), getY(), width, height, borderColor);
                graphics.centeredText(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
            }
        });
        
        // --- Settings Modal Widgets ---
        int settingsW = 200;
        int settingsH = 270; // Increased height
        int settingsX = (this.width - settingsW) / 2;
        int settingsY = (this.height - settingsH) / 2;
        
        toggleCompassBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.compass").append(": ").append(Component.translatable(ClientSettings.showCompass ? "voxelview3d.settings.compass.on" : "voxelview3d.settings.compass.off")), b -> {
            ClientSettings.showCompass = !ClientSettings.showCompass;
            b.setMessage(Component.translatable("voxelview3d.settings.compass").append(": ").append(Component.translatable(ClientSettings.showCompass ? "voxelview3d.settings.compass.on" : "voxelview3d.settings.compass.off")));
            SettingsManager.saveSettings();
        }).bounds(settingsX + 10, settingsY + 50, settingsW - 20, 20).build());
        
        toggleCoordsBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.coords").append(Component.translatable(ClientSettings.showCoords ? "voxelview3d.on" : "voxelview3d.off")), b -> {
            ClientSettings.showCoords = !ClientSettings.showCoords;
            b.setMessage(Component.translatable("voxelview3d.settings.coords").append(Component.translatable(ClientSettings.showCoords ? "voxelview3d.on" : "voxelview3d.off")));
            SettingsManager.saveSettings();
        }).bounds(settingsX + 10, settingsY + 75, settingsW - 20, 20).build());
        
        renderDistanceBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.render_dist").append(String.valueOf(ClientSettings.renderDistance)), b -> {
            ClientSettings.renderDistance += 1;
            if (ClientSettings.renderDistance > 15) ClientSettings.renderDistance = 5;
            b.setMessage(Component.translatable("voxelview3d.settings.render_dist").append(String.valueOf(ClientSettings.renderDistance)));
            SettingsManager.saveSettings();
        }).bounds(settingsX + 10, settingsY + 100, settingsW - 20, 20).build());

        autoDeathPointsBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.auto_death").append(Component.translatable(ClientSettings.autoDeathPoints ? "voxelview3d.on" : "voxelview3d.off")), b -> {
            ClientSettings.autoDeathPoints = !ClientSettings.autoDeathPoints;
            b.setMessage(Component.translatable("voxelview3d.settings.auto_death").append(Component.translatable(ClientSettings.autoDeathPoints ? "voxelview3d.on" : "voxelview3d.off")));
            SettingsManager.saveSettings();
        }).bounds(settingsX + 10, settingsY + 125, settingsW - 20, 20).build());

        // fullBrightMapBtn removed

        hudSizeBtn = addRenderableWidget(Button.builder(Component.translatable("voxelview3d.settings.hud_size").append(": ").append(Component.translatable("voxelview3d.settings.hud_size." + ClientSettings.hudSize.name())), b -> {
            switch (ClientSettings.hudSize) {
                case SMALL -> ClientSettings.hudSize = ClientSettings.HudSize.MEDIUM;
                case MEDIUM -> ClientSettings.hudSize = ClientSettings.HudSize.LARGE;
                case LARGE -> ClientSettings.hudSize = ClientSettings.HudSize.SMALL;
            }
            b.setMessage(Component.translatable("voxelview3d.settings.hud_size").append(": ").append(Component.translatable("voxelview3d.settings.hud_size." + ClientSettings.hudSize.name())));
            SettingsManager.saveSettings();
        }).bounds(settingsX + 10, settingsY + 150, settingsW - 20, 20).build());

        // Delete All Button (Settings)
        deleteAllBtn = addRenderableWidget(new Button.Plain(settingsX + 10, settingsY + 210, settingsW - 20, 20, Component.translatable("voxelview3d.waypoint.delete_all"), b -> {
             ClientSettings.waypoints.clear();
             WaypointManager.saveWaypoints();
        }, Supplier::get) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                // Draw background (Reddish?)
                int color = isHovered ? 0xFFFF0000 : 0xFFAA0000;
                graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80000000); // Black background
                graphics.outline(getX(), getY(), width, height, color); // Red border
                
                // Draw Icon
                Identifier icon = Identifier.fromNamespaceAndPath("voxelview3d", "textures/deletehover.png");
                // texture icon
                // enableBlend
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, icon, getX() + 5, getY() + 2, 0, 0, 16, 16, 16, 16);
                // disableBlend
                
                // Draw Text (Red)
                graphics.centeredText(font, getMessage(), getX() + width / 2 + 10, getY() + (height - 8) / 2, 0xFFFF5555);
            }
        });

        closeSettingsBtn = addRenderableWidget(new Button.Plain(settingsX + settingsW - 25, settingsY + 10, 20, 20, Component.literal("X"), b -> {
            showSettingsModal = false;
            updateModalVisibility();
        }, Supplier::get) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                // Custom dark style
                int bgColor = isHovered ? 0xFF202020 : 0xFF101010;
                int borderColor = isHovered ? 0xFFAAAAAA : 0xFF606060;
                int textColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
                
                graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
                graphics.outline(getX(), getY(), width, height, borderColor);
                graphics.centeredText(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
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
        if (layerSlider != null) layerSlider.visible = showBottom;
        if (closeMapBtn != null) closeMapBtn.visible = showBottom;
        if (goBtn != null) goBtn.visible = showBottom;
        
        if (hudSizeBtn != null) {
            hudSizeBtn.visible = showSettingsModal && !showWaypointModal;
        }

        updateBlockSelectionVisibility();
    }

    private void selectMapBlock(double screenX, double screenY, double menuMouseX,
                                double menuMouseY, int effectiveWidth, int effectiveHeight) {
        selectedMapBlock = VoxelMapRenderer.pickBlock(screenX, screenY);
        if (selectedMapBlock != null) {
            int menuWidth = 190;
            int menuHeight = 48;
            int desiredX = (int)menuMouseX + 8;
            int desiredY = (int)menuMouseY + 8;
            if (desiredY + menuHeight > effectiveHeight - 35) {
                desiredY = (int)menuMouseY - menuHeight - 8;
            }
            blockMenuX = Mth.clamp(desiredX, 4, Math.max(4, effectiveWidth - menuWidth - 4));
            blockMenuY = Mth.clamp(desiredY, 4, Math.max(4, effectiveHeight - menuHeight - 39));
        }
        updateBlockSelectionVisibility();
    }

    private void clearBlockSelection() {
        selectedMapBlock = null;
        updateBlockSelectionVisibility();
    }

    private void updateBlockSelectionVisibility() {
        if (selectedWaypointBtn == null || selectedTeleportBtn == null) return;

        boolean visible = selectedMapBlock != null && !showWaypointModal && !showSettingsModal;
        selectedWaypointBtn.visible = visible;
        selectedTeleportBtn.visible = visible;
        if (visible) {
            selectedWaypointBtn.setX(blockMenuX + 5);
            selectedWaypointBtn.setY(blockMenuY + 23);
            selectedTeleportBtn.setX(blockMenuX + 135);
            selectedTeleportBtn.setY(blockMenuY + 23);
            selectedTeleportBtn.active = canUseTeleportCommand();
        }
    }

    private boolean canUseTeleportCommand() {
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) return false;
        var root = minecraft.getConnection().getCommands().getRoot();
        return root.getChild("tp") != null || root.getChild("teleport") != null;
    }

    private boolean teleportToCoordinates(double x, double y, double z) {
        if (!canUseTeleportCommand() || minecraft.player == null) return false;

        minecraft.player.connection.sendCommand("tp " + x + " " + y + " " + z);
        this.onClose();
        return true;
    }

    private void teleportToSelectedBlock() {
        if (selectedMapBlock == null) return;
        teleportToCoordinates(selectedMapBlock.getX() + 0.5,
                selectedMapBlock.getY() + 1.0,
                selectedMapBlock.getZ() + 0.5);
    }

    private void createWaypointAtSelectedBlock() {
        if (selectedMapBlock == null) return;

        showWaypointModal = true;
        showSettingsModal = false;
        isCreatingMode = true;
        editingWaypoint = null;
        if (minecraft.level != null) {
            currentDimensionFilter = minecraft.level.dimension().identifier().toString();
        }

        wpX.setValue(String.valueOf(selectedMapBlock.getX()));
        wpY.setValue(String.valueOf(selectedMapBlock.getY() + 1));
        wpZ.setValue(String.valueOf(selectedMapBlock.getZ()));
        waypointNameField.setValue("");
        selectedIcon = "icon1";
        updateModalVisibility();
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
    public boolean keyPressed(KeyEvent event) {
        if (KeyBindings.OPEN_MAP_KEY.matches(event)) {
            // If user is typing in an input field, don't close the map
            if (this.getFocused() instanceof EditBox) {
                return super.keyPressed(event);
            }
            this.onClose();
            return true;
        }

        if (KeyBindings.CREATE_WAYPOINT_KEY.matches(event)) {
            showWaypointModal = true;
            isCreatingMode = true;
            showSettingsModal = false; // Ensure settings are closed
            editingWaypoint = null;
            
            // Auto-switch filter to current dimension so the new waypoint will be visible after creation
            if (minecraft.level != null) {
                currentDimensionFilter = minecraft.level.dimension().identifier().toString();
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

        if (event.key() == 256) { // ESC
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
        
        return super.keyPressed(event);
    }
    
    // State tracking for auto-switching
    private boolean lastUndergroundState = false;

    @Override
    public void tick() {
        super.tick();
        
        // Auto-switch Night Mode based on Underground State
        boolean currentUnderground = VoxelMapRenderer.isUndergroundState;
        
        if (currentUnderground != lastUndergroundState) {
            if (currentUnderground) {
                // Entered Cave -> Enable Lighting (Night Mode) to show torches
                ClientSettings.isNightMode = true;
            } else {
                // Left Cave -> Sync with World Time
                if (this.minecraft.level != null) {
                    long time = this.minecraft.level.getGameTime() % 24000;
                    ClientSettings.isNightMode = time >= 13000 && time <= 23000;
                }
            }
            lastUndergroundState = currentUnderground;
        }
        
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        float scale = getHudScale();
        double scaledX = mouseX / scale;
        double scaledY = mouseY / scale;
        
        if (super.mouseClicked(event, doubleClick)) return true;
        
        int effectiveWidth = (int)(this.width / scale);
        int effectiveHeight = (int)(this.height / scale);
        
        // Settings Button Click (Top Left)
        if (!showWaypointModal && !showSettingsModal) {
            int btnX = 10;
            int btnY = 10;
            int btnSize = 20;
            if (scaledX >= btnX && scaledX <= btnX + btnSize && scaledY >= btnY && scaledY <= btnY + btnSize) {
                showSettingsModal = true;
                updateModalVisibility();
                return true;
            }
        }
        
        if (showWaypointModal && !isCreatingMode) {
            // Check clicks on the Waypoint List (Full Width)
            int margin = 10;
            int modalW = effectiveWidth - (margin * 2);
            int modalH = effectiveHeight - (margin * 2);
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
            int clickedIndex = (int)((scaledY - listY + scrollOffset) / itemHeight);
            
            List<ClientSettings.Waypoint> waypoints = getFilteredWaypoints();
            
            // Check if click is within list bounds visually
            if (scaledY >= listY && scaledY <= listY + listHeight && 
                clickedIndex >= 0 && clickedIndex < waypoints.size() && 
                scaledX >= listX && scaledX <= listX + listWidth) {
                
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
                 
                 if (scaledX >= rightEdge - 150 && scaledX < rightEdge - 130) {
                     // Share
                     String msg = WaypointSharingHandler.createShareMessage(wp);
                     this.minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen(msg, false));
                     return true;
                 }
                 
                 if (scaledX >= rightEdge - 120 && scaledX < rightEdge - 100) {
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
                 
                 if (scaledX >= rightEdge - 90 && scaledX < rightEdge - 70) {
                     // Toggle Visibility
                     wp.visible = !wp.visible;
                     WaypointManager.saveWaypoints();
                     return true;
                 }
                 
                 if (scaledX >= rightEdge - 60 && scaledX < rightEdge - 40) {
                     // TP
                     teleportToCoordinates(wp.x, wp.y, wp.z);
                     return true;
                 }
                 
                 if (scaledX >= rightEdge - 30 && scaledX < rightEdge - 10) {
                     // Delete
                     ClientSettings.waypoints.remove(wp);
                     WaypointManager.saveWaypoints();
                     return true;
                 }
            }
        }
        
        // If not hovering any UI element, start dragging map
        if (!isHoveringUI(scaledX, scaledY) && (button == 0 || button == 1)) {
            isDraggingMap = true;
            mapDragMoved = false;
            mapPressX = mouseX;
            mapPressY = mouseY;
            return true;
        }
        
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        boolean handledByMap = isDraggingMap;
        isDraggingMap = false;
        float scale = getHudScale();
        if (handledByMap) {
            if (button == 1 && !mapDragMoved && !showWaypointModal && !showSettingsModal) {
                int effectiveWidth = (int)(this.width / scale);
                int effectiveHeight = (int)(this.height / scale);
                selectMapBlock(mouseX, mouseY, mouseX / scale, mouseY / scale,
                        effectiveWidth, effectiveHeight);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    private float getHudScale() {
        return switch (ClientSettings.hudSize) {
            case SMALL -> 0.5f;
            case MEDIUM -> 0.75f;
            case LARGE -> 1.0f;
        };
    }

    private void updateLayout(int width, int height) {
        // Bottom Menu
        int buttonY = height - 25;
        int btnWidth = 20;
        int x = 10;
        
        if (toggleVillagers != null) { toggleVillagers.setX(x); toggleVillagers.setY(buttonY); } x += btnWidth + 5;
        if (toggleAnimals != null) { toggleAnimals.setX(x); toggleAnimals.setY(buttonY); } x += btnWidth + 5;
        if (toggleEnemies != null) { toggleEnemies.setX(x); toggleEnemies.setY(buttonY); } x += btnWidth + 5;
        if (togglePlayers != null) { togglePlayers.setX(x); togglePlayers.setY(buttonY); } x += btnWidth + 10;
        
        if (toggleNightMode != null) { toggleNightMode.setX(x); toggleNightMode.setY(buttonY); } x += btnWidth + 5;
        if (toggleChunkGrid != null) { toggleChunkGrid.setX(x); toggleChunkGrid.setY(buttonY); } x += btnWidth + 5;
        if (togglePerspective != null) { togglePerspective.setX(x); togglePerspective.setY(buttonY); } x += btnWidth + 5;
        
        if (waypointsBtn != null) { waypointsBtn.setX(x); waypointsBtn.setY(buttonY); }
        
        // Go Button
        if (goBtn != null) {
            goBtn.setY(height - 65);
        }
        
        // Close Map Button
        if (closeMapBtn != null) {
            closeMapBtn.setX(width - 25);
        }
        
        // Modals
        int margin = 10;
        int modalW = width - (margin * 2);
        int modalH = height - (margin * 2);
        int modalX = margin;
        int modalY = margin;
        int centerX = width / 2;
        
        // List Mode
        if (searchField != null) {
            searchField.setWidth(modalW - 20 - 25 - 5 - 20 - 5); // Recalculate width
            searchField.setX(modalX + 10);
            searchField.setY(modalY + 35);
        }
        
        int searchW = (searchField != null) ? searchField.getWidth() : 200; // Approximate if null
        
        if (hideAllBtn != null) {
            hideAllBtn.setX(modalX + 10 + searchW + 5);
            hideAllBtn.setY(modalY + 35);
        }
        
        if (dimensionFilterBtn != null) {
            dimensionFilterBtn.setX(modalX + 10 + searchW + 5 + 20 + 5);
            dimensionFilterBtn.setY(modalY + 35);
        }
        
        if (openCreateModeBtn != null) {
            openCreateModeBtn.setX(centerX - 100);
            openCreateModeBtn.setY(modalY + modalH - 40);
        }
        
        if (closeModalBtn != null) {
            closeModalBtn.setX(modalX + modalW - 25);
            closeModalBtn.setY(modalY + 5);
        }
        
        // Create Mode
        if (waypointNameField != null) {
            waypointNameField.setX(centerX - 100);
            waypointNameField.setY(modalY + 30);
        }
        
        int coordW = 60;
        int coordGap = 10;
        int totalCoordW = (coordW * 3) + (coordGap * 2);
        int startCoordX = centerX - (totalCoordW / 2);
        int coordsY = modalY + 65;
        
        if (wpX != null) { wpX.setX(startCoordX); wpX.setY(coordsY); }
        if (wpY != null) { wpY.setX(startCoordX + coordW + coordGap); wpY.setY(coordsY); }
        if (wpZ != null) { wpZ.setX(startCoordX + (coordW + coordGap) * 2); wpZ.setY(coordsY); }
        
        // Color Buttons
        int colSize = 20;
        int colorsW = (COLORS.length * (colSize + 4)) - 4;
        int colorStartX = centerX - (colorsW / 2);
        int colorStartY = modalY + 105;
        
        for (int i = 0; i < colorButtons.size(); i++) {
            Button btn = colorButtons.get(i);
            btn.setX(colorStartX + (i * (colSize + 4)));
            btn.setY(colorStartY);
        }
        
        // Icon Buttons
        int iconSize = 20;
        int numIcons = iconButtons.size();
        int iconsW = (numIcons * (iconSize + 2)) - 2;
        int iconStartX = centerX - (iconsW / 2);
        int iconStartY = modalY + 145;
        
        for (int i = 0; i < iconButtons.size(); i++) {
            Button btn = iconButtons.get(i);
            btn.setX(iconStartX + (i * (iconSize + 2)));
            btn.setY(iconStartY);
        }
        
        if (createWaypointBtn != null) {
            int minButtonY = iconStartY + 30;
            int desiredButtonY = modalY + modalH - 30;
            int finalButtonY = Math.max(minButtonY, desiredButtonY);
            createWaypointBtn.setX(centerX - 100);
            createWaypointBtn.setY(finalButtonY);
        }
        
        // Settings Modal
        int settingsW = 200;
        int settingsH = 270;
        int settingsX = (width - settingsW) / 2;
        int settingsY = (height - settingsH) / 2;
        
        if (toggleCompassBtn != null) { toggleCompassBtn.setX(settingsX + 10); toggleCompassBtn.setY(settingsY + 50); }
        if (toggleCoordsBtn != null) { toggleCoordsBtn.setX(settingsX + 10); toggleCoordsBtn.setY(settingsY + 75); }
        if (renderDistanceBtn != null) { renderDistanceBtn.setX(settingsX + 10); renderDistanceBtn.setY(settingsY + 100); }
        if (autoDeathPointsBtn != null) { autoDeathPointsBtn.setX(settingsX + 10); autoDeathPointsBtn.setY(settingsY + 125); }
        if (hudSizeBtn != null) { hudSizeBtn.setX(settingsX + 10); hudSizeBtn.setY(settingsY + 150); }
        if (deleteAllBtn != null) { deleteAllBtn.setX(settingsX + 10); deleteAllBtn.setY(settingsY + 210); }
        if (closeSettingsBtn != null) { closeSettingsBtn.setX(settingsX + settingsW - 25); closeSettingsBtn.setY(settingsY + 10); }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The map supplies its own background. Minecraft 1.21 blurs the current
        // framebuffer in Screen#renderBackground, so calling it again from
        // super.render() would blur the map and modal contents drawn below.
        this.extractTransparentBackground(graphics);
        
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        
        // Center on screen
        poseStack.translate(this.width / 2.0 + panX, this.height / 2.0 + panY, 600);
        
        // Use shared renderer
        if (!showWaypointModal && !showSettingsModal) {
            float pitch = ClientSettings.isTopDownView ? 90.0f : cameraPitch;
            VoxelMapRenderer.renderMap(poseStack, zoom, pitch, cameraYaw, false,
                    ClientSettings.renderDistance, selectedMapBlock);
        }
        
        // Clear depth buffer to ensure UI draws cleanly on top of the 3D map
        // depthMask
        // clear
        
        poseStack.popPose();
        
        // Start UI Layer (Higher Z-index to sit above the map)
        poseStack.pushPose();
        
        // Apply HUD Scaling
        float scale = getHudScale();
        poseStack.scale(scale, scale, 1.0f);
        
        int effectiveWidth = (int)(this.width / scale);
        int effectiveHeight = (int)(this.height / scale);
        
        // Update Layout positions
        updateLayout(effectiveWidth, effectiveHeight);
        
        // Scale mouse coordinates for UI interaction logic within this frame
        int scaledMouseX = (int)(mouseX / scale);
        int scaledMouseY = (int)(mouseY / scale);

        poseStack.translate(0, 0, 800);
        
        // Draw Bottom Menu Background
        int menuHeight = 35;
        int menuY = effectiveHeight - menuHeight;
        if (!showWaypointModal && !showSettingsModal) { 
             graphics.fill(0, menuY, effectiveWidth, effectiveHeight, 0x80000000); // Semi-transparent black
             
             // Draw Separator Pipe between Type toggles and Night Mode toggle
             int separatorX = 10 + (25 * 4); // 110
             int sepY = menuY + 5;
             int sepH = 25; // height of separator
             
             // Draw Pipe
             graphics.fill(separatorX, sepY, separatorX + 1, sepY + sepH, 0xFF888888); // Gray vertical line
        }
        
        // Draw Settings Button (Top Left)
        if (!showWaypointModal && !showSettingsModal) {
            int btnX = 10;
            int btnY = 10;
            int btnSize = 20;
            
            // Hover effect
            boolean isHovered = scaledMouseX >= btnX && scaledMouseX <= btnX + btnSize && scaledMouseY >= btnY && scaledMouseY <= btnY + btnSize;
            int renderSize = isHovered ? 24 : 20;
            int renderX = btnX - (renderSize - btnSize) / 2;
            int renderY = btnY - (renderSize - btnSize) / 2;
            
            Identifier settingsIcon = Identifier.fromNamespaceAndPath("voxelview3d", "textures/settings.png");
            // texture settingsIcon
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, settingsIcon, renderX, renderY, 0, 0, renderSize, renderSize, renderSize, renderSize);
            
            // Draw Y value below Layer Slider
             int currentY = ClientMapData.getInstance().getCutY();
             String text = "Y : " + currentY;
             int textW = this.font.width(text);
             // Slider at x=15, width=15 -> center=22.5 (round to 22)
             int sliderCenter = 15 + (15 / 2);
             int textX = sliderCenter - (textW / 2);
             // Slider Y=50, Height=50 -> Bottom=100. Text at 102.
             if (textX < 2) textX = 2;
             graphics.text(this.font, text, textX, 102, 0xFFFFFFFF);
         }
        
        // Draw Zoom Level
        if (!showWaypointModal && !showSettingsModal) {
            // Draw Zoom Icon
            Identifier zoomIcon = Identifier.fromNamespaceAndPath("voxelview3d", "textures/zoom.png");
            // texture zoomIcon
            // enableBlend
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, zoomIcon, effectiveWidth - 100, menuY + 6, 0, 0, 16, 16, 16, 16);
            // disableBlend

            Component zoomText = Component.translatable("voxelview3d.zoom", String.format("%.1f", zoom));
            graphics.text(this.font, zoomText, effectiveWidth - 80, menuY + 10, 0xFFFFFFFF);
            
            // Draw Compass
            // renderCompass(graphics, effectiveWidth, effectiveHeight);
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
                        .map(ResourceKey::identifier)
                        .map(Identifier::getPath)
                        .orElse(Component.translatable("voxelview3d.unknown").getString());
                
                // Capitalize
                biomeName = capitalize(biomeName.replace('_', ' '));
            }
            
            int centerX = effectiveWidth / 2;
            int topMargin = 10;
            
            graphics.centeredText(this.font, coords, centerX, topMargin, 0xFFFFFF);
            graphics.centeredText(this.font, biomeName, centerX, topMargin + 12, 0xFFFFFF);
        }

        if (selectedMapBlock != null && !showWaypointModal && !showSettingsModal) {
            selectedTeleportBtn.active = canUseTeleportCommand();
            renderBlockSelectionMenu(graphics);
        }
        
        // Render Modal if open
        if (showWaypointModal) {
            renderWaypointModal(graphics, scaledMouseX, scaledMouseY, effectiveWidth, effectiveHeight);
        }
        
        if (showSettingsModal) {
            renderSettingsModal(graphics, scaledMouseX, scaledMouseY, effectiveWidth, effectiveHeight);
        }
        
        // Render Widgets (Buttons, etc.)
        super.extractRenderState(graphics, scaledMouseX, scaledMouseY, a);
        
        // Draw icon on waypoints button handled by IconTextButton
        
        // Render Modal Overlays (Icons, Selection Borders) if open
        if (showWaypointModal && isCreatingMode) {
            renderModalOverlays(graphics, scaledMouseX, scaledMouseY);
        }
        
        poseStack.popPose();
        
        // Render Tooltips (outside scaled context)
        if (!showWaypointModal && !showSettingsModal && toggleNightMode != null && toggleNightMode.isHovered()) {
             boolean isOn = ClientSettings.isNightMode;
             Component tooltip = Component.translatable(isOn ? "voxelview3d.night_mode.on" : "voxelview3d.night_mode.off");
             if (VoxelMapRenderer.isUndergroundState && isOn) {
                 tooltip = Component.translatable("voxelview3d.cave_mode");
             }
             
             toggleNightMode.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
        }
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Intentionally empty: extractRenderState() draws the dim layer once before the map.
    }
    
    private void renderCompass(GuiGraphicsExtractor graphics, int width, int height) {
        // Compass configuration
        int cx = width - 40;
        int cy = 40;
        float radius = 20.0f;
        
        float effectivePitch = ClientSettings.isTopDownView ? 90.0f : cameraPitch;
        
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(cx, cy, 0);
        
        // Scale and Rotate (Flip Y for screen coords)
        poseStack.scale(radius, -radius, radius); 
        poseStack.mulPose(Axis.XP.rotationDegrees(effectivePitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(cameraYaw));
        
        // Draw Axis Lines
        // setShader
        BufferBuilder buf;
        // disableDepthTest // Draw on top
        // lineWidth // Make lines thicker
        
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f pose = poseStack.last().pose();
        
        // Axis Lines (Length 1.5 for visibility)
        float len = 1.5f;
        // N (-Z) - Red
        buf.addVertex(pose, 0, 0, 0).setColor(255, 100, 100, 255);
        buf.addVertex(pose, 0, 0, -len).setColor(255, 0, 0, 255);
        // S (+Z) - Dark Red
        buf.addVertex(pose, 0, 0, 0).setColor(200, 200, 200, 255);
        buf.addVertex(pose, 0, 0, len).setColor(150, 50, 50, 255);
        // E (+X) - Blue
        buf.addVertex(pose, 0, 0, 0).setColor(100, 100, 255, 255);
        buf.addVertex(pose, len, 0, 0).setColor(0, 0, 255, 255);
        // W (-X) - Dark Blue
        buf.addVertex(pose, 0, 0, 0).setColor(200, 200, 200, 255);
        buf.addVertex(pose, -len, 0, 0).setColor(50, 50, 150, 255);

        RenderBufferUtil.drawIfNotEmpty(buf);
        // lineWidth // Reset line width
        
        poseStack.popPose();
        
        // Draw labels N, S, E, W
        // N (-Z), S (+Z), E (+X), W (-X)
        drawCompassLabel(graphics, cx, cy, radius * 1.8f, 0, 0, -1, "N", 0xFFFF0000, effectivePitch);
        drawCompassLabel(graphics, cx, cy, radius * 1.8f, 0, 0, 1, "S", 0xFFAAAAAA, effectivePitch);
        drawCompassLabel(graphics, cx, cy, radius * 1.8f, 1, 0, 0, "E", 0xFF0000FF, effectivePitch);
        drawCompassLabel(graphics, cx, cy, radius * 1.8f, -1, 0, 0, "W", 0xFFAAAAAA, effectivePitch);
    }

    private void drawCompassLabel(GuiGraphicsExtractor graphics, int cx, int cy, float radius, float x, float y, float z, String text, int color, float pitch) {
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
        graphics.text(this.font, text, sx - w / 2, sy - 4, color, false);
    }

    private void renderModalOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Draw colors over buttons
        for (int i = 0; i < colorButtons.size(); i++) {
            Button btn = colorButtons.get(i);
            int color = COLORS[i];
            
            // Fill inside button
            graphics.fill(btn.getX() + 2, btn.getY() + 2, btn.getX() + btn.getWidth() - 2, btn.getY() + btn.getHeight() - 2, color | 0xFF000000);
            
            // Selection border
            if (selectedColor == color) {
                graphics.outline(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0xFFFFFFFF);
            }
        }
        
        // Draw icon selection border and icons
        for (int i = 0; i < iconButtons.size(); i++) {
            Button btn = iconButtons.get(i);
            String iconName = "icon" + (i + 1);
            
            // Draw Icon
            Identifier iconLoc = Identifier.fromNamespaceAndPath("voxelview3d", "textures/waypoints/" + iconName + ".png");
            
            float r = ((selectedColor >> 16) & 0xFF) / 255.0f;
            float g = ((selectedColor >> 8) & 0xFF) / 255.0f;
            float b = (selectedColor & 0xFF) / 255.0f;
            
            // enableBlend
            // setShaderColor
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, iconLoc, btn.getX() + 2, btn.getY() + 2, 0, 0, 16, 16, 16, 16);
            // setShaderColor
            // disableBlend
            
            if (selectedIcon.equals(iconName)) {
                graphics.outline(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0xFFFFFFFF);
            }
        }
    }
    
    private void renderSettingsModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int width, int height) {
        int settingsW = 200;
        int settingsH = 270;
        int settingsX = (width - settingsW) / 2;
        int settingsY = (height - settingsH) / 2;
        
        // Background
        graphics.fill(settingsX, settingsY, settingsX + settingsW, settingsY + settingsH, 0xF0101010);
        
        // Title Bar
        graphics.fill(settingsX, settingsY, settingsX + settingsW, settingsY + 40, 0xFF000000);
        
        // Border
        graphics.outline(settingsX, settingsY, settingsW, settingsH, 0xFF404040);
        
        // Title
        graphics.centeredText(this.font, Component.translatable("voxelview3d.settings.title"), settingsX + settingsW / 2, settingsY + 15, 0xFFFFFFFF);
    }

    private void renderBlockSelectionMenu(GuiGraphicsExtractor graphics) {
        int menuWidth = 190;
        int menuHeight = 48;
        graphics.fill(blockMenuX, blockMenuY, blockMenuX + menuWidth,
                blockMenuY + menuHeight, 0xE0101010);
        graphics.outline(blockMenuX, blockMenuY, menuWidth, menuHeight, 0xFF00FFFF);

        Component coordinates = Component.translatable("voxelview3d.selection.block",
                selectedMapBlock.getX(), selectedMapBlock.getY(), selectedMapBlock.getZ());
        graphics.centeredText(font, coordinates, blockMenuX + menuWidth / 2,
                blockMenuY + 7, 0xFFFFFFFF);
    }

    private void renderWaypointModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int width, int height) {
        // Dynamic Full Screen Modal
        int margin = 10;
        int modalX = margin;
        int modalY = margin;
        int modalW = width - (margin * 2);
        int modalH = height - (margin * 2);
        
        // Background (Almost solid dark)
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF0101010);
        
        // Title bar (Solid black)
        graphics.fill(modalX, modalY, modalX + modalW, modalY + 30, 0xFF000000); 
        
        // Border
        graphics.outline(modalX, modalY, modalW, modalH, 0xFF404040);
        
        Component title = isCreatingMode ? (editingWaypoint != null ? Component.translatable("voxelview3d.waypoint.edit_title") : Component.translatable("voxelview3d.waypoint.create_title")) : Component.translatable("voxelview3d.waypoint.list_title");
        graphics.centeredText(this.font, title, width / 2, modalY + 11, 0xFFFFFFFF);
        
        if (isCreatingMode) {
            // Render labels for create mode
            int centerX = width / 2;
            int coordW = 60;
            int coordGap = 10;
            int totalCoordW = (coordW * 3) + (coordGap * 2);
            int startCoordX = centerX - (totalCoordW / 2);
            
            graphics.centeredText(this.font, Component.translatable("voxelview3d.waypoint.x"), startCoordX + (coordW/2), modalY + 55, 0xFFAAAAAA);
            graphics.centeredText(this.font, Component.translatable("voxelview3d.waypoint.y"), startCoordX + coordW + coordGap + (coordW/2), modalY + 55, 0xFFAAAAAA);
            graphics.centeredText(this.font, Component.translatable("voxelview3d.waypoint.z"), startCoordX + (coordW + coordGap) * 2 + (coordW/2), modalY + 55, 0xFFAAAAAA);
            
            graphics.centeredText(this.font, Component.translatable("voxelview3d.waypoint.color"), centerX, modalY + 95, 0xFFAAAAAA);
            graphics.centeredText(this.font, Component.translatable("voxelview3d.waypoint.icon"), centerX, modalY + 135, 0xFFAAAAAA);
            
        } else {
            // Render List
            int listX = modalX + 10;
            int listY = modalY + 65; // Pushed down for search bar
            int itemHeight = 30;
            int listHeight = modalH - 115; // Reserve space for search bar (30) and button at bottom (85) -> total 115
            
            int listWidth = modalW - 20;
            
            // Enable Scissor to clip content
            float scale = getHudScale();
            graphics.enableScissor((int)(listX * scale), (int)(listY * scale), (int)((listX + listWidth) * scale), (int)((listY + listHeight) * scale));
            
            List<ClientSettings.Waypoint> waypoints = getFilteredWaypoints();
            
            for (int i = 0; i < waypoints.size(); i++) {
                 ClientSettings.Waypoint wp = waypoints.get(i);
                 int rowY = (int)(listY + (i * itemHeight) - scrollOffset);
                 
                 // Skip if out of view
                 if (rowY + itemHeight < listY || rowY > listY + listHeight) continue;
                 
                 // Row Background (alternate)
                 if (i % 2 == 0) {
                     graphics.fill(listX, rowY, listX + listWidth, rowY + itemHeight, 0x20FFFFFF);
                 }
                 
                 // Hover effect
                 if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= rowY && mouseY < rowY + itemHeight) {
                     graphics.fill(listX, rowY, listX + listWidth, rowY + itemHeight, 0x10FFFFFF);
                 }
                 
                 // Icon
                 Identifier iconLoc = Identifier.fromNamespaceAndPath("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
                 
                 float r = ((wp.color >> 16) & 0xFF) / 255.0f;
                 float g = ((wp.color >> 8) & 0xFF) / 255.0f;
                 float b = (wp.color & 0xFF) / 255.0f;
                 
                 // enableBlend
                 // setShaderColor
                 graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, iconLoc, listX + 5, rowY + 5, 0, 0, 20, 20, 20, 20);
                 // setShaderColor
                 // disableBlend
                 
                 // Name
                 graphics.text(this.font, wp.name, listX + 35, rowY + 5, wp.color);
                 
                 // Distance
                 if (this.minecraft.player != null) {
                     double d = Math.sqrt(this.minecraft.player.distanceToSqr(wp.x + 0.5, wp.y + 0.5, wp.z + 0.5));
                     String distStr = String.format("%.1fm", d);
                     graphics.text(this.font, distStr, listX + 35, rowY + 15, 0xFF888888);
                 }
                 
                 // Buttons: [Edit] [Eye] [TP] [Trash] (Aligned Right)
                 int rightEdge = listX + listWidth;
                 
                 // Coords (Before buttons)
                 String coords = String.format("[%d, %d, %d]", wp.x, wp.y, wp.z);
                 int coordsW = this.font.width(coords);
                 graphics.text(this.font, coords, rightEdge - 170 - coordsW, rowY + 11, 0xFFAAAAAA);
                 
                 // Share
                 boolean shareHover = mouseX >= rightEdge - 150 && mouseX < rightEdge - 130 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 graphics.fill(rightEdge - 150, rowY + 5, rightEdge - 130, rowY + 25, shareHover ? 0xFF606060 : 0xFF404040);
                 Identifier shareIcon = Identifier.fromNamespaceAndPath("voxelview3d", "textures/share.png");
                 // texture shareIcon
                 // enableBlend
                 graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, shareIcon, rightEdge - 148, rowY + 7, 0, 0, 16, 16, 16, 16);
                 // disableBlend
                 
                 // Edit
                 boolean editHover = mouseX >= rightEdge - 120 && mouseX < rightEdge - 100 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 graphics.fill(rightEdge - 120, rowY + 5, rightEdge - 100, rowY + 25, editHover ? 0xFF606060 : 0xFF404040);
                 Identifier editIcon = Identifier.fromNamespaceAndPath("voxelview3d", "textures/edit.png");
                 // texture editIcon
                 // enableBlend
                 graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, editIcon, rightEdge - 118, rowY + 7, 0, 0, 16, 16, 16, 16);
                 // disableBlend
                 
                 // Eye (Visibility)
                 boolean eyeHover = mouseX >= rightEdge - 90 && mouseX < rightEdge - 70 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 graphics.fill(rightEdge - 90, rowY + 5, rightEdge - 70, rowY + 25, eyeHover ? 0xFF606060 : 0xFF404040);
                 Identifier eyeIcon = Identifier.fromNamespaceAndPath("voxelview3d", "textures/" + (wp.visible ? "nothide.png" : "hide.png"));
                 // texture eyeIcon
                 // enableBlend
                 graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, eyeIcon, rightEdge - 88, rowY + 7, 0, 0, 16, 16, 16, 16);
                 // disableBlend
                 
                 // TP
                 boolean canTeleport = canUseTeleportCommand();
                 boolean tpHover = canTeleport && mouseX >= rightEdge - 60 && mouseX < rightEdge - 40
                         && mouseY >= rowY && mouseY < rowY + itemHeight;
                 int tpBackground = canTeleport ? (tpHover ? 0xFF606060 : 0xFF404040) : 0xFF202020;
                 graphics.fill(rightEdge - 60, rowY + 5, rightEdge - 40, rowY + 25, tpBackground);
                 graphics.centeredText(this.font, "/TP", rightEdge - 50, rowY + 11,
                         canTeleport ? 0xFFFFFFFF : 0xFF777777);
                 
                 // Trash
                 boolean trashHover = mouseX >= rightEdge - 30 && mouseX < rightEdge - 10 && mouseY >= rowY && mouseY < rowY + itemHeight;
                 graphics.fill(rightEdge - 30, rowY + 5, rightEdge - 10, rowY + 25, trashHover ? 0xFF606060 : 0xFF404040);
                 Identifier deleteIcon = Identifier.fromNamespaceAndPath("voxelview3d", trashHover ? "textures/deletehover.png" : "textures/delete.png");
                 // texture deleteIcon
                 // enableBlend
                 graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, deleteIcon, rightEdge - 28, rowY + 7, 0, 0, 16, 16, 16, 16);
                 // disableBlend
            }
            
            graphics.disableScissor();
            
            // Scrollbar
            int totalContentHeight = waypoints.size() * itemHeight;
            if (totalContentHeight > listHeight) {
                int scrollBarW = 6;
                int scrollBarX = listX + listWidth - scrollBarW - 2;
                int scrollBarH = (int)((float)listHeight * ((float)listHeight / totalContentHeight));
                if (scrollBarH < 20) scrollBarH = 20;
                
                int maxScroll = totalContentHeight - listHeight;
                int scrollY = listY + (int)((scrollOffset / maxScroll) * (listHeight - scrollBarH));
                
                graphics.fill(scrollBarX, listY, scrollBarX + scrollBarW, listY + listHeight, 0x80000000); // Track
                graphics.fill(scrollBarX, scrollY, scrollBarX + scrollBarW, scrollY + scrollBarH, 0xFF808080); // Thumb
            }
        }
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        float scale = getHudScale();
        if (super.mouseDragged(event, dragX / scale, dragY / scale)) return true;
        
        if (isDraggingMap) {
            if (!mapDragMoved
                    && Math.hypot(mouseX - mapPressX, mouseY - mapPressY) > 3.0) {
                mapDragMoved = true;
                clearBlockSelection();
            }

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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float scale = getHudScale();
        double scaledX = mouseX / scale;
        double scaledY = mouseY / scale;
        int effectiveWidth = (int)(this.width / scale);
        int effectiveHeight = (int)(this.height / scale);

        if (showWaypointModal && !isCreatingMode) {
             // Check if hovering list
             int margin = 10;
             int modalW = effectiveWidth - (margin * 2);
             int modalH = effectiveHeight - (margin * 2);
             int modalX = margin;
             int modalY = margin;
             
             int listX = modalX + 10;
             int listY = modalY + 65;
             int listWidth = modalW - 20;
             int listHeight = modalH - 115; // Matches render logic
             
             if (scaledX >= listX && scaledX <= listX + listWidth && scaledY >= listY && scaledY <= listY + listHeight) {
                 this.scrollOffset -= scrollY * 20; // Scroll speed
                 if (this.scrollOffset < 0) this.scrollOffset = 0;
                 
                 // Calculate max scroll
                 int itemHeight = 30;
                 int totalHeight = getFilteredWaypoints().size() * itemHeight;
                 int maxScroll = Math.max(0, totalHeight - listHeight);
                 
                 if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;
                 
                 return true;
             }
        }

        if (super.mouseScrolled(scaledX, scaledY, scrollX, scrollY)) return true;
        
        if (isHoveringUI(scaledX, scaledY)) return false;

        // Map interaction: Zoom
        clearBlockSelection();
        this.zoom += (float)scrollY * 0.5f;
        if (this.zoom < 0.5f) this.zoom = 0.5f;
        if (this.zoom > 15.0f) this.zoom = 15.0f;
        return true;
    }
    
    private boolean isHoveringUI(double mouseX, double mouseY) {
        float scale = getHudScale();
        int effectiveWidth = (int)(this.width / scale);
        int effectiveHeight = (int)(this.height / scale);
        
        // Bottom Menu
        if (mouseY >= effectiveHeight - 35) return true;

        if (selectedMapBlock != null && !showWaypointModal && !showSettingsModal
                && mouseX >= blockMenuX && mouseX <= blockMenuX + 190
                && mouseY >= blockMenuY && mouseY <= blockMenuY + 48) {
            return true;
        }
        
        // Modal
        if (showWaypointModal) {
            int margin = 10;
            int modalX = margin;
            int modalY = margin;
            int modalW = effectiveWidth - (margin * 2);
            int modalH = effectiveHeight - (margin * 2);
            
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

    private static class ImageToggleButton extends Button.Plain {
        protected final Identifier textureOn;
        protected final Identifier textureOff;
        protected final Supplier<Boolean> stateSupplier;

        public ImageToggleButton(int x, int y, int width, int height, Identifier textureOn, Identifier textureOff, Supplier<Boolean> stateSupplier, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.textureOn = textureOn;
            this.textureOff = textureOff;
            this.stateSupplier = stateSupplier;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            boolean isOn = stateSupplier.get();
            Identifier texture = isOn ? textureOn : textureOff;
            
            // texture texture
            // enableBlend
            
            // Draw background if hovered
            if (this.isHovered) {
                 graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x40FFFFFF);
            }
            
            // Draw icon centered
            int iconSize = width - 4;
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, getX() + 2, getY() + 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
            // disableBlend
        }
    }

    private static class IconTextButton extends Button.Plain {
        private final Identifier icon;

        public IconTextButton(int x, int y, int width, int height, Component message, Identifier icon, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.icon = icon;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            // Draw background
            int color = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80000000); // Semi-transparent black
            graphics.outline(getX(), getY(), width, height, color); // Border
            
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
            // texture icon
            // enableBlend
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, icon, contentX, iconY, 0, 0, 16, 16, 16, 16);
            // disableBlend
            
            // Draw Text
            graphics.text(font, getMessage(), contentX + iconWidth + gap, textY, color);
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
            String current = minecraft.level.dimension().identifier().toString();
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
    
    private Identifier getDimensionIcon(String dim) {
        if (dim.contains("nether")) return Identifier.fromNamespaceAndPath("minecraft", "textures/block/netherrack.png");
        if (dim.contains("end") && !dim.contains("render")) return Identifier.fromNamespaceAndPath("minecraft", "textures/block/end_stone.png");
        return Identifier.fromNamespaceAndPath("minecraft", "textures/block/grass_block_side.png");
    }

    private class LayerSlider extends AbstractWidget {
        private final int min;
        private final int max;
        private double value; // 0.0 to 1.0
        private int lastPlayerY;

        public LayerSlider(int x, int y, int width, int height, int min, int max) {
            super(x, y, width, height, Component.empty());
            this.min = min;
            this.max = max;
            
            // Set initial value based on player pos
            if (minecraft.player != null) {
                int playerY = minecraft.player.getBlockY();
                this.lastPlayerY = playerY;
                
                // Ensure playerY is within bounds
                playerY = Mth.clamp(playerY, min, max);
                
                this.value = (double)(playerY - min) / (double)(max - min);
                this.value = Mth.clamp(this.value, 0.0, 1.0);
                
                // Update map immediately
                ClientMapData.getInstance().setCutY(playerY);
            } else {
                this.value = 1.0; // Full height
                this.lastPlayerY = 0;
            }
        }

        public void resetToPlayer() {
            if (minecraft.player != null) {
                int playerY = minecraft.player.getBlockY();
                this.lastPlayerY = playerY;
                
                // Ensure playerY is within bounds
                playerY = Mth.clamp(playerY, min, max);
                
                this.value = (double)(playerY - min) / (double)(max - min);
                this.value = Mth.clamp(this.value, 0.0, 1.0);
                
                // Update map immediately
                ClientMapData.getInstance().setCutY(playerY);
            }
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            // Update value if player moved vertically (User Request)
            if (minecraft.player != null) {
                int currentPlayerY = minecraft.player.getBlockY();
                if (currentPlayerY != lastPlayerY) {
                    int delta = currentPlayerY - lastPlayerY;
                    
                    // Only update if we are not currently dragging the slider (to avoid conflict)
                    if (!this.isFocused() && !this.isHovered()) { // Simple check, might be enough. Or just always update relative?
                        // Always update relative allows "falling while inspecting".
                        int currentCutY = ClientMapData.getInstance().getCutY();
                        int newCutY = Mth.clamp(currentCutY + delta, min, max);
                        
                        this.value = (double)(newCutY - min) / (double)(max - min);
                        this.value = Mth.clamp(this.value, 0.0, 1.0);
                        
                        ClientMapData.getInstance().setCutY(newCutY);
                    }
                    
                    lastPlayerY = currentPlayerY;
                }
            }

            // Draw Background (Track)
            // Center track (4px wide)
            int centerX = getX() + (width/2);
            graphics.fill(centerX - 2, getY(), centerX + 2, getY() + height, 0x80000000);
            // Border for track
            graphics.outline(centerX - 2, getY(), 4, height, 0xFF404040);
            
            // Draw Handle
            // Value 1.0 is TOP (MaxY), Value 0.0 is BOTTOM (MinY).
            // Screen Y: Top is Y, Bottom is Y + Height.
            // So Value 1.0 -> Y
            // Value 0.0 -> Y + Height - HandleH
            
            int handleH = 8;
            int trackH = height - handleH;
            int handleY = getY() + (int)((1.0 - value) * trackH);
            
            // Handle Box
            int handleX = getX(); // Full width handle
            int handleW = width;
            
            boolean hovered = mouseX >= handleX && mouseX <= handleX + handleW && 
                              mouseY >= handleY && mouseY <= handleY + handleH;
            
            // Fill Handle
            graphics.fill(handleX, handleY, handleX + handleW, handleY + handleH, hovered ? 0xFFFFFFFF : 0xFFAAAAAA);
            // Border Handle
            graphics.outline(handleX, handleY, handleW, handleH, 0xFF000000);
            
            // Draw current Y value tooltip if hovered over widget
            if (isHovered) {
                int currentY = min + (int)(value * (max - min));
                Component tooltip = Component.literal("Y: " + currentY);
                this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            // No narration needed for now
        }
        
        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            updateValue(event.y());
        }
        
        @Override
        protected void onDrag(MouseButtonEvent event, double dx, double dy) {
            updateValue(event.y());
        }
        
        private void updateValue(double mouseY) {
            int handleH = 8;
            int trackH = height - handleH;
            
            // Calculate value from mouse Y relative to track top
            // 1.0 (Top) -> mouseY = getY() + handleH/2
            // 0.0 (Bottom) -> mouseY = getY() + height - handleH/2
            
            double relativeY = mouseY - (getY() + handleH / 2.0);
            double normalized = 1.0 - (relativeY / trackH);
            
            this.value = Mth.clamp(normalized, 0.0, 1.0);
            
            int currentY = min + (int)(value * (max - min));
            ClientMapData.getInstance().setCutY(currentY);
        }
    }
}
