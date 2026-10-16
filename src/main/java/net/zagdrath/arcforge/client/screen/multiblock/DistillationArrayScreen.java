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
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity.Product;
import net.zagdrath.arcforge.steam.SteamGrade;

// Feed and steam tanks on the left, the screen (temperature, heat bar, steam stripping, status), the Pitch slot and the
// column's size and feed rate under it, then a narrow tank for each product, lightest first (Refinery Gas, Naphtha,
// Kerosene, Light Oil, Diesel, Heavy Oil, Bitumen); a product the column's recipe doesn't make at its height is locked.
// All positions are relative to leftPos/topPos; the background is 232×172 (tools/distillation_array_gui_gen.py), the
// player inventory centred.
public class DistillationArrayScreen extends MachineScreen<DistillationArrayMenu> {
    private static final int FEED_X = 9, STEAM_X = 25, PRODUCT_X = 137, PRODUCT_PITCH = 13;
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50, PRODUCT_W = 8;
    // The screen recess is x40..133, y18..52 (inner 41..132 x 19..51); text keeps 2 px off its inner edges.
    private static final int SCREEN_X = 43, TEMP_RIGHT = 132, TEMP_Y = 21, HEAT_X = 45, HEAT_Y = 31, HEAT_W = 84, BONUS_Y = 36;
    private static final int LED_X = 43, LED_Y = 44, STATUS_X = 51, STATUS_Y = 44, STATUS_W = TEMP_RIGHT - STATUS_X;
    private static final int INFO_X = 61, SIZE_Y = 56, RATE_Y = 65, INFO_W = TEMP_RIGHT - INFO_X;
    private static final int NO_STEAM_COLOR = 0xFF707070;

    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier productGauge = sprite("product_gauge");
    private final Identifier productFill = sprite("product_fill");
    private final Identifier locked = sprite("product_locked");

    public DistillationArrayScreen(DistillationArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "distillation_array", List.of(new HeatTab(menu::getHeat,
                Component.translatable("gui.arcforge.usage"),
                () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatPerTick()),
                Component.translatable("gui.arcforge.temp"),
                () -> Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())))), true,
                DistillationArrayMenu.WIDTH, DistillationArrayMenu.HEIGHT, true);
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
        for (Product product : Product.values()) {
            int tankX = productX(product);
            drawFluidTank(graphics, menu.getProductFluid(product), menu.getProduct(product), menu.getOutputCapacity(), productFill, productGauge,
                    x, y, tankX, TANK_Y, PRODUCT_W, TANK_H);
            if (!menu.makes(product) && menu.getProduct(product) <= 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, locked, x + tankX, y + TANK_Y, PRODUCT_W, TANK_H);
            }
        }
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    private static int productX(Product product) {
        return PRODUCT_X + PRODUCT_PITCH * product.ordinal();
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        textRight(graphics, Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())), TEMP_RIGHT, TEMP_Y, ArcforgeGui.TEXT);
        SteamGrade grade = SteamGrade.of(menu.getSteam());
        if (menu.getBonus() > 0 && grade != null) {
            graphics.text(font, clipped(Component.translatable("gui.arcforge.naphtha_bonus_short", menu.getBonus()), TEMP_RIGHT - SCREEN_X), SCREEN_X,
                    BONUS_Y, grade.guiColor(), false);
        } else {
            graphics.text(font, clipped(Component.translatable("gui.arcforge.no_steam"), TEMP_RIGHT - SCREEN_X), SCREEN_X, BONUS_Y, NO_STEAM_COLOR, false);
        }
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        graphics.text(font, clipped(sizeText(), INFO_W), INFO_X, SIZE_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(Component.translatable("gui.arcforge.mb_per_tick", ArcforgeGui.grouped(menu.getFeedRate())), INFO_W),
                INFO_X, RATE_Y, ArcforgeGui.ACCENT, false);
    }

    private Component sizeText() {
        return Component.translatable("gui.arcforge.distillation_array.size", menu.getWidth(), menu.getHeight());
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (overTank(FEED_X, TANK_W, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getFeed(), Component.translatable("gui.arcforge.distillation_array.feed"), menu.getFeedAmount(),
                    menu.getFeedCapacity());
            return;
        }
        if (overTank(STEAM_X, TANK_W, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getSteam(), Component.translatable("gui.arcforge.steam"), menu.getSteamAmount(), menu.getSteamCapacity());
            lines.add(Component.translatable("gui.arcforge.steam_stripping").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (Product product : Product.values()) {
            if (overTank(productX(product), PRODUCT_W, mouseX, mouseY)) {
                addOutputTooltip(lines, product);
                return;
            }
        }
        if (isHovering(SCREEN_X, TEMP_Y - 1, TEMP_RIGHT - SCREEN_X, 10, mouseX, mouseY)
                || isHovering(HEAT_X, HEAT_Y - 2, HEAT_W, 8, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.column_temperature", ArcforgeGui.grouped(DistillingRecipe.DEFAULT_MIN_TEMP)));
        } else if (isHovering(SCREEN_X, BONUS_Y - 1, TEMP_RIGHT - SCREEN_X, 9, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.steam_stripping"));
            lines.add(Component.translatable("gui.arcforge.naphtha_bonus", menu.getBonus()).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(LED_X, STATUS_Y - 1, TEMP_RIGHT - LED_X, 9, mouseX, mouseY)) {
            // The status can be clipped to fit the screen; the tooltip has it in full.
            lines.add(menu.getStatus().getDescription());
        } else if (isHovering(INFO_X, SIZE_Y - 1, INFO_W, RATE_Y - SIZE_Y + 9, mouseX, mouseY)) {
            lines.add(sizeText());
            lines.add(Component.translatable("gui.arcforge.distillation_array.rate", ArcforgeGui.grouped(menu.getFeedRate()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private boolean overTank(int tankX, int width, int mouseX, int mouseY) {
        return isHovering(tankX - 1, TANK_Y - 1, width + 2, TANK_H + 2, mouseX, mouseY);
    }

    // A product the column doesn't make (at its height, from its feed) says so instead of showing an empty tank.
    private void addOutputTooltip(List<Component> lines, Product product) {
        Fluid fluid = menu.getProductFluid(product);
        int amount = menu.getProduct(product);
        addFluidTooltip(lines, fluid, fluid.getFluidType().getDescription(), amount, menu.getOutputCapacity());
        if (!menu.makes(product)) {
            lines.add(Component.translatable("gui.arcforge.distillation_array.not_made").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
