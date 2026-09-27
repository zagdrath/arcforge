/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.menu.multiblock.CarbonizerMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// Layout follows carbonizer_gui_layout.json. All positions are relative to leftPos/topPos.
public class CarbonizerScreen extends MultiblockScreen<CarbonizerMenu> {
    private static final int PROGRESS_X = 52, PROGRESS_Y = 24, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int GAUGE_X = 151, GAUGE_Y = 17, GAUGE_W = 16, GAUGE_H = 54;
    private static final int CHAMBER_X = 11, CHAMBER_Y = 50, CHAMBER_W = 10, CHAMBER_H = 16, CHAMBER_PITCH = 13;

    private final Identifier progress = sprite("progress");
    private final Identifier gauge = sprite("gauge");
    private final Identifier chamberEmpty = sprite("chamber_empty");
    private final Identifier chamberIdle = sprite("chamber_idle");
    private final Identifier chamberActive = sprite("chamber_active");
    private final Identifier ghostCoal = sprite("ghost_coal");
    private final Identifier ghostBucket = sprite("ghost_bucket");

    public CarbonizerScreen(CarbonizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "carbonizer", menu::getRedstoneMode, menu::getPorts);
        enableAutoEject(menu::isAutoEject);
    }

    @Override
    protected Component currentTitle() {
        int chambers = menu.getChambers();
        if (chambers <= 0) {
            return Component.translatable("gui.arcforge.carbonizer.incomplete");
        }
        Component size = Component.translatable("gui.arcforge.carbonizer.size", menu.getSliceHeight(), menu.getSliceDepth());
        return chambers == 1
                ? Component.translatable("gui.arcforge.carbonizer.chamber", size)
                : Component.translatable("gui.arcforge.carbonizer.chambers", chambers, size);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        // The arrow follows the most advanced working chamber.
        float best = 0.0F;
        for (int chamber = 0; chamber < menu.getChambers(); chamber++) {
            best = Math.max(best, menu.getChamberProgress(chamber));
        }
        int arrow = Mth.ceil(PROGRESS_W * best);
        if (arrow > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, progress, PROGRESS_W, PROGRESS_H, 0, 0,
                    x + PROGRESS_X, y + PROGRESS_Y, arrow, PROGRESS_H);
        }

        // One icon per chamber: its fire rises from the bottom as it works.
        for (int chamber = 0; chamber < CarbonizerBlockEntity.MAX_CHAMBERS; chamber++) {
            int cx = x + CHAMBER_X + chamber * CHAMBER_PITCH;
            int cy = y + CHAMBER_Y;
            if (chamber >= menu.getChambers()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, chamberEmpty, cx, cy, CHAMBER_W, CHAMBER_H);
                continue;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, chamberIdle, cx, cy, CHAMBER_W, CHAMBER_H);
            float chamberProgress = menu.getChamberProgress(chamber);
            int fire = chamberProgress < 0 ? 0 : Math.max(1, Math.round(CHAMBER_H * chamberProgress));
            if (fire > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, chamberActive, CHAMBER_W, CHAMBER_H, 0, CHAMBER_H - fire,
                        cx, cy + CHAMBER_H - fire, CHAMBER_W, fire);
            }
        }

        drawGauge(graphics, x, y);

        ghost(graphics, ghostCoal, !menu.getSlotFor(CarbonizerBlockEntity.SLOT_INPUT).hasItem(), CarbonizerMenu.INPUT_X, CarbonizerMenu.INPUT_Y);
        ghost(graphics, ghostBucket, !menu.getSlotFor(CarbonizerBlockEntity.SLOT_BUCKET_IN).hasItem(), CarbonizerMenu.BUCKET_IN_X, CarbonizerMenu.BUCKET_IN_Y);
    }

    // The fluid's still texture, tinted and tiled in 16px steps, clipped to the fill, then the gauge marks.
    private void drawGauge(GuiGraphicsExtractor graphics, int x, int y) {
        Fluid fluid = menu.getFluid();
        int capacity = menu.getFluidCapacity();
        int fill = capacity <= 0 ? 0 : (int) Math.min(GAUGE_H, Math.round((double) menu.getFluidAmount() * GAUGE_H / capacity));
        int left = x + GAUGE_X;
        int bottom = y + GAUGE_Y + GAUGE_H;
        if (fill > 0 && fluid != Fluids.EMPTY) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            TextureAtlasSprite still = model.stillMaterial().sprite();
            int tint = (model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(new FluidStack(fluid, 1)) : -1) | 0xFF000000;
            graphics.enableScissor(left, bottom - fill, left + GAUGE_W, bottom);
            for (int tileY = bottom - 16; tileY > bottom - fill - 16; tileY -= 16) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, still, left, tileY, 16, 16, tint);
            }
            graphics.disableScissor();
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, gauge, left, y + GAUGE_Y, GAUGE_W, GAUGE_H);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
            Fluid fluid = menu.getFluid();
            lines.add(fluid != Fluids.EMPTY ? fluid.getFluidType().getDescription() : ModFluids.CREOSOTE_TYPE.get().getDescription());
            lines.add(Component.translatable("gui.arcforge.mb_stored", ArcforgeGui.grouped(menu.getFluidAmount()), ArcforgeGui.grouped(menu.getFluidCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int chamber = 0; chamber < menu.getChambers(); chamber++) {
            if (isHovering(CHAMBER_X + chamber * CHAMBER_PITCH, CHAMBER_Y, CHAMBER_W, CHAMBER_H, mouseX, mouseY)) {
                float chamberProgress = menu.getChamberProgress(chamber);
                lines.add(chamberProgress < 0
                        ? Component.translatable("gui.arcforge.carbonizer.chamber_idle", chamber + 1)
                        : Component.translatable("gui.arcforge.carbonizer.chamber_progress", chamber + 1, (int) (chamberProgress * 100), batchName(chamber)));
                return;
            }
        }
    }

    // "3 coal": how many of which item the chamber is baking.
    private Component batchName(int chamber) {
        net.minecraft.world.item.Item item = menu.getChamberItem(chamber);
        Component name = item != null ? item.getName(item.getDefaultInstance()) : Component.empty();
        return Component.translatable("gui.arcforge.carbonizer.batch", menu.getChamberBatch(chamber), name);
    }
}
