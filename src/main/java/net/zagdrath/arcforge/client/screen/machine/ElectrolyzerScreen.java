/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.ElectrolyzerMenu;

// Layout follows electrolyzer_gui_layout.json. All positions are relative to leftPos/topPos. Water on the left,
// hydrogen and oxygen on the right; between them the recipe's ratio and the FE per mB of hydrogen.
public class ElectrolyzerScreen extends MachineScreen<ElectrolyzerMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int WATER_X = 25, HYDROGEN_X = 139, OXYGEN_X = 155;
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int PROGRESS_X = 114, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int TEXT_X = 43, RATIO_Y = 24, COST_Y = 36, TEXT_W = 66;
    private static final int LED_X = 43, LED_Y = 58;
    private static final int STATUS_X = 51, STATUS_Y = 58;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier ventOn = sprite("vent_on");
    private final Identifier ventOff = sprite("vent_off");
    // A vent toggle under each gas tank, in the gap above the inventory.
    private static final int VENT_Y = 71, VENT_W = 14, VENT_H = 12;
    private static final int HOVER = 0x30FFFFFF;
    private int mouseX, mouseY;

    public ElectrolyzerScreen(ElectrolyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "electrolyzer", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        // Every fluid here has a model (the gases share the steam texture, tinted), so the gauge doubles as the
        // (unused) fallback fill.
        drawFluidTank(graphics, menu.getWaterFluid(), menu.getWater(), menu.getWaterCapacity(), tankGauge, tankGauge,
                x, y, WATER_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, menu.getPrimaryFluid(), menu.getPrimary(), menu.getGasCapacity(), tankGauge, tankGauge,
                x, y, HYDROGEN_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, menu.getSecondaryFluid(), menu.getSecondary(), menu.getGasCapacity(), tankGauge, tankGauge,
                x, y, OXYGEN_X, TANK_Y, TANK_W, TANK_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        vent(graphics, x, y, HYDROGEN_X - 1, menu.isVentingHydrogen());
        vent(graphics, x, y, OXYGEN_X - 1, menu.isVentingOxygen());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    private void vent(GuiGraphicsExtractor graphics, int x, int y, int ventX, boolean on) {
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, on ? ventOn : ventOff, x + ventX, y + VENT_Y, VENT_W, VENT_H);
        if (isHovering(ventX, VENT_Y, VENT_W, VENT_H, mouseX, mouseY)) {
            graphics.fill(x + ventX, y + VENT_Y, x + ventX + VENT_W, y + VENT_Y + VENT_H, HOVER);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && minecraft.gameMode != null) {
            int button = isHovering(HYDROGEN_X - 1, VENT_Y, VENT_W, VENT_H, event.x(), event.y()) ? ElectrolyzerMenu.BUTTON_VENT_HYDROGEN
                    : isHovering(OXYGEN_X - 1, VENT_Y, VENT_W, VENT_H, event.x(), event.y()) ? ElectrolyzerMenu.BUTTON_VENT_OXYGEN : -1;
            if (button >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
                ArcforgeGui.playClickSound();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        Component ratio = ratio();
        if (ratio != null) {
            graphics.text(font, ratio, TEXT_X, RATIO_Y, ArcforgeGui.LABEL, false);
            graphics.text(font, Component.translatable("gui.arcforge.electrolyzer.cost",
                    ArcforgeGui.grouped(perPrimary(menu.getCost()))), TEXT_X, COST_Y, ArcforgeGui.LABEL, false);
        }
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    // "1 → 2 + 1": the recipe's input, primary and secondary amounts, reduced. Null without a recipe.
    private Component ratio() {
        int input = menu.getRecipeInput();
        int primary = menu.getRecipePrimary();
        int secondary = menu.getRecipeSecondary();
        if (input <= 0 || primary <= 0) {
            return null;
        }
        int divisor = gcd(gcd(input, primary), secondary);
        if (secondary <= 0) {
            return Component.literal(input / divisor + " → " + primary / divisor);
        }
        return Component.translatable("gui.arcforge.electrolyzer.ratio", input / divisor, primary / divisor, secondary / divisor);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    // FE per mB of the primary output (hydrogen), rounded up.
    private long perPrimary(int fe) {
        int primary = menu.getRecipePrimary();
        return primary > 0 ? (long) Math.ceil((double) fe / primary) : 0;
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        for (boolean hydrogen : new boolean[] { true, false }) {
            if (isHovering((hydrogen ? HYDROGEN_X : OXYGEN_X) - 1, VENT_Y, VENT_W, VENT_H, mouseX, mouseY)) {
                boolean on = hydrogen ? menu.isVentingHydrogen() : menu.isVentingOxygen();
                lines.add(Component.translatable(hydrogen ? "gui.arcforge.electrolyzer.vent_hydrogen" : "gui.arcforge.electrolyzer.vent_oxygen"));
                lines.add(Component.translatable(on ? "gui.arcforge.electrolyzer.vent_on" : "gui.arcforge.electrolyzer.vent_off")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }
        }
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
            return;
        }
        if (isHovering(WATER_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getWaterFluid(), Component.translatable("gui.arcforge.empty"), menu.getWater(), menu.getWaterCapacity());
            return;
        }
        if (isHovering(HYDROGEN_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getPrimaryFluid(), Component.translatable("fluid_type.arcforge.hydrogen"), menu.getPrimary(), menu.getGasCapacity());
            return;
        }
        if (isHovering(OXYGEN_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getSecondaryFluid(), Component.translatable("fluid_type.arcforge.oxygen"), menu.getSecondary(), menu.getGasCapacity());
            return;
        }
        if (menu.getRecipePrimary() > 0 && isHovering(TEXT_X, COST_Y - 1, TEXT_W, font.lineHeight + 1, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.electrolyzer.cost.hint", ArcforgeGui.grouped(perPrimary(menu.getFloor())))
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.percent", (int) (100L * menu.getProgress() / menu.getTotal())));
        }
    }
}
