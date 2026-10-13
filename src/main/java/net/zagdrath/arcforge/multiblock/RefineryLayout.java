/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;

// Where the parts of a formed Distillation Array's refinery tower are (see DistillationArrayRenderer, which draws it,
// and DistillationArrayCasingBlock, whose shape follows it). In the model's frame (ModelFrame): x across, y up, u
// along, in px from the column's minimum corner, the front (the controller's side) toward -u. A column w blocks across
// and h tall, from the bottom up:
//   - the fired reboiler: a plated box over the whole footprint up to REBOILER_TOP, with burner windows that glow while
//     it's hot, and a stack at the back left; its corner at the front left is cut away for the ladder;
//   - the shell: a round column RADIUS_PER_BLOCK x w across, standing on a deck plate, in tray sections SECTION px tall
//     joined by bolted flanges; each section has a sight glass on the front (a flat boss with a window onto the tray)
//     and a manway on the left; its elliptical head ends just under the top deck;
//   - platforms on every other flange (a grating ring out to the footprint's edge, with railings), and a caged ladder
//     up the front left corner from the ground to the top deck;
//   - on the top layer, the overhead deck with an air-cooled condenser bank (a tube bundle on legs, its headers at both
//     ends and a fan or two on top), fed by the vapour line from the head and returning the reflux to the top section;
//   - the draw-off pipes (drawn per product, see sections()) on the right.
public final class RefineryLayout {
    public static final float REBOILER_TOP = 13.0F, BASE_DECK = 15.0F;
    public static final float SECTION = 32.0F;
    public static final float RAIL = 11.0F, DECK = 1.5F;
    // The ladder's corner (front left); the ladder's half width (its stiles are LADDER_HALF either side of its middle,
    // 11 px apart, so it's as wide as a ladder block).
    public static final double LADDER_ANGLE = -0.75 * Math.PI;
    public static final float LADDER_HALF = 5.5F;
    public static final double STACK_ANGLE = 0.75 * Math.PI;

    public final int width, height;
    public final float size, cx, cu, radius, notch;
    public final int sides;
    public final float shellBottom = BASE_DECK, shellTop, headHeight, deckBottom, deckTop;
    // Bottom to top: each tray section's {y0, y1}.
    public final List<float[]> sections = new ArrayList<>();
    // The flanges between sections, and the platforms' deck tops (not the top deck), in y.
    public final List<Float> seams = new ArrayList<>();
    public final List<Float> platforms = new ArrayList<>();
    // The condenser bank: its tube bundle {x0, y0, u0, x1, y1, u1}, headers, legs' footprint and fans {x, u, radius}.
    public final float[] bundle, leftHeader, rightHeader;
    public final List<float[]> fans = new ArrayList<>();
    // The stack: {x, u, radius, top}.
    public final float[] stack;

    public RefineryLayout(int width, int height) {
        this.width = width;
        this.height = height;
        this.size = width * 16.0F;
        this.cx = size / 2.0F;
        this.cu = size / 2.0F;
        this.radius = width == 2 ? 11.5F : 17.5F;
        this.sides = width == 2 ? 16 : 24;
        this.notch = width == 2 ? 11.5F : 15.0F;
        this.deckBottom = (height - 1) * 16.0F + 0.5F;
        this.deckTop = deckBottom + DECK;
        this.headHeight = radius * 0.5F;
        this.shellTop = deckBottom - 1.0F - headHeight;
        float y = shellBottom;
        while (y + SECTION + 20.0F <= shellTop) {
            seams.add(y + SECTION);
            sections.add(new float[] { y, y + SECTION });
            y += SECTION;
        }
        sections.add(new float[] { y, shellTop });
        // A platform on every other flange (every 4 blocks), so each sits at the foot of a section, below its fittings.
        for (int i = 0; i < seams.size(); i += 2) {
            if (seams.get(i) <= deckBottom - 20.0F) {
                platforms.add(seams.get(i));
            }
        }
        float bundleBottom = deckTop + 5.0F;
        this.bundle = new float[] { 4.0F, bundleBottom, cu - 1.0F, size - 4.0F, bundleBottom + 4.0F, size - 2.5F };
        this.leftHeader = new float[] { 1.5F, bundleBottom - 1.0F, cu - 1.0F, 4.0F, bundleBottom + 5.0F, size - 2.5F };
        this.rightHeader = new float[] { size - 4.0F, bundleBottom - 1.0F, cu - 1.0F, size - 1.5F, bundleBottom + 5.0F, size - 2.5F };
        float fanRadius = Math.min(6.5F, (bundle[5] - bundle[2]) / 2.0F - 0.5F);
        float fanU = (bundle[2] + bundle[5]) / 2.0F;
        int fanCount = width == 2 ? 1 : 2;
        for (int i = 0; i < fanCount; i++) {
            fans.add(new float[] { size * (i + 1) / (fanCount + 1), fanU, fanRadius });
        }
        float stackDistance = (float) (Math.hypot(cx - 1.0F, cu - 1.0F) + radius) / 2.0F + 0.5F;
        this.stack = new float[] { cx + (float) Math.cos(STACK_ANGLE) * stackDistance, cu + (float) Math.sin(STACK_ANGLE) * stackDistance, 2.0F,
                Math.min(height * 16.0F - 18.0F, REBOILER_TOP + 44.0F) };
    }

    // The middle of a section, in y.
    public float middle(float[] section) {
        return (section[0] + section[1]) / 2.0F;
    }

