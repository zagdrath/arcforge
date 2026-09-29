/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;

// Shows stored heat and the rate it's made or used at, plus extra rows (e.g. the Geothermal Plant's nearby lava
// and magma, or a burner's oxygen). A row is a label over a value, or one line; rows can hide themselves. The panel
// is as wide as its widest line (at least 100) and as tall as the rows it shows.
public class HeatTab extends SideTab {
    private static final int MIN_WIDTH = 100, PADDING = 12, BASE_HEIGHT = 74;
    private static final int PAIR_HEIGHT = 22, LINE_HEIGHT = 12;

    // An extra row: a label and a value (or just the value, on one line when the label is null), shown while `shown`.
    public record Row(@Nullable Component label, Supplier<Component> value, BooleanSupplier shown) {
        public static Row always(Component label, Supplier<Component> value) {
            return new Row(label, value, () -> true);
        }

        int height() {
            return label != null ? PAIR_HEIGHT : LINE_HEIGHT;
        }
    }

    private final IntSupplier stored;
    private final Component rateLabel;
    private final Supplier<Component> rate;
    private final List<Row> rows;

    public HeatTab(IntSupplier stored, Component rateLabel, Supplier<Component> rate) {
        this(stored, rateLabel, rate, List.of());
    }

    public HeatTab(IntSupplier stored, Component rateLabel, Supplier<Component> rate,
            @Nullable Component extraLabel, @Nullable Supplier<Component> extra) {
        this(stored, rateLabel, rate, extraLabel != null && extra != null ? List.of(Row.always(extraLabel, extra)) : List.of());
    }

    public HeatTab(IntSupplier stored, Component rateLabel, Supplier<Component> rate, List<Row> rows) {
        super(ArcforgeGui.widget("icon_heat"), Component.translatable("gui.arcforge.tab.heat"), MIN_WIDTH, BASE_HEIGHT, Side.LEFT);
        this.stored = stored;
        this.rateLabel = rateLabel;
        this.rate = rate;
        this.rows = rows;
    }

    private List<Row> shownRows() {
        List<Row> shown = new ArrayList<>();
        for (Row row : rows) {
            if (row.shown().getAsBoolean()) {
                shown.add(row);
            }
        }
        return shown;
    }

    private Component storedText() {
        return Component.translatable("gui.arcforge.hu_amount", ArcforgeGui.grouped(stored.getAsInt()));
    }

    @Override
    protected int expandedWidth() {
        Font font = Minecraft.getInstance().font;
        int widest = Math.max(font.width(Component.translatable("gui.arcforge.stored")), font.width(storedText()));
        widest = Math.max(widest, Math.max(font.width(rateLabel), font.width(rate.get())));
        for (Row row : shownRows()) {
            widest = Math.max(widest, font.width(row.value().get()));
            if (row.label() != null) {
                widest = Math.max(widest, font.width(row.label()));
            }
        }
        return Math.max(MIN_WIDTH, widest + PADDING);
    }

    @Override
    protected int expandedHeight() {
        int height = BASE_HEIGHT;
        for (Row row : shownRows()) {
            height += row.height();
        }
        return height;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("gui.arcforge.stored"), x + 6, y + 26, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, storedText(), x + 6, y + 36, ArcforgeGui.WHITE, false);
        graphics.text(font, rateLabel, x + 6, y + 48, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, rate.get(), x + 6, y + 58, ArcforgeGui.HEAT, false);
        int rowY = y + 70;
        for (Row row : shownRows()) {
            if (row.label() != null) {
                graphics.text(font, row.label(), x + 6, rowY, ArcforgeGui.TOOLTIP_GRAY, false);
                graphics.text(font, row.value().get(), x + 6, rowY + 10, ArcforgeGui.TEXT, false);
            } else {
                graphics.text(font, row.value().get(), x + 6, rowY, ArcforgeGui.TEXT, false);
            }
            rowY += row.height();
        }
    }
}
