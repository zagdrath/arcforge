/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.menu.machine.ArcQuarryMenu;

// Layout follows gui_layouts.json "main" (176x214): a status screen (what it's doing or why it's paused, the area,
// the targets and how many are mined), the replace slot, Start/Stop, Reset and Settings, and the 27-slot buffer.
public class ArcQuarryScreen extends MachineScreen<ArcQuarryMenu> {
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    // The gauge ends level with the status screen, above the buffer.
    private static final int ENERGY_X = 9, ENERGY_Y = 17, ENERGY_H = 44;
    private static final int LED_X = 27, LED_Y = 21;
    private static final int TEXT_X = 27, STATUS_X = 35, STATUS_Y = 20, AREA_Y = 31, TARGETS_Y = 42, MINED_Y = 52, TEXT_W = 90;
    private static final int START_X = 146, START_Y = 16, RESET_X = 146, RESET_Y = 40, SETTINGS_X = 123, SETTINGS_Y = 40, BUTTON_SIZE = 20;
    // The empty replace slot shows a faint cobblestone.
    private static final int GHOST_WASH = 0xB08B8B8B;

    private final Identifier energyBar = sprite("energy_bar");
    private int mouseX, mouseY;

    public ArcQuarryScreen(ArcQuarryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "arc_quarry", List.of(EnergyTab.usage(menu::getEnergy, () -> 0)), true, 214);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    private @Nullable ArcQuarryBlockEntity quarry() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.getPos()) instanceof ArcQuarryBlockEntity quarry
                ? quarry : null;
    }

    private boolean running() {
        ArcQuarryBlockEntity.State state = menu.getState();
        return state == ArcQuarryBlockEntity.State.MINING || state == ArcQuarryBlockEntity.State.SCANNING;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, ENERGY_H, menu.getEnergy(), menu.getCapacity());
        drawLed(graphics, x, y, LED_X, LED_Y);
        if (!menu.getSlot(0).hasItem()) {
            int gx = x + ArcQuarryMenu.REPLACE_X;
            int gy = y + ArcQuarryMenu.REPLACE_Y;
            graphics.fakeItem(new ItemStack(Items.COBBLESTONE), gx, gy);
            graphics.fill(gx, gy, gx + 16, gy + 16, GHOST_WASH);
        }
        button(graphics, x, y, START_X, START_Y, running() ? "stop" : "start");
        button(graphics, x, y, RESET_X, RESET_Y, "reset");
        button(graphics, x, y, SETTINGS_X, SETTINGS_Y, "config");
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int bx, int by, String icon) {
        boolean hovered = isHovering(bx, by, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? BUTTON_HOVER : BUTTON, x + bx, y + by, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(icon), x + bx + 2, y + by + 2, 16, 16);
    }

    // What it's doing, or why it's paused.
    private Component statusLine() {
        MachineStatus status = menu.getStatus();
        if (menu.getState() == ArcQuarryBlockEntity.State.SCANNING) {
            return Component.translatable("gui.arcforge.quarry.scanning", menu.getScanPercent());
        }
        ArcQuarryBlockEntity quarry = quarry();
        if (status == MachineStatus.OUT_OF_REPLACE) {
            return Component.translatable("gui.arcforge.status.out_of_replace",
                    quarry != null ? quarry.getLastReplace().getName() : net.minecraft.world.level.block.Blocks.COBBLESTONE.getName());
        }
        return status.getDescription();
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(statusLine(), TEXT_W - 6), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        ArcQuarryBlockEntity quarry = quarry();
        long area = quarry != null ? quarry.getSettings().areaVolume() : 0;
        graphics.text(font, Component.translatable("gui.arcforge.quarry.area", ArcforgeGui.grouped(area)), TEXT_X, AREA_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.quarry.targets", ArcforgeGui.grouped(menu.getTargets())), TEXT_X, TARGETS_Y,
                ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.quarry.mined", ArcforgeGui.grouped(menu.getMined())), TEXT_X, MINED_Y,
                ArcforgeGui.ACCENT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && minecraft.gameMode != null) {
            int button = -1;
            if (isHovering(START_X, START_Y, BUTTON_SIZE, BUTTON_SIZE, event.x(), event.y())) {
                button = ArcQuarryMenu.BUTTON_START_STOP;
            } else if (isHovering(RESET_X, RESET_Y, BUTTON_SIZE, BUTTON_SIZE, event.x(), event.y())) {
                button = ArcQuarryMenu.BUTTON_RESET;
            } else if (isHovering(SETTINGS_X, SETTINGS_Y, BUTTON_SIZE, BUTTON_SIZE, event.x(), event.y())) {
                button = ArcQuarryMenu.BUTTON_SETTINGS;
            }
            if (button >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
                ArcforgeGui.playClickSound();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, ENERGY_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (isHovering(START_X, START_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            lines.add(Component.translatable(running() ? "gui.arcforge.quarry.stop" : "gui.arcforge.quarry.start"));
            return;
        }
        if (isHovering(RESET_X, RESET_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.quarry.reset"));
            return;
        }
        if (isHovering(SETTINGS_X, SETTINGS_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.quarry.config"));
            return;
        }
        ArcQuarryBlockEntity quarry = quarry();
        if (quarry != null && isHovering(TEXT_X, AREA_Y - 1, TEXT_W, font.lineHeight + 1, mouseX, mouseY)) {
            QuarrySettings settings = quarry.getSettings();
            lines.add(Component.translatable("gui.arcforge.quarry.area.hint", settings.side(), settings.side(), settings.maxY() - settings.minY() + 1));
            return;
        }
        if (!menu.getSlot(0).hasItem() && isHovering(ArcQuarryMenu.REPLACE_X - 1, ArcQuarryMenu.REPLACE_Y - 1, 18, 18, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.quarry.replace_slot"));
        }
    }
}
