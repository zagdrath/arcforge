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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.block.nuclear.ControlRodChannelBlock;
import net.zagdrath.arcforge.blockentity.multiblock.FissionReactorBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.FissionReactorMenu;
import net.zagdrath.arcforge.nuclear.reactor.ReactorParams;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.nuclear.reactor.ReactorSim;

// The Fission Reactor's control desk (300×288: the desk across the top, under it the nine rod input slots and the nine
// output slots in 3×3 grids over the ends of the player inventory, which is at the bottom), three pages on its screen:
//  - Core: the readings down the left (thermal power, k-effective, reactivity, period, the hottest fuel and cladding,
//    the coolant, pressure, xenon, decay heat), the four rod banks and All on the right (each a bar of how far in it
//    is with a marker at its target, - out / + in buttons, shift for 10%, and how far in, with the target while it
//    moves);
//  - Trends: the last minute of power (log scale), coolant temperature and k-effective;
//  - Protection: each trip point on or off with - and +, the coolant pump, Redstone Rods, auto-unload and Unload All.
// The status, Page, Reset and SCRAM stay in the desk's bottom right on every page. All positions are relative to
// leftPos/topPos.
public class FissionReactorScreen extends MachineScreen<FissionReactorMenu> {
    private enum Page { CORE, TRENDS, PROTECTION }

    private static final int LEFT = 10, VALUE_RIGHT = 128, LINE = 10, TOP = 19;
    // The Core page's readings, 9 apart to fit eleven on the screen.
    private static final int READINGS = 11, READING_LINE = 9;
    // Five columns: banks A to D, then All.
    private static final int COLUMNS = ReactorSim.BANKS + 1;
    private static final int BANK_X = 142, BANK_PITCH = 30, BANK_W = 12, BANK_LABEL_Y = 18, BAR_Y = 27, BAR_H = 44, BUTTON_Y = 74, PERCENT_Y = 83;
    private static final int STATUS_Y = 94, LED_X = 140, STATUS_X = 148, STATUS_W = 142;
    private static final int PAGE_X = 140, RESET_X = 156, SCRAM_X = 196, CONTROL_Y = 104, CONTROL_H = 12, PAGE_W = 12, WIDE = 36;
    private static final int SHIFT_X = 236, SHIFT_W = 54;
    private static final int BUTTON = 7;
    private static final int GRAPH_X = 10, GRAPH_W = 280, GRAPH_Y = 19, GRAPH_H = 22, GRAPH_STEP = 25;
    private static final int TRIP_Y = 19, TRIP_MINUS = 270, TRIP_PLUS = 280, TOGGLE_X = 10, ROW_W = 120;
    private static final int SLOTS_W = 54;
    // The tanks either side of the rod grids (12x52, level with them): intake and vessel, then hot leg and steam.
    private static final int[] TANK_X = { 20, 40, 248, 268 };
    private static final int TANK_Y = FissionReactorMenu.SLOTS_Y, TANK_W = 12, TANK_H = 52;
    private static final String[] TANKS = { "intake", "vessel", "hot_leg", "steam" };
    // The rod counts, in the gap between the input and output grids.
    private static final int COUNTS_X = FissionReactorMenu.INPUT_X + SLOTS_W, COUNTS_W = FissionReactorMenu.OUTPUT_X - FissionReactorMenu.INPUT_X - SLOTS_W;
    // Prompt critical: past the delayed neutrons' share (ReactorParams.beta), k-effective 1.01.
    private static final double PROMPT = 0.010;
    private static final int XENON_SAMPLE = 200;
    private static final int TRACK = 0xFF161616, TRACK_DARK = 0xFF0E0E0E, TRACK_LIGHT = 0xFF3A3A3A;
    private static final int[] TRIP_COLORS ={ 0xFF5FD4C4, 0xFFFF9A3C, 0xFFE0E0E0 };

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
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier liquidFill = sprite("liquid_fill");
    private final Identifier gasFill = sprite("gas_fill");
    private Page shown = Page.CORE;
    // Xenon a while ago and which way it has gone since (+1 building, -1 burning away, 0 steady), sampled every
    // XENON_SAMPLE ticks.
    private double xenonThen = Double.NaN;
    private int xenonTrend, xenonTicks;
    private int mouseX, mouseY;

