/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.CentrifugeCascadeMenu;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.registry.ModFluids;

// The FE gauge on the left; the house screen with the centrifuges and the separative work they do, the target and tails
// assays with - and + buttons (shift for a bigger step), the feed and SWU each mB of product takes, and the status; and
// the feed, product and tails tanks on the right, all the full height of the top. All positions are relative to
// leftPos/topPos; the background is 216×186, the player inventory 104 down and centred.
public class CentrifugeCascadeScreen extends MachineScreen<CentrifugeCascadeMenu> {
    private static final int WIDTH = 216, HEIGHT = 186;
    private static final int ENERGY_X = 9, ENERGY_Y = 19, ENERGY_H = 72;
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 72;
    private static final int[] TANK_X = { 163, 179, 195 };
    private static final int LEFT = 27, RIGHT = 155;
    private static final int CENTRIFUGES_Y = 21, WORK_Y = 31, TARGET_Y = 43, TAILS_Y = 55, COST_Y = 67;
    private static final int LED_X = 27, LED_Y = 81, STATUS_X = 35, STATUS_Y = 81, STATUS_W = 120;
    // The - and + buttons, 7 px square, either side of the value.
    private static final int MINUS_X = 114, PLUS_X = 148, BUTTON = 7;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier gasFill = sprite("gas_fill");
    private final Identifier minus = sprite("button_minus");
    private final Identifier minusHover = sprite("button_minus_hover");
    private final Identifier plus = sprite("button_plus");
    private final Identifier plusHover = sprite("button_plus_hover");
    private int mouseX, mouseY;

