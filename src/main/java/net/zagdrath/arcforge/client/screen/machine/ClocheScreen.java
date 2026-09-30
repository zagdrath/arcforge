/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.gui.tab.SideTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.ClocheMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// The Glass Cloche, Grow Chamber and Hydroponic Cell: the tank (water, or Nutrient Solution), the seed, soil and
// fertilizer slots, the growth arrow, the four output slots, and on the powered two the FE gauge (and the Hydroponic
// Cell's Carbon Dioxide tank). The Glass Cloche's faces are fixed, so it has no Sides tab, and no Upgrades tab.
// Positions are relative to leftPos/topPos.
public class ClocheScreen extends MachineScreen<ClocheMenu> {
    private static final int TANK_X = 9, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int PROGRESS_X = 72, PROGRESS_Y = 30, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int GAS_X = 139, GAS_Y = 19, GAS_W = 12, GAS_H = 50;
    private static final int ENERGY_X = 157, ENERGY_Y = 19;
    private static final int LED_X = 30, LED_Y = 61;
    private static final int STATUS_X = 38, STATUS_Y = 60, STATUS_W = 96;

    private final ClocheBlockEntity.Kind kind;
    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier fluidFill = sprite("fluid_fill");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier progress = sprite("progress");
    private final Identifier ghostSeed = sprite("ghost_seed");
    private final Identifier ghostSoil = sprite("ghost_soil");
    private final Identifier ghostFertilizer = sprite("ghost_fertilizer");

    public ClocheScreen(ClocheMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.kind().id(), tabs(menu), menu.kind().powered(), 166, menu.kind().powered());
        this.kind = menu.kind();
        if (kind.powered()) {
            enableAutoEject();
        }
    }

    private static List<SideTab> tabs(ClocheMenu menu) {
        return menu.kind().powered() ? List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)) : List.of();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, menu.getFluid(), menu.getFluidAmount(), menu.getFluidCapacity(), fluidFill, tankGauge,
                x, y, TANK_X, TANK_Y, TANK_W, TANK_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        if (kind.powered()) {
            drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getEnergyCapacity());
        }
        if (kind.hydroponic()) {
            drawFluidTank(graphics, menu.getGasAmount() > 0 ? ModFluids.CARBON_DIOXIDE.get() : Fluids.EMPTY, menu.getGasAmount(), menu.getGasCapacity(),
                    sprite("gas_fill"), sprite("gas_gauge"), x, y, GAS_X, GAS_Y, GAS_W, GAS_H);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
        ghost(graphics, ghostSeed, !menu.getSlotFor(ClocheBlockEntity.SLOT_SEED).hasItem(), ClocheMenu.SEED_X, ClocheMenu.SEED_Y);
        if (kind.hydroponic()) {
            ghost(graphics, ghostFertilizer, !menu.getSlotFor(ClocheBlockEntity.SLOT_FERTILIZER).hasItem(), ClocheMenu.SOIL_X, ClocheMenu.SOIL_Y);
        } else {
            ghost(graphics, ghostSoil, !menu.getSlotFor(ClocheBlockEntity.SLOT_SOIL).hasItem(), ClocheMenu.SOIL_X, ClocheMenu.SOIL_Y);
            ghost(graphics, ghostFertilizer, !menu.getSlotFor(ClocheBlockEntity.SLOT_FERTILIZER).hasItem(), ClocheMenu.FERTILIZER_X, ClocheMenu.FERTILIZER_Y);
        }
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(TANK_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            Component empty = kind.hydroponic() ? ModFluids.NUTRIENT_SOLUTION_TYPE.get().getDescription() : Fluids.WATER.getFluidType().getDescription();
            addFluidTooltip(lines, menu.getFluid(), empty, menu.getFluidAmount(), menu.getFluidCapacity());
        } else if (kind.hydroponic() && isHovering(GAS_X - 1, GAS_Y - 1, GAS_W + 2, GAS_H + 2, mouseX, mouseY)) {
            lines.add(ModFluids.CARBON_DIOXIDE_TYPE.get().getDescription());
            lines.add(Component.translatable("gui.arcforge.mb_stored", ArcforgeGui.grouped(menu.getGasAmount()), ArcforgeGui.grouped(menu.getGasCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.cloche.co2_bonus").withStyle(ChatFormatting.DARK_GRAY));
        } else if (kind.powered() && isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getEnergyCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            int percent = (int) Math.floor(100.0 * menu.getProgress() / menu.getTotal());
            lines.add(Component.translatable("gui.arcforge.cloche.growth", percent));
            if (menu.getRate() > 0) {
                lines.add(Component.translatable("gui.arcforge.cloche.rate", String.format(Locale.ROOT, "%.2f", menu.getRate()))
                        .withStyle(ChatFormatting.GRAY));
            }
            if (menu.getFertilizer() > 0) {
                lines.add(Component.translatable("gui.arcforge.cloche.fertilizer", menu.getFertilizer()).withStyle(ChatFormatting.GREEN));
            }
        }
    }
}
