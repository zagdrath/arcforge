/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.farming.GreenhouseBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.GreenhouseMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// The Greenhouse Array (176x206): the FE, water, Nutrient Solution and Carbon Dioxide gauges on the left; the fertilizer
// slot with the beds, growth speed and temperature under it; the 3x3 harvest on the right; and the systems panel below:
// the status, then an LED for water, nutrients, CO2, heat, lamps and warmth. Positions are relative to leftPos/topPos.
public class GreenhouseScreen extends MachineScreen<GreenhouseMenu> {
    private static final int ENERGY_X = 9, WATER_X = 25, NUTRIENTS_X = 41, CO2_X = 57, GAUGE_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int INFO_X = 76, BEDS_Y = 41, SPEED_Y = 51, TEMP_Y = 61, INFO_W = 36;
    private static final int PANEL_X = 10, STATUS_Y = 79, ROW1_Y = 89, ROW2_Y = 99;
    // The systems panel's three columns (x of each LED; its label follows 8 px on) and their widths.
    private static final int[] COLUMN_X = { 10, 60, 118 }, COLUMN_W = { 50, 58, 48 };

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier fluidFill = sprite("fluid_fill");
    private final Identifier ghostFertilizer = sprite("ghost_fertilizer");

    // The systems panel: which system, its label, its column and row.
    private record SystemCell(int dataIndex, String name, int column, int y) {}

    private static final List<SystemCell> CELLS = List.of(
            new SystemCell(GreenhouseMenu.DATA_SYSTEM_WATER, "water", 0, ROW1_Y),
            new SystemCell(GreenhouseMenu.DATA_SYSTEM_NUTRIENTS, "nutrients", 1, ROW1_Y),
            new SystemCell(GreenhouseMenu.DATA_SYSTEM_CO2, "co2", 2, ROW1_Y),
            new SystemCell(GreenhouseMenu.DATA_SYSTEM_HEAT, "heat", 0, ROW2_Y),
            new SystemCell(GreenhouseMenu.DATA_SYSTEM_LAMPS, "lamps", 1, ROW2_Y),
            new SystemCell(-1, "warmth", 2, ROW2_Y));

