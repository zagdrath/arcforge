package net.zagdrath.arcforge.client.screen.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.gui.tab.RedstoneTab;
import net.zagdrath.arcforge.client.gui.tab.SideConfigTab;
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.client.gui.tab.UpgradesTab;
import net.zagdrath.arcforge.heat.GeothermalHeat;
import net.zagdrath.arcforge.menu.machine.GeothermalPlantMenu;

// Layout follows the Geothermal Plant GUI spec. All positions are relative to leftPos/topPos.
public class GeothermalPlantScreen extends AbstractContainerScreen<GeothermalPlantMenu> {
    private static final String MACHINE = "geothermal_plant";
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/geothermal_plant.png");
    private static final Identifier ENERGY_BAR = sprite("energy_bar");
    private static final Identifier LAVA_FILL = sprite("lava_fill");
    private static final Identifier TANK_GAUGE = sprite("tank_gauge");
    private static final Identifier HEAT_BAR = sprite("heat_bar");
    private static final Identifier HEAT_MARKER = sprite("heat_marker");
    private static final Identifier FLAME_ON = sprite("flame_on");
    private static final Identifier FLAME_OFF = sprite("flame_off");
    private static final Identifier GHOST_BUCKET = sprite("ghost_bucket");

    private static final int ENERGY_X = 157, ENERGY_Y = 19, ENERGY_W = 10, ENERGY_H = 50;
    private static final int TANK_X = 9, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int HEAT_X = 57, HEAT_Y = 34, HEAT_W = 84, HEAT_H = 4;
    private static final int FLAME_X = 127, FLAME_Y = 52, FLAME_SIZE = 14;
    private static final int LED_X = 57, LED_Y = 56, LED_SIZE = 6;
    private static final int STATUS_X = 65, STATUS_Y = 56;
    private static final int SCREEN_LEFT = 57, SCREEN_RIGHT = 141;
    private static final int HEAT_TEXT_Y = 23, OUTPUT_TEXT_Y = 42;

    private final SideTabPanel tabs;

    public GeothermalPlantScreen(GeothermalPlantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.tabs = new SideTabPanel()
                .add(new EnergyTab(menu::getEnergy, menu::getFePerTick))
                .add(new RedstoneTab(menu::getRedstoneMode, mode -> sendButton(GeothermalPlantMenu.redstoneButtonId(mode))))
                .add(new SideConfigTab(menu::getSideMode, (side, action) -> sendButton(GeothermalPlantMenu.sideButtonId(side, action))))
                .add(new UpgradesTab(menu.getUpgradeSlots()));
    }

    private static Identifier sprite(String name) {
        return ArcforgeGui.sprite("container/" + MACHINE + "/" + name);
    }

    private void sendButton(int buttonId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    public void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        tabs.layout(leftPos, topPos);
    }

    // --- Rendering ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);

        drawTank(graphics, x, y);

