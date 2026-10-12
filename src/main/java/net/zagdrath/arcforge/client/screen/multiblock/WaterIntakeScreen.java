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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.WaterIntakeMenu;

// The Industrial Water Intake's GUI (textures/gui/container/water_intake.png, the Condenser Array's frame): the FE gauge
// on the left, the water tank on the right, and between them the rate it can pump, the water body it stands in (and the
// river or ocean bonus) and the status. All positions are relative to leftPos/topPos.
public class WaterIntakeScreen extends MachineScreen<WaterIntakeMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19, OUT_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int TEXT_X = 53, VALUE_RIGHT = 145, RATE_Y = 23, BODY_Y = 34, BONUS_Y = 44;
    private static final int LED_X = 53, LED_Y = 56, STATUS_X = 61, STATUS_Y = 56;
    private static final int OPEN_WATER_COLOR = 0xFF6FA8FF;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier waterFill = sprite("water_fill");
    private final Identifier tankGauge = sprite("tank_gauge");

    public WaterIntakeScreen(WaterIntakeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "water_intake", List.of(), false);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case OUTPUT -> sideModeName("water_output");
            case ENERGY -> sideModeName("energy_input");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getEnergyCapacity());
        drawFluidTank(graphics, Fluids.WATER, menu.getWater(), menu.getTankCapacity(), waterFill, tankGauge, x, y, OUT_X, TANK_Y, TANK_W, TANK_H);
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.water_intake.rate"), TEXT_X, RATE_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.mb_per_tick", ArcforgeGui.grouped(menu.getRate())), VALUE_RIGHT, RATE_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.water_intake.body"), TEXT_X, BODY_Y, ArcforgeGui.LABEL, false);
        int needed = ArcforgeConfig.WATER_INTAKE_MIN_WATER.getAsInt();
        textRight(graphics, Component.literal(ArcforgeGui.grouped(menu.getWaterBlocks()) + " / " + ArcforgeGui.grouped(needed)), VALUE_RIGHT, BODY_Y,
                menu.getWaterBlocks() >= needed ? ArcforgeGui.TEXT : ArcforgeGui.HEAT);
        if (menu.isOpenWater()) {
            textRight(graphics, Component.translatable("gui.arcforge.water_intake.open_water",
                    multiplier(ArcforgeConfig.WATER_INTAKE_OPEN_WATER_MULTIPLIER.getAsDouble())), VALUE_RIGHT, BONUS_Y, OPEN_WATER_COLOR);
        }
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    private static String multiplier(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getEnergyCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(OUT_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, Fluids.WATER, Component.translatable("gui.arcforge.water"), menu.getWater(), menu.getTankCapacity());
        } else if (isHovering(TEXT_X, BODY_Y - 1, VALUE_RIGHT - TEXT_X, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.water_intake.body.hint", ArcforgeGui.grouped(ArcforgeConfig.WATER_INTAKE_MIN_WATER.getAsInt())));
        } else if (isHovering(LED_X, STATUS_Y - 1, VALUE_RIGHT - LED_X, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
            lines.add(Component.translatable("gui.arcforge.mb_per_tick_gain", ArcforgeGui.grouped(menu.getPumped())).withStyle(ChatFormatting.GRAY));
        }
    }
}
