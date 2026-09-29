/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.FermenterMenu;
import net.zagdrath.arcforge.recipe.MachineRecipes;

// Layout follows the handoff's gui_layouts.json: energy, the water tank, the crop slot, the arrow, the Ethanol tank
// and the byproduct slot, with the status under them.
public class FermenterScreen extends MachineScreen<FermenterMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int WATER_X = 25, ETHANOL_X = 113, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int PROGRESS_X = 72, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 43, LED_Y = 58;
    private static final int STATUS_X = 51, STATUS_Y = 58;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");

    public FermenterScreen(FermenterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "fermenter", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawFluidTank(graphics, Fluids.WATER, menu.getWater(), menu.getWaterCapacity(), tankGauge, tankGauge, x, y, WATER_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, menu.getEthanolFluid(), menu.getEthanol(), menu.getEthanolCapacity(), tankGauge, tankGauge,
                x, y, ETHANOL_X, TANK_Y, TANK_W, TANK_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(WATER_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, Fluids.WATER, Component.translatable("block.minecraft.water"), menu.getWater(), menu.getWaterCapacity());
        } else if (isHovering(ETHANOL_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getEthanolFluid(), Component.translatable("fluid_type.arcforge.ethanol"), menu.getEthanol(), menu.getEthanolCapacity());
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        }
    }

    // What the crop in the input slot ferments into.
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot == null || hoveredSlot.getContainerSlot() != 0 || hoveredSlot.container == minecraft.player.getInventory()) {
            return lines;
        }
        return MachineRecipes.fermenting(null, stack).map(holder -> {
            List<Component> withResult = new ArrayList<>(lines);
            var result = holder.value().result();
            withResult.add(Component.translatable("gui.arcforge.arc_melter.melts_into", ArcforgeGui.grouped(result.amount()),
                    result.fluid().value().getFluidType().getDescription()).withStyle(ChatFormatting.GOLD));
            return (List<Component>) withResult;
        }).orElse(lines);
    }
}
