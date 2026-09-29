/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.tool;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.ToolModules;
import net.zagdrath.arcforge.menu.tool.ArcToolMenu;

// An Arc Drill or Arc Saw's modules (layout in the handoff's gui_layouts.json): the charge, the tool itself, its
// four module slots (those past its tier locked) and a strip with a light and the name of each installed module;
// clicking a name switches it on or off.
public class ArcToolScreen extends AbstractContainerScreen<ArcToolMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/arc_tool.png");
    private static final Identifier ENERGY_BAR = sprite("energy_bar");
    private static final Identifier SLOT_LOCKED = sprite("slot_locked");
    private static final Identifier MODULE_ON = sprite("module_on");
    private static final Identifier MODULE_OFF = sprite("module_off");
    private static final int ENERGY_X = 9, ENERGY_Y = 19, ENERGY_W = 10, ENERGY_H = 50;
    private static final int TOOL_X = 26, TOOL_Y = 31;
    private static final int ENERGY_TEXT_X = 128, ENERGY_TEXT_Y = 35;
    private static final int STRIP_X = 26, STRIP_RIGHT = 23 + 146, LED_Y = 58, NAME_Y = 56, GAP = 8;

    // Each module name's box in the strip, for clicks and hover.
    private record Entry(int index, int x, int width) {}

    public ArcToolScreen(ArcToolMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "container/arc_tool/" + name);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        titleLabelY = 5;
        inventoryLabelY = 72;
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        ToolModules modules = menu.toolModules();
        int x = STRIP_X + 5;
        for (int index = 0; index < menu.usableSlots(); index++) {
            if (modules.slot(index).isEmpty()) {
                continue;
            }
            int width = font.width(modules.slot(index).get().displayName());
            if (x + width > STRIP_RIGHT) {
                break;
            }
            entries.add(new Entry(index, x, width));
            x += width + GAP + 5;
        }
        return entries;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        ItemStack tool = menu.tool();
        if (tool.getItem() instanceof ArcToolItem item) {
            int height = Math.round((float) ENERGY_H * ArcToolItem.energy(tool) / item.capacity());
            if (height > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_BAR, ENERGY_W, ENERGY_H, 0, ENERGY_H - height,
                        x + ENERGY_X, y + ENERGY_Y + ENERGY_H - height, ENERGY_W, height);
            }
            graphics.item(tool, x + TOOL_X, y + TOOL_Y);
        }
        for (int index = menu.usableSlots(); index < ToolModules.SIZE; index++) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_LOCKED, x + ArcToolMenu.SLOT_X[index], y + ArcToolMenu.SLOT_Y, 16, 16);
        }
        ToolModules modules = menu.toolModules();
        for (Entry entry : entries()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, modules.isOn(entry.index()) ? MODULE_ON : MODULE_OFF,
                    x + entry.x() - 5, y + LED_Y, 3, 3);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
        graphics.text(font, ArcforgeGui.compact(ArcToolItem.energy(menu.tool())) + " FE", ENERGY_TEXT_X, ENERGY_TEXT_Y, ArcforgeGui.LABEL, false);
        ToolModules modules = menu.toolModules();
        for (Entry entry : entries()) {
            boolean hovered = isHovering(entry.x(), NAME_Y - 1, entry.width(), font.lineHeight + 1, mouseX, mouseY);
            int color = modules.isOn(entry.index()) ? ArcforgeGui.ACCENT : ArcforgeGui.LABEL;
            graphics.text(font, modules.slot(entry.index()).get().displayName(), entry.x(), NAME_Y, hovered ? ArcforgeGui.TEXT : color, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, ENERGY_W + 2, ENERGY_H + 2, mouseX, mouseY) && menu.tool().getItem() instanceof ArcToolItem item) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("tooltip.arcforge.energy",
                    ArcforgeGui.grouped(ArcToolItem.energy(menu.tool())), ArcforgeGui.grouped(item.capacity())).withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && minecraft.gameMode != null) {
            for (Entry entry : entries()) {
                if (isHovering(entry.x(), NAME_Y - 1, entry.width(), font.lineHeight + 1, event.x(), event.y())) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ArcToolMenu.TOGGLE + entry.index());
                    ArcforgeGui.playClickSound();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
