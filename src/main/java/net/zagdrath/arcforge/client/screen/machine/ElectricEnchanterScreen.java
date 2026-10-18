/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.blockentity.machine.ElectricEnchanterBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.ElectricEnchanterMenu;
import net.zagdrath.arcforge.network.EnchanterTargetPayload;
import net.zagdrath.arcforge.registry.ModFluids;

// Layout follows gui_layouts.json "electric_enchanter" (176x222). The top holds the energy gauge, the Liquid
// Experience tank, the input and Lapis slots, the progress arrow, the output and the status with this item's cost. Under
// it the target list: every enchantment on offer (those the input item supports when there is one), each with its
// target level and - / + buttons; clicking a name sets it to its max level or clears it. A row that clashes with a
// target is greyed (setting it drops the one it clashes with).
public class ElectricEnchanterScreen extends MachineScreen<ElectricEnchanterMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int XP_X = 27, XP_Y = 19, XP_W = 6, XP_H = 50;
    private static final int PROGRESS_X = 66, PROGRESS_Y = 20, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 66, LED_Y = 46;
    private static final int STATUS_X = 74, STATUS_Y = 46;
    private static final int COST_X = 66, COST_Y = 58;
    // The target list: rows of ROW_H from LIST_Y, LIST_W wide, with the scrollbar track to their right.
    private static final int LIST_X = 8, LIST_Y = 76, LIST_W = 146, ROW_H = 12, ROWS = 4;
    private static final int MINUS_DX = 108, LEVEL_DX = 124, PLUS_DX = 135, BUTTON = 9;
    private static final int TRACK_X = 157, TRACK_Y = 76, TRACK_W = 12, TRACK_H = 48, THUMB_H = 15;
    private static final int CLASH = 0xFF6E6E6E;
    private static final int TARGETED = 0xFFC9A6FF;
    private static final Identifier SCROLLER = ArcforgeGui.sprite("container/crate/scroller");
    private static final Identifier SCROLLER_DISABLED = ArcforgeGui.sprite("container/crate/scroller_disabled");

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier xpFill = sprite("xp_fill");
    private final Identifier xpGauge = sprite("xp_gauge");
    private final Identifier progress = sprite("progress");
    private final Identifier ghostBook = sprite("ghost_book");
    private final Identifier ghostLapis = sprite("ghost_lapis");
    private final Identifier row = sprite("row");
    private final Identifier rowHover = sprite("row_hover");
    private final Identifier rowTarget = sprite("row_target");
    private final Identifier minus = sprite("minus");
    private final Identifier minusHover = sprite("minus_hover");
    private final Identifier plus = sprite("plus");
    private final Identifier plusHover = sprite("plus_hover");
    private int scroll;
    private boolean dragging;
    private int mouseX, mouseY;

    public ElectricEnchanterScreen(ElectricEnchanterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "electric_enchanter", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)), true, 222);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    // --- The list ---

    private ItemStack input() {
        return menu.getSlotFor(ElectricEnchanterBlockEntity.SLOT_INPUT).getItem();
    }

    private List<Holder<Enchantment>> candidates() {
        if (minecraft.level == null) {
            return List.of();
        }
        return ElectricEnchanterBlockEntity.candidates(minecraft.level.registryAccess(), input(), menu.treasureOffered());
    }

    private Map<ResourceKey<Enchantment>, Integer> target() {
        if (minecraft.level != null && minecraft.level.getBlockEntity(menu.getPos()) instanceof ElectricEnchanterBlockEntity enchanter) {
            return enchanter.getTarget();
        }
        return Map.of();
    }

    private int targetLevel(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(key -> target().getOrDefault(key, 0)).orElse(0);
    }

    // Whether it clashes with a target other than itself.
    private boolean clashes(Holder<Enchantment> enchantment) {
        if (minecraft.level == null) {
            return false;
        }
        var lookup = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (ResourceKey<Enchantment> key : target().keySet()) {
            var other = lookup.get(key);
            if (other.isPresent() && !other.get().is(enchantment) && !Enchantment.areCompatible(enchantment, other.get())) {
                return true;
            }
        }
        return false;
    }

    private int maxScroll(int count) {
        return Math.max(0, count - ROWS);
    }

    // The candidate index of the row under the mouse, or -1.
    private int rowAt(double mouseX, double mouseY, int count) {
        if (!isHovering(LIST_X, LIST_Y, LIST_W, ROWS * ROW_H, mouseX, mouseY)) {
            return -1;
        }
        int index = scroll + (int) ((mouseY - topPos - LIST_Y) / ROW_H);
        return index < count ? index : -1;
    }

    private void send(Holder<Enchantment> enchantment, int level) {
        enchantment.unwrapKey().ifPresent(key -> ClientPacketDistributor.sendToServer(
                new EnchanterTargetPayload(menu.containerId, key.identifier(), Math.max(0, level))));
        ArcforgeGui.playClickSound();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getEnergyCapacity());
        drawFluidTank(graphics, ModFluids.LIQUID_EXPERIENCE.get(), menu.getXp(), menu.getXpCapacity(), xpFill, xpGauge,
                x, y, XP_X, XP_Y, XP_W, XP_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        ghost(graphics, ghostBook, !menu.getSlotFor(ElectricEnchanterBlockEntity.SLOT_INPUT).hasItem(), ElectricEnchanterMenu.INPUT_X,
                ElectricEnchanterMenu.INPUT_Y);
        ghost(graphics, ghostLapis, !menu.getSlotFor(ElectricEnchanterBlockEntity.SLOT_LAPIS).hasItem(), ElectricEnchanterMenu.LAPIS_X,
                ElectricEnchanterMenu.LAPIS_Y);
        List<Holder<Enchantment>> list = candidates();
        scroll = Mth.clamp(scroll, 0, maxScroll(list.size()));
        int hovered = rowAt(mouseX, mouseY, list.size());
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            Holder<Enchantment> enchantment = list.get(scroll + i);
            int rowY = y + LIST_Y + i * ROW_H;
            Identifier background = targetLevel(enchantment) > 0 ? rowTarget : scroll + i == hovered ? rowHover : row;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, background, x + LIST_X, rowY, LIST_W, ROW_H);
            boolean overMinus = isHovering(LIST_X + MINUS_DX, LIST_Y + i * ROW_H + 1, BUTTON, BUTTON, mouseX, mouseY);
            boolean overPlus = isHovering(LIST_X + PLUS_DX, LIST_Y + i * ROW_H + 1, BUTTON, BUTTON, mouseX, mouseY);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overMinus ? minusHover : minus, x + LIST_X + MINUS_DX, rowY + 1, BUTTON, BUTTON);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overPlus ? plusHover : plus, x + LIST_X + PLUS_DX, rowY + 1, BUTTON, BUTTON);
        }
        int maxScroll = maxScroll(list.size());
        int thumbY = TRACK_Y + (maxScroll == 0 ? 0 : Math.round((TRACK_H - THUMB_H) * scroll / (float) maxScroll));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, maxScroll > 0 ? SCROLLER : SCROLLER_DISABLED, x + TRACK_X, y + thumbY, TRACK_W, THUMB_H);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), imageWidth - 8 - STATUS_X), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        if (menu.getLapisCost() > 0 || menu.getXpCost() > 0) {
            Component cost = Component.translatable("gui.arcforge.enchanter.cost", menu.getLapisCost(),
                    ArcforgeGui.grouped(menu.getXpCost() / LiquidExperience.MB_PER_POINT));
            graphics.text(font, clipped(cost, imageWidth - 8 - COST_X), COST_X, COST_Y, ArcforgeGui.LABEL, false);
        }
        List<Holder<Enchantment>> list = candidates();
        if (list.isEmpty()) {
            graphics.text(font, Component.translatable("gui.arcforge.enchanter.none"), LIST_X + 3, LIST_Y + 2, ArcforgeGui.LABEL, false);
        }
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            Holder<Enchantment> enchantment = list.get(scroll + i);
            int level = targetLevel(enchantment);
            int rowY = LIST_Y + i * ROW_H + 2;
            int color = level > 0 ? TARGETED : clashes(enchantment) ? CLASH : ArcforgeGui.TEXT;
            Component plain = enchantment.value().description();
            graphics.text(font, clipped(plain, MINUS_DX - 4), LIST_X + 3, rowY, color, false);
            Component levelText = level > 0 ? Component.translatable("enchantment.level." + level) : Component.literal("-");
            int centre = LIST_X + LEVEL_DX + 5;
            graphics.text(font, levelText, centre - font.width(levelText) / 2, rowY, color, false);
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            List<Holder<Enchantment>> list = candidates();
            if (isHovering(TRACK_X, TRACK_Y, TRACK_W, TRACK_H, event.x(), event.y()) && maxScroll(list.size()) > 0) {
                dragging = true;
                dragTo(event.y(), list.size());
                return true;
            }
            int index = rowAt(event.x(), event.y(), list.size());
            if (index >= 0) {
                Holder<Enchantment> enchantment = list.get(index);
                int rowY = LIST_Y + (index - scroll) * ROW_H + 1;
                int level = targetLevel(enchantment);
                int max = enchantment.value().getMaxLevel();
                if (isHovering(LIST_X + MINUS_DX, rowY, BUTTON, BUTTON, event.x(), event.y())) {
                    if (level > 0) {
                        send(enchantment, level - 1);
                    }
                } else if (isHovering(LIST_X + PLUS_DX, rowY, BUTTON, BUTTON, event.x(), event.y())) {
                    if (level < max) {
                        send(enchantment, level + 1);
                    }
                } else {
                    send(enchantment, level > 0 ? 0 : max);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void dragTo(double mouseY, int count) {
        float along = (float) (mouseY - topPos - TRACK_Y - THUMB_H / 2.0) / (TRACK_H - THUMB_H);
        scroll = Math.round(Mth.clamp(along, 0.0F, 1.0F) * maxScroll(count));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            dragTo(event.y(), candidates().size());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isHovering(LIST_X, LIST_Y, LIST_W + TRACK_W + 3, ROWS * ROW_H, mouseX, mouseY) && scrollY != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll(candidates().size()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getEnergyCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(XP_X - 1, XP_Y - 1, XP_W + 2, XP_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, ModFluids.LIQUID_EXPERIENCE.get(), Component.translatable("fluid_type.arcforge.liquid_experience"),
                    menu.getXp(), menu.getXpCapacity());
            lines.add(Component.translatable("gui.arcforge.vacuum.xp_points", ArcforgeGui.grouped(menu.getXp() / LiquidExperience.MB_PER_POINT))
                    .withStyle(ChatFormatting.GREEN));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY)) {
            if (menu.getTotal() > 0) {
                lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
            }
            if (menu.getLapisCost() > 0 || menu.getXpCost() > 0) {
                lines.add(Component.translatable("gui.arcforge.enchanter.cost_lapis", menu.getLapisCost()).withStyle(ChatFormatting.BLUE));
                lines.add(Component.translatable("gui.arcforge.enchanter.cost_xp", ArcforgeGui.grouped(menu.getXpCost()),
                        ArcforgeGui.grouped(menu.getXpCost() / LiquidExperience.MB_PER_POINT)).withStyle(ChatFormatting.GREEN));
            }
        } else {
            List<Holder<Enchantment>> list = candidates();
            int index = rowAt(mouseX, mouseY, list.size());
            if (index >= 0) {
                Holder<Enchantment> enchantment = list.get(index);
                int level = targetLevel(enchantment);
                lines.add(enchantment.value().description());
                lines.add(Component.translatable("gui.arcforge.enchanter.max_level", Component.translatable("enchantment.level." + enchantment.value().getMaxLevel()))
                        .withStyle(ChatFormatting.GRAY));
                if (level == 0 && clashes(enchantment)) {
                    lines.add(Component.translatable("gui.arcforge.enchanter.clashes").withStyle(ChatFormatting.RED));
                }
                lines.add(Component.translatable("gui.arcforge.enchanter.row_help").withStyle(ChatFormatting.DARK_GRAY));
            } else if (!menu.getSlotFor(ElectricEnchanterBlockEntity.SLOT_INPUT).hasItem()
                    && isHovering(ElectricEnchanterMenu.INPUT_X - 1, ElectricEnchanterMenu.INPUT_Y - 1, 18, 18, mouseX, mouseY)) {
                lines.add(Component.translatable("gui.arcforge.enchanter.input"));
            } else if (!menu.getSlotFor(ElectricEnchanterBlockEntity.SLOT_LAPIS).hasItem()
                    && isHovering(ElectricEnchanterMenu.LAPIS_X - 1, ElectricEnchanterMenu.LAPIS_Y - 1, 18, 18, mouseX, mouseY)) {
                lines.add(Component.translatable("gui.arcforge.enchanter.lapis"));
            }
        }
    }
}
