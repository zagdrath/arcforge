/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.menu.multiblock.BatteryArrayMenu;

// A tall FE gauge on the left (cropped from the bottom, like the Energy Cell's), and the house screen with what it holds,
// its capacity, the FE that came in and went out last tick, its transfer limit, its cells and regulators, and its status.
// All positions are relative to leftPos/topPos; the background is 176×186, the player inventory 104 down.
public class BatteryArrayScreen extends MachineScreen<BatteryArrayMenu> {
    private static final int HEIGHT = 186;
    private static final int RECESS_X = 8, RECESS_Y = 17, RECESS_W = 16, RECESS_H = 72;
    private static final int FILL_X = 9, FILL_Y = 18, FILL_W = 14, FILL_H = 70;
    private static final int LEFT = 34, RIGHT = 164;
    private static final int STORED_Y = 21, CAPACITY_Y = 31, INPUT_Y = 41, OUTPUT_Y = 51, LIMIT_Y = 61, PARTS_Y = 71;
    private static final int LED_X = 34, LED_Y = 81, STATUS_X = 42, STATUS_Y = 81, STATUS_W = 122;
    private static final int IN_COLOR = 0xFF55FF55, OUT_COLOR = 0xFFFF5555;

    private final Identifier energyFill = sprite("energy_fill");

    public BatteryArrayScreen(BatteryArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "battery_array", List.of(), false, HEIGHT);
    }

    // The fill, cropped from the bottom so the segment lines stay put.
    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        long capacity = menu.getCapacity();
        int height = capacity <= 0 ? 0 : (int) Math.min(FILL_H, Math.round((double) menu.getStored() * FILL_H / capacity));
        if (height <= 0 && menu.getStored() > 0) {
            height = 1;
        }
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, energyFill, FILL_W, FILL_H, 0, FILL_H - height,
                    x + FILL_X, y + FILL_Y + FILL_H - height, FILL_W, height);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    private void row(GuiGraphicsExtractor graphics, String label, Component value, int y, int color) {
        graphics.text(font, Component.translatable("gui.arcforge.battery_array." + label), LEFT, y, ArcforgeGui.LABEL, false);
        textRight(graphics, value, RIGHT, y, color);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        row(graphics, "stored", Component.translatable("gui.arcforge.fe_amount", ArcforgeGui.compact(menu.getStored())), STORED_Y, ArcforgeGui.WHITE);
        row(graphics, "capacity", Component.translatable("gui.arcforge.fe_amount", ArcforgeGui.compact(menu.getCapacity())), CAPACITY_Y, ArcforgeGui.TEXT);
        row(graphics, "input", Component.translatable("gui.arcforge.fe_per_tick_gain", ArcforgeGui.compact(menu.getInput())), INPUT_Y, IN_COLOR);
        row(graphics, "output", Component.translatable("gui.arcforge.fe_per_tick_loss", ArcforgeGui.compact(menu.getOutput())), OUTPUT_Y, OUT_COLOR);
        row(graphics, "limit", Component.translatable("gui.arcforge.fe_per_tick", ArcforgeGui.compact(menu.getTransfer())), LIMIT_Y, ArcforgeGui.ACCENT);
        graphics.text(font, clipped(Component.translatable("gui.arcforge.battery_array.parts", menu.getCells(), menu.getRegulators()), RIGHT - LEFT),
                LEFT, PARTS_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(RECESS_X, RECESS_Y, RECESS_W, RECESS_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getStored()), ArcforgeGui.grouped(menu.getCapacity())));
            if (menu.getCapacity() > 0) {
                lines.add(Component.literal(String.format(Locale.ROOT, "%.1f%%", 100.0 * menu.getStored() / menu.getCapacity()))
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (isHovering(LEFT, STORED_Y - 1, RIGHT - LEFT, 20, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getStored()), ArcforgeGui.grouped(menu.getCapacity())));
        } else if (isHovering(LEFT, INPUT_Y - 1, RIGHT - LEFT, 20, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_gain", ArcforgeGui.grouped(menu.getInput())).withStyle(ChatFormatting.GREEN));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", ArcforgeGui.grouped(menu.getOutput())).withStyle(ChatFormatting.RED));
        } else if (isHovering(LEFT, LIMIT_Y - 1, RIGHT - LEFT, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.battery_array.limit_hint", ArcforgeGui.grouped(menu.getTransfer())));
            lines.add(Component.translatable("gui.arcforge.battery_array.redstone_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