    public FissionReactorScreen(FissionReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "fission_reactor", List.of(), false, FissionReactorMenu.WIDTH, FissionReactorMenu.HEIGHT, true);
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
    protected int textureWidth() {
        return 512;
    }

    @Override
    protected int textureHeight() {
        return 512;
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

    private static int columnX(int column) {
        return BANK_X + column * BANK_PITCH;
    }

    // A column's insertion and target: a bank's own, or for All the banks' mean.
    private double insertion(int column) {
        if (column < ReactorSim.BANKS) {
            return menu.insertion(column);
        }
        double sum = 0;
        for (int bank = 0; bank < ReactorSim.BANKS; bank++) {
            sum += menu.insertion(bank);
        }
        return sum / ReactorSim.BANKS;
    }

    private double target(int column) {
        if (column < ReactorSim.BANKS) {
            return menu.target(column);
        }
        double sum = 0;
        for (int bank = 0; bank < ReactorSim.BANKS; bank++) {
            sum += menu.target(bank);
        }
        return sum / ReactorSim.BANKS;
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        switch (shown) {
            case CORE -> {
                for (int column = 0; column < COLUMNS; column++) {
                    int bx = columnX(column);
                    // The empty track (a 1 px bevel, dark top-left, light bottom-right), so a bank all the way out
                    // still shows its bar.
                    graphics.fill(x + bx - 1, y + BAR_Y - 1, x + bx + BANK_W + 1, y + BAR_Y + BAR_H + 1, TRACK_LIGHT);
                    graphics.fill(x + bx - 1, y + BAR_Y - 1, x + bx + BANK_W, y + BAR_Y + BAR_H, TRACK_DARK);
                    graphics.fill(x + bx, y + BAR_Y, x + bx + BANK_W, y + BAR_Y + BAR_H, TRACK);
                    int filled = (int) Math.round(insertion(column) * BAR_H);
                    if (filled > 0) {
                        // Rods go in from the top: the bar fills downwards.
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bankFill, BANK_W, BAR_H, 0, 0, x + bx, y + BAR_Y, BANK_W, filled);
                    }
                    int marker = BAR_Y + (int) Math.round(target(column) * BAR_H);
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bankMarker, x + bx - 2, y + marker - 1, BANK_W + 4, 3);
                    blit(graphics, minus, minusHover, x, y, bx - 2, BUTTON_Y, BUTTON, BUTTON);
                    blit(graphics, plus, plusHover, x, y, bx + 7, BUTTON_Y, BUTTON, BUTTON);
                }
            }
            case TRENDS -> {
                graph(graphics, x, y, 0, GRAPH_Y, -9000, 500, TRIP_COLORS[0]);
                graph(graphics, x, y, 1, GRAPH_Y + GRAPH_STEP, 200, 4_000, TRIP_COLORS[1]);
                graph(graphics, x, y, 2, GRAPH_Y + 2 * GRAPH_STEP, 90_000, 110_000, TRIP_COLORS[2]);
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
        for (int tank = 0; tank < TANKS.length; tank++) {
            drawFluidTank(graphics, tankFluid(tank), tankAmount(tank), tankCapacity(tank), tank == 3 ? gasFill : liquidFill, tankGauge, x, y,
                    TANK_X[tank], TANK_Y, TANK_W, TANK_H);
        }
        drawLed(graphics, x, y, LED_X, STATUS_Y);
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

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++xenonTicks >= XENON_SAMPLE || Double.isNaN(xenonThen)) {
            double now = menu.xenon();
            if (!Double.isNaN(xenonThen)) {
                xenonTrend = now > xenonThen + 0.0005 ? 1 : now < xenonThen - 0.0005 ? -1 : 0;
            }
            xenonThen = now;
            xenonTicks = 0;
        }
    }

