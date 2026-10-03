/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.FireboxArrayMenu;

// The Fuel Burner's frame: the liquid and gas fuel tank on the left, the solid fuel slot with the flame under it (how much
// of the burning item is left), the screen with the temperature, heat bar and output, and the heat buffer on the right.
// All positions are relative to leftPos/topPos.
public class FireboxArrayScreen extends MachineScreen<FireboxArrayMenu> {
    private static final int BUFFER_X = 157, BUFFER_Y = 19;
    private static final int TANK_X = 9, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int FLAME_X = 32, FLAME_Y = 44;
    private static final int HEAT_X = 57, HEAT_Y = 34, HEAT_W = 84;
    private static final int LED_X = 57, LED_Y = 56;
    private static final int STATUS_X = 65, STATUS_Y = 56, STATUS_W = 76;
    private static final int SCREEN_LEFT = 57, SCREEN_RIGHT = 141;
    private static final int HEAT_TEXT_Y = 23, OUTPUT_TEXT_Y = 42;

    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier fuelFill = sprite("fuel_fill");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier ghostCoal = sprite("ghost_coal");

    public FireboxArrayScreen(FireboxArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "firebox_array", List.of(new HeatTab(menu::getHeat,
                Component.translatable("gui.arcforge.output"),
                () -> Component.translatable("gui.arcforge.hu_per_tick_gain", ArcforgeGui.grouped(menu.getHeatPerTick())),
                List.of(HeatTab.Row.always(Component.translatable("gui.arcforge.firebox_array.most"),
                                () -> Component.translatable("gui.arcforge.hu_per_tick_gain", ArcforgeGui.grouped(menu.getMaxHeatPerTick()))),
                        new HeatTab.Row(null, () -> Component.translatable("gui.arcforge.heat_tab.oxygen", ArcforgeGui.grouped(menu.getOxygen()),
                                ArcforgeGui.grouped(menu.getOxygenCapacity())), () -> menu.getOxygen() > 0)))), false);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case HEAT -> sideModeName("heat_output");
            case GAS_OUTPUT -> sideModeName("flue_gas");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, menu.getFluid(), menu.getFluidAmount(), menu.getFluidCapacity(), fuelFill, tankGauge,
                x, y, TANK_X, TANK_Y, TANK_W, TANK_H);
        ghost(graphics, ghostCoal, !menu.getFuelSlot().hasItem(), FireboxArrayMenu.FUEL_SLOT_X, FireboxArrayMenu.FUEL_SLOT_Y);
        // The flame shows what's left of the burning item; on liquid or gas fuel it's fully lit while it burns.
        boolean solid = menu.getSolidTotal() > 0 && menu.getSolidLeft() > 0;
        drawFlame(graphics, x, y, FLAME_X, FLAME_Y, solid ? menu.getSolidLeft() : menu.getHeatPerTick() > 0 ? 1 : 0,
                solid ? menu.getSolidTotal() : 1, menu.isOxyActive());
        drawGauge(graphics, heatBuffer, x, y, BUFFER_X, BUFFER_Y, menu.getHeat(), menu.getHeatCapacity());
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.heat_label"), SCREEN_LEFT, HEAT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())), SCREEN_RIGHT, HEAT_TEXT_Y,
                ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.output"), SCREEN_LEFT, OUTPUT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.hu_per_tick_gain", ArcforgeGui.grouped(menu.getHeatPerTick())), SCREEN_RIGHT,
                OUTPUT_TEXT_Y, ArcforgeGui.HEAT);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_gain", ArcforgeGui.grouped(menu.getHeatPerTick())).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.arcforge.firebox_array.size", ArcforgeGui.grouped(menu.getVolume()),
                    ArcforgeGui.grouped(menu.getMaxHeatPerTick())).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(TANK_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            // Any burner fuel, liquid or gas, so an empty tank is just "Fuel".
            addFluidTooltip(lines, menu.getFluid(), Component.translatable("gui.arcforge.fuel"), menu.getFluidAmount(), menu.getFluidCapacity());
            BurnerFuel fuel = menu.getFluid() != Fluids.EMPTY ? BurnerFuel.of(menu.getFluid()) : null;
            if (fuel != null) {
                lines.add(Component.translatable("gui.arcforge.burns_at",
                        ArcforgeGui.grouped(fuel.burnTemperature(ArcforgeConfig.FIREBOX_ARRAY_MAX_TEMPERATURE.getAsInt()))).withStyle(ChatFormatting.GOLD));
            }
        } else if (isHovering(HEAT_X, HEAT_Y - 2, HEAT_W, 8, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())));
            if (menu.getBurnTemperature() > 0) {
                lines.add(Component.translatable("gui.arcforge.burns_at", ArcforgeGui.grouped(menu.getBurnTemperature())).withStyle(ChatFormatting.GOLD));
            }
        } else if (isHovering(FLAME_X, FLAME_Y, FLAME_SIZE, FLAME_SIZE, mouseX, mouseY)) {
            if (menu.getBurningItem() != Items.AIR && menu.getSolidTotal() > 0) {
                lines.add(Component.translatable("gui.arcforge.firebox_array.burning", menu.getBurningItem().getDefaultInstance().getHoverName(),
                        String.format(Locale.ROOT, "%d%%", Math.round(100.0 * menu.getSolidLeft() / menu.getSolidTotal()))));
            }
            if (menu.isOxyActive() || menu.getOxygen() > 0) {
                addOxyFuelTooltip(lines, menu.getOxygen(), menu.getOxygenCapacity(),
                        menu.getMaxHeatPerTick() / 1000.0 * ArcforgeConfig.FIREBOX_ARRAY_OXYGEN_PER_THOUSAND_HU.getAsDouble());
            }
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