    public CentrifugeCascadeScreen(CentrifugeCascadeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "centrifuge_cascade", List.of(), false, WIDTH, HEIGHT, true);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("feed_input");
            case OUTPUT -> sideModeName("product_output");
            case ENERGY -> sideModeName("energy_input");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawEnergy(graphics, x, y);
        drawFluidTank(graphics, ModFluids.URANIUM_HEXAFLUORIDE.get(), menu.getFeed(), menu.getFeedCapacity(), gasFill, tankGauge,
                x, y, TANK_X[0], TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.URANIUM_HEXAFLUORIDE.get(), menu.getProduct(), menu.getProductCapacity(), gasFill, tankGauge,
                x, y, TANK_X[1], TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.DEPLETED_URANIUM_HEXAFLUORIDE.get(), menu.getTails(), menu.getTailsCapacity(), gasFill, tankGauge,
                x, y, TANK_X[2], TANK_Y, TANK_W, TANK_H);
        for (int rowY : new int[] { TARGET_Y, TAILS_Y }) {
            button(graphics, x, y, MINUS_X, rowY, minus, minusHover);
            button(graphics, x, y, PLUS_X, rowY, plus, plusHover);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    // The FE gauge, as tall as the tanks (its sprite is too), filled from the bottom.
    private void drawEnergy(GuiGraphicsExtractor graphics, int x, int y) {
        int height = scaled(menu.getEnergy(), menu.getEnergyCapacity(), ENERGY_H);
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, energyBar, GAUGE_W, ENERGY_H, 0, ENERGY_H - height, x + ENERGY_X, y + ENERGY_Y + ENERGY_H - height,
                    GAUGE_W, height);
        }
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int buttonX, int rowY, Identifier normal, Identifier hovered) {
        boolean over = isHovering(buttonX, rowY, BUTTON, BUTTON, mouseX, mouseY);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, over ? hovered : normal, x + buttonX, y + rowY, BUTTON, BUTTON);
    }

    private void label(GuiGraphicsExtractor graphics, String key, int y) {
        graphics.text(font, Component.translatable("gui.arcforge.centrifuge_cascade." + key), LEFT, y, ArcforgeGui.LABEL, false);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        label(graphics, "centrifuges", CENTRIFUGES_Y);
        textRight(graphics, Component.literal(Integer.toString(menu.getCentrifuges())), RIGHT, CENTRIFUGES_Y, ArcforgeGui.TEXT);
        label(graphics, "work", WORK_Y);
        textRight(graphics, Component.translatable("gui.arcforge.centrifuge_cascade.swu_per_tick", String.format(Locale.ROOT, "%.2f", menu.getSwu())),
                RIGHT, WORK_Y, menu.getSwu() > 0 ? ArcforgeGui.ACCENT : ArcforgeGui.TEXT);
        label(graphics, "target", TARGET_Y);
        centred(graphics, Enrichment.percent(menu.getTarget()), TARGET_Y + 0, ArcforgeGui.WHITE);
        label(graphics, "tails", TAILS_Y);
        centred(graphics, Enrichment.percent(menu.getTailsAssay()), TAILS_Y + 0, ArcforgeGui.TEXT);
        double feed = menu.feedPerProduct();
        Component cost = Double.isInfinite(feed) ? Component.translatable("gui.arcforge.centrifuge_cascade.cannot")
                : Component.translatable("gui.arcforge.centrifuge_cascade.cost", String.format(Locale.ROOT, "%.1f", feed),
                        String.format(Locale.ROOT, "%.1f", menu.swuPerProduct()));
        graphics.text(font, clipped(cost, RIGHT - LEFT), LEFT, COST_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    // A value between the - and + buttons.
    private void centred(GuiGraphicsExtractor graphics, String text, int y, int color) {
        int middle = (MINUS_X + BUTTON + PLUS_X) / 2;
        graphics.text(font, text, middle - font.width(text) / 2, y, color, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, ENERGY_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getEnergyCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", ArcforgeGui.grouped(menu.getEnergyUse())).withStyle(ChatFormatting.RED));
        } else if (isHovering(TANK_X[0] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            tank(lines, "feed", menu.getFeed(), menu.getFeedCapacity(), menu.getFeedAssay());
        } else if (isHovering(TANK_X[1] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            tank(lines, "product", menu.getProduct(), menu.getProductCapacity(), menu.getProduct() > 0 ? menu.getProductAssay() : menu.getTarget());
        } else if (isHovering(TANK_X[2] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            tank(lines, "tails_tank", menu.getTails(), menu.getTailsCapacity(), menu.getTailsAssay());
        } else if (isHovering(LEFT, WORK_Y - 1, RIGHT - LEFT, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.centrifuge_cascade.capacity", String.format(Locale.ROOT, "%.2f", menu.getSwuCapacity())));
        } else if (isHovering(LEFT, TARGET_Y - 1, RIGHT - LEFT, 22, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.centrifuge_cascade.assay_hint"));
            lines.add(Component.translatable("gui.arcforge.centrifuge_cascade.civil").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(LEFT, COST_Y - 1, RIGHT - LEFT, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.centrifuge_cascade.cost_hint"));
        } else if (isHovering(LED_X, STATUS_Y - 1, RIGHT - LED_X, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }

    private static void tank(List<Component> lines, String key, int amount, int capacity, int assay) {
        lines.add(Component.translatable("gui.arcforge.centrifuge_cascade." + key));
        lines.add(Component.translatable("gui.arcforge.mb_stored", ArcforgeGui.grouped(amount), ArcforgeGui.grouped(capacity)).withStyle(ChatFormatting.GRAY));
        if (assay > 0) {
            lines.add(Component.translatable("gui.arcforge.centrifuge_cascade.assay", Enrichment.percent(assay)).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Left or right click, as on a Sides tab.
        if ((event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) && minecraft.gameMode != null) {
            boolean big = Minecraft.getInstance().hasShiftDown();
            int id = -1;
            if (isHovering(MINUS_X, TARGET_Y, BUTTON, BUTTON, event.x(), event.y())) {
                id = big ? CentrifugeCascadeMenu.TARGET_DOWN_BIG : CentrifugeCascadeMenu.TARGET_DOWN;
            } else if (isHovering(PLUS_X, TARGET_Y, BUTTON, BUTTON, event.x(), event.y())) {
                id = big ? CentrifugeCascadeMenu.TARGET_UP_BIG : CentrifugeCascadeMenu.TARGET_UP;
            } else if (isHovering(MINUS_X, TAILS_Y, BUTTON, BUTTON, event.x(), event.y())) {
                id = big ? CentrifugeCascadeMenu.TAILS_DOWN_BIG : CentrifugeCascadeMenu.TAILS_DOWN;
            } else if (isHovering(PLUS_X, TAILS_Y, BUTTON, BUTTON, event.x(), event.y())) {
                id = big ? CentrifugeCascadeMenu.TAILS_UP_BIG : CentrifugeCascadeMenu.TAILS_UP;
            }
            if (id >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                ArcforgeGui.playClickSound();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
