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
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;

// Layout follows firebox_gui_layout.json. All positions are relative to leftPos/topPos.
public class FireboxScreen extends MachineScreen<BurnerMenu> {
    private static final int BUFFER_X = 157, BUFFER_Y = 19;
    private static final int FLAME_X = 21, FLAME_Y = 46;
    private static final int HEAT_X = 57, HEAT_Y = 34, HEAT_W = 84;
    private static final int LED_X = 57, LED_Y = 56;
    private static final int STATUS_X = 65, STATUS_Y = 56;
    private static final int SCREEN_LEFT = 57, SCREEN_RIGHT = 141;
    private static final int HEAT_TEXT_Y = 23, OUTPUT_TEXT_Y = 42;

    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier ghostCoal = sprite("ghost_coal");

    public FireboxScreen(BurnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "firebox", List.of(new HeatTab(menu::getStored,
                Component.translatable("gui.arcforge.output"),
                () -> Component.translatable("gui.arcforge.hu_per_tick_gain", menu.getOutputPerTick()))));
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        ghost(graphics, ghostCoal, !menu.getFuelSlot().hasItem(), BurnerMenu.FUEL_SLOT_X, BurnerMenu.FUEL_SLOT_Y);
        drawFlame(graphics, x, y, FLAME_X, FLAME_Y, menu.getBurnTime(), menu.getBurnTotal());
        drawGauge(graphics, heatBuffer, x, y, BUFFER_X, BUFFER_Y, menu.getStored(), menu.getCapacity());
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.heat_label"), SCREEN_LEFT, HEAT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", menu.getTemperature()), SCREEN_RIGHT, HEAT_TEXT_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.output"), SCREEN_LEFT, OUTPUT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.hu_per_tick_gain", menu.getOutputPerTick()), SCREEN_RIGHT, OUTPUT_TEXT_Y, ArcforgeGui.HEAT);
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getStored()), ArcforgeGui.grouped(menu.getCapacity())));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_gain", menu.getOutputPerTick()).withStyle(ChatFormatting.GOLD));
        } else if (isHovering(HEAT_X, HEAT_Y - 2, HEAT_W, 8, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.celsius", menu.getTemperature()));
        } else if (isHovering(FLAME_X, FLAME_Y, FLAME_SIZE, FLAME_SIZE, mouseX, mouseY) && menu.getBurnTime() > 0) {
            lines.add(Component.translatable("gui.arcforge.burn_time", (menu.getBurnTime() + 19) / 20));
        }
    }
}
