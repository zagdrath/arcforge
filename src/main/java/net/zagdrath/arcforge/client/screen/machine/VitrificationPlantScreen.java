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
import net.zagdrath.arcforge.blockentity.machine.VitrificationPlantBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.VitrificationPlantMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// The Vitrification Plant's GUI (textures/gui/container/vitrification_plant.png): the FE gauge, the waste tank, the glass,
// the progress arrow and the log, the status line under them.
public class VitrificationPlantScreen extends MachineScreen<VitrificationPlantMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int TANK_X = 25, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int PROGRESS_X = 88, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 62, LED_Y = 62;
    private static final int STATUS_X = 70, STATUS_Y = 62;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier fluidFill = sprite("fluid_fill");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier progress = sprite("progress");
    private final Identifier ghostGlass = sprite("ghost_glass");

    public VitrificationPlantScreen(VitrificationPlantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "vitrification_plant", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getEnergyCapacity());
        drawFluidTank(graphics, menu.getFluid(), menu.getFluidAmount(), menu.getFluidCapacity(), fluidFill, tankGauge, x, y, TANK_X, TANK_Y, TANK_W, TANK_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        ghost(graphics, ghostGlass, !menu.getSlotFor(VitrificationPlantBlockEntity.SLOT_GLASS).hasItem(), VitrificationPlantMenu.GLASS_X,
                VitrificationPlantMenu.GLASS_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getEnergyCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(TANK_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getFluid(), ModFluids.HIGH_LEVEL_WASTE_TYPE.get().getDescription(), menu.getFluidAmount(), menu.getFluidCapacity());
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        }
    }
}
