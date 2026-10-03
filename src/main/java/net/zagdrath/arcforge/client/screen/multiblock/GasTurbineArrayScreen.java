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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.GasTurbineArrayMenu;

// Layout follows gui_layouts.json gas_turbine_array: the Steam Turbine Array's background, with the fuel tank
// where the steam was, the fuel burned and FE/t beside the RPM dial, and the exhaust under it (orange while it
// vents to the air). All positions are relative to leftPos/topPos.
public class GasTurbineArrayScreen extends MachineScreen<GasTurbineArrayMenu> {
    private static final int FUEL_X = 9, FUEL_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int ENERGY_X = 157, ENERGY_Y = 19;
    private static final int DIAL_X = 38, DIAL_Y = 22, DIAL_W = 44, DIAL_H = 26;
    private static final float PIVOT_X = 60.0F, PIVOT_Y = 45.5F, NEEDLE_LENGTH = 13.0F;
    private static final int RPM_CENTER_X = 60, RPM_Y = 52, EXHAUST_Y = 61, EXHAUST_MAX_W = 52;
    private static final int LABEL_X = 88, VALUE_RIGHT = 147, FUEL_LABEL_Y = 22, FUEL_VALUE_Y = 31, OUTPUT_LABEL_Y = 42, OUTPUT_Y = 51;
    private static final int LED_X = 88, LED_Y = 61, STATUS_X = 96, STATUS_Y = 61;
    // The screen's inner right edge is x150: values end 3 px in from it, the status is clipped 2 px short of it.
    private static final int STATUS_W = 150 - 2 - STATUS_X + 2;
    private static final int NEEDLE_COLOR = 0xFFFF5A4A;
    private static final int VENTING_COLOR = 0xFFE8913A;
    private static final float NEEDLE_EASE = 0.15F;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier tankGauge = sprite("tank_gauge");
    private float shownRpm = -1;

    public GasTurbineArrayScreen(GasTurbineArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "gas_turbine_array", List.of(new EnergyTab(menu::getEnergy, menu::getFePerTick)), false);
    }

    @Override
    protected List<RedstoneMode> redstoneModes() {
        return RedstoneMode.THROTTLED;
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("fuel_input");
            case ENERGY -> sideModeName("energy_output");
            case LUBRICANT -> sideModeName("lubricant_input");
            case HEAT -> sideModeName("heat_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, menu.getFuel(), menu.getFuelAmount(), menu.getFuelCapacity(), tankGauge, tankGauge,
                x, y, FUEL_X, FUEL_Y, TANK_W, TANK_H);
        drawLubricant(graphics, x, y, menu.getLubricantFluid(), menu.getLubricant(), menu.getLubricantCapacity());
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawNeedle(graphics, x, y);
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    // As on the Steam Turbine Array: a 1px line from the pivot, 0 rpm on the left to the maximum on the right.
    private void drawNeedle(GuiGraphicsExtractor graphics, int x, int y) {
        float target = menu.getRpm();
        shownRpm = shownRpm < 0 ? target : shownRpm + (target - shownRpm) * NEEDLE_EASE;
        int max = Math.max(1, menu.getMaxRpm());
        double angle = Math.PI * (1.0 - Mth.clamp(shownRpm / max, 0.0F, 1.0F));
        float dx = (float) Math.cos(angle);
        float dy = (float) -Math.sin(angle);
        for (float step = 0; step <= NEEDLE_LENGTH; step += 0.5F) {
            int px = x + Math.round(PIVOT_X + dx * step - 0.5F);
            int py = y + Math.round(PIVOT_Y + dy * step - 0.5F);
            graphics.fill(px, py, px + 1, py + 1, NEEDLE_COLOR);
        }
        int capX = x + Math.round(PIVOT_X) - 1;
        int capY = y + Math.round(PIVOT_Y) - 1;
        graphics.fill(capX, capY, capX + 2, capY + 2, ArcforgeGui.TEXT);
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private Component exhaustText() {
        return Component.translatable("gui.arcforge.gas_turbine.exhaust", ArcforgeGui.grouped(menu.getExhaustHu()));
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        Component rpm = Component.translatable("gui.arcforge.gas_turbine.rpm", oneDecimal(menu.getRpm() / 1000.0));
        graphics.text(font, rpm, RPM_CENTER_X - font.width(rpm) / 2, RPM_Y, ArcforgeGui.WHITE, false);
        if (menu.getExhaustHu() > 0) {
            Component exhaust = exhaustText();
            int width = Math.min(EXHAUST_MAX_W, font.width(exhaust));
            graphics.text(font, exhaust, RPM_CENTER_X - width / 2, EXHAUST_Y, menu.isVenting() ? VENTING_COLOR : ArcforgeGui.LABEL, false);
        }
        graphics.text(font, Component.translatable("gui.arcforge.gas_turbine.fuel"), LABEL_X, FUEL_LABEL_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.mb_per_tick", oneDecimal(menu.getFuelPerTick())), VALUE_RIGHT, FUEL_VALUE_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.output"), LABEL_X, OUTPUT_LABEL_Y, ArcforgeGui.LABEL, false);
        int output = menu.getFePerTick();
        textRight(graphics, Component.translatable("gui.arcforge.fe_per_tick_gain", output < 10_000 ? ArcforgeGui.grouped(output) : ArcforgeGui.compact(output)),
                VALUE_RIGHT, OUTPUT_Y, ArcforgeGui.ACCENT);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (menu.getExhaustHu() > 0 && isHovering(RPM_CENTER_X - EXHAUST_MAX_W / 2, EXHAUST_Y - 1, EXHAUST_MAX_W, font.lineHeight + 1, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.gas_turbine.exhaust_tooltip", ArcforgeGui.grouped(menu.getExhaustHu()), menu.getExhaustCelsius()));
            if (menu.isVenting()) {
                lines.add(Component.translatable("gui.arcforge.gas_turbine.exhaust_venting", ArcforgeGui.grouped(menu.getVentedHu())).withStyle(ChatFormatting.GOLD));
            }
            return;
        }
        if (isHovering(LABEL_X, FUEL_LABEL_Y - 1, VALUE_RIGHT - LABEL_X, FUEL_VALUE_Y + font.lineHeight - FUEL_LABEL_Y + 1, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.gas_turbine.throttle", menu.getThrottlePercent()));
            return;
        }
        if (isHovering(FUEL_X - 1, FUEL_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getFuel(), Component.translatable("gui.arcforge.gas_turbine.fuel"), menu.getFuelAmount(), menu.getFuelCapacity());
            if (menu.getFuelAmount() > 0) {
                lines.add(Component.translatable("gui.arcforge.gas_turbine.fe_per_mb", oneDecimal(menu.getFePerMb()).replaceAll("\\.0$", ""))
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (isHovering(LUBE_X - 1, LUBE_Y - 1, LUBE_W + 2, LUBE_H + 2, mouseX, mouseY)) {
            addLubricantTooltip(lines, menu.getLubricantFluid(), menu.getLubricant(), menu.getLubricantCapacity(), ArcforgeConfig.GAS_TURBINE_LUBRICANT_SPIN_UP.getAsDouble());
        } else if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(DIAL_X, DIAL_Y, DIAL_W, DIAL_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.rpm_of", ArcforgeGui.grouped(menu.getRpm()), ArcforgeGui.grouped(menu.getMaxRpm())));
        }
    }
}
