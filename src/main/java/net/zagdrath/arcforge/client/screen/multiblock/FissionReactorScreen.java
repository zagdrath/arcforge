/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.block.nuclear.ControlRodChannelBlock;
import net.zagdrath.arcforge.blockentity.multiblock.FissionReactorBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.FissionReactorMenu;
import net.zagdrath.arcforge.nuclear.reactor.ReactorSim;

// The Fission Reactor's control desk (176×254, the player inventory 172 down), three pages on its screen:
//  - Core: the readings down the left (thermal power, k-effective, reactivity, period, the hottest fuel and cladding,
//    the coolant, pressure, xenon, decay heat), the four rod banks on the right (each a bar of how far in it is with a
//    marker at its target, and - out / + in buttons; shift for 10%), the status, and Page, Reset and SCRAM;
//  - Trends: the last minute of power (log scale), coolant temperature and k-effective;
//  - Protection: each trip point on or off with - and +, the coolant pump, Redstone Rods, auto-unload and Unload All.
// Under the desk the nine rod input slots and the nine output slots. All positions are relative to leftPos/topPos.
public class FissionReactorScreen extends MachineScreen<FissionReactorMenu> {
    private enum Page { CORE, TRENDS, PROTECTION }

    private static final int LEFT = 10, VALUE_RIGHT = 88, LINE = 10, TOP = 19;
    private static final int BANK_X = 96, BANK_PITCH = 18, BANK_W = 12, BANK_LABEL_Y = 18, BAR_Y = 27, BAR_H = 44, BUTTON_Y = 74, PERCENT_Y = 83;
    private static final int STATUS_Y = 94, LED_X = 94;
    private static final int PAGE_X = 94, RESET_X = 108, SCRAM_X = 138, CONTROL_Y = 104, CONTROL_H = 12, PAGE_W = 12, WIDE = 28;
    private static final int BUTTON = 7;
    private static final int GRAPH_X = 10, GRAPH_W = 156, GRAPH_Y = 19, GRAPH_H = 25, GRAPH_GAP = 3;
    private static final int TRIP_Y = 19, TRIP_MINUS = 136, TRIP_PLUS = 146, TOGGLE_X = 10;
    private static final int[] TRIP_COLORS = { 0xFF5FD4C4, 0xFFFF9A3C, 0xFFE0E0E0 };

    private final Identifier bankFill = sprite("bank_fill");
    private final Identifier bankMarker = sprite("bank_marker");
    private final Identifier minus = sprite("button_minus");
    private final Identifier minusHover = sprite("button_minus_hover");
    private final Identifier plus = sprite("button_plus");
    private final Identifier plusHover = sprite("button_plus_hover");
    private final Identifier scram = sprite("scram");
    private final Identifier scramHover = sprite("scram_hover");
    private final Identifier reset = sprite("reset");
    private final Identifier resetHover = sprite("reset_hover");
    private final Identifier page = sprite("page");
    private final Identifier pageHover = sprite("page_hover");
    private final Identifier toggleOn = sprite("toggle_on");
    private final Identifier toggleOff = sprite("toggle_off");
    private Page shown = Page.CORE;
    private int mouseX, mouseY;

    public FissionReactorScreen(FissionReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "fission_reactor", List.of(), false, FissionReactorMenu.HEIGHT);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("coolant_and_rods_input");
            case OUTPUT -> sideModeName("rods_output");
            case STEAM -> sideModeName("steam_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    private boolean over(int x, int y, int w, int h) {
        return isHovering(x, y, w, h, mouseX, mouseY);
    }

