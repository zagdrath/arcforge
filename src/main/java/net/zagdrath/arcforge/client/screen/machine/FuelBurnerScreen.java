/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.FuelBurnerMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// Layout follows fuel_burner_gui_layout.json: the Geothermal Plant's frame with a fuel tank.
// All positions are relative to leftPos/topPos.
public class FuelBurnerScreen extends MachineScreen<FuelBurnerMenu> {
    private static final int BUFFER_X = 157, BUFFER_Y = 19;
    private static final int TANK_X = 9, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int HEAT_X = 57, HEAT_Y = 34, HEAT_W = 84;
    private static final int FLAME_X = 127, FLAME_Y = 52;
    private static final int LED_X = 57, LED_Y = 56;
    private static final int STATUS_X = 65, STATUS_Y = 56;
    private static final int SCREEN_LEFT = 57, SCREEN_RIGHT = 141;
    private static final int HEAT_TEXT_Y = 23, OUTPUT_TEXT_Y = 42;

    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier fuelFill = sprite("fuel_fill");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier ghostBucket = sprite("ghost_bucket");

    public FuelBurnerScreen(FuelBurnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "fuel_burner", List.of(new HeatTab(menu::getHeat,
                Component.translatable("gui.arcforge.output"),
                () -> Component.translatable("gui.arcforge.hu_per_tick_gain", menu.getHeatPerTick()),
                Component.translatable("gui.arcforge.fuel"),
                () -> Component.translatable("gui.arcforge.fuel_burner.fuel_value", fuelName(menu),
                        String.format(Locale.ROOT, "%.2f", menu.getBurnRate()).replaceAll("0+$", "").replaceAll("\\.$", "")))));
    }

    private static Component fuelName(FuelBurnerMenu menu) {
        return menu.getFluid() != Fluids.EMPTY ? menu.getFluid().getFluidType().getDescription() : Component.translatable("gui.arcforge.none");
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.HEAT ? sideModeName("heat_output") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, menu.getFluid(), menu.getFluidAmount(), menu.getFluidCapacity(), fuelFill, tankGauge,
                x, y, TANK_X, TANK_Y, TANK_W, TANK_H);
        drawGauge(graphics, heatBuffer, x, y, BUFFER_X, BUFFER_Y, menu.getHeat(), menu.getHeatCapacity());
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        // A continuous burner: the flame is fully lit while fuel flows.
        drawFlame(graphics, x, y, FLAME_X, FLAME_Y, menu.getHeatPerTick() > 0 ? 1 : 0, 1);
        drawLed(graphics, x, y, LED_X, LED_Y);
        ghost(graphics, ghostBucket, !menu.getOutputSlot().hasItem(), FuelBurnerMenu.BUCKET_OUT_X, FuelBurnerMenu.BUCKET_OUT_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.heat_label"), SCREEN_LEFT, HEAT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())), SCREEN_RIGHT, HEAT_TEXT_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.output"), SCREEN_LEFT, OUTPUT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.hu_per_tick_gain", menu.getHeatPerTick()), SCREEN_RIGHT, OUTPUT_TEXT_Y, ArcforgeGui.HEAT);
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_gain", menu.getHeatPerTick()).withStyle(ChatFormatting.GOLD));
        } else if (isHovering(TANK_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getFluid(), ModFluids.CREOSOTE_TYPE.get().getDescription(), menu.getFluidAmount(), menu.getFluidCapacity());
            BurnerFuel fuel = BurnerFuel.of(menu.getFluid());
            if (fuel != null) {
                lines.add(Component.translatable("gui.arcforge.burns_at", ArcforgeGui.grouped(fuel.burnTemperature(ArcforgeConfig.FUEL_BURNER_MAX_TEMPERATURE.getAsInt())))
                        .withStyle(ChatFormatting.GOLD));
            }
        } else if (isHovering(HEAT_X, HEAT_Y - 2, HEAT_W, 8, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())));
        }
    }
}