        // Energy: cropped from the bottom so the pixel pattern stays put.
        int energyHeight = scaledRound(menu.getEnergy(), menu.getEnergyCapacity(), ENERGY_H);
        if (energyHeight > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_BAR, ENERGY_W, ENERGY_H, 0, ENERGY_H - energyHeight,
                    x + ENERGY_X, y + ENERGY_Y + ENERGY_H - energyHeight, ENERGY_W, energyHeight);
        }

        int heatWidth = scaledRound(menu.getHeat(), menu.getMaxHeat(), HEAT_W);
        if (heatWidth > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEAT_BAR, HEAT_W, HEAT_H, 0, 0, x + HEAT_X, y + HEAT_Y, heatWidth, HEAT_H);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEAT_MARKER, x + HEAT_X + heatWidth - 1, y + HEAT_Y - 1, 2, 6);
        }

        // Flame drains from the top as the current fuel burns down.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME_OFF, x + FLAME_X, y + FLAME_Y, FLAME_SIZE, FLAME_SIZE);
        if (menu.getBurnTotal() > 0 && menu.getBurnTime() > 0) {
            int flameHeight = Mth.ceil(FLAME_SIZE * (float) menu.getBurnTime() / menu.getBurnTotal());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME_ON, FLAME_SIZE, FLAME_SIZE, 0, FLAME_SIZE - flameHeight,
                    x + FLAME_X, y + FLAME_Y + FLAME_SIZE - flameHeight, FLAME_SIZE, flameHeight);
        }

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.getStatus().getLedSprite(MACHINE), x + LED_X, y + LED_Y, LED_SIZE, LED_SIZE);

        if (!menu.getOutputSlot().hasItem()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST_BUCKET,
                    x + GeothermalPlantMenu.OUTPUT_SLOT_X, y + GeothermalPlantMenu.OUTPUT_SLOT_Y, 16, 16);
        }

        // Tabs overlap the panel edge, so they are drawn after the background.
        tabs.render(graphics, font, x, y, mouseX, mouseY);
    }

    // Renders the lava's real still texture tiled in 16px steps, clipped to the fill, then the tick overlay.
    private void drawTank(GuiGraphicsExtractor graphics, int x, int y) {
        int fillHeight = scaledRound(menu.getLava(), menu.getLavaCapacity(), TANK_H);
        int left = x + TANK_X;
        int bottom = y + TANK_Y + TANK_H;
        if (fillHeight > 0) {
            TextureAtlasSprite lava = lavaSprite();
            if (lava != null) {
                graphics.enableScissor(left, bottom - fillHeight, left + TANK_W, bottom);
                for (int tileY = bottom - 16; tileY > bottom - fillHeight - 16; tileY -= 16) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, lava, left, tileY, 16, 16);
                }
                graphics.disableScissor();
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LAVA_FILL, TANK_W, TANK_H, 0, TANK_H - fillHeight,
                        left, bottom - fillHeight, TANK_W, fillHeight);
            }
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TANK_GAUGE, left, y + TANK_Y, TANK_W, TANK_H);
    }

    private static TextureAtlasSprite lavaSprite() {
        try {
            return Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                    .get(Fluids.LAVA.defaultFluidState()).stillMaterial().sprite();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);

        graphics.text(font, Component.translatable("gui.arcforge.heat_label"), SCREEN_LEFT, HEAT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", GeothermalHeat.toCelsius(menu.getHeat())), HEAT_TEXT_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.output"), SCREEN_LEFT, OUTPUT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.fe_per_tick_gain", menu.getFePerTick()), OUTPUT_TEXT_Y, ArcforgeGui.ACCENT);
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    private void textRight(GuiGraphicsExtractor graphics, Component text, int y, int color) {
        graphics.text(font, text, SCREEN_RIGHT - font.width(text), y, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();

        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, ENERGY_W + 2, ENERGY_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", format(menu.getEnergy()), format(menu.getEnergyCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_gain", menu.getFePerTick()).withStyle(ChatFormatting.GREEN));
        } else if (isHovering(TANK_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            lines.add(Fluids.LAVA.getFluidType().getDescription());
            lines.add(Component.translatable("gui.arcforge.mb_stored", format(menu.getLava()), format(menu.getLavaCapacity())).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(HEAT_X, HEAT_Y - 2, HEAT_W, HEAT_H + 4, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.heat", format(menu.getHeat()), format(menu.getMaxHeat())));
            lines.add(Component.translatable("gui.arcforge.lava_sources", menu.getAdjacentLavaSources()).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(FLAME_X, FLAME_Y, FLAME_SIZE, FLAME_SIZE, mouseX, mouseY) && menu.getBurnTime() > 0) {
            lines.add(Component.translatable("gui.arcforge.burn_time", (menu.getBurnTime() + 19) / 20));
        } else {
            tabs.addTooltip(lines, mouseX, mouseY);
        }

        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.getCarried().isEmpty() && tabs.mouseClicked(event)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // Clicks on the side tabs are not "outside" the GUI, so carried items are not thrown.
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop) && !tabs.isInside(mouseX, mouseY);
    }

    // Areas covered by side tabs, for recipe-viewer integrations (JEI/EMI) to avoid.
    public List<Rect2i> getExtraAreas() {
        return tabs.getAreas();
    }

    private static int scaledRound(int value, int max, int size) {
        return max <= 0 ? 0 : (int) Math.min(size, Math.round((double) value * size / max));
    }

    private static String format(int value) {
        return String.format("%,d", value);
    }
}
