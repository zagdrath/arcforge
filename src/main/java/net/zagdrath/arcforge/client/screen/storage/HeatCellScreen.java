/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.storage;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.RedstoneTab;
import net.zagdrath.arcforge.client.gui.tab.SideConfigTab;
import net.zagdrath.arcforge.client.gui.tab.UpgradesTab;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.storage.HeatCellMenu;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Layout follows heat_cell_gui_layout.json. All positions are relative to leftPos/topPos.
public class HeatCellScreen extends StorageScreen<HeatCellMenu> {
    private static final int RECESS_X = 30, RECESS_Y = 16, RECESS_W = 16, RECESS_H = 56;
    private static final int FILL_X = 31, FILL_Y = 17, FILL_W = 14, FILL_H = 54;
    private static final int TEXT_LEFT = 55, TEXT_RIGHT = 163;
    private static final int STORED_Y = 21, TEMP_Y = 31, IN_OUT_Y = 50, LEAK_Y = 61;
    private static final int BAR_X = 55, BAR_Y = 41, BAR_W = 108, BAR_H = 4;
    private static final int PIPS_X = 140, PIPS_Y = 62, PIP_PITCH = 6, PIP_SIZE = 5;
    private static final int OUT_COLOR = 0xFFFF5555, LEAK_COLOR = 0xFFB0A090;

    private final Identifier heatFill = sprite("heat_fill");
    private final Identifier heatBar = sprite("heat_bar");
    private final Identifier heatMarker = sprite("heat_marker");
    private final Identifier pipOff = sprite("insulation_pip_off");

    public HeatCellScreen(HeatCellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "heat_cell");
        tabs.add(new RedstoneTab(menu::getRedstoneMode, mode -> sendButton(MachineMenuButtons.redstoneButtonId(mode))))
                .add(new SideConfigTab(menu::getSideMode,
                        (side, action) -> sendButton(MachineMenuButtons.sideButtonId(side, action)),
                        () -> sendButton(MachineMenuButtons.CLEAR_SIDES)))
                .add(new UpgradesTab(List.of(menu.getUpgradeSlot())).withInfo(() -> Component.translatable("gui.arcforge.heat_cell.leak_multiplier",
                        String.format(Locale.ROOT, "%.2f", UpgradeType.leakMultiplier(menu.getInsulation())))));
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        // Cropped from the bottom (not scaled) so the segment lines stay put.
        int height = scaledRound(menu.getHeat(), menu.getCapacity(), FILL_H);
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, heatFill, FILL_W, FILL_H, 0, FILL_H - height,
                    x + FILL_X, y + FILL_Y + FILL_H - height, FILL_W, height);
        }

        int width = HeatScale.fillWidth(BAR_W, menu.getTemperature());
        if (width > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, heatBar, BAR_W, BAR_H, 0, 0, x + BAR_X, y + BAR_Y, width, BAR_H);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, heatMarker, x + BAR_X + width - 1, y + BAR_Y - 1, 2, BAR_H + 2);
        }

        // One lit pip per insulation level, in the tier's colour.
        ConduitTier tier = menu.getTier();
        Identifier pipOn = sprite("insulation_pip_" + tier.getSerializedName());
        for (int i = 0; i < ConduitTier.values().length; i++) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, i <= tier.ordinal() ? pipOn : pipOff,
                    x + PIPS_X + i * PIP_PITCH, y + PIPS_Y, PIP_SIZE, PIP_SIZE);
        }
    }

    @Override
    protected void drawInfo(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.stored"), TEXT_LEFT, STORED_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.hu_stored_compact", compact(menu.getHeat()), compact(menu.getCapacity())), STORED_Y, ArcforgeGui.WHITE);
        graphics.text(font, Component.translatable("gui.arcforge.temp"), TEXT_LEFT, TEMP_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", menu.getTemperature()), TEMP_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.heat_cell.in", menu.getReceivedPerTick()), TEXT_LEFT, IN_OUT_Y, ArcforgeGui.HEAT, false);
        textRight(graphics, Component.translatable("gui.arcforge.heat_cell.out", menu.getExtractedPerTick()), IN_OUT_Y, OUT_COLOR);
        graphics.text(font, Component.translatable("gui.arcforge.heat_cell.leak", menu.getLeakPerTick()), TEXT_LEFT, LEAK_Y, LEAK_COLOR, false);
    }

    private void textRight(GuiGraphicsExtractor graphics, Component text, int y, int color) {
        graphics.text(font, text, TEXT_RIGHT - font.width(text), y, color, false);
    }

    // "124K", "3.2M".
    private static String compact(int value) {
        return ArcforgeGui.compact(value).toUpperCase(Locale.ROOT);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(RECESS_X, RECESS_Y, RECESS_W, RECESS_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getCapacity())));
            lines.add(Component.translatable("gui.arcforge.celsius", menu.getTemperature()).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(PIPS_X, PIPS_Y, ConduitTier.values().length * PIP_PITCH, PIP_SIZE, mouseX, mouseY)) {
            ConduitTier tier = menu.getTier();
            int insulation = menu.getInsulation();
            String leak = HeatCellBlockEntity.leakPercentText(HeatCellBlockEntity.leakPercentPerMinute(tier, insulation));
            lines.add(insulation > 0
                    ? Component.translatable("gui.arcforge.insulation_upgraded", tier.getDisplayName(), insulation, leak)
                    : Component.translatable("gui.arcforge.insulation", tier.getDisplayName(), leak));
        }
    }
}
