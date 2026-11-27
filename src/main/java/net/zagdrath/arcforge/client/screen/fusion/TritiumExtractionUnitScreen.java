/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.fusion;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.client.screen.machine.ArcCrusherScreen;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.fusion.TritiumExtractionUnitMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// The Tritium Extraction Unit's GUI (textures/gui/container/tritium_extraction_unit.png): the heat buffer on the left,
// the rod, the arrow and the emptied cladding, the Tritium and Helium tanks on the right, the temperature bar with a
// tick at what it needs, and the status.
public class TritiumExtractionUnitScreen extends MachineScreen<TritiumExtractionUnitMenu> {
    private static final int BUFFER_X = 9, BUFFER_Y = 19;
    private static final int TRITIUM_X = 143, HELIUM_X = 157, TANK_Y = 19;
    private static final int PROGRESS_X = 68, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 43, LED_Y = 56;
    private static final int STATUS_X = 51, STATUS_Y = 56, STATUS_W = 88;
    private static final int HEAT_X = 43, HEAT_Y = 66, HEAT_W = 91;
    private static final int TICK_Y = 63, TICK_W = 3, TICK_H = 7;

    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier progress = sprite("progress");
    private final Identifier minTempTick = sprite("min_temp_tick");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier gasFill = sprite("gas_fill");

    public TritiumExtractionUnitScreen(TritiumExtractionUnitMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "tritium_extraction_unit", List.of(new HeatTab(() -> menu.get(TritiumExtractionUnitMenu.DATA_HEAT),
                Component.translatable("gui.arcforge.usage"),
                () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.get(TritiumExtractionUnitMenu.DATA_HEAT_USAGE)))));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case HEAT -> sideModeName("heat_input");
            case GAS_OUTPUT -> sideModeName("tritium_output");
            case BYPRODUCT -> sideModeName("helium_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, heatBuffer, x, y, BUFFER_X, BUFFER_Y, menu.get(TritiumExtractionUnitMenu.DATA_HEAT), menu.get(TritiumExtractionUnitMenu.DATA_HEAT_CAPACITY));
        int tank = Math.max(1, menu.get(TritiumExtractionUnitMenu.DATA_TANK));
        drawFluidTank(graphics, ModFluids.TRITIUM.get(), menu.get(TritiumExtractionUnitMenu.DATA_TRITIUM), tank, gasFill, tankGauge, x, y, TRITIUM_X, TANK_Y,
                GAUGE_W, GAUGE_H);
        drawFluidTank(graphics, ModFluids.HELIUM.get(), menu.get(TritiumExtractionUnitMenu.DATA_HELIUM), tank, gasFill, tankGauge, x, y, HELIUM_X, TANK_Y,
                GAUGE_W, GAUGE_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.get(TritiumExtractionUnitMenu.DATA_PROGRESS),
                menu.get(TritiumExtractionUnitMenu.DATA_TOTAL));
        drawLed(graphics, x, y, LED_X, LED_Y);
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.get(TritiumExtractionUnitMenu.DATA_TEMPERATURE));
        int tickX = HEAT_X + HeatScale.fillWidth(HEAT_W, menu.get(TritiumExtractionUnitMenu.DATA_MIN_TEMP)) - 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, minTempTick, x + tickX, y + TICK_Y, TICK_W, TICK_H);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    private Component temperatureLine() {
        return Component.translatable("gui.arcforge.celsius_needs", menu.get(TritiumExtractionUnitMenu.DATA_TEMPERATURE),
                Component.translatable("gui.arcforge.min_temp", menu.get(TritiumExtractionUnitMenu.DATA_MIN_TEMP)));
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        int tank = Math.max(1, menu.get(TritiumExtractionUnitMenu.DATA_TANK));
        if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.get(TritiumExtractionUnitMenu.DATA_HEAT)),
                    ArcforgeGui.grouped(menu.get(TritiumExtractionUnitMenu.DATA_HEAT_CAPACITY))));
            lines.add(temperatureLine().copy().withStyle(ChatFormatting.GRAY));
        } else if (isHovering(TRITIUM_X - 1, TANK_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            int amount = menu.get(TritiumExtractionUnitMenu.DATA_TRITIUM);
            addFluidTooltip(lines, amount > 0 ? ModFluids.TRITIUM.get() : Fluids.EMPTY, Component.translatable("fluid_type.arcforge.tritium"), amount, tank);
        } else if (isHovering(HELIUM_X - 1, TANK_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            int amount = menu.get(TritiumExtractionUnitMenu.DATA_HELIUM);
            addFluidTooltip(lines, amount > 0 ? ModFluids.HELIUM.get() : Fluids.EMPTY, Component.translatable("fluid_type.arcforge.helium"), amount, tank);
        } else if (isHovering(HEAT_X, TICK_Y, HEAT_W, TICK_H, mouseX, mouseY)) {
            lines.add(temperatureLine());
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.get(TritiumExtractionUnitMenu.DATA_TOTAL) > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.get(TritiumExtractionUnitMenu.DATA_PROGRESS),
                    menu.get(TritiumExtractionUnitMenu.DATA_TOTAL)));
        }
    }
}
