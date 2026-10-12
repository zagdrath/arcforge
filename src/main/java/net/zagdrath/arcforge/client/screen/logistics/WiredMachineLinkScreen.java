/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.logistics;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.logistics.WiredMachineLinkBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.SecurityTab;
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.logistics.WiredMachineLinkMenu;

// A Wired Machine Link's screen (176x126, no inventory). The read-out across the top: the machine it's linked to (its
// name, e.g. "Steam Boiler Array 7×9×12"), the face of the machine the cable is on and the cable's length, and a status
// line; or "Not linked", with why on the status line. Below, a face grid like the machines' Sides tab for its own faces (left click cycles
// through the machine's port modes, right click back, shift-click clears; the cable's face is the locked purple link),
// and how it works. A Security tab on the right.
public class WiredMachineLinkScreen extends AbstractContainerScreen<WiredMachineLinkMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/wired_machine_link.png");
    private static final Identifier FACE_HOVER = ArcforgeGui.widget("face_hover");
    private static final int WIDTH = 176, HEIGHT = 126;
    private static final int TEXT_X = 12, NAME_Y = 22, CABLE_Y = 33, LED_X = 12, STATUS_Y = 47, TEXT_RIGHT = 164;
    private static final int GRID_X = 8, GRID_Y = 66, FACE_SIZE = 16;
    private static final int HINT_X = 92, HINT_Y = 68, HINT_WIDTH = 76;

    private final SideTabPanel tabs = new SideTabPanel();

    public WiredMachineLinkScreen(WiredMachineLinkMenu menu, Inventory inventory, Component title) {
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

    // The face grid, laid out like the Sides tab's.
    private static int faceX(RelativeSide side) {
        return GRID_X + switch (side) {
            case TOP, FRONT, BOTTOM -> 18;
            case LEFT -> 0;
            case RIGHT -> 36;
            case BACK -> 54;
        };
    }

    private static int faceY(RelativeSide side) {
        return GRID_Y + switch (side) {
            case TOP -> 0;
            case BOTTOM -> 36;
            default -> 18;
        };
    }

    private @Nullable RelativeSide faceAt(double mouseX, double mouseY) {
        for (RelativeSide side : RelativeSide.values()) {
            if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + faceX(side), topPos + faceY(side), FACE_SIZE, FACE_SIZE)) {
                return side;
            }
        }
        return null;
    }

    private SideMode mode(@Nullable WiredMachineLinkBlockEntity link, RelativeSide side) {
        return link != null ? link.getFaceMode(side) : SideMode.NONE;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        WiredMachineLinkBlockEntity link = menu.link();
        RelativeSide hovered = faceAt(mouseX, mouseY);
        for (RelativeSide side : RelativeSide.values()) {
            int x = leftPos + faceX(side), y = topPos + faceY(side);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.widget("face_" + mode(link, side).getSerializedName()), x, y, FACE_SIZE, FACE_SIZE);
            if (side == hovered && mode(link, side) != SideMode.MACHINE_LINK) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FACE_HOVER, x, y, FACE_SIZE, FACE_SIZE);
            }
        }
        String led = link != null && link.isLinked() ? "led_running" : "led_blocked";
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.sprite("container/vacuum_collector/" + led), leftPos + LED_X, topPos + STATUS_Y + 1, 6, 6);
        tabs.render(graphics, font, leftPos, topPos, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        WiredMachineLinkBlockEntity link = menu.link();
        if (link != null && link.isLinked()) {
            graphics.text(font, clipped(link.getMachineName(), TEXT_RIGHT - TEXT_X), TEXT_X, NAME_Y, ArcforgeGui.TEXT, false);
            graphics.text(font, clipped(cableLine(link), TEXT_RIGHT - TEXT_X), TEXT_X, CABLE_Y, ArcforgeGui.LABEL, false);
            graphics.text(font, Component.translatable("gui.arcforge.wired_machine_link.linked"), LED_X + 8, STATUS_Y, ArcforgeGui.TEXT, false);
        } else {
            graphics.text(font, Component.translatable("gui.arcforge.wired_machine_link.not_linked"), TEXT_X, NAME_Y, ArcforgeGui.WARNING, false);
            WiredMachineLinkBlockEntity.Status status = link != null ? link.getStatus() : WiredMachineLinkBlockEntity.Status.NO_CABLE;
            graphics.text(font, clipped(status.description(), TEXT_RIGHT - LED_X - 8), LED_X + 8, STATUS_Y, ArcforgeGui.TEXT, false);
        }
        int y = HINT_Y;
        for (FormattedCharSequence line : font.split(Component.translatable("gui.arcforge.wired_machine_link.hint"), HINT_WIDTH)) {
            graphics.text(font, line, HINT_X, y, ArcforgeGui.LABEL, false);
            y += 9;
        }
    }

    // "Cable on its Back face · 12 blocks"
    private static Component cableLine(WiredMachineLinkBlockEntity link) {
        RelativeSide side = link.getMachineSide();
        Component face = side != null ? side.getDescription() : Component.literal("?");
        return Component.translatable("gui.arcforge.wired_machine_link.cable", face, link.getDistance());
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
        WiredMachineLinkBlockEntity link = menu.link();
        RelativeSide side = faceAt(mouseX, mouseY);
        if (side != null) {
            SideMode mode = mode(link, side);
            lines.add(side.getDescription());
            lines.add(mode.getDescription().copy().withStyle(ChatFormatting.GRAY));
            if (mode == SideMode.MACHINE_LINK) {
                lines.add(Component.translatable("gui.arcforge.wired_machine_link.cable_face").withStyle(ChatFormatting.DARK_GRAY));
            } else if (link == null || !link.isLinked()) {
                lines.add(Component.translatable("gui.arcforge.wired_machine_link.face_unlinked").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (link != null && ArcforgeGui.isInside(mouseX, mouseY, leftPos + TEXT_X, topPos + NAME_Y - 1, TEXT_RIGHT - TEXT_X, 22)) {
            if (link.isLinked()) {
                lines.add(link.getMachineName());
                lines.add(cableLine(link).copy().withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable("gui.arcforge.wired_machine_link.not_linked"));
                lines.add(link.getStatus().description().copy().withStyle(ChatFormatting.GRAY));
            }
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
        RelativeSide side = faceAt(event.x(), event.y());
        if (side != null && (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
            if (mode(menu.link(), side) != SideMode.MACHINE_LINK) {
                int action = event.hasShiftDown() ? SideConfig.ACTION_CLEAR
                        : event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? SideConfig.ACTION_PREVIOUS : SideConfig.ACTION_NEXT;
                sendButton(WiredMachineLinkMenu.faceButtonId(side, action));
                ArcforgeGui.playClickSound();
            }
            return true;
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
