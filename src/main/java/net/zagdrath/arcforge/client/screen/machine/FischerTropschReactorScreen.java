/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.FischerTropschReactorMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// 176x186 (the inventory 104 down). FE and the Syngas tank on the left, the catalyst slot (with the operations its dust
// has left under it), the arrow, the four product tanks (Naphtha, Light Oil, Heavy Oil, Water) and the heat buffer. The
// row under them: the status, the temperature and its window, and the heat bar with ticks at both ends of the window
// (200°C and 350°C). Positions are relative to leftPos/topPos.
public class FischerTropschReactorScreen extends MachineScreen<FischerTropschReactorMenu> {
    private static final int HEIGHT = 186;
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int SYNGAS_X = 25;
    private static final int[] PRODUCT_X = { 89, 105, 121, 137 };
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int BUFFER_X = 157, BUFFER_Y = 19;
    private static final int PROGRESS_X = 64, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int CATALYST_TEXT_X = 41, CATALYST_TEXT_Y = 57, CATALYST_TEXT_W = 44;
    private static final int LED_X = 9, LED_Y = 75;
    private static final int STATUS_X = 17, STATUS_Y = 75, STATUS_W = 82;
    private static final int TEMP_RIGHT = 167, TEMP_Y = 75;
    private static final int HEAT_X = 9, HEAT_Y = 88, HEAT_W = 158;
    private static final int TICK_Y = 85, TICK_W = 3, TICK_H = 7;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier minTempTick = sprite("min_temp_tick");
    private final Identifier ghostDust = sprite("ghost_dust");

    public FischerTropschReactorScreen(FischerTropschReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "fischer_tropsch_reactor", List.of(
                EnergyTab.usage(menu::getEnergy, menu::getUsage),
                new HeatTab(menu::getHeat,
                        Component.translatable("gui.arcforge.usage"),
                        () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()))), true, HEIGHT);
    }

    private static Fluid product(int index) {
        return switch (index) {
            case 0 -> ModFluids.NAPHTHA.get();
            case 1 -> ModFluids.LIGHT_OIL.get();
            case 2 -> ModFluids.HEAVY_OIL.get();
            default -> Fluids.WATER;
        };
    }

    private int productAmount(int index) {
        return switch (index) {
            case 0 -> menu.getNaphtha();
            case 1 -> menu.getLightOil();
            case 2 -> menu.getHeavyOil();
            default -> menu.getWater();
        };
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case ENERGY -> sideModeName("energy_input");
            case HEAT -> sideModeName("heat_input");
            case BYPRODUCT -> sideModeName("water_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        ghost(graphics, ghostDust, !menu.getSlot(0).hasItem(), FischerTropschReactorMenu.CATALYST_X, FischerTropschReactorMenu.CATALYST_Y);
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawGauge(graphics, heatBuffer, x, y, BUFFER_X, BUFFER_Y, menu.getHeat(), menu.getHeatCapacity());
        Fluid syngas = menu.getSyngasFluid();
        drawFluidTank(graphics, syngas == Fluids.EMPTY ? ModFluids.SYNGAS.get() : syngas, menu.getSyngas(), menu.getSyngasCapacity(), tankGauge, tankGauge,
                x, y, SYNGAS_X, TANK_Y, TANK_W, TANK_H);
        for (int i = 0; i < PRODUCT_X.length; i++) {
            drawFluidTank(graphics, product(i), productAmount(i), menu.getProductCapacity(), tankGauge, tankGauge, x, y, PRODUCT_X[i], TANK_Y, TANK_W, TANK_H);
        }
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        // White ticks at both ends of the window it works in.
        for (int celsius : new int[] { menu.getMinTemperature(), menu.getMaxTemperature() }) {
            int tickX = HEAT_X + HeatScale.fillWidth(HEAT_W, celsius) - 1;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, minTempTick, x + tickX, y + TICK_Y, TICK_W, TICK_H);
        }
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        if (menu.getCatalystLife() > 0) {
            graphics.text(font, clipped(Component.translatable("gui.arcforge.fischer_tropsch_reactor.catalyst", menu.getCatalystLeft()), CATALYST_TEXT_W),
                    CATALYST_TEXT_X, CATALYST_TEXT_Y, ArcforgeGui.LABEL, false);
        }
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", menu.getTemperature()), TEMP_RIGHT, TEMP_Y, ArcforgeGui.LABEL);
    }

    private MutableComponent windowLine() {
        return Component.translatable("gui.arcforge.fischer_tropsch_reactor.window", menu.getTemperature(), menu.getMinTemperature(), menu.getMaxTemperature());
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
            return;
        }
        if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            lines.add(windowLine().withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()).withStyle(ChatFormatting.GOLD));
            return;
        }
        if (isHovering(SYNGAS_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getSyngasFluid(), ModFluids.SYNGAS.get().getFluidType().getDescription(), menu.getSyngas(), menu.getSyngasCapacity());
            return;
        }
        for (int i = 0; i < PRODUCT_X.length; i++) {
            if (isHovering(PRODUCT_X[i] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
                addFluidTooltip(lines, product(i), product(i).getFluidType().getDescription(), productAmount(i), menu.getProductCapacity());
                return;
            }
        }
        if (isHovering(HEAT_X, TICK_Y, HEAT_W, TICK_H, mouseX, mouseY) || isHovering(TEMP_RIGHT - 40, TEMP_Y - 1, 40, 10, mouseX, mouseY)) {
            lines.add(windowLine());
        } else if (isHovering(CATALYST_TEXT_X, CATALYST_TEXT_Y - 1, CATALYST_TEXT_W, 10, mouseX, mouseY)
                || isHovering(FischerTropschReactorMenu.CATALYST_X, FischerTropschReactorMenu.CATALYST_Y, 16, 16, mouseX, mouseY) && !menu.getSlot(0).hasItem()) {
            lines.add(Component.translatable("gui.arcforge.fischer_tropsch_reactor.catalyst_hint"));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
