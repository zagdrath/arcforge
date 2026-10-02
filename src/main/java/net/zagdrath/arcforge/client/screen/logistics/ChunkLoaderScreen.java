/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.logistics;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.logistics.ChunkLoaderBlockEntity;
import net.zagdrath.arcforge.chunkloading.ChunkLoaderStatus;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.SecurityTab;
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.menu.logistics.ChunkLoaderMenu;

// A Chunk Loader's screen (176x122, no inventory): a 5x5 map of the chunks round it (its own in the middle; the chunks
// in its radius filled status cyan while it loads them, outlined while it doesn't), the radius with - and + buttons,
// its status, how many chunks it and its owner load against the limit, and its FE when FE is required. While the screen
// is open the area is outlined in the world too (RangeOutlineRenderer). A Security tab on the right.
public class ChunkLoaderScreen extends AbstractContainerScreen<ChunkLoaderMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/chunk_loader.png");
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final Identifier BUTTON_DISABLED = ArcforgeGui.widget("button_disabled");
    private static final int WIDTH = 176, HEIGHT = 122;
    private static final int MAP_X = 9, MAP_Y = 20, CELL = 12, PITCH = 13, MAP_CELLS = 5;
    private static final int TEXT_X = 80, RADIUS_Y = 21, BUTTONS_Y = 31, MINUS_X = 80, PLUS_X = 104, BUTTON_SIZE = 20;
    private static final int LED_X = 80, STATUS_Y = 58, LOADED_Y = 69, OWNER_Y = 79;
    private static final int ENERGY_LABEL_Y = 93, BAR_X = 10, BAR_Y = 103, BAR_W = 156, BAR_H = 4, ENERGY_TEXT_Y = 107;
    private static final int CELL_IDLE = 0xFF2F6E68, CELL_ACTIVE = 0xFF5FD4C4, CELL_HOME = 0xFFB8F2EA, CELL_OFF = 0xFF2A2A2A;

    private final SideTabPanel tabs = new SideTabPanel();

    public ChunkLoaderScreen(ChunkLoaderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        tabs.add(new SecurityTab(() -> menu.containerId, this::sendButton));
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        titleLabelY = 6;
        tabs.layout(leftPos, topPos);
    }

    private boolean energyRequired() {
        return menu.value(ChunkLoaderMenu.DATA_COST) > 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        int radius = menu.getRadius();
        boolean active = menu.getStatus() == ChunkLoaderStatus.ACTIVE;
        int centre = MAP_CELLS / 2;
        for (int row = 0; row < MAP_CELLS; row++) {
            for (int col = 0; col < MAP_CELLS; col++) {
                int cx = x + MAP_X + col * PITCH, cy = y + MAP_Y + row * PITCH;
                boolean inside = Math.abs(col - centre) <= radius && Math.abs(row - centre) <= radius;
                boolean home = col == centre && row == centre;
                if (inside && active) {
                    graphics.fill(cx, cy, cx + CELL, cy + CELL, home ? CELL_HOME : CELL_ACTIVE);
                } else if (inside) {
                    graphics.fill(cx, cy, cx + CELL, cy + CELL, CELL_OFF);
                    graphics.outline(cx, cy, CELL, CELL, CELL_IDLE);
                } else {
                    graphics.fill(cx, cy, cx + CELL, cy + CELL, CELL_OFF);
                }
                if (home) {
                    // The loader itself: a dot in its chunk.
                    graphics.fill(cx + 5, cy + 5, cx + 7, cy + 7, inside && active ? 0xFF16181B : CELL_ACTIVE);
                }
            }
        }
        button(graphics, mouseX, mouseY, MINUS_X, "minus", radius > 0);
        button(graphics, mouseX, mouseY, PLUS_X, "plus", radius < ChunkLoaderBlockEntity.MAX_RADIUS);
        String led = switch (menu.getStatus()) {
            case ACTIVE -> "led_running";
            case DISABLED -> "led_off";
            default -> "led_blocked";
        };
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.sprite("container/vacuum_collector/" + led), x + LED_X, y + STATUS_Y + 1, 6, 6);
        if (energyRequired()) {
            int capacity = Math.max(1, menu.value(ChunkLoaderMenu.DATA_CAPACITY));
            int filled = (int) Math.min(BAR_W, Math.round((double) menu.value(ChunkLoaderMenu.DATA_ENERGY) * BAR_W / capacity));
            if (filled > 0) {
                graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + filled, y + BAR_Y + BAR_H, ArcforgeGui.ACCENT);
            }
        }
        tabs.render(graphics, font, leftPos, topPos, mouseX, mouseY);
    }

    private void button(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int bx, String icon, boolean enabled) {
        int x = leftPos + bx, y = topPos + BUTTONS_Y;
        boolean hovered = ArcforgeGui.isInside(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled ? BUTTON_DISABLED : hovered ? BUTTON_HOVER : BUTTON, x, y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.sprite("container/vacuum_collector/" + icon), x + 2, y + 2, 16, 16);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        int radius = menu.getRadius();
        int side = 2 * radius + 1;
        graphics.text(font, Component.translatable("gui.arcforge.chunk_loader.radius", radius, side, side), TEXT_X, RADIUS_Y, ArcforgeGui.TEXT, false);
        graphics.text(font, clipped(menu.getStatus().description(), 168 - LED_X - 9), LED_X + 9, STATUS_Y, ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable("gui.arcforge.chunk_loader.loaded", menu.value(ChunkLoaderMenu.DATA_LOADED)), TEXT_X, LOADED_Y,
                ArcforgeGui.LABEL, false);
        int total = menu.value(ChunkLoaderMenu.DATA_OWNER_TOTAL), limit = menu.value(ChunkLoaderMenu.DATA_LIMIT);
        graphics.text(font, Component.translatable("gui.arcforge.chunk_loader.owner_total", total, limit), TEXT_X, OWNER_Y,
                total > limit || menu.getStatus() == ChunkLoaderStatus.LIMIT ? 0xFFFF6A5A : ArcforgeGui.LABEL, false);
        if (energyRequired()) {
            graphics.text(font, Component.translatable("gui.arcforge.chunk_loader.energy", ArcforgeGui.grouped(menu.value(ChunkLoaderMenu.DATA_COST))),
                    BAR_X, ENERGY_LABEL_Y, ArcforgeGui.LABEL, false);
            graphics.text(font, Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.value(ChunkLoaderMenu.DATA_ENERGY)),
                    ArcforgeGui.grouped(menu.value(ChunkLoaderMenu.DATA_CAPACITY))), BAR_X, ENERGY_TEXT_Y + 2, ArcforgeGui.TEXT, false);
        } else {
            graphics.text(font, Component.translatable("gui.arcforge.chunk_loader.no_energy"), BAR_X, ENERGY_LABEL_Y, ArcforgeGui.LABEL, false);
        }
    }

    private Component clipped(Component text, int width) {
        String full = text.getString();
        if (font.width(full) <= width) {
            return text;
        }
        return Component.literal(font.plainSubstrByWidth(full, Math.max(0, width - font.width("…"))) + "…");
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + MINUS_X, topPos + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            lines.add(Component.translatable("gui.arcforge.chunk_loader.radius_minus"));
        } else if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + PLUS_X, topPos + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            lines.add(Component.translatable("gui.arcforge.chunk_loader.radius_plus"));
        } else if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + MAP_X, topPos + MAP_Y, MAP_CELLS * PITCH, MAP_CELLS * PITCH)) {
            lines.add(Component.translatable("gui.arcforge.chunk_loader.map"));
            lines.add(Component.translatable("gui.arcforge.chunk_loader.map.tooltip").withStyle(ChatFormatting.GRAY));
        } else if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + LED_X, topPos + STATUS_Y, 88, 9)) {
            lines.add(menu.getStatus().description());
            lines.add(Component.translatable("gui.arcforge.chunk_loader.status." + menu.getStatus().getName() + ".tooltip").withStyle(ChatFormatting.GRAY));
        } else {
            tabs.addTooltip(lines, mouseX, mouseY);
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int radius = menu.getRadius();
            if (radius > 0 && ArcforgeGui.isInside(event.x(), event.y(), leftPos + MINUS_X, topPos + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                sendButton(ChunkLoaderMenu.BUTTON_RADIUS_MINUS);
                ArcforgeGui.playClickSound();
                return true;
            }
            if (radius < ChunkLoaderBlockEntity.MAX_RADIUS && ArcforgeGui.isInside(event.x(), event.y(), leftPos + PLUS_X, topPos + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                sendButton(ChunkLoaderMenu.BUTTON_RADIUS_PLUS);
                ArcforgeGui.playClickSound();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop) && !tabs.isInside(mouseX, mouseY);
    }

    public List<Rect2i> getExtraAreas() {
        return tabs.getAreas();
    }
}
