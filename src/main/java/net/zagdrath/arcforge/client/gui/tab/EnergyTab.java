package net.zagdrath.arcforge.client.gui.tab;

import java.util.function.IntSupplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;

// Shows stored FE and current output.
public class EnergyTab extends SideTab {
    private final IntSupplier stored;
    private final IntSupplier perTick;

    public EnergyTab(IntSupplier stored, IntSupplier perTick) {
        super(ArcforgeGui.widget("icon_energy"), Component.translatable("gui.arcforge.tab.energy"), 100, 66);
        this.stored = stored;
        this.perTick = perTick;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("gui.arcforge.stored"), x + 6, y + 26, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, Component.translatable("gui.arcforge.fe_amount", String.format("%,d", stored.getAsInt())), x + 6, y + 36, ArcforgeGui.WHITE, false);
        graphics.text(font, Component.translatable("gui.arcforge.output"), x + 6, y + 46, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, Component.translatable("gui.arcforge.fe_per_tick_gain", perTick.getAsInt()), x + 6, y + 56, ArcforgeGui.TOOLTIP_GREEN, false);
    }
}
