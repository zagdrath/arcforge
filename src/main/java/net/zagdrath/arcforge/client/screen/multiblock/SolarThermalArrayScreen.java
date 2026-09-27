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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.blockentity.multiblock.SolarThermalArrayBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.SolarModel;
import net.zagdrath.arcforge.menu.multiblock.SolarThermalArrayMenu;
import net.zagdrath.arcforge.steam.SteamGrade;

// Layout follows solar_thermal_array_gui_layout.json: the heat buffer on the left, then the screen: a sky
// dial with the sun (or moon) on its arc over the day, the four collectors (lit or shaded), the weather,
// Output and Temp, a temperature bar with a tick at 500°C (High-Pressure Steam), the tracking axis and the
// status. All positions are relative to leftPos/topPos.
public class SolarThermalArrayScreen extends MachineScreen<SolarThermalArrayMenu> {
    private static final int BUFFER_X = 9, BUFFER_Y = 19;
    private static final float PIVOT_X = 50.0F, PIVOT_Y = 44.5F;
    private static final int RADIUS = 18, BODY = 5;
    private static final int DIAL_X = 28, DIAL_Y = 21, DIAL_W = 44, DIAL_H = 26;
    private static final int COLLECTORS_X = 29, COLLECTORS_Y = 51, COLLECTOR_STEP = 8, COLLECTOR = 6;
    private static final int WEATHER_X = 63, WEATHER_Y = 50, WEATHER = 7;
    private static final int LABEL_X = 76, RIGHT = 164, OUTPUT_Y = 22, TEMP_Y = 32, AXIS_Y = 51;
    private static final int BAR_X = 76, BAR_Y = 44, BAR_W = 88, BAR_H = 4;
    private static final int LED_X = 76, LED_Y = 61, STATUS_X = 84, STATUS_Y = 61;
    // High-Pressure Steam needs the source at 500°C or more.
    private static final int HIGH_PRESSURE = 500;
    private static final int STEAM_COLOR = 0xFFD8E0E6, HIGH_PRESSURE_COLOR = 0xFF8CC8FF;
    private static final int NORTH_SOUTH_COLOR = 0xFF5FD4C4, EAST_WEST_COLOR = 0xFFE8B83A;

