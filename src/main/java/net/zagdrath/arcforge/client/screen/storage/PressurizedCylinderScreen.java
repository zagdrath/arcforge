/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.storage;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.RedstoneTab;
import net.zagdrath.arcforge.client.gui.tab.SideConfigTab;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.storage.PressurizedCylinderMenu;
import net.zagdrath.arcforge.steam.SteamGrade;

// Layout follows pressurized_cylinder_gui_layout.json.
public class PressurizedCylinderScreen extends StorageScreen<PressurizedCylinderMenu> {
    private static final int GAUGE_X = 30, GAUGE_Y = 16, GAUGE_W = 22, GAUGE_H = 56;
    private static final int FILL_X = 31, FILL_Y = 17, FILL_W = 20, FILL_H = 54;
    private static final int TEXT_X = 58, TEXT_MAX_WIDTH = 106;
    private static final int GAS_Y = 21, STORED_LABEL_Y = 35, AMOUNT_Y = 45, CAPACITY_Y = 57;
    private static final int EMPTY_COLOR = 0xFF707070;

    private final Identifier gauge = sprite("gauge");
    private final Identifier fallback = sprite("gas_fill");

    // "Hardened Cylinder": the full block name doesn't fit the panel.
    public PressurizedCylinderScreen(PressurizedCylinderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("container.arcforge.cylinder_short", menu.getTier().getDisplayName()), "pressurized_cylinder");
        tabs.add(new RedstoneTab(menu::getRedstoneMode, mode -> sendButton(MachineMenuButtons.redstoneButtonId(mode))))
                .add(new SideConfigTab(menu::getSideMode,
                        (side, action) -> sendButton(MachineMenuButtons.sideButtonId(side, action)),
                        () -> sendButton(MachineMenuButtons.CLEAR_SIDES))
                        .withAutoEject(menu::isAutoEject, () -> sendButton(MachineMenuButtons.TOGGLE_AUTO_EJECT)));
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        FluidStack gas = menu.getGas();
        int fill = scaledRound(menu.getAmount(), menu.getCapacity(), FILL_H);
        ArcforgeGui.drawFluid(graphics, gas.getFluid(), gas.isEmpty() ? 0 : fill, fallback, x + FILL_X, y + FILL_Y + FILL_H, FILL_W, FILL_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, gauge, x + FILL_X, y + FILL_Y, FILL_W, FILL_H);
    }

    @Override
    protected void drawInfo(GuiGraphicsExtractor graphics) {
        FluidStack gas = menu.getGas();
        if (gas.isEmpty()) {
            graphics.text(font, Component.translatable("gui.arcforge.empty"), TEXT_X, GAS_Y, EMPTY_COLOR, false);
        } else {
            // Steam shows in its grade colour.
            SteamGrade grade = SteamGrade.of(gas.getFluid());
            String name = gas.getHoverName().getString();
            if (font.width(name) > TEXT_MAX_WIDTH) {
                name = font.plainSubstrByWidth(name, TEXT_MAX_WIDTH - font.width("…")) + "…";
            }
            graphics.text(font, name, TEXT_X, GAS_Y, grade != null ? grade.guiColor() : ArcforgeGui.TEXT, false);
        }
        graphics.text(font, Component.translatable("gui.arcforge.stored"), TEXT_X, STORED_LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.buckets", ArcforgeGui.buckets(menu.getAmount())), TEXT_X, AMOUNT_Y, ArcforgeGui.WHITE, false);
        graphics.text(font, Component.translatable("gui.arcforge.buckets_capacity", ArcforgeGui.buckets(menu.getCapacity())), TEXT_X, CAPACITY_Y, ArcforgeGui.LABEL, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
            FluidStack gas = menu.getGas();
            lines.add(gas.isEmpty() ? Component.translatable("gui.arcforge.empty") : gas.getHoverName());
            lines.add(Component.translatable("gui.arcforge.mb_stored", ArcforgeGui.grouped(menu.getAmount()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
