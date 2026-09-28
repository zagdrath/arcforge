/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.quarry.BlockFilter;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.menu.machine.ArcQuarryConfigMenu;
import net.zagdrath.arcforge.network.ArcQuarryAreaPayload;
import net.zagdrath.arcforge.network.ArcQuarryTagPayload;

// The Arc Quarry's settings (gui_layouts.json "config", 176x236): radius and Y range boxes, the 9x2 block filter
// (ghost cells, each with a tag chip), a tag box with suggestions, the switches, and the last scan. Everything
// shown comes from the quarry as synced; changes go to the server (menu buttons and payloads), which clamps them
// and syncs them back. While it's open the area outline shows in the world (see RangeOutlineRenderer).
public class ArcQuarryConfigScreen extends AbstractContainerScreen<ArcQuarryConfigMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/arc_quarry_config.png");
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final int RADIUS_X = 9, MIN_Y_X = 63, MAX_Y_X = 117, FIELD_Y = 27, FIELD_W = 50, FIELD_H = 12;
    private static final int LABEL_Y = 16;
    private static final int GRID_X = 8, GRID_Y = 47, PITCH = 18, COLUMNS = 9;
    private static final int CHIP_W = 8, CHIP_H = 6;
    private static final int TAG_X = 8, TAG_Y = 87, TAG_W = 112, TAG_H = 12, SUGGESTIONS = 6;
    private static final int LIST_MODE_X = 124, SILK_X = 148, TOGGLES_Y1 = 84;
    private static final int REPLACE_X = 8, SHOW_X = 30, SCAN_X = 52, BACK_X = 148, TOGGLES_Y2 = 102, BUTTON_SIZE = 20;
    private static final int SCAN_X_TEXT = 11, SCAN_Y1 = 128, SCAN_Y2 = 138, SCAN_W = 154;
    private static final int HOVER = 0x80FFFFFF;
    private static final int BAD_TAG = 0xFFFF5555;
    private static final int SUGGESTION_BG = 0xF0101010;

    private @Nullable EditBox radius, minY, maxY, tag;
    private @Nullable EditBox lastFocused;
    private List<Identifier> suggestions = List.of();

    public ArcQuarryConfigScreen(ArcQuarryConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 236);
        this.inventoryLabelY = ArcQuarryConfigMenu.INVENTORY_Y - 11;
    }

    private @Nullable ArcQuarryBlockEntity quarry() {
        return minecraft != null && minecraft.player != null ? menu.quarry(minecraft.player) : null;
    }

    private QuarrySettings settings() {
        ArcQuarryBlockEntity quarry = quarry();
        return quarry != null ? quarry.getSettings() : QuarrySettings.defaults();
    }

    @Override
    public void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        QuarrySettings settings = settings();
        radius = numberBox(RADIUS_X, settings.radius(), "gui.arcforge.quarry.radius");
        minY = numberBox(MIN_Y_X, settings.minY(), "gui.arcforge.quarry.min_y");
        maxY = numberBox(MAX_Y_X, settings.maxY(), "gui.arcforge.quarry.max_y");
        tag = new EditBox(font, leftPos + TAG_X + 2, topPos + TAG_Y + 2, TAG_W - 4, TAG_H - 2, Component.translatable("gui.arcforge.quarry.tag_hint"));
        tag.setBordered(false);
        tag.setMaxLength(128);
        tag.setHint(Component.translatable("gui.arcforge.quarry.tag_hint").withStyle(ChatFormatting.DARK_GRAY));
        tag.setResponder(text -> {
            suggestions = BlockFilter.suggest(text, SUGGESTIONS);
            Identifier id = parseTag(text);
            tag.setTextColor(text.isEmpty() || id != null && BlockFilter.tagExists(id) ? ArcforgeGui.TEXT : BAD_TAG);
        });
        addRenderableWidget(tag);
    }

    private EditBox numberBox(int x, int value, String name) {
        EditBox box = new EditBox(font, leftPos + x + 2, topPos + FIELD_Y + 2, FIELD_W - 4, FIELD_H - 2, Component.translatable(name));
        box.setBordered(false);
        box.setMaxLength(6);
        box.setValue(Integer.toString(value));
        addRenderableWidget(box);
        return box;
    }

    private static @Nullable Identifier parseTag(String text) {
        String trimmed = text.trim();
        return Identifier.tryParse(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed);
    }

    // --- Sending changes ---

    private static int parse(@Nullable EditBox box, int fallback) {
        if (box == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // Sends the three number boxes (the server clamps them).
    private void sendArea() {
        QuarrySettings settings = settings();
        int r = parse(radius, settings.radius());
        int lo = parse(minY, settings.minY());
        int hi = parse(maxY, settings.maxY());
        if (r != settings.radius() || lo != settings.minY() || hi != settings.maxY()) {
            ClientPacketDistributor.sendToServer(new ArcQuarryAreaPayload(menu.containerId, r, lo, hi));
        }
    }

    private void sendTag() {
        if (tag == null) {
            return;
        }
        Identifier id = parseTag(tag.getValue());
        if (id != null && BlockFilter.tagExists(id)) {
            ClientPacketDistributor.sendToServer(new ArcQuarryTagPayload(menu.containerId, id.toString()));
            tag.setValue("");
        }
    }

    private void sendButton(int id) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
        ArcforgeGui.playClickSound();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        EditBox focused = getFocused() instanceof EditBox box ? box : null;
        // Leaving a number box sends it.
        if (lastFocused != null && lastFocused != focused && lastFocused != tag) {
            sendArea();
        }
        lastFocused = focused;
        // Boxes not being edited follow what the server says (after clamping).
        QuarrySettings settings = settings();
        follow(radius, settings.radius(), focused);
        follow(minY, settings.minY(), focused);
        follow(maxY, settings.maxY(), focused);
    }

    private static void follow(@Nullable EditBox box, int value, @Nullable EditBox focused) {
        if (box != null && box != focused && !box.getValue().equals(Integer.toString(value))) {
            box.setValue(Integer.toString(value));
        }
    }

    // --- Layout ---

    private static int cellX(int cell) {
        return GRID_X + (cell % COLUMNS) * PITCH;
    }

    private static int cellY(int cell) {
        return GRID_Y + (cell / COLUMNS) * PITCH;
    }

    private int cellAt(double mouseX, double mouseY) {
        for (int cell = 0; cell < QuarrySettings.FILTER_SIZE; cell++) {
            if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + cellX(cell) - 1, topPos + cellY(cell) - 1, PITCH, PITCH)) {
                return cell;
            }
        }
        return -1;
    }

    // The tag chip of a set cell with an item (bottom-left corner), or -1.
    private int chipAt(double mouseX, double mouseY) {
        QuarrySettings settings = settings();
        for (int cell = 0; cell < QuarrySettings.FILTER_SIZE; cell++) {
            if (settings.entry(cell).item().isPresent()
                    && ArcforgeGui.isInside(mouseX, mouseY, leftPos + cellX(cell), topPos + cellY(cell) + 16 - CHIP_H, CHIP_W, CHIP_H)) {
                return cell;
            }
        }
        return -1;
    }

    private int buttonAt(double mouseX, double mouseY) {
        if (over(mouseX, mouseY, LIST_MODE_X, TOGGLES_Y1)) return ArcQuarryConfigMenu.BUTTON_LIST_MODE;
        if (over(mouseX, mouseY, SILK_X, TOGGLES_Y1)) return ArcQuarryConfigMenu.BUTTON_SILK;
        if (over(mouseX, mouseY, REPLACE_X, TOGGLES_Y2)) return ArcQuarryConfigMenu.BUTTON_REPLACE;
        if (over(mouseX, mouseY, SHOW_X, TOGGLES_Y2)) return ArcQuarryConfigMenu.BUTTON_SHOW_AREA;
        if (over(mouseX, mouseY, SCAN_X, TOGGLES_Y2)) return ArcQuarryConfigMenu.BUTTON_SCAN;
        if (over(mouseX, mouseY, BACK_X, TOGGLES_Y2)) return ArcQuarryConfigMenu.BUTTON_BACK;
        return -1;
    }

    private boolean over(double mouseX, double mouseY, int x, int y) {
        return ArcforgeGui.isInside(mouseX, mouseY, leftPos + x, topPos + y, BUTTON_SIZE, BUTTON_SIZE);
    }

    // The suggestion under the mouse, or -1.
    private int suggestionAt(double mouseX, double mouseY) {
        if (tag == null || !tag.isFocused()) {
            return -1;
        }
        for (int i = 0; i < suggestions.size(); i++) {
            if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + TAG_X, topPos + TAG_Y + TAG_H + i * 10, TAG_W, 10)) {
                return i;
            }
        }
        return -1;
    }

    // --- Rendering ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        QuarrySettings settings = settings();
        int hovered = cellAt(mouseX, mouseY);
        for (int cell = 0; cell < QuarrySettings.FILTER_SIZE; cell++) {
            FilterSettings.Entry entry = settings.entry(cell);
            int cx = x + cellX(cell);
            int cy = y + cellY(cell);
            if (BlockFilter.isSet(entry)) {
                ItemStack icon = BlockFilter.icon(entry);
                if (!icon.isEmpty()) {
                    graphics.fakeItem(icon, cx, cy);
                }
                if (entry.item().isPresent()) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.sprite("container/conduit_filter/" + (entry.tag().isPresent() ? "match_tag" : "match_exact")),
                            cx, cy + 16 - CHIP_H, CHIP_W, CHIP_H);
                }
            }
            if (cell == hovered) {
                graphics.fill(cx, cy, cx + 16, cy + 16, HOVER);
            }
        }
        drawButton(graphics, LIST_MODE_X, TOGGLES_Y1, "container/conduit_filter/" + (settings.deny() ? "filter_deny" : "filter_allow"), mouseX, mouseY);
        drawButton(graphics, SILK_X, TOGGLES_Y1, "container/arc_quarry/" + (settings.silkTouch() ? "silk_on" : "silk_off"), mouseX, mouseY);
        drawButton(graphics, REPLACE_X, TOGGLES_Y2, "container/arc_quarry/" + (settings.replace() ? "replace_on" : "replace_off"), mouseX, mouseY);
        drawButton(graphics, SHOW_X, TOGGLES_Y2, "container/vacuum_collector/" + (settings.showArea() ? "show_range" : "hide_range"), mouseX, mouseY);
        drawButton(graphics, SCAN_X, TOGGLES_Y2, "container/arc_quarry/scan", mouseX, mouseY);
        drawButton(graphics, BACK_X, TOGGLES_Y2, "container/arc_quarry/back", mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int bx, int by, String icon, int mouseX, int mouseY) {
        int x = leftPos + bx;
        int y = topPos + by;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, over(mouseX, mouseY, bx, by) ? BUTTON_HOVER : BUTTON, x, y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.sprite(icon), x + 2, y + 2, 16, 16);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.quarry.radius"), RADIUS_X - 1, LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.quarry.min_y"), MIN_Y_X - 1, LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.quarry.max_y"), MAX_Y_X - 1, LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.literal(font.plainSubstrByWidth(scanLine().getString(), SCAN_W)), SCAN_X_TEXT, SCAN_Y1, ArcforgeGui.TEXT, false);
        String top = topCounts(3);
        if (!top.isEmpty()) {
            String clipped = font.plainSubstrByWidth(top, SCAN_W);
            graphics.text(font, Component.literal(clipped.length() < top.length() ? font.plainSubstrByWidth(top, SCAN_W - font.width("…")) + "…" : top),
                    SCAN_X_TEXT, SCAN_Y2, ArcforgeGui.LABEL, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // Suggestions over everything, under the tag box.
        if (tag != null && tag.isFocused() && !suggestions.isEmpty()) {
            int x = leftPos + TAG_X;
            int y = topPos + TAG_Y + TAG_H;
            graphics.fill(x, y, x + TAG_W, y + suggestions.size() * 10, SUGGESTION_BG);
            int hovered = suggestionAt(mouseX, mouseY);
            for (int i = 0; i < suggestions.size(); i++) {
                graphics.text(font, Component.literal(font.plainSubstrByWidth("#" + suggestions.get(i), TAG_W - 4)), x + 2, y + 1 + i * 10,
                        i == hovered ? ArcforgeGui.ACCENT : ArcforgeGui.TEXT, false);
            }
        }
    }

    private Component scanLine() {
        ArcQuarryBlockEntity quarry = quarry();
        if (quarry == null) {
            return Component.translatable("gui.arcforge.quarry.not_scanned");
        }
        if (quarry.getQuarryState() == ArcQuarryBlockEntity.State.SCANNING) {
            return Component.translatable("gui.arcforge.quarry.scanning", quarry.scanPercent());
        }
        if (quarry.isStale() && quarry.getScanCounts().isEmpty()) {
            return Component.translatable("gui.arcforge.quarry.not_scanned");
        }
        return Component.translatable("gui.arcforge.quarry.scan_result", ArcforgeGui.grouped(quarry.getTargetCount()));
    }

    private List<Map.Entry<Block, Integer>> sortedCounts() {
        ArcQuarryBlockEntity quarry = quarry();
        if (quarry == null) {
            return List.of();
        }
        List<Map.Entry<Block, Integer>> counts = new ArrayList<>(quarry.getScanCounts().entrySet());
        counts.sort(Map.Entry.<Block, Integer>comparingByValue(Comparator.reverseOrder()));
        return counts;
    }

    private String topCounts(int limit) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Block, Integer> entry : sortedCounts()) {
            if (parts.size() >= limit) {
                break;
            }
            parts.add(entry.getKey().getName().getString() + " " + ArcforgeGui.grouped(entry.getValue()));
        }
        return String.join(" · ", parts);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        addTooltip(lines, mouseX, mouseY);
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (suggestionAt(mouseX, mouseY) >= 0) {
            return;
        }
        QuarrySettings settings = settings();
        int cell = Math.max(cellAt(mouseX, mouseY), chipAt(mouseX, mouseY));
        if (cell >= 0) {
            FilterSettings.Entry entry = settings.entry(cell);
            if (!BlockFilter.isSet(entry)) {
                lines.add(Component.translatable("gui.arcforge.conduit_filter.empty_item").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (entry.item().isPresent()) {
                lines.add(entry.item().get().create().getHoverName());
            }
            lines.add(entry.tag()
                    .<Component>map(id -> Component.translatable("gui.arcforge.conduit_filter.match_tag", id.toString()))
                    .orElseGet(() -> Component.translatable("gui.arcforge.conduit_filter.match_exact"))
                    .copy().withStyle(ChatFormatting.GRAY));
            if (entry.item().isPresent()) {
                lines.add(Component.translatable("gui.arcforge.conduit_filter.chip_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }
        int perBlock = ArcforgeConfig.QUARRY_ENERGY_PER_BLOCK.getAsInt();
        switch (buttonAt(mouseX, mouseY)) {
            case ArcQuarryConfigMenu.BUTTON_LIST_MODE -> {
                lines.add(Component.translatable(settings.deny() ? "gui.arcforge.conduit_filter.denylist" : "gui.arcforge.conduit_filter.allowlist"));
                lines.add(Component.translatable(settings.deny() ? "gui.arcforge.conduit_filter.denylist.desc" : "gui.arcforge.conduit_filter.allowlist.desc")
                        .withStyle(ChatFormatting.GRAY));
            }
            case ArcQuarryConfigMenu.BUTTON_SILK -> lines.add(settings.silkTouch()
                    ? Component.translatable("gui.arcforge.quarry.silk_on", ArcforgeGui.grouped(Math.round(perBlock * ArcforgeConfig.QUARRY_SILK_MULTIPLIER.getAsDouble())))
                    : Component.translatable("gui.arcforge.quarry.silk_off", ArcforgeGui.grouped(perBlock)));
            case ArcQuarryConfigMenu.BUTTON_REPLACE -> lines.add(Component.translatable(settings.replace() ? "gui.arcforge.quarry.replace_on" : "gui.arcforge.quarry.replace_off"));
            case ArcQuarryConfigMenu.BUTTON_SHOW_AREA -> lines.add(Component.translatable(settings.showArea() ? "gui.arcforge.quarry.hide_area" : "gui.arcforge.quarry.show_area"));
            case ArcQuarryConfigMenu.BUTTON_SCAN -> lines.add(Component.translatable("gui.arcforge.quarry.scan"));
            case ArcQuarryConfigMenu.BUTTON_BACK -> lines.add(Component.translatable("gui.arcforge.quarry.back"));
            default -> {
                if (isHovering(SCAN_X_TEXT - 4, SCAN_Y1 - 4, SCAN_W + 8, 22, mouseX, mouseY)) {
                    for (Map.Entry<Block, Integer> entry : sortedCounts()) {
                        lines.add(Component.literal(entry.getKey().getName().getString() + " " + ArcforgeGui.grouped(entry.getValue())));
                    }
                    ArcQuarryBlockEntity quarry = quarry();
                    if (quarry != null && quarry.getUnscanned() > 0) {
                        lines.add(Component.literal(ArcforgeGui.grouped(quarry.getUnscanned()) + " unloaded").withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int suggestion = suggestionAt(event.x(), event.y());
        if (suggestion >= 0 && tag != null) {
            tag.setValue("#" + suggestions.get(suggestion));
            ArcforgeGui.playClickSound();
            return true;
        }
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        int chip = chipAt(event.x(), event.y());
        if (chip >= 0 && (left || right)) {
            sendButton((left ? ArcQuarryConfigMenu.BUTTON_TAG_NEXT : ArcQuarryConfigMenu.BUTTON_TAG_PREVIOUS) + chip);
            return true;
        }
        int cell = cellAt(event.x(), event.y());
        if (cell >= 0 && (left || right)) {
            // Left sets from the carried block (or clears with an empty hand); right clears.
            sendButton((right ? ArcQuarryConfigMenu.BUTTON_CLEAR_CELL : ArcQuarryConfigMenu.BUTTON_CELL) + cell);
            return true;
        }
        int button = left ? buttonAt(event.x(), event.y()) : -1;
        if (button >= 0) {
            sendArea();
            sendButton(button);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        EditBox focused = getFocused() instanceof EditBox box ? box : null;
        if (focused != null && focused.canConsumeInput()) {
            if (event.isEscape()) {
                return super.keyPressed(event);
            }
            if (focused == tag && event.key() == InputConstants.KEY_TAB && !suggestions.isEmpty()) {
                tag.setValue("#" + suggestions.get(0));
                return true;
            }
            if (event.isConfirmation()) {
                if (focused == tag) {
                    sendTag();
                } else {
                    sendArea();
                }
                return true;
            }
            focused.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    // The scroll wheel over a number box changes it by 1 (Shift: 5) and sends it.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (EditBox box : new EditBox[] { radius, minY, maxY }) {
            if (box != null && box.isMouseOver(mouseX, mouseY) && scrollY != 0) {
                int step = (minecraft.hasShiftDown() ? 5 : 1) * (scrollY > 0 ? 1 : -1);
                box.setValue(Integer.toString(parse(box, 0) + step));
                sendArea();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        sendArea();
        super.removed();
    }
}
