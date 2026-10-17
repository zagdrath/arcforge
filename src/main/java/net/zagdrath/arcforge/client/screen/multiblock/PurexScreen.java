/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.PurexMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// The PUREX Plant (176×204, the inventory 122 down): the FE gauge and the Nitric Acid and solvent tanks on the left,
// the house screen (the PUREX / THOREX switch, the status, the fissile oxide carried over, the progress bar) and the
// waste tank on the right; under them the rods in (2×2) and the products out (3×2).
public class PurexScreen extends MachineScreen<PurexMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int TANK_Y = 17, TANK_W = 12, TANK_H = 52;
    private static final int ACID_X = 25, SOLVENT_X = 41, WASTE_X = 155;
    private static final int LEFT = 59, RIGHT = 148, ROW = 10;
    private static final int MODE_X = 59, MODE_Y = 18, MODE_W = 42, MODE_H = 12;
    private static final int PROGRESS_X = 59, PROGRESS_Y = 63, PROGRESS_W = 76;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier gauge = sprite("tank_gauge");
    private final Identifier liquidFill = sprite("liquid_fill");
    private final Identifier progressBar = sprite("progress");
    private final Identifier modeButton = sprite("mode");
    private final Identifier modeHover = sprite("mode_hover");
    private int mouseX, mouseY;

    public PurexScreen(PurexMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "purex_plant", List.of(), false, PurexMenu.HEIGHT);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case ENERGY -> sideModeName("energy_input");
            case INPUT -> sideModeName("rods_acid_and_solvent_input");
            case OUTPUT -> sideModeName("products_and_waste_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    private Fluid solvent() {
        int id = menu.get(PurexMenu.DATA_SOLVENT_FLUID);
        return id >= 0 ? BuiltInRegistries.FLUID.byId(id) : ModFluids.KEROSENE.get();
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.get(PurexMenu.DATA_ENERGY), menu.get(PurexMenu.DATA_ENERGY_CAPACITY));
        drawFluidTank(graphics, ModFluids.NITRIC_ACID.get(), menu.get(PurexMenu.DATA_ACID), menu.get(PurexMenu.DATA_ACID_CAPACITY), liquidFill, gauge, x, y,
                ACID_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, solvent(), menu.get(PurexMenu.DATA_SOLVENT), menu.get(PurexMenu.DATA_SOLVENT_CAPACITY), liquidFill, gauge, x, y, SOLVENT_X,
                TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.HIGH_LEVEL_WASTE.get(), menu.get(PurexMenu.DATA_WASTE), menu.get(PurexMenu.DATA_WASTE_CAPACITY), liquidFill, gauge, x,
                y, WASTE_X, TANK_Y, TANK_W, TANK_H);
        boolean over = isHovering(MODE_X, MODE_Y, MODE_W, MODE_H, mouseX, mouseY);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, over ? modeHover : modeButton, x + MODE_X, y + MODE_Y, MODE_W, MODE_H);
        int total = menu.get(PurexMenu.DATA_TOTAL);
        int width = total > 0 ? Math.min(PROGRESS_W, menu.get(PurexMenu.DATA_PROGRESS) * PROGRESS_W / total) : 0;
        if (width > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, progressBar, PROGRESS_W, 4, 0, 0, x + PROGRESS_X, y + PROGRESS_Y, width, 4);
        }
        drawLed(graphics, x, y, LEFT, MODE_Y + MODE_H + 4);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        Component mode = Component.translatable(menu.isThorex() ? "gui.arcforge.purex_plant.thorex" : "gui.arcforge.purex_plant.purex");
        graphics.text(font, mode, MODE_X + (MODE_W - font.width(mode)) / 2, MODE_Y + 2, ArcforgeGui.ACCENT, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), RIGHT - LEFT - 8), LEFT + 8, MODE_Y + MODE_H + 4, ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable(menu.isThorex() ? "gui.arcforge.purex_plant.u233_owed" : "gui.arcforge.purex_plant.pu_owed"), LEFT,
                MODE_Y + MODE_H + 4 + ROW, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.literal(String.format(Locale.ROOT, "%.2f", menu.get(PurexMenu.DATA_OWED) / 100.0)), RIGHT, MODE_Y + MODE_H + 4 + ROW,
                ArcforgeGui.TEXT);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.get(PurexMenu.DATA_ENERGY)),
                    ArcforgeGui.grouped(menu.get(PurexMenu.DATA_ENERGY_CAPACITY))).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", ArcforgeGui.grouped(menu.get(PurexMenu.DATA_USAGE))).withStyle(ChatFormatting.RED));
        } else if (isHovering(ACID_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.get(PurexMenu.DATA_ACID) > 0 ? ModFluids.NITRIC_ACID.get() : Fluids.EMPTY,
                    ModFluids.NITRIC_ACID_TYPE.get().getDescription(), menu.get(PurexMenu.DATA_ACID), menu.get(PurexMenu.DATA_ACID_CAPACITY));
        } else if (isHovering(SOLVENT_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.get(PurexMenu.DATA_SOLVENT) > 0 ? solvent() : Fluids.EMPTY, Component.translatable("gui.arcforge.purex_plant.solvent"),
                    menu.get(PurexMenu.DATA_SOLVENT), menu.get(PurexMenu.DATA_SOLVENT_CAPACITY));
        } else if (isHovering(WASTE_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.get(PurexMenu.DATA_WASTE) > 0 ? ModFluids.HIGH_LEVEL_WASTE.get() : Fluids.EMPTY,
                    ModFluids.HIGH_LEVEL_WASTE_TYPE.get().getDescription(), menu.get(PurexMenu.DATA_WASTE), menu.get(PurexMenu.DATA_WASTE_CAPACITY));
        } else if (isHovering(MODE_X, MODE_Y, MODE_W, MODE_H, mouseX, mouseY)) {
            lines.add(Component.translatable(menu.isThorex() ? "gui.arcforge.purex_plant.thorex_hint" : "gui.arcforge.purex_plant.purex_hint"));
            lines.add(Component.translatable("gui.arcforge.purex_plant.mode_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y - 2, PROGRESS_W, 8, mouseX, mouseY) && menu.get(PurexMenu.DATA_TOTAL) > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.get(PurexMenu.DATA_PROGRESS), menu.get(PurexMenu.DATA_TOTAL)));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && minecraft.gameMode != null && isHovering(MODE_X, MODE_Y, MODE_W, MODE_H, event.x(), event.y())) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PurexMenu.MODE);
            ArcforgeGui.playClickSound();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
