package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.machine.config.RedstoneMode;

// Three buttons selecting the machine's redstone mode.
public class RedstoneTab extends SideTab {
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final Identifier BUTTON_PRESSED = ArcforgeGui.widget("button_pressed");
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTONS_X = 8, BUTTONS_Y = 24, BUTTON_PITCH = 24;

    private final Supplier<RedstoneMode> current;
    private final Consumer<RedstoneMode> select;

    public RedstoneTab(Supplier<RedstoneMode> current, Consumer<RedstoneMode> select) {
        super(ArcforgeGui.widget("icon_redstone"), Component.translatable("gui.arcforge.tab.redstone"), 100, 52);
        this.current = current;
        this.select = select;
    }

    private static Identifier icon(RedstoneMode mode) {
        return ArcforgeGui.widget("redstone_" + mode.getSerializedName());
    }

    private static int buttonX(RedstoneMode mode) {
        return BUTTONS_X + mode.ordinal() * BUTTON_PITCH;
    }

    private static RedstoneMode buttonAt(int localX, int localY) {
        for (RedstoneMode mode : RedstoneMode.values()) {
            if (ArcforgeGui.isInside(localX, localY, buttonX(mode), BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                return mode;
            }
        }
        return null;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        RedstoneMode hovered = buttonAt(mouseX - x, mouseY - y);
        for (RedstoneMode mode : RedstoneMode.values()) {
            boolean pressed = mode == current.get();
            Identifier sprite = pressed ? BUTTON_PRESSED : mode == hovered ? BUTTON_HOVER : BUTTON;
            int bx = x + buttonX(mode);
            int by = y + BUTTONS_Y;
            int iconOffset = pressed ? 3 : 2;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, bx, by, BUTTON_SIZE, BUTTON_SIZE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon(mode), bx + iconOffset, by + iconOffset, 16, 16);
        }
    }

    @Override
    protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        RedstoneMode mode = buttonAt(localX, localY);
        if (mode == null || event.button() != 0) {
            return false;
        }
        if (mode != current.get()) {
            select.accept(mode);
            ArcforgeGui.playClickSound();
        }
        return true;
    }

    @Override
    protected void addTooltip(List<Component> lines, int localX, int localY) {
        RedstoneMode mode = buttonAt(localX, localY);
        if (mode != null) {
            lines.add(mode.getDescription());
        }
    }
}
