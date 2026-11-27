/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.HeliumGasTurbineMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// The Helium Gas Turbine's screen, on the Steam Turbine Array's background and sprites as the Nuclear Steam Turbine's: the
// Hot Helium tank on the left and the cooled Helium waiting to go back out in the narrow gauge beside it, the speed dial,
// the flow and output, and under the dial the Hot Helium coming in. All positions are relative to leftPos/topPos.
public class HeliumGasTurbineScreen extends MachineScreen<HeliumGasTurbineMenu> {
    private static final int HOT_X = 9, HOT_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int ENERGY_X = 157, ENERGY_Y = 19;
    private static final int DIAL_X = 38, DIAL_Y = 22, DIAL_W = 44, DIAL_H = 26;
    private static final float PIVOT_X = 60.0F, PIVOT_Y = 45.5F, NEEDLE_LENGTH = 13.0F;
    private static final int RPM_CENTER_X = 60, RPM_Y = 52, NOTE_Y = 61;
    private static final int LABEL_X = 88, VALUE_RIGHT = 147, FLOW_LABEL_Y = 22, FLOW_Y = 31, OUTPUT_LABEL_Y = 42, OUTPUT_Y = 51;
    private static final int LED_X = 88, LED_Y = 61, STATUS_X = 96, STATUS_Y = 61;
    private static final int STATUS_W = 150 - 2 - STATUS_X + 2, NOTE_W = LED_X - 2 - LUBE_X - LUBE_W - 2;
    private static final int NEEDLE_COLOR = 0xFFFF5A4A;
    private static final float NEEDLE_EASE = 0.15F;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier hotFill = sprite("steam_fill");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier cooledFill = sprite("lube_fill");
    private final Identifier cooledGauge = sprite("lube_gauge");
    private float shownRpm = -1;

    public HeliumGasTurbineScreen(HeliumGasTurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "steam_turbine_array", List.of(new EnergyTab(menu::getEnergy, menu::getFePerTick)), false);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("hot_helium_input");
            case OUTPUT -> sideModeName("helium_output");
            case ENERGY -> sideModeName("energy_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, ModFluids.HOT_HELIUM.get(), menu.getHotAmount(), menu.getHotCapacity(), hotFill, tankGauge,
                x, y, HOT_X, HOT_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.HELIUM.get(), menu.getCooledAmount(), menu.getCooledCapacity(), cooledFill, cooledGauge,
                x, y, LUBE_X, LUBE_Y, LUBE_W, LUBE_H);
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawNeedle(graphics, x, y);
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    // A 1px line from the pivot, left (stopped) to right (synchronous speed), stepped along in half pixels, then a 2x2 cap.
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

    // Under the dial: the Hot Helium coming in.
    private Component note() {
        return Component.translatable("gui.arcforge.helium_turbine.inflow_short", ArcforgeGui.compact(menu.getInflow()));
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        Component rpm = Component.translatable("gui.arcforge.rpm", ArcforgeGui.grouped(menu.getRpm()));
        graphics.text(font, rpm, RPM_CENTER_X - font.width(rpm) / 2, RPM_Y, ArcforgeGui.WHITE, false);
        Component note = clipped(note(), NOTE_W);
        graphics.text(font, note, RPM_CENTER_X - font.width(note) / 2, NOTE_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.turbine.flow_label"), LABEL_X, FLOW_LABEL_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.turbine.flow_value", ArcforgeGui.compact(menu.getFlow()), ArcforgeGui.compact(menu.getMaxFlow())),
                VALUE_RIGHT, FLOW_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.output"), LABEL_X, OUTPUT_LABEL_Y, ArcforgeGui.LABEL, false);
        int output = menu.getFePerTick();
        textRight(graphics, Component.translatable("gui.arcforge.fe_per_tick_gain", output < 10_000 ? ArcforgeGui.grouped(output) : ArcforgeGui.compact(output)),
                VALUE_RIGHT, OUTPUT_Y, ArcforgeGui.ACCENT);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(LABEL_X, FLOW_LABEL_Y - 1, VALUE_RIGHT - LABEL_X, OUTPUT_Y + font.lineHeight - FLOW_LABEL_Y + 1, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.flow_value_compact", menu.getFlow(), menu.getMaxFlow()));
            lines.add(Component.translatable("gui.arcforge.helium_turbine.inflow", ArcforgeGui.grouped(menu.getInflow())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_gain", ArcforgeGui.grouped(menu.getFePerTick())).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (isHovering(HOT_X - 1, HOT_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, ModFluids.HOT_HELIUM.get(), Component.translatable("gui.arcforge.empty"), menu.getHotAmount(), menu.getHotCapacity());
        } else if (isHovering(LUBE_X - 1, LUBE_Y - 1, LUBE_W + 2, LUBE_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, ModFluids.HELIUM.get(), Component.translatable("gui.arcforge.empty"), menu.getCooledAmount(), menu.getCooledCapacity());
            lines.add(Component.translatable("gui.arcforge.helium_turbine.helium_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
        } else if (isHovering(DIAL_X, DIAL_Y, DIAL_W, DIAL_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.rpm_of", ArcforgeGui.grouped(menu.getRpm()), ArcforgeGui.grouped(menu.getMaxRpm())));
            lines.add(Component.translatable("gui.arcforge.nuclear_turbine.synchronous").withStyle(ChatFormatting.GRAY));
        }
    }
}
