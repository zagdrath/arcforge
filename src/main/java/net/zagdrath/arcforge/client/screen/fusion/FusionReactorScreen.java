/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.fusion;

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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.block.fusion.FusionModuleBlock;
import net.zagdrath.arcforge.blockentity.fusion.FusionReactorBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.fusion.FusionAlarm;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.fusion.FusionReactorMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// The Fusion Reactor's control desk (300×270: the desk across the top, the plant's input tanks left of the player
// inventory and its outputs and FE right of it), three pages on its screen:
//  - Plasma: the plasma's readings down the left (temperature, density against its limit, current, field, confinement
//    time, normalised beta, q95, vertical position, impurity, pulse time left); the power balance on the right (fusion
//    power, heating, the gain Q, the neutrons, alphas and radiation, the heat out) and the last minute of fusion power.
//    Without a Diagnostics Port the temperature, density and position read only roughly.
//  - Plant: vacuum, the coils (temperature, field against its target, the Central Solenoid's charge), the wall's and
//    blanket's temperatures, the fuel cycle (TBR, burn and breeding), pumping; on the right, wear bars for the First Wall,
//    Divertor, blanket and coils, the modules fitted and the alarms up.
//  - Control: the start-up checklist on the left; on the right the setpoints (heating MW, density, current and field %)
//    with - and + (shift for a big step), the fuel (D-T or D-He3), Repeat, and Silence.
// Status, Page, Run (Stop while running) and Fast Shutdown stay along the desk's bottom on every page.
public class FusionReactorScreen extends MachineScreen<FusionReactorMenu> {
    private enum Page { PLASMA, PLANT, CONTROL }

    private static final int LEFT = 11, VALUE_RIGHT = 142, TOP = 19, LINE = 10;
    private static final int RIGHT = 152, RIGHT_END = 288;
    private static final int STATUS_Y = 137, LED_X = 11, STATUS_X = 19, STATUS_W = 127;
    private static final int PAGE_X = 150, RUN_X = 166, SHUTDOWN_X = 206, CONTROL_Y = 135, CONTROL_H = 12, PAGE_W = 12, WIDE = 36, SHUTDOWN_W = 82;
    private static final int BUTTON = 7;
    private static final int GRAPH_X = RIGHT, GRAPH_W = RIGHT_END - RIGHT, GRAPH_Y = 96, GRAPH_H = 32;
    // Tanks: the inputs (Water, Lead-Lithium, Liquid Helium, Liquid Nitrogen) left of the inventory, the outputs (Hot
    // Coolant, Hot Lead-Lithium) and the FE gauge right of it.
    private static final int[] TANK_X = { 8, 23, 38, 53, 238, 253 };
    private static final int TANK_Y = FusionReactorMenu.INVENTORY_Y, TANK_W = 12, TANK_H = 52;
    private static final String[] TANKS = { "water", "lead_lithium", "liquid_helium", "liquid_nitrogen", "hot_coolant", "hot_lead_lithium" };
    private static final int ENERGY_X = 272, ENERGY_Y = FusionReactorMenu.INVENTORY_Y, ENERGY_W = 18;
    // Wear bars on the Plant page.
    private static final int WEAR_X = RIGHT + 54, WEAR_W = RIGHT_END - RIGHT - 54, WEAR_H = 5;
    private static final String[] WEAR = { "first_wall", "divertor", "blanket", "coils" };
    // The setpoints on the Control page: label, then - value +, the value right-aligned just left of + with room for the
    // widest ("1000 MW", 39 px) before -.
    private static final int SET_PLUS = RIGHT_END - BUTTON, SET_VALUE_RIGHT = SET_PLUS - 3, SET_VALUE_W = 40, SET_MINUS = SET_VALUE_RIGHT - SET_VALUE_W - 3 - BUTTON;
    private static final String[] SETPOINTS = { "heating", "density", "current", "field" };
    private static final int ALARM_RED = 0xFFE8483A, ALARM_AMBER = 0xFFE0A020, GOOD = 0xFF6CC832, TRACK = 0xFF161616, PLASMA = 0xFFE07AD8;

    private final Identifier minus = sprite("button_minus");
    private final Identifier minusHover = sprite("button_minus_hover");
    private final Identifier plus = sprite("button_plus");
    private final Identifier plusHover = sprite("button_plus_hover");
    private final Identifier page = sprite("page");
    private final Identifier pageHover = sprite("page_hover");
    private final Identifier run = sprite("run");
    private final Identifier runHover = sprite("run_hover");
    private final Identifier shutdown = sprite("shutdown");
    private final Identifier shutdownHover = sprite("shutdown_hover");
    private final Identifier toggleOn = sprite("toggle_on");
    private final Identifier toggleOff = sprite("toggle_off");
    private final Identifier silence = sprite("silence");
    private final Identifier silenceHover = sprite("silence_hover");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier liquidFill = sprite("liquid_fill");
    private final Identifier energyBar = sprite("energy_bar");
    private Page shown = Page.PLASMA;
    private int mouseX, mouseY;

