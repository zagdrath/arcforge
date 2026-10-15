/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.blockentity.machine.RefiningMachineBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.client.gui.tab.SideTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.RefiningMenu;
import net.zagdrath.arcforge.recipe.RefiningProcess;

// The refinery units' screen, 176x186 (the inventory 104 down), laid out like the Fischer-Tropsch Reactor's: FE (the
// Blender, the Hydrotreater) and the feed tank on the left, then the second feed tank where the unit has one, the
// catalyst slot (the Cracking Unit, with the operations its item has left under it), the arrow, the product tanks (and
// the Coker's coke slot), and the heat buffer on the right. Under them the status, the temperature and the heat bar
// with a tick at the least temperature the recipe needs. Each unit's background (gui/container/<unit>.png) shows only
// the parts it has.
public class RefiningScreen extends MachineScreen<RefiningMenu> {
    private static final int HEIGHT = 186;
    private static final int ENERGY_X = 9, GAUGE_Y = 19;
    private static final int[] PRODUCT_X = { 89, 105, 121 };
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int BUFFER_X = 157;
    private static final int PROGRESS_X = 64, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int CATALYST_TEXT_X = 41, CATALYST_TEXT_Y = 57, CATALYST_TEXT_W = 44;
    private static final int LED_X = 9, LED_Y = 75;
    private static final int STATUS_X = 17, STATUS_Y = 75, STATUS_W = 100;
    private static final int TEMP_RIGHT = 167, TEMP_Y = 75;
    private static final int HEAT_X = 9, HEAT_Y = 88, HEAT_W = 158;
    private static final int TICK_Y = 85, TICK_W = 3, TICK_H = 7;

    private final RefiningProcess process;
    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier minTempTick = sprite("min_temp_tick");
    private final Identifier ghostCatalyst = sprite("ghost_catalyst");

