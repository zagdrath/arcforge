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
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.DieselGeneratorMenu;

// The Pumpjack's frame: the FE gauge on the left, the screen (FE/t against its full output, its speed as a bar, the
// fuel it burns and what each mB is worth, the status) and the fuel tank on the right. All positions are relative to
// leftPos/topPos.
public class DieselGeneratorScreen extends MachineScreen<DieselGeneratorMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int TANK_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int LABEL_X = 31, VALUE_RIGHT = 145, OUTPUT_Y = 21, FUEL_Y = 31, VALUE_Y = 41;
    private static final int BAR_X = 31, BAR_Y = 52, BAR_W = 114, BAR_H = 3;
    private static final int LED_X = 31, LED_Y = 59, STATUS_X = 39, STATUS_Y = 59, STATUS_W = 106;
    private static final int BAR_EMPTY = 0xFF0E0E0E;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier fuelFill = sprite("fuel_fill");
    private final Identifier tankGauge = sprite("tank_gauge");

    public DieselGeneratorScreen(DieselGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "diesel_generator", List.of(new EnergyTab(menu::getEnergy, menu::getMade)), false);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("fuel_input");
            case ENERGY -> sideModeName("energy_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawFluidTank(graphics, menu.getFuelFluid(), menu.getFuel(), menu.getFuelCapacity(), fuelFill, tankGauge, x, y, TANK_X, TANK_Y, TANK_W, TANK_H);
        graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, BAR_EMPTY);
        int width = Math.min(BAR_W, Mth.ceil(BAR_W * menu.getSpeed()));
        if (width > 0) {
            graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + width, y + BAR_Y + BAR_H, ArcforgeGui.ACCENT);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        Component label = Component.translatable("gui.arcforge.output");
        graphics.text(font, label, LABEL_X, OUTPUT_Y, ArcforgeGui.LABEL, false);
        // In full while it fits beside the label, in short (8.2k / 15k) when it doesn't.
        Component output = Component.translatable("gui.arcforge.diesel_generator.output", ArcforgeGui.grouped(menu.getMade()),
                ArcforgeGui.grouped(menu.getMaxOutput()));
        if (font.width(output) > VALUE_RIGHT - LABEL_X - font.width(label) - 4) {
            output = Component.translatable("gui.arcforge.diesel_generator.output", ArcforgeGui.compact(menu.getMade()),
                    ArcforgeGui.compact(menu.getMaxOutput()));
        }
        textRight(graphics, output, VALUE_RIGHT, OUTPUT_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.diesel_generator.fuel"), LABEL_X, FUEL_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.mb_per_tick", String.format(Locale.ROOT, "%.2f", menu.getBurned())), VALUE_RIGHT, FUEL_Y,
                ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.diesel_generator.value"), LABEL_X, VALUE_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, menu.getFePerMb() > 0 ? Component.translatable("gui.arcforge.diesel_generator.fe_per_mb", menu.getFePerMb())
                : Component.translatable("gui.arcforge.none"), VALUE_RIGHT, VALUE_Y, ArcforgeGui.TEXT);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_gain", ArcforgeGui.grouped(menu.getMade())).withStyle(ChatFormatting.GREEN));
        } else if (isHovering(TANK_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getFuelFluid(), Component.translatable("gui.arcforge.diesel_generator.fuel_tank"), menu.getFuel(),
                    menu.getFuelCapacity());
            if (menu.getFuelFluid() == Fluids.EMPTY) {
                lines.add(Component.translatable("gui.arcforge.diesel_generator.fuels").withStyle(ChatFormatting.GRAY));
            }
        } else if (isHovering(LABEL_X, OUTPUT_Y - 1, VALUE_RIGHT - LABEL_X, 10, mouseX, mouseY)) {
            // The output in full, for when it's shown short.
            lines.add(Component.translatable("gui.arcforge.diesel_generator.output", ArcforgeGui.grouped(menu.getMade()),
                    ArcforgeGui.grouped(menu.getMaxOutput())));
        } else if (isHovering(BAR_X, BAR_Y - 2, BAR_W, BAR_H + 4, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.diesel_generator.speed", Math.round(menu.getSpeed() * 100)));
        } else if (isHovering(LED_X, STATUS_Y - 1, VALUE_RIGHT - LED_X, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