    // Pressurized and below the Hot Leg's flow temperature: the core's heat stays in the vessel.
    private boolean warmingUp() {
        return menu.mode() == ReactorSim.Mode.PRESSURIZED && menu.coolantTemp() < ReactorParams.defaults().pwrInletCelsius;
    }

    private Component number(String format, double value) {
        return Component.literal(String.format(Locale.ROOT, format, value));
    }

    // A label on the left and its value right-aligned; the label gives way (clipped) to a value too wide for both.
    private void reading(GuiGraphicsExtractor graphics, int line, String key, Component value, int color) {
        int y = TOP + line * READING_LINE;
        int room = VALUE_RIGHT - LEFT - font.width(value) - 4;
        graphics.text(font, clipped(Component.translatable("gui.arcforge.fission_reactor." + key), room), LEFT, y, ArcforgeGui.LABEL, false);
        textRight(graphics, value, VALUE_RIGHT, y, color);
    }

    // Reactivity in pcm, in thousands from ±10000 (-14664 pcm reads -15k pcm); the tooltip has it exactly.
    static String pcm(double rho) {
        double pcm = rho * 1.0E5;
        return Math.abs(pcm) < 10_000 ? String.format(Locale.ROOT, "%+.0f pcm", pcm) : String.format(Locale.ROOT, "%+.0fk pcm", pcm / 1000.0);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        switch (shown) {
            case CORE -> drawCore(graphics);
            case TRENDS -> drawTrends(graphics);
            case PROTECTION -> drawProtection(graphics);
        }
        // Rods waiting that can't load yet say why, over the status they'd otherwise hide behind ("No fuel").
        boolean waiting = rodsWaiting() && !menu.flag(FissionReactorMenu.FLAG_CAN_REFUEL);
        Component status = menu.scrammed() ? Component.translatable("gui.arcforge.fission_reactor.trip." + menu.trip().name().toLowerCase(Locale.ROOT))
                : waiting ? Component.translatable("gui.arcforge.fission_reactor.load_needs_shutdown") : menu.getStatus().getDescription();
        graphics.text(font, clipped(status, STATUS_W), STATUS_X, STATUS_Y,
                menu.scrammed() ? ArcforgeGui.HEAT : waiting ? ArcforgeGui.WARNING : ArcforgeGui.TEXT, false);
        textCentred(graphics, Component.translatable("gui.arcforge.fission_reactor.reset"), RESET_X, WIDE, CONTROL_Y + 2, ArcforgeGui.TEXT);
        textCentred(graphics, Component.translatable("gui.arcforge.fission_reactor.scram"), SCRAM_X, WIDE, CONTROL_Y + 2, ArcforgeGui.WHITE);
        if (shown == Page.CORE) {
            graphics.text(font, clipped(Component.translatable("gui.arcforge.fission_reactor.shift_hint"), SHIFT_W), SHIFT_X, CONTROL_Y + 2,
                    ArcforgeGui.LABEL, false);
        }
        drawRodCounts(graphics);
        textCentred(graphics, Component.translatable("gui.arcforge.fission_reactor.tanks_coolant"), TANK_X[0], TANK_X[1] + TANK_W - TANK_X[0],
                FissionReactorMenu.SLOTS_Y - 11, ArcforgeGui.LABEL);
        textCentred(graphics, Component.translatable("gui.arcforge.fission_reactor.tanks_out"), TANK_X[2], TANK_X[3] + TANK_W - TANK_X[2],
                FissionReactorMenu.SLOTS_Y - 11, ArcforgeGui.LABEL);
        int labelY = FissionReactorMenu.SLOTS_Y - 11;
        graphics.text(font, Component.translatable("gui.arcforge.fission_reactor.rods_in"), FissionReactorMenu.INPUT_X, labelY, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.fission_reactor.rods_out"), FissionReactorMenu.OUTPUT_X + SLOTS_W - 2, labelY,
                ArcforgeGui.LABEL);
    }

