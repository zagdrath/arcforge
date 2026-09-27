/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.handbook;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.StructureRenderer;

// The Engineer's Handbook: a two-page book. The first spread lists the chapters; a chapter lists its
// entries; an entry's pages flow across spreads. An entry shows the items it covers at the top (click one
// to see its recipes in JEI), text, and step-by-step multiblock builds you can turn and step through.
public class EngineersHandbookScreen extends Screen {
    private static final int BOOK_W = 300, BOOK_H = 196;
    private static final int LEFT_PAGE_X = 16, RIGHT_PAGE_X = 158, PAGE_Y = 14, PAGE_W = 126, PAGE_H = 150;
    private static final int FOOTER_Y = BOOK_H - 22;
    private static final int ROW_H = 18, LINE_H = 10, HEADING_H = 12, PARAGRAPH_GAP = 4;
    private static final int ENTRY_ROWS = PAGE_H / ROW_H;

    private static final int COVER = 0xFF2B3138, COVER_EDGE = 0xFF15181C, TEAL = 0xFF5FD4C4;
    private static final int PAGE = 0xFFEDE4CC, PAGE_SHADE = 0xFFD9CCAA, CREASE = 0xFFB9A983;
    private static final int TEXT = 0xFF3A3024, HEADING = 0xFF1F5E57, MUTED = 0xFF7A6A52, HOVER = 0x33000000;

    private static final Identifier PAGE_FORWARD = Identifier.withDefaultNamespace("widget/page_forward");
    private static final Identifier PAGE_FORWARD_HOVER = Identifier.withDefaultNamespace("widget/page_forward_highlighted");
    private static final Identifier PAGE_BACKWARD = Identifier.withDefaultNamespace("widget/page_backward");
    private static final Identifier PAGE_BACKWARD_HOVER = Identifier.withDefaultNamespace("widget/page_backward_highlighted");

    // Multiblock page controls, relative to the page.
    private static final int VIEW_Y = 12, VIEW_H = 96, CONTROLS_Y = 110, CONTROL_H = 12, ARROW_W = 12, TURN_W = 40;
    private static final double DRAG_PER_TURN = 30.0;

    private enum View { HOME, CHAPTER, ENTRY }

    private record Place(View view, int chapter, int entry, int spread) {}

    // A laid-out page: text lines, or one multiblock build.
    private record Line(FormattedCharSequence text, int x, int y, int color) {}

    private static final class LaidPage {
        final List<Line> lines = new ArrayList<>();
        EngineersHandbookData.@Nullable MultiblockPage multiblock;
        int used;
    }

    // How a multiblock page is being viewed.
    private static final class BuildView {
        int rotation;
        int layer = Integer.MAX_VALUE;
        double drag;
    }

    private EngineersHandbookData.Book book = new EngineersHandbookData.Book(List.of());
    private View view = View.HOME;
    private int chapter;
    private int entry;
    private int spread;
    private final Deque<Place> history = new ArrayDeque<>();
    private List<LaidPage> laidPages = List.of();
    private final Map<EngineersHandbookData.MultiblockPage, BuildView> buildViews = new HashMap<>();
    private int left;
    private int top;

    public EngineersHandbookScreen() {
        super(Component.translatable("item.arcforge.engineers_handbook"));
    }

    public static void open() {
        Minecraft.getInstance().gui.setScreen(new EngineersHandbookScreen());
    }

