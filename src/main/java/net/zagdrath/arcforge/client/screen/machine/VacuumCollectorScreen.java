/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.renderer.RangeOutlineRenderer;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.VacuumCollectorMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// Layout follows gui_layouts.json "vacuum_collector" (176x206): the filter slot and status at the top, the range
// buttons under them, the Liquid Experience tank at the right, and the 18-slot buffer.
public class VacuumCollectorScreen extends MachineScreen<VacuumCollectorMenu> {
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int LED_X = 52, LED_Y = 22;
    private static final int STATUS_X = 60, STATUS_Y = 22;
    private static final int MINUS_X = 30, PLUS_X = 96, SHOW_X = 122, BUTTONS_Y = 44, BUTTON_SIZE = 20;
    private static final int RANGE_Y = 50;
    // The Liquid Experience tank, at the right of the panel.
    private static final int XP_X = 161, XP_Y = 19, XP_W = 6, XP_H = 50;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier ghostFilter = sprite("ghost_filter");
    private final Identifier minus = sprite("minus");
    private final Identifier plus = sprite("plus");
    private final Identifier showRange = sprite("show_range");
    private final Identifier hideRange = sprite("hide_range");
    private int mouseX, mouseY;

    public VacuumCollectorScreen(VacuumCollectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "vacuum_collector", List.of(EnergyTab.usage(menu::getEnergy, () -> 0)), true, 206);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawFluidTank(graphics, ModFluids.LIQUID_EXPERIENCE.get(), menu.getXp(), menu.getXpCapacity(), sprite("xp_fill"), sprite("xp_gauge"),
                x, y, XP_X, XP_Y, XP_W, XP_H);
        ghost(graphics, ghostFilter, !menu.getSlot(0).hasItem(), VacuumCollectorMenu.FILTER_X, VacuumCollectorMenu.FILTER_Y);
        drawLed(graphics, x, y, LED_X, LED_Y);
        int hovered = buttonAt(mouseX, mouseY);
        button(graphics, x, y, MINUS_X, minus, hovered);
        button(graphics, x, y, PLUS_X, plus, hovered);
        button(graphics, x, y, SHOW_X, RangeOutlineRenderer.isGuiOutlineShown() ? hideRange : showRange, hovered);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int buttonX, Identifier icon, int hovered) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered == buttonX ? BUTTON_HOVER : BUTTON, x + buttonX, y + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + buttonX + 2, y + BUTTONS_Y + 2, 16, 16);
    }

    // The x of the button under the mouse, or -1.
    private int buttonAt(double mouseX, double mouseY) {
        for (int buttonX : new int[] { MINUS_X, PLUS_X, SHOW_X }) {
            if (isHovering(buttonX, BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
                return buttonX;
            }
        }
        return -1;
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), XP_X - 3 - STATUS_X), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        Component range = Component.translatable("gui.arcforge.vacuum.range", menu.getRange());
        int centre = (MINUS_X + BUTTON_SIZE + PLUS_X) / 2;
        graphics.text(font, range, centre - font.width(range) / 2, RANGE_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int buttonX = buttonAt(event.x(), event.y());
            if (buttonX == SHOW_X) {
                RangeOutlineRenderer.toggleGuiOutline();
                ArcforgeGui.playClickSound();
                return true;
            }
            if ((buttonX == MINUS_X || buttonX == PLUS_X) && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        buttonX == PLUS_X ? VacuumCollectorMenu.BUTTON_RANGE_PLUS : VacuumCollectorMenu.BUTTON_RANGE_MINUS);
                ArcforgeGui.playClickSound();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (isHovering(XP_X - 1, XP_Y - 1, XP_W + 2, XP_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, ModFluids.LIQUID_EXPERIENCE.get(), Component.translatable("fluid_type.arcforge.liquid_experience"),
                    menu.getXp(), menu.getXpCapacity());
            lines.add(Component.translatable("gui.arcforge.vacuum.xp_points", ArcforgeGui.grouped(menu.getXp() / LiquidExperience.MB_PER_POINT))
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        switch (buttonAt(mouseX, mouseY)) {
            case MINUS_X -> lines.add(Component.translatable("gui.arcforge.vacuum.range_minus"));
            case PLUS_X -> lines.add(Component.translatable("gui.arcforge.vacuum.range_plus"));
            case SHOW_X -> lines.add(Component.translatable(RangeOutlineRenderer.isGuiOutlineShown() ? "gui.arcforge.vacuum.hide_range" : "gui.arcforge.vacuum.show_range"));
            default -> {
                if (!menu.getSlot(0).hasItem() && isHovering(VacuumCollectorMenu.FILTER_X - 1, VacuumCollectorMenu.FILTER_Y - 1, 18, 18, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.vacuum.filter"));
                }
            }
        }
    }
}
