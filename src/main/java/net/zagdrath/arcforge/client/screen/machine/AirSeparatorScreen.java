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
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.AirSeparatorMenu;

// The Electrolyzer's layout without the water tank: the FE gauge on the left, what one operation makes, the arrow, and
// the Nitrogen and Oxygen tanks on the right. All positions are relative to leftPos/topPos.
public class AirSeparatorScreen extends MachineScreen<AirSeparatorMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int NITROGEN_X = 139, OXYGEN_X = 155;
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int PROGRESS_X = 114, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int TEXT_X = 27, NITROGEN_Y = 24, OXYGEN_Y = 36, TEXT_W = 84;
    private static final int LED_X = 27, LED_Y = 58;
    private static final int STATUS_X = 35, STATUS_Y = 58, STATUS_W = 100;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");

    public AirSeparatorScreen(AirSeparatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "air_separator", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
    }

    // Nitrogen leaves through this machine's Gas Output faces.
    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case ENERGY -> sideModeName("energy_input");
            case GAS_OUTPUT -> sideModeName("nitrogen");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        // The gases share the steam texture, tinted, so the gauge doubles as the (unused) fallback fill.
        drawFluidTank(graphics, menu.getPrimaryFluid(), menu.getPrimary(), menu.getGasCapacity(), tankGauge, tankGauge,
                x, y, NITROGEN_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, menu.getSecondaryFluid(), menu.getSecondary(), menu.getGasCapacity(), tankGauge, tankGauge,
                x, y, OXYGEN_X, TANK_Y, TANK_W, TANK_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    // What one operation makes, one gas a line.
    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        if (menu.getRecipePrimary() > 0) {
            graphics.text(font, clipped(Component.translatable("gui.arcforge.air_separator.nitrogen", menu.getRecipePrimary()), TEXT_W),
                    TEXT_X, NITROGEN_Y, ArcforgeGui.LABEL, false);
            if (menu.getRecipeSecondary() > 0) {
                graphics.text(font, clipped(Component.translatable("gui.arcforge.air_separator.oxygen", menu.getRecipeSecondary()), TEXT_W),
                        TEXT_X, OXYGEN_Y, ArcforgeGui.LABEL, false);
            }
        }
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(NITROGEN_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getPrimaryFluid(), Component.translatable("fluid_type.arcforge.nitrogen"), menu.getPrimary(), menu.getGasCapacity());
        } else if (isHovering(OXYGEN_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getSecondaryFluid(), Component.translatable("fluid_type.arcforge.oxygen"), menu.getSecondary(), menu.getGasCapacity());
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        } else if (isHovering(TEXT_X, NITROGEN_Y - 1, TEXT_W, OXYGEN_Y - NITROGEN_Y + 10, mouseX, mouseY) && menu.getRecipePrimary() > 0) {
            lines.add(Component.translatable("gui.arcforge.air_separator.vent_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
