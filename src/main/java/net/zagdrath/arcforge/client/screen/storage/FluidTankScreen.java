/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.storage;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.SideConfigTab;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.storage.FluidTankMenu;

// Layout follows layout.json -> fluid_tank.
public class FluidTankScreen extends StorageScreen<FluidTankMenu> {
    private static final int GAUGE_X = 63, GAUGE_Y = 17, GAUGE_W = 36, GAUGE_H = 54;
    private static final int TEXT_X = 111, TEXT_MAX_WIDTH = 54;
    private static final int FLUID_Y = 21, STORED_LABEL_Y = 35, AMOUNT_Y = 45, CAPACITY_Y = 57;
    private static final int EMPTY_COLOR = 0xFF707070;

    private final Identifier gauge = sprite("gauge");
    private final Identifier fallback = sprite("fluid_fallback");

    public FluidTankScreen(FluidTankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "fluid_tank");
        tabs.add(new SideConfigTab(menu::getSideMode,
                (side, action) -> sendButton(MachineMenuButtons.sideButtonId(side, action)),
                () -> sendButton(MachineMenuButtons.CLEAR_SIDES)));
    }

    // Renders the fluid's still texture, tinted and tiled in 16px steps, clipped to the fill, then the gauge marks.
    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        FluidStack fluid = menu.getFluid();
        int fillHeight = scaledRound(menu.getAmount(), menu.getCapacity(), GAUGE_H);
        int left = x + GAUGE_X;
        int bottom = y + GAUGE_Y + GAUGE_H;
        if (fillHeight > 0 && !fluid.isEmpty()) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            TextureAtlasSprite still = model.stillMaterial().sprite();
            if (still != null) {
                int tint = (model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(fluid) : -1) | 0xFF000000;
                graphics.enableScissor(left, bottom - fillHeight, left + GAUGE_W, bottom);
                for (int tileY = bottom - 16; tileY > bottom - fillHeight - 16; tileY -= 16) {
                    for (int tileX = left; tileX < left + GAUGE_W; tileX += 16) {
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, still, tileX, tileY, 16, 16, tint);
                    }
                }
                graphics.disableScissor();
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, fallback, GAUGE_W, GAUGE_H, 0, GAUGE_H - fillHeight,
                        left, bottom - fillHeight, GAUGE_W, fillHeight);
            }
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, gauge, left, y + GAUGE_Y, GAUGE_W, GAUGE_H);
    }

    @Override
    protected void drawInfo(GuiGraphicsExtractor graphics) {
        FluidStack fluid = menu.getFluid();
        if (fluid.isEmpty()) {
            graphics.text(font, Component.translatable("gui.arcforge.empty"), TEXT_X, FLUID_Y, EMPTY_COLOR, false);
        } else {
            String name = fluid.getHoverName().getString();
            if (font.width(name) > TEXT_MAX_WIDTH) {
                name = font.plainSubstrByWidth(name, TEXT_MAX_WIDTH - font.width("…")) + "…";
            }
            graphics.text(font, name, TEXT_X, FLUID_Y, ArcforgeGui.TEXT, false);
        }
        graphics.text(font, Component.translatable("gui.arcforge.stored"), TEXT_X, STORED_LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.buckets", ArcforgeGui.buckets(menu.getAmount())), TEXT_X, AMOUNT_Y, ArcforgeGui.WHITE, false);
        graphics.text(font, Component.translatable("gui.arcforge.buckets_capacity", ArcforgeGui.buckets(menu.getCapacity())), TEXT_X, CAPACITY_Y, ArcforgeGui.LABEL, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
            FluidStack fluid = menu.getFluid();
            lines.add(fluid.isEmpty() ? Component.translatable("gui.arcforge.empty") : fluid.getHoverName());
            lines.add(Component.translatable("gui.arcforge.mb_stored", ArcforgeGui.grouped(menu.getAmount()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
