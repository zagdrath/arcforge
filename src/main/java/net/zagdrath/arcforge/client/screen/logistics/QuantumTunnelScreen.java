/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.SecurityTab;
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.menu.logistics.QuantumTunnelMenu;
import net.zagdrath.arcforge.network.QuantumFrequencyPayload;
import net.zagdrath.arcforge.network.QuantumStatePayload;
import net.zagdrath.arcforge.quantum.Frequency;
import net.zagdrath.arcforge.quantum.QuantumFaces;
import net.zagdrath.arcforge.quantum.QuantumResource;

// A Quantum Tunnel's screen (176x230, no inventory): the frequency it's on; a name field with a public/private toggle and
// a Set button (puts the tunnel on that frequency, making it if it's new); the frequencies the viewer may use (click one
// to switch, x to delete one you made; a padlock marks private ones, with their owner); the frequency's five buffers;
// and the side settings, a grid of faces by resources, each cell cycling none / input / output. A Security tab on the
// right.
public class QuantumTunnelScreen extends AbstractContainerScreen<QuantumTunnelMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/quantum_tunnel.png");
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final Identifier BUTTON_DISABLED = ArcforgeGui.widget("button_disabled");
    private static final Identifier REMOVE = ArcforgeGui.widget("icon_remove");
    private static final Identifier[] FACE_SPRITES = { ArcforgeGui.widget("face_none"), ArcforgeGui.widget("face_input"), ArcforgeGui.widget("face_output") };
    private static final Identifier FACE_HOVER = ArcforgeGui.widget("face_hover");
    private static final int WIDTH = 176, HEIGHT = 230;
    private static final int ERROR_COLOR = 0xFFFF6A5A;
    private static final long ERROR_MILLIS = 3_000;
    private static final int CURRENT_Y = 18;
    private static final int FIELD_X = 10, FIELD_Y = 33, FIELD_W = 100;
    private static final int TOGGLE_X = 117, SET_X = 141, SET_W = 28, BUTTONS_Y = 27, BUTTON_SIZE = 20;
    private static final int LIST_X = 8, LIST_Y = 52, LIST_W = 161, ROW_H = 11, ROWS = 5, DELETE_SIZE = 9;
    private static final int BUFFER_X = 10, BUFFER_PITCH = 32, BUFFER_LABEL_Y = 115, BAR_Y = 124, BAR_W = 28, BAR_H = 4, BUFFER_VALUE_Y = 131;
    private static final int GRID_HEADER_Y = 149, GRID_X = 52, GRID_Y = 160, CELL = 12, CELL_PITCH_X = 19, CELL_PITCH_Y = 13, ROW_LABEL_X = 10;
    private static final Direction[] FACES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
    private static final int[] BAR_COLORS = { ArcforgeGui.ACCENT, ArcforgeGui.HEAT, 0xFF4A8FE0, 0xFFC8D4E0, 0xFFB4BAC0 };

    private final SideTabPanel tabs = new SideTabPanel();
    private @Nullable EditBox name;
    private boolean makePrivate;
    private int scroll;
    private String shownError = "";
    private long errorUntil;
    private @Nullable QuantumStatePayload seen;

    public QuantumTunnelScreen(QuantumTunnelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        tabs.add(new SecurityTab(() -> menu.containerId, this::sendButton));
    }

    private QuantumStatePayload state() {
        QuantumStatePayload latest = QuantumStatePayload.latest;
        return latest.containerId() == menu.containerId ? latest : new QuantumStatePayload(menu.containerId, Optional.empty(), List.of(), "");
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void send(QuantumFrequencyPayload.Action action, String frequency, Optional<java.util.UUID> owner) {
        ClientPacketDistributor.sendToServer(new QuantumFrequencyPayload(menu.containerId, action, frequency, owner));
        ArcforgeGui.playClickSound();
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        titleLabelY = 6;
        name = new EditBox(font, leftPos + FIELD_X, topPos + FIELD_Y, FIELD_W, 10, Component.translatable("gui.arcforge.quantum_tunnel.name_hint"));
        name.setBordered(false);
        name.setMaxLength(Frequency.MAX_NAME_LENGTH);
        name.setHint(Component.translatable("gui.arcforge.quantum_tunnel.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(name);
        tabs.layout(leftPos, topPos);
    }

    // Puts the tunnel on the typed frequency (made if it's new): private to the viewer when the toggle says so.
    private void set() {
        if (name != null && !name.getValue().isBlank()) {
            send(QuantumFrequencyPayload.Action.CREATE, name.getValue().trim(),
                    makePrivate && minecraft != null && minecraft.player != null ? Optional.of(minecraft.player.getUUID()) : Optional.empty());
            name.setValue("");
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        QuantumStatePayload state = state();
        if (state != seen) {
            seen = state;
            if (!state.error().isEmpty()) {
                shownError = state.error();
                errorUntil = Util.getMillis() + ERROR_MILLIS;
            }
        }
        scroll = Math.min(scroll, Math.max(0, state.frequencies().size() - ROWS));
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        // The public / private toggle and the Set button.
        boolean toggleHovered = ArcforgeGui.isInside(mouseX, mouseY, x + TOGGLE_X, y + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, toggleHovered ? BUTTON_HOVER : BUTTON, x + TOGGLE_X, y + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.widget(makePrivate ? "security_private" : "security_public"),
                x + TOGGLE_X + 2, y + BUTTONS_Y + 2, 16, 16);
        boolean canSet = name != null && !name.getValue().isBlank();
        boolean setHovered = ArcforgeGui.isInside(mouseX, mouseY, x + SET_X, y + BUTTONS_Y, SET_W, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, !canSet ? BUTTON_DISABLED : setHovered ? BUTTON_HOVER : BUTTON, x + SET_X, y + BUTTONS_Y, SET_W, BUTTON_SIZE);
        // The list: the current frequency outlined, a padlock on private ones, x on those the viewer may delete.
        QuantumStatePayload state = state();
        List<QuantumStatePayload.Entry> list = state.frequencies();
        for (int row = 0; row < ROWS && scroll + row < list.size(); row++) {
            QuantumStatePayload.Entry entry = list.get(scroll + row);
            int rowY = y + LIST_Y + row * ROW_H;
            boolean current = state.current().map(entry::is).orElse(false);
            if (current) {
                graphics.outline(x + LIST_X, rowY, LIST_W - 1, ROW_H, ArcforgeGui.ACCENT);
            } else if (ArcforgeGui.isInside(mouseX, mouseY, x + LIST_X, rowY, LIST_W - DELETE_SIZE - 4, ROW_H)) {
                graphics.fill(x + LIST_X, rowY, x + LIST_X + LIST_W - 1, rowY + ROW_H, 0x18FFFFFF);
            }
            if (entry.isPrivate()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.widget("security_private"), x + LIST_X + 2, rowY + 1, 9, 9);
            }
            if (entry.deletable()) {
                int deleteX = x + LIST_X + LIST_W - DELETE_SIZE - 3;
                boolean hovered = ArcforgeGui.isInside(mouseX, mouseY, deleteX, rowY + 1, DELETE_SIZE, DELETE_SIZE);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? BUTTON_HOVER : BUTTON, deleteX, rowY + 1, DELETE_SIZE, DELETE_SIZE);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, REMOVE, deleteX + 1, rowY + 2, 7, 7);
            }
        }
        // Buffer bars.
        for (int i = 0; i < 5; i++) {
            int filled = barFill(i);
            if (filled > 0) {
                graphics.fill(x + BUFFER_X + i * BUFFER_PITCH, y + BAR_Y, x + BUFFER_X + i * BUFFER_PITCH + filled, y + BAR_Y + BAR_H, BAR_COLORS[i]);
            }
        }
        // The side grid.
        for (int r = 0; r < QuantumResource.values().length; r++) {
            for (int c = 0; c < FACES.length; c++) {
                int cellX = x + GRID_X + c * CELL_PITCH_X, cellY = y + GRID_Y + r * CELL_PITCH_Y;
                int mode = menu.mode(FACES[c], QuantumResource.values()[r]);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FACE_SPRITES[mode], cellX, cellY, CELL, CELL);
                if (ArcforgeGui.isInside(mouseX, mouseY, cellX, cellY, CELL, CELL)) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FACE_HOVER, cellX, cellY, CELL, CELL);
                }
            }
        }
        tabs.render(graphics, font, leftPos, topPos, mouseX, mouseY);
    }

    private int barFill(int index) {
        int amount, capacity;
        switch (index) {
            case 0 -> { amount = menu.value(QuantumTunnelMenu.DATA_ENERGY); capacity = menu.value(QuantumTunnelMenu.DATA_ENERGY_CAPACITY); }
            case 1 -> { amount = menu.value(QuantumTunnelMenu.DATA_HEAT); capacity = menu.value(QuantumTunnelMenu.DATA_HEAT_CAPACITY); }
            case 2 -> { amount = menu.value(QuantumTunnelMenu.DATA_FLUID); capacity = menu.value(QuantumTunnelMenu.DATA_FLUID_CAPACITY); }
            case 3 -> { amount = menu.value(QuantumTunnelMenu.DATA_GAS); capacity = menu.value(QuantumTunnelMenu.DATA_GAS_CAPACITY); }
            default -> { amount = menu.value(QuantumTunnelMenu.DATA_ITEMS); capacity = menu.value(QuantumTunnelMenu.DATA_ITEM_SLOTS); }
        }
        return capacity <= 0 ? 0 : (int) Math.min(BAR_W, Math.round((double) amount * BAR_W / capacity));
    }

    private Component bufferValue(int index) {
        return switch (index) {
            case 0 -> Component.literal(ArcforgeGui.compact(menu.value(QuantumTunnelMenu.DATA_ENERGY)));
            case 1 -> Component.translatable("gui.arcforge.celsius", menu.value(QuantumTunnelMenu.DATA_TEMPERATURE));
            case 2 -> Component.literal(ArcforgeGui.compact(menu.value(QuantumTunnelMenu.DATA_FLUID)));
            case 3 -> Component.literal(ArcforgeGui.compact(menu.value(QuantumTunnelMenu.DATA_GAS)));
            default -> Component.literal(menu.value(QuantumTunnelMenu.DATA_ITEMS) + "/" + menu.value(QuantumTunnelMenu.DATA_ITEM_SLOTS));
        };
    }

    private static Component shortName(QuantumResource resource) {
        return Component.translatable("gui.arcforge.quantum_tunnel.short." + resource.getSerializedName());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        QuantumStatePayload state = state();
        // The frequency it's on, or an error for a few seconds after a refused request.
        Component current;
        int color = ArcforgeGui.TEXT;
        if (!shownError.isEmpty() && Util.getMillis() < errorUntil) {
            current = Component.translatable(shownError);
            color = ERROR_COLOR;
        } else if (state.current().isPresent()) {
            current = Component.literal(state.current().get().name());
            color = menu.isLinked() ? ArcforgeGui.ACCENT : ERROR_COLOR;
        } else {
            current = Component.translatable("gui.arcforge.quantum_tunnel.none");
            color = ArcforgeGui.LABEL;
        }
        Component label = Component.translatable("gui.arcforge.quantum_tunnel.frequency");
        graphics.text(font, label, 8, CURRENT_Y, ArcforgeGui.LABEL, false);
        Component shown = clipped(current, 160 - font.width(label) - 4);
        graphics.text(font, shown, 168 - font.width(shown), CURRENT_Y, color, false);
        Component set = Component.translatable("gui.arcforge.quantum_tunnel.set");
        graphics.text(font, set, SET_X + (SET_W - font.width(set)) / 2, BUTTONS_Y + 6, ArcforgeGui.TEXT, false);
        // List rows: name, then the owner of a private one.
        List<QuantumStatePayload.Entry> list = state.frequencies();
        if (list.isEmpty()) {
            graphics.text(font, Component.translatable("gui.arcforge.quantum_tunnel.empty_list"), LIST_X + 4, LIST_Y + 2, ArcforgeGui.LABEL, false);
        }
        for (int row = 0; row < ROWS && scroll + row < list.size(); row++) {
            QuantumStatePayload.Entry entry = list.get(scroll + row);
            int rowY = LIST_Y + row * ROW_H + 2;
            int nameX = LIST_X + 13;
            int right = LIST_X + LIST_W - DELETE_SIZE - 6;
            Component owner = entry.isPrivate() ? Component.literal(entry.creatorName()) : Component.empty();
            int ownerWidth = entry.isPrivate() ? Math.min(50, font.width(owner)) : 0;
            graphics.text(font, clipped(Component.literal(entry.name()), right - nameX - ownerWidth - 4), nameX, rowY, ArcforgeGui.TEXT, false);
            if (entry.isPrivate()) {
                Component clippedOwner = clipped(owner, 50);
                graphics.text(font, clippedOwner, right - font.width(clippedOwner), rowY, ArcforgeGui.LABEL, false);
            }
        }
        // Buffers.
        for (int i = 0; i < 5; i++) {
            Component name = shortName(QuantumResource.values()[i]);
            int centre = BUFFER_X + i * BUFFER_PITCH + BAR_W / 2;
            graphics.text(font, name, centre - font.width(name) / 2, BUFFER_LABEL_Y, ArcforgeGui.LABEL, false);
            Component value = clipped(bufferValue(i), BUFFER_PITCH - 2);
            graphics.text(font, value, centre - font.width(value) / 2, BUFFER_VALUE_Y, ArcforgeGui.TEXT, false);
        }
        // Grid headers.
        for (int c = 0; c < FACES.length; c++) {
            Component face = Component.translatable("gui.arcforge.quantum_tunnel.face." + FACES[c].getSerializedName());
            graphics.text(font, face, GRID_X + c * CELL_PITCH_X + CELL / 2 - font.width(face) / 2, GRID_HEADER_Y, ArcforgeGui.LABEL, false);
        }
        for (int r = 0; r < QuantumResource.values().length; r++) {
            graphics.text(font, clipped(QuantumResource.values()[r].displayName(), GRID_X - ROW_LABEL_X - 3), ROW_LABEL_X, GRID_Y + r * CELL_PITCH_Y + 2,
                    ArcforgeGui.LABEL, false);
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
        int x = leftPos, y = topPos;
        if (ArcforgeGui.isInside(mouseX, mouseY, x + TOGGLE_X, y + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            lines.add(Component.translatable(makePrivate ? "gui.arcforge.quantum_tunnel.private" : "gui.arcforge.quantum_tunnel.public"));
            lines.add(Component.translatable(makePrivate ? "gui.arcforge.quantum_tunnel.private.tooltip" : "gui.arcforge.quantum_tunnel.public.tooltip")
                    .withStyle(ChatFormatting.GRAY));
        } else if (ArcforgeGui.isInside(mouseX, mouseY, x + SET_X, y + BUTTONS_Y, SET_W, BUTTON_SIZE)) {
            lines.add(Component.translatable("gui.arcforge.quantum_tunnel.set.tooltip"));
        }
        List<QuantumStatePayload.Entry> list = state().frequencies();
        for (int row = 0; row < ROWS && scroll + row < list.size(); row++) {
            QuantumStatePayload.Entry entry = list.get(scroll + row);
            int rowY = y + LIST_Y + row * ROW_H;
            if (entry.deletable() && ArcforgeGui.isInside(mouseX, mouseY, x + LIST_X + LIST_W - DELETE_SIZE - 3, rowY + 1, DELETE_SIZE, DELETE_SIZE)) {
                lines.add(Component.translatable("gui.arcforge.quantum_tunnel.delete", entry.name()));
                lines.add(Component.translatable("gui.arcforge.quantum_tunnel.delete.tooltip").withStyle(ChatFormatting.RED));
            } else if (ArcforgeGui.isInside(mouseX, mouseY, x + LIST_X, rowY, LIST_W, ROW_H)) {
                lines.add(Component.literal(entry.name()));
                lines.add(Component.translatable(entry.isPrivate() ? "gui.arcforge.quantum_tunnel.private_of" : "gui.arcforge.quantum_tunnel.public_by",
                        entry.creatorName()).withStyle(ChatFormatting.GRAY));
            }
        }
        for (int i = 0; i < 5; i++) {
            if (ArcforgeGui.isInside(mouseX, mouseY, x + BUFFER_X + i * BUFFER_PITCH - 1, y + BUFFER_LABEL_Y - 1, BUFFER_PITCH - 2, 26)) {
                bufferTooltip(lines, i);
            }
        }
        for (int r = 0; r < QuantumResource.values().length; r++) {
            for (int c = 0; c < FACES.length; c++) {
                if (ArcforgeGui.isInside(mouseX, mouseY, x + GRID_X + c * CELL_PITCH_X, y + GRID_Y + r * CELL_PITCH_Y, CELL, CELL)) {
                    QuantumResource resource = QuantumResource.values()[r];
                    int mode = menu.mode(FACES[c], resource);
                    lines.add(Component.translatable("gui.arcforge.quantum_tunnel.cell", resource.displayName(),
                            Component.translatable("gui.arcforge.quantum_tunnel.face_name." + FACES[c].getSerializedName())));
                    lines.add(Component.translatable("gui.arcforge.quantum_tunnel.mode." + (mode == QuantumFaces.INPUT ? "input"
                            : mode == QuantumFaces.OUTPUT ? "output" : "none")).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (lines.isEmpty()) {
            tabs.addTooltip(lines, mouseX, mouseY);
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private void bufferTooltip(List<Component> lines, int index) {
        if (!menu.isLinked()) {
            lines.add(Component.translatable("gui.arcforge.quantum_tunnel.not_linked"));
            return;
        }
        switch (index) {
            case 0 -> lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.value(QuantumTunnelMenu.DATA_ENERGY)),
                    ArcforgeGui.grouped(menu.value(QuantumTunnelMenu.DATA_ENERGY_CAPACITY))));
            case 1 -> {
                lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.value(QuantumTunnelMenu.DATA_HEAT)),
                        ArcforgeGui.grouped(menu.value(QuantumTunnelMenu.DATA_HEAT_CAPACITY))));
                lines.add(Component.translatable("gui.arcforge.celsius", menu.value(QuantumTunnelMenu.DATA_TEMPERATURE)).withStyle(ChatFormatting.GRAY));
            }
            case 2 -> fluidLines(lines, menu.value(QuantumTunnelMenu.DATA_FLUID_ID), menu.value(QuantumTunnelMenu.DATA_FLUID),
                    menu.value(QuantumTunnelMenu.DATA_FLUID_CAPACITY), "gui.arcforge.quantum_tunnel.resource.fluid");
            case 3 -> fluidLines(lines, menu.value(QuantumTunnelMenu.DATA_GAS_ID), menu.value(QuantumTunnelMenu.DATA_GAS),
                    menu.value(QuantumTunnelMenu.DATA_GAS_CAPACITY), "gui.arcforge.quantum_tunnel.resource.gas");
            default -> {
                lines.add(Component.translatable("gui.arcforge.quantum_tunnel.slots_used", menu.value(QuantumTunnelMenu.DATA_ITEMS),
                        menu.value(QuantumTunnelMenu.DATA_ITEM_SLOTS)));
                lines.add(Component.translatable("gui.arcforge.quantum_tunnel.items_held", ArcforgeGui.grouped(menu.value(QuantumTunnelMenu.DATA_ITEM_COUNT)),
                        ArcforgeGui.grouped(menu.value(QuantumTunnelMenu.DATA_ITEM_CAPACITY))).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void fluidLines(List<Component> lines, int id, int amount, int capacity, String emptyKey) {
        Fluid fluid = id >= 0 ? BuiltInRegistries.FLUID.byId(id) : Fluids.EMPTY;
        lines.add(fluid != Fluids.EMPTY ? fluid.getFluidType().getDescription() : Component.translatable(emptyKey));
        lines.add(Component.translatable("gui.arcforge.mb_stored", ArcforgeGui.grouped(amount), ArcforgeGui.grouped(capacity)).withStyle(ChatFormatting.GRAY));
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int x = leftPos, y = topPos;
            if (ArcforgeGui.isInside(event.x(), event.y(), x + TOGGLE_X, y + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                makePrivate = !makePrivate;
                ArcforgeGui.playClickSound();
                return true;
            }
            if (ArcforgeGui.isInside(event.x(), event.y(), x + SET_X, y + BUTTONS_Y, SET_W, BUTTON_SIZE)) {
                set();
                return true;
            }
            QuantumStatePayload state = state();
            List<QuantumStatePayload.Entry> list = state.frequencies();
            for (int row = 0; row < ROWS && scroll + row < list.size(); row++) {
                QuantumStatePayload.Entry entry = list.get(scroll + row);
                int rowY = y + LIST_Y + row * ROW_H;
                if (entry.deletable() && ArcforgeGui.isInside(event.x(), event.y(), x + LIST_X + LIST_W - DELETE_SIZE - 3, rowY + 1, DELETE_SIZE, DELETE_SIZE)) {
                    send(QuantumFrequencyPayload.Action.DELETE, entry.name(), entry.owner());
                    return true;
                }
                if (ArcforgeGui.isInside(event.x(), event.y(), x + LIST_X, rowY, LIST_W, ROW_H)) {
                    boolean current = state.current().map(entry::is).orElse(false);
                    send(current ? QuantumFrequencyPayload.Action.CLEAR : QuantumFrequencyPayload.Action.SELECT, entry.name(), entry.owner());
                    return true;
                }
            }
            for (int r = 0; r < QuantumResource.values().length; r++) {
                for (int c = 0; c < FACES.length; c++) {
                    if (ArcforgeGui.isInside(event.x(), event.y(), x + GRID_X + c * CELL_PITCH_X, y + GRID_Y + r * CELL_PITCH_Y, CELL, CELL)) {
                        sendButton(QuantumTunnelMenu.faceButton(FACES[c], QuantumResource.values()[r]));
                        ArcforgeGui.playClickSound();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y, LIST_W, ROWS * ROW_H)) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, state().frequencies().size() - ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Enter sets; while typing, the inventory key types instead of closing the screen.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (name != null && name.isFocused()) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                set();
                return true;
            }
            if (event.key() != InputConstants.KEY_ESCAPE) {
                return name.keyPressed(event) || name.canConsumeInput() || super.keyPressed(event);
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