    // Between the rod grids: fuel rods loaded of the Fuel Channels, control rods of the Control Rod Channels (yellow while
    // any channel is empty).
    private void drawRodCounts(GuiGraphicsExtractor graphics) {
        int centre = COUNTS_X + COUNTS_W / 2;
        int[][] counts = { { menu.rods(), menu.channels() }, { menu.controlRods(), menu.controlChannels() } };
        String[] keys = { "fuel_rods", "control_rods" };
        for (int i = 0; i < 2; i++) {
            int y = FissionReactorMenu.SLOTS_Y + 2 + i * 26;
            Component label = clipped(Component.translatable("gui.arcforge.fission_reactor." + keys[i]), COUNTS_W);
            graphics.text(font, label, centre - font.width(label) / 2, y, ArcforgeGui.LABEL, false);
            String value = counts[i][0] + " / " + counts[i][1];
            graphics.text(font, value, centre - font.width(value) / 2, y + 10, counts[i][0] < counts[i][1] ? ArcforgeGui.WARNING : ArcforgeGui.TEXT,
                    false);
        }
    }

    // A tank's fluid, amount and size: the coolant coming in, the coolant in the vessel, the Hot Coolant going out, the
    // steam a boiling core makes.
    private Fluid tankFluid(int tank) {
        boolean heavy = menu.heavyWater();
        return switch (tank) {
            case 0, 1 -> heavy ? ModFluids.HEAVY_WATER.get() : Fluids.WATER;
            case 2 -> heavy ? ModFluids.HOT_HEAVY_WATER.get() : ModFluids.HOT_COOLANT.get();
            default -> ModFluids.CONTAMINATED_STEAM.get();
        };
    }

    private int tankAmount(int tank) {
        return switch (tank) {
            case 0 -> menu.intake();
            case 1 -> menu.coolant();
            case 2 -> menu.hotLeg();
            default -> menu.steam();
        };
    }

    private int tankCapacity(int tank) {
        return switch (tank) {
            case 1 -> menu.coolantCapacity();
            case 3 -> FissionReactorBlockEntity.STEAM_TANK;
            default -> FissionReactorBlockEntity.TANK;
        };
    }