    public FusionReactorScreen(FusionReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "fusion_reactor", List.of(EnergyTab.usage(() -> menu.get(FusionReactorMenu.DATA_ENERGY), () -> menu.get(FusionReactorMenu.DATA_ENERGY_USE))), false, FusionReactorMenu.WIDTH, FusionReactorMenu.HEIGHT, true);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("fusion_input");
            case OUTPUT -> sideModeName("hot_coolant_output");
            case HOT_LEG -> sideModeName("hot_lead_lithium_output");
            case ENERGY -> sideModeName("energy_input");
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

    private static int lineY(int line) {
        return TOP + line * LINE;
    }

    // --- Drawing ---

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        switch (shown) {
            case PLASMA -> graph(graphics, x, y);
            case PLANT -> {
                double[] wear = { menu.scaled(FusionReactorMenu.DATA_FW_WEAR, 1000.0), menu.scaled(FusionReactorMenu.DATA_DIV_WEAR, 1000.0),
                        menu.scaled(FusionReactorMenu.DATA_BLANKET_WEAR, 1000.0), menu.scaled(FusionReactorMenu.DATA_COIL_DAMAGE, 1000.0) };
                for (int i = 0; i < wear.length; i++) {
                    int by = y + lineY(i) + 1;
                    graphics.fill(x + WEAR_X, by, x + WEAR_X + WEAR_W, by + WEAR_H, TRACK);
                    int width = (int) Math.round(Math.min(1.0, wear[i]) * WEAR_W);
                    int color = wear[i] >= 0.8 ? ALARM_RED : wear[i] >= 0.5 ? ALARM_AMBER : GOOD;
                    if (width > 0) {
                        graphics.fill(x + WEAR_X, by, x + WEAR_X + width, by + WEAR_H, color);
                    }
                }
                List<FusionAlarm> up = alarms();
                for (int i = 0; i < Math.min(5, up.size()); i++) {
                    FusionAlarm alarm = up.get(i);
                    int color = (FusionAlarm.klaxonMask() & alarm.bit) == 0 ? ArcforgeGui.ACCENT : (menu.silenced() & alarm.bit) != 0 ? ALARM_AMBER : ALARM_RED;
                    int ly = y + lineY(6 + i) + 1;
                    graphics.fill(x + RIGHT, ly, x + RIGHT + 5, ly + 5, color);
                }
            }
            case CONTROL -> {
                for (int i = 0; i < FusionReactorMenu.CHECKS; i++) {
                    int ly = y + lineY(i + 1) + 1;
                    graphics.fill(x + LEFT, ly, x + LEFT + 5, ly + 5, menu.check(1 << i) ? GOOD : ALARM_RED);
                }
                for (int i = 0; i < SETPOINTS.length; i++) {
                    int rowY = lineY(i);
                    blit(graphics, minus, minusHover, x, y, SET_MINUS, rowY, BUTTON, BUTTON);
                    blit(graphics, plus, plusHover, x, y, SET_PLUS, rowY, BUTTON, BUTTON);
                }
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.fuelMode() == FusionReactorBlockEntity.FuelMode.DHE3 ? toggleOn : toggleOff,
                        x + RIGHT, y + lineY(5), BUTTON, BUTTON);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.flag(FusionReactorMenu.FLAG_REPEAT) ? toggleOn : toggleOff, x + RIGHT, y + lineY(6),
                        BUTTON, BUTTON);
                blit(graphics, silence, silenceHover, x, y, RIGHT, lineY(8), WIDE + 18, CONTROL_H);
            }
        }
        for (int tank = 0; tank < TANKS.length; tank++) {
            drawFluidTank(graphics, tankFluid(tank), tankAmount(tank), menu.get(FusionReactorMenu.DATA_TANK) / (tank == 2 || tank == 3 ? 4 : 1),
                    liquidFill, tankGauge, x, y, TANK_X[tank], TANK_Y, TANK_W, TANK_H);
        }
        int energy = scaled(menu.get(FusionReactorMenu.DATA_ENERGY), Math.max(1, menu.get(FusionReactorMenu.DATA_ENERGY_CAPACITY)), TANK_H);
        graphics.fill(x + ENERGY_X, y + ENERGY_Y, x + ENERGY_X + ENERGY_W, y + ENERGY_Y + TANK_H, TRACK);
        if (energy > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, energyBar, ENERGY_W, TANK_H, 0, TANK_H - energy, x + ENERGY_X, y + ENERGY_Y + TANK_H - energy,
                    ENERGY_W, energy);
        }
        drawLed(graphics, x, y, LED_X, STATUS_Y);
        blit(graphics, page, pageHover, x, y, PAGE_X, CONTROL_Y, PAGE_W, CONTROL_H);
        blit(graphics, run, runHover, x, y, RUN_X, CONTROL_Y, WIDE, CONTROL_H);
        blit(graphics, shutdown, shutdownHover, x, y, SHUTDOWN_X, CONTROL_Y, SHUTDOWN_W, CONTROL_H);
    }

    // The last minute of fusion power, scaled to its own peak (at least 10 MW), as a line on the screen's floor.
    private void graph(GuiGraphicsExtractor graphics, int x, int y) {
        int count = FusionReactorBlockEntity.HISTORY;
        int peak = 10;
        for (int i = 0; i < count; i++) {
            peak = Math.max(peak, menu.history(0, i));
        }
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            int value = menu.history(0, count - 1 - i);
            double t = Math.max(0.0, Math.min(1.0, value / (double) peak));
            int px = x + GRAPH_X + (int) Math.round(i * (GRAPH_W - 1) / (double) (count - 1));
            int py = y + GRAPH_Y + GRAPH_H - 1 - (int) Math.round(t * (GRAPH_H - 1));
            if (lastY != Integer.MIN_VALUE) {
                graphics.fill(px - 2, Math.min(lastY, py), px + 1, Math.max(lastY, py) + 1, PLASMA);
            }
            lastY = py;
        }
    }

    private List<FusionAlarm> alarms() {
        java.util.ArrayList<FusionAlarm> up = new java.util.ArrayList<>();
        for (FusionAlarm alarm : FusionAlarm.values()) {
            if ((menu.alarms() & alarm.bit) != 0) {
                up.add(alarm);
            }
        }
        return up;
    }

    private boolean heliumLoop() {
        return menu.get(FusionReactorMenu.DATA_HELIUM_LOOP) != 0;
    }

    // The tank's name for its label (the coolant pair reads as Helium and Hot Helium on the gas loop).
    private String tankName(int tank) {
        return heliumLoop() && tank == 0 ? "helium" : heliumLoop() && tank == 4 ? "hot_helium" : TANKS[tank];
    }

    private Fluid tankFluid(int tank) {
        return switch (tank) {
            case 0 -> heliumLoop() ? ModFluids.HELIUM.get() : Fluids.WATER;
            case 1 -> ModFluids.LEAD_LITHIUM.get();
            case 2 -> ModFluids.LIQUID_HELIUM.get();
            case 3 -> ModFluids.LIQUID_NITROGEN.get();
            case 4 -> heliumLoop() ? ModFluids.HOT_HELIUM.get() : ModFluids.HOT_COOLANT.get();
            default -> ModFluids.HOT_LEAD_LITHIUM.get();
        };
    }

    private int tankAmount(int tank) {
        return menu.get(switch (tank) {
            case 0 -> FusionReactorMenu.DATA_WATER;
            case 1 -> FusionReactorMenu.DATA_LEAD_LITHIUM;
            case 2 -> FusionReactorMenu.DATA_LIQUID_HELIUM;
            case 3 -> FusionReactorMenu.DATA_LIQUID_NITROGEN;
            case 4 -> FusionReactorMenu.DATA_HOT_COOLANT;
            default -> FusionReactorMenu.DATA_HOT_LEAD_LITHIUM;
        });
    }

    // --- Text ---

    private static Component number(String format, double value) {
        return Component.literal(String.format(Locale.ROOT, format, value));
    }

    private void textCentred(GuiGraphicsExtractor graphics, Component text, int left, int width, int y, int color) {
        graphics.text(font, text, left + (width - font.width(text)) / 2, y, color, false);
    }

    // A label and its value right-aligned, on a column from left to right; the label gives way to a wide value.
    private void reading(GuiGraphicsExtractor graphics, int left, int right, int line, String key, Component value, int color) {
        int y = lineY(line);
        int room = right - left - font.width(value) - 4;
        graphics.text(font, clipped(Component.translatable("gui.arcforge.fusion_reactor." + key), room), left, y, ArcforgeGui.LABEL, false);
        textRight(graphics, value, right, y, color);
    }

    private boolean alive() {
        return menu.flag(FusionReactorMenu.FLAG_ALIVE);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        if (!menu.isFormed()) {
            graphics.text(font, Component.translatable("gui.arcforge.fusion_reactor.not_formed"), LEFT, TOP, ArcforgeGui.WARNING, false);
            graphics.text(font, clipped(Component.translatable("gui.arcforge.fusion_reactor.not_formed_hint"), RIGHT_END - LEFT), LEFT, TOP + LINE,
                    ArcforgeGui.LABEL, false);
        } else {
            switch (shown) {
                case PLASMA -> drawPlasma(graphics);
                case PLANT -> drawPlant(graphics);
                case CONTROL -> drawControl(graphics);
            }
        }
        Component status = menu.getStatus().getDescription();
        graphics.text(font, clipped(status, STATUS_W), STATUS_X, STATUS_Y, alive() ? ArcforgeGui.ACCENT : ArcforgeGui.TEXT, false);
        boolean running = menu.flag(FusionReactorMenu.FLAG_RUN);
        textCentred(graphics, Component.translatable(running ? "gui.arcforge.fusion_reactor.stop" : "gui.arcforge.fusion_reactor.run"), RUN_X, WIDE,
                CONTROL_Y + 2, ArcforgeGui.TEXT);
        textCentred(graphics, Component.translatable("gui.arcforge.fusion_reactor.fast_shutdown"), SHUTDOWN_X, SHUTDOWN_W, CONTROL_Y + 2, ArcforgeGui.WHITE);
        graphics.text(font, Component.translatable("gui.arcforge.fusion_reactor.tanks_in"), TANK_X[0], TANK_Y - 10, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.fusion_reactor.tanks_out"), TANK_X[4], TANK_Y - 10, ArcforgeGui.LABEL, false);
    }

    private void drawPlasma(GuiGraphicsExtractor graphics) {
        boolean live = alive();
        boolean exact = menu.flag(FusionReactorMenu.FLAG_DIAGNOSTICS);
        double t = menu.scaled(FusionReactorMenu.DATA_TEMPERATURE, 10.0);
        double ne = menu.scaled(FusionReactorMenu.DATA_DENSITY, 1000.0);
        double gw = menu.scaled(FusionReactorMenu.DATA_GREENWALD, 1000.0);
        double position = menu.scaled(FusionReactorMenu.DATA_POSITION, 1000.0);
        int dash = ArcforgeGui.LABEL;
        Component none = Component.literal("—");
        reading(graphics, LEFT, VALUE_RIGHT, 0, "temperature", !live ? none : exact ? number("%.1f keV", t) : number("~%.0f keV", t),
                live ? ArcforgeGui.ACCENT : dash);
        reading(graphics, LEFT, VALUE_RIGHT, 1, "density", !live ? none : exact ? number("%.2f×10²⁰", ne) : number("~%.1f×10²⁰", ne),
                live ? ArcforgeGui.TEXT : dash);
        reading(graphics, LEFT, VALUE_RIGHT, 2, "density_limit", !live ? none : number("%.0f%%", gw * 100.0),
                gw > 0.9 ? ALARM_RED : live ? ArcforgeGui.TEXT : dash);
        if (!menu.stellarator()) {
            reading(graphics, LEFT, VALUE_RIGHT, 3, "current", number("%.2f MA", menu.scaled(FusionReactorMenu.DATA_CURRENT, 100.0)),
                    live ? ArcforgeGui.TEXT : dash);
        } else {
            reading(graphics, LEFT, VALUE_RIGHT, 3, "current", Component.translatable("gui.arcforge.fusion_reactor.no_current"), dash);
        }
        reading(graphics, LEFT, VALUE_RIGHT, 4, "field", number("%.2f T", menu.scaled(FusionReactorMenu.DATA_FIELD, 100.0)), ArcforgeGui.TEXT);
        reading(graphics, LEFT, VALUE_RIGHT, 5, "tau", !live ? none : number("%.2f s", menu.scaled(FusionReactorMenu.DATA_TAU, 1000.0)),
                live ? ArcforgeGui.TEXT : dash);
        double beta = menu.scaled(FusionReactorMenu.DATA_BETA, 100.0);
        reading(graphics, LEFT, VALUE_RIGHT, 6, "beta", !live || menu.stellarator() ? none : number("%.2f", beta), beta > 3.2 ? ALARM_RED : ArcforgeGui.TEXT);
        reading(graphics, LEFT, VALUE_RIGHT, 7, "q95", !live || menu.stellarator() ? none : number("%.1f", menu.scaled(FusionReactorMenu.DATA_Q95, 100.0)),
                ArcforgeGui.TEXT);
        reading(graphics, LEFT, VALUE_RIGHT, 8, "position", !live || menu.stellarator() ? none
                : exact ? number("%+.0f%%", position * 100.0) : Component.translatable(Math.abs(position) > 0.33
                        ? "gui.arcforge.fusion_reactor.position_drifting" : "gui.arcforge.fusion_reactor.position_centred"),
                Math.abs(position) > 0.33 ? ALARM_RED : ArcforgeGui.TEXT);
        int pulse = menu.get(FusionReactorMenu.DATA_PULSE_LEFT);
        reading(graphics, LEFT, VALUE_RIGHT, 9, "pulse", !live ? none : pulse < 0 ? Component.translatable("gui.arcforge.fusion_reactor.steady")
                : Component.literal(pulse >= 120 ? pulse / 60 + " min" : pulse + " s"), pulse >= 0 && pulse < 30 ? ALARM_AMBER : ArcforgeGui.TEXT);
        double fusion = menu.scaled(FusionReactorMenu.DATA_FUSION, 10.0);
        double heating = menu.scaled(FusionReactorMenu.DATA_HEATING, 10.0);
        double q = menu.scaled(FusionReactorMenu.DATA_Q, 100.0);
        reading(graphics, RIGHT, RIGHT_END, 0, "fusion_power", number("%,.0f MW", fusion), live ? PLASMA : dash);
        reading(graphics, RIGHT, RIGHT_END, 1, "heating", number("%,.1f MW", heating), ArcforgeGui.TEXT);
        reading(graphics, RIGHT, RIGHT_END, 2, "gain", menu.flag(FusionReactorMenu.FLAG_IGNITED) ? Component.translatable("gui.arcforge.fusion_reactor.ignited")
                : number("%.2f", q), q >= 1.0 ? GOOD : ArcforgeGui.TEXT);
        reading(graphics, RIGHT, RIGHT_END, 3, "neutrons", number("%,.0f MW", menu.scaled(FusionReactorMenu.DATA_NEUTRON, 10.0)), ArcforgeGui.TEXT);
        reading(graphics, RIGHT, RIGHT_END, 4, "alpha", number("%,.0f MW", menu.scaled(FusionReactorMenu.DATA_ALPHA, 10.0)), ArcforgeGui.TEXT);
        reading(graphics, RIGHT, RIGHT_END, 5, "radiated", number("%,.1f MW", menu.scaled(FusionReactorMenu.DATA_RADIATED, 10.0)), ArcforgeGui.TEXT);
        reading(graphics, RIGHT, RIGHT_END, 6, "heat_out", Component.translatable("gui.arcforge.hu_per_tick", ArcforgeGui.grouped(menu.get(FusionReactorMenu.DATA_THERMAL))),
                ArcforgeGui.HEAT);
    }

    private void drawPlant(GuiGraphicsExtractor graphics) {
        double vacuum = Math.pow(10.0, menu.scaled(FusionReactorMenu.DATA_VACUUM, 100.0));
        boolean vacuumReady = menu.check(FusionReactorMenu.CHECK_VACUUM);
        reading(graphics, LEFT, VALUE_RIGHT, 0, "vacuum", Component.literal(vacuum >= 0.5 ? "1 atm" : String.format(Locale.ROOT, "%.0e atm", vacuum)),
                vacuumReady ? GOOD : ALARM_AMBER);
        double coil = menu.scaled(FusionReactorMenu.DATA_COIL_TEMP, 10.0);
        reading(graphics, LEFT, VALUE_RIGHT, 1, "coils", number("%.1f K", coil), coil <= FusionReactorBlockEntity.TC_FULL ? ArcforgeGui.ACCENT
                : coil <= FusionReactorBlockEntity.TC_NITROGEN ? ArcforgeGui.TEXT : ALARM_AMBER);
        reading(graphics, LEFT, VALUE_RIGHT, 2, "field_target", Component.literal(String.format(Locale.ROOT, "%.2f / %.2f T",
                menu.scaled(FusionReactorMenu.DATA_FIELD, 100.0), menu.scaled(FusionReactorMenu.DATA_FIELD_TARGET, 100.0))), ArcforgeGui.TEXT);
        if (!menu.stellarator()) {
            reading(graphics, LEFT, VALUE_RIGHT, 3, "solenoid", number("%.0f%%", menu.scaled(FusionReactorMenu.DATA_CS_CHARGE, 10.0)), ArcforgeGui.TEXT);
        } else {
            reading(graphics, LEFT, VALUE_RIGHT, 3, "solenoid", Component.translatable("gui.arcforge.fusion_reactor.no_solenoid"), ArcforgeGui.LABEL);
        }
        int wall = menu.get(FusionReactorMenu.DATA_WALL_TEMP), blanket = menu.get(FusionReactorMenu.DATA_BLANKET_TEMP);
        reading(graphics, LEFT, VALUE_RIGHT, 4, "wall_temp", Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(wall)),
                wall > FusionReactorBlockEntity.WALL_LIMIT * 0.8 ? ALARM_RED : ArcforgeGui.HEAT);
        reading(graphics, LEFT, VALUE_RIGHT, 5, "blanket_temp", Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(blanket)),
                blanket > FusionReactorBlockEntity.BLANKET_LIMIT * 0.8 ? ALARM_RED : ArcforgeGui.HEAT);
        double tbr = menu.scaled(FusionReactorMenu.DATA_TBR, 1000.0);
        reading(graphics, LEFT, VALUE_RIGHT, 6, "tbr", number("%.2f", tbr), tbr >= 1.0 ? GOOD : ALARM_AMBER);
        reading(graphics, LEFT, VALUE_RIGHT, 7, "burn", number("%.2f mB/s", menu.scaled(FusionReactorMenu.DATA_BURN, 1000.0)), ArcforgeGui.TEXT);
        reading(graphics, LEFT, VALUE_RIGHT, 8, "bred", number("%.2f mB/s", menu.scaled(FusionReactorMenu.DATA_BRED, 1000.0)), ArcforgeGui.TEXT);
        reading(graphics, LEFT, VALUE_RIGHT, 9, "pumping", number("%.0f%%", menu.scaled(FusionReactorMenu.DATA_PUMPING, 10.0)), ArcforgeGui.TEXT);
        for (int i = 0; i < WEAR.length; i++) {
            graphics.text(font, Component.translatable("gui.arcforge.fusion_reactor.wear." + WEAR[i]), RIGHT, lineY(i), ArcforgeGui.LABEL, false);
        }
        java.util.StringJoiner modules = new java.util.StringJoiner(" ");
        for (FusionModuleBlock.Kind kind : FusionModuleBlock.Kind.values()) {
            modules.add(String.valueOf(menu.modules(kind)));
        }
        reading(graphics, RIGHT, RIGHT_END, 4, "modules", Component.literal(modules.toString()), ArcforgeGui.TEXT);
        List<FusionAlarm> up = alarms();
        if (up.isEmpty()) {
            graphics.text(font, Component.translatable("gui.arcforge.fusion_reactor.no_alarms"), RIGHT, lineY(6), GOOD, false);
        }
        for (int i = 0; i < Math.min(5, up.size()); i++) {
            graphics.text(font, clipped(up.get(i).description(), RIGHT_END - RIGHT - 8), RIGHT + 8, lineY(6 + i), ArcforgeGui.TEXT, false);
        }
    }

    private void drawControl(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.fusion_reactor.checklist"), LEFT, lineY(0), ArcforgeGui.ACCENT, false);
        String[] checks = { "vacuum", "cold", "field", menu.stellarator() ? "no_solenoid" : "solenoid", "heating", "fuel" };
        for (int i = 0; i < checks.length; i++) {
            graphics.text(font, clipped(Component.translatable("gui.arcforge.fusion_reactor.check." + checks[i]), VALUE_RIGHT - LEFT - 8), LEFT + 8,
                    lineY(i + 1), menu.check(1 << i) ? ArcforgeGui.TEXT : ArcforgeGui.LABEL, false);
        }
        graphics.text(font, clipped(Component.translatable(menu.flag(FusionReactorMenu.FLAG_ALIVE) ? "gui.arcforge.fusion_reactor.burning"
                : "gui.arcforge.fusion_reactor.event." + menu.lastEvent().name().toLowerCase(Locale.ROOT)), VALUE_RIGHT - LEFT), LEFT, lineY(8),
                menu.lastEvent().name().startsWith("DISRUPTION") ? ALARM_RED : ArcforgeGui.LABEL, false);
        int[] values = { menu.get(FusionReactorMenu.DATA_HEATING_SET), menu.get(FusionReactorMenu.DATA_DENSITY_SET), menu.get(FusionReactorMenu.DATA_CURRENT_SET),
                menu.get(FusionReactorMenu.DATA_FIELD_SET) };
        for (int i = 0; i < SETPOINTS.length; i++) {
            boolean disabled = i == 2 && menu.stellarator();
            graphics.text(font, Component.translatable("gui.arcforge.fusion_reactor.set." + SETPOINTS[i]), RIGHT, lineY(i),
                    disabled ? ArcforgeGui.LABEL : ArcforgeGui.TEXT, false);
            textRight(graphics, Component.literal(values[i] + (i == 0 ? " MW" : "%")), SET_VALUE_RIGHT, lineY(i), disabled ? ArcforgeGui.LABEL : ArcforgeGui.ACCENT);
        }
        graphics.text(font, Component.translatable("gui.arcforge.fusion_reactor.fuel." + menu.fuelMode().name().toLowerCase(Locale.ROOT)), RIGHT + 10,
                lineY(5), ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable("gui.arcforge.fusion_reactor.repeat"), RIGHT + 10, lineY(6), ArcforgeGui.TEXT, false);
        textCentred(graphics, Component.translatable("gui.arcforge.fusion_reactor.silence"), RIGHT, WIDE + 18, lineY(8) + 2, ArcforgeGui.TEXT);
    }

    // --- Tooltips ---

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(PAGE_X, CONTROL_Y, PAGE_W, CONTROL_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fusion_reactor.page." + shown.name().toLowerCase(Locale.ROOT)));
            return;
        }
        if (isHovering(RUN_X, CONTROL_Y, WIDE, CONTROL_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fusion_reactor.run_hint"));
            return;
        }
        if (isHovering(SHUTDOWN_X, CONTROL_Y, SHUTDOWN_W, CONTROL_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fusion_reactor.fast_shutdown_hint"));
            return;
        }
        if (isHovering(LED_X, STATUS_Y - 1, STATUS_X + STATUS_W - LED_X, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
            return;
        }
        for (int tank = 0; tank < TANKS.length; tank++) {
            if (isHovering(TANK_X[tank], TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
                int amount = tankAmount(tank);
                addFluidTooltip(lines, amount > 0 ? tankFluid(tank) : Fluids.EMPTY, Component.translatable("gui.arcforge.fusion_reactor.tank." + tankName(tank)),
                        amount, menu.get(FusionReactorMenu.DATA_TANK) / (tank == 2 || tank == 3 ? 4 : 1));
                lines.add(Component.translatable("gui.arcforge.fusion_reactor.tank." + tankName(tank) + ".hint").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
        }
        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, TANK_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.get(FusionReactorMenu.DATA_ENERGY)),
                    ArcforgeGui.grouped(menu.get(FusionReactorMenu.DATA_ENERGY_CAPACITY))));
            lines.add(Component.translatable("gui.arcforge.fusion_reactor.energy_hint").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!menu.isFormed()) {
            return;
        }
        switch (shown) {
            case PLASMA -> {
                for (int line = 0; line < 10; line++) {
                    if (isHovering(LEFT, lineY(line) - 1, VALUE_RIGHT - LEFT, LINE, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.arcforge.fusion_reactor.hint.plasma_" + line));
                        if (line == 2 && menu.isFormed()) {
                            lines.add(Component.translatable("gui.arcforge.fusion_reactor.density_limit_value", String.format(Locale.ROOT, "%.2f",
                                    menu.scaled(FusionReactorMenu.DATA_DENSITY_LIMIT, 1000.0))).withStyle(ChatFormatting.GRAY));
                        }
                        if ((line == 0 || line == 1 || line == 8) && !menu.flag(FusionReactorMenu.FLAG_DIAGNOSTICS)) {
                            lines.add(Component.translatable("gui.arcforge.fusion_reactor.needs_diagnostics").withStyle(ChatFormatting.GOLD));
                        }
                        return;
                    }
                    if (line < 7 && isHovering(RIGHT, lineY(line) - 1, RIGHT_END - RIGHT, LINE, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.arcforge.fusion_reactor.hint.power_" + line));
                        return;
                    }
                }
                if (isHovering(GRAPH_X, GRAPH_Y, GRAPH_W, GRAPH_H, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fusion_reactor.trend_hint"));
                }
            }
            case PLANT -> {
                for (int line = 0; line < 10; line++) {
                    if (isHovering(LEFT, lineY(line) - 1, VALUE_RIGHT - LEFT, LINE, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.arcforge.fusion_reactor.hint.plant_" + line));
                        return;
                    }
                }
                for (int i = 0; i < WEAR.length; i++) {
                    if (isHovering(RIGHT, lineY(i) - 1, RIGHT_END - RIGHT, LINE, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.arcforge.fusion_reactor.wear." + WEAR[i] + ".hint"));
                        return;
                    }
                }
                if (isHovering(RIGHT, lineY(4) - 1, RIGHT_END - RIGHT, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fusion_reactor.modules_hint"));
                    for (FusionModuleBlock.Kind kind : FusionModuleBlock.Kind.values()) {
                        lines.add(Component.translatable("block.arcforge." + kind.getSerializedName()).append(": " + menu.modules(kind))
                                .withStyle(ChatFormatting.GRAY));
                    }
                    lines.add(Component.translatable("gui.arcforge.fusion_reactor.quench_units", menu.get(FusionReactorMenu.DATA_QUENCH_UNITS))
                            .withStyle(ChatFormatting.GRAY));
                    return;
                }
                List<FusionAlarm> up = alarms();
                for (int i = 0; i < Math.min(5, up.size()); i++) {
                    if (isHovering(RIGHT, lineY(6 + i) - 1, RIGHT_END - RIGHT, LINE, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.arcforge.fusion_reactor.alarm." + up.get(i).name().toLowerCase(Locale.ROOT) + ".hint"));
                        return;
                    }
                }
            }
            case CONTROL -> {
                String[] checks = { "vacuum", "cold", "field", "solenoid", "heating", "fuel" };
                for (int i = 0; i < checks.length; i++) {
                    if (isHovering(LEFT, lineY(i + 1) - 1, VALUE_RIGHT - LEFT, LINE, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.arcforge.fusion_reactor.check." + checks[i] + ".hint"));
                        return;
                    }
                }
                for (int i = 0; i < SETPOINTS.length; i++) {
                    if (isHovering(RIGHT, lineY(i) - 1, RIGHT_END - RIGHT, LINE, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.arcforge.fusion_reactor.set." + SETPOINTS[i] + ".hint"));
                        if (i == 0) {
                            lines.add(Component.translatable("gui.arcforge.fusion_reactor.heating_available", String.format(Locale.ROOT, "%.0f",
                                    menu.scaled(FusionReactorMenu.DATA_HEAT_AVAILABLE, 10.0))).withStyle(ChatFormatting.GRAY));
                        }
                        lines.add(Component.translatable("gui.arcforge.fusion_reactor.shift_hint").withStyle(ChatFormatting.DARK_GRAY));
                        return;
                    }
                }
                if (isHovering(RIGHT, lineY(5) - 1, RIGHT_END - RIGHT, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fusion_reactor.fuel_hint"));
                } else if (isHovering(RIGHT, lineY(6) - 1, RIGHT_END - RIGHT, LINE, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fusion_reactor.repeat_hint"));
                } else if (isHovering(RIGHT, lineY(8), WIDE + 18, CONTROL_H, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.arcforge.fusion_reactor.silence_hint"));
                }
            }
        }
    }

    // --- Clicks ---

    // The button under the mouse, or -1.
    private int buttonAt(double mx, double my, boolean shift) {
        if (isHovering(RUN_X, CONTROL_Y, WIDE, CONTROL_H, mx, my)) {
            return FusionReactorMenu.RUN;
        }
        if (isHovering(SHUTDOWN_X, CONTROL_Y, SHUTDOWN_W, CONTROL_H, mx, my)) {
            return FusionReactorMenu.FAST_SHUTDOWN;
        }
        if (shown != Page.CONTROL) {
            return -1;
        }
        for (int i = 0; i < SETPOINTS.length; i++) {
            if (isHovering(SET_MINUS, lineY(i), BUTTON, BUTTON, mx, my)) {
                return FusionReactorMenu.stepButton(i, false, shift);
            }
            if (isHovering(SET_PLUS, lineY(i), BUTTON, BUTTON, mx, my)) {
                return FusionReactorMenu.stepButton(i, true, shift);
            }
        }
        if (isHovering(RIGHT, lineY(5), RIGHT_END - RIGHT, BUTTON, mx, my)) {
            return FusionReactorMenu.FUEL;
        }
        if (isHovering(RIGHT, lineY(6), RIGHT_END - RIGHT, BUTTON, mx, my)) {
            return FusionReactorMenu.REPEAT;
        }
        if (isHovering(RIGHT, lineY(8), WIDE + 18, CONTROL_H, mx, my)) {
            return FusionReactorMenu.SILENCE;
        }
        return -1;
    }

    // A left or right click on the desk's controls, run from a highest-priority ScreenEvent.MouseButtonPressed.Pre listener
    // (see ArcforgeClient) as well as mouseClicked, as the Fission Reactor's desk is.
    public static void deskClick(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof FusionReactorScreen screen && isPress(event.getButton()) && screen.deskClick(event.getMouseX(), event.getMouseY())) {
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
        int id = buttonAt(mx, my, Minecraft.getInstance().hasShiftDown());
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
