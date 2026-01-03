package com.agustinbenitez.voxelview3d.client;

import com.agustinbenitez.voxelview3d.world.ChunkScanner;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.item.DyeColor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.world.level.Level;
import java.util.List;

public class VoxelMapRenderer {

    public static void renderMap(PoseStack poseStack, float zoom, float cameraPitch, float cameraYaw, boolean isHud, int renderRadius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        Player player = mc.player;
        
        // Setup 3D Rendering State
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.enableCull(); // Enable Cull to avoid seeing backfaces and reduce visual noise
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        // Enable Blending for transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        poseStack.pushPose();
        
        // Scale and Rotate
        poseStack.scale(zoom, -zoom, zoom);
        poseStack.mulPose(Axis.XP.rotationDegrees(cameraPitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(cameraYaw));
        
        // Render Player Marker (Textured Cube)
        renderPlayerMarker(poseStack, player);
        
        ClientMapData clientData = ClientMapData.getInstance();
        int cutY = clientData.getCutY();
        
        int minBuildHeight = mc.level != null ? mc.level.getMinBuildHeight() : -64;
        
        // Calculate Vertical Culling Bounds
        int renderMinY = minBuildHeight;
        int renderMaxY = cutY;
        
        boolean isUnderground = false;
        
        boolean isNether = mc.level != null && mc.level.dimension().location().getPath().contains("nether");
        boolean isEnd = mc.level != null && mc.level.dimension().location().getPath().contains("end");

        if (mc.level != null) {
            int playerY = player.getBlockY();
            boolean canSeeSky = mc.level.canSeeSky(player.blockPosition());
            
            // Underground if can't see sky OR if deep in a hole (surface is significantly higher)
            if (isEnd) {
                // In The End, use Heightmap because canSeeSky is unreliable (no skylight)
                // If there are blocks significantly above us, treat as underground/indoors
                int h = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
                isUnderground = h > player.getBlockY() + 4;
            } else {
                isUnderground = !canSeeSky && !isNether; // Overworld logic
            }
            
            if (!isUnderground && !isNether && !isEnd) {
                 int x = player.getBlockX();
                 int z = player.getBlockZ();
                 
                 // Check surrounding height (3x3 area around player)
                 // If average surrounding height is significantly higher than playerY, we are in a hole/trench
                 int h1 = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + 2, z);
                 int h2 = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x - 2, z);
                 int h3 = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z + 2);
                 int h4 = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z - 2);
                 
                 int avgSurrounding = (h1 + h2 + h3 + h4) / 4;
                 
