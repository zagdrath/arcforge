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
import net.minecraft.world.item.Item;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;

// Layout follows combustion_plant_gui_layout.json. All positions are relative to leftPos/topPos.
public class CombustionPlantScreen extends MachineScreen<BurnerMenu> {
    private static final int ENERGY_X = 157, ENERGY_Y = 19;
    private static final int FLAME_X = BurnerMenu.FLAME_X, FLAME_Y = BurnerMenu.FLAME_Y;
    private static final int BURN_X = 57, BURN_Y = 34, BURN_W = 84, BURN_H = 4;
    private static final int LED_X = 57, LED_Y = 56;
    private static final int STATUS_X = 65, STATUS_Y = 56;
    private static final int SCREEN_LEFT = 57, SCREEN_RIGHT = 141;
    private static final int FUEL_TEXT_Y = 23, OUTPUT_TEXT_Y = 42;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier burnBar = sprite("burn_bar");
    private final Identifier marker = sprite("heat_marker");
    private final Identifier ghostCoal = sprite("ghost_coal");

    public CombustionPlantScreen(BurnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "combustion_plant", List.of(new EnergyTab(menu::getStored, menu::getOutputPerTick)));
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        ghost(graphics, ghostCoal, !menu.getFuelSlot().hasItem(), BurnerMenu.FUEL_SLOT_X, BurnerMenu.FUEL_SLOT_Y);
        drawFlame(graphics, x, y, FLAME_X, FLAME_Y, menu.getBurnTime(), menu.getBurnTotal());
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getStored(), menu.getCapacity());
        // Fuel left in the item burning now.
        drawBar(graphics, burnBar, marker, x, y, BURN_X, BURN_Y, BURN_W, BURN_H, scaled(menu.getBurnTime(), menu.getBurnTotal(), BURN_W));
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.fuel"), SCREEN_LEFT, FUEL_TEXT_Y, ArcforgeGui.LABEL, false);
        Item burning = menu.getBurningItem();
        Component fuel = burning != null ? burning.getName(burning.getDefaultInstance()) : Component.translatable("gui.arcforge.none");
        // Clipped to the room right of the "Fuel" label (4 px gap), e.g. "Block of Coal"; the tooltip has the full name.
        int room = SCREEN_RIGHT - (SCREEN_LEFT + font.width(Component.translatable("gui.arcforge.fuel")) + 4);
        textRight(graphics, clipped(fuel, room), SCREEN_RIGHT, FUEL_TEXT_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.output"), SCREEN_LEFT, OUTPUT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.fe_per_tick_gain", menu.getOutputPerTick()), SCREEN_RIGHT, OUTPUT_TEXT_Y, ArcforgeGui.ACCENT);
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getStored()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_gain", menu.getOutputPerTick()).withStyle(ChatFormatting.GREEN));
        } else if ((isHovering(FLAME_X, FLAME_Y, FLAME_SIZE, FLAME_SIZE, mouseX, mouseY) || isHovering(BURN_X, BURN_Y - 2, BURN_W, 8, mouseX, mouseY))
                && menu.getBurnTime() > 0) {
            lines.add(Component.translatable("gui.arcforge.burn_time", (menu.getBurnTime() + 19) / 20));
        } else if (isHovering(SCREEN_LEFT, FUEL_TEXT_Y - 1, SCREEN_RIGHT - SCREEN_LEFT, 10, mouseX, mouseY) && menu.getBurningItem() != null) {
            Item burning = menu.getBurningItem();
            lines.add(burning.getName(burning.getDefaultInstance()));
        }
    }
}
