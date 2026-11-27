/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.fusion;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.blockentity.fusion.RemoteHandlingArmBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.ArcCrusherScreen;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.fusion.RemoteHandlingArmMenu;

// The Remote Handling Arm's GUI (textures/gui/container/remote_handling_arm.png): the FE gauge, the spare parts (blue),
// the arrow, the Activated Components taken out (orange), the status and the count of parts replaced.
public class RemoteHandlingArmScreen extends MachineScreen<RemoteHandlingArmMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int PROGRESS_X = 89, PROGRESS_Y = 37, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 30, LED_Y = 76;
    private static final int STATUS_X = 38, STATUS_Y = 76, STATUS_W = 76;
    private static final int REPLACED_RIGHT = 168;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");

    public RemoteHandlingArmScreen(RemoteHandlingArmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "remote_handling_arm", List.of(EnergyTab.usage(menu::getEnergy, () -> RemoteHandlingArmBlockEntity.ENERGY_PER_TICK)),
                false, RemoteHandlingArmMenu.HEIGHT);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getEnergyCapacity());
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), RemoteHandlingArmBlockEntity.SERVICE_TICKS);
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        Component replaced = Component.translatable("gui.arcforge.remote_handling_arm.replaced", menu.getReplaced());
        graphics.text(font, replaced, REPLACED_RIGHT - font.width(replaced), STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getEnergyCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", RemoteHandlingArmBlockEntity.ENERGY_PER_TICK).withStyle(ChatFormatting.RED));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.remote_handling_arm.hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