    private void blit(GuiGraphicsExtractor graphics, Identifier normal, Identifier hovered, int x, int y, int bx, int by, int w, int h) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, over(bx, by, w, h) ? hovered : normal, x + bx, y + by, w, h);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        switch (shown) {
            case CORE -> {
                for (int bank = 0; bank < ReactorSim.BANKS; bank++) {
                    int bx = BANK_X + bank * BANK_PITCH;
                    int filled = (int) Math.round(menu.insertion(bank) * BAR_H);
                    if (filled > 0) {
                        // Rods go in from the top: the bar fills downwards.
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bankFill, BANK_W, BAR_H, 0, 0, x + bx, y + BAR_Y, BANK_W, filled);
                    }
                    int marker = BAR_Y + (int) Math.round(menu.target(bank) * BAR_H);
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bankMarker, x + bx - 2, y + marker - 1, BANK_W + 4, 3);
                    blit(graphics, minus, minusHover, x, y, bx - 1, BUTTON_Y, BUTTON, BUTTON);
                    blit(graphics, plus, plusHover, x, y, bx + 6, BUTTON_Y, BUTTON, BUTTON);
                }
                drawLed(graphics, x, y, LED_X, STATUS_Y);
            }
            case TRENDS -> {
                graph(graphics, x, y, 0, GRAPH_Y, -9000, 500, TRIP_COLORS[0]);
                graph(graphics, x, y, 1, GRAPH_Y + GRAPH_H + GRAPH_GAP + 9, 200, 4_000, TRIP_COLORS[1]);
                graph(graphics, x, y, 2, GRAPH_Y + 2 * (GRAPH_H + GRAPH_GAP + 9), 90_000, 110_000, TRIP_COLORS[2]);
            }
            case PROTECTION -> {
                for (int trip = 0; trip < 5; trip++) {
                    int rowY = TRIP_Y + trip * LINE;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.tripOn(trip) ? toggleOn : toggleOff, x + TOGGLE_X, y + rowY, BUTTON, BUTTON);
                    blit(graphics, minus, minusHover, x, y, TRIP_MINUS, rowY, BUTTON, BUTTON);
                    blit(graphics, plus, plusHover, x, y, TRIP_PLUS, rowY, BUTTON, BUTTON);
                }
                int pumpY = TRIP_Y + 5 * LINE + 4;
                blit(graphics, minus, minusHover, x, y, TRIP_MINUS, pumpY, BUTTON, BUTTON);
                blit(graphics, plus, plusHover, x, y, TRIP_PLUS, pumpY, BUTTON, BUTTON);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.flag(FissionReactorMenu.FLAG_REDSTONE_RODS) ? toggleOn : toggleOff,
                        x + TOGGLE_X, y + pumpY + LINE, BUTTON, BUTTON);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.flag(FissionReactorMenu.FLAG_AUTO_UNLOAD) ? toggleOn : toggleOff,
                        x + TOGGLE_X, y + pumpY + 2 * LINE, BUTTON, BUTTON);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.flag(FissionReactorMenu.FLAG_UNLOADING) ? toggleOn : toggleOff,
                        x + TOGGLE_X, y + pumpY + 3 * LINE, BUTTON, BUTTON);
            }
        }
        blit(graphics, page, pageHover, x, y, PAGE_X, CONTROL_Y, PAGE_W, CONTROL_H);
        blit(graphics, reset, resetHover, x, y, RESET_X, CONTROL_Y, WIDE, CONTROL_H);
        blit(graphics, scram, scramHover, x, y, SCRAM_X, CONTROL_Y, WIDE, CONTROL_H);
    }

    // One trend: the last minute (newest on the right) between low and high, as a 1 px line on the screen's floor.
    private void graph(GuiGraphicsExtractor graphics, int x, int y, int series, int top, int low, int high, int color) {
        int count = FissionReactorBlockEntity.HISTORY;
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            int ago = count - 1 - i;
            int value = menu.history(series, ago);
            double t = Math.max(0.0, Math.min(1.0, (value - low) / (double) (high - low)));
            int px = x + GRAPH_X + (int) Math.round(i * (GRAPH_W - 1) / (double) (count - 1));
            int py = y + top + GRAPH_H - 1 - (int) Math.round(t * (GRAPH_H - 1));
            if (lastY != Integer.MIN_VALUE) {
                graphics.fill(px - 2, Math.min(lastY, py), px + 1, Math.max(lastY, py) + 1, color);
            }
            lastY = py;
        }
    }

    private Component number(String format, double value) {
        return Component.literal(String.format(Locale.ROOT, format, value));
    }

    private void reading(GuiGraphicsExtractor graphics, int line, String key, Component value, int color) {
        int y = TOP + line * LINE;
        graphics.text(font, Component.translatable("gui.arcforge.fission_reactor." + key), LEFT, y, ArcforgeGui.LABEL, false);
        textRight(graphics, value, VALUE_RIGHT, y, color);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        switch (shown) {
            case CORE -> drawCore(graphics);
            case TRENDS -> drawTrends(graphics);
            case PROTECTION -> drawProtection(graphics);
        }
        textCentred(graphics, Component.translatable("gui.arcforge.fission_reactor.reset"), RESET_X, WIDE, CONTROL_Y + 2, ArcforgeGui.TEXT);
        textCentred(graphics, Component.translatable("gui.arcforge.fission_reactor.scram"), SCRAM_X, WIDE, CONTROL_Y + 2, ArcforgeGui.WHITE);
    }

    private void textCentred(GuiGraphicsExtractor graphics, Component text, int left, int width, int y, int color) {
        graphics.text(font, text, left + (width - font.width(text)) / 2, y, color, false);
    }

    private void drawCore(GuiGraphicsExtractor graphics) {
        double power = menu.power();
        boolean critical = power > 0.01;
        reading(graphics, 0, "power", Component.literal(power >= 0.001 ? String.format(Locale.ROOT, "%.1f%%", power * 100.0)
                : String.format(Locale.ROOT, "%.0e", power * 100.0) + "%"), critical ? ArcforgeGui.ACCENT : ArcforgeGui.TEXT);
        double k = menu.k();
        reading(graphics, 1, "k", number("%.4f", k), k > 1.0 + 0.0065 ? ArcforgeGui.HEAT : ArcforgeGui.TEXT);
        double rho = menu.rho();
        reading(graphics, 2, "rho", Component.literal(String.format(Locale.ROOT, "%+.0f", rho * 1.0E5) + " pcm"), rho > 0.0065 ? ArcforgeGui.HEAT
                : rho > 0 ? ArcforgeGui.WARNING : ArcforgeGui.TEXT);
        double period = menu.period();
        reading(graphics, 3, "period", Double.isInfinite(period) ? Component.literal("∞") : number("%.0f s", period),
                period > 0 && period < 20 ? ArcforgeGui.HEAT : ArcforgeGui.TEXT);
        reading(graphics, 4, "fuel", number("%.0f°C", menu.fuelTemp()), menu.fuelTemp() > menu.tripValue(2) * 0.9 ? ArcforgeGui.HEAT : ArcforgeGui.TEXT);
        reading(graphics, 5, "cladding", number("%.0f°C", menu.cladTemp()), menu.cladTemp() > 1_000 ? ArcforgeGui.HEAT : ArcforgeGui.TEXT);
        reading(graphics, 6, "coolant", number("%.0f°C", menu.coolantTemp()), ArcforgeGui.TEXT);
        reading(graphics, 7, "pressure", number("%.1f bar", menu.pressure()), ArcforgeGui.TEXT);
        reading(graphics, 8, "xenon", number("%.0f%%", menu.xenon() * 100.0), ArcforgeGui.TEXT);
        reading(graphics, 9, "decay", Component.literal(ArcforgeGui.compact(menu.decay()) + " HU/t"), ArcforgeGui.TEXT);
        for (int bank = 0; bank < ReactorSim.BANKS; bank++) {
            int bx = BANK_X + bank * BANK_PITCH;
            textCentred(graphics, Component.literal(ControlRodChannelBlock.bankName(bank)), bx, BANK_W, BANK_LABEL_Y, ArcforgeGui.LABEL);
            String percent = Integer.toString((int) Math.round(menu.insertion(bank) * 100.0));
            graphics.pose().pushMatrix();
            graphics.pose().translate(bx + BANK_W / 2.0F, PERCENT_Y);
            graphics.pose().scale(0.75F, 0.75F);
            graphics.text(font, percent, -font.width(percent) / 2, 0, ArcforgeGui.TEXT, false);
            graphics.pose().popMatrix();
        }
        Component status = menu.scrammed() ? Component.translatable("gui.arcforge.fission_reactor.trip." + menu.trip().name().toLowerCase(Locale.ROOT))
                : menu.getStatus().getDescription();
        graphics.text(font, clipped(status, 64), LED_X + 8, STATUS_Y, menu.scrammed() ? ArcforgeGui.HEAT : ArcforgeGui.TEXT, false);
    }

    private void drawTrends(GuiGraphicsExtractor graphics) {
        String[] keys = { "trend_power", "trend_coolant", "trend_k" };
        for (int i = 0; i < 3; i++) {
            int top = GRAPH_Y + i * (GRAPH_H + GRAPH_GAP + 9);
            graphics.text(font, Component.translatable("gui.arcforge.fission_reactor." + keys[i]), GRAPH_X, top + GRAPH_H + 1, TRIP_COLORS[i], false);
        }
    }

    private static final String[] TRIPS = { "high_power", "short_period", "high_fuel_temperature", "high_pressure", "low_coolant_flow" };

    private void drawProtection(GuiGraphicsExtractor graphics) {
        String[] units = { "%d%%", "%d s", "%d°C", "%d bar", "%d%%" };
        for (int trip = 0; trip < 5; trip++) {
            int rowY = TRIP_Y + trip * LINE;
            graphics.text(font, clipped(Component.translatable("gui.arcforge.fission_reactor.trip." + TRIPS[trip]), 100), TOGGLE_X + 10, rowY,
                    menu.tripOn(trip) ? ArcforgeGui.TEXT : ArcforgeGui.LABEL, false);
            textRight(graphics, Component.literal(String.format(Locale.ROOT, units[trip], menu.tripValue(trip))), TRIP_MINUS - 2, rowY, ArcforgeGui.WHITE);
        }
        int pumpY = TRIP_Y + 5 * LINE + 4;
        graphics.text(font, Component.translatable("gui.arcforge.fission_reactor.pump"), TOGGLE_X + 10, pumpY, ArcforgeGui.TEXT, false);
        textRight(graphics, Component.literal(menu.pump() + "%"), TRIP_MINUS - 2, pumpY, ArcforgeGui.WHITE);
        graphics.text(font, Component.translatable("gui.arcforge.fission_reactor.redstone_rods"), TOGGLE_X + 10, pumpY + LINE, ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable("gui.arcforge.fission_reactor.auto_unload"), TOGGLE_X + 10, pumpY + 2 * LINE, ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable("gui.arcforge.fission_reactor.unload_all"), TOGGLE_X + 10, pumpY + 3 * LINE,
                menu.flag(FissionReactorMenu.FLAG_CAN_REFUEL) ? ArcforgeGui.TEXT : ArcforgeGui.LABEL, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(PAGE_X, CONTROL_Y, PAGE_W, CONTROL_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fission_reactor.page." + shown.name().toLowerCase(Locale.ROOT)));
            return;
        }
        if (isHovering(SCRAM_X, CONTROL_Y, WIDE, CONTROL_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fission_reactor.scram_hint"));
            return;
        }
        if (isHovering(RESET_X, CONTROL_Y, WIDE, CONTROL_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fission_reactor.reset_hint"));
            return;
        }
        if (isHovering(FissionReactorMenu.SLOT_X, FissionReactorMenu.INPUT_Y, 162, 16, mouseX, mouseY) && menu.getCarried().isEmpty()) {
            lines.add(Component.translatable("gui.arcforge.fission_reactor.input_slots").withStyle(ChatFormatting.GRAY));
            if (!menu.flag(FissionReactorMenu.FLAG_CAN_REFUEL)) {
                lines.add(Component.translatable("gui.arcforge.fission_reactor.refuel_shut_down").withStyle(ChatFormatting.GOLD));
            }
            return;
        }
        switch (shown) {
            case CORE -> coreTooltip(lines, mouseX, mouseY);
            case TRENDS -> {
                if (isHovering(GRAPH_X, GRAPH_Y, GRAPH_W, 3 * (GRAPH_H + GRAPH_GAP + 9), mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.trend_hint"));
                }
            }
            case PROTECTION -> {
                for (int trip = 0; trip < 5; trip++) {
                    if (isHovering(TOGGLE_X, TRIP_Y + trip * LINE - 1, TRIP_PLUS + BUTTON - TOGGLE_X, LINE, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.arcforge.fission_reactor.trip." + TRIPS[trip] + ".hint"));
                    }
                }
                int pumpY = TRIP_Y + 5 * LINE + 4;
                if (isHovering(TOGGLE_X, pumpY - 1, TRIP_PLUS + BUTTON - TOGGLE_X, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.pump_hint"));
                } else if (isHovering(TOGGLE_X, pumpY + LINE - 1, 150, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.redstone_rods_hint"));
                } else if (isHovering(TOGGLE_X, pumpY + 2 * LINE - 1, 150, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.auto_unload_hint"));
                } else if (isHovering(TOGGLE_X, pumpY + 3 * LINE - 1, 150, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.unload_all_hint"));
                }
            }
        }
    }

    private void coreTooltip(List<Component> lines, int mouseX, int mouseY) {
        int line = (mouseY - topPos - TOP + 1) / LINE;
        if (isHovering(LEFT, TOP - 1, VALUE_RIGHT - LEFT, 10 * LINE, mouseX, mouseY) && line >= 0 && line < 10) {
            switch (line) {
                case 0 -> {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.power_hint", ArcforgeGui.grouped(menu.thermal()),
                            ArcforgeGui.grouped(menu.rated())));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.rods_hint", menu.rods(), menu.channels(), menu.controlRods(),
                            String.format(Locale.ROOT, "%.1f", menu.burnup() * 100.0)).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.peaking_hint", String.format(Locale.ROOT, "%.2f", menu.peaking()))
                            .withStyle(ChatFormatting.GRAY));
                }
                case 1, 2 -> {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.k_hint"));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.doppler", String.format(Locale.ROOT, "%+.2f", menu.doppler()))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.void", String.format(Locale.ROOT, "%+.1f", menu.voidCoefficient()))
                            .withStyle(menu.voidCoefficient() > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
                }
                case 3 -> lines.add(Component.translatable("gui.arcforge.fission_reactor.period_hint"));
                case 4, 5 -> lines.add(Component.translatable("gui.arcforge.fission_reactor.temperature_hint"));
                case 6, 7 -> {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.mode." + menu.mode().name().toLowerCase(Locale.ROOT)));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.coolant_hint",
                            Component.translatable(menu.heavyWater() ? "fluid_type.arcforge.heavy_water" : "block.minecraft.water"),
                            ArcforgeGui.grouped(menu.coolant()), ArcforgeGui.grouped(menu.coolantCapacity())).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.flow_hint", Math.round(menu.flow() * 100.0),
                            Math.round(menu.voidFraction() * 100.0)).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.tanks_hint", ArcforgeGui.grouped(menu.intake()),
                            ArcforgeGui.grouped(menu.hotLeg()), ArcforgeGui.grouped(menu.steam())).withStyle(ChatFormatting.DARK_GRAY));
                }
                case 8 -> lines.add(Component.translatable("gui.arcforge.fission_reactor.xenon_hint"));
                default -> lines.add(Component.translatable("gui.arcforge.fission_reactor.decay_hint"));
            }
            return;
        }
        for (int bank = 0; bank < ReactorSim.BANKS; bank++) {
            int bx = BANK_X + bank * BANK_PITCH;
            if (isHovering(bx - 1, BANK_LABEL_Y, BANK_W + 2, PERCENT_Y + 8 - BANK_LABEL_Y, mouseX, mouseY)) {
                lines.add(Component.translatable("gui.arcforge.fission_reactor.bank", ControlRodChannelBlock.bankName(bank),
                        Math.round(menu.insertion(bank) * 100.0), Math.round(menu.target(bank) * 100.0)));
                lines.add(Component.translatable("gui.arcforge.fission_reactor.bank_hint").withStyle(ChatFormatting.GRAY));
                return;
            }
        }
        if (isHovering(LED_X, STATUS_Y - 1, 72, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && minecraft.gameMode != null) {
            boolean big = Minecraft.getInstance().hasShiftDown();
            double mx = event.x(), my = event.y();
            if (isHovering(PAGE_X, CONTROL_Y, PAGE_W, CONTROL_H, mx, my)) {
                shown = Page.values()[(shown.ordinal() + 1) % Page.values().length];
                ArcforgeGui.playClickSound();
                return true;
            }
            int id = -1;
            if (isHovering(SCRAM_X, CONTROL_Y, WIDE, CONTROL_H, mx, my)) {
                id = FissionReactorMenu.SCRAM;
            } else if (isHovering(RESET_X, CONTROL_Y, WIDE, CONTROL_H, mx, my)) {
                id = FissionReactorMenu.RESET;
            } else if (shown == Page.CORE) {
                for (int bank = 0; bank < ReactorSim.BANKS && id < 0; bank++) {
                    int bx = BANK_X + bank * BANK_PITCH;
                    if (isHovering(bx - 1, BUTTON_Y, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.bankButton(bank, false, big);
                    } else if (isHovering(bx + 6, BUTTON_Y, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.bankButton(bank, true, big);
                    }
                }
            } else if (shown == Page.PROTECTION) {
                for (int trip = 0; trip < 5 && id < 0; trip++) {
                    int rowY = TRIP_Y + trip * LINE;
                    if (isHovering(TOGGLE_X, rowY, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.TRIP_TOGGLE + trip;
                    } else if (isHovering(TRIP_MINUS, rowY, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.TRIP_STEP + trip * 2;
                    } else if (isHovering(TRIP_PLUS, rowY, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.TRIP_STEP + trip * 2 + 1;
                    }
                }
                int pumpY = TRIP_Y + 5 * LINE + 4;
                if (id < 0) {
                    if (isHovering(TRIP_MINUS, pumpY, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.PUMP_DOWN;
                    } else if (isHovering(TRIP_PLUS, pumpY, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.PUMP_UP;
                    } else if (isHovering(TOGGLE_X, pumpY + LINE, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.REDSTONE_RODS;
                    } else if (isHovering(TOGGLE_X, pumpY + 2 * LINE, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.AUTO_UNLOAD;
                    } else if (isHovering(TOGGLE_X, pumpY + 3 * LINE, BUTTON, BUTTON, mx, my)) {
                        id = FissionReactorMenu.UNLOAD_ALL;
                    }
                }
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
