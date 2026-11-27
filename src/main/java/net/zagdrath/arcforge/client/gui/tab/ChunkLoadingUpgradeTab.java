/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.chunkloading.ChunkLoaderStatus;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.registry.ModItems;

// A Quantum Tunnel's Upgrades tab: its one Chunk Loading Upgrade slot and what the upgrade is doing. The tunnel's screen
// has no inventory, so clicking the slot fits an upgrade from the player's inventory, or takes the fitted one out
// (the button is the menu's, see QuantumTunnelMenu.UPGRADE_BUTTON).
public class ChunkLoadingUpgradeTab extends SideTab {
    private static final Identifier SLOT = ArcforgeGui.widget("slot_upgrade");
    private static final int SLOT_X = 8, SLOT_Y = 24, TEXT_X = 30;

    private final BooleanSupplier fitted;
    private final Supplier<ChunkLoaderStatus> status;
    private final IntConsumer sendButton;
    private final int button;

    public ChunkLoadingUpgradeTab(BooleanSupplier fitted, Supplier<ChunkLoaderStatus> status, IntConsumer sendButton, int button) {
        super(ArcforgeGui.widget("icon_upgrades"), Component.translatable("gui.arcforge.tab.upgrades"), 132, 50);
        this.fitted = fitted;
        this.status = status;
        this.sendButton = sendButton;
        this.button = button;
    }

    private Component line() {
        return fitted.getAsBoolean() ? status.get().description() : Component.translatable("gui.arcforge.quantum_tunnel.upgrade.empty");
    }

    @Override
    protected int expandedWidth() {
        return Math.max(132, TEXT_X + 6 + net.minecraft.client.Minecraft.getInstance().font.width(line()));
    }

    private static boolean onSlot(int localX, int localY) {
        return ArcforgeGui.isInside(localX, localY, SLOT_X, SLOT_Y, 18, 18);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x + SLOT_X, y + SLOT_Y, 18, 18);
        if (fitted.getAsBoolean()) {
            graphics.item(new ItemStack(ModItems.CHUNK_LOADING_UPGRADE.get()), x + SLOT_X + 1, y + SLOT_Y + 1);
        }
        if (onSlot(mouseX - x, mouseY - y)) {
            graphics.fill(x + SLOT_X + 1, y + SLOT_Y + 1, x + SLOT_X + 17, y + SLOT_Y + 17, 0x80FFFFFF);
        }
        boolean active = fitted.getAsBoolean() && status.get() == ChunkLoaderStatus.ACTIVE;
        graphics.text(font, line(), x + TEXT_X, y + SLOT_Y + 5,
                !fitted.getAsBoolean() ? ArcforgeGui.TOOLTIP_GRAY : active ? ArcforgeGui.ACCENT : 0xFFFF6A5A, false);
    }

    @Override
    protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        if (!onSlot(localX, localY) || !ArcforgeGui.isPress(event)) {
            return false;
        }
        sendButton.accept(button);
        ArcforgeGui.playClickSound();
        return true;
    }

    @Override
    protected void addTooltip(List<Component> lines, int localX, int localY) {
        if (onSlot(localX, localY)) {
            lines.add(Component.translatable("item.arcforge.chunk_loading_upgrade"));
            lines.add(Component.translatable(fitted.getAsBoolean() ? "gui.arcforge.quantum_tunnel.upgrade.take_out"
                    : "gui.arcforge.quantum_tunnel.upgrade.fit").withStyle(ChatFormatting.GRAY));
        } else if (fitted.getAsBoolean() && localY >= SLOT_Y && localY < SLOT_Y + 18) {
            lines.add(Component.translatable("gui.arcforge.chunk_loader.status." + status.get().getName() + ".tooltip").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    protected void addCollapsedTooltip(List<Component> lines) {
        super.addCollapsedTooltip(lines);
        lines.add(line().copy().withStyle(ChatFormatting.GRAY));
    }
}
