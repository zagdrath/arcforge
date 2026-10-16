/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Heavy Water Tower's model (see HeavyWaterTowerRenderer), on the refinery tower's sheet: an exchange tower of
// trays in the refinery tower's manner, but squat, filling most of its footprint on the plinth. From the bottom: a
// skirt, the bottom head, the shell in plated tray sections (one a block tall, a flange at each tray seam), the top
// head and the gas outlet on its crown, and the hydrogen sulfide loop: a pipe down the tower's side, in at the top and
// bottom sections. The port nozzles run from the walls to the shell. Sized to the structure.
public final class HeavyWaterTowerMesh extends VesselMesh {
    static final float OUTLET_R = 3.0F, LOOP_R = 2.0F;

    final float radius, headY, headHeight, crown;
    final int panelX, panelY;

    public HeavyWaterTowerMesh(ModelFrame frame, int panelX, int panelY) {
        super(frame);
        this.panelX = panelX;
        this.panelY = panelY;
        this.radius = Math.min(width, length) / 2.0F - 5.5F;
        this.headHeight = Math.min(radius * 0.4F, 10.0F);
        this.crown = height - 4.0F;
        this.headY = crown - headHeight;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        plinth();
        float r = radius, skirt = PLINTH + 4.0F, bottom = PX;
        shell(new float[][] { { PLINTH, r - 3.0F }, { skirt, r - 3.0F } }, SHELL_SIDES);
        ring(skirt, r - 3.0F, 0.0F, SHELL_SIDES, false);
        shell(new float[][] { { skirt, r - 3.0F }, { skirt + (bottom - skirt) * 0.6F, r - 0.75F }, { bottom, r } }, SHELL_SIDES);
        // The tray sections, a block tall, the last up to the head; a flange at each seam.
        float y = bottom;
        while (y < headY - 0.5F) {
            float next = headY - (y + PX) < 6.0F ? headY : y + PX;
            shell(new float[][] { { y, r }, { next, r } }, SHELL_SIDES);
            if (next < headY) {
                flange(next - 0.75F, next + 0.75F, r + 1.25F, r, SHELL_SIDES);
            }
            y = next;
        }
        flange(headY - 1.5F, headY, r + 1.25F, r, SHELL_SIDES);
        uprightTube(cx, cu, head(headY, r, headHeight, true), SHELL_SIDES, Math.PI / SHELL_SIDES, Paint.CASING, Paint.PLAIN);
        uprights.add(new float[] { cx, cu, r * 0.75F, headY, crown });
        // The gas outlet on the crown.
        float top = height - 1.0F;
        uprightTube(cx, cu, new float[][] { { crown - 1.0F, OUTLET_R }, { top - 1.5F, OUTLET_R } }, 8, Math.PI / 8, Paint.BANDED, null);
        uprightTube(cx, cu, new float[][] { { top - 1.5F, OUTLET_R + 1.5F }, { top, OUTLET_R + 1.5F } }, 8, Math.PI / 8, Paint.BANDED, Paint.PLAIN);
        uprights.add(new float[] { cx, cu, OUTLET_R + 1.5F, crown, top });
        loop(r);
        panel(panelX, panelY);
        return finish(ports);
    }

    // The hydrogen sulfide loop up the tower's back left (seen from the front): a pipe standing off the shell, in at
    // the lowest and highest tray sections by short legs (the cold and hot ends of the exchange).
    private void loop(float r) {
        double a = Math.PI / 4.0;
        float off = r + 3.5F;
        float x = cx + (float) Math.cos(a) * off, u = cu + (float) Math.sin(a) * off;
        float y0 = PX + 6.0F, y1 = headY - 6.0F;
        if (y1 - y0 < 8.0F) {
            return;
        }
        prismY(x, u, y0 - LOOP_R, y1 + LOOP_R, LOOP_R, Paint.BANDED, Paint.BANDED);
        for (float y : new float[] { y0, y1 }) {
            float[] from = { x, y, u };
            float[] into = { cx + (float) Math.cos(a) * (r - 1.0F), y, cu + (float) Math.sin(a) * (r - 1.0F) };
            // Thinner than the riser: at its radius a leg's side would lie in one of the riser's faces.
            tube(from, into, LOOP_R - 0.5F, 8, Paint.BANDED);
        }
    }
}