    // The height of a section's sight glass and manway: its middle, or as near it as keeps them clear of a platform's
    // deck and railings (a platform's band runs from its deck's underside to its railings' top).
    public float fittingsY(float[] section) {
        float middle = middle(section);
        if (isClear(middle)) {
            return middle;
        }
        float best = middle;
        for (float y = section[0] + 8.0F; y <= section[1] - 8.0F; y += 0.5F) {
            if (isClear(y) && (best == middle || Math.abs(y - middle) < Math.abs(best - middle))) {
                best = y;
            }
        }
        return best;
    }

    private boolean isClear(float y) {
        for (float p : platforms) {
            if (y + 7.0F > p - DECK - 0.5F && y - 7.0F < p + RAIL + 0.5F) {
                return false;
            }
        }
        return true;
    }

    // The sight glass of a section: the boss {x0, y0, u0, x1, y1, u1} on the front of the shell (its outer face at u0,
    // its back inside the shell), whose middle 12 x 12 is the window.
    public float[] sightGlass(float[] section) {
        float y = fittingsY(section);
        return new float[] { cx - 7.0F, y - 7.0F, cu - radius - 4.25F, cx + 7.0F, y + 7.0F, cu - radius + 2.5F };
    }

    // How far from the axis the footprint's edge (inset half a px) is, along angle a (radians, in the x-u plane).
    public float edge(double a) {
        double half = size / 2.0 - 0.5;
        return (float) (half / Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a))));
    }

    // Whether a point is in the ladder's corner (left open in the decks, railings and reboiler).
    public boolean inLadderCorner(float x, float u) {
        return x < notch && u < notch;
    }

    // The ladder: the middle of its rails (x, u), and the direction out from the shell.
    public float[] ladder() {
        float d = radius + 3.0F;
        return new float[] { cx + (float) Math.cos(LADDER_ANGLE) * d, cu + (float) Math.sin(LADDER_ANGLE) * d };
    }

    // Where a draw-off leaves the shell: a product's angle (round the right side), given its place among n.
    public static double drawOffAngle(int index, int count) {
        return count <= 1 ? 0.0 : Math.toRadians(-27.0 + 54.0 * index / (count - 1));
    }

    // The section a product draws off at, lightest at the top, given its place among n (0 = lightest).
    public int drawOffSection(int index, int count) {
        int n = sections.size();
        return Math.max(0, n - 1 - (count <= 1 ? 0 : index * (n - 1) / Math.max(1, count - 1)));
    }

    // The solid parts, as boxes {x0, y0, u0, x1, y1, u1}: what the blocks' shapes are made of. Thin parts (pipes,
    // ladder, fan blades) are left out; railings are kept so players don't walk off a platform.
    public List<float[]> solids() {
        List<float[]> boxes = new ArrayList<>();
        // Reboiler and base deck, round the ladder's corner.
        boxes.add(new float[] { 1.0F, 0.0F, notch, size - 1.0F, REBOILER_TOP, size - 1.0F });
        boxes.add(new float[] { notch, 0.0F, 1.0F, size - 1.0F, REBOILER_TOP, notch });
        boxes.add(new float[] { 0.5F, REBOILER_TOP, notch, size - 0.5F, BASE_DECK, size - 0.5F });
        boxes.add(new float[] { notch, REBOILER_TOP, 0.5F, size - 0.5F, BASE_DECK, notch });
        // The shell, in strips 2 px wide, up into the head.
        float top = shellTop + headHeight * 0.7F;
        for (float x = cx - radius; x < cx + radius - 0.01F; x += 2.0F) {
            float x1 = Math.min(cx + radius, x + 2.0F);
            float nearest = x <= cx && x1 >= cx ? 0.0F : Math.min(Math.abs(x - cx), Math.abs(x1 - cx));
            float half = (float) Math.sqrt(Math.max(0.0, radius * radius - nearest * nearest));
            boxes.add(new float[] { x, shellBottom, cu - half, x1, top, cu + half });
        }
        for (float[] section : sections) {
            boxes.add(sightGlass(section));
            float y = fittingsY(section);
            boxes.add(new float[] { cx - radius - 4.2F, y - 5.2F, cu - 5.2F, cx - radius + 1.0F, y + 5.2F, cu + 5.2F });
        }
        // Platforms and the top deck, with their railings.
        for (float p : platforms) {
            deck(boxes, p - DECK, p);
        }
        deck(boxes, deckBottom, deckTop);
        // The condenser bank, and its fans as boxes.
        boxes.add(bundle);
        boxes.add(leftHeader);
        boxes.add(rightHeader);
        for (float[] fan : fans) {
            boxes.add(new float[] { fan[0] - fan[2], bundle[4], fan[1] - fan[2], fan[0] + fan[2], bundle[4] + 2.5F, fan[1] + fan[2] });
        }
        boxes.add(new float[] { stack[0] - stack[2], REBOILER_TOP, stack[1] - stack[2], stack[0] + stack[2], stack[3], stack[1] + stack[2] });
        return boxes;
    }

    // A deck plate over the footprint (but the ladder's corner) from y0 to y1, and the railings round its edge.
    private void deck(List<float[]> boxes, float y0, float y1) {
        boxes.add(new float[] { 0.5F, y0, notch, size - 0.5F, y1, size - 0.5F });
        boxes.add(new float[] { notch, y0, 0.5F, size - 0.5F, y1, notch });
        float r = y1 + RAIL;
        boxes.add(new float[] { notch, y1, 0.5F, size - 0.5F, r, 1.5F });
        boxes.add(new float[] { 0.5F, y1, size - 1.5F, size - 0.5F, r, size - 0.5F });
        boxes.add(new float[] { 0.5F, y1, notch, 1.5F, r, size - 0.5F });
        boxes.add(new float[] { size - 1.5F, y1, 0.5F, size - 0.5F, r, size - 0.5F });
    }
}
