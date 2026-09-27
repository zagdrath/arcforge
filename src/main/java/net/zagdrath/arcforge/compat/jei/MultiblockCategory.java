/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jei;

import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.StructureRenderer;
import net.zagdrath.arcforge.multiblock.MultiblockBlueprints;
import net.zagdrath.arcforge.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;

// How to build each multiblock, as a 3D view you can turn (drag, or the Turn button) and step through a
// layer at a time (the arrows, or scroll), with the blocks it takes below.
final class MultiblockCategory extends ArcforgeCategory<MultiblockCategory.Build> {
    static final IRecipeType<Build> TYPE = IRecipeType.create(Arcforge.MODID, "multiblock", Build.class);

    private static final int WIDTH = 170, HEIGHT = 164;
    private static final int VIEW_X = 0, VIEW_Y = 12, VIEW_W = 170, VIEW_H = 108;
    private static final int CONTROLS_Y = 124, BUTTON_H = 12;
    private static final int PREV_X = 0, NEXT_X = 88, ARROW_W = 12, TURN_X = 120, TURN_W = 50;
    private static final int SLOTS_Y = 144, SLOT_PITCH = 18;
    // Dragging this far turns the view a quarter turn.
    private static final double DRAG_PER_TURN = 30.0;
    private static final int BUTTON = 0xFF8B8B8B, BUTTON_HOVER = 0xFFA0A0A0, BUTTON_BORDER = 0xFF373737;

    // One multiblock, and how it's being viewed (kept while JEI is open).
    static final class Build {
        final MultiblockBlueprints.Blueprint blueprint;
        int rotation;
        int layer;
        double drag;

        Build(MultiblockBlueprints.Blueprint blueprint) {
            this.blueprint = blueprint;
            this.layer = blueprint.layers() - 1;
        }

        boolean showsAll() {
            return layer >= blueprint.layers() - 1;
        }

        void step(int by) {
            layer = Math.clamp(layer + by, 0, blueprint.layers() - 1);
        }
    }

    MultiblockCategory(IGuiHelper gui) {
        super(TYPE, "multiblock", ModBlocks.STEAM_BOILER_ARRAY_CASING.get(), gui, WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Build build, IFocusGroup focuses) {
        int x = 1;
        for (Map.Entry<Item, Integer> entry : build.blueprint.bill().entrySet()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, SLOTS_Y).setStandardSlotBackground()
                    .add(new ItemStack(entry.getKey(), entry.getValue()));
            x += SLOT_PITCH;
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Build build, IFocusGroup focuses) {
        builder.addGuiEventListener(new IJeiGuiEventListener() {
            @Override
            public ScreenRectangle getArea() {
                return new ScreenRectangle(0, 0, WIDTH, SLOTS_Y - 2);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (inside(mouseX, mouseY, PREV_X, CONTROLS_Y, ARROW_W, BUTTON_H)) {
                    build.step(-1);
                } else if (inside(mouseX, mouseY, NEXT_X, CONTROLS_Y, ARROW_W, BUTTON_H)) {
                    build.step(1);
                } else if (inside(mouseX, mouseY, TURN_X, CONTROLS_Y, TURN_W, BUTTON_H)) {
                    build.rotation++;
                } else {
                    return inside(mouseX, mouseY, VIEW_X, VIEW_Y, VIEW_W, VIEW_H);
                }
                ArcforgeGui.playClickSound();
                return true;
            }

            @Override
            public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
                if (!inside(mouseX, mouseY, VIEW_X, VIEW_Y, VIEW_W, VIEW_H)) {
                    return false;
                }
                build.drag += dragX;
                while (Math.abs(build.drag) >= DRAG_PER_TURN) {
                    build.rotation += build.drag > 0 ? 1 : -1;
                    build.drag -= Math.signum(build.drag) * DRAG_PER_TURN;
                }
                return true;
            }

            @Override
            public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
                if (!inside(mouseX, mouseY, VIEW_X, VIEW_Y, VIEW_W, VIEW_H) || scrollY == 0) {
                    return false;
                }
                build.step(scrollY > 0 ? 1 : -1);
                return true;
            }
        });
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void draw(Build build, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        text(graphics, build.blueprint.name(), 0, 0);
        StructureRenderer.draw(graphics, build.blueprint, VIEW_X, VIEW_Y, VIEW_W, VIEW_H, build.rotation, build.layer, !build.showsAll());

        button(graphics, PREV_X, ARROW_W, Component.literal("<"), mouseX, mouseY);
        button(graphics, NEXT_X, ARROW_W, Component.literal(">"), mouseX, mouseY);
        button(graphics, TURN_X, TURN_W, Component.translatable("jei.arcforge.multiblock.turn"), mouseX, mouseY);
        Component layer = build.showsAll()
                ? Component.translatable("jei.arcforge.multiblock.all_layers", build.blueprint.layers())
                : Component.translatable("jei.arcforge.multiblock.layer", build.layer + 1, build.blueprint.layers());
        int labelX = PREV_X + ARROW_W + (NEXT_X - PREV_X - ARROW_W - Minecraft.getInstance().font.width(layer)) / 2;
        text(graphics, layer, labelX, CONTROLS_Y + 2);
    }

    private static void button(GuiGraphicsExtractor graphics, int x, int width, Component label, double mouseX, double mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, CONTROLS_Y, width, BUTTON_H);
        graphics.fill(x, CONTROLS_Y, x + width, CONTROLS_Y + BUTTON_H, BUTTON_BORDER);
        graphics.fill(x + 1, CONTROLS_Y + 1, x + width - 1, CONTROLS_Y + BUTTON_H - 1, hovered ? BUTTON_HOVER : BUTTON);
        var font = Minecraft.getInstance().font;
        graphics.text(font, label, x + (width - font.width(label)) / 2, CONTROLS_Y + 2, 0xFFFFFFFF, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, Build build, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, VIEW_X, VIEW_Y, VIEW_W, VIEW_H)) {
            tooltip.add(build.blueprint.name());
            tooltip.add(build.blueprint.rules().copy().withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("jei.arcforge.multiblock.controls").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
