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
import net.zagdrath.arcforge.menu.multiblock.HeavyWaterTowerMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// The Heavy Water Tower (176×166): the FE gauge, three input tanks, the house screen (its trays, the progress of the
// operation they're all doing, what the last one made, the status) and the Heavy Water tank.
public class HeavyWaterTowerScreen extends MachineScreen<HeavyWaterTowerMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int TANK_Y = 17, TANK_W = 12, TANK_H = 52;
    private static final int[] INPUT_X = { 24, 40, 56 };
    private static final int OUTPUT_X = 155;
    private static final int LEFT = 74, RIGHT = 150, ROW = 10;
    private static final int PROGRESS_X = 74, PROGRESS_Y = 49, PROGRESS_W = 76;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier gauge = sprite("tank_gauge");
    private final Identifier liquidFill = sprite("liquid_fill");
    private final Identifier progressBar = sprite("progress");

    public HeavyWaterTowerScreen(HeavyWaterTowerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "heavy_water_tower", List.of(), false);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case ENERGY -> sideModeName("energy_input");
            default -> super.sideModeName(mode);
        };
    }

    private Fluid input(int i) {
        int id = menu.get(HeavyWaterTowerMenu.DATA_INPUT_FLUID + i);
        return id >= 0 ? BuiltInRegistries.FLUID.byId(id) : Fluids.EMPTY;
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.get(HeavyWaterTowerMenu.DATA_ENERGY), menu.get(HeavyWaterTowerMenu.DATA_ENERGY_CAPACITY));
        int capacity = menu.get(HeavyWaterTowerMenu.DATA_CAPACITY);
        for (int i = 0; i < 3; i++) {
            Fluid fluid = input(i);
            drawFluidTank(graphics, fluid == Fluids.EMPTY ? Fluids.WATER : fluid, menu.get(HeavyWaterTowerMenu.DATA_INPUT + i), capacity, liquidFill, gauge,
                    x, y, INPUT_X[i], TANK_Y, TANK_W, TANK_H);
        }
        drawFluidTank(graphics, ModFluids.HEAVY_WATER.get(), menu.get(HeavyWaterTowerMenu.DATA_OUTPUT), capacity, liquidFill, gauge, x, y, OUTPUT_X, TANK_Y,
                TANK_W, TANK_H);
        int time = menu.get(HeavyWaterTowerMenu.DATA_TIME);
        int width = time > 0 ? Math.min(PROGRESS_W, menu.get(HeavyWaterTowerMenu.DATA_PROGRESS) * PROGRESS_W / time) : 0;
        if (width > 0) {
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, progressBar, PROGRESS_W, 4, 0, 0, x + PROGRESS_X, y + PROGRESS_Y,
                    width, 4);
        }
        drawLed(graphics, x, y, LEFT, TANK_Y + 4 * ROW + 1);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.heavy_water_tower.trays"), LEFT, TANK_Y + 2, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.literal(Integer.toString(menu.get(HeavyWaterTowerMenu.DATA_TRAYS))), RIGHT, TANK_Y + 2, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.heavy_water_tower.made"), LEFT, TANK_Y + 2 + ROW, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.literal(menu.get(HeavyWaterTowerMenu.DATA_LAST_MADE) + " mB"), RIGHT, TANK_Y + 2 + ROW, ArcforgeGui.TEXT);
        graphics.text(font, clipped(menu.getStatus().getDescription(), RIGHT - LEFT - 8), LEFT + 8, TANK_Y + 4 * ROW + 1, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        int capacity = menu.get(HeavyWaterTowerMenu.DATA_CAPACITY);
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.get(HeavyWaterTowerMenu.DATA_ENERGY)),
                    ArcforgeGui.grouped(menu.get(HeavyWaterTowerMenu.DATA_ENERGY_CAPACITY))).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", ArcforgeGui.grouped(menu.get(HeavyWaterTowerMenu.DATA_ENERGY_USE)))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (isHovering(INPUT_X[i] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
                addFluidTooltip(lines, input(i), Component.translatable("gui.arcforge.heavy_water_tower.input_tank"), menu.get(HeavyWaterTowerMenu.DATA_INPUT + i),
                        capacity);
                return;
            }
        }
        if (isHovering(OUTPUT_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.get(HeavyWaterTowerMenu.DATA_OUTPUT) > 0 ? ModFluids.HEAVY_WATER.get() : Fluids.EMPTY,
                    Component.translatable("gui.arcforge.heavy_water_tower.output_tank"), menu.get(HeavyWaterTowerMenu.DATA_OUTPUT), capacity);
        } else if (isHovering(LEFT, TANK_Y + 1, RIGHT - LEFT, ROW, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.heavy_water_tower.trays_hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
