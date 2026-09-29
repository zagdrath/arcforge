/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.logistics;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.SecurityTab;
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.machine.meter.MeterKind;
import net.zagdrath.arcforge.machine.meter.MeterSettings;
import net.zagdrath.arcforge.menu.logistics.MeterMenu;

// A Meter's screen (the house layout, GUI_REVIEW section 1): no player inventory. The screen recess shows the rate, a
// bar of it against the cap with the threshold's tick, the threshold and the signal; below it the Above/Below buttons
// and the threshold field, which is sent when Enter is pressed or it loses focus. A Security tab on the right.
public class MeterScreen extends AbstractContainerScreen<MeterMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/meter.png");
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final Identifier BUTTON_PRESSED = ArcforgeGui.widget("button_pressed");
    private static final Identifier TICK = sprite("threshold_tick");
    private static final int WIDTH = 176, HEIGHT = 124;
    private static final int LABEL_X = 12, VALUE_RIGHT = 164;
    private static final int RATE_Y = 23, BAR_X = 12, BAR_Y = 34, BAR_W = 152, BAR_H = 4, TICK_Y = 33, THRESHOLD_Y = 42;
    private static final int LED_X = 12, LED_Y = 56, STATUS_X = 20;
    private static final int SIGNAL_WHEN_X = 8, SIGNAL_WHEN_Y = 82, ABOVE_X = 122, BELOW_X = 146, MODE_Y = 76, BUTTON_SIZE = 20;
    private static final int FIELD_LABEL_X = 8, FIELD_Y = 105, FIELD_X = 69, FIELD_W = 70, UNIT_RIGHT = 165;

    private final SideTabPanel tabs = new SideTabPanel();
    private @Nullable EditBox field;
    private boolean wasFocused;

    public MeterScreen(MeterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        tabs.add(new SecurityTab(() -> menu.containerId, this::sendButton));
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "container/meter/" + name);
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private MeterKind kind() {
        return menu.getKind();
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        titleLabelY = 6;
        field = new EditBox(font, leftPos + FIELD_X, topPos + FIELD_Y, FIELD_W, 10, Component.translatable("gui.arcforge.meter.threshold"));
        field.setBordered(false);
        field.setMaxLength(10);
        // Digits only.
        field.setResponder(text -> {
            if (field != null && !text.chars().allMatch(Character::isDigit)) {
                StringBuilder digits = new StringBuilder();
                text.chars().filter(Character::isDigit).forEach(digit -> digits.append((char) digit));
                field.setValue(digits.toString());
            }
        });
        field.setValue(Integer.toString(menu.getThreshold()));
        addRenderableWidget(field);
        tabs.layout(leftPos, topPos);
    }

    // Sends the field's threshold (clamped to the cap), if it changed.
    private void commit() {
        if (field == null) {
            return;
        }
        long typed;
        try {
            typed = field.getValue().isEmpty() ? 0 : Long.parseLong(field.getValue());
        } catch (NumberFormatException e) {
            typed = 0;
        }
        int threshold = (int) Math.min(Math.max(0, typed), kind().cap());
        field.setValue(Integer.toString(threshold));
        if (threshold != menu.getThreshold()) {
            sendButton(MeterMenu.THRESHOLD_BUTTON + threshold);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (field == null) {
            return;
        }
        if (wasFocused && !field.isFocused()) {
            commit();
        }
        wasFocused = field.isFocused();
        // Show the server's value while it isn't being edited.
        if (!field.isFocused() && !field.getValue().equals(Integer.toString(menu.getThreshold()))) {
            field.setValue(Integer.toString(menu.getThreshold()));
        }
    }

    private Component rateText() {
        return Component.literal(ArcforgeGui.grouped(menu.getRate()) + " ").append(kind().unit());
    }

    private int rateColor() {
        return switch (kind()) {
            case ENERGY -> ArcforgeGui.ACCENT;
            case HEAT -> ArcforgeGui.HEAT;
            case FLUID, GAS -> ArcforgeGui.TEXT;
        };
    }

    private void textRight(GuiGraphicsExtractor graphics, Component text, int right, int y, int color) {
        graphics.text(font, text, right - font.width(text), y, color, false);
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        int cap = Math.max(1, kind().cap());
        int filled = (int) Math.min(BAR_W, Math.round((double) menu.getRate() * BAR_W / cap));
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("rate_bar_" + kind().getSerializedName()), BAR_W, BAR_H, 0, 0,
                    x + BAR_X, y + BAR_Y, filled, BAR_H);
        }
        int tickX = BAR_X + (int) Math.min(BAR_W - 1, Math.round((double) menu.getThreshold() * BAR_W / cap));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TICK, x + tickX, y + TICK_Y, 1, 6);
        String led = menu.isPowered() ? "led_running" : menu.getRate() > 0 ? "led_idle" : "led_off";
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(led), x + LED_X, y + LED_Y, 6, 6);
        modeButton(graphics, mouseX, mouseY, ABOVE_X, MeterSettings.Mode.ABOVE, "meter_above");
        modeButton(graphics, mouseX, mouseY, BELOW_X, MeterSettings.Mode.BELOW, "meter_below");
        // Tabs overlap the panel edge, so they are drawn after the background.
        tabs.render(graphics, font, leftPos, topPos, mouseX, mouseY);
    }

    private void modeButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int bx, MeterSettings.Mode mode, String icon) {
        int x = leftPos + bx, y = topPos + MODE_Y;
        boolean selected = menu.getMode() == mode;
        boolean hovered = ArcforgeGui.isInside(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, selected ? BUTTON_PRESSED : hovered ? BUTTON_HOVER : BUTTON, x, y, BUTTON_SIZE, BUTTON_SIZE);
        int offset = selected ? 3 : 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.widget(icon), x + offset, y + offset, 16, 16);
        if (selected) {
            graphics.outline(x - 1, y - 1, BUTTON_SIZE + 2, BUTTON_SIZE + 2, ArcforgeGui.ACCENT);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable("gui.arcforge.meter.rate"), LABEL_X, RATE_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, rateText(), VALUE_RIGHT, RATE_Y, rateColor());
        graphics.text(font, Component.translatable("gui.arcforge.meter.threshold"), LABEL_X, THRESHOLD_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.meter.threshold_value", menu.getMode().displayName(), ArcforgeGui.grouped(menu.getThreshold()),
                kind().unit()), VALUE_RIGHT, THRESHOLD_Y, ArcforgeGui.TEXT);
        Component status = Component.translatable(menu.isPowered() ? "gui.arcforge.meter.signal_on"
                : menu.getRate() > 0 ? "gui.arcforge.meter.signal_off" : "gui.arcforge.meter.no_flow");
        graphics.text(font, status, STATUS_X, LED_Y - 1, ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable("gui.arcforge.meter.signal_when"), SIGNAL_WHEN_X, SIGNAL_WHEN_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.meter.threshold"), FIELD_LABEL_X, FIELD_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, kind().unit(), UNIT_RIGHT, FIELD_Y, ArcforgeGui.LABEL);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + ABOVE_X, topPos + MODE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            lines.add(Component.translatable("gui.arcforge.meter.mode.above.tooltip"));
        } else if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + BELOW_X, topPos + MODE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            lines.add(Component.translatable("gui.arcforge.meter.mode.below.tooltip"));
        } else {
            tabs.addTooltip(lines, mouseX, mouseY);
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (ArcforgeGui.isInside(event.x(), event.y(), leftPos + ABOVE_X, topPos + MODE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                if (menu.getMode() != MeterSettings.Mode.ABOVE) {
                    sendButton(MeterMenu.BUTTON_ABOVE);
                    ArcforgeGui.playClickSound();
                }
                return true;
            }
            if (ArcforgeGui.isInside(event.x(), event.y(), leftPos + BELOW_X, topPos + MODE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                if (menu.getMode() != MeterSettings.Mode.BELOW) {
                    sendButton(MeterMenu.BUTTON_BELOW);
                    ArcforgeGui.playClickSound();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    // Enter sends the threshold; while typing, the inventory key types instead of closing the screen.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (field != null && field.isFocused()) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                commit();
                field.setFocused(false);
                return true;
            }
            if (event.key() != InputConstants.KEY_ESCAPE) {
                return field.keyPressed(event) || field.canConsumeInput() || super.keyPressed(event);
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop) && !tabs.isInside(mouseX, mouseY);
    }

    // Areas covered by the side tabs, for recipe viewers to avoid.
    public List<Rect2i> getExtraAreas() {
        return tabs.getAreas();
    }
}