                 // If player is > 3 blocks below the surrounding terrain, treat as underground (transparency)
                 if (playerY < avgSurrounding - 3) {
                    isUnderground = true;
                }
            }
            
            if (isUnderground) {
                // Cave Mode: Strict vertical limits to see player inside
                // Show floor slightly below
                renderMinY = Math.max(minBuildHeight, playerY - 16); 
                
                // Dynamic Ceiling Detection
                // Search for the first solid block above the player to determine ceiling height
                int ceilingHeight = playerY + 1; // Default fallback (just above head)
                int maxSearchHeight = playerY + 64; // Search up to 64 blocks up
                
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(player.getBlockX(), playerY + 2, player.getBlockZ());
                boolean foundCeiling = false;
                
                for (int y = playerY + 2; y <= maxSearchHeight; y++) {
                    pos.setY(y);
                    BlockState state = mc.level.getBlockState(pos);
                    
                    // Check if block is solid enough to be a roof
                    // We use canOcclude() as a general check for blocks that block view
                    if (state.canOcclude() && !state.isAir()) {
                        ceilingHeight = y;
                        foundCeiling = true;
                        break;
                    }
                }
                
                if (foundCeiling) {
                    // If we found a ceiling, render up to just below it
                    // This allows seeing the full height of the walls in the room
                    renderMaxY = Math.min(cutY, ceilingHeight - 1);
                } else {
                    // If no ceiling found (e.g. large cavern or glitch), 
                    // render a reasonable amount above head
                    renderMaxY = Math.min(cutY, playerY + 16);
                }
                
                // Ensure we always see at least just above the player
                renderMaxY = Math.max(renderMaxY, playerY + 1);
                
            } else if (isNether) {
                // Nether Mode
                // We want to see down to the lava lake (y=31) even if we are high up
                // User reported lava disappearing at high altitudes, so we ensure we render deep enough
                // Using minBuildHeight ensures we see the lava lake and its shores/depth
                int lavaLevel = minBuildHeight; 
                
                // If we are high, renderMinY should reach down to lavaLevel
                renderMinY = Math.min(playerY - 32, lavaLevel);
                renderMinY = Math.max(minBuildHeight, renderMinY);
                
                // Ceiling: just above player or cutY
                renderMaxY = Math.min(cutY, playerY + 32); 
            } else if (isEnd) {
                // End Mode
                // Always render from the bottom to ensure floating islands are visible even when high up
                // The End is mostly void, so performance impact is minimal
                renderMinY = minBuildHeight;
                // renderMaxY remains cutY (or sky)
            } else {
                // Surface Mode: Show deeper context
                // Ensure we see down to sea level (60) when flying high, but keep culling when low
                renderMinY = Math.max(minBuildHeight, Math.min(playerY - 32, 60));
                // renderMaxY remains cutY (or sky)
            }
        }
        
        // Render Chunks
        renderChunks(poseStack, player, renderRadius, minBuildHeight, renderMinY, renderMaxY, isUnderground, cameraYaw);
        
        // Nether Lava Floor Fallback
        // If in Nether and we are high up, render a flat lava plane at y=31 to simulate the ocean
        // in case chunks are missing or unloaded.
        if (isNether) {
            renderNetherLavaFloor(poseStack, player, renderRadius);
        }
        
        // Render Entities
        renderEntities(poseStack, player, renderMinY, renderMaxY, renderRadius);

        // Render Chunk Grid
        renderChunkGrid(poseStack, player, renderRadius);

        // Render Waypoints
        renderWaypoints(poseStack, player, cameraYaw, cameraPitch);
        
        poseStack.popPose();
        
        // Reset state
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
    }

    // --- Helper for Textured Blocks ---
    private static void renderTexturedBox(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float w, float h, float d, BakedModel model, BlockState state, float r, float g, float b, float a, int exposedFaces) {
        float x0 = (float)x;
        float y0 = (float)y;
        float z0 = (float)z;
        float x1 = (float)(x + w);
        float y1 = (float)(y + h);
        float z1 = (float)(z + d);
        
        float y_min = y0;
        float y_max = y1;
        
        RandomSource rand = RandomSource.create();
        
        // Top Face (y1) - Face Y+ - Bit 3 (8)
        if ((exposedFaces & 8) != 0) {
            TextureAtlasSprite sprite = getFaceSprite(model, state, Direction.UP, rand);
            float minU = sprite.getU0();
            float maxU = sprite.getU1();
            float minV = sprite.getV0();
            float maxV = sprite.getV1();
            buf.vertex(pose, x0, y_max, z0).uv(minU, minV).color(r, g, b, a).endVertex();
            buf.vertex(pose, x0, y_max, z1).uv(minU, maxV).color(r, g, b, a).endVertex();
            buf.vertex(pose, x1, y_max, z1).uv(maxU, maxV).color(r, g, b, a).endVertex();
            buf.vertex(pose, x1, y_max, z0).uv(maxU, minV).color(r, g, b, a).endVertex();
        }
        
        // Bottom Face (y0) - Face Y- - Bit 2 (4)
        if ((exposedFaces & 4) != 0) {
            TextureAtlasSprite sprite = getFaceSprite(model, state, Direction.DOWN, rand);
            float minU = sprite.getU0();
            float maxU = sprite.getU1();
            float minV = sprite.getV0();
            float maxV = sprite.getV1();
            buf.vertex(pose, x1, y_min, z0).uv(minU, minV).color(r*0.5f, g*0.5f, b*0.5f, a).endVertex();
            buf.vertex(pose, x1, y_min, z1).uv(minU, maxV).color(r*0.5f, g*0.5f, b*0.5f, a).endVertex();
            buf.vertex(pose, x0, y_min, z1).uv(maxU, maxV).color(r*0.5f, g*0.5f, b*0.5f, a).endVertex();
            buf.vertex(pose, x0, y_min, z0).uv(maxU, minV).color(r*0.5f, g*0.5f, b*0.5f, a).endVertex();
        }
        
        // North Face (z0) - Face Z- - Bit 4 (16)
        if ((exposedFaces & 16) != 0) {
            TextureAtlasSprite sprite = getFaceSprite(model, state, Direction.NORTH, rand);
            float minU = sprite.getU0();
            float maxU = sprite.getU1();
            float minV = sprite.getV0();
            float maxV = sprite.getV1();
            buf.vertex(pose, x1, y_max, z0).uv(minU, minV).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x1, y_min, z0).uv(minU, maxV).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x0, y_min, z0).uv(maxU, maxV).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x0, y_max, z0).uv(maxU, minV).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
        }
        
        // South Face (z1) - Face Z+ - Bit 5 (32)
        if ((exposedFaces & 32) != 0) {
            TextureAtlasSprite sprite = getFaceSprite(model, state, Direction.SOUTH, rand);
            float minU = sprite.getU0();
            float maxU = sprite.getU1();
            float minV = sprite.getV0();
            float maxV = sprite.getV1();
            buf.vertex(pose, x0, y_max, z1).uv(minU, minV).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x0, y_min, z1).uv(minU, maxV).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x1, y_min, z1).uv(maxU, maxV).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x1, y_max, z1).uv(maxU, minV).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
        }
        
        // West Face (x0) - Face X- - Bit 0 (1)
        if ((exposedFaces & 1) != 0) {
            TextureAtlasSprite sprite = getFaceSprite(model, state, Direction.WEST, rand);
            float minU = sprite.getU0();
            float maxU = sprite.getU1();
            float minV = sprite.getV0();
            float maxV = sprite.getV1();
            buf.vertex(pose, x0, y_max, z0).uv(minU, minV).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x0, y_min, z0).uv(minU, maxV).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x0, y_min, z1).uv(maxU, maxV).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x0, y_max, z1).uv(maxU, minV).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
        }
        
        // East Face (x1) - Face X+ - Bit 1 (2)
        if ((exposedFaces & 2) != 0) {
            TextureAtlasSprite sprite = getFaceSprite(model, state, Direction.EAST, rand);
            float minU = sprite.getU0();
            float maxU = sprite.getU1();
            float minV = sprite.getV0();
            float maxV = sprite.getV1();
            buf.vertex(pose, x1, y_max, z1).uv(minU, minV).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x1, y_min, z1).uv(minU, maxV).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x1, y_min, z0).uv(maxU, maxV).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x1, y_max, z0).uv(maxU, minV).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
        }
    }
    
    private static TextureAtlasSprite getFaceSprite(BakedModel model, BlockState state, Direction dir, RandomSource rand) {
        List<BakedQuad> quads = model.getQuads(state, dir, rand);
        if (quads != null && !quads.isEmpty()) {
            return quads.get(0).getSprite();
        }
        return model.getParticleIcon();
    }
    
    private static void renderChunkBlocksTextured(BufferBuilder buf, Matrix4f pose, ChunkPos cp, ChunkScanner.ScannedChunk chunkData, double centerX, double centerZ, double centerY, int minBuildHeight, int minY, int maxY, boolean isUnderground, Direction cullDirection) {
        int[] packedPositions = chunkData.positions;
        int[] colors = chunkData.colors;
        byte[] lights = chunkData.lights;
        
        if (packedPositions == null) return;
        
        for (int i = 0; i < packedPositions.length; i++) {
            int packed = packedPositions[i];
            // Unpack
            int renderType = (packed >> 17) & 0x7F;
            
            if (renderType <= 32 || renderType == 61) continue; // Skip standard blocks (<=32) and Rails (61) which are handled in non-textured pass
            
            int x = packed & 0xF;
            int z = (packed >> 4) & 0xF;
            int relY = (packed >> 8) & 0x1FF;
            int exposedFaces = (packed >> 24) & 0x3F;
            
            int h = relY + minBuildHeight;
            if (h < minY || h > maxY) continue;
            
            double rx = (cp.x * 16 + x) - centerX;
            double ry = h - centerY;
            double rz = (cp.z * 16 + z) - centerZ;
            
            // Sims 4 Style Culling (Wall Cutaway)
            if (isUnderground && cullDirection != null) {
                 // Check Center Y > Feet Y (ry is Min Y)
                 // If ry = -1 (Floor), ry+0.5 = -0.5. Not culled.
                 // If ry = 0 (Feet Block), ry+0.5 = 0.5. Culled.
                 if ((ry + 0.5) >= 0) {
                     // Check Center X/Z
                     double cx = rx + 0.5;
                     double cz = rz + 0.5;
                     
                     if (cullDirection == Direction.NORTH && cz < -0.5) continue;
                     if (cullDirection == Direction.SOUTH && cz > 0.5) continue;
                     if (cullDirection == Direction.EAST && cx > 0.5) continue;
                     if (cullDirection == Direction.WEST && cx < -0.5) continue;
                 }
            }

            int color = colors[i];
            
            float brightness = 1.0f;
            float alpha = 1.0f;
            
            // Lighting Logic (Simplified copy)
            if (isUnderground && !ClientSettings.fullBrightMap) {
                double distSq = rx * rx + ry * ry + rz * rz;
                if (distSq > 36.0) {
                    double dist = Math.sqrt(distSq);
                    double fade = 1.0 - ((dist - 6.0) / 10.0);
                    brightness = (float) Math.max(0.3, Math.min(1.0, fade));
                }
            }
            
            boolean useNightMode = ClientSettings.isNightMode || isUnderground;
            if (useNightMode && lights != null && i < lights.length) {
                int lightLevel = lights[i];
                float nightBase = 0.25f; 
                float lightContrib = (lightLevel / 15.0f);
                brightness *= (nightBase + lightContrib * (1.0f - nightBase));
            }
            
            BlockState state = Blocks.AIR.defaultBlockState();
            if (renderType == 32) state = Blocks.CHEST.defaultBlockState();
            else if (renderType == 33) state = Blocks.CRAFTING_TABLE.defaultBlockState();
            else if (renderType == 34) state = Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.LIT, false);
            else if (renderType == 35) state = Blocks.BOOKSHELF.defaultBlockState();
            else if (renderType == 36) state = Blocks.TNT.defaultBlockState();
            else if (renderType == 37) state = Blocks.PUMPKIN.defaultBlockState();
            else if (renderType == 38) state = Blocks.MELON.defaultBlockState();
            else if (renderType == 39) state = Blocks.ENCHANTING_TABLE.defaultBlockState();
            else if (renderType == 40) state = Blocks.BARREL.defaultBlockState();
            else if (renderType >= 41 && renderType <= 56) {
                int colorId = renderType - 41;
                state = getCarpetState(DyeColor.byId(colorId));
            } else if (renderType == 57) {
                state = Blocks.MOSS_CARPET.defaultBlockState();
            }
            
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            
            float bw = 1.0f;
            float bh = 1.0f;
            float bd = 1.0f;
            
            if (renderType == 39 || (renderType >= 41 && renderType <= 57)) {
                // Use actual 3D model for carpets and non-full blocks to get correct side UVs and geometry
                Level level = Minecraft.getInstance().level;
                BlockPos pos = new BlockPos(cp.getMinBlockX() + x, h, cp.getMinBlockZ() + z);
                renderBakedModel(buf, pose, rx, ry, rz, model, state, brightness, brightness, brightness, alpha, exposedFaces, level, pos);
            } else {
                renderTexturedBox(buf, pose, rx, ry, rz, bw, bh, bd, model, state, brightness, brightness, brightness, alpha, exposedFaces);
            }
        }
    }

    private static void renderBakedModel(BufferBuilder buf, Matrix4f pose, double x, double y, double z, BakedModel model, BlockState state, float r, float g, float b, float a, int exposedFaces, Level level, BlockPos pos) {
        RandomSource rand = RandomSource.create();
        
        // Iterate over directions based on exposedFaces
        // Bits: 0=West, 1=East, 2=Down, 3=Up, 4=North, 5=South
        
        // West (X-)
        if ((exposedFaces & 1) != 0) renderFace(buf, pose, x, y, z, model, state, Direction.WEST, rand, r * 0.6f, g * 0.6f, b * 0.6f, a, level, pos);
        // East (X+)
        if ((exposedFaces & 2) != 0) renderFace(buf, pose, x, y, z, model, state, Direction.EAST, rand, r * 0.6f, g * 0.6f, b * 0.6f, a, level, pos);
        // Down (Y-)
        if ((exposedFaces & 4) != 0) renderFace(buf, pose, x, y, z, model, state, Direction.DOWN, rand, r * 0.5f, g * 0.5f, b * 0.5f, a, level, pos);
        // Up (Y+)
        if ((exposedFaces & 8) != 0) renderFace(buf, pose, x, y, z, model, state, Direction.UP, rand, r, g, b, a, level, pos);
        // North (Z-)
        if ((exposedFaces & 16) != 0) renderFace(buf, pose, x, y, z, model, state, Direction.NORTH, rand, r * 0.8f, g * 0.8f, b * 0.8f, a, level, pos);
        // South (Z+)
        if ((exposedFaces & 32) != 0) renderFace(buf, pose, x, y, z, model, state, Direction.SOUTH, rand, r * 0.8f, g * 0.8f, b * 0.8f, a, level, pos);
        
        // Also check for unculled faces (null direction) - always render these
        renderFace(buf, pose, x, y, z, model, state, null, rand, r, g, b, a, level, pos);
    }
    
    private static void renderFace(BufferBuilder buf, Matrix4f pose, double x, double y, double z, BakedModel model, BlockState state, Direction dir, RandomSource rand, float r, float g, float b, float a, Level level, BlockPos pos) {
        List<BakedQuad> quads = model.getQuads(state, dir, rand);
        if (quads == null || quads.isEmpty()) return;
        
        BlockColors blockColors = Minecraft.getInstance().getBlockColors();
        
        for (BakedQuad quad : quads) {
            float qr = r;
            float qg = g;
            float qb = b;
            
            if (quad.isTinted() && level != null && pos != null) {
                int tintIndex = quad.getTintIndex();
                int color = blockColors.getColor(state, level, pos, tintIndex);
                float tr = (color >> 16 & 255) / 255.0F;
                float tg = (color >> 8 & 255) / 255.0F;
                float tb = (color & 255) / 255.0F;
                qr *= tr;
                qg *= tg;
                qb *= tb;
            }

            int[] vertices = quad.getVertices();
            // DefaultVertexFormat.BLOCK: 8 ints per vertex (32 bytes)
            // Position: 0, 1, 2 (floats)
            // UV: 4, 5 (floats)
            
            for (int i = 0; i < 4; i++) {
                float vx = Float.intBitsToFloat(vertices[i * 8 + 0]);
                float vy = Float.intBitsToFloat(vertices[i * 8 + 1]);
                float vz = Float.intBitsToFloat(vertices[i * 8 + 2]);
                
                float u = Float.intBitsToFloat(vertices[i * 8 + 4]);
                float v = Float.intBitsToFloat(vertices[i * 8 + 5]);
                
                buf.vertex(pose, (float)(x + vx), (float)(y + vy), (float)(z + vz))
                   .uv(u, v)
                   .color(qr, qg, qb, a)
                   .endVertex();
            }
        }
    }

    private static BlockState getCarpetState(DyeColor color) {
        switch (color) {
            case WHITE: return Blocks.WHITE_CARPET.defaultBlockState();
            case ORANGE: return Blocks.ORANGE_CARPET.defaultBlockState();
            case MAGENTA: return Blocks.MAGENTA_CARPET.defaultBlockState();
            case LIGHT_BLUE: return Blocks.LIGHT_BLUE_CARPET.defaultBlockState();
            case YELLOW: return Blocks.YELLOW_CARPET.defaultBlockState();
            case LIME: return Blocks.LIME_CARPET.defaultBlockState();
            case PINK: return Blocks.PINK_CARPET.defaultBlockState();
            case GRAY: return Blocks.GRAY_CARPET.defaultBlockState();
            case LIGHT_GRAY: return Blocks.LIGHT_GRAY_CARPET.defaultBlockState();
            case CYAN: return Blocks.CYAN_CARPET.defaultBlockState();
            case PURPLE: return Blocks.PURPLE_CARPET.defaultBlockState();
            case BLUE: return Blocks.BLUE_CARPET.defaultBlockState();
            case BROWN: return Blocks.BROWN_CARPET.defaultBlockState();
            case GREEN: return Blocks.GREEN_CARPET.defaultBlockState();
            case RED: return Blocks.RED_CARPET.defaultBlockState();
            case BLACK: return Blocks.BLACK_CARPET.defaultBlockState();
            default: return Blocks.WHITE_CARPET.defaultBlockState();
        }
    }

    private static void renderChunkChests(BufferBuilder buf, Matrix4f pose, ChunkPos cp, ChunkScanner.ScannedChunk chunkData, double centerX, double centerZ, double centerY, int minBuildHeight, int minY, int maxY, boolean isUnderground, Direction cullDirection) {
        int[] packedPositions = chunkData.positions;
        byte[] lights = chunkData.lights;
        if (packedPositions == null) return;
        
        // Pass 1: Single Chests
        renderChestPass(buf, pose, packedPositions, lights, cp, centerX, centerZ, centerY, minBuildHeight, minY, maxY, isUnderground, cullDirection, 0);
        // Pass 2: Left Chests
        renderChestPass(buf, pose, packedPositions, lights, cp, centerX, centerZ, centerY, minBuildHeight, minY, maxY, isUnderground, cullDirection, 1);
        // Pass 3: Right Chests
        renderChestPass(buf, pose, packedPositions, lights, cp, centerX, centerZ, centerY, minBuildHeight, minY, maxY, isUnderground, cullDirection, 2);
    }

    private static void renderChestPass(BufferBuilder buf, Matrix4f pose, int[] positions, byte[] lights, ChunkPos cp, double centerX, double centerZ, double centerY, int minBuildHeight, int minY, int maxY, boolean isUnderground, Direction cullDirection, int targetType) {
        // Use standard POSITION_COLOR shader (no texture binding needed for manual geometry)
        // Ensure shader is set before calling this if not already set, or set here?
        // Caller (renderChunks) sets POSITION_COLOR before calling renderChunkChests.
        // But renderChunkChests was setting texture before.
        // We need to ensure we are in POSITION_COLOR mode.
        // Actually, renderChunks calls renderChunkChests then restores POSITION_COLOR.
        // So we can assume POSITION_COLOR is active if we don't change it, OR we should explicitly set it if we want to be safe.
        // But since we are inside a method that might be called in sequence, let's just assume the builder is ready for COLOR.
        // Wait, the previous implementation used POSITION_TEX_COLOR and bound a texture.
        // We need to switch to POSITION_COLOR.
        
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull(); // Disable Cull for manual geometry to ensure visibility regardless of winding
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        for (int i = 0; i < positions.length; i++) {
            int packed = positions[i];
            int renderType = (packed >> 17) & 0x7F;
            
            if (renderType != 32) continue; // Only Chests
            
            int exposedFaces = (packed >> 24) & 0x3F;
            int type = (exposedFaces >> 2) & 0x3;
            
            if (type != targetType) continue;
            
            int facingIdx = exposedFaces & 0x3; // 0=N, 1=S, 2=E, 3=W
            
            int x = packed & 0xF;
            int z = (packed >> 4) & 0xF;
            int relY = (packed >> 8) & 0x1FF;
            int h = relY + minBuildHeight;
            
            if (h < minY || h > maxY) continue;
            
            double rx = (cp.x * 16 + x) + 0.5 - centerX;
            double ry = h - centerY;
            double rz = (cp.z * 16 + z) + 0.5 - centerZ;
            
            // Sims 4 Style Culling (Wall Cutaway)
            if (isUnderground && cullDirection != null) {
                // Only cull blocks at or above player feet level (Walls)
                if (ry >= 0) {
                    if (cullDirection == Direction.NORTH && rz < -0.5) continue;
                    if (cullDirection == Direction.SOUTH && rz > 0.5) continue;
                    if (cullDirection == Direction.EAST && rx > 0.5) continue;
                    if (cullDirection == Direction.WEST && rx < -0.5) continue;
                }
            }

            float brightness = 1.0f;
            if (isUnderground && !ClientSettings.fullBrightMap) {
                double distSq = rx * rx + ry * ry + rz * rz;
                if (distSq > 36.0) {
                    double dist = Math.sqrt(distSq);
                    double fade = 1.0 - ((dist - 6.0) / 10.0);
                    brightness = (float) Math.max(0.3, Math.min(1.0, fade));
                }
            }
            
            boolean useNightMode = ClientSettings.isNightMode || isUnderground;
            if (useNightMode && lights != null && i < lights.length) {
                int lightLevel = lights[i];
                float nightBase = 0.25f; 
                float lightContrib = (lightLevel / 15.0f);
                brightness *= (nightBase + lightContrib * (1.0f - nightBase));
            }
            
            // Standard Chest Rotation - Inverted logic as per request (Flip 180)
            float yRot = 0;
            switch(facingIdx) {
                case 0: yRot = 0; break;   // North (Was 180)
                case 1: yRot = 180; break; // South (Was 0)
                case 2: yRot = 90; break;  // East (Was 270)
                case 3: yRot = 270; break; // West (Was 90)
            }
            
            Matrix4f localPose = new Matrix4f(pose);
            localPose.translate((float)rx, (float)ry + 0.5f, (float)rz);
            localPose.rotate(Axis.YP.rotationDegrees(yRot));
            localPose.translate(-0.5f, -0.5f, -0.5f);
            
            renderChestModelManual(buf, localPose, type, brightness);
        }
        
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull(); // Re-enable Cull
    }

    private static void renderChestModelManual(BufferBuilder buf, Matrix4f pose, int type, float b) {
        float alpha = 1.0f;
        
        // Colors
        // Wood: #8f6222
        // R=0.56, G=0.38, B=0.13
        float woodR = 0.56f * b;
        float woodG = 0.38f * b;
        float woodB = 0.13f * b;
        
        // Lock: Silver
        // R=0.75, G=0.75, B=0.75 (C0C0C0)
        float lockR = 0.75f * b;
        float lockG = 0.75f * b;
        float lockB = 0.75f * b;
        
        // Separation Line / Shadow: Darker
        float shadowR = woodR * 0.6f;
        float shadowG = woodG * 0.6f;
        float shadowB = woodB * 0.6f;

        // Dimensions
        // Standard Chest: 14x14 pixels (1/16 padding on sides)
        // Height: 14/16 (0.875)
        float hBase = 10/16f; // 0.625
        float hLid = 4/16f;   // 0.25
        float hTotal = hBase + hLid; // 0.875
        
        // X Bounds (Left/Right) - Facing South (+Z is Front, +X is Left, -X is Right)
        // Wait, in my manual setup:
        // +X is East (Left of South-facing player? No, Right of player)
        // Let's stick to Local Space.
        // Z+ is Front. Z- is Back.
        // X+ is Right (East). X- is Left (West).
        // Standard: Center is 0.5. Width 14/16.
        // X Range: 1/16 to 15/16.
        
        float xMin = 1/16f;
        float xMax = 15/16f;
        
        int faces = 63; // All faces (1=W, 2=E, 4=D, 8=U, 16=N, 32=S)
        
        if (type == 2) { // Swapped: Treat Type 2 as Left Chest (Connects to Right)
            // Left (East) connects on Local X+
            xMin = 1/16f;
            xMax = 1.0f; // Connects on +X side
            faces &= ~2; // Hide East Face (Local X+)
        } else if (type == 1) { // Swapped: Treat Type 1 as Right Chest
            // Right (West) connects on Local X-
            xMin = 0.0f; // Connects on -X side
            xMax = 15/16f;
            faces &= ~1; // Hide West Face (Local X-)
        }
        
        float zMin = 1/16f;
        float zMax = 15/16f; // Front Face is at Z=15/16
        
        // 1. Base (Bottom Box)
        // Y: 0 to 10/16
        // Add a small gap at top for "separation"
        float yBaseEnd = 9.5f/16f;
        renderColorBox(buf, pose, xMin, 0, zMin, xMax, yBaseEnd, zMax, woodR, woodG, woodB, alpha, faces);
        
        // 2. Lid (Top Box)
        // Y: 10/16 to 14/16
        // Start slightly higher to create gap
        float yLidStart = 10/16f;
        float yLidEnd = 14/16f;
        renderColorBox(buf, pose, xMin, yLidStart, zMin, xMax, yLidEnd, zMax, woodR, woodG, woodB, alpha, faces);
        
        // 3. Dark Separation Line
        // Extend to edge if connected
        float sepXMin = xMin + 0.01f;
        float sepXMax = xMax - 0.01f;
        
        if (type == 2) sepXMax = xMax; // Swapped: No gap on connected side (East) for Type 2
        if (type == 1) sepXMin = xMin; // Swapped: No gap on connected side (West) for Type 1
        
        renderColorBox(buf, pose, sepXMin, yBaseEnd, zMin + 0.01f, sepXMax, yLidStart, zMax - 0.01f, shadowR, shadowG, shadowB, alpha, faces);
        
        // 4. Lock
        // Size: 2 pixels wide (2/16 = 0.125), 4 pixels high (4/16 = 0.25), 1 pixel thick (1/16)
        // Centered on ZMax face.
        // Y Center: Around the seam (10/16).
        // Y Range: 8/16 to 12/16?
        float lockW = 2/16f;
        float lockH = 4/16f;
        float lockD = 1/16f;
        
        float lockY = 8/16f;
        float lockZ = zMax; // On the front face
        
        float lockXMin, lockXMax;
        
        if (type == 0) { // Single
            // Center
            float cx = (xMin + xMax) / 2.0f;
            lockXMin = cx - lockW/2;
            lockXMax = cx + lockW/2;
            renderColorBox(buf, pose, lockXMin, lockY, lockZ, lockXMax, lockY + lockH, lockZ + lockD, lockR, lockG, lockB, alpha);
        } else if (type == 2) { // Swapped: Left (Type 2)
            // Lock is on the connecting edge (X=1)
            // Draw half lock on the right edge.
            // Width 1 pixel (1/16).
            lockXMin = xMax - 1/16f;
            lockXMax = xMax;
             renderColorBox(buf, pose, lockXMin, lockY, lockZ, lockXMax, lockY + lockH, lockZ + lockD, lockR, lockG, lockB, alpha);
        } else if (type == 1) { // Swapped: Right (Type 1)
            // Lock is on the connecting edge (X=0)
            // Draw half lock on the left edge.
            lockXMin = xMin;
            lockXMax = xMin + 1/16f;
             renderColorBox(buf, pose, lockXMin, lockY, lockZ, lockXMax, lockY + lockH, lockZ + lockD, lockR, lockG, lockB, alpha);
        }
    }

    private static void renderColorBox(BufferBuilder buf, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a) {
        renderColorBox(buf, pose, x0, y0, z0, x1, y1, z1, r, g, b, a, 63);
    }

    private static void renderColorBox(BufferBuilder buf, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a, int faces) {
        // Bottom (4)
        if ((faces & 4) != 0) {
            buf.vertex(pose, x0, y0, z0).color(r*0.5f, g*0.5f, b*0.5f, a).endVertex();
            buf.vertex(pose, x1, y0, z0).color(r*0.5f, g*0.5f, b*0.5f, a).endVertex();
            buf.vertex(pose, x1, y0, z1).color(r*0.5f, g*0.5f, b*0.5f, a).endVertex();
            buf.vertex(pose, x0, y0, z1).color(r*0.5f, g*0.5f, b*0.5f, a).endVertex();
        }
        
        // Top (8)
        if ((faces & 8) != 0) {
            buf.vertex(pose, x0, y1, z0).color(r, g, b, a).endVertex();
            buf.vertex(pose, x0, y1, z1).color(r, g, b, a).endVertex();
            buf.vertex(pose, x1, y1, z1).color(r, g, b, a).endVertex();
            buf.vertex(pose, x1, y1, z0).color(r, g, b, a).endVertex();
        }
        
        // West (x0) (1)
        if ((faces & 1) != 0) {
            buf.vertex(pose, x0, y0, z0).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x0, y0, z1).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x0, y1, z1).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x0, y1, z0).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
        }
        
        // East (x1) (2)
        if ((faces & 2) != 0) {
            buf.vertex(pose, x1, y0, z0).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x1, y1, z0).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x1, y1, z1).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
            buf.vertex(pose, x1, y0, z1).color(r*0.8f, g*0.8f, b*0.8f, a).endVertex();
        }
        
        // North (z0) (16)
        if ((faces & 16) != 0) {
            buf.vertex(pose, x0, y0, z0).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x0, y1, z0).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x1, y1, z0).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x1, y0, z0).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
        }
        
        // South (z1) (32)
        if ((faces & 32) != 0) {
            buf.vertex(pose, x0, y0, z1).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x1, y0, z1).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x1, y1, z1).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
            buf.vertex(pose, x0, y1, z1).color(r*0.6f, g*0.6f, b*0.6f, a).endVertex();
        }
    }

    private static void renderNetherLavaFloor(PoseStack poseStack, Player player, int radius) {
        double centerX = player.getX();
        double centerZ = player.getZ();
        double centerY = player.getY();
        
        // Lava surface level (bottom of block 31)
        double planeY = 31.0 - centerY; 
        
        ChunkPos playerChunk = player.chunkPosition();
        Map<ChunkPos, ChunkScanner.ScannedChunk> data = ChunkScanner.getData();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        // Lava Color: Bright Orange 0xFF6600
        float r = 1.0f; 
        float g = 0.4f;
        float b = 0.0f;
        float alpha = 1.0f;
        
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f pose = poseStack.last().pose();
        
        // Iterate only loaded chunks within radius
        if (radius > 0) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                     ChunkPos cp = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                     
                     // Only render lava floor if the chunk is actually loaded/scanned
                     if (data.containsKey(cp)) {
                         double rx = (cp.x * 16) - centerX;
                         double rz = (cp.z * 16) - centerZ;
                         
                         float x1 = (float)rx;
                         float z1 = (float)rz;
                         float x2 = (float)(rx + 16.0);
                         float z2 = (float)(rz + 16.0);
                         float y = (float)planeY;

                         buf.vertex(pose, x1, y, z1).color(r, g, b, alpha).endVertex();
                         buf.vertex(pose, x1, y, z2).color(r, g, b, alpha).endVertex();
                         buf.vertex(pose, x2, y, z2).color(r, g, b, alpha).endVertex();
                         buf.vertex(pose, x2, y, z1).color(r, g, b, alpha).endVertex();
                     }
                }
            }
        }
        
        BufferUploader.drawWithShader(buf.end());
    }

    private static void renderWaypoints(PoseStack poseStack, Player player, float cameraYaw, float cameraPitch) {
        if (ClientSettings.waypoints.isEmpty()) return;
        
        double centerX = player.getX();
        double centerZ = player.getZ();
        double centerY = player.getY();
        
        // Track waypoint counts at each location to stack them
        Map<String, Integer> waypointCounts = new HashMap<>();
        
        for (ClientSettings.Waypoint wp : ClientSettings.waypoints) {
            if (!wp.visible) continue;
            // Only render waypoints in the current dimension
            if (player.level() != null && !wp.getDimension().equals(player.level().dimension().location().toString())) continue;
            
            double rx = wp.x + 0.5 - centerX;
            double rz = wp.z + 0.5 - centerZ;
            
            // Calculate distance for fading
            double distSq = rx*rx + rz*rz;
            float maxFadeDist = 3.0f; // blocks
            float alpha = 0.8f;
            
            if (distSq < maxFadeDist * maxFadeDist) {
                double dist = Math.sqrt(distSq);
                alpha = (float) (dist / maxFadeDist) * 0.8f;
                if (alpha < 0.1f) alpha = 0.05f; // almost invisible
            }
            
            // Calculate stacking offset
            String locKey = wp.x + "_" + wp.y + "_" + wp.z;
            int stackIndex = waypointCounts.getOrDefault(locKey, 0);
            waypointCounts.put(locKey, stackIndex + 1);
            
            // Draw Name Tag & Icon
            // Position name tag slightly above the waypoint's actual Y coordinate
            // Stack overlapping waypoints vertically (4.0 blocks per index)
            double ry = wp.y - centerY; 
            double stackOffset = stackIndex * 4.0; 
            
            if (alpha > 0.2f) { // Only show name if somewhat visible (not right on top of player)
                poseStack.pushPose();
                poseStack.translate(rx, ry + 2.5 + stackOffset, rz);
                // Billboard effect
                poseStack.mulPose(Axis.YP.rotationDegrees(-cameraYaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(-cameraPitch));
                
                float scale = 0.15f; // Slightly larger text
                poseStack.scale(scale, -scale, scale);
                
                // Disable depth test to ensure text/icon is always visible (on top of beam and blocks)
                RenderSystem.disableDepthTest();

                // --- Draw Icon ---
                ResourceLocation iconLoc = new ResourceLocation("voxelview3d", "textures/waypoints/" + wp.iconName + ".png");
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, iconLoc);
                
                // Tint Icon with Waypoint Color
                float r = ((wp.color >> 16) & 0xFF) / 255.0f;
                float g = ((wp.color >> 8) & 0xFF) / 255.0f;
                float b = (wp.color & 0xFF) / 255.0f;
                RenderSystem.setShaderColor(r, g, b, 1.0F);
                
                // Draw Icon Quad
                float iconSize = 16.0f; // Size in local scaled units
                float iconY = -2.0f; // Slightly above text
                
                // We need a new buffer for the icon
                Tesselator tessIcon = Tesselator.getInstance();
                BufferBuilder bufIcon = tessIcon.getBuilder();
                bufIcon.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                Matrix4f matrix = poseStack.last().pose();
                
                bufIcon.vertex(matrix, -iconSize/2, iconY - iconSize, 0).uv(0, 0).endVertex();
                bufIcon.vertex(matrix, -iconSize/2, iconY, 0).uv(0, 1).endVertex();
                bufIcon.vertex(matrix, iconSize/2, iconY, 0).uv(1, 1).endVertex();
                bufIcon.vertex(matrix, iconSize/2, iconY - iconSize, 0).uv(1, 0).endVertex();
                
                BufferUploader.drawWithShader(bufIcon.end());
                
                // Reset Shader Color
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                // -----------------
                
                int textWidth = Minecraft.getInstance().font.width(wp.name);
                int halfWidth = textWidth / 2;
                
                // Draw background for text for better readability
                // Use fill (we need to access Tesselator or just use standard GuiGraphics fill? 
                // We are in 3D context, so drawInBatch handles it, but background requires a separate quad or font options.
                // drawInBatch has backgroundColor param (the last int).
                // Use SEE_THROUGH to match depth disabled state
                
                Minecraft.getInstance().font.drawInBatch(wp.name, -halfWidth, 0, 0xFFFFFFFF, false, poseStack.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(), Font.DisplayMode.SEE_THROUGH, 0x40000000, 15728880);
                
                // Re-enable depth test
                RenderSystem.enableDepthTest();
                
                poseStack.popPose();
            }
        }
    }
    
    private static void renderPlayerMarker(PoseStack poseStack, Player player) {
        if (!(player instanceof AbstractClientPlayer)) return;
        
        AbstractClientPlayer abstractClientPlayer = (AbstractClientPlayer) player;
        ResourceLocation skin = abstractClientPlayer.getSkinTextureLocation();
        
        RenderSystem.setShaderTexture(0, skin);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        
        poseStack.pushPose();
        // Rotate to match player facing
        // Model faces North (-Z). Minecraft Yaw: S=0, W=90, N=180, E=270.
        // We need to rotate by (180 - playerYaw) to align model with player direction.
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - player.getYRot()));
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        float headSize = 2.0f; // Increased size (was 1.0f)
        float borderSize = 2.3f; // Slightly larger for border
        
        // 1. Render Textured Head
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        renderTexturedHead(buf, poseStack.last().pose(), 0, 0, 0, headSize);
        BufferUploader.drawWithShader(buf.end());
        
        // 2. Render White Border (Inverted Hull)
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderInvertedColorBox(buf, poseStack.last().pose(), 0, 0, 0, borderSize, 1.0f, 1.0f, 1.0f, 1.0f);
        BufferUploader.drawWithShader(buf.end());
        
        poseStack.popPose();
    }
    
    private static void renderChunks(PoseStack poseStack, Player player, int radius, int minBuildHeight, int renderMinY, int renderMaxY, boolean isUnderground, float cameraYaw) {
        double centerX = player.getX();
        double centerZ = player.getZ();
        double centerY = player.getY();
        
        ChunkPos playerChunk = player.chunkPosition();
        Map<ChunkPos, ChunkScanner.ScannedChunk> data = ChunkScanner.getData();
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Matrix4f pose = poseStack.last().pose();
        
        // Calculate Cull Direction (Sims 4 Style Wall Culling)
        // Based on Camera Yaw.
        // User feedback: "lo detecta justo al revez" (It detects it exactly backwards).
        // So we invert the logic: Look South -> Cull South (instead of North).
        Direction cullDirection = null;
        if (isUnderground) {
            float yaw = (cameraYaw % 360 + 360) % 360;
            if (yaw >= 315 || yaw < 45) { // South (0)
                cullDirection = Direction.SOUTH;
            } else if (yaw >= 45 && yaw < 135) { // West (90)
                cullDirection = Direction.WEST;
            } else if (yaw >= 135 && yaw < 225) { // North (180)
                cullDirection = Direction.NORTH;
            } else { // East (270)
                cullDirection = Direction.EAST;
            }
        }

        if (radius > 0) {
            // Optimized loop: only iterate nearby chunks (coordinate loop)
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                     ChunkPos cp = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                     ChunkScanner.ScannedChunk chunkData = data.get(cp);
                     if (chunkData != null) {
                         // Check Version?
                         // If version mismatch, we could request rescan, but scan is server/world thread based.
                         // For now, assume if data exists it is valid or will be replaced eventually.
                         
                         // Flush per chunk to avoid massive buffers (OutOfMemory)
                         buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                         renderChunkBlocks(buf, pose, cp, chunkData, centerX, centerZ, centerY, minBuildHeight, renderMinY, renderMaxY, isUnderground, cullDirection);
                         BufferUploader.drawWithShader(buf.end());
                         
                         // Textured Pass
                         RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                         RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                         buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                         renderChunkBlocksTextured(buf, pose, cp, chunkData, centerX, centerZ, centerY, minBuildHeight, renderMinY, renderMaxY, isUnderground, cullDirection);
                         BufferUploader.drawWithShader(buf.end());
                         
                         // Chest Pass
                         renderChunkChests(buf, pose, cp, chunkData, centerX, centerZ, centerY, minBuildHeight, renderMinY, renderMaxY, isUnderground, cullDirection);
                         
                         // Restore Shader for next iteration
                         RenderSystem.setShader(GameRenderer::getPositionColorShader);
                     }
                }
            }
        } else {
            // Render all scanned chunks (for full map)
            for (Map.Entry<ChunkPos, ChunkScanner.ScannedChunk> entry : data.entrySet()) {
                buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                renderChunkBlocks(buf, pose, entry.getKey(), entry.getValue(), centerX, centerZ, centerY, minBuildHeight, renderMinY, renderMaxY, isUnderground, cullDirection);
                BufferUploader.drawWithShader(buf.end());
                
                // Textured Pass
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                renderChunkBlocksTextured(buf, pose, entry.getKey(), entry.getValue(), centerX, centerZ, centerY, minBuildHeight, renderMinY, renderMaxY, isUnderground, cullDirection);
                BufferUploader.drawWithShader(buf.end());

                // Chest Pass
                renderChunkChests(buf, pose, entry.getKey(), entry.getValue(), centerX, centerZ, centerY, minBuildHeight, renderMinY, renderMaxY, isUnderground, cullDirection);
                
                // Restore Shader
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
            }
        }
    }
    
    private static void renderChunkBlocks(BufferBuilder buf, Matrix4f pose, ChunkPos cp, ChunkScanner.ScannedChunk chunkData, double centerX, double centerZ, double centerY, int minBuildHeight, int minY, int maxY, boolean isUnderground, Direction cullDirection) {
        int[] packedPositions = chunkData.positions;
        int[] colors = chunkData.colors;
        byte[] lights = chunkData.lights;
        
        if (packedPositions == null) return;
        
        for (int i = 0; i < packedPositions.length; i++) {
            int packed = packedPositions[i];
            int color = colors[i];
            
            int x = packed & 0xF;
            int z = (packed >> 4) & 0xF;
            int relY = (packed >> 8) & 0x1FF;
            int renderType = (packed >> 17) & 0x7F; // Expanded to 7 bits
            int exposedFaces = (packed >> 24) & 0x3F; // Shifted to 24
            
            if (renderType >= 32 && renderType != 61 && renderType != 62 && renderType != 63 && renderType != 64) continue; // Skip textured blocks (handled in textured pass), but allow Rails/Repeater/Comparator/Piston
            
            // If exposedFaces is 0, it might be old data OR a block with no exposed faces (fully buried).
            // But fully buried blocks shouldn't be in the list?
            // Actually, buried blocks are culled during scan.
            // So if it's in the list, it MUST have some exposure.
            // If exposedFaces is 0, it means it's old data format (where bits were 0).
            // So we default to ALL exposed to be safe (and ugly grid) or just assume Top?
            // Let's assume old data has Top exposed at least.
            // BUT: We must exclude blocks that use exposedFaces for data packing where 0 is a valid value!
            // 20: Button, 21: Lever, 61: Rail, 62: Repeater, 63: Comparator
            if (exposedFaces == 0 && renderType != 20 && renderType != 21 && renderType != 61 && renderType != 62 && renderType != 63) {
                exposedFaces = 0x3F; 
            }
            
            int h = relY + minBuildHeight;
            
            if (h < minY || h > maxY) continue;
            
            // Calculate absolute position relative to player (camera)
            // Center the block (blocks are 0-1, so center is +0.5)
            double rx = (cp.x * 16 + x) + 0.5 - centerX;
            double ry = h - centerY;
            double rz = (cp.z * 16 + z) + 0.5 - centerZ;
            
            // Sims 4 Style Culling (Wall Cutaway)
            if (isUnderground && cullDirection != null) {
                // Only cull blocks at or above player feet level (Walls)
                // ry=0 means block center is at player feet Y.
                // Floor is usually ry = -1.
                if (ry >= 0) {
                    if (cullDirection == Direction.NORTH && rz < -0.5) continue;
                    if (cullDirection == Direction.SOUTH && rz > 0.5) continue;
                    if (cullDirection == Direction.EAST && rx > 0.5) continue;
                    if (cullDirection == Direction.WEST && rx < -0.5) continue;
                }
            }

            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            
            // "Exterior Darker" Logic:
            // If underground, apply distance-based fog/dimming to simulate lighting
            float brightness = 1.0f;
            float alpha = 1.0f;

            if (isUnderground && !ClientSettings.fullBrightMap) {
                // Distance squared
                double distSq = rx * rx + ry * ry + rz * rz;
                double dist = Math.sqrt(distSq);

                // Brightness (Fog): Darken blocks far away
                // Fade start at 6 blocks, fully dark at 16 blocks?
                double maxDist = 16.0;
                if (distSq > 36.0) { // Start fading after 6 blocks
                    double fade = 1.0 - ((dist - 6.0) / (maxDist - 6.0));
                    brightness = (float) Math.max(0.3, Math.min(1.0, fade)); // Min brightness 0.3
                }

                // Transparency: Make nearby blocks transparent so we can see through walls
                // User request: "transparenté lo que este cerca no todo"
                double transStart = 2.0;
                double transEnd = 8.0;

                if (dist < transStart) {
                    alpha = 0.3f;
                } else if (dist > transEnd) {
                    alpha = 1.0f;
                } else {
                    // Interpolate from 0.3 to 1.0
                    double t = (dist - transStart) / (transEnd - transStart);
                    alpha = (float) (0.3 + t * 0.7);
                }
            }
            
            // Night Mode Lighting Logic (or Underground Mode)
            boolean useNightMode = ClientSettings.isNightMode || isUnderground;
            
            if (useNightMode && lights != null && i < lights.length) {
                int lightLevel = lights[i];
                
                // Add light level contribution (up to 1.0)
                float nightBase = 0.25f; 
                float lightContrib = (lightLevel / 15.0f);
                float nightFactor = nightBase + (lightContrib * (1.0f - nightBase));
                
                brightness *= nightFactor;
            }

            // Offset for manual models to avoid z-fighting with ground
            // User reported floor occupies a bit more height, cutting off models.
            double mOff = 0.02; // Manual Offset (Lift everything slightly)
            double ryOff = ry + mOff;

            // Render based on Type
            if (renderType == 1) { // RENDER_TORCH
                // Small box, centered at bottom of block space
                // Visual size: 0.2 x 0.6 x 0.2
                renderBox(buf, pose, rx, ryOff, rz, 0.2f, 0.6f, 0.2f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
            } else if (renderType == 2) { // RENDER_LANTERN
                // Lantern: Metal Top, Color Bottom
                
                // 1. Bottom Light Part (Main Body)
                // More rectangular and taller as requested.
                // Old: ry - 0.3, Height 0.4.
                // New: Start lower (ry - 0.4), Height 0.5. Width 0.35.
                // Note: Lanterns hang usually, but if on floor, we want them lifted too.
                renderBox(buf, pose, rx, ryOff, rz, 0.35f, 0.5f, 0.35f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                
                // Metal Color: Dark Gray
                float metalR = 68 / 255.0f;
                float metalG = 68 / 255.0f;
                float metalB = 68 / 255.0f;

                // 2. Middle Metal Ring (Top Cap)
                // Pos: ry + 0.1 (Above body). Height 0.05. Slightly wider.
                renderBox(buf, pose, rx, ryOff + 0.5, rz, 0.4f, 0.05f, 0.4f, metalR * brightness, metalG * brightness, metalB * brightness, alpha);

                // 3. Top Metal Handle/Box (Extra Grey Part)
                // Pos: ry + 0.15 (Above ring). Height 0.1. Narrower.
                // This adds the "extra grey part on top" requested.
                renderBox(buf, pose, rx, ryOff + 0.55, rz, 0.2f, 0.1f, 0.2f, metalR * brightness, metalG * brightness, metalB * brightness, alpha);
                
            } else if (renderType == 3 || renderType == 4) { // RENDER_CAVE_VINE or RENDER_CAVE_VINE_WITH_BERRIES
                // Cave Vine Logic
                // 1. Brown Stem/Root (Inner Core)
                // Thin, full height to connect blocks.
                float stemR = 89 / 255.0f;
                float stemG = 61 / 255.0f;
                float stemB = 41 / 255.0f;
                renderBox(buf, pose, rx, ryOff, rz, 0.15f, 1.0f, 0.15f, stemR * brightness, stemG * brightness, stemB * brightness, alpha);

                // 2. Green Foliage (Outer Layer)
                // Slightly shorter than full block to reveal stem at joints, giving a "connected" look.
                // Size: 0.45 width, 0.8 height.
                float vineR = 92 / 255.0f;
                float vineG = 124 / 255.0f;
                float vineB = 53 / 255.0f;
                renderBox(buf, pose, rx, ryOff + 0.1, rz, 0.45f, 0.8f, 0.45f, vineR * brightness, vineG * brightness, vineB * brightness, alpha);
                
                // If it has berries (Type 4), render small orange cubes
                if (renderType == 4) {
                    // Berries: Small orange glowing boxes.
                    float berryR = 1.0f; // Bright Orange/Yellow
                    float berryG = 0.6f;
                    float berryB = 0.0f;
                    
                    // Boost brightness for glow
                    float berryBrightness = Math.max(brightness, 0.9f); 
                    float bSize = 0.15f;
                    
                    // Berry 1 (Lower, Right-ish)
                    // Offset randomly to look organic.
                    // X + 0.25, Y - 0.1, Z + 0.1
                    renderBox(buf, pose, rx + 0.25, ryOff - 0.1, rz + 0.1, bSize, bSize, bSize, berryR * berryBrightness, berryG * berryBrightness, berryB * berryBrightness, alpha);
                    
                    // Berry 2 (Higher, Left-ish)
                    // X - 0.25, Y + 0.3, Z - 0.1
                    // Staggered height and opposite side
                    renderBox(buf, pose, rx - 0.25, ryOff + 0.3, rz - 0.1, bSize, bSize, bSize, berryR * berryBrightness, berryG * berryBrightness, berryB * berryBrightness, alpha);
                }

            } else if (renderType == 5) { // RENDER_SUGAR_CANE
                  // Sugar Cane: 3 Green Tubes with Borders
                  float caneR = 150 / 255.0f;
                  float caneG = 210 / 255.0f;
                  float caneB = 100 / 255.0f;
                  
                  // Dark Green Border Color (Lighter now)
                  float bR = 60 / 255.0f;
                  float bG = 100 / 255.0f;
                  float bB = 40 / 255.0f;
                  
                  float tubeSize = 0.25f;
                  float bThick = 0.03f; // Border thickness
                  
                  // Tube Offsets: {x, z}
                  float[][] offsets = {
                      {-0.2f, -0.1f}, // Left-Back
                      {0.2f, -0.1f},  // Right-Back
                      {0.0f, 0.2f}    // Front-Center
                  };
                  
                  for (float[] off : offsets) {
                      double tx = rx + off[0];
                      double tz = rz + off[1];
                      
                      // Main Tube
                      renderBox(buf, pose, tx, ryOff, tz, tubeSize, 1.0f, tubeSize, caneR * brightness, caneG * brightness, caneB * brightness, alpha);
                      
                      // Borders
                      // West
                      renderBox(buf, pose, tx - tubeSize/2 - bThick/2, ryOff, tz, bThick, 1.0f, tubeSize + 2*bThick, bR * brightness, bG * brightness, bB * brightness, alpha);
                      // East
                      renderBox(buf, pose, tx + tubeSize/2 + bThick/2, ryOff, tz, bThick, 1.0f, tubeSize + 2*bThick, bR * brightness, bG * brightness, bB * brightness, alpha);
                      // North (Between W/E)
                      renderBox(buf, pose, tx, ryOff, tz - tubeSize/2 - bThick/2, tubeSize, 1.0f, bThick, bR * brightness, bG * brightness, bB * brightness, alpha);
                      // South (Between W/E)
                      renderBox(buf, pose, tx, ryOff, tz + tubeSize/2 + bThick/2, tubeSize, 1.0f, bThick, bR * brightness, bG * brightness, bB * brightness, alpha);
                  }
                  
             } else if (renderType == 6) { // RENDER_CACTUS
                 // Cactus: Thinner body + Spines + Vertical Stripes
                 // Body Size: 14/16 = 0.875
                 float bodySize = 0.875f;
                 
                 // Use block color (usually green)
                 float baseR = (r * brightness) / 255.0f;
                 float baseG = (g * brightness) / 255.0f;
                 float baseB = (b * brightness) / 255.0f;
                 
                 renderBox(buf, pose, rx, ryOff, rz, bodySize, 1.0f, bodySize, baseR, baseG, baseB, alpha);
                 
                 // Vertical Dark Stripes (Ribs)
                 // Darker green: Multiply by 0.6
                 float stripeR = baseR * 0.6f;
                 float stripeG = baseG * 0.6f;
                 float stripeB = baseB * 0.6f;
                 
                 float sW = 0.1f; // Stripe Width
                 float sD = 0.02f; // Stripe Depth (Protrusion)
                 float sOff = bodySize / 2.0f + (sD / 2.0f); // Slightly outside
                 
                 // Z- Face
                 renderBox(buf, pose, rx - 0.2, ryOff, rz - sOff, sW, 1.0f, sD, stripeR, stripeG, stripeB, alpha);
                 renderBox(buf, pose, rx + 0.2, ryOff, rz - sOff, sW, 1.0f, sD, stripeR, stripeG, stripeB, alpha);
                 
                 // Z+ Face
                 renderBox(buf, pose, rx - 0.2, ryOff, rz + sOff, sW, 1.0f, sD, stripeR, stripeG, stripeB, alpha);
                 renderBox(buf, pose, rx + 0.2, ryOff, rz + sOff, sW, 1.0f, sD, stripeR, stripeG, stripeB, alpha);
                 
                 // X- Face (Rotated)
                 renderBox(buf, pose, rx - sOff, ryOff, rz - 0.2, sD, 1.0f, sW, stripeR, stripeG, stripeB, alpha);
                 renderBox(buf, pose, rx - sOff, ryOff, rz + 0.2, sD, 1.0f, sW, stripeR, stripeG, stripeB, alpha);
                 
                 // X+ Face (Rotated)
                 renderBox(buf, pose, rx + sOff, ryOff, rz - 0.2, sD, 1.0f, sW, stripeR, stripeG, stripeB, alpha);
                 renderBox(buf, pose, rx + sOff, ryOff, rz + 0.2, sD, 1.0f, sW, stripeR, stripeG, stripeB, alpha);

                 // Spines (Thorns)
                 float spineLen = 0.1f;
                 float spineThick = 0.05f;
                 float spineR = 0.9f; 
                 float spineG = 0.9f;
                 float spineB = 0.8f;
                 
                 float offset = bodySize / 2.0f; 
                 
                 // Face 1 (Z-): 2 Spines (Centered between stripes)
                renderBox(buf, pose, rx, ryOff + 0.75, rz - offset, spineThick, spineThick, spineLen, spineR, spineG, spineB, alpha);
                renderBox(buf, pose, rx, ryOff + 0.25, rz - offset, spineThick, spineThick, spineLen, spineR, spineG, spineB, alpha);

                // Face 2 (Z+): 2 Spines
                renderBox(buf, pose, rx, ryOff + 0.75, rz + offset, spineThick, spineThick, spineLen, spineR, spineG, spineB, alpha);
                renderBox(buf, pose, rx, ryOff + 0.25, rz + offset, spineThick, spineThick, spineLen, spineR, spineG, spineB, alpha);

                // Face 3 (X-): 2 Spines
                renderBox(buf, pose, rx - offset, ryOff + 0.75, rz, spineLen, spineThick, spineThick, spineR, spineG, spineB, alpha);
                renderBox(buf, pose, rx - offset, ryOff + 0.25, rz, spineLen, spineThick, spineThick, spineR, spineG, spineB, alpha);

                // Face 4 (X+): 2 Spines
                renderBox(buf, pose, rx + offset, ryOff + 0.75, rz, spineLen, spineThick, spineThick, spineR, spineG, spineB, alpha);
                renderBox(buf, pose, rx + offset, ryOff + 0.25, rz, spineLen, spineThick, spineThick, spineR, spineG, spineB, alpha);
 
             } else if (renderType == 7) { // RENDER_SAPLING
                // Sapling: Small Brown Block (0.4x0.4x0.4)
                float sSize = 0.4f;
                // Brown Wood Color
                float woodR = 120 / 255.0f;
                float woodG = 80 / 255.0f;
                float woodB = 40 / 255.0f;
                
                // Position: Bottom Center
               // rx, ry, rz is block center.
               // To put on floor (y-0.5), we need center at (y-0.5) + (sSize/2) = y - 0.5 + 0.2 = y - 0.3
                renderBox(buf, pose, rx, ryOff, rz, sSize, sSize, sSize, woodR * brightness, woodG * brightness, woodB * brightness, alpha);
                  
              } else if (renderType == 8) { // RENDER_BAMBOO
                 // Bamboo: Single Green Tube, varying thickness but usually thin
                 float bambooSize = 0.25f; // Thin stalk
                 
                 // Bamboo Green
                 float bamR = 100 / 255.0f;
                 float bamG = 180 / 255.0f;
                 float bamB = 60 / 255.0f;
                 
                 // Render Stalk (Full Height)
                 renderBox(buf, pose, rx, ryOff, rz, bambooSize, 1.0f, bambooSize, bamR * brightness, bamG * brightness, bamB * brightness, alpha);
                 
                 // Add small leaves? (Optional, maybe later if requested)
                 // For now just the "tube upwards" as requested.
                 
              } else if (renderType == 9) { // RENDER_POTTED_PLANT
                 // 1. Flower Pot: Brown Square at bottom
                 float potR = 180 / 255.0f; // Terracotta-ish
                 float potG = 100 / 255.0f;
                 float potB = 80 / 255.0f;
                 
                 float potSize = 0.35f;
                 float potHeight = 0.3f;
                 
                 // Center Y for Pot: Bottom (-0.5) + Half Height (0.15) = -0.35
                 renderBox(buf, pose, rx, ryOff, rz, potSize, potHeight, potSize, potR * brightness, potG * brightness, potB * brightness, alpha);
                 
                 // 2. Plant Inside: Small Block with Specific Color
                 // Use the passed r, g, b values which come from ChunkScanner's getPottedPlantColor
                 float plantSize = 0.25f;
                 
                 // Center Y for Plant: Top of Pot (-0.2) + Half Plant (0.125) = -0.075
                renderBox(buf, pose, rx, ryOff + potHeight, rz, plantSize, plantSize, plantSize, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);

             } else if (renderType == 10) { // RENDER_FLOWER_POT
                 // Empty Flower Pot
                 float potR = 180 / 255.0f; // Terracotta-ish
                 float potG = 100 / 255.0f;
                 float potB = 80 / 255.0f;
                 
                 float potSize = 0.375f; // Exact MC size
                 float potHeight = 0.375f; // Exact MC height
                 
                 // Center Y for Pot: Bottom (-0.5) + Half Height (0.1875) = -0.3125
                 renderBox(buf, pose, rx, ryOff, rz, potSize, potHeight, potSize, potR * brightness, potG * brightness, potB * brightness, alpha);

                 // Pot "Hole" (Dark Top)
                 float holeSize = 0.25f; // Smaller than pot
                 float holeHeight = 0.02f; // Very thin
                 // On top of pot: Bottom (-0.5) + Height (0.375) + Half Hole (0.01) = -0.115
                 renderBox(buf, pose, rx, ryOff + potHeight, rz, holeSize, holeHeight, holeSize, 0.2f * brightness, 0.1f * brightness, 0.1f * brightness, alpha);
                 
              } else if (renderType == 11) { // RENDER_GRASS
                 // Grass/Fern: Render as multiple small blades/tufts to give "relief"
                 // Use the block color (usually biome green) passed in r,g,b
                 
                 float bladeW = 0.1f;
                 float bladeH = 0.45f; // Short grass height
                 
                 // Blade 1 (Left-Back)
                renderBox(buf, pose, rx - 0.2, ryOff, rz - 0.2, bladeW, bladeH, bladeW, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                // Blade 2 (Right-Front)
                renderBox(buf, pose, rx + 0.2, ryOff, rz + 0.2, bladeW, bladeH * 0.8f, bladeW, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                // Blade 3 (Left-Front)
                renderBox(buf, pose, rx - 0.15, ryOff, rz + 0.15, bladeW, bladeH * 0.9f, bladeW, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                // Blade 4 (Right-Back)
                renderBox(buf, pose, rx + 0.1, ryOff, rz - 0.1, bladeW, bladeH * 1.1f, bladeW, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);

             } else if (renderType == 12) { // RENDER_FLOWER
                 // Small Flower: Stem + Head
                 // Stem: Green
                 float stemR = 50 / 255.0f;
                 float stemG = 120 / 255.0f;
                 float stemB = 50 / 255.0f;
                 float stemW = 0.1f;
                 float stemH = 0.4f;
                 
                 // Render Stem (Bottom)
                 renderBox(buf, pose, rx, ryOff, rz, stemW, stemH, stemW, stemR * brightness, stemG * brightness, stemB * brightness, alpha);
                 
                 // Render Flower Head (Color passed in r,g,b)
                 float headSize = 0.25f;
                 // On top of stem
                 renderBox(buf, pose, rx, ryOff + stemH, rz, headSize, headSize, headSize, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                 
             } else if (renderType == 13) { // RENDER_TALL_FLOWER
                 // Tall Flower (Lilac, Rose Bush, etc.)
                 // Stem: Green core
                 float stemR = 50 / 255.0f;
                 float stemG = 120 / 255.0f;
                 float stemB = 50 / 255.0f;
                 float stemW = 0.15f;
                 
                 renderBox(buf, pose, rx, ryOff, rz, stemW, 1.0f, stemW, stemR * brightness, stemG * brightness, stemB * brightness, alpha);
                 
                 // Flower/Foliage Clusters
                 float bushSize = 0.5f;
                 
                 // Main Cluster
                 renderBox(buf, pose, rx, ryOff, rz, bushSize, 0.8f, bushSize, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                 
                 // Add relief/texture
                 float off = 0.2f;
                 float smallSize = 0.25f;
                 renderBox(buf, pose, rx + off, ryOff + 0.2, rz + off, smallSize, smallSize, smallSize, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                 renderBox(buf, pose, rx - off, ryOff - 0.2, rz - off, smallSize, smallSize, smallSize, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);

             } else if (renderType == 14) { // RENDER_MUSHROOM
                 // Stem: White/Off-white
                 float stemR = 240 / 255.0f;
                 float stemG = 240 / 255.0f;
                 float stemB = 240 / 255.0f;
                 float stemW = 0.15f;
                 float stemH = 0.2f;
                 
                 // Stem Position: Bottom (-0.5) + Half Height (0.1) = -0.4
                 renderBox(buf, pose, rx, ryOff, rz, stemW, stemH, stemW, stemR * brightness, stemG * brightness, stemB * brightness, alpha);
                 
                 // Cap: Uses color passed in r,g,b
                 float capSize = 0.4f;
                 float capH = 0.2f;
                 
                 // Cap Position: On top of stem (-0.5 + 0.2 + 0.1) = -0.2
                 renderBox(buf, pose, rx, ryOff + stemH, rz, capSize, capH, capSize, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                 
                 // Add white spots for Red Mushroom if color is Red-ish
                 if (r > 200 && g < 50 && b < 50) {
                     // Simple spots
                     float spotSize = 0.1f;
                     float spotH = 0.02f;
                     // Top center spot
                     renderBox(buf, pose, rx, ryOff + stemH + (capH/2) + (spotH/2), rz, spotSize, spotH, spotSize, 1.0f * brightness, 1.0f * brightness, 1.0f * brightness, alpha);
                 }

             } else if (renderType == 15) { // RENDER_GLOW_LICHEN
                 // Thin layer on attached faces.
                 // exposedFaces bits: 0:West, 1:East, 2:Down, 3:Up, 4:North, 5:South
                 float thick = 0.05f;
                 float size = 1.0f;
                 float offset = 0.5f - (thick / 2.0f); // ~0.475

                 // Glow Lichen is bright (ignore some shading?)
                 // Boost brightness
                 float lBri = Math.max(brightness, 0.9f);

                 if ((exposedFaces & 1) != 0) { // West
                     renderBox(buf, pose, rx - offset, ryOff, rz, thick, size, size, (r * lBri) / 255.0f, (g * lBri) / 255.0f, (b * lBri) / 255.0f, alpha);
                 }
                 if ((exposedFaces & 2) != 0) { // East
                     renderBox(buf, pose, rx + offset, ryOff, rz, thick, size, size, (r * lBri) / 255.0f, (g * lBri) / 255.0f, (b * lBri) / 255.0f, alpha);
                 }
                 if ((exposedFaces & 4) != 0) { // Down
                     renderBox(buf, pose, rx, ryOff - offset, rz, size, thick, size, (r * lBri) / 255.0f, (g * lBri) / 255.0f, (b * lBri) / 255.0f, alpha);
                 }
                 if ((exposedFaces & 8) != 0) { // Up
                     renderBox(buf, pose, rx, ryOff + offset, rz, size, thick, size, (r * lBri) / 255.0f, (g * lBri) / 255.0f, (b * lBri) / 255.0f, alpha);
                 }
                 if ((exposedFaces & 16) != 0) { // North
                     renderBox(buf, pose, rx, ryOff, rz - offset, size, size, thick, (r * lBri) / 255.0f, (g * lBri) / 255.0f, (b * lBri) / 255.0f, alpha);
                 }
                 if ((exposedFaces & 32) != 0) { // South
                     renderBox(buf, pose, rx, ryOff, rz + offset, size, size, thick, (r * lBri) / 255.0f, (g * lBri) / 255.0f, (b * lBri) / 255.0f, alpha);
                 }

             } else if (renderType == 16) { // RENDER_VINE
                 // Same as Lichen but standard brightness and green color usually
                 float thick = 0.05f;
                 float size = 1.0f;
                 float offset = 0.5f - (thick / 2.0f);

                 if ((exposedFaces & 1) != 0) renderBox(buf, pose, rx - offset, ryOff, rz, thick, size, size, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                 if ((exposedFaces & 2) != 0) renderBox(buf, pose, rx + offset, ryOff, rz, thick, size, size, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                 if ((exposedFaces & 4) != 0) renderBox(buf, pose, rx, ryOff - offset, rz, size, thick, size, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                 if ((exposedFaces & 8) != 0) renderBox(buf, pose, rx, ryOff + offset, rz, size, thick, size, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                 if ((exposedFaces & 16) != 0) renderBox(buf, pose, rx, ryOff, rz - offset, size, size, thick, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
                if ((exposedFaces & 32) != 0) renderBox(buf, pose, rx, ryOff, rz + offset, size, size, thick, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);

            } else if (renderType == 17) { // RENDER_FIRE
                // Fire: 3 distinct flame pillars simulating 3D fire
                float lBri = 1.0f; // Always bright
                float flameW = 0.15f;
                
                // Calculate colors normalized
                float fR = (r * lBri) / 255.0f;
                float fG = (g * lBri) / 255.0f;
                float fB = (b * lBri) / 255.0f;

                // Center flame (Tallest)
                // Height 0.8, Y-center = Bottom(-0.5) + 0.4 = -0.1
                renderBox(buf, pose, rx, ryOff - 0.1, rz, flameW, 0.8f, flameW, fR, fG, fB, alpha);
                
                // Side flame 1 (Left-ish)
                // Height 0.6, Y-center = Bottom(-0.5) + 0.3 = -0.2
                renderBox(buf, pose, rx - 0.2, ryOff - 0.2, rz + 0.1, flameW, 0.6f, flameW, fR, fG, fB, alpha);
                
                // Side flame 2 (Right-ish)
                // Height 0.5, Y-center = Bottom(-0.5) + 0.25 = -0.25
                renderBox(buf, pose, rx + 0.15, ryOff - 0.25, rz - 0.15, flameW, 0.5f, flameW, fR, fG, fB, alpha);

            } else if (renderType == 18) { // RENDER_REDSTONE_LAMP
                // Framed Lamp
                
                // Inner Color: Passed in r, g, b
                float innerR = (r * brightness) / 255.0f;
                float innerG = (g * brightness) / 255.0f;
                float innerB = (b * brightness) / 255.0f;
                
                // If Lit (Bright Yellow/Light Yellow), force full brightness
                // Lit: r>200, g>200. Unlit: r~74.
                boolean isLit = (r > 200 && g > 200);
                if (isLit) {
                    innerR = r / 255.0f;
                    innerG = g / 255.0f;
                    innerB = b / 255.0f;
                } else {
                    // Unlit: Darker center to show border
                    innerR = 0.15f * brightness;
                    innerG = 0.08f * brightness;
                    innerB = 0.08f * brightness;
                }
                
                // Render Frame (Base Block)
                // Color: Dark Brown (0x4A2B2B -> 0.29, 0.17, 0.17)
                float frameR = 0.29f * brightness;
                float frameG = 0.17f * brightness;
                float frameB = 0.17f * brightness;
                
                renderBox(buf, pose, rx, ry, rz, 1.0f, 1.0f, 1.0f, frameR, frameG, frameB, alpha);
                
                // Render Panels (Lit or Unlit)
                // Render 3 intersecting boxes to create "panels" on the faces with a border
                // Border width = (1.0 - 0.7) / 2 = 0.15 (approx 2.4 pixels)
                float panelW = 0.75f; // Slightly larger panels
                float thickness = 1.002f; // Slightly sticking out
                
                // Box 1: Sticks out X faces
                renderBox(buf, pose, rx, ry, rz, thickness, panelW, panelW, innerR, innerG, innerB, alpha);
                // Box 2: Sticks out Y faces
                renderBox(buf, pose, rx, ry, rz, panelW, thickness, panelW, innerR, innerG, innerB, alpha);
                // Box 3: Sticks out Z faces
                renderBox(buf, pose, rx, ry, rz, panelW, panelW, thickness, innerR, innerG, innerB, alpha);
                
            } else if (renderType == 19) { // RENDER_DOOR
                // Door Rendering
                // Decode exposedFaces for door data
                // Bit 0-1: Facing (0:S, 1:W, 2:N, 3:E)
                // Bit 2: Open
                // Bit 3: Hinge
                // Bit 4: Half
                
                int facing = exposedFaces & 3;
                boolean isOpen = (exposedFaces & 4) != 0;
                
                // Color from block map color
                float dR = (r * brightness) / 255.0f;
                float dG = (g * brightness) / 255.0f;
                float dB = (b * brightness) / 255.0f;
                
                // Thickness
                float th = 0.1875f; // 3 pixels
                
                // Determine orientation
                boolean isZAligned = false; // Default X-aligned
                
                // Logic:
                // South(0) -> Closed: X-aligned (blocking Z)
                // West(1)  -> Closed: Z-aligned (blocking X)
                // North(2) -> Closed: X-aligned
                // East(3)  -> Closed: Z-aligned
                
                if (facing == 1 || facing == 3) {
                    isZAligned = true;
                }
                
                // If Open, rotate 90 degrees
                if (isOpen) {
                    isZAligned = !isZAligned;
                }
                
                if (isZAligned) {
                    // Render along Z axis
                    renderBox(buf, pose, rx, ryOff, rz, th, 1.0f, 1.0f, dR, dG, dB, alpha);
                } else {
                    // Render along X axis
                    renderBox(buf, pose, rx, ryOff, rz, 1.0f, 1.0f, th, dR, dG, dB, alpha);
                }

            } else if (renderType == 20) { // RENDER_BUTTON
                // Decode Face/Facing/Powered
                // Bits 0-1: Face (0:Floor, 1:Wall, 2:Ceiling)
                // Bits 2-3: Facing (0:S, 1:W, 2:N, 3:E)
                // Bit 4: Powered
                
                int face = exposedFaces & 3;
                int facing = (exposedFaces >> 2) & 3;
                boolean powered = (exposedFaces & 16) != 0;
                
                // Geometry
                // Unpressed: 6x2x4 (WxHxD) -> 0.375 x 0.125 x 0.25 (approx)
                // Pressed: Height 0.0625
                
                float bW = 0.375f;
                float bH = powered ? 0.0625f : 0.125f;
                float bD = 0.25f; // Depth (along facing axis usually)
                
                // Default color from block (passed in r,g,b)
                float bR = (r * brightness) / 255.0f;
                float bG = (g * brightness) / 255.0f;
                float bB = (b * brightness) / 255.0f;
                
                // Position logic
                double bx = rx;
                double by = ry;
                double bz = rz;
                
                // Button usually 4x6x8 or similar? 
                // Let's assume standard button size.
                // 6 wide, 4 high, 8 deep? No, 6 wide, 2 thick, 4 deep.
                
                if (face == 0) { // Floor
                    // On bottom face, Y shifted up slightly
                    by = ryOff;
                    // Facing controls rotation (Width vs Depth alignment)
                    // If Facing=North/South, Width is along X.
                    // If Facing=West/East, Width is along Z.
                    if (facing == 1 || facing == 3) { // West/East
                         renderBox(buf, pose, bx, by, bz, bD, bH, bW, bR, bG, bB, alpha);
                    } else {
                         renderBox(buf, pose, bx, by, bz, bW, bH, bD, bR, bG, bB, alpha);
                    }
                } else if (face == 2) { // Ceiling
                    // On top face, Y shifted down
                    by = ryOff + 1.0 - bH;
                    if (facing == 1 || facing == 3) { // West/East
                         renderBox(buf, pose, bx, by, bz, bD, bH, bW, bR, bG, bB, alpha);
                    } else {
                         renderBox(buf, pose, bx, by, bz, bW, bH, bD, bR, bG, bB, alpha);
                    }
                } else { // Wall
                    // Attached to side face.
                    // Facing is Direction button points OUT.
                    by = ryOff + 0.5 - (bD / 2.0); // Center on wall
                    // North(2) -> Points North (Z-). Attached to South Face (Z=Max).
                    // Wait, standard Facing property logic:
                    // Facing=NORTH means "Button's front is North". Back is South. Attached to block at South?
                    // No, button occupies block pos X. Attached to Neighbor X+1 (if Facing East? No).
                    // If Facing=NORTH, attached to block at South (Z+1).
                    // Position within THIS block: On South Face (Z=1)? No, Facing=NORTH means attached to SOUTH face of the block space.
                    // So it's at Z ~ 0.5.
                    // Let's verify: Button on North Wall of room. Room is South of wall. Button faces South.
                    // Button Facing = SOUTH. Attached to North face of block?
                    // Let's assume Facing = Direction of protrusion.
                    
                    float offset = 0.5f - (bH / 2.0f);
                    
                    if (facing == 2) { // North (Z-)
                        bz = rz + offset; // Attached to South face (Z+) ?? No.
                        // If facing North, it is on the South side of the block?
                        // Or is it on the North side?
                        // "Facing" usually means the normal of the face it's on? No.
                        // Face=WALL, Facing=NORTH. Attached to the block to the SOUTH.
                        // So inside THIS block, it's at Z=1.0 (South edge).
                        // Let's try Z offset +0.4.
                        renderBox(buf, pose, bx, by, rz + offset, bW, bD, bH, bR, bG, bB, alpha); // WxHxD -> W x H x Thickness
                    } else if (facing == 0) { // South (Z+)
                        renderBox(buf, pose, bx, by, rz - offset, bW, bD, bH, bR, bG, bB, alpha);
                    } else if (facing == 1) { // West (X-)
                        renderBox(buf, pose, rx + offset, by, bz, bH, bD, bW, bR, bG, bB, alpha);
                    } else if (facing == 3) { // East (X+)
                        renderBox(buf, pose, rx - offset, by, bz, bH, bD, bW, bR, bG, bB, alpha);
                    }
                }

            } else if (renderType == 21) { // RENDER_LEVER
                // Similar to Button but with Base and Handle
                // Face/Facing/Powered
                
                int face = exposedFaces & 3;
                int facing = (exposedFaces >> 2) & 3;
                boolean powered = (exposedFaces & 16) != 0;
                
                // Colors
                float cobbleR = 0.5f * brightness;
                float cobbleG = 0.5f * brightness;
                float cobbleB = 0.5f * brightness;
                
                float stickR = 0.4f * brightness;
                float stickG = 0.3f * brightness;
                float stickB = 0.2f * brightness;
                
                // Base: 6x8x2?
                // Handle: Stick
                
                // Simplified: Render base flat box + stick sticking out
                float baseW = 0.375f;
                float baseTh = 0.15f; // Base thickness
                float baseL = 0.5f;
                
                // For Wall: Base is vertical.
                // For Floor/Ceiling: Base is horizontal.
                
                double lx = rx;
                double ly = ry;
                double lz = rz;
                
                // Stick properties
                float sW = 0.1f;
                float sL = 0.4f; // Stick length
                
                // State offset for stick (0.2 is roughly 45 degrees projection or shift)
                // Wall: Up/Down. Floor/Ceiling: Forward/Back.
                float stateOffset = powered ? -0.2f : 0.2f; 
                
                if (face == 0) { // Floor
                    ly = ryOff;
                    // Base
                    if (facing == 1 || facing == 3) {
                        renderBox(buf, pose, lx, ly, lz, baseL, baseTh, baseW, cobbleR, cobbleG, cobbleB, alpha);
                        // Stick moves along X
                        // If facing West(1), "On" might be West? 
                        // Let's just shift based on state.
                        // Facing West(1) is X-. Facing East(3) is X+.
                        float dir = (facing == 1) ? 1.0f : -1.0f; 
                        renderBox(buf, pose, lx + (stateOffset * dir), ly + 0.2, lz, sW, sL, sW, stickR, stickG, stickB, alpha);
                    } else {
                        renderBox(buf, pose, lx, ly, lz, baseW, baseTh, baseL, cobbleR, cobbleG, cobbleB, alpha);
                        // Stick moves along Z
                        // Facing South(0) is Z+. Facing North(2) is Z-.
                        float dir = (facing == 2) ? 1.0f : -1.0f;
                        renderBox(buf, pose, lx, ly + 0.2, lz + (stateOffset * dir), sW, sL, sW, stickR, stickG, stickB, alpha);
                    }
                    
                } else if (face == 2) { // Ceiling
                    ly = ryOff + 1.0 - baseTh;
                    if (facing == 1 || facing == 3) {
                        renderBox(buf, pose, lx, ly, lz, baseL, baseTh, baseW, cobbleR, cobbleG, cobbleB, alpha);
                        float dir = (facing == 1) ? 1.0f : -1.0f;
                        renderBox(buf, pose, lx + (stateOffset * dir), ly - 0.2, lz, sW, sL, sW, stickR, stickG, stickB, alpha);
                    } else {
                        renderBox(buf, pose, lx, ly, lz, baseW, baseTh, baseL, cobbleR, cobbleG, cobbleB, alpha);
                        float dir = (facing == 2) ? 1.0f : -1.0f;
                        renderBox(buf, pose, lx, ly - 0.2, lz + (stateOffset * dir), sW, sL, sW, stickR, stickG, stickB, alpha);
                    }
                    
                } else { // Wall
                    ly = ryOff + 0.5 - (baseTh / 2.0); // Center on wall
                    float offset = 0.5f - (baseTh / 2.0f);
                    
                    // Wall levers toggle Up/Down usually.
                    // Powered = Down (-Y). Unpowered = Up (+Y).
                    // My stateOffset is: Powered(-0.2), Unpowered(0.2).
                    // So just add stateOffset to Y.
                    
                    if (facing == 2) { // North (Attached South)
                        renderBox(buf, pose, lx, ly, rz + offset, baseW, baseL, baseTh, cobbleR, cobbleG, cobbleB, alpha);
                        renderBox(buf, pose, lx, ly + stateOffset, rz + offset - 0.2, sW, sL, sW, stickR, stickG, stickB, alpha); 
                    } else if (facing == 0) { // South
                        renderBox(buf, pose, lx, ly, rz - offset, baseW, baseL, baseTh, cobbleR, cobbleG, cobbleB, alpha);
                        renderBox(buf, pose, lx, ly + stateOffset, rz - offset + 0.2, sW, sL, sW, stickR, stickG, stickB, alpha);
                    } else if (facing == 1) { // West
                        renderBox(buf, pose, rx + offset, ly, lz, baseTh, baseL, baseW, cobbleR, cobbleG, cobbleB, alpha);
                        renderBox(buf, pose, rx + offset - 0.2, ly + stateOffset, lz, sW, sL, sW, stickR, stickG, stickB, alpha);
                    } else if (facing == 3) { // East
                        renderBox(buf, pose, rx - offset, ly, lz, baseTh, baseL, baseW, cobbleR, cobbleG, cobbleB, alpha);
                        renderBox(buf, pose, rx - offset + 0.2, ly + stateOffset, lz, sW, sL, sW, stickR, stickG, stickB, alpha);
                    }
                }

            } else if (renderType == 22) { // RENDER_REDSTONE_WIRE
                // Connections: N(1), S(2), E(4), W(8)
                boolean cN = (exposedFaces & 1) != 0;
                boolean cS = (exposedFaces & 2) != 0;
                boolean cE = (exposedFaces & 4) != 0;
                boolean cW = (exposedFaces & 8) != 0;
                
                // Determine Power State based on Color (Dark Red vs Bright Red)
                // Unpowered: ~85 (0x55), Powered: ~255 (0xFF)
                boolean isPowered = r > 150;
                
                // Lighting: If powered, use full brightness (Glow).
                float useBrightness = isPowered ? 1.0f : brightness;
                
                // Color: Red (passed in)
                float rR = (r * useBrightness) / 255.0f;
                float rG = (g * useBrightness) / 255.0f;
                float rB = (b * useBrightness) / 255.0f;
                
                // Height: Flat dust
                float wireTh = 0.0625f; // 1 pixel thick (Dust)
                float wireW = 0.25f;    // 4 pixels wide (Standard Dust width)
                // Correctly place wire on floor (ryOff)
                // ry is Bottom Y. ryOff is Bottom Y + 0.02.
                // renderBox takes minY. So we pass ryOff.
                double wireY = ryOff; 

                // Center Dot/Square
                renderBox(buf, pose, rx, wireY, rz, wireW, wireTh, wireW, rR, rG, rB, alpha);
                
                boolean isSingle = !cN && !cS && !cE && !cW;
                
                // Arms
                // Center box is +/- wireW/2 = 0.125.
                // Distance to edge is 0.5 - 0.125 = 0.375.
                float fullArmL = 0.375f;
                float shortArmL = 0.1875f; // Half-way to edge (3 pixels)
                
                float armOffset = 0.125f + (fullArmL / 2.0f); // Center of full arm
                float shortArmOffset = 0.125f + (shortArmL / 2.0f); // Center of short arm
                
                if (isSingle) {
                    // Render Cross (4 short arms)
                    renderBox(buf, pose, rx, wireY, rz - shortArmOffset, wireW, wireTh, shortArmL, rR, rG, rB, alpha);
                    renderBox(buf, pose, rx, wireY, rz + shortArmOffset, wireW, wireTh, shortArmL, rR, rG, rB, alpha);
                    renderBox(buf, pose, rx + shortArmOffset, wireY, rz, shortArmL, wireTh, wireW, rR, rG, rB, alpha);
                    renderBox(buf, pose, rx - shortArmOffset, wireY, rz, shortArmL, wireTh, wireW, rR, rG, rB, alpha);
                } else {
                    if (cN) renderBox(buf, pose, rx, wireY, rz - armOffset, wireW, wireTh, fullArmL, rR, rG, rB, alpha);
                    if (cS) renderBox(buf, pose, rx, wireY, rz + armOffset, wireW, wireTh, fullArmL, rR, rG, rB, alpha);
                    if (cE) renderBox(buf, pose, rx + armOffset, wireY, rz, fullArmL, wireTh, wireW, rR, rG, rB, alpha);
                    if (cW) renderBox(buf, pose, rx - armOffset, wireY, rz, fullArmL, wireTh, wireW, rR, rG, rB, alpha);
                }

            } else if (renderType == 23) { // RENDER_IRON_BARS
                // Connections: N(1), S(2), E(4), W(8)
                boolean cN = (exposedFaces & 1) != 0;
                boolean cS = (exposedFaces & 2) != 0;
                boolean cE = (exposedFaces & 4) != 0;
                boolean cW = (exposedFaces & 8) != 0;
                
                // Color: Usually Iron Grey (passed in or default)
                float iR = (r * brightness) / 255.0f;
                float iG = (g * brightness) / 255.0f;
                float iB = (b * brightness) / 255.0f;
                
                // Dimensions for "3 bars" look
                float th = 0.0625f;       // 1 pixel thickness
                float spread = 0.125f;    // 2 pixels spacing from center
                
                // Determine orientation
                // NS: Connected N/S but not E/W (Straight wall N-S)
                // EW: Connected E/W but not N/S (Straight wall E-W)
                // Cross: Everything else (Corners, Intersections) - EXCLUDING Isolated
                
                boolean isIsolated = !cN && !cS && !cE && !cW;
                boolean isNS = (cN || cS) && !(cE || cW);
                boolean isEW = (cE || cW) && !(cN || cS);
                
                // Only spread if NOT isolated.
                // If isolated, we want ONLY the center post.
                // Logic: Spread if (NS OR Cross/Corner) AND Not Isolated.
                // Cross/Corner is (!isNS && !isEW).
                
                boolean hasXSpread = !isIsolated && (isNS || (!isNS && !isEW)); // Spread along X
                boolean hasZSpread = !isIsolated && (isEW || (!isNS && !isEW)); // Spread along Z
                
                // 1. Vertical Bars
                // Center Bar (Always)
                renderBox(buf, pose, rx, ryOff, rz, th, 1.0f, th, iR, iG, iB, alpha);
                
                // Side Bars
                if (hasXSpread) {
                    renderBox(buf, pose, rx - spread, ryOff, rz, th, 1.0f, th, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx + spread, ryOff, rz, th, 1.0f, th, iR, iG, iB, alpha);
                }
                if (hasZSpread) {
                    renderBox(buf, pose, rx, ryOff, rz - spread, th, 1.0f, th, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx, ryOff, rz + spread, th, 1.0f, th, iR, iG, iB, alpha);
                }
                
                // 2. Horizontal Rails (Top, Middle, Bottom)
                // To connect the bars and the neighbors
                float railY1 = (float) (ryOff + 0.85); // Top rail
                float railY2 = (float) (ryOff + 0.15); // Bottom rail
                float railY3 = (float) (ryOff + 0.5);  // Middle rail
                float railH = th;
                
                // Hub Rails (Connecting internal bars)
                // Only render Hub Rails if we have some spread (not isolated)
                if (hasXSpread) {
                    float w = spread * 2.0f + th;
                    renderBox(buf, pose, rx, railY1, rz, w, railH, th, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx, railY2, rz, w, railH, th, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx, railY3, rz, w, railH, th, iR, iG, iB, alpha);
                }
                if (hasZSpread) {
                    float d = spread * 2.0f + th;
                    renderBox(buf, pose, rx, railY1, rz, th, railH, d, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx, railY2, rz, th, railH, d, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx, railY3, rz, th, railH, d, iR, iG, iB, alpha);
                }
                
                // Arm Rails (Connecting to neighbors)
                // Only render arms if connected in that direction
                
                // Start from the edge of the hub area to the block edge
                float edge = spread + th / 2.0f; 
                float armL = 0.5f - edge; 
                float armOff = edge + armL / 2.0f;
                
                // Arms match the spread width (if we have X spread, N/S arms are wide)
                float wideDim = spread * 2.0f + th;
                float thinDim = th;
                float armW = hasXSpread ? wideDim : thinDim;
                float armD = hasZSpread ? wideDim : thinDim;
                
                if (cN) {
                    renderBox(buf, pose, rx, railY1, rz - armOff, armW, railH, armL, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx, railY2, rz - armOff, armW, railH, armL, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx, railY3, rz - armOff, armW, railH, armL, iR, iG, iB, alpha);
                }
                if (cS) {
                    renderBox(buf, pose, rx, railY1, rz + armOff, armW, railH, armL, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx, railY2, rz + armOff, armW, railH, armL, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx, railY3, rz + armOff, armW, railH, armL, iR, iG, iB, alpha);
                }
                if (cE) {
                    renderBox(buf, pose, rx + armOff, railY1, rz, armL, railH, armD, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx + armOff, railY2, rz, armL, railH, armD, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx + armOff, railY3, rz, armL, railH, armD, iR, iG, iB, alpha);
                }
                if (cW) {
                    renderBox(buf, pose, rx - armOff, railY1, rz, armL, railH, armD, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx - armOff, railY2, rz, armL, railH, armD, iR, iG, iB, alpha);
                    renderBox(buf, pose, rx - armOff, railY3, rz, armL, railH, armD, iR, iG, iB, alpha);
                }
                
            } else if (renderType == 24) { // RENDER_FENCE
                // Connections: N(1), S(2), E(4), W(8)
                boolean cN = (exposedFaces & 1) != 0;
                boolean cS = (exposedFaces & 2) != 0;
                boolean cE = (exposedFaces & 4) != 0;
                boolean cW = (exposedFaces & 8) != 0;
                
                // Color: Wood (passed in)
                float fR = (r * brightness) / 255.0f;
                float fG = (g * brightness) / 255.0f;
                float fB = (b * brightness) / 255.0f;
                
                // Post: 4x4 pixels -> 0.25f
                float postTh = 0.25f;
                
                renderBox(buf, pose, rx, ryOff, rz, postTh, 1.0f, postTh, fR, fG, fB, alpha);
                
                // Rails
                float railTh = 0.125f;
                float railH = 0.1875f; // 3 pixels high
                
                double rY1 = ryOff + 0.75; // Upper rail center
                double rY2 = ryOff + 0.375; // Lower rail center
                
                // Arm Length: Center(0) to Edge(0.5) - PostHalf(0.125) = 0.375
                float armL = 0.375f;
                float armOffset = 0.5f - (armL / 2.0f);
                
                if (cN) {
                     renderBox(buf, pose, rx, rY1, rz - armOffset, railTh, railH, armL, fR, fG, fB, alpha);
                     renderBox(buf, pose, rx, rY2, rz - armOffset, railTh, railH, armL, fR, fG, fB, alpha);
                }
                if (cS) {
                     renderBox(buf, pose, rx, rY1, rz + armOffset, railTh, railH, armL, fR, fG, fB, alpha);
                     renderBox(buf, pose, rx, rY2, rz + armOffset, railTh, railH, armL, fR, fG, fB, alpha);
                }
                if (cE) {
                     renderBox(buf, pose, rx + armOffset, rY1, rz, armL, railH, railTh, fR, fG, fB, alpha);
                     renderBox(buf, pose, rx + armOffset, rY2, rz, armL, railH, railTh, fR, fG, fB, alpha);
                }
                if (cW) {
                     renderBox(buf, pose, rx - armOffset, rY1, rz, armL, railH, railTh, fR, fG, fB, alpha);
                     renderBox(buf, pose, rx - armOffset, rY2, rz, armL, railH, railTh, fR, fG, fB, alpha);
                }

            } else if (renderType == 25) { // RENDER_STAIRS
                // Unpack
                int facing = exposedFaces & 3;
                boolean isTop = (exposedFaces & 4) != 0;
                int shape = (exposedFaces >> 3) & 7;
                
                float sR = (r * brightness) / 255.0f;
                float sG = (g * brightness) / 255.0f;
                float sB = (b * brightness) / 255.0f;
                
                // Base Slab (Always present)
                double baseY = isTop ? ryOff + 0.5 : ryOff;
                renderBoxWithOutlines(buf, pose, rx, baseY, rz, 1.0f, 0.5f, 1.0f, sR, sG, sB, alpha);
                
                // Step Layer
                double stepY = isTop ? ryOff : ryOff + 0.5;
                
                // Determine which quarters to render
                // 0:NW, 1:NE, 2:SW, 3:SE
                boolean[] q = new boolean[4];
                
                if (shape == 0) { // STRAIGHT
                    if (facing == 2) { q[0]=true; q[1]=true; } // North -> High North (NW, NE)
                    else if (facing == 0) { q[2]=true; q[3]=true; } // South -> High South (SW, SE)
                    else if (facing == 3) { q[1]=true; q[3]=true; } // East -> High East (NE, SE)
                    else if (facing == 1) { q[0]=true; q[2]=true; } // West -> High West (NW, SW)
                } 
                else if (shape == 1) { // INNER_LEFT
                     if (facing == 2) { q[0]=true; q[1]=true; q[2]=true; } // North + West -> Missing SE
                     else if (facing == 0) { q[1]=true; q[2]=true; q[3]=true; } // South + East -> Missing NW
                     else if (facing == 3) { q[0]=true; q[1]=true; q[3]=true; } // East + North -> Missing SW
                     else if (facing == 1) { q[0]=true; q[2]=true; q[3]=true; } // West + South -> Missing NE
                }
                else if (shape == 2) { // INNER_RIGHT
                     if (facing == 2) { q[0]=true; q[1]=true; q[3]=true; } // North + East -> Missing SW
                     else if (facing == 0) { q[0]=true; q[2]=true; q[3]=true; } // South + West -> Missing NE
                     else if (facing == 3) { q[1]=true; q[2]=true; q[3]=true; } // East + South -> Missing NW
                     else if (facing == 1) { q[0]=true; q[1]=true; q[2]=true; } // West + North -> Missing SE
                }
                else if (shape == 3) { // OUTER_LEFT
                     if (facing == 2) { q[0]=true; } // North + West -> NW
                     else if (facing == 0) { q[3]=true; } // South + East -> SE
                     else if (facing == 3) { q[1]=true; } // East + North -> NE
                     else if (facing == 1) { q[2]=true; } // West + South -> SW
                }
                else if (shape == 4) { // OUTER_RIGHT
                     if (facing == 2) { q[1]=true; } // North + East -> NE
                     else if (facing == 0) { q[2]=true; } // South + West -> SW
                     else if (facing == 3) { q[3]=true; } // East + South -> SE
                     else if (facing == 1) { q[0]=true; } // West + North -> NW
                }

                // Render Quarters with Merging to avoid internal borders
                boolean[] rendered = new boolean[4];
                
                // 1. Try Horizontal Merges (North/South)
                // North (NW + NE) -> q[0] & q[1]
                if (q[0] && q[1]) {
                    renderBoxWithOutlines(buf, pose, rx, stepY, rz - 0.25, 1.0f, 0.5f, 0.5f, sR, sG, sB, alpha);
                    rendered[0] = true;
                    rendered[1] = true;
                }
                
                // South (SW + SE) -> q[2] & q[3]
                if (q[2] && q[3]) {
                    renderBoxWithOutlines(buf, pose, rx, stepY, rz + 0.25, 1.0f, 0.5f, 0.5f, sR, sG, sB, alpha);
                    rendered[2] = true;
                    rendered[3] = true;
                }
                
                // 2. Try Vertical Merges (West/East) on remaining
                // West (NW + SW) -> q[0] & q[2]
                if (!rendered[0] && !rendered[2] && q[0] && q[2]) {
                    renderBoxWithOutlines(buf, pose, rx - 0.25, stepY, rz, 0.5f, 0.5f, 1.0f, sR, sG, sB, alpha);
                    rendered[0] = true;
                    rendered[2] = true;
                }
                
                // East (NE + SE) -> q[1] & q[3]
                if (!rendered[1] && !rendered[3] && q[1] && q[3]) {
                    renderBoxWithOutlines(buf, pose, rx + 0.25, stepY, rz, 0.5f, 0.5f, 1.0f, sR, sG, sB, alpha);
                    rendered[1] = true;
                    rendered[3] = true;
                }
                
                // 3. Render Remaining Single Quarters
                float qSize = 0.5f;
                if (q[0] && !rendered[0]) renderBoxWithOutlines(buf, pose, rx - 0.25, stepY, rz - 0.25, qSize, 0.5f, qSize, sR, sG, sB, alpha);
                if (q[1] && !rendered[1]) renderBoxWithOutlines(buf, pose, rx + 0.25, stepY, rz - 0.25, qSize, 0.5f, qSize, sR, sG, sB, alpha);
                if (q[2] && !rendered[2]) renderBoxWithOutlines(buf, pose, rx - 0.25, stepY, rz + 0.25, qSize, 0.5f, qSize, sR, sG, sB, alpha);
                if (q[3] && !rendered[3]) renderBoxWithOutlines(buf, pose, rx + 0.25, stepY, rz + 0.25, qSize, 0.5f, qSize, sR, sG, sB, alpha);

            } else if (renderType == 26) { // RENDER_SLAB
                 int type = exposedFaces & 3;
                 
                 float sR = (r * brightness) / 255.0f;
                 float sG = (g * brightness) / 255.0f;
                 float sB = (b * brightness) / 255.0f;
                 
                 if (type == 2) { // Double
                     renderBoxWithOutlines(buf, pose, rx, ryOff, rz, 1.0f, 1.0f, 1.0f, sR, sG, sB, alpha);
                 } else if (type == 1) { // Top
                     renderBoxWithOutlines(buf, pose, rx, ryOff + 0.5, rz, 1.0f, 0.5f, 1.0f, sR, sG, sB, alpha);
                 } else { // Bottom
                     renderBoxWithOutlines(buf, pose, rx, ryOff, rz, 1.0f, 0.5f, 1.0f, sR, sG, sB, alpha);
                 }
            } else if (renderType == 27) { // RENDER_TRAPDOOR
                 // Unpack
                 int facing = exposedFaces & 3;
                 boolean isTop = (exposedFaces & 4) != 0;
                 boolean isOpen = (exposedFaces & 8) != 0;
                 
                 float tR = (r * brightness) / 255.0f;
                 float tG = (g * brightness) / 255.0f;
                 float tB = (b * brightness) / 255.0f;
                 
                 float th = 0.1875f; // 3 pixels thickness
                 
                 if (isOpen) {
                     // Attached to the face specified by Facing
                     // Facing: S(0), W(1), N(2), E(3)
                     
                     if (facing == 2) { // North (Attached to Z-)
                         // Position: Z = rz - 0.5 + th/2
                         renderBox(buf, pose, rx, ryOff, rz - 0.5 + (th/2.0), 1.0f, 1.0f, th, tR, tG, tB, alpha);
                     } else if (facing == 0) { // South (Attached to Z+)
                         renderBox(buf, pose, rx, ryOff, rz + 0.5 - (th/2.0), 1.0f, 1.0f, th, tR, tG, tB, alpha);
                     } else if (facing == 1) { // West (Attached to X-)
                         renderBox(buf, pose, rx - 0.5 + (th/2.0), ryOff, rz, th, 1.0f, 1.0f, tR, tG, tB, alpha);
                     } else if (facing == 3) { // East (Attached to X+)
                         renderBox(buf, pose, rx + 0.5 - (th/2.0), ryOff, rz, th, 1.0f, 1.0f, tR, tG, tB, alpha);
                     }
                 } else {
                     // Closed (Horizontal)
                     // If Top: Top of block. If Bottom: Bottom of block.
                     
                     if (isTop) {
                         // Top part of block space
                         // Center Y = ry + 0.5 - th/2
                         renderBox(buf, pose, rx, ryOff + 1.0 - th, rz, 1.0f, th, 1.0f, tR, tG, tB, alpha);
                     } else {
                        // Bottom part
                        // Center Y = ry - 0.5 + th/2
                        renderBox(buf, pose, rx, ryOff, rz, 1.0f, th, 1.0f, tR, tG, tB, alpha);
                    }
                }
            } else if (renderType == 28) { // RENDER_GLASS_PANE
                // Connections: N(1), S(2), E(4), W(8)
                boolean cN = (exposedFaces & 1) != 0;
                boolean cS = (exposedFaces & 2) != 0;
                boolean cE = (exposedFaces & 4) != 0;
                boolean cW = (exposedFaces & 8) != 0;
                
                // Fix Color: Glass panes might be dark in map data. Force a lighter tint if needed.
                // Or just assume they should be bright.
                // Boost brightness slightly for glass
                float boost = 1.2f;
                float gR = Math.min(1.0f, (r * brightness * boost) / 255.0f);
                float gG = Math.min(1.0f, (g * brightness * boost) / 255.0f);
                float gB = Math.min(1.0f, (b * brightness * boost) / 255.0f);
                float glassAlpha = 0.3f; // More transparent
                
                // Border Color: Opaque and slightly visible
                float bR = gR;
                float bG = gG;
                float bB = gB;
                float bAlpha = 0.9f;
                float bTh = 0.03f; // Border thickness
                
                // Glass Pane Model: Flat sheets with borders
                // Post: Thin vertical column
                float postTh = 0.1f; // Same as paneTh for seamless look
                
                // If no connections, it's just a vertical post
                if (!cN && !cS && !cE && !cW) {
                    // Main Glass
                    renderBox(buf, pose, rx, ryOff, rz, postTh, 1.0f, postTh, gR, gG, gB, glassAlpha);
                    // Borders (Vertical edges for isolated post)
                    renderBox(buf, pose, rx - postTh/2, ryOff, rz - postTh/2, bTh, 1.0f, bTh, bR, bG, bB, bAlpha);
                    renderBox(buf, pose, rx + postTh/2, ryOff, rz - postTh/2, bTh, 1.0f, bTh, bR, bG, bB, bAlpha);
                    renderBox(buf, pose, rx - postTh/2, ryOff, rz + postTh/2, bTh, 1.0f, bTh, bR, bG, bB, bAlpha);
                    renderBox(buf, pose, rx + postTh/2, ryOff, rz + postTh/2, bTh, 1.0f, bTh, bR, bG, bB, bAlpha);
                } else {
                    // Center Post
                    renderBox(buf, pose, rx, ryOff, rz, postTh, 1.0f, postTh, gR, gG, gB, glassAlpha);
                    // Post Top/Bottom Borders (to connect with arms)
                    renderBox(buf, pose, rx, ryOff + 1.0f - bTh, rz, postTh, bTh, postTh, bR, bG, bB, bAlpha);
                    renderBox(buf, pose, rx, ryOff, rz, postTh, bTh, postTh, bR, bG, bB, bAlpha);
                    
                    // Arms
                    float paneTh = 0.1f; // Very thin glass
                    float armL = 0.45f; // Reach to edge (0.5 - postTh/2) = 0.45
                    float armOffset = 0.5f - (armL / 2.0f);
                    
                    if (cN) {
                        // Glass
                        renderBox(buf, pose, rx, ryOff, rz - armOffset, paneTh, 1.0f, armL, gR, gG, gB, glassAlpha);
                        // Top Border
                        renderBox(buf, pose, rx, ryOff + 1.0f - bTh, rz - armOffset, bTh, bTh, armL, bR, bG, bB, bAlpha);
                        // Bottom Border
                        renderBox(buf, pose, rx, ryOff, rz - armOffset, bTh, bTh, armL, bR, bG, bB, bAlpha);
                    }
                    if (cS) {
                         renderBox(buf, pose, rx, ryOff, rz + armOffset, paneTh, 1.0f, armL, gR, gG, gB, glassAlpha);
                         renderBox(buf, pose, rx, ryOff + 1.0f - bTh, rz + armOffset, bTh, bTh, armL, bR, bG, bB, bAlpha);
                         renderBox(buf, pose, rx, ryOff, rz + armOffset, bTh, bTh, armL, bR, bG, bB, bAlpha);
                    }
                    if (cE) {
                        renderBox(buf, pose, rx + armOffset, ryOff, rz, armL, 1.0f, paneTh, gR, gG, gB, glassAlpha);
                        renderBox(buf, pose, rx + armOffset, ryOff + 1.0f - bTh, rz, armL, bTh, bTh, bR, bG, bB, bAlpha);
                        renderBox(buf, pose, rx + armOffset, ryOff, rz, armL, bTh, bTh, bR, bG, bB, bAlpha);
                    }
                    if (cW) {
                        renderBox(buf, pose, rx - armOffset, ryOff, rz, armL, 1.0f, paneTh, gR, gG, gB, glassAlpha);
                        renderBox(buf, pose, rx - armOffset, ryOff + 1.0f - bTh, rz, armL, bTh, bTh, bR, bG, bB, bAlpha);
                        renderBox(buf, pose, rx - armOffset, ryOff, rz, armL, bTh, bTh, bR, bG, bB, bAlpha);
                    }
                }

            } else if (renderType == 29) { // RENDER_GLASS_BLOCK
                float glassAlpha = 0.4f;
                renderBlockWithBorders(buf, pose, rx, ry, rz, 1.0f, 1.0f, 1.0f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, glassAlpha, exposedFaces);
            
            } else if (renderType == 30) { // RENDER_END_ROD
                 // End Rod: Base + Rod
                 // Facing: D(0), U(1), N(2), S(3), W(4), E(5)
                 int facing = exposedFaces & 7;
                 
                 // Color: Force bright white/cyan for "glow" effect
                 // We ignore brightness multiplier for the rod to make it look self-luminous
                 float eR = 1.0f; 
                 float eG = 1.0f;
                 float eB = 1.0f;
                 
                 // Dimensions
                 float rodTh = 0.125f; // 2 pixels
                 float rodL = 1.0f; // Length
                 float baseW = 0.25f; // 4 pixels
                 float baseH = 0.0625f; // 1 pixel
                 
                 // Base Color (Purple-ish)
                 float bR = 0.8f;
                 float bG = 0.6f;
                 float bB = 0.9f;
                 
                 // Center defaults
                 float cx = (float)rx;
                float cy = (float)ryOff;
                float cz = (float)rz;
                 
                 if (facing == 0) { // Down
                     // Rod
                     renderBox(buf, pose, cx, cy, cz, rodTh, rodL, rodTh, eR, eG, eB, alpha);
                     // Base (Top)
                     renderBox(buf, pose, cx, cy + 0.5 - baseH/2.0, cz, baseW, baseH, baseW, bR, bG, bB, alpha);
                 } else if (facing == 1) { // Up
                     // Rod
                     renderBox(buf, pose, cx, cy, cz, rodTh, rodL, rodTh, eR, eG, eB, alpha);
                     // Base (Bottom)
                     renderBox(buf, pose, cx, cy - 0.5 + baseH/2.0, cz, baseW, baseH, baseW, bR, bG, bB, alpha);
                 } else if (facing == 2) { // North (Z-)
                     // Rod along Z
                     renderBox(buf, pose, cx, cy, cz, rodTh, rodTh, rodL, eR, eG, eB, alpha);
                     // Base (South end -> Z+)
                     renderBox(buf, pose, cx, cy, cz + 0.5 - baseH/2.0, baseW, baseW, baseH, bR, bG, bB, alpha);
                 } else if (facing == 3) { // South (Z+)
                     // Rod along Z
                     renderBox(buf, pose, cx, cy, cz, rodTh, rodTh, rodL, eR, eG, eB, alpha);
                     // Base (North end -> Z-)
                     renderBox(buf, pose, cx, cy, cz - 0.5 + baseH/2.0, baseW, baseW, baseH, bR, bG, bB, alpha);
                 } else if (facing == 4) { // West (X-)
                     // Rod along X
                     renderBox(buf, pose, cx, cy, cz, rodL, rodTh, rodTh, eR, eG, eB, alpha);
                     // Base (East end -> X+)
                     renderBox(buf, pose, cx + 0.5 - baseH/2.0, cy, cz, baseH, baseW, baseW, bR, bG, bB, alpha);
                 } else if (facing == 5) { // East (X+)
                     // Rod along X
                     renderBox(buf, pose, cx, cy, cz, rodL, rodTh, rodTh, eR, eG, eB, alpha);
                     // Base (West end -> X-)
                     renderBox(buf, pose, cx - 0.5 + baseH/2.0, cy, cz, baseH, baseW, baseW, bR, bG, bB, alpha);
                 }

            } else if (renderType == 31) { // RENDER_BANNER
                 // Banner
                 boolean isWall = (exposedFaces & 16) != 0;
                 int data = exposedFaces & 15;
                 
                 // Banner Color (from block)
                 float bR = (r * brightness) / 255.0f;
                 float bG = (g * brightness) / 255.0f;
                 float bB = (b * brightness) / 255.0f;
                 
                 // Wood Color (Dark Oak-ish)
                 float wR = 0.4f * brightness;
                 float wG = 0.3f * brightness;
                 float wB = 0.1f * brightness;
                 
                 // Create a local copy of the matrix for rotation
                Matrix4f model = new Matrix4f(pose);
                model.translate((float)rx, (float)ryOff, (float)rz);
                 
                 if (isWall) {
                     // Wall Banner
                     // Facing: N(2), S(3), W(4), E(5)
                     // Logic: Model faces North (-Z). Offset to Z=+0.5 (Back to Wall).
                     float rot = 0;
                     if (data == 2) rot = 0; // North (Faces North, Wall at South)
                     else if (data == 3) rot = 180; // South (Faces South, Wall at North)
                     else if (data == 4) rot = 90; // West (Faces West, Wall at East)
                     else if (data == 5) rot = 270; // East (Faces East, Wall at West)
                     
                     model.rotate(Axis.YP.rotationDegrees(rot));
                     
                     // Wall Banner Model (Taller)
                     // Crossbar (Against wall at Z=0.5)
                     // Slightly forward from wall: Z=0.4
                     float zOff = 0.4f;
                     
                     // Crossbar
                     renderBox(buf, model, 0, 0.35, zOff, 0.8f, 0.1f, 0.1f, wR, wG, wB, alpha);
                     
                     // Sheet (Longer)
                     // Height 1.5 blocks visually?
                     // Start at 0.3, go down to -1.2?
                     renderBox(buf, model, 0, -0.4, zOff - 0.06, 0.7f, 1.4f, 0.05f, bR, bG, bB, alpha);
                     
                 } else {
                     // Standing Banner
                     // Rotation 0-15.
                     float angle = data * 22.5f;
                     
                     model.rotate(Axis.YP.rotationDegrees(-angle));
                     
                     // Standing Banner Model (Taller)
                     // Pole: Height 2.0 blocks (starts at -0.5? No, base is at y-0.5 relative to center?)
                     // Block center is 0.5. Base is at 0.
                     // So relative to center, base is at -0.5.
                     // Top is at +1.5.
                     
                     // Pole (Thin)
                     renderBox(buf, model, 0, 0.5, 0, 0.0625f, 2.0f, 0.0625f, wR, wG, wB, alpha);
                     
                     // Crossbar (Top)
                     // At Y = +1.4
                     renderBox(buf, model, 0, 1.4, 0, 0.8f, 0.0625f, 0.0625f, wR, wG, wB, alpha);
                     
                     // Sheet (Hanging)
                     // From 1.4 down. Length 1.6.
                     // Center Y = 1.4 - 0.8 = 0.6.
                     renderBox(buf, model, 0, 0.6, 0.04, 0.7f, 1.6f, 0.04f, bR, bG, bB, alpha);
                 }

            } else if (renderType >= 41 && renderType <= 56) { // RENDER_CARPET (White to Black)
                // Carpet: Thin slice at bottom
                // Thickness: 1/16 = 0.0625
                float cThick = 0.0625f;
                
                // Use passed color (r,g,b)
                // Position: ryOff (on floor)
                renderBoxWithOutlines(buf, pose, rx, ryOff, rz, 1.0f, cThick, 1.0f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);
            
            } else if (renderType == 57) { // RENDER_MOSS_CARPET
                 float cThick = 0.0625f;
                 // Moss Green
                 renderBoxWithOutlines(buf, pose, rx, ryOff, rz, 1.0f, cThick, 1.0f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha);

            } else if (renderType == 61) { // RENDER_RAIL
                int shapeOrdinal = exposedFaces & 15;
                boolean isRedstone = (exposedFaces & 16) != 0;
                boolean isPowered = (exposedFaces & 32) != 0;
                
                // Rail Color (Iron-ish)
                float rR = 0.7f * brightness;
                float rG = 0.7f * brightness;
                float rB = 0.7f * brightness;
                
                // Tie Color (Wood-ish)
                float tR = 0.4f * brightness;
                float tG = 0.3f * brightness;
                float tB = 0.1f * brightness;
                
                // Dimensions
                float railW = 0.125f; // 2 pixels
                float railH = 0.125f; // 2 pixels height
                float gauge = 0.6f; // Distance between rail centers
                float tieW = 0.125f; // 2 pixels
                float tieL = 0.8f; // Length of tie
                float tieH = 0.0625f; // 1 pixel
                
                // Local Matrix for rotation
                Matrix4f model = new Matrix4f(pose);
                model.translate((float)rx, (float)ryOff, (float)rz);
                
                // 0:N-S, 1:E-W, 2:ASC_E, 3:ASC_W, 4:ASC_N, 5:ASC_S
                // 6:SE, 7:SW, 8:NW, 9:NE
                
                if (shapeOrdinal == 0 || shapeOrdinal == 1) { // NORTH-SOUTH or EAST-WEST
                    // Use the logic of W-E (which rotates 90) but rotate it appropriately
                    // If N-S (0), rotate 0 (or 180). If E-W (1), rotate 90.
                    // The user requested to make N-S look like E-W but rotated, implying they should share the exact same drawing code.
                    float rot = (shapeOrdinal == 1) ? 90.0f : 0.0f;
                    model.rotate(Axis.YP.rotationDegrees(rot));

                    // Rails
                    renderBox(buf, model, -gauge/2, railH/2, 0, railW, railH, 1.0f, rR, rG, rB, alpha);
                    renderBox(buf, model, gauge/2, railH/2, 0, railW, railH, 1.0f, rR, rG, rB, alpha);
                    // Ties (3 per block)
                    renderBox(buf, model, 0, tieH/2, 0, tieL, tieH, tieW, tR, tG, tB, alpha);
                    renderBox(buf, model, 0, tieH/2, -0.35f, tieL, tieH, tieW, tR, tG, tB, alpha);
                    renderBox(buf, model, 0, tieH/2, 0.35f, tieL, tieH, tieW, tR, tG, tB, alpha);
                } else if (shapeOrdinal >= 2 && shapeOrdinal <= 5) { // ASCENDING
                    float slopeAngle = 45.0f;
                    // Rotate based on direction
                    if (shapeOrdinal == 2) { // ASC_EAST (X+)
                         model.rotate(Axis.YP.rotationDegrees(90));
                         model.rotate(Axis.XP.rotationDegrees(-slopeAngle));
                    } else if (shapeOrdinal == 3) { // ASC_WEST (X-)
                         model.rotate(Axis.YP.rotationDegrees(270));
                         model.rotate(Axis.XP.rotationDegrees(-slopeAngle));
                    } else if (shapeOrdinal == 4) { // ASC_NORTH (Z-)
                         model.rotate(Axis.YP.rotationDegrees(180));
                         model.rotate(Axis.XP.rotationDegrees(-slopeAngle));
                    } else if (shapeOrdinal == 5) { // ASC_SOUTH (Z+)
                         model.rotate(Axis.YP.rotationDegrees(0));
                         model.rotate(Axis.XP.rotationDegrees(-slopeAngle));
                    }
                    // Rails (Longer for slope? 1.0/cos(45) ~ 1.414)
                    float len = 1.414f;
                    renderBox(buf, model, -gauge/2, railH/2 + 0.5f, 0, railW, railH, len, rR, rG, rB, alpha);
                    renderBox(buf, model, gauge/2, railH/2 + 0.5f, 0, railW, railH, len, rR, rG, rB, alpha);
                    // Ties
                    renderBox(buf, model, 0, tieH/2 + 0.5f, 0, tieL, tieH, tieW, tR, tG, tB, alpha);
                    renderBox(buf, model, 0, tieH/2 + 0.5f, -0.35f, tieL, tieH, tieW, tR, tG, tB, alpha);
                    renderBox(buf, model, 0, tieH/2 + 0.5f, 0.35f, tieL, tieH, tieW, tR, tG, tB, alpha);
                } else if (shapeOrdinal >= 6 && shapeOrdinal <= 9) { // CURVES
                    // 6:SE, 7:SW, 8:NW, 9:NE
                    // Render as two straight segments meeting at corner
                    
                    float rot = 0;
                    if (shapeOrdinal == 6) rot = 0; // SE: South + East
                    else if (shapeOrdinal == 7) rot = 270; // SW: South + West (Fixed rotation)
                    else if (shapeOrdinal == 8) rot = 180; // NW: North + West
                    else if (shapeOrdinal == 9) rot = 90; // NE: North + East (Fixed rotation)
                    
                    model.rotate(Axis.YP.rotationDegrees(rot));
                    
                    // Segment 1: Center to South (+Z)
                    renderBox(buf, model, -gauge/2, railH/2, 0.25f, railW, railH, 0.5f, rR, rG, rB, alpha);
                    renderBox(buf, model, gauge/2, railH/2, 0.25f, railW, railH, 0.5f, rR, rG, rB, alpha);
                    // Tie 1
                    renderBox(buf, model, 0, tieH/2, 0.25f, tieL, tieH, tieW, tR, tG, tB, alpha);

                    // Segment 2: Center to East (+X)
                    Matrix4f arm2 = new Matrix4f(model);
                    arm2.rotate(Axis.YP.rotationDegrees(90));
                    
                    renderBox(buf, arm2, -gauge/2, railH/2, 0.25f, railW, railH, 0.5f, rR, rG, rB, alpha);
                    renderBox(buf, arm2, gauge/2, railH/2, 0.25f, railW, railH, 0.5f, rR, rG, rB, alpha);
                    renderBox(buf, arm2, 0, tieH/2, 0.25f, tieL, tieH, tieW, tR, tG, tB, alpha);
                    
                    // Center Tie (Diagonal)
                    Matrix4f center = new Matrix4f(model);
                    center.rotate(Axis.YP.rotationDegrees(45));
                    renderBox(buf, center, 0, tieH/2, 0, tieL, tieH, tieW, tR, tG, tB, alpha);
                }
                
                // Redstone Rail Indicator
                if (isRedstone) {
                    float rsR = isPowered ? 1.0f : 0.6f; // Bright red if powered, dark red if not
                    float rsG = 0.0f;
                    float rsB = 0.0f;
                    
                    float yPos = tieH + 0.02f;
                    // Adjust Y for ascending rails to be in the middle of the slope
                    if (shapeOrdinal >= 2 && shapeOrdinal <= 5) {
                        yPos += 0.5f;
                    }
                    
                    // Draw red box in center
                    renderBox(buf, model, 0, yPos, 0, 0.25f, 0.1f, 0.25f, rsR, rsG, rsB, alpha);
                }

            } else if (renderType == 62) { // RENDER_REPEATER
                // Unpack
                int facing = exposedFaces & 3; // 0=S, 1=W, 2=N, 3=E
                boolean powered = (exposedFaces & 4) != 0;
                int delay = (exposedFaces >> 3) & 3; // 0-3
                boolean locked = (exposedFaces & 32) != 0;
                
                // Base Slab (Stone Grey)
                float baseR = 0.6f * brightness;
                float baseG = 0.6f * brightness;
                float baseB = 0.6f * brightness;
                if (locked) { // Bedrock-ish
                    baseR = 0.3f * brightness;
                    baseG = 0.3f * brightness;
                    baseB = 0.3f * brightness;
                }
                
                renderBoxWithOutlines(buf, pose, rx, ryOff, rz, 1.0f, 0.125f, 1.0f, baseR, baseG, baseB, alpha);
                
                // Torches
                // Setup Matrix for rotation
                Matrix4f model = new Matrix4f(pose);
                model.translate((float)rx, (float)ryOff, (float)rz);
                
                float rot = 0;
                if (facing == 0) rot = 180;
                else if (facing == 1) rot = 270;
                else if (facing == 2) rot = 0;
                else if (facing == 3) rot = 90;
                
                model.rotate(Axis.YP.rotationDegrees(rot));
                
                // Torch Color
                float tR = powered ? 1.0f : 0.5f;
                float tG = 0.0f;
                float tB = 0.0f;
                
                // Fixed Torch (Input side, which is "Back", i.e. +Z in local space)
                renderBox(buf, model, 0, 0.125f + 0.2f, 0.3125f, 0.125f, 0.4f, 0.125f, tR, tG, tB, alpha);
                
                // Moving Torch (Output side, moves towards -Z)
                float moveZ = 0.125f - (delay * 0.12f); 
                renderBox(buf, model, 0, 0.125f + 0.2f, moveZ, 0.125f, 0.4f, 0.125f, tR, tG, tB, alpha);
                
            } else if (renderType == 63) { // RENDER_COMPARATOR
                int facing = exposedFaces & 3;
                boolean powered = (exposedFaces & 4) != 0;
                boolean subtract = (exposedFaces & 8) != 0;
                
                // Base Slab
                float baseR = 0.7f * brightness; // Lighter (Quartz)
                float baseG = 0.7f * brightness;
                float baseB = 0.7f * brightness;
                
                renderBoxWithOutlines(buf, pose, rx, ryOff, rz, 1.0f, 0.125f, 1.0f, baseR, baseG, baseB, alpha);
                
                Matrix4f model = new Matrix4f(pose);
                model.translate((float)rx, (float)ryOff, (float)rz);
                
                float rot = 0;
                if (facing == 0) rot = 180;
                else if (facing == 1) rot = 270;
                else if (facing == 2) rot = 0;
                else if (facing == 3) rot = 90;
                model.rotate(Axis.YP.rotationDegrees(rot));
                
                // Torches
                float tR = powered ? 1.0f : 0.5f;
                float tG = 0.0f;
                float tB = 0.0f;
                
                // Back Left
                renderBox(buf, model, -0.25f, 0.125f + 0.2f, 0.3125f, 0.125f, 0.4f, 0.125f, tR, tG, tB, alpha);
                // Back Right
                renderBox(buf, model, 0.25f, 0.125f + 0.2f, 0.3125f, 0.125f, 0.4f, 0.125f, tR, tG, tB, alpha);
                
                // Front Torch
                float frontH = subtract ? 0.3f : 0.2f;
                float frontY = 0.125f + (frontH/2.0f);
                
                renderBox(buf, model, 0, frontY, -0.3125f, 0.125f, frontH, 0.125f, tR, tG, tB, alpha);

            } else {
                // Render block as a box (1.0 size for solid terrain)
                renderBlockWithBorders(buf, pose, rx, ry, rz, 1.0f, 1.0f, 1.0f, (r * brightness) / 255.0f, (g * brightness) / 255.0f, (b * brightness) / 255.0f, alpha, exposedFaces);
            }
        }
    }
    
    private static void renderEntities(PoseStack poseStack, Player player, int minY, int maxY, int radius) {
        double centerX = player.getX();
        double centerZ = player.getZ();
        double centerY = player.getY();
        
        Minecraft mc = Minecraft.getInstance();
        Iterable<Entity> entities = mc.level.entitiesForRendering();
        Map<ChunkPos, ChunkScanner.ScannedChunk> scannedData = ChunkScanner.getData();
        ChunkPos playerChunk = player.chunkPosition();
        
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        
        for (Entity e : entities) {
            if (e == player) continue; 
            
            // Vertical Culling
            if (e.getY() < minY || e.getY() > maxY) continue;
            
            // Horizontal Culling
            ChunkPos entityChunk = new ChunkPos(e.blockPosition());
            if (!scannedData.containsKey(entityChunk)) continue;
            
            if (radius > 0) {
                 int dx = Math.abs(entityChunk.x - playerChunk.x);
                 int dz = Math.abs(entityChunk.z - playerChunk.z);
                 if (dx > radius || dz > radius) continue;
            }
            
            // Check visibility settings
            boolean visible = false;
            
            if (e instanceof Enemy) {
                if (ClientSettings.showEnemies) visible = true;
            } else if (e instanceof Villager) {
                if (ClientSettings.showVillagers) visible = true;
            } else if (e instanceof Animal || e instanceof Squid || e instanceof GlowSquid) {
                if (ClientSettings.showAnimals) visible = true;
            } else if (e instanceof Player) {
                if (ClientSettings.showPlayers) visible = true;
            } else if (e instanceof EndCrystal) {
                visible = true;
            }
            
            if (!visible) continue;
            
            double rx = e.getX() - centerX;
            double ry = e.getY() - centerY;
            double rz = e.getZ() - centerZ;
            
            poseStack.pushPose();
            poseStack.translate(rx, ry, rz);
            
            // Render Entity
            // Pass the interpolated yaw to the renderer so it handles rotation correctly
            float partialTick = mc.getPartialTick();
            float lerpYaw = net.minecraft.util.Mth.lerp(partialTick, e.yRotO, e.getYRot());
            
            try {
                // Render White Border for Players
                if (e instanceof Player) {
                    float width = e.getBbWidth() + 0.1f;
                    float height = e.getBbHeight() + 0.1f;
                    
                    Tesselator tess = Tesselator.getInstance();
                    BufferBuilder buf = tess.getBuilder();
                    
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    // Use slightly different logic than marker to ensure it surrounds the model
                    // Center X/Z, Bottom Y
                    buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                    renderInvertedColorBox(buf, poseStack.last().pose(), 0, 0, 0, width, height, width, 1.0f, 1.0f, 1.0f, 1.0f);
                    BufferUploader.drawWithShader(buf.end());
                }

                // Use full bright light (15, 15) so they are visible on the map
                mc.getEntityRenderDispatcher().render(e, 0, 0, 0, lerpYaw, partialTick, poseStack, bufferSource, LightTexture.pack(15, 15));
            } catch (Exception ex) {
                // Ignore rendering errors
            }
            
            poseStack.popPose();
        }
        
        // Flush buffers to ensure entities are drawn
        bufferSource.endBatch();
    }

    private static void renderBlockWithBorders(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float w, float h, float d, float r, float g, float b, float a, int exposedFaces) {
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
        
        // Bit 3: Up (+y) - Top Face
        if ((exposedFaces & 8) != 0) {
            buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
            
            // Render Borders on Top Face
            // Border color: Black Opaque (for visibility)
            int br = 0, bg = 0, bb = 0, ba = 255;
            float bSize = 0.125f; // 1/8th of a block
            float yOff = 0.02f; // Increased offset to prevent z-fighting
            
            // West Edge (Bit 0)
            if ((exposedFaces & 1) != 0) {
                 // CCW Order: Top-Left -> Bottom-Left -> Bottom-Right -> Top-Right
                 buf.vertex(pose, minX, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, minX, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, minX + bSize, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, minX + bSize, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
            }
            
            // East Edge (Bit 1)
            if ((exposedFaces & 2) != 0) {
                 // CCW Order
                 buf.vertex(pose, maxX - bSize, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, maxX - bSize, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, maxX, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                 buf.vertex(pose, maxX, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
            }
            
            // North Edge (Bit 4)
            if ((exposedFaces & 16) != 0) {
                  // CCW Order
                  buf.vertex(pose, minX, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, minX, maxY + yOff, minZ + bSize).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, maxX, maxY + yOff, minZ + bSize).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, maxX, maxY + yOff, minZ).color(br, bg, bb, ba).endVertex();
            }
            
            // South Edge (Bit 5)
            if ((exposedFaces & 32) != 0) {
                  // CCW Order
                  buf.vertex(pose, minX, maxY + yOff, maxZ - bSize).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, minX, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, maxX, maxY + yOff, maxZ).color(br, bg, bb, ba).endVertex();
                  buf.vertex(pose, maxX, maxY + yOff, maxZ - bSize).color(br, bg, bb, ba).endVertex();
            }
        }
        
        // Bit 2: Down (-y) - Bottom Face
        if ((exposedFaces & 4) != 0) {
            buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        }
        
        // Bit 4: North (-z) - Front/Back? 
        // Logic says North is -z.
        // In renderBox: "Front" was minZ. "Back" was maxZ. 
        // Wait, Front usually means +z or -z depending on convention.
        // In renderBox:
        // Front: minZ face? (lines 520-523 uses minZ for Z coords). Yes.
        // Back: maxZ face? (lines 526-529 uses maxZ). Yes.
        
        // So North (-z) corresponds to "Front" in renderBox code?
        // Let's check renderBox "Front":
        // buf.vertex(pose, maxX, maxY, minZ)...
        // Yes, all Z are minZ. So "Front" is North face (-z).
        
        if ((exposedFaces & 16) != 0) { // North
            buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        }
        
        // Bit 5: South (+z) - Back
        if ((exposedFaces & 32) != 0) { // South
            buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        }
        
        // Bit 0: West (-x) - Left?
        // In renderBox "Left": all X are minX. Yes, West.
        if ((exposedFaces & 1) != 0) { // West
            buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        }
        
        // Bit 1: East (+x) - Right?
        // In renderBox "Right": all X are maxX. Yes, East.
        if ((exposedFaces & 2) != 0) { // East
            buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
            buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        }
    }

    private static void renderBoxWithOutlines(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float w, float h, float d, float r, float g, float b, float a) {
        // Render Main Box
        renderBox(buf, pose, x, y, z, w, h, d, r, g, b, a);
        
        // Render Outlines
        float or = 0.0f; 
        float og = 0.0f; 
        float ob = 0.0f;
        float oa = 1.0f;
        
        float th = 0.02f; // Thickness of outline
        
        float minX = (float)(x - w/2);
        float maxX = (float)(x + w/2);
        float minZ = (float)(z - d/2);
        float maxZ = (float)(z + d/2);
        
        // Vertical Edges (4)
        renderBox(buf, pose, minX, y, minZ, th, h, th, or, og, ob, oa); // NW
        renderBox(buf, pose, maxX, y, minZ, th, h, th, or, og, ob, oa); // NE
        renderBox(buf, pose, maxX, y, maxZ, th, h, th, or, og, ob, oa); // SE
        renderBox(buf, pose, minX, y, maxZ, th, h, th, or, og, ob, oa); // SW
        
        // Top Edges (4)
        renderBox(buf, pose, x, y + h, minZ, w, th, th, or, og, ob, oa); // N
        renderBox(buf, pose, x, y + h, maxZ, w, th, th, or, og, ob, oa); // S
        renderBox(buf, pose, minX, y + h, z, th, th, d, or, og, ob, oa); // W
        renderBox(buf, pose, maxX, y + h, z, th, th, d, or, og, ob, oa); // E
        
        // Bottom Edges (4)
        renderBox(buf, pose, x, y, minZ, w, th, th, or, og, ob, oa); // N
        renderBox(buf, pose, x, y, maxZ, w, th, th, or, og, ob, oa); // S
        renderBox(buf, pose, minX, y, z, th, th, d, or, og, ob, oa); // W
        renderBox(buf, pose, maxX, y, z, th, th, d, or, og, ob, oa); // E
    }

    public static void renderBox(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float w, float h, float d, float r, float g, float b, float a) {
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
    
    private static void renderTexturedHead(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float size) {
        float minX = (float)(x - size/2);
        float maxX = (float)(x + size/2);
        float minY = (float)y;
        float maxY = (float)(y + size);
        float minZ = (float)(z - size/2);
        float maxZ = (float)(z + size/2);
        
        float uScale = 1.0f / 64.0f;
        float vScale = 1.0f / 64.0f;
        
        // Simplified Cube Mapping for Head
        // Top
        float uMin = 8 * uScale; float uMax = 16 * uScale;
        float vMin = 0 * vScale; float vMax = 8 * vScale;
        buf.vertex(pose, minX, maxY, minZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).uv(uMax, vMin).endVertex();
        
        // Bottom
        uMin = 16 * uScale; uMax = 24 * uScale;
        vMin = 0 * vScale; vMax = 8 * vScale;
        buf.vertex(pose, maxX, minY, minZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, minX, minY, maxZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, minX, minY, minZ).uv(uMax, vMin).endVertex();
        
        // Front
        uMin = 8 * uScale; uMax = 16 * uScale;
        vMin = 8 * vScale; vMax = 16 * vScale;
        buf.vertex(pose, maxX, maxY, minZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, maxX, minY, minZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, minX, minY, minZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, minX, maxY, minZ).uv(uMax, vMin).endVertex();
        
        // Back
        uMin = 24 * uScale; uMax = 32 * uScale;
        vMin = 8 * vScale; vMax = 16 * vScale;
        buf.vertex(pose, minX, maxY, maxZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, minX, minY, maxZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).uv(uMax, vMin).endVertex();
        
        // Left
        uMin = 16 * uScale; uMax = 24 * uScale;
        vMin = 8 * vScale; vMax = 16 * vScale;
        buf.vertex(pose, minX, maxY, minZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, minX, minY, minZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, minX, minY, maxZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).uv(uMax, vMin).endVertex();
        
        // Right
        uMin = 0 * uScale; uMax = 8 * uScale;
        vMin = 8 * vScale; vMax = 16 * vScale;
        buf.vertex(pose, maxX, maxY, maxZ).uv(uMin, vMin).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).uv(uMin, vMax).endVertex();
        buf.vertex(pose, maxX, minY, minZ).uv(uMax, vMax).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).uv(uMax, vMin).endVertex();
    }
    
    private static void renderEntityHeadIcon(PoseStack poseStack, Entity e, double x, double y, double z, int borderColor) {
        // Get Entity Texture
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<? super Entity> renderer = mc.getEntityRenderDispatcher().getRenderer(e);
        ResourceLocation texture = renderer.getTextureLocation(e);
        
        if (texture == null) return;
        
        // Calculate Size based on entity width (User requested "smaller for chicken")
        float width = e.getBbWidth();
        float size = Math.max(0.5f, width * 1.2f); // Minimum size 0.5, scaled slightly larger than hit box
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        
        // 1. Render Border (Colored Box below texture)
        // Extract RGBA from borderColor
        float bA = ((borderColor >> 24) & 0xFF) / 255.0f;
        float bR = ((borderColor >> 16) & 0xFF) / 255.0f;
        float bG = ((borderColor >> 8) & 0xFF) / 255.0f;
        float bB = (borderColor & 0xFF) / 255.0f;
        
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        // Render flat quad on XZ plane, slightly larger than texture
        float borderSize = size + 0.1f;
        float yPos = (float)y + e.getBbHeight() + 0.5f; // Float above entity head
        
        // Center on XZ
        float minX = (float)(x - borderSize/2);
        float maxX = (float)(x + borderSize/2);
        float minZ = (float)(z - borderSize/2);
        float maxZ = (float)(z + borderSize/2);
        
        buf.vertex(poseStack.last().pose(), minX, yPos, minZ).color(bR, bG, bB, bA).endVertex();
        buf.vertex(poseStack.last().pose(), minX, yPos, maxZ).color(bR, bG, bB, bA).endVertex();
        buf.vertex(poseStack.last().pose(), maxX, yPos, maxZ).color(bR, bG, bB, bA).endVertex();
        buf.vertex(poseStack.last().pose(), maxX, yPos, minZ).color(bR, bG, bB, bA).endVertex();
        
        BufferUploader.drawWithShader(buf.end());
        
        // 2. Render Textured Face Quad
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        
        float texSize = size;
        float texY = yPos + 0.01f; // Slightly above border
        
        float tMinX = (float)(x - texSize/2);
        float tMaxX = (float)(x + texSize/2);
        float tMinZ = (float)(z - texSize/2);
        float tMaxZ = (float)(z + texSize/2);
        
        // UV Calculation (Standard Face: 8,8 to 16,16)
        // Normalized for 64x64 texture (0.125 to 0.25)
        // Most mobs share this layout for the head face.
        // Villager head front is at different location, but let's try standard first.
        
        float uMin = 8.0f / 64.0f;
        float uMax = 16.0f / 64.0f;
        float vMin = 8.0f / 64.0f;
        float vMax = 16.0f / 64.0f;
        
        // Villager check
        if (e instanceof Villager) {
             // Villager texture is 64x64.
             // Head Front is 8x10.
             // Start (0,0) -> Face at (8, 0)? No.
             // Based on VillagerModel:
             // head = new ModelPart(this, 0, 0);
             // head.addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F);
             // Standard box UVs:
             // Front face starts at U = z(8) = 8.
             // V starts at z(8) = 8.
             // Size 8x10.
             // So U: 8->16. V: 8->18.
             uMin = 8.0f / 64.0f;
             uMax = 16.0f / 64.0f;
             vMin = 8.0f / 64.0f;
             vMax = 18.0f / 64.0f;
        }
        
        // Render Quad (Facing Up)
        // Orientation: Top of texture (vMin) is North (-Z)?
        // Usually on map: Up is North.
        // So vMin should be at minZ.
        
        buf.vertex(poseStack.last().pose(), tMinX, texY, tMinZ).uv(uMin, vMin).endVertex(); // Top-Left (North-West)
        buf.vertex(poseStack.last().pose(), tMinX, texY, tMaxZ).uv(uMin, vMax).endVertex(); // Bottom-Left (South-West)
        buf.vertex(poseStack.last().pose(), tMaxX, texY, tMaxZ).uv(uMax, vMax).endVertex(); // Bottom-Right (South-East)
        buf.vertex(poseStack.last().pose(), tMaxX, texY, tMinZ).uv(uMax, vMin).endVertex(); // Top-Right (North-East)
        
        BufferUploader.drawWithShader(buf.end());
    }

    private static void renderInvertedColorBox(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float w, float h, float d, float red, float green, float blue, float alpha) {
        float minX = (float)(x - w/2);
        float maxX = (float)(x + w/2);
        float minY = (float)y;
        float maxY = (float)(y + h);
        float minZ = (float)(z - d/2);
        float maxZ = (float)(z + d/2);
        
        // Reverse winding order (3, 2, 1, 0) for inverted hull effect
        
        // Top
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Bottom
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Front
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Back
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        
        // Left
        buf.vertex(pose, minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        
        // Right
        buf.vertex(pose, maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buf.vertex(pose, maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
    }

    private static void renderInvertedColorBox(BufferBuilder buf, Matrix4f pose, double x, double y, double z, float size, float red, float green, float blue, float alpha) {
        renderInvertedColorBox(buf, pose, x, y, z, size, size, size, red, green, blue, alpha);
    }

    private static void renderChunkGrid(PoseStack poseStack, Player player, int radius) {
        if (!ClientSettings.showChunkGrid) return;

        double cx = player.getX();
        double cz = player.getZ();
        
        ChunkPos centerChunk = player.chunkPosition();
        
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f);
        
        // Use LINES mode
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f pose = poseStack.last().pose();
        
        int minChunkX = centerChunk.x - radius;
        int maxChunkX = centerChunk.x + radius;
        int minChunkZ = centerChunk.z - radius;
        int maxChunkZ = centerChunk.z + radius;
        
        // Yellow color for grid, semi-transparent
        int r = 255, g = 255, b = 0, a = 128;
        
        // Vertical lines (Z-axis lines, varying X)
        for (int x = minChunkX; x <= maxChunkX + 1; x++) {
             double worldX = x * 16.0;
             double rx = worldX - cx;
             
             double worldZMin = minChunkZ * 16.0;
             double worldZMax = (maxChunkZ + 1) * 16.0;
             
             double rzMin = worldZMin - cz;
             double rzMax = worldZMax - cz;
             
             buf.vertex(pose, (float)rx, 0, (float)rzMin).color(r, g, b, a).endVertex();
             buf.vertex(pose, (float)rx, 0, (float)rzMax).color(r, g, b, a).endVertex();
        }
        
        // Horizontal lines (X-axis lines, varying Z)
        for (int z = minChunkZ; z <= maxChunkZ + 1; z++) {
             double worldZ = z * 16.0;
             double rz = worldZ - cz;
             
             double worldXMin = minChunkX * 16.0;
             double worldXMax = (maxChunkX + 1) * 16.0;
             
             double rxMin = worldXMin - cx;
             double rxMax = worldXMax - cx;
             
             buf.vertex(pose, (float)rxMin, 0, (float)rz).color(r, g, b, a).endVertex();
             buf.vertex(pose, (float)rxMax, 0, (float)rz).color(r, g, b, a).endVertex();
        }
        
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.lineWidth(1.0f);
    }
}
