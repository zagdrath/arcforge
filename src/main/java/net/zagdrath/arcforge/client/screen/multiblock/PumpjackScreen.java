/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.PumpjackMenu;
import net.zagdrath.arcforge.oil.DepositKind;
import net.zagdrath.arcforge.registry.ModFluids;

// The Electric Pump's frame: the FE gauge on the left, the screen (the deposit under it, how fast it pumps, how much of
// the deposit is left as a bar, the status), the bucket slots and the Crude Oil tank on the right. All positions are
// relative to leftPos/topPos.
public class PumpjackScreen extends MachineScreen<PumpjackMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int TANK_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int LABEL_X = 31, VALUE_RIGHT = 121, DEPOSIT_Y = 23, RATE_Y = 34;
    private static final int BAR_X = 31, BAR_Y = 45, BAR_W = 90, BAR_H = 4;
    private static final int LED_X = 31, LED_Y = 56, STATUS_X = 39, STATUS_Y = 56, STATUS_W = 82;
    private static final int BAR_EMPTY = 0xFF0E0E0E, TRICKLE = 0xFF8E231C;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier crudeFill = sprite("crude_fill");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier ghostBucket = sprite("ghost_bucket");

    public PumpjackScreen(PumpjackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "pumpjack", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)), false);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case ENERGY -> sideModeName("energy_input");
            case OUTPUT -> sideModeName("crude_oil_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawFluidTank(graphics, menu.getOil(), menu.getCrude(), menu.getCrudeCapacity(), crudeFill, tankGauge,
                x, y, TANK_X, TANK_Y, TANK_W, TANK_H);
        graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, BAR_EMPTY);
        if (menu.getKind() != null) {
            if (menu.getLeft() > 0) {
                int width = Math.min(BAR_W, Mth.ceil(BAR_W * (float) menu.getLeft() / Math.max(1, menu.getAmount())));
                graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + width, y + BAR_Y + BAR_H, ArcforgeGui.ACCENT);
            } else {
                graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + 2, y + BAR_Y + BAR_H, TRICKLE);
            }
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
        ghost(graphics, ghostBucket, !menu.getBucketInSlot().hasItem(), PumpjackMenu.BUCKET_IN_X, PumpjackMenu.BUCKET_IN_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.pumpjack.deposit"), LABEL_X, DEPOSIT_Y, ArcforgeGui.LABEL, false);
        DepositKind kind = menu.getKind();
        Component name = kind != null ? kind.getDescription() : Component.translatable("gui.arcforge.none");
        textRight(graphics, clipped(name, VALUE_RIGHT - LABEL_X - font.width(Component.translatable("gui.arcforge.pumpjack.deposit")) - 4),
                VALUE_RIGHT, DEPOSIT_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.rate"), LABEL_X, RATE_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.pumpjack.rate", String.format(Locale.ROOT, "%.1f", menu.getRate())),
                VALUE_RIGHT, RATE_Y, ArcforgeGui.TEXT);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(TANK_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getOil(), menu.getOil().getFluidType().getDescription(), menu.getCrude(), menu.getCrudeCapacity());
        } else if (isHovering(BAR_X, BAR_Y - 2, BAR_W, BAR_H + 4, mouseX, mouseY) && menu.getKind() != null) {
            lines.add(menu.getLeft() > 0
                    ? Component.translatable("gui.arcforge.pumpjack.left", ArcforgeGui.grouped(menu.getLeft()), ArcforgeGui.grouped(menu.getAmount()))
                    : Component.translatable("gui.arcforge.pumpjack.trickle"));
        } else if (isHovering(LED_X, STATUS_Y - 1, VALUE_RIGHT - LED_X, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
