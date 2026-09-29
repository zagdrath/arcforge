/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;

// Layout follows electric_pump_gui_layout.json. All positions are relative to leftPos/topPos.
public class ElectricPumpScreen extends MachineScreen<ElectricPumpMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int TANK_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int LABEL_X = 31, VALUE_RIGHT = 121, SOURCE_Y = 23, RATE_Y = 34;
    private static final int BAR_X = 31, BAR_Y = 45, BAR_W = 90, BAR_H = 4;
    private static final int LED_X = 31, LED_Y = 56, STATUS_X = 39, STATUS_Y = 56;
    private static final int BAR_EMPTY = 0xFF0E0E0E;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier waterFill = sprite("water_fill");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier ghostBucket = sprite("ghost_bucket");

    public ElectricPumpScreen(ElectricPumpMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "electric_pump", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawFluidTank(graphics, menu.getFluid(), menu.getFluidAmount(), menu.getFluidCapacity(), waterFill, tankGauge,
                x, y, TANK_X, TANK_Y, TANK_W, TANK_H);
        graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, BAR_EMPTY);
        int total = menu.getTotal();
        if (total > 0 && menu.getProgress() > 0) {
            int width = Math.min(BAR_W, Mth.ceil(BAR_W * (float) menu.getProgress() / total));
            graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + width, y + BAR_Y + BAR_H, ArcforgeGui.ACCENT);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
        ghost(graphics, ghostBucket, !menu.getBucketInSlot().hasItem(), ElectricPumpMenu.BUCKET_IN_X, ElectricPumpMenu.BUCKET_IN_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.source"), LABEL_X, SOURCE_Y, ArcforgeGui.LABEL, false);
        // Clipped to the room right of the label (4 px gap); the tooltip has the full name.
        textRight(graphics, clipped(sourceName(), valueRoom("gui.arcforge.source")), VALUE_RIGHT, SOURCE_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.rate"), LABEL_X, RATE_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.mb_per_second", ArcforgeGui.grouped(menu.getRate())), VALUE_RIGHT, RATE_Y, ArcforgeGui.TEXT);
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    private Component sourceName() {
        Fluid source = menu.getSource();
        return source != Fluids.EMPTY ? source.getFluidType().getDescription() : Component.translatable("gui.arcforge.none");
    }

    private int valueRoom(String labelKey) {
        return VALUE_RIGHT - (LABEL_X + font.width(Component.translatable(labelKey)) + 4);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(TANK_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getFluid(), Component.translatable("gui.arcforge.empty"), menu.getFluidAmount(), menu.getFluidCapacity());
        } else if (isHovering(LABEL_X, SOURCE_Y - 1, VALUE_RIGHT - LABEL_X, 10, mouseX, mouseY) && menu.getSource() != Fluids.EMPTY) {
            lines.add(sourceName());
            if (menu.isInfiniteSource()) {
                lines.add(Component.translatable("gui.arcforge.infinite_source").withColor(ArcforgeGui.ACCENT));
            }
        } else if (isHovering(BAR_X, BAR_Y - 2, BAR_W, BAR_H + 4, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        }
    }
}
