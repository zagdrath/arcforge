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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.DistillationArrayMenu;
import net.zagdrath.arcforge.recipe.DistillingRecipe;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.SteamGrade;

// Layout follows distillation_array_gui_layout.json: feed and steam tanks on the left, the screen, then the
// Naphtha, Light Oil and Heavy Oil tanks (the last two locked below the height that makes them) and the
// Pitch slot. All positions are relative to leftPos/topPos.
public class DistillationArrayScreen extends MachineScreen<DistillationArrayMenu> {
    private static final int FEED_X = 9, STEAM_X = 25, NAPHTHA_X = 97, LIGHT_X = 115, HEAVY_X = 133;
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int LIGHT_HEIGHT = 8, HEAVY_HEIGHT = 6;
    private static final int SCREEN_X = 46, TEMP_RIGHT = 87, TEMP_Y = 23, HEAT_Y = 34, HEAT_W = 42, BONUS_Y = 43;
    private static final int LED_X = 46, LED_Y = 56, STATUS_X = 54, STATUS_Y = 56;
    private static final int NO_STEAM_COLOR = 0xFF707070;

    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier locked = sprite("locked");

    public DistillationArrayScreen(DistillationArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "distillation_array", List.of(new HeatTab(menu::getHeat,
                Component.translatable("gui.arcforge.usage"),
                () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatPerTick()),
                Component.translatable("gui.arcforge.temp"),
                () -> Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())))), false);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("feed_input");
            case STEAM -> sideModeName("steam_input");
            case HEAT -> sideModeName("heat_input");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, menu.getFeed(), menu.getFeedAmount(), menu.getFeedCapacity(), sprite("creosote_fill"), tankGauge,
                x, y, FEED_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, menu.getSteam(), menu.getSteamAmount(), menu.getSteamCapacity(), sprite("steam_fill"), tankGauge,
                x, y, STEAM_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.NAPHTHA.get(), menu.getNaphtha(), menu.getOutputCapacity(), sprite("naphtha_fill"), tankGauge,
                x, y, NAPHTHA_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.LIGHT_OIL.get(), menu.getLightOil(), menu.getOutputCapacity(), sprite("light_oil_fill"), tankGauge,
                x, y, LIGHT_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.HEAVY_OIL.get(), menu.getHeavyOil(), menu.getOutputCapacity(), sprite("heavy_oil_fill"), tankGauge,
                x, y, HEAVY_X, TANK_Y, TANK_W, TANK_H);
        if (menu.getHeight() < LIGHT_HEIGHT) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, locked, x + LIGHT_X, y + TANK_Y, TANK_W, TANK_H);
        }
        if (menu.getHeight() < HEAVY_HEIGHT) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, locked, x + HEAVY_X, y + TANK_Y, TANK_W, TANK_H);
        }
        drawHeatBar(graphics, x, y, SCREEN_X, HEAT_Y, HEAT_W, menu.getTemperature());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        textRight(graphics, Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())), TEMP_RIGHT, TEMP_Y, ArcforgeGui.TEXT);
        SteamGrade grade = SteamGrade.of(menu.getSteam());
        if (menu.getBonus() > 0 && grade != null) {
            graphics.text(font, Component.translatable("gui.arcforge.naphtha_bonus_short", menu.getBonus()), SCREEN_X, BONUS_Y, grade.guiColor(), false);
        } else {
            graphics.text(font, Component.translatable("gui.arcforge.no_steam"), SCREEN_X, BONUS_Y, NO_STEAM_COLOR, false);
        }
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (overTank(FEED_X, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getFeed(), ModFluids.CREOSOTE_TYPE.get().getDescription(), menu.getFeedAmount(), menu.getFeedCapacity());
        } else if (overTank(STEAM_X, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getSteam(), Component.translatable("gui.arcforge.steam"), menu.getSteamAmount(), menu.getSteamCapacity());
            lines.add(Component.translatable("gui.arcforge.steam_stripping").withStyle(ChatFormatting.DARK_GRAY));
        } else if (overTank(NAPHTHA_X, mouseX, mouseY)) {
            addOutputTooltip(lines, ModFluids.NAPHTHA.get(), menu.getNaphtha(), 0);
        } else if (overTank(LIGHT_X, mouseX, mouseY)) {
            addOutputTooltip(lines, ModFluids.LIGHT_OIL.get(), menu.getLightOil(), LIGHT_HEIGHT);
        } else if (overTank(HEAVY_X, mouseX, mouseY)) {
            addOutputTooltip(lines, ModFluids.HEAVY_OIL.get(), menu.getHeavyOil(), HEAVY_HEIGHT);
        } else if (isHovering(SCREEN_X, TEMP_Y - 1, TEMP_RIGHT - SCREEN_X, 10, mouseX, mouseY)
                || isHovering(SCREEN_X, HEAT_Y - 2, HEAT_W, 8, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.column_temperature", ArcforgeGui.grouped(DistillingRecipe.DEFAULT_MIN_TEMP)));
        } else if (isHovering(SCREEN_X, BONUS_Y - 1, TEMP_RIGHT - SCREEN_X, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.steam_stripping"));
            lines.add(Component.translatable("gui.arcforge.naphtha_bonus", menu.getBonus()).withStyle(ChatFormatting.GRAY));
        }
    }

    private boolean overTank(int tankX, int mouseX, int mouseY) {
        return isHovering(tankX - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY);
    }

    // neededHeight: the column height that makes this fraction (0 if every column does).
    private void addOutputTooltip(List<Component> lines, Fluid fluid, int amount, int neededHeight) {
        if (menu.getHeight() < neededHeight) {
            lines.add(fluid.getFluidType().getDescription());
            lines.add(Component.translatable("gui.arcforge.needs_height", neededHeight).withStyle(ChatFormatting.GRAY));
        } else {
            addFluidTooltip(lines, fluid, fluid.getFluidType().getDescription(), amount, menu.getOutputCapacity());
        }
    }
}
