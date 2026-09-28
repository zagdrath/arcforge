/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.BlockBreakerMenu;

// Layout follows gui_layouts.json "block_breaker": the target's name, a thin progress bar under it, and the
// 3x3 buffer on the right.
public class BlockBreakerScreen extends MachineScreen<BlockBreakerMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int TARGET_X = 30, TARGET_Y = 22, TARGET_W = 60;
    private static final int BAR_X = 31, BAR_Y = 42, BAR_W = 56, BAR_H = 2;
    private static final int LED_X = 30, LED_Y = 60;
    // Clipped short of the buffer grid (x 97).
    private static final int STATUS_X = 38, STATUS_Y = 60, STATUS_W = 57;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progressBar = sprite("progress_bar");

    public BlockBreakerScreen(BlockBreakerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "block_breaker", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
        enableAutoEject();
    }

    @Override
    protected List<RedstoneMode> redstoneModes() {
        return RedstoneMode.WITH_PULSE;
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        int fill = scaled(menu.getProgress(), menu.getTotal(), BAR_W);
        if (fill > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, progressBar, BAR_W, BAR_H, 0, 0, x + BAR_X, y + BAR_Y, fill, BAR_H);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    private Component target() {
        if (minecraft == null || minecraft.level == null) {
            return Component.empty();
        }
        BlockState machine = minecraft.level.getBlockState(menu.getPos());
        BlockState target = minecraft.level.getBlockState(menu.getPos().relative(MachineBlock.facing(machine)));
        return target.isAir() ? Component.translatable("gui.arcforge.status.nothing_to_break") : target.getBlock().getName();
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        String name = font.plainSubstrByWidth(target().getString(), TARGET_W);
        graphics.text(font, Component.literal(name), TARGET_X, TARGET_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
            return;
        }
        if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, font.lineHeight + 1, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
            return;
        }
        if (isHovering(TARGET_X, TARGET_Y - 1, TARGET_W, font.lineHeight + 1, mouseX, mouseY)) {
            lines.add(target());
            return;
        }
        if (isHovering(BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        }
    }
}
