/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.nuclear;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.menu.nuclear.GeologicalRepositoryMenu;

// A Geological Repository's GUI (textures/gui/container/geological_repository.png, 176x166): the screen listing what
// it holds over the deposit slot, the player inventory below.
public class GeologicalRepositoryScreen extends AbstractContainerScreen<GeologicalRepositoryMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/geological_repository.png");
    private static final int TEXT_X = 12, TEXT_Y = 18, LINE = 10;

    public GeologicalRepositoryScreen(GeologicalRepositoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        titleLabelY = 5;
        inventoryLabelY = 72;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
        if (!menu.validSite()) {
            graphics.text(font, Component.translatable("gui.arcforge.geological_repository.invalid"), TEXT_X, TEXT_Y, ArcforgeGui.WARNING, false);
            graphics.text(font, Component.translatable("gui.arcforge.geological_repository.invalid_hint"), TEXT_X, TEXT_Y + LINE, ArcforgeGui.LABEL, false);
        } else {
            graphics.text(font, Component.translatable("gui.arcforge.geological_repository.logs", menu.logs()), TEXT_X, TEXT_Y, ArcforgeGui.TEXT, false);
            graphics.text(font, Component.translatable("gui.arcforge.geological_repository.casks", menu.casks(), menu.rods()), TEXT_X, TEXT_Y + LINE,
                    ArcforgeGui.TEXT, false);
        }
        graphics.text(font, Component.translatable("gui.arcforge.geological_repository.activity", String.format(Locale.ROOT, "%,.1f", menu.activity())),
                TEXT_X, TEXT_Y + LINE * 2, ArcforgeGui.ACCENT, false);
    }
}