    // Whether a rod sits in the input slots.
    private boolean rodsWaiting() {
        for (int slot = 0; slot < FissionReactorBlockEntity.INPUT_SLOTS; slot++) {
            if (!menu.getSlot(slot).getItem().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void textCentred(GuiGraphicsExtractor graphics, Component text, int left, int width, int y, int color) {
        graphics.text(font, text, left + (width - font.width(text)) / 2, y, color, false);
    }

    private void drawCore(GuiGraphicsExtractor graphics) {
        double power = menu.power();
        boolean critical = power > 0.01;
        reading(graphics, 0, "power", Component.literal(ReactorSim.percent(power) + "%"), critical ? ArcforgeGui.ACCENT : ArcforgeGui.TEXT);
        double k = menu.k();
        reading(graphics, 1, "thermal", Component.literal(ArcforgeGui.compact(menu.thermal()) + " HU/t"), critical ? ArcforgeGui.ACCENT : ArcforgeGui.TEXT);
        reading(graphics, 2, "k", number("%.4f", k), k > 1.0 + PROMPT ? ArcforgeGui.HEAT : ArcforgeGui.TEXT);
        double rho = menu.rho();
        reading(graphics, 3, "rho", Component.literal(pcm(rho)), rho > PROMPT ? ArcforgeGui.HEAT : rho > 0 ? ArcforgeGui.WARNING : ArcforgeGui.TEXT);
        double period = menu.period();
        reading(graphics, 4, "period", Double.isInfinite(period) ? Component.literal("∞") : number("%.0f s", period),
                // Red under 10 s (the default trip), yellow under 30 s: slow down.
                period > 0 && period < 10 ? ArcforgeGui.HEAT : period > 0 && period < 30 ? ArcforgeGui.WARNING : ArcforgeGui.TEXT);
        reading(graphics, 5, "fuel", number("%.0f°C", menu.fuelTemp()), menu.fuelTemp() > menu.tripValue(2) * 0.9 ? ArcforgeGui.HEAT : ArcforgeGui.TEXT);
        reading(graphics, 6, "cladding", number("%.0f°C", menu.cladTemp()), menu.cladTemp() > 1_000 ? ArcforgeGui.HEAT : ArcforgeGui.TEXT);
        // Yellow while warming up: below the Hot Leg's flow temperature no heat leaves yet.
        reading(graphics, 7, "coolant", number("%.0f°C", menu.coolantTemp()), warmingUp() ? ArcforgeGui.WARNING : ArcforgeGui.TEXT);
        reading(graphics, 8, "pressure", number("%.1f bar", menu.pressure()), ArcforgeGui.TEXT);
        // With an arrow while it builds up or burns away (over the last ten seconds).
        reading(graphics, 9, "xenon", Component.literal(String.format(Locale.ROOT, "%.0f%%", menu.xenon() * 100.0) + (xenonTrend > 0 ? "↑" : xenonTrend < 0 ? "↓" : "")),
                xenonTrend > 0 ? ArcforgeGui.WARNING : ArcforgeGui.TEXT);
        reading(graphics, 10, "decay", Component.literal(ArcforgeGui.compact(menu.decay()) + " HU/t"), ArcforgeGui.TEXT);
        for (int column = 0; column < COLUMNS; column++) {
            int bx = columnX(column);
            Component name = column < ReactorSim.BANKS ? Component.literal(ControlRodChannelBlock.bankName(column))
                    : Component.translatable("gui.arcforge.fission_reactor.all_banks");
            textCentred(graphics, name, bx, BANK_W, BANK_LABEL_Y, ArcforgeGui.LABEL);
            // How far in, to 0.1%; while the rods travel, where they are going instead (the bar shows where they are),
            // so a step shows at once.
            long in = Math.round(insertion(column) * 1000.0), to = Math.round(target(column) * 1000.0);
            String percent = in == to ? tenths(in) : "→" + tenths(to);
            graphics.pose().pushMatrix();
            graphics.pose().translate(bx + BANK_W / 2.0F, PERCENT_Y);
            graphics.pose().scale(0.75F, 0.75F);
            graphics.text(font, percent, -font.width(percent) / 2, 0, in == to ? ArcforgeGui.TEXT : ArcforgeGui.ACCENT, false);
            graphics.pose().popMatrix();
        }
    }

    private void drawTrends(GuiGraphicsExtractor graphics) {
        String[] keys = { "trend_power", "trend_coolant", "trend_k" };
        for (int i = 0; i < 3; i++) {
            // In the graph's top-left corner.
            graphics.text(font, Component.translatable("gui.arcforge.fission_reactor." + keys[i]), GRAPH_X + 1, GRAPH_Y + i * GRAPH_STEP + 1,
                    TRIP_COLORS[i], false);
        }
    }

    private static final String[] TRIPS = { "high_power", "short_period", "high_fuel_temperature", "high_pressure", "low_coolant_flow" };

    private void drawProtection(GuiGraphicsExtractor graphics) {
        String[] units = { "%d%%", "%d s", "%d°C", "%d bar", "%d%%" };
        for (int trip = 0; trip < 5; trip++) {
            int rowY = TRIP_Y + trip * LINE;
            graphics.text(font, clipped(Component.translatable("gui.arcforge.fission_reactor.trip." + TRIPS[trip]), 180), TOGGLE_X + 10, rowY,
                    menu.tripOn(trip) ? ArcforgeGui.TEXT : ArcforgeGui.LABEL, false);
            textRight(graphics, Component.literal(String.format(Locale.ROOT, units[trip], menu.tripValue(trip))), TRIP_MINUS - 3, rowY, ArcforgeGui.WHITE);
        }
        int pumpY = TRIP_Y + 5 * LINE + 4;
        graphics.text(font, Component.translatable("gui.arcforge.fission_reactor.pump"), TOGGLE_X + 10, pumpY, ArcforgeGui.TEXT, false);
        textRight(graphics, Component.literal(menu.pump() + "%"), TRIP_MINUS - 3, pumpY, ArcforgeGui.WHITE);
        graphics.text(font, clipped(Component.translatable("gui.arcforge.fission_reactor.redstone_rods"), ROW_W - 10), TOGGLE_X + 10, pumpY + LINE,
                ArcforgeGui.TEXT, false);
        graphics.text(font, clipped(Component.translatable("gui.arcforge.fission_reactor.auto_unload"), ROW_W - 10), TOGGLE_X + 10, pumpY + 2 * LINE,
                ArcforgeGui.TEXT, false);
        graphics.text(font, clipped(Component.translatable("gui.arcforge.fission_reactor.unload_all"), ROW_W - 10), TOGGLE_X + 10, pumpY + 3 * LINE,
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
        if (isHovering(LED_X, STATUS_Y - 1, STATUS_X + STATUS_W - LED_X, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
            return;
        }
        for (int tank = 0; tank < TANKS.length; tank++) {
            if (isHovering(TANK_X[tank], TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
                lines.add(Component.translatable("gui.arcforge.fission_reactor.tank." + TANKS[tank]));
                int amount = tankAmount(tank);
                addFluidTooltip(lines, amount > 0 ? tankFluid(tank) : Fluids.EMPTY, Component.translatable("gui.arcforge.empty"), amount, tankCapacity(tank));
                lines.add(Component.translatable("gui.arcforge.fission_reactor.tank." + TANKS[tank] + ".hint").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
        }
        if (isHovering(COUNTS_X, FissionReactorMenu.SLOTS_Y, COUNTS_W, SLOTS_W - 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fission_reactor.rods_hint", menu.rods(), menu.channels(), menu.controlRods(),
                    String.format(Locale.ROOT, "%.1f", menu.burnup() * 100.0)));
            lines.add(Component.translatable("gui.arcforge.fission_reactor.rod_counts_hint").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (isHovering(FissionReactorMenu.INPUT_X, FissionReactorMenu.SLOTS_Y, SLOTS_W - 2, SLOTS_W - 2, mouseX, mouseY) && menu.getCarried().isEmpty()) {
            lines.add(Component.translatable("gui.arcforge.fission_reactor.input_slots").withStyle(ChatFormatting.GRAY));
            if (!menu.flag(FissionReactorMenu.FLAG_CAN_REFUEL)) {
                lines.add(Component.translatable("gui.arcforge.fission_reactor.refuel_shut_down").withStyle(ChatFormatting.GOLD));
            }
            return;
        }
        switch (shown) {
            case CORE -> coreTooltip(lines, mouseX, mouseY);
            case TRENDS -> {
                if (isHovering(GRAPH_X, GRAPH_Y, GRAPH_W, 2 * GRAPH_STEP + GRAPH_H, mouseX, mouseY)) {
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
                } else if (isHovering(TOGGLE_X, pumpY + LINE - 1, ROW_W, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.redstone_rods_hint"));
                } else if (isHovering(TOGGLE_X, pumpY + 2 * LINE - 1, ROW_W, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.auto_unload_hint"));
                } else if (isHovering(TOGGLE_X, pumpY + 3 * LINE - 1, ROW_W, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.unload_all_hint"));
                }
            }
        }
    }

    private void coreTooltip(List<Component> lines, int mouseX, int mouseY) {
        int line = (mouseY - topPos - TOP + 1) / READING_LINE;
        if (isHovering(LEFT, TOP - 1, VALUE_RIGHT - LEFT, READINGS * READING_LINE, mouseX, mouseY) && line >= 0 && line < READINGS) {
            switch (line) {
                case 0, 1 -> {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.power_hint", ArcforgeGui.grouped(menu.thermal()),
                            ArcforgeGui.grouped(menu.rated())));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.rods_hint", menu.rods(), menu.channels(), menu.controlRods(),
                            String.format(Locale.ROOT, "%.1f", menu.burnup() * 100.0)).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.peaking_hint", String.format(Locale.ROOT, "%.2f", menu.peaking()))
                            .withStyle(ChatFormatting.GRAY));
                }
                case 2, 3 -> {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.k_hint"));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.rho_exact", String.format(Locale.ROOT, "%+.0f", menu.rho() * 1.0E5))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.doppler", String.format(Locale.ROOT, "%+.2f", menu.doppler()))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.void", String.format(Locale.ROOT, "%+.1f", menu.voidCoefficient()))
                            .withStyle(menu.voidCoefficient() > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
                }
                case 4 -> lines.add(Component.translatable("gui.arcforge.fission_reactor.period_hint"));
                case 5, 6 -> lines.add(Component.translatable("gui.arcforge.fission_reactor.temperature_hint"));
                case 7, 8 -> {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.mode." + menu.mode().name().toLowerCase(Locale.ROOT)));
                    if (warmingUp()) {
                        lines.add(Component.translatable("gui.arcforge.fission_reactor.warming_up", Math.round(ReactorParams.defaults().pwrInletCelsius))
                                .withStyle(ChatFormatting.YELLOW));
                    }
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.coolant_hint",
                            Component.translatable(menu.heavyWater() ? "fluid_type.arcforge.heavy_water" : "block.minecraft.water"),
                            ArcforgeGui.grouped(menu.coolant()), ArcforgeGui.grouped(menu.coolantCapacity())).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.flow_hint", Math.round(menu.flow() * 100.0),
                            Math.round(menu.voidFraction() * 100.0)).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.tanks_hint", ArcforgeGui.grouped(menu.intake()),
                            ArcforgeGui.grouped(menu.hotLeg()), ArcforgeGui.grouped(menu.steam())).withStyle(ChatFormatting.DARK_GRAY));
                }
                case 9 -> lines.add(Component.translatable("gui.arcforge.fission_reactor.xenon_hint"));
                default -> lines.add(Component.translatable("gui.arcforge.fission_reactor.decay_hint"));
            }
            return;
        }
        for (int column = 0; column < COLUMNS; column++) {
            int bx = columnX(column);
            if (isHovering(bx - 2, BANK_LABEL_Y, BANK_W + 4, PERCENT_Y + 8 - BANK_LABEL_Y, mouseX, mouseY)) {
                if (column < ReactorSim.BANKS) {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.bank", ControlRodChannelBlock.bankName(column),
                            tenths(Math.round(menu.insertion(column) * 1000.0)), tenths(Math.round(menu.target(column) * 1000.0))));
                } else {
                    lines.add(Component.translatable("gui.arcforge.fission_reactor.all_banks_hint"));
                }
                lines.add(Component.translatable("gui.arcforge.fission_reactor.bank_hint").withStyle(ChatFormatting.GRAY));
                return;
            }
        }
    }

    // A percentage in tenths: whole when it is, else to one decimal (415 reads 41.5).
    private static String tenths(long tenths) {
        return tenths % 10 == 0 ? Long.toString(tenths / 10) : String.format(Locale.ROOT, "%.1f", tenths / 10.0);
    }

    // The button under the mouse on the page shown, or -1; a bank button steps 1%, 10% with shift (big) or 0.1% with
    // control (fine).
    private int buttonAt(double mx, double my, boolean big, boolean fine) {
        if (isHovering(SCRAM_X, CONTROL_Y, WIDE, CONTROL_H, mx, my)) {
            return FissionReactorMenu.SCRAM;
        }
        if (isHovering(RESET_X, CONTROL_Y, WIDE, CONTROL_H, mx, my)) {
            return FissionReactorMenu.RESET;
        }
        if (shown == Page.CORE) {
            for (int column = 0; column < COLUMNS; column++) {
                int bx = columnX(column);
                for (boolean in : new boolean[] { false, true }) {
                    if (isHovering(in ? bx + 7 : bx - 2, BUTTON_Y, BUTTON, BUTTON, mx, my)) {
                        if (fine) {
                            return FissionReactorMenu.fineButton(column, in);
                        }
                        return column < ReactorSim.BANKS ? FissionReactorMenu.bankButton(column, in, big) : FissionReactorMenu.allBanksButton(in, big);
                    }
                }
            }
        } else if (shown == Page.PROTECTION) {
            for (int trip = 0; trip < 5; trip++) {
                int rowY = TRIP_Y + trip * LINE;
                if (isHovering(TOGGLE_X, rowY, BUTTON, BUTTON, mx, my)) {
                    return FissionReactorMenu.TRIP_TOGGLE + trip;
                } else if (isHovering(TRIP_MINUS, rowY, BUTTON, BUTTON, mx, my)) {
                    return FissionReactorMenu.TRIP_STEP + trip * 2;
                } else if (isHovering(TRIP_PLUS, rowY, BUTTON, BUTTON, mx, my)) {
                    return FissionReactorMenu.TRIP_STEP + trip * 2 + 1;
                }
            }
            int pumpY = TRIP_Y + 5 * LINE + 4;
            if (isHovering(TRIP_MINUS, pumpY, BUTTON, BUTTON, mx, my)) {
                return FissionReactorMenu.PUMP_DOWN;
            } else if (isHovering(TRIP_PLUS, pumpY, BUTTON, BUTTON, mx, my)) {
                return FissionReactorMenu.PUMP_UP;
            } else if (isHovering(TOGGLE_X, pumpY + LINE, BUTTON, BUTTON, mx, my)) {
                return FissionReactorMenu.REDSTONE_RODS;
            } else if (isHovering(TOGGLE_X, pumpY + 2 * LINE, BUTTON, BUTTON, mx, my)) {
                return FissionReactorMenu.AUTO_UNLOAD;
            } else if (isHovering(TOGGLE_X, pumpY + 3 * LINE, BUTTON, BUTTON, mx, my)) {
                return FissionReactorMenu.UNLOAD_ALL;
            }
        }
        return -1;
    }

    // A left or right click on the desk's controls (both do the same, as on a Sides tab): pages through the screen or
    // sends the button to the server. Run from a
    // highest-priority ScreenEvent.MouseButtonPressed.Pre listener (see ArcforgeClient) as well as mouseClicked, so a
    // click on the desk reaches it before any other mod's screen listener can take it.
    public static void deskClick(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof FissionReactorScreen screen && isPress(event.getButton()) && screen.deskClick(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    private static boolean isPress(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    private boolean deskClick(double mx, double my) {
        if (minecraft.gameMode == null || !menu.getCarried().isEmpty()) {
            return false;
        }
        if (isHovering(PAGE_X, CONTROL_Y, PAGE_W, CONTROL_H, mx, my)) {
            shown = Page.values()[(shown.ordinal() + 1) % Page.values().length];
            ArcforgeGui.playClickSound();
            return true;
        }
        int id = buttonAt(mx, my, Minecraft.getInstance().hasShiftDown(), Minecraft.getInstance().hasControlDown());
        if (id < 0) {
            return false;
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        ArcforgeGui.playClickSound();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (isPress(event.button()) && deskClick(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
