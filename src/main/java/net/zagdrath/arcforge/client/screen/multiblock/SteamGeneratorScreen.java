/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.SteamGeneratorMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// The Steam Generator (176×166): the hot coolant and feedwater tanks on the left, the house screen in the middle (its
// tubes, the coolant it cools and the steam it makes a tick, the status), the cooled coolant and steam tanks on the
// right.
public class SteamGeneratorScreen extends MachineScreen<SteamGeneratorMenu> {
    private static final int TANK_Y = 17, TANK_W = 12, TANK_H = 52;
    private static final int[] TANK_X = { 9, 25, 139, 155 };
    private static final int LEFT = 42, RIGHT = 133, LED_X = 42, ROW = 10;

    private final Identifier gauge = sprite("tank_gauge");
    private final Identifier liquidFill = sprite("liquid_fill");
    private final Identifier gasFill = sprite("gas_fill");

    public SteamGeneratorScreen(SteamGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "steam_generator", List.of(), false);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("hot_leg_and_feedwater_input");
            case OUTPUT -> sideModeName("cold_leg_output");
            case STEAM -> sideModeName("steam_output");
            default -> super.sideModeName(mode);
        };
    }

    private static Fluid fluid(int id, Fluid fallback) {
        return id >= 0 ? BuiltInRegistries.FLUID.byId(id) : fallback;
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        int capacity = menu.get(SteamGeneratorMenu.DATA_CAPACITY);
        drawFluidTank(graphics, fluid(menu.get(SteamGeneratorMenu.DATA_HOT_FLUID), ModFluids.HOT_COOLANT.get()), menu.get(SteamGeneratorMenu.DATA_HOT),
                capacity, liquidFill, gauge, x, y, TANK_X[0], TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, Fluids.WATER, menu.get(SteamGeneratorMenu.DATA_FEED), capacity, liquidFill, gauge, x, y, TANK_X[1], TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, fluid(menu.get(SteamGeneratorMenu.DATA_COOLED_FLUID), Fluids.WATER), menu.get(SteamGeneratorMenu.DATA_COOLED), capacity,
                liquidFill, gauge, x, y, TANK_X[2], TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.HIGH_PRESSURE_STEAM.get(), menu.get(SteamGeneratorMenu.DATA_STEAM), menu.get(SteamGeneratorMenu.DATA_STEAM_CAPACITY),
                gasFill, gauge, x, y, TANK_X[3], TANK_Y, TANK_W, TANK_H);
        drawLed(graphics, x, y, LED_X, TANK_Y + 4 * ROW);
    }

    private void row(GuiGraphicsExtractor graphics, int line, String key, Component value) {
        int y = TANK_Y + 2 + line * ROW;
        graphics.text(font, Component.translatable("gui.arcforge.steam_generator." + key), LEFT, y, ArcforgeGui.LABEL, false);
        textRight(graphics, value, RIGHT, y, ArcforgeGui.TEXT);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        row(graphics, 0, "tubes", Component.literal(Integer.toString(menu.get(SteamGeneratorMenu.DATA_TUBES))));
        row(graphics, 1, "cooling", Component.literal(ArcforgeGui.grouped(menu.get(SteamGeneratorMenu.DATA_LAST_HOT)) + " mB/t"));
        row(graphics, 2, "steam", Component.literal(ArcforgeGui.grouped(menu.get(SteamGeneratorMenu.DATA_LAST_STEAM)) + " mB/t"));
        graphics.text(font, clipped(menu.getStatus().getDescription(), RIGHT - LED_X - 8), LED_X + 8, TANK_Y + 4 * ROW, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        int capacity = menu.get(SteamGeneratorMenu.DATA_CAPACITY);
        if (isHovering(TANK_X[0] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.get(SteamGeneratorMenu.DATA_HOT) > 0 ? fluid(menu.get(SteamGeneratorMenu.DATA_HOT_FLUID), Fluids.EMPTY) : Fluids.EMPTY,
                    Component.translatable("gui.arcforge.steam_generator.hot_tank"), menu.get(SteamGeneratorMenu.DATA_HOT), capacity);
        } else if (isHovering(TANK_X[1] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.get(SteamGeneratorMenu.DATA_FEED) > 0 ? Fluids.WATER : Fluids.EMPTY,
                    Component.translatable("gui.arcforge.steam_generator.feed_tank"), menu.get(SteamGeneratorMenu.DATA_FEED), capacity);
        } else if (isHovering(TANK_X[2] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.get(SteamGeneratorMenu.DATA_COOLED) > 0 ? fluid(menu.get(SteamGeneratorMenu.DATA_COOLED_FLUID), Fluids.EMPTY)
                    : Fluids.EMPTY, Component.translatable("gui.arcforge.steam_generator.cooled_tank"), menu.get(SteamGeneratorMenu.DATA_COOLED), capacity);
        } else if (isHovering(TANK_X[3] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.get(SteamGeneratorMenu.DATA_STEAM) > 0 ? ModFluids.HIGH_PRESSURE_STEAM.get() : Fluids.EMPTY,
                    Component.translatable("gui.arcforge.steam_generator.steam_tank"), menu.get(SteamGeneratorMenu.DATA_STEAM),
                    menu.get(SteamGeneratorMenu.DATA_STEAM_CAPACITY));
        } else if (isHovering(LEFT, TANK_Y + 1, RIGHT - LEFT, ROW, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.steam_generator.tubes_hint", ArcforgeGui.grouped(menu.get(SteamGeneratorMenu.DATA_RATE)))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
