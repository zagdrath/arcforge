/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.fusion;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.blockentity.fusion.FusionPlantBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.fusion.FusionPlantMenu;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.Gases;

// The Cryogenic Plant, Cryogenic Distillation Column and Tritium Plant (176x166, textures/gui/container/<kind>.png):
// the FE gauge, the input tank, the house screen (its size, what the last step made, the column's progress, the
// status) and the output tanks. Every inset spans y 16..69 (insides 17..68).
public class FusionPlantScreen extends MachineScreen<FusionPlantMenu> {
    private static final int TANK_Y = 17, TANK_W = 12, TANK_H = 52;
    private static final int ENERGY_X = 9;
    private static final int INPUT_X = 24;
    private static final int LEFT = 42, ROW = 10;
    private static final int PROGRESS_Y = 49, PROGRESS_W = 76;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier gauge = sprite("tank_gauge");
    private final Identifier liquidFill = sprite("liquid_fill");
    private final Identifier gasFill = sprite("gas_fill");
    private final Identifier progressBar = sprite("progress");
    private final int[] outputX;
    private final int right;

    public FusionPlantScreen(FusionPlantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.kind().id(), List.of(EnergyTab.usage(() -> menu.get(FusionPlantMenu.DATA_ENERGY), () -> menu.get(FusionPlantMenu.DATA_ENERGY_USE))), false);
        this.outputX = switch (menu.kind()) {
            case CRYOGENIC_PLANT -> new int[] { 155 };
            case CRYOGENIC_COLUMN -> new int[] { 125, 140, 155 };
            case TRITIUM_PLANT -> new int[] { 110, 125, 140, 155 };
        };
        // The screen's inside ends 2 short of the first output tank's inset (its text 1 inside that).
        this.right = outputX[0] - 7;
    }

    private String key() {
        return "gui.arcforge." + menu.kind().id();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        if (mode == SideMode.ENERGY) {
            return sideModeName("energy_input");
        }
        int index = FusionPlantBlockEntity.OUTPUT_MODES.indexOf(mode);
        if (index >= 0 && index < outputX.length) {
            return Component.translatable(key() + ".output_" + index);
        }
        return super.sideModeName(mode);
    }

    private Fluid fluid(int id) {
        return id >= 0 ? BuiltInRegistries.FLUID.byId(id) : Fluids.EMPTY;
    }

    private Fluid input() {
        return fluid(menu.get(FusionPlantMenu.DATA_INPUT_FLUID));
    }

    private Fluid output(int i) {
        return fluid(menu.get(FusionPlantMenu.DATA_OUTPUT_FLUID + i));
    }

    // What an empty tank is drawn as (its gauge only) and named in its tooltip.
    private Fluid placeholder(int i) {
        return switch (menu.kind()) {
            case CRYOGENIC_PLANT -> ModFluids.LIQUID_NITROGEN.get();
            case CRYOGENIC_COLUMN -> ModFluids.NITROGEN.get();
            case TRITIUM_PLANT -> List.of(ModFluids.DEUTERIUM.get(), ModFluids.TRITIUM.get(), ModFluids.HELIUM_3.get(), ModFluids.HELIUM.get()).get(i);
        };
    }

    private void tank(GuiGraphicsExtractor graphics, int x, int y, int tankX, Fluid fluid, int amount, int capacity) {
        drawFluidTank(graphics, fluid, amount, capacity, Gases.isGas(fluid) ? gasFill : liquidFill, gauge, x, y, tankX, TANK_Y, TANK_W, TANK_H);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        int energy = scaled(menu.get(FusionPlantMenu.DATA_ENERGY), menu.get(FusionPlantMenu.DATA_ENERGY_CAPACITY), TANK_H);
        if (energy > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, energyBar, GAUGE_W, TANK_H, 0, TANK_H - energy, x + ENERGY_X, y + TANK_Y + TANK_H - energy,
                    GAUGE_W, energy);
        }
        int capacity = menu.get(FusionPlantMenu.DATA_CAPACITY);
        Fluid in = input();
        tank(graphics, x, y, INPUT_X, in == Fluids.EMPTY ? ModFluids.NITROGEN.get() : in, menu.get(FusionPlantMenu.DATA_INPUT), capacity);
        for (int i = 0; i < outputX.length; i++) {
            Fluid out = output(i);
            tank(graphics, x, y, outputX[i], out == Fluids.EMPTY ? placeholder(i) : out, menu.get(FusionPlantMenu.DATA_OUTPUT + i), capacity);
        }
        int time = menu.get(FusionPlantMenu.DATA_TIME);
        int width = time > 0 ? Math.min(PROGRESS_W, menu.get(FusionPlantMenu.DATA_PROGRESS) * PROGRESS_W / time) : 0;
        if (width > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, progressBar, PROGRESS_W, 4, 0, 0, x + LEFT, y + PROGRESS_Y, width, 4);
        }
        drawLed(graphics, x, y, LEFT, TANK_Y + 4 * ROW + 1);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable(key() + ".size"), LEFT, TANK_Y + 2, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.literal(Integer.toString(menu.get(FusionPlantMenu.DATA_SIZE))), right, TANK_Y + 2, ArcforgeGui.TEXT);
        Component made = Component.translatable("gui.arcforge.fusion_plant.made");
        Component amount = Component.literal(ArcforgeGui.grouped(menu.get(FusionPlantMenu.DATA_LAST_MADE)) + " mB");
        graphics.text(font, made, LEFT, TANK_Y + 2 + ROW, ArcforgeGui.LABEL, false);
        // The Tritium Plant's four output tanks leave its screen narrow: the amount goes on the row below when it
        // doesn't fit beside the label.
        boolean fits = font.width(made) + 4 + font.width(amount) <= right - LEFT;
        textRight(graphics, amount, right, TANK_Y + 2 + (fits ? ROW : 2 * ROW), ArcforgeGui.TEXT);
        graphics.text(font, clipped(menu.getStatus().getDescription(), right - LEFT - 8), LEFT + 8, TANK_Y + 4 * ROW + 1, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        int capacity = menu.get(FusionPlantMenu.DATA_CAPACITY);
        if (isHovering(ENERGY_X - 1, TANK_Y - 1, GAUGE_W + 2, TANK_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.get(FusionPlantMenu.DATA_ENERGY)),
                    ArcforgeGui.grouped(menu.get(FusionPlantMenu.DATA_ENERGY_CAPACITY))).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", ArcforgeGui.grouped(menu.get(FusionPlantMenu.DATA_ENERGY_USE)))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (isHovering(INPUT_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, input(), Component.translatable(key() + ".input"), menu.get(FusionPlantMenu.DATA_INPUT), capacity);
            if (menu.kind() == FusionPlantBlockEntity.Kind.CRYOGENIC_COLUMN && input() == Fluids.EMPTY) {
                lines.add(Component.translatable("gui.arcforge.cryogenic_column.air_hint").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        for (int i = 0; i < outputX.length; i++) {
            if (isHovering(outputX[i] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
                addFluidTooltip(lines, output(i), Component.translatable(key() + ".output_" + i), menu.get(FusionPlantMenu.DATA_OUTPUT + i), capacity);
                return;
            }
        }
        if (isHovering(LEFT, TANK_Y + 1, right - LEFT, ROW, mouseX, mouseY)) {
            lines.add(Component.translatable(key() + ".size_hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