    public SolarThermalArrayScreen(SolarThermalArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "solar_thermal_array", List.of(new HeatTab(menu::getHeat,
                Component.translatable("gui.arcforge.output"),
                () -> Component.translatable("gui.arcforge.hu_per_tick_gain", menu.getHeatPerTick()),
                Component.translatable("gui.arcforge.temp"),
                () -> Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())))), false);
    }

    private long dayTime() {
        return minecraft.level != null ? Math.floorMod(minecraft.level.getDefaultClockTime(), (long) SolarModel.DAY_LENGTH) : 6_000L;
    }

    // Where the temperature sits on the bar, 20-550°C across its width.
    private static int barWidth(int celsius) {
        int max = ArcforgeConfig.SOLAR_MAX_TEMPERATURE.getAsInt();
        return Mth.clamp(Math.round((float) BAR_W * (celsius - HeatBuffer.AMBIENT_CELSIUS) / (max - HeatBuffer.AMBIENT_CELSIUS)), 0, BAR_W);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, sprite("heat_buffer"), x, y, BUFFER_X, BUFFER_Y, menu.getHeat(), menu.getHeatCapacity());

        // The sun by day, the moon by night, from the east (left) over to the west.
        long time = dayTime();
        boolean night = SolarModel.isNight(time);
        double angle = Math.PI * (1.0 - (night ? time - SolarModel.DAYLIGHT : time) / (double) SolarModel.DAYLIGHT);
        int bodyX = Math.round(PIVOT_X + RADIUS * (float) Math.cos(angle) - BODY / 2.0F);
        int bodyY = Math.round(PIVOT_Y - RADIUS * (float) Math.sin(angle) - BODY / 2.0F);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(night ? "moon" : "sun"), x + bodyX, y + bodyY, BODY, BODY);

        for (int i = 0; i < SolarModel.COLLECTORS; i++) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(menu.isCollectorLit(i) ? "collector_sky" : "collector_shaded"),
                    x + COLLECTORS_X + i * COLLECTOR_STEP, y + COLLECTORS_Y, COLLECTOR, COLLECTOR);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("weather_" + menu.getWeather().name().toLowerCase(Locale.ROOT)),
                x + WEATHER_X, y + WEATHER_Y, WEATHER, WEATHER);

        drawBar(graphics, sprite("heat_bar"), sprite("heat_marker"), x, y, BAR_X, BAR_Y, BAR_W, BAR_H, barWidth(menu.getTemperature()));
        int tick = x + BAR_X + barWidth(HIGH_PRESSURE);
        graphics.fill(tick, y + BAR_Y, tick + 1, y + BAR_Y + BAR_H, HIGH_PRESSURE_COLOR);
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.output"), LABEL_X, OUTPUT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.hu_per_tick_gain", menu.getHeatPerTick()), RIGHT, OUTPUT_Y, ArcforgeGui.HEAT);
        graphics.text(font, Component.translatable("gui.arcforge.temp"), LABEL_X, TEMP_Y, ArcforgeGui.LABEL, false);
        int temperature = menu.getTemperature();
        textRight(graphics, Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(temperature)), RIGHT, TEMP_Y,
                temperature >= HIGH_PRESSURE ? HIGH_PRESSURE_COLOR : STEAM_COLOR);
        graphics.text(font, Component.translatable("gui.arcforge.solar.axis"), LABEL_X, AXIS_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, axisName(), RIGHT, AXIS_Y, menu.isNorthSouth() ? NORTH_SOUTH_COLOR : EAST_WEST_COLOR);
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    // "N-S ×1.0" or "E-W ×0.7" (the font has no en dash).
    private Component axisName() {
        double multiplier = menu.isNorthSouth() ? ArcforgeConfig.SOLAR_NORTH_SOUTH_MULTIPLIER.getAsDouble() : ArcforgeConfig.SOLAR_EAST_WEST_MULTIPLIER.getAsDouble();
        return Component.translatable(menu.isNorthSouth() ? "gui.arcforge.solar.north_south" : "gui.arcforge.solar.east_west",
                String.format(Locale.ROOT, "%.1f", multiplier));
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.heat", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
        } else if (isHovering(DIAL_X, DIAL_Y, DIAL_W, DIAL_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.solar.sun", (int) Math.round(SolarModel.sun(dayTime()) * 100)));
            lines.add(Component.translatable("gui.arcforge.solar.panel", Component.translatable(menu.isStowed()
                    ? "gui.arcforge.solar.panel.stowed" : "gui.arcforge.solar.panel.tracking")).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(COLLECTORS_X, COLLECTORS_Y, 3 * COLLECTOR_STEP + COLLECTOR, COLLECTOR, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.solar.collectors", menu.getSkyCount()));
            lines.add(Component.translatable("gui.arcforge.solar.collectors_output",
                    String.format(Locale.ROOT, "%.2f", menu.getSkyCount() / (double) SolarModel.COLLECTORS)).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(WEATHER_X, WEATHER_Y, WEATHER, WEATHER, mouseX, mouseY)) {
            SolarModel.Weather weather = menu.getWeather();
            lines.add(Component.translatable("gui.arcforge.solar.weather",
                    Component.translatable("gui.arcforge.solar.weather." + weather.name().toLowerCase(Locale.ROOT)),
                    String.format(Locale.ROOT, "%.1f", SolarModel.weatherMultiplier(weather, SolarThermalArrayBlockEntity.settings()))));
            lines.add(Component.translatable("gui.arcforge.solar.biome", biomeName(),
                    String.format(Locale.ROOT, "%.2f", menu.getBiomeMultiplier())).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(LABEL_X, AXIS_Y - 1, RIGHT - LABEL_X, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.solar.axis_tooltip", axisName()));
            lines.add(Component.translatable("gui.arcforge.solar.axis_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(LABEL_X, TEMP_Y - 1, RIGHT - LABEL_X, 10, mouseX, mouseY)
                || isHovering(BAR_X, BAR_Y - 2, BAR_W, BAR_H + 4, mouseX, mouseY)) {
            SteamGrade grade = menu.getTemperature() >= HIGH_PRESSURE ? SteamGrade.HIGH_PRESSURE : SteamGrade.STEAM;
            lines.add(Component.translatable("gui.arcforge.solar.makes", grade.fluid().getFluidType().getDescription()));
            lines.add(Component.translatable("gui.arcforge.solar.high_pressure", HIGH_PRESSURE).withStyle(ChatFormatting.GRAY));
        }
    }

    private Component biomeName() {
        double multiplier = menu.getBiomeMultiplier();
        return Component.translatable(multiplier > 1.001 ? "gui.arcforge.solar.biome.hot" : multiplier < 0.999 ? "gui.arcforge.solar.biome.cold"
                : "gui.arcforge.solar.biome.temperate");
    }
}
