/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.blockentity.multiblock.MetalPressingArrayBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.MetalPressingArrayMenu;

// Layout follows metal_pressing_array_gui_layout.json. All positions are relative to leftPos/topPos.
public class MetalPressingArrayScreen extends MachineScreen<MetalPressingArrayMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int PROGRESS_X = 68, FIRST_PROGRESS_Y = 20, LANE_PITCH = 18, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int TEXT_X = 119, LANES_LABEL_Y = 23, LANES_Y = 33, POWER_LABEL_Y = 45, POWER_Y = 55;
    private static final int SCREEN_X = 114, SCREEN_Y = 18, SCREEN_W = 56, SCREEN_H = 52;
    private static final int LED_X = 161, LED_Y = 23;
    private static final int SAVING_COLOR = 0xFF55FF55;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");
    private final Identifier ghostDie = sprite("ghost_die");

    public MetalPressingArrayScreen(MetalPressingArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "metal_pressing_array", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        for (int lane = 0; lane < MetalPressingArrayBlockEntity.LANES; lane++) {
            int total = menu.getTotal(lane);
            int done = menu.getProgress(lane);
            if (total > 0 && done > 0) {
                int width = Math.min(PROGRESS_W, Mth.ceil(PROGRESS_W * (float) done / total));
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, progress, PROGRESS_W, PROGRESS_H, 0, 0,
                        x + PROGRESS_X, y + FIRST_PROGRESS_Y + lane * LANE_PITCH, width, PROGRESS_H);
            }
            ghost(graphics, ghostDie, !menu.getDieSlot(lane).hasItem(), MetalPressingArrayMenu.DIE_X,
                    MetalPressingArrayMenu.FIRST_LANE_Y + lane * MetalPressingArrayMenu.LANE_PITCH);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.lanes"), TEXT_X, LANES_LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.pressing_array.lanes_value", menu.getPressing(), MetalPressingArrayBlockEntity.LANES),
                TEXT_X, LANES_Y, ArcforgeGui.ACCENT, false);
        graphics.text(font, Component.translatable("gui.arcforge.power"), TEXT_X, POWER_LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.array.power_value", menu.getPowerSaving()), TEXT_X, POWER_Y, SAVING_COLOR, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(SCREEN_X, SCREEN_Y, SCREEN_W, SCREEN_H, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
            lines.add(Component.translatable("gui.arcforge.pressing_array.lanes_hint").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.pressing_array.power_hint", menu.getPowerSaving()).withStyle(ChatFormatting.GRAY));
        } else {
            for (int lane = 0; lane < MetalPressingArrayBlockEntity.LANES; lane++) {
                int laneY = MetalPressingArrayMenu.FIRST_LANE_Y + lane * MetalPressingArrayMenu.LANE_PITCH;
                if (isHovering(PROGRESS_X, FIRST_PROGRESS_Y + lane * LANE_PITCH, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal(lane) > 0) {
                    lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(lane), menu.getTotal(lane)));
                } else if (isHovering(MetalPressingArrayMenu.DIE_X - 1, laneY - 1, 18, 18, mouseX, mouseY) && !menu.getDieSlot(lane).hasItem()) {
                    lines.add(Component.translatable("gui.arcforge.die"));
                    lines.add(Component.translatable("gui.arcforge.die.none").withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }
}