    public GreenhouseScreen(GreenhouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "greenhouse", List.of(
                EnergyTab.usage(() -> menu.get(GreenhouseMenu.DATA_ENERGY), () -> menu.get(GreenhouseMenu.DATA_ENERGY_USAGE)),
                new HeatTab(() -> menu.get(GreenhouseMenu.DATA_HEAT),
                        Component.translatable("gui.arcforge.usage"),
                        () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.get(GreenhouseMenu.DATA_HEAT_USAGE)),
                        Component.translatable("gui.arcforge.temp"),
                        () -> Component.literal(celsius(menu.getTemperature())))), false, 206);
        enableAutoEject();
    }

    private static String celsius(double value) {
        return String.format(Locale.ROOT, "%.1f°C", value);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case HEAT -> sideModeName("heat_input");
            case ENERGY -> sideModeName("energy_input");
            default -> super.sideModeName(mode);
        };
    }

    private boolean ideal() {
        double t = menu.getTemperature();
        return t >= ArcforgeConfig.GREENHOUSE_IDEAL_MIN.getAsInt() && t <= ArcforgeConfig.GREENHOUSE_IDEAL_MAX.getAsInt();
    }

    private GreenhouseBlockEntity.SystemState state(SystemCell cell) {
        if (cell.dataIndex() >= 0) {
            return menu.system(cell.dataIndex());
        }
        double factor = GreenhouseBlockEntity.temperatureFactor(menu.getTemperature());
        return factor >= 1.0 ? GreenhouseBlockEntity.SystemState.ON : factor > 0 ? GreenhouseBlockEntity.SystemState.IDLE
                : GreenhouseBlockEntity.SystemState.BLOCKED;
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, GAUGE_Y, menu.get(GreenhouseMenu.DATA_ENERGY), menu.get(GreenhouseMenu.DATA_ENERGY_CAPACITY));
        drawFluidTank(graphics, Fluids.WATER, menu.get(GreenhouseMenu.DATA_WATER), menu.get(GreenhouseMenu.DATA_WATER_CAPACITY), fluidFill, tankGauge,
                x, y, WATER_X, GAUGE_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.NUTRIENT_SOLUTION.get(), menu.get(GreenhouseMenu.DATA_NUTRIENTS), menu.get(GreenhouseMenu.DATA_NUTRIENTS_CAPACITY),
                fluidFill, tankGauge, x, y, NUTRIENTS_X, GAUGE_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.CARBON_DIOXIDE.get(), menu.get(GreenhouseMenu.DATA_CO2), menu.get(GreenhouseMenu.DATA_CO2_CAPACITY),
                fluidFill, tankGauge, x, y, CO2_X, GAUGE_Y, TANK_W, TANK_H);
        ghost(graphics, ghostFertilizer, !menu.getSlotFor(GreenhouseBlockEntity.SLOT_FERTILIZER).hasItem(), GreenhouseMenu.FERTILIZER_X,
                GreenhouseMenu.FERTILIZER_Y);
        drawLed(graphics, x, y, PANEL_X, STATUS_Y + 1);
        for (SystemCell cell : CELLS) {
            Identifier led = Identifier.fromNamespaceAndPath(Arcforge.MODID, "container/greenhouse/" + state(cell).led());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, led, x + COLUMN_X[cell.column()], y + cell.y() + 1, LED_SIZE, LED_SIZE);
        }
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(Component.translatable("gui.arcforge.greenhouse.beds", menu.get(GreenhouseMenu.DATA_BEDS)), INFO_W),
                INFO_X, BEDS_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(Component.literal(String.format(Locale.ROOT, "x%.2f", menu.getSpeed())), INFO_W), INFO_X, SPEED_Y,
                ArcforgeGui.ACCENT, false);
        graphics.text(font, clipped(Component.literal(celsius(menu.getTemperature())), INFO_W), INFO_X, TEMP_Y,
                ideal() ? ArcforgeGui.TEXT : ArcforgeGui.HEAT, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), 150), PANEL_X + 8, STATUS_Y, ArcforgeGui.TEXT, false);
        for (SystemCell cell : CELLS) {
            graphics.text(font, clipped(Component.translatable("gui.arcforge.greenhouse.system." + cell.name()), COLUMN_W[cell.column()] - 10),
                    COLUMN_X[cell.column()] + 8, cell.y(), ArcforgeGui.LABEL, false);
        }
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, GAUGE_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.get(GreenhouseMenu.DATA_ENERGY)),
                    ArcforgeGui.grouped(menu.get(GreenhouseMenu.DATA_ENERGY_CAPACITY))).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.get(GreenhouseMenu.DATA_ENERGY_USAGE)).withStyle(ChatFormatting.RED));
            // FE only runs the Grow Lamps: by day under the sky it stays full.
            lines.add(Component.translatable("gui.arcforge.greenhouse.energy_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isHovering(WATER_X - 1, GAUGE_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, Fluids.WATER, Fluids.WATER.getFluidType().getDescription(), menu.get(GreenhouseMenu.DATA_WATER),
                    menu.get(GreenhouseMenu.DATA_WATER_CAPACITY));
        } else if (isHovering(NUTRIENTS_X - 1, GAUGE_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, ModFluids.NUTRIENT_SOLUTION.get(), ModFluids.NUTRIENT_SOLUTION_TYPE.get().getDescription(),
                    menu.get(GreenhouseMenu.DATA_NUTRIENTS), menu.get(GreenhouseMenu.DATA_NUTRIENTS_CAPACITY));
        } else if (isHovering(CO2_X - 1, GAUGE_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, ModFluids.CARBON_DIOXIDE.get(), ModFluids.CARBON_DIOXIDE_TYPE.get().getDescription(),
                    menu.get(GreenhouseMenu.DATA_CO2), menu.get(GreenhouseMenu.DATA_CO2_CAPACITY));
        } else if (isHovering(INFO_X, BEDS_Y - 1, INFO_W, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.greenhouse.beds_hint", menu.get(GreenhouseMenu.DATA_GROWING),
                    menu.get(GreenhouseMenu.DATA_BEDS)));
        } else if (isHovering(INFO_X, SPEED_Y - 1, INFO_W, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.greenhouse.speed_hint", String.format(Locale.ROOT, "%.2f", menu.getSpeed())));
            lines.add(Component.translatable("gui.arcforge.greenhouse.speed_detail").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(INFO_X, TEMP_Y - 1, INFO_W, 10, mouseX, mouseY)) {
            addTemperatureTooltip(lines);
        } else {
            for (SystemCell cell : CELLS) {
                if (isHovering(COLUMN_X[cell.column()], cell.y() - 1, COLUMN_W[cell.column()] - 2, 10, mouseX, mouseY)) {
                    addSystemTooltip(lines, cell);
                    return;
                }
            }
        }
    }

    private void addTemperatureTooltip(List<Component> lines) {
        lines.add(Component.translatable("gui.arcforge.greenhouse.temperature", celsius(menu.getTemperature())));
        lines.add(Component.translatable("gui.arcforge.greenhouse.outside", celsius(menu.getAmbient())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.arcforge.greenhouse.ideal", ArcforgeConfig.GREENHOUSE_IDEAL_MIN.getAsInt(),
                ArcforgeConfig.GREENHOUSE_IDEAL_MAX.getAsInt()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.arcforge.greenhouse.warmth_factor",
                Math.round(GreenhouseBlockEntity.temperatureFactor(menu.getTemperature()) * 100)).withStyle(ChatFormatting.GOLD));
    }

    private void addSystemTooltip(List<Component> lines, SystemCell cell) {
        if (cell.dataIndex() < 0) {
            addTemperatureTooltip(lines);
            return;
        }
        GreenhouseBlockEntity.SystemState state = state(cell);
        lines.add(Component.translatable("gui.arcforge.greenhouse.system." + cell.name()));
        lines.add(Component.translatable("gui.arcforge.greenhouse." + cell.name() + "." + state.name().toLowerCase(Locale.ROOT))
                .withStyle(state == GreenhouseBlockEntity.SystemState.ON ? ChatFormatting.GREEN
                        : state == GreenhouseBlockEntity.SystemState.BLOCKED ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (cell.name().equals("lamps")) {
            lines.add(Component.translatable("gui.arcforge.greenhouse.lamps_count", menu.get(GreenhouseMenu.DATA_LAMPS)).withStyle(ChatFormatting.DARK_GRAY));
        } else if (cell.name().equals("nutrients") && menu.get(GreenhouseMenu.DATA_FERTILIZER) > 0) {
            lines.add(Component.translatable("gui.arcforge.cloche.fertilizer", menu.get(GreenhouseMenu.DATA_FERTILIZER)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
