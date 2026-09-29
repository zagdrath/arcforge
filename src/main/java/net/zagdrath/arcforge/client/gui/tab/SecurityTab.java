/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.network.SecuritySyncPayload;
import net.zagdrath.arcforge.security.SecurityMode;

// Who owns the machine and who else may use it: Public, Trusted or Private, with the one in effect selected. With no
// override that's the owner's profile mode; picking another sets an override, and picking the profile's mode clears
// it, so the machine follows the profile again. Only the owner (or an operator) can change it; others see the buttons
// greyed out. Hidden when security is off or the block has no owner. Its data comes from SecuritySyncPayload for this
// menu.
public class SecurityTab extends SideTab {
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final Identifier BUTTON_PRESSED = ArcforgeGui.widget("button_pressed");
    private static final Identifier BUTTON_DISABLED = ArcforgeGui.widget("button_disabled");
    private static final int BUTTON_SIZE = 20, BUTTONS_X = 8, BUTTONS_Y = 35, BUTTON_PITCH = 24;
    private static final int OWNER_X = 6, OWNER_Y = 24, OWNER_WIDTH = 96, MODE_Y = 62;
    private static final List<SecurityMode> CHOICES = List.of(SecurityMode.values());

    private final IntSupplier containerId;
    private final IntConsumer sendButton;

    public SecurityTab(IntSupplier containerId, IntConsumer sendButton) {
        super(ArcforgeGui.widget("icon_security"), Component.translatable("gui.arcforge.security.tab"), 108, 80);
        this.containerId = containerId;
        this.sendButton = sendButton;
    }

    private SecuritySyncPayload data() {
        SecuritySyncPayload latest = SecuritySyncPayload.latest;
        return latest.containerId() == containerId.getAsInt() ? latest : null;
    }

    @Override
    public boolean isVisible() {
        SecuritySyncPayload data = data();
        return data != null && data.enabled() && data.owned();
    }

    private static Identifier icon(SecurityMode mode) {
        return ArcforgeGui.widget("security_" + mode.getSerializedName());
    }

    private int choiceAt(int localX, int localY) {
        for (int index = 0; index < CHOICES.size(); index++) {
            if (ArcforgeGui.isInside(localX, localY, BUTTONS_X + index * BUTTON_PITCH, BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                return index;
            }
        }
        return -1;
    }

    // The override that picking this mode sends: none (follow the profile) for the profile's own mode.
    private static Optional<SecurityMode> overrideFor(SecuritySyncPayload data, SecurityMode mode) {
        return mode == data.profileMode() ? Optional.empty() : Optional.of(mode);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        SecuritySyncPayload data = data();
        if (data == null) {
            return;
        }
        Component owner = Component.translatable("security.arcforge.owner", data.ownerName().isEmpty()
                ? Component.translatable("security.arcforge.unknown_owner") : Component.literal(data.ownerName()));
        String trimmed = font.plainSubstrByWidth(owner.getString(), OWNER_WIDTH);
        graphics.text(font, trimmed.length() < owner.getString().length() ? trimmed.substring(0, Math.max(0, trimmed.length() - 1)) + "…" : trimmed,
                x + OWNER_X, y + OWNER_Y, ArcforgeGui.LABEL, false);
        int hovered = choiceAt(mouseX - x, mouseY - y);
        for (int index = 0; index < CHOICES.size(); index++) {
            boolean selected = CHOICES.get(index) == data.effectiveMode();
            int bx = x + BUTTONS_X + index * BUTTON_PITCH;
            int by = y + BUTTONS_Y;
            Identifier sprite = selected ? BUTTON_PRESSED : !data.canEdit() ? BUTTON_DISABLED : index == hovered ? BUTTON_HOVER : BUTTON;
            int iconOffset = selected ? 3 : 2;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, bx, by, BUTTON_SIZE, BUTTON_SIZE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon(CHOICES.get(index)), bx + iconOffset, by + iconOffset, 16, 16,
                    !selected && !data.canEdit() ? ARGB.white(0.5F) : -1);
            if (selected) {
                graphics.outline(bx - 1, by - 1, BUTTON_SIZE + 2, BUTTON_SIZE + 2, ArcforgeGui.ACCENT);
            }
        }
        graphics.text(font, data.effectiveMode().displayName(), x + OWNER_X, y + MODE_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        SecuritySyncPayload data = data();
        int index = choiceAt(localX, localY);
        if (data == null || index < 0 || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (data.canEdit() && CHOICES.get(index) != data.effectiveMode()) {
            sendButton.accept(MachineMenuButtons.securityButtonId(overrideFor(data, CHOICES.get(index))));
            ArcforgeGui.playClickSound();
        }
        return true;
    }

    @Override
    protected void addCollapsedTooltip(List<Component> lines) {
        super.addCollapsedTooltip(lines);
        SecuritySyncPayload data = data();
        if (data != null) {
            lines.add(data.effectiveMode().displayName().copy().withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    protected void addTooltip(List<Component> lines, int localX, int localY) {
        SecuritySyncPayload data = data();
        int index = choiceAt(localX, localY);
        if (data == null || index < 0) {
            return;
        }
        lines.add(CHOICES.get(index).displayName());
        if (!data.canEdit()) {
            lines.add(Component.translatable("gui.arcforge.security.owner_only").withStyle(ChatFormatting.GRAY));
        }
    }
}
