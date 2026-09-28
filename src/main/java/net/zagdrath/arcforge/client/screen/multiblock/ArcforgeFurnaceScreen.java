/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.menu.multiblock.ArcforgeFurnaceMenu;

// Layout follows arcforge_furnace_gui_layout.json. All positions are relative to leftPos/topPos.
public class ArcforgeFurnaceScreen extends MultiblockScreen<ArcforgeFurnaceMenu> {
    private static final int PROGRESS_X = 84, PROGRESS_Y = 24, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int HEAT_X = 12, HEAT_Y = 61, HEAT_W = 152, HEAT_H = 4;
    private static final int HEAT_LABEL_X = 13, HEAT_TEXT_Y = 51, HEAT_TEXT_RIGHT = 163;
    private static final int SCREEN_X = 8, SCREEN_Y = 46, SCREEN_W = 160, SCREEN_H = 24;
    private static final int OXYGEN_GAP = 8;

    private final Identifier progress = sprite("progress");
    private final Identifier heatBar = sprite("heat_bar");
    private final Identifier heatMarker = sprite("heat_marker");
    private final Identifier ghostIron = sprite("ghost_iron");
    private final Identifier ghostAdditive = sprite("ghost_additive");
    private final Identifier ghostCoke = sprite("ghost_coke");

    public ArcforgeFurnaceScreen(ArcforgeFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "arcforge_furnace", menu::getRedstoneMode, menu::getPorts);
        enableAutoEject(menu::isAutoEject);
    }

    @Override
    protected Component currentTitle() {
        return Component.translatable(menu.isFormed() ? "container.arcforge.arcforge_furnace" : "gui.arcforge.arcforge_furnace.incomplete");
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        if (menu.getProgressTotal() > 0 && menu.getProgress() > 0) {
            int arrow = Mth.ceil(PROGRESS_W * (float) menu.getProgress() / menu.getProgressTotal());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, progress, PROGRESS_W, PROGRESS_H, 0, 0,
                    x + PROGRESS_X, y + PROGRESS_Y, Math.min(arrow, PROGRESS_W), PROGRESS_H);
        }

        int heat = HeatScale.fillWidth(HEAT_W, menu.getHeat());
        if (heat > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, heatBar, HEAT_W, HEAT_H, 0, 0, x + HEAT_X, y + HEAT_Y, heat, HEAT_H);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, heatMarker, x + HEAT_X + heat - 1, y + HEAT_Y - 1, 2, 6);
        }

        ghost(graphics, ghostIron, !menu.getSlotFor(ArcforgeFurnaceBlockEntity.SLOT_METAL).hasItem(), ArcforgeFurnaceMenu.METAL_X, ArcforgeFurnaceMenu.METAL_Y);
        ghost(graphics, ghostAdditive, !menu.getSlotFor(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE).hasItem(), ArcforgeFurnaceMenu.ADDITIVE_X, ArcforgeFurnaceMenu.ADDITIVE_Y);
        ghost(graphics, ghostAdditive, !menu.getSlotFor(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE_2).hasItem(), ArcforgeFurnaceMenu.ADDITIVE_2_X, ArcforgeFurnaceMenu.ADDITIVE_2_Y);
        ghost(graphics, ghostCoke, !menu.getSlotFor(ArcforgeFurnaceBlockEntity.SLOT_COKE).hasItem(), ArcforgeFurnaceMenu.COKE_X, ArcforgeFurnaceMenu.COKE_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        graphics.text(font, Component.translatable("gui.arcforge.heat_label"), HEAT_LABEL_X, HEAT_TEXT_Y, ArcforgeGui.LABEL, false);
        Component celsius = Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getHeat()));
        graphics.text(font, celsius, HEAT_TEXT_RIGHT - font.width(celsius), HEAT_TEXT_Y, ArcforgeGui.TEXT, false);
        // Oxygen, right-aligned before the temperature: "O2 1,250 mB", then "×1.5" while this smelt is boosted.
        if (menu.getOxygen() > 0 || menu.getBoost() > 0) {
            int right = HEAT_TEXT_RIGHT - font.width(celsius) - OXYGEN_GAP;
            if (menu.getBoost() > 0) {
                Component boost = Component.translatable("gui.arcforge.furnace.oxygen_boost", ArcforgeFurnaceMenu.boostText(menu.getBoost()));
                right -= font.width(boost);
                graphics.text(font, boost, right, HEAT_TEXT_Y, ArcforgeGui.ACCENT, false);
                right -= 3;
            }
            Component oxygen = Component.translatable("gui.arcforge.furnace.oxygen", ArcforgeGui.grouped(menu.getOxygen()));
            graphics.text(font, oxygen, right - font.width(oxygen), HEAT_TEXT_Y, ArcforgeGui.LABEL, false);
        }
    }


    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(SCREEN_X, SCREEN_Y, SCREEN_W, SCREEN_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.arcforge_furnace.heat", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getMaxHeat())));
            lines.add(Component.translatable("gui.arcforge.arcforge_furnace.min_heat", ArcforgeGui.grouped(menu.getMinHeat())).withStyle(ChatFormatting.GRAY));
            if (menu.getBurnTime() > 0) {
                lines.add(Component.translatable("gui.arcforge.burn_time", (menu.getBurnTime() + 19) / 20).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
