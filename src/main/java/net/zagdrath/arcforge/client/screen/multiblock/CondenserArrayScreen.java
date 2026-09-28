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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.CondenserArrayMenu;

// Layout follows gui_layouts.json "condenser_array": Exhaust Steam in on the left, water out on the right, and
// between them the cooling rate, what makes it up (open air, water and ice touching it, the climate) and the
// status. All positions are relative to leftPos/topPos.
public class CondenserArrayScreen extends MachineScreen<CondenserArrayMenu> {
    private static final int IN_X = 9, OUT_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int TEXT_X = 53, VALUE_RIGHT = 145, RATE_Y = 23, PARTS_Y = 34, ICE_Y = 44;
    private static final int LED_X = 53, LED_Y = 56, STATUS_X = 61, STATUS_Y = 56;
    // The climate multiplier: blue when it helps (cold), orange when it hurts (the Nether).
    private static final int COLD_COLOR = 0xFF6FA8FF;

    private final Identifier steamFill = sprite("steam_fill");
    private final Identifier waterFill = sprite("water_fill");
    private final Identifier tankGauge = sprite("tank_gauge");

    public CondenserArrayScreen(CondenserArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "condenser_array", List.of(), false);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("exhaust_input");
            case OUTPUT -> sideModeName("water_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, CondenserArrayMenu.exhaustFluid(), menu.getExhaust(), menu.getTankCapacity(), steamFill, tankGauge,
                x, y, IN_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, Fluids.WATER, menu.getWater(), menu.getTankCapacity(), waterFill, tankGauge, x, y, OUT_X, TANK_Y, TANK_W, TANK_H);
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.condenser.cooling"), TEXT_X, RATE_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.mb_per_tick", ArcforgeGui.grouped(menu.getRate())), VALUE_RIGHT, RATE_Y, ArcforgeGui.TEXT);
        // "Air 120 · Water 40" (water left out when there is none), then "Ice 70" and the climate multiplier.
        MutableComponent parts = Component.translatable("gui.arcforge.condenser.air", menu.getAir());
        if (menu.getWaterBonus() > 0) {
            parts.append(" · ").append(Component.translatable("gui.arcforge.condenser.water", menu.getWaterBonus()));
        }
        graphics.text(font, parts, TEXT_X, PARTS_Y, ArcforgeGui.LABEL, false);
        if (menu.getIce() > 0) {
            graphics.text(font, Component.translatable("gui.arcforge.condenser.ice", menu.getIce()), TEXT_X, ICE_Y, ArcforgeGui.LABEL, false);
        }
        Component climate = climateText();
        if (climate != null) {
            textRight(graphics, climate, VALUE_RIGHT, ICE_Y, menu.getMultiplierPercent() > 100 ? COLD_COLOR : ArcforgeGui.HEAT);
        }
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    // "×1.25" or "×0.5", or null in a plain climate.
    private Component climateText() {
        int percent = menu.getMultiplierPercent();
        if (percent == 100) {
            return null;
        }
        return Component.translatable(percent > 100 ? "gui.arcforge.condenser.cold" : "gui.arcforge.condenser.nether", multiplier(percent));
    }

    private static String multiplier(int percent) {
        return String.format(Locale.ROOT, "%.2f", percent / 100.0).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(IN_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, CondenserArrayMenu.exhaustFluid(), CondenserArrayMenu.exhaustFluid().getFluidType().getDescription(),
                    menu.getExhaust(), menu.getTankCapacity());
        } else if (isHovering(OUT_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, Fluids.WATER, Component.translatable("gui.arcforge.water"), menu.getWater(), menu.getTankCapacity());
        } else if (isHovering(TEXT_X, ICE_Y - 1, VALUE_RIGHT - TEXT_X, 10, mouseX, mouseY) && menu.getMultiplierPercent() != 100) {
            int percent = menu.getMultiplierPercent();
            lines.add(Component.translatable(percent > 100 ? "gui.arcforge.condenser.cold.hint" : "gui.arcforge.condenser.nether.hint", multiplier(percent)));
        } else if (isHovering(LED_X, STATUS_Y - 1, VALUE_RIGHT - LED_X, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
            lines.add(Component.translatable("gui.arcforge.mb_per_tick_gain", ArcforgeGui.grouped(menu.getCondensed())).withStyle(ChatFormatting.GRAY));
        }
    }
}
