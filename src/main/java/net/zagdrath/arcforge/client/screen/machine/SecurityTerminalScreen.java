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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.menu.machine.SecurityTerminalMenu;
import net.zagdrath.arcforge.network.SecurityEditPayload;
import net.zagdrath.arcforge.network.SecurityProfilePayload;
import net.zagdrath.arcforge.security.SecurityMode;

// The viewer's own security profile (layout in the handoff's gui_layouts.json): the default mode for all their
// blocks, the players they trust (each with a remove button), and a name box to trust someone new. Errors from the
// server (no such player, already trusted, yourself) show for 3 seconds in place of the hint line.
public class SecurityTerminalScreen extends AbstractContainerScreen<SecurityTerminalMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/security_terminal.png");
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final Identifier BUTTON_PRESSED = ArcforgeGui.widget("button_pressed");
    private static final Identifier BUTTON_DISABLED = ArcforgeGui.widget("button_disabled");
    private static final Identifier REMOVE = ArcforgeGui.widget("icon_remove");
    private static final int ERROR_COLOR = 0xFFFF6A5A;
    private static final long ERROR_MILLIS = 3_000;
    private static final int MODE_X = 8, MODE_Y = 30, MODE_PITCH = 24, BUTTON_SIZE = 20, MODE_NAME_X = 84, MODE_NAME_Y = 36;
    private static final int LIST_X = 7, LIST_Y = 68, LIST_W = 162, ROW_H = 14, ROWS = 5, REMOVE_SIZE = 12;
    private static final int FIELD_X = 7, FIELD_Y = 146, FIELD_W = 118, ADD_X = 129, ADD_Y = 145, ADD_W = 40, ADD_H = 20;
    private static final int FEEDBACK_Y = 170;
    private static final List<SecurityMode> MODES = List.of(SecurityMode.PUBLIC, SecurityMode.TRUSTED, SecurityMode.PRIVATE);

    private @Nullable EditBox name;
    private int scroll;
    private String shownError = "";
    private long errorUntil;

    public SecurityTerminalScreen(SecurityTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 184);
    }

    private static SecurityProfilePayload profile() {
        return SecurityProfilePayload.latest;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        titleLabelY = 6;
        name = new EditBox(font, leftPos + FIELD_X + 2, topPos + FIELD_Y + 5, FIELD_W - 4, 10, Component.translatable("gui.arcforge.security_terminal.name_hint"));
        name.setBordered(false);
        name.setMaxLength(16);
        name.setHint(Component.translatable("gui.arcforge.security_terminal.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(name);
    }

    private boolean enabled() {
        return profile().enabled();
    }

    private void send(SecurityEditPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
        ArcforgeGui.playClickSound();
    }

    private void add() {
        if (name != null && enabled() && !name.getValue().isBlank()) {
            send(SecurityEditPayload.add(name.getValue().trim()));
            name.setValue("");
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        SecurityProfilePayload profile = profile();
        if (!profile.error().isEmpty() && !profile.error().equals(shownError)) {
            shownError = profile.error();
            errorUntil = Util.getMillis() + ERROR_MILLIS;
        }
        if (profile.error().isEmpty()) {
            shownError = "";
        }
        if (name != null) {
            name.active = enabled();
        }
        scroll = Math.min(scroll, Math.max(0, profile.trusted().size() - ROWS));
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        SecurityProfilePayload profile = profile();
        for (int index = 0; index < MODES.size(); index++) {
            SecurityMode mode = MODES.get(index);
            int x = leftPos + MODE_X + index * MODE_PITCH;
            int y = topPos + MODE_Y;
            boolean selected = mode == profile.defaultMode();
            boolean hovered = ArcforgeGui.isInside(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE);
            Identifier sprite = selected ? BUTTON_PRESSED : !enabled() ? BUTTON_DISABLED : hovered ? BUTTON_HOVER : BUTTON;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, BUTTON_SIZE, BUTTON_SIZE);
            int offset = selected ? 3 : 2;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.widget("security_" + mode.getSerializedName()), x + offset, y + offset, 16, 16);
            if (selected) {
                graphics.outline(x - 1, y - 1, BUTTON_SIZE + 2, BUTTON_SIZE + 2, ArcforgeGui.ACCENT);
            }
        }
        List<SecurityProfilePayload.Entry> trusted = profile.trusted();
        for (int row = 0; row < ROWS && scroll + row < trusted.size(); row++) {
            SecurityProfilePayload.Entry entry = trusted.get(scroll + row);
            int x = leftPos + LIST_X;
            int y = topPos + LIST_Y + row * ROW_H;
            PlayerFaceExtractor.extractRenderState(graphics, skin(entry), x + 3, y + 3, 8);
            int removeX = x + LIST_W - 14;
            boolean hovered = ArcforgeGui.isInside(mouseX, mouseY, removeX, y + 1, REMOVE_SIZE, REMOVE_SIZE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled() ? BUTTON_DISABLED : hovered ? BUTTON_HOVER : BUTTON, removeX, y + 1, REMOVE_SIZE, REMOVE_SIZE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, REMOVE, removeX + 2, y + 3, 8, 8);
        }
        boolean canAdd = enabled() && name != null && !name.getValue().isBlank();
        boolean addHovered = ArcforgeGui.isInside(mouseX, mouseY, leftPos + ADD_X, topPos + ADD_Y, ADD_W, ADD_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, !canAdd ? BUTTON_DISABLED : addHovered ? BUTTON_HOVER : BUTTON,
                leftPos + ADD_X, topPos + ADD_Y, ADD_W, ADD_H);
    }

    // Online players' own skins; others (and anyone not loaded yet) the default for their UUID.
    private PlayerSkin skin(SecurityProfilePayload.Entry entry) {
        PlayerInfo info = minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(entry.id()) : null;
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(entry.id());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SecurityProfilePayload profile = profile();
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable("gui.arcforge.security_terminal.default_mode"), MODE_X, 20, ArcforgeGui.LABEL, false);
        graphics.text(font, profile.defaultMode().displayName(), MODE_NAME_X, MODE_NAME_Y, ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable("gui.arcforge.security_terminal.trusted", profile.trusted().size()), MODE_X, 58, ArcforgeGui.LABEL, false);
        List<SecurityProfilePayload.Entry> trusted = profile.trusted();
        for (int row = 0; row < ROWS && scroll + row < trusted.size(); row++) {
            graphics.text(font, trusted.get(scroll + row).name(), LIST_X + 14, LIST_Y + row * ROW_H + 3, ArcforgeGui.TEXT, false);
        }
        Component add = Component.translatable("gui.arcforge.security_terminal.add");
        graphics.text(font, add, ADD_X + (ADD_W - font.width(add)) / 2, ADD_Y + 6, ArcforgeGui.TEXT, false);
        Component feedback;
        int color = ArcforgeGui.LABEL;
        if (!enabled()) {
            feedback = Component.translatable("gui.arcforge.security_terminal.disabled");
            color = ERROR_COLOR;
        } else if (!shownError.isEmpty() && Util.getMillis() < errorUntil) {
            feedback = Component.translatable(shownError, profile.errorArg());
            color = ERROR_COLOR;
        } else {
            feedback = Component.translatable("gui.arcforge.security_terminal.applies");
        }
        graphics.text(font, feedback, MODE_X, FEEDBACK_Y, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<SecurityProfilePayload.Entry> trusted = profile().trusted();
        for (int row = 0; row < ROWS && scroll + row < trusted.size(); row++) {
            int removeX = leftPos + LIST_X + LIST_W - 14;
            if (ArcforgeGui.isInside(mouseX, mouseY, removeX, topPos + LIST_Y + row * ROW_H + 1, REMOVE_SIZE, REMOVE_SIZE)) {
                graphics.setComponentTooltipForNextFrame(font,
                        List.of(Component.translatable("gui.arcforge.security_terminal.remove", trusted.get(scroll + row).name())), mouseX, mouseY);
            }
        }
        for (int index = 0; index < MODES.size(); index++) {
            if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + MODE_X + index * MODE_PITCH, topPos + MODE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                graphics.setComponentTooltipForNextFrame(font, List.of(MODES.get(index).displayName()), mouseX, mouseY);
            }
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && enabled()) {
            for (int index = 0; index < MODES.size(); index++) {
                if (ArcforgeGui.isInside(event.x(), event.y(), leftPos + MODE_X + index * MODE_PITCH, topPos + MODE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                    if (MODES.get(index) != profile().defaultMode()) {
                        send(SecurityEditPayload.setMode(MODES.get(index)));
                    }
                    return true;
                }
            }
            List<SecurityProfilePayload.Entry> trusted = profile().trusted();
            for (int row = 0; row < ROWS && scroll + row < trusted.size(); row++) {
                if (ArcforgeGui.isInside(event.x(), event.y(), leftPos + LIST_X + LIST_W - 14, topPos + LIST_Y + row * ROW_H + 1, REMOVE_SIZE, REMOVE_SIZE)) {
                    send(SecurityEditPayload.remove(trusted.get(scroll + row).id()));
                    return true;
                }
            }
            if (ArcforgeGui.isInside(event.x(), event.y(), leftPos + ADD_X, topPos + ADD_Y, ADD_W, ADD_H)) {
                add();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y, LIST_W, ROWS * ROW_H)) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, profile().trusted().size() - ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Enter adds; while typing, the inventory key types instead of closing the screen.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (name != null && name.isFocused()) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                add();
                return true;
            }
            if (event.key() != InputConstants.KEY_ESCAPE) {
                return name.keyPressed(event) || name.canConsumeInput() || super.keyPressed(event);
            }
        }
        return super.keyPressed(event);
    }
}