    @Override
    protected void init() {
        super.init();
        left = (width - BOOK_W) / 2;
        top = (height - BOOK_H) / 2;
        book = EngineersHandbookData.load(Minecraft.getInstance().getResourceManager());
        if (view == View.ENTRY) {
            layOut();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Navigation ---

    private void go(View to, int toChapter, int toEntry) {
        history.push(new Place(view, chapter, entry, spread));
        view = to;
        chapter = toChapter;
        entry = toEntry;
        spread = 0;
        if (view == View.ENTRY) {
            layOut();
        }
        ArcforgeGui.playClickSound();
    }

    private void back() {
        if (history.isEmpty()) {
            onClose();
            return;
        }
        Place place = history.pop();
        view = place.view();
        chapter = place.chapter();
        entry = place.entry();
        spread = place.spread();
        if (view == View.ENTRY) {
            layOut();
        }
        ArcforgeGui.playClickSound();
    }

    private EngineersHandbookData.@Nullable Chapter currentChapter() {
        return chapter >= 0 && chapter < book.chapters().size() ? book.chapters().get(chapter) : null;
    }

    private EngineersHandbookData.@Nullable Entry currentEntry() {
        EngineersHandbookData.Chapter current = currentChapter();
        return current != null && entry >= 0 && entry < current.entries().size() ? current.entries().get(entry) : null;
    }

    // How many spreads the current view has.
    private int spreads() {
        return switch (view) {
            case HOME -> 1;
            case CHAPTER -> {
                EngineersHandbookData.Chapter current = currentChapter();
                yield current == null ? 1 : Math.max(1, (current.entries().size() + ENTRY_ROWS - 1) / ENTRY_ROWS);
            }
            case ENTRY -> Math.max(1, (laidPages.size() + 1) / 2);
        };
    }

    // --- Laying out an entry's pages ---

    private void layOut() {
        laidPages = new ArrayList<>();
        EngineersHandbookData.Entry current = currentEntry();
        if (current == null) {
            return;
        }
        LaidPage page = new LaidPage();
        // The first page starts under the entry's header and its row of items.
        page.used = HEADING_H + 8 + (current.items().isEmpty() ? 0 : ((current.items().size() + 6) / 7) * ROW_H + 4);
        laidPages.add(page);
        for (EngineersHandbookData.Page source : current.pages()) {
            if (source instanceof EngineersHandbookData.MultiblockPage multiblock) {
                if (!page.lines.isEmpty() || page.multiblock != null || laidPages.size() == 1) {
                    page = new LaidPage();
                    laidPages.add(page);
                }
                page.multiblock = multiblock;
                page.used = PAGE_H;
                continue;
            }
            for (String paragraph : ((EngineersHandbookData.TextPage) source).text().split("\n")) {
                boolean heading = paragraph.startsWith("# ");
                boolean bullet = paragraph.startsWith("- ");
                String body = heading || bullet ? paragraph.substring(2) : paragraph;
                int indent = bullet ? 8 : 0;
                Component text = heading
                        ? Component.literal(body).withStyle(ChatFormatting.BOLD)
                        : Component.literal(body);
                List<FormattedCharSequence> wrapped = font.split(text, PAGE_W - indent);
                int height = wrapped.size() * LINE_H + (heading ? 2 : 0);
                if (page.used + height > PAGE_H) {
                    page = new LaidPage();
                    laidPages.add(page);
                }
                if (heading && page.used > 0) {
                    page.used += 2;
                }
                for (int i = 0; i < wrapped.size(); i++) {
                    if (page.used + LINE_H > PAGE_H) {
                        page = new LaidPage();
                        laidPages.add(page);
                    }
                    if (bullet && i == 0) {
                        page.lines.add(new Line(Component.literal("•").getVisualOrderText(), 1, page.used, TEXT));
                    }
                    page.lines.add(new Line(wrapped.get(i), indent, page.used, heading ? HEADING : TEXT));
                    page.used += LINE_H;
                }
                page.used += bullet ? 1 : PARAGRAPH_GAP;
            }
        }
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.fill(left - 4, top - 4, left + BOOK_W + 4, top + BOOK_H + 4, COVER_EDGE);
        graphics.fill(left - 3, top - 3, left + BOOK_W + 3, top + BOOK_H + 3, COVER);
        graphics.fill(left - 3, top + BOOK_H - 1, left + BOOK_W + 3, top + BOOK_H + 1, TEAL);
        graphics.fill(left + 4, top + 4, left + BOOK_W / 2 - 1, top + BOOK_H - 4, PAGE);
        graphics.fill(left + BOOK_W / 2 + 1, top + 4, left + BOOK_W - 4, top + BOOK_H - 4, PAGE);
        graphics.fill(left + BOOK_W / 2 - 4, top + 4, left + BOOK_W / 2 - 1, top + BOOK_H - 4, PAGE_SHADE);
        graphics.fill(left + BOOK_W / 2 + 1, top + 4, left + BOOK_W / 2 + 4, top + BOOK_H - 4, PAGE_SHADE);
        graphics.fill(left + BOOK_W / 2 - 1, top + 4, left + BOOK_W / 2 + 1, top + BOOK_H - 4, CREASE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        switch (view) {
            case HOME -> drawHome(graphics, mouseX, mouseY);
            case CHAPTER -> drawChapter(graphics, mouseX, mouseY);
            case ENTRY -> drawEntry(graphics, mouseX, mouseY);
        }
        drawFooter(graphics, mouseX, mouseY);
    }

    private void drawHome(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + LEFT_PAGE_X, y = top + PAGE_Y;
        centered(graphics, title.copy().withStyle(ChatFormatting.BOLD), x, y + 4, HEADING);
        centered(graphics, Component.translatable("handbook.arcforge.subtitle"), x, y + 16, MUTED);
        int lineY = y + 34;
        for (FormattedCharSequence line : font.split(Component.translatable("handbook.arcforge.intro"), PAGE_W)) {
            graphics.text(font, line, x, lineY, TEXT, false);
            lineY += LINE_H;
        }
        int rx = left + RIGHT_PAGE_X;
        graphics.text(font, Component.translatable("handbook.arcforge.chapters").withStyle(ChatFormatting.BOLD), rx, y, HEADING, false);
        for (int i = 0; i < book.chapters().size(); i++) {
            EngineersHandbookData.Chapter current = book.chapters().get(i);
            row(graphics, rx, y + HEADING_H + 2 + i * ROW_H, current.icon(), current.title(), mouseX, mouseY);
        }
    }

    private void drawChapter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        EngineersHandbookData.Chapter current = currentChapter();
        if (current == null) {
            return;
        }
        int x = left + LEFT_PAGE_X, y = top + PAGE_Y;
        graphics.item(current.icon(), x, y);
        graphics.text(font, current.title().copy().withStyle(ChatFormatting.BOLD), x + 20, y + 4, HEADING, false);
        int lineY = y + 24;
        for (FormattedCharSequence line : font.split(current.description(), PAGE_W)) {
            graphics.text(font, line, x, lineY, TEXT, false);
            lineY += LINE_H;
        }
        int rx = left + RIGHT_PAGE_X;
        int first = spread * ENTRY_ROWS;
        for (int i = first; i < Math.min(current.entries().size(), first + ENTRY_ROWS); i++) {
            EngineersHandbookData.Entry item = current.entries().get(i);
            row(graphics, rx, y + (i - first) * ROW_H, item.icon(), item.title(), mouseX, mouseY);
        }
    }

    private void drawEntry(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        EngineersHandbookData.Entry current = currentEntry();
        if (current == null) {
            return;
        }
        for (int side = 0; side < 2; side++) {
            int index = spread * 2 + side;
            if (index >= laidPages.size()) {
                break;
            }
            int x = left + (side == 0 ? LEFT_PAGE_X : RIGHT_PAGE_X), y = top + PAGE_Y;
            if (index == 0) {
                graphics.item(current.icon(), x, y - 2);
                graphics.text(font, current.title().copy().withStyle(ChatFormatting.BOLD), x + 20, y + 2, HEADING, false);
                for (int i = 0; i < current.items().size(); i++) {
                    int ix = x + (i % 7) * 18, iy = y + HEADING_H + 8 + (i / 7) * ROW_H;
                    graphics.fill(ix - 1, iy - 1, ix + 17, iy + 17, PAGE_SHADE);
                    graphics.item(current.items().get(i), ix, iy);
                    if (inside(mouseX, mouseY, ix - 1, iy - 1, 18, 18)) {
                        graphics.fill(ix - 1, iy - 1, ix + 17, iy + 17, HOVER);
                        List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), current.items().get(i)));
                        tooltip.add(Component.translatable(RecipeLinks.available() ? "handbook.arcforge.click_recipes" : "handbook.arcforge.install_jei")
                                .withStyle(ChatFormatting.DARK_AQUA));
                        graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                    }
                }
            }
            LaidPage page = laidPages.get(index);
            for (Line line : page.lines) {
                graphics.text(font, line.text(), x + line.x(), y + line.y(), line.color(), false);
            }
            if (page.multiblock != null) {
                drawBuild(graphics, page.multiblock, x, y, mouseX, mouseY);
            }
        }
    }

    private void drawBuild(GuiGraphicsExtractor graphics, EngineersHandbookData.MultiblockPage page, int x, int y, int mouseX, int mouseY) {
        BuildView build = buildViews.computeIfAbsent(page, key -> new BuildView());
        int layers = page.blueprint().layers();
        build.layer = Math.clamp(build.layer, 0, layers - 1);
        boolean all = build.layer == layers - 1;
        graphics.text(font, page.blueprint().name().copy().withStyle(ChatFormatting.BOLD), x, y, HEADING, false);
        StructureRenderer.draw(graphics, page.blueprint(), x, y + VIEW_Y, PAGE_W, VIEW_H, build.rotation, build.layer, !all);
        button(graphics, x, y + CONTROLS_Y, ARROW_W, Component.literal("<"), mouseX, mouseY);
        button(graphics, x + 70, y + CONTROLS_Y, ARROW_W, Component.literal(">"), mouseX, mouseY);
        button(graphics, x + PAGE_W - TURN_W, y + CONTROLS_Y, TURN_W, Component.translatable("jei.arcforge.multiblock.turn"), mouseX, mouseY);
        Component label = all ? Component.translatable("jei.arcforge.multiblock.all_layers", layers)
                : Component.translatable("jei.arcforge.multiblock.layer", build.layer + 1, layers);
        graphics.text(font, label, x + ARROW_W + (70 - ARROW_W - font.width(label)) / 2, y + CONTROLS_Y + 2, TEXT, false);
        int lineY = y + CONTROLS_Y + CONTROL_H + 4;
        for (FormattedCharSequence line : font.split(page.blueprint().rules(), PAGE_W)) {
            if (lineY + LINE_H > y + PAGE_H) {
                break;
            }
            graphics.text(font, line, x, lineY, MUTED, false);
            lineY += LINE_H;
        }
        if (inside(mouseX, mouseY, x, y + VIEW_Y, PAGE_W, VIEW_H)) {
            graphics.setTooltipForNextFrame(Component.translatable("jei.arcforge.multiblock.controls"), mouseX, mouseY);
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = top + FOOTER_Y;
        if (spread > 0) {
            boolean hovered = inside(mouseX, mouseY, left + 16, y, 23, 13);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? PAGE_BACKWARD_HOVER : PAGE_BACKWARD, left + 16, y, 23, 13);
        }
        if (spread < spreads() - 1) {
            boolean hovered = inside(mouseX, mouseY, left + BOOK_W - 39, y, 23, 13);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? PAGE_FORWARD_HOVER : PAGE_FORWARD, left + BOOK_W - 39, y, 23, 13);
        }
        if (view != View.HOME) {
            Component back = Component.translatable("handbook.arcforge.back");
            int bx = left + BOOK_W / 2 - 8 - font.width(back);
            boolean hovered = inside(mouseX, mouseY, bx - 2, y, font.width(back) + 4, 12);
            graphics.text(font, back, bx, y + 2, hovered ? HEADING : MUTED, false);
            Component home = Component.translatable("handbook.arcforge.home");
            int hx = left + BOOK_W / 2 + 8;
            hovered = inside(mouseX, mouseY, hx - 2, y, font.width(home) + 4, 12);
            graphics.text(font, home, hx, y + 2, hovered ? HEADING : MUTED, false);
        }
        if (spreads() > 1) {
            Component pages = Component.literal((spread + 1) + " / " + spreads());
            graphics.text(font, pages, left + BOOK_W - 44 - font.width(pages), y + 3, MUTED, false);
        }
    }

    private void row(GuiGraphicsExtractor graphics, int x, int y, ItemStack icon, Component label, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, x - 2, y - 1, PAGE_W + 4, ROW_H)) {
            graphics.fill(x - 2, y - 1, x + PAGE_W + 2, y + ROW_H - 1, HOVER);
        }
        graphics.item(icon, x, y);
        graphics.text(font, font.substrByWidth(label, PAGE_W - 22).getString(), x + 20, y + 4, TEXT, false);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, CONTROL_H);
        graphics.fill(x, y, x + width, y + CONTROL_H, COVER_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + CONTROL_H - 1, hovered ? HEADING : COVER);
        graphics.text(font, label, x + (width - font.width(label)) / 2, y + 2, 0xFFFFFFFF, false);
    }

    private void centered(GuiGraphicsExtractor graphics, Component text, int pageX, int y, int color) {
        graphics.text(font, text, pageX + (PAGE_W - font.width(text)) / 2, y, color, false);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            back();
            return true;
        }
        int y = top + FOOTER_Y;
        if (spread > 0 && inside(mouseX, mouseY, left + 16, y, 23, 13)) {
            turn(-1);
            return true;
        }
        if (spread < spreads() - 1 && inside(mouseX, mouseY, left + BOOK_W - 39, y, 23, 13)) {
            turn(1);
            return true;
        }
        if (view != View.HOME) {
            Component back = Component.translatable("handbook.arcforge.back");
            int bx = left + BOOK_W / 2 - 8 - font.width(back);
            if (inside(mouseX, mouseY, bx - 2, y, font.width(back) + 4, 12)) {
                back();
                return true;
            }
            int hx = left + BOOK_W / 2 + 8;
            if (inside(mouseX, mouseY, hx - 2, y, font.width(Component.translatable("handbook.arcforge.home")) + 4, 12)) {
                history.clear();
                view = View.HOME;
                spread = 0;
                ArcforgeGui.playClickSound();
                return true;
            }
        }
        int pageTop = top + PAGE_Y;
        switch (view) {
            case HOME -> {
                int rx = left + RIGHT_PAGE_X;
                for (int i = 0; i < book.chapters().size(); i++) {
                    if (inside(mouseX, mouseY, rx - 2, pageTop + HEADING_H + 1 + i * ROW_H, PAGE_W + 4, ROW_H)) {
                        go(View.CHAPTER, i, 0);
                        return true;
                    }
                }
            }
            case CHAPTER -> {
                EngineersHandbookData.Chapter current = currentChapter();
                int first = spread * ENTRY_ROWS;
                for (int i = first; current != null && i < Math.min(current.entries().size(), first + ENTRY_ROWS); i++) {
                    if (inside(mouseX, mouseY, left + RIGHT_PAGE_X - 2, pageTop + (i - first) * ROW_H - 1, PAGE_W + 4, ROW_H)) {
                        go(View.ENTRY, chapter, i);
                        return true;
                    }
                }
            }
            case ENTRY -> {
                if (clickEntry(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickEntry(double mouseX, double mouseY) {
        EngineersHandbookData.Entry current = currentEntry();
        if (current == null) {
            return false;
        }
        for (int side = 0; side < 2; side++) {
            int index = spread * 2 + side;
            if (index >= laidPages.size()) {
                break;
            }
            int x = left + (side == 0 ? LEFT_PAGE_X : RIGHT_PAGE_X), y = top + PAGE_Y;
            if (index == 0) {
                for (int i = 0; i < current.items().size(); i++) {
                    int ix = x + (i % 7) * 18, iy = y + HEADING_H + 8 + (i / 7) * ROW_H;
                    if (inside(mouseX, mouseY, ix - 1, iy - 1, 18, 18) && RecipeLinks.available()) {
                        RecipeLinks.show(current.items().get(i));
                        return true;
                    }
                }
            }
            EngineersHandbookData.MultiblockPage multiblock = laidPages.get(index).multiblock;
            if (multiblock != null) {
                BuildView build = buildViews.computeIfAbsent(multiblock, key -> new BuildView());
                int layers = multiblock.blueprint().layers();
                if (inside(mouseX, mouseY, x, y + CONTROLS_Y, ARROW_W, CONTROL_H)) {
                    build.layer = Math.clamp(build.layer - 1, 0, layers - 1);
                } else if (inside(mouseX, mouseY, x + 70, y + CONTROLS_Y, ARROW_W, CONTROL_H)) {
                    build.layer = Math.clamp(build.layer + 1, 0, layers - 1);
                } else if (inside(mouseX, mouseY, x + PAGE_W - TURN_W, y + CONTROLS_Y, TURN_W, CONTROL_H)) {
                    build.rotation++;
                } else {
                    continue;
                }
                ArcforgeGui.playClickSound();
                return true;
            }
        }
        return false;
    }

    private @Nullable BuildView buildAt(double mouseX, double mouseY) {
        if (view != View.ENTRY) {
            return null;
        }
        for (int side = 0; side < 2; side++) {
            int index = spread * 2 + side;
            int x = left + (side == 0 ? LEFT_PAGE_X : RIGHT_PAGE_X), y = top + PAGE_Y;
            if (index < laidPages.size() && laidPages.get(index).multiblock != null && inside(mouseX, mouseY, x, y + VIEW_Y, PAGE_W, VIEW_H)) {
                return buildViews.computeIfAbsent(laidPages.get(index).multiblock, key -> new BuildView());
            }
        }
        return null;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        BuildView build = buildAt(event.x(), event.y());
        if (build == null) {
            return super.mouseDragged(event, dx, dy);
        }
        build.drag += dx;
        while (Math.abs(build.drag) >= DRAG_PER_TURN) {
            build.rotation += build.drag > 0 ? 1 : -1;
            build.drag -= Math.signum(build.drag) * DRAG_PER_TURN;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        BuildView build = buildAt(mouseX, mouseY);
        if (build != null && scrollY != 0) {
            build.layer = Math.max(0, build.layer + (scrollY > 0 ? 1 : -1));
            return true;
        }
        if (scrollY != 0) {
            turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_BACKSPACE -> {
                back();
                return true;
            }
            case InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP -> {
                turn(-1);
                return true;
            }
            case InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEDOWN -> {
                turn(1);
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    private void turn(int by) {
        int next = Math.clamp(spread + by, 0, spreads() - 1);
        if (next != spread) {
            spread = next;
            ArcforgeGui.playClickSound();
        }
    }
}