    public RefiningScreen(RefiningMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, RefiningMachineBlockEntity.name(menu.getProcess()), tabs(menu), true, HEIGHT);
        this.process = menu.getProcess();
    }

    private static List<SideTab> tabs(RefiningMenu menu) {
        List<SideTab> tabs = new ArrayList<>();
        if (menu.getProcess().powered()) {
            tabs.add(EnergyTab.usage(menu::getEnergy, menu::getUsage));
        }
        if (menu.getProcess().heated()) {
            tabs.add(new HeatTab(menu::getHeat, Component.translatable("gui.arcforge.usage"),
                    () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage())));
        }
        return tabs;
    }

    private int feedX() {
        return process.powered() ? 25 : 9;
    }

    private int secondX() {
        return feedX() + 16;
    }

    private Fluid productShown(int i) {
        Fluid held = menu.getProductFluid(i);
        return held != Fluids.EMPTY ? held : process.products().get(i);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case ENERGY -> sideModeName("energy_input");
            case HEAT -> sideModeName("heat_input");
            case INPUT -> sideModeName("feed_input");
            case GAS_OUTPUT -> switch (process) {
                case REFORMING -> sideModeName("hydrogen");
                case HYDROTREATING -> sideModeName("hydrogen_sulfide");
                case SULFUR_RECOVERY -> sideModeName("steam_output");
                case STEAM_REFORMING -> sideModeName("carbon_dioxide");
                default -> sideModeName("refinery_gas");
            };
            case HYDROGEN -> process == RefiningProcess.STEAM_REFORMING ? sideModeName("hydrogen") : sideModeName("hydrogen_input");
            case OXYGEN -> sideModeName("oxygen_input");
            case STEAM -> sideModeName("steam_input");
            case OUTPUT -> process == RefiningProcess.SULFUR_RECOVERY ? sideModeName("sulfur_output") : super.sideModeName(mode);
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        if (process.catalysed()) {
            ghost(graphics, ghostCatalyst, !menu.getSlot(0).hasItem(), RefiningMenu.CATALYST_X, RefiningMenu.CATALYST_Y);
        }
        if (process.powered()) {
            drawGauge(graphics, energyBar, x, y, ENERGY_X, GAUGE_Y, menu.getEnergy(), menu.getCapacity());
        }
        if (process.heated()) {
            drawGauge(graphics, heatBuffer, x, y, BUFFER_X, GAUGE_Y, menu.getHeat(), menu.getHeatCapacity());
        }
        Fluid feed = menu.getFeedFluid();
        drawFluidTank(graphics, feed, menu.getFeed(), menu.getFeedCapacity(), tankGauge, tankGauge, x, y, feedX(), TANK_Y, TANK_W, TANK_H);
        if (process.secondFeed() != null) {
            drawFluidTank(graphics, menu.getSecondFluid(), menu.getSecond(), menu.getSecondCapacity(), tankGauge, tankGauge, x, y, secondX(), TANK_Y, TANK_W,
                    TANK_H);
        }
        for (int i = 0; i < process.tanks(); i++) {
            drawFluidTank(graphics, productShown(i), menu.getProduct(i), menu.getProductCapacity(), tankGauge, tankGauge, x, y, PRODUCT_X[i], TANK_Y,
                    TANK_W, TANK_H);
        }
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        if (process.heated()) {
            drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
            if (menu.getMinTemperature() > 0) {
                int tickX = HEAT_X + HeatScale.fillWidth(HEAT_W, menu.getMinTemperature()) - 1;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, minTempTick, x + tickX, y + TICK_Y, TICK_W, TICK_H);
            }
        }
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        if (process.catalysed() && menu.getCatalystLife() > 0) {
            graphics.text(font, clipped(Component.translatable("gui.arcforge.refining.catalyst", menu.getCatalystLeft()), CATALYST_TEXT_W),
                    CATALYST_TEXT_X, CATALYST_TEXT_Y, ArcforgeGui.LABEL, false);
        }
        graphics.text(font, clipped(menu.getStatus().getDescription(), process.heated() ? STATUS_W - 30 : STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT,
                false);
        if (process.heated()) {
            textRight(graphics, Component.translatable("gui.arcforge.celsius", menu.getTemperature()), TEMP_RIGHT, TEMP_Y, ArcforgeGui.LABEL);
        }
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (process.powered() && isHovering(ENERGY_X - 1, GAUGE_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
            return;
        }
        if (process.heated() && isHovering(BUFFER_X - 1, GAUGE_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            if (menu.getMinTemperature() > 0) {
                lines.add(Component.translatable("gui.arcforge.refining.min_temperature", menu.getMinTemperature()).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()).withStyle(ChatFormatting.GOLD));
            return;
        }
        if (isHovering(feedX() - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getFeedFluid(), Component.translatable("gui.arcforge.refining.feed"), menu.getFeed(), menu.getFeedCapacity());
            return;
        }
        if (process.secondFeed() != null && isHovering(secondX() - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getSecondFluid(), Component.translatable("gui.arcforge.refining.second_feed." + process.secondFeed().getSerializedName()),
                    menu.getSecond(), menu.getSecondCapacity());
            return;
        }
        for (int i = 0; i < process.tanks(); i++) {
            if (isHovering(PRODUCT_X[i] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
                Fluid shown = productShown(i);
                addFluidTooltip(lines, menu.getProductFluid(i), shown.getFluidType().getDescription(), menu.getProduct(i), menu.getProductCapacity());
                return;
            }
        }
        if (process.heated() && (isHovering(HEAT_X, TICK_Y, HEAT_W, TICK_H, mouseX, mouseY) || isHovering(TEMP_RIGHT - 40, TEMP_Y - 1, 40, 10, mouseX, mouseY))) {
            lines.add(Component.translatable("gui.arcforge.refining.temperature", menu.getTemperature(), menu.getMinTemperature()));
        } else if (process.catalysed() && (isHovering(CATALYST_TEXT_X, CATALYST_TEXT_Y - 1, CATALYST_TEXT_W, 10, mouseX, mouseY)
                || isHovering(RefiningMenu.CATALYST_X, RefiningMenu.CATALYST_Y, 16, 16, mouseX, mouseY) && !menu.getSlot(0).hasItem())) {
            lines.add(Component.translatable("gui.arcforge.refining.catalyst_hint"));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
