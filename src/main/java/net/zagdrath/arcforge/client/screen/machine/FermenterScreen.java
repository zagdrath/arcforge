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
import net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.menu.machine.FermenterMenu;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.recipe.MachineRecipes;

// Energy and the water tank on the left; the crop over the additive (Dried Hops), the arrow and the byproduct in the
// middle with the status under them; the Ethanol and Carbon Dioxide tanks on the right.
public class FermenterScreen extends MachineScreen<FermenterMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int WATER_X = 25, ETHANOL_X = 141, CO2_X = 157, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int PROGRESS_X = 66, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 66, LED_Y = 58;
    private static final int STATUS_X = 74, STATUS_Y = 58, STATUS_W = 62;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier ghostHops = sprite("ghost_hops");

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
        drawFluidTank(graphics, ModFluids.CARBON_DIOXIDE.get(), menu.getCarbonDioxide(), menu.getCarbonDioxideCapacity(), tankGauge, tankGauge,
                x, y, CO2_X, TANK_Y, TANK_W, TANK_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        ghost(graphics, ghostHops, menu.getSlot(FermenterMenu.ADDITIVE_SLOT_INDEX).getItem().isEmpty(), FermenterMenu.ADDITIVE_X, FermenterMenu.ADDITIVE_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
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
        } else if (isHovering(CO2_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, ModFluids.CARBON_DIOXIDE.get(), Component.translatable("fluid_type.arcforge.carbon_dioxide"), menu.getCarbonDioxide(),
                    menu.getCarbonDioxideCapacity());
            lines.add(Component.translatable("gui.arcforge.fermenter.co2_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isHovering(FermenterMenu.ADDITIVE_X - 1, FermenterMenu.ADDITIVE_Y - 1, 18, 18, mouseX, mouseY)
                && menu.getSlot(FermenterMenu.ADDITIVE_SLOT_INDEX).getItem().isEmpty()) {
            lines.add(Component.translatable("gui.arcforge.fermenter.additive"));
            lines.add(Component.translatable("gui.arcforge.fermenter.additive_hint", bonusPercent(), ArcforgeConfig.FERMENTER_ADDITIVE_OPERATIONS.getAsInt())
                    .withStyle(ChatFormatting.GRAY));
            if (menu.getAdditiveLeft() > 0) {
                lines.add(Component.translatable("gui.arcforge.fermenter.additive_left", menu.getAdditiveLeft()).withStyle(ChatFormatting.GOLD));
            }
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        }
    }

    private static int bonusPercent() {
        return (int) Math.round(ArcforgeConfig.FERMENTER_ADDITIVE_BONUS.getAsDouble() * 100);
    }

    // What the crop in the input slot ferments into; what the Dried Hops in the additive slot do.
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot == null || hoveredSlot.container == minecraft.player.getInventory()) {
            return lines;
        }
        if (hoveredSlot.getContainerSlot() == FermenterBlockEntity.SLOT_ADDITIVE) {
            List<Component> withBonus = new ArrayList<>(lines);
            withBonus.add(Component.translatable("gui.arcforge.fermenter.additive_hint", bonusPercent(), ArcforgeConfig.FERMENTER_ADDITIVE_OPERATIONS.getAsInt())
                    .withStyle(ChatFormatting.GOLD));
            if (menu.getAdditiveLeft() > 0) {
                withBonus.add(Component.translatable("gui.arcforge.fermenter.additive_left", menu.getAdditiveLeft()).withStyle(ChatFormatting.GRAY));
            }
            return withBonus;
        }
        if (hoveredSlot.getContainerSlot() != FermenterBlockEntity.SLOT_INPUT) {
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
