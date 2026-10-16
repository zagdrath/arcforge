/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Fission Reactor's model (see FissionReactorRenderer), on the refinery tower's sheet: a heavy cylindrical pressure
// vessel standing in the casing frame on its plinth. From the bottom: a skirt, the bottom head, the plated shell, the
// bolted flange where the closure head meets it, the domed closure head, and the control-rod drive housings standing
// in a grid on the head, tied by a support plate below their latch housings. Each piece of Reactor Glass in the side
// walls is a viewport in the shell facing it: a bolted boss with the shut-down core dark behind it, and while the
// reactor runs the Cherenkov glow over it, full-bright (in its own list, drawn only then). The port nozzles run from
// the walls to the shell. Sized to the structure: the vessel as wide as the footprint's narrower side allows and as
// tall as its height.
public final class FissionReactorMesh extends VesselMesh {
    // The drive housings' pitch on the head and their radius (px), and how far out the grid reaches (of the vessel's).
    static final float CRDM_PITCH = 6.0F, CRDM_R = 1.25F, CRDM_LATCH_R = 2.0F, CRDM_REACH = 0.62F;
    // A viewport: its boss across and up, and its window.
    static final float VIEWPORT = 12.0F, WINDOW = 9.0F;

    // A viewport's glass block in the side walls: its block {x, y, u} in the frame and the wall's outward normal.
    public record Viewport(int x, int y, int u) {}

    final float radius, skirtTop, bottomHeadTop, flangeY, headY, headHeight, crdmTop, crown;
    final List<Viewport> viewports;
    final int panelX, panelY;
    // The windows' outer faces, for the glow while running: {angle, y}.
    private final List<float[]> windows = new ArrayList<>();

    public FissionReactorMesh(ModelFrame frame, List<Viewport> viewports, int panelX, int panelY) {
        super(frame);
        this.viewports = viewports;
        this.panelX = panelX;
        this.panelY = panelY;
        this.radius = Math.min(width, length) / 2.0F - 6.0F;
        this.skirtTop = PLINTH + 4.0F;
        this.bottomHeadTop = skirtTop + Math.max(4.0F, radius * 0.22F);
        this.headHeight = radius * 0.45F;
        this.crdmTop = height - 3.0F;
        float crdm = Math.max(8.0F, Math.min(28.0F, height * 0.18F));
        this.headY = crdmTop - crdm - headHeight;
        this.flangeY = headY - 2.0F;
        this.crown = headY + headHeight;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow (the panel's screen), Cherenkov (the
    // viewports' glow, drawn only while the reactor runs)}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        windows.clear();
        plinth();
        casingFrame();
        vessel();
        drives();
        for (Viewport viewport : viewports) {
            viewport(viewport);
        }
        panel(panelX, panelY);
        float[][] parts = finish(ports);
        // The Cherenkov glow over each window, a hair out from its dark glass.
        glow = new Floats();
        glowing = true;
        for (float[] window : windows) {
            bossFace(window[0], radius, window[1], WINDOW, WINDOW, 1.65F, Paint.M_CORE_LIT, true);
        }
        glowing = false;
        return new float[][] { parts[0], parts[1], parts[2], glow.toArray() };
    }

    // The skirt, the bottom head, the shell up to the flange, the flange pair and the closure head.
    private void vessel() {
        float r = radius;
        shell(new float[][] { { PLINTH, r - 4.0F }, { skirtTop, r - 4.0F } }, SHELL_SIDES);
        ring(skirtTop, r - 4.0F, 0.0F, SHELL_SIDES, false);
        shell(new float[][] { { skirtTop, r - 4.0F }, { skirtTop + (bottomHeadTop - skirtTop) * 0.55F, r - 1.0F }, { bottomHeadTop, r } }, SHELL_SIDES);
        shell(new float[][] { { bottomHeadTop, r }, { flangeY - 2.0F, r } }, SHELL_SIDES);
        // The vessel flange and the head flange bolted to it, a little apart.
        flange(flangeY - 2.0F, flangeY, r + 2.0F, r, SHELL_SIDES);
        flange(flangeY + 0.25F, headY, r + 2.0F, r, SHELL_SIDES);
        uprights.add(new float[] { cx, cu, r + 2.0F, flangeY - 2.0F, headY });
        float[][] head = head(headY, r, headHeight, true);
        uprightTube(cx, cu, head, SHELL_SIDES, Math.PI / SHELL_SIDES, Paint.CASING, Paint.PLAIN);
        uprights.add(new float[] { cx, cu, r * 0.75F, headY, crown });
    }

    // The control-rod drive housings in a square grid on the head (as many as fit within CRDM_REACH of the vessel's
    // radius), each a slim pressure housing from the head up to a wider latch housing and a capped top; the seismic
    // support plate round them all near the top.
    private void drives() {
        float reach = radius * CRDM_REACH;
        int n = (int) Math.floor(reach / CRDM_PITCH);
        float top = crdmTop, latch = top - 4.0F;
        float plateY = latch - 3.0F;
        boolean any = false;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                float dx = i * CRDM_PITCH, du = j * CRDM_PITCH;
                float dr = (float) Math.hypot(dx, du);
                if (dr > reach) {
                    continue;
                }
                any = true;
                float x = cx + dx, u = cu + du, base = headAt(headY, radius, headHeight, dr) - 1.0F;
                uprightTube(x, u, new float[][] { { base, CRDM_R + 0.75F }, { base + 2.0F, CRDM_R + 0.75F } }, 6, Math.PI / 6, Paint.BANDED, null);
                uprightTube(x, u, new float[][] { { base + 2.0F, CRDM_R }, { latch, CRDM_R } }, 6, Math.PI / 6, Paint.BANDED, null);
                uprightTube(x, u, new float[][] { { latch, CRDM_LATCH_R }, { top, CRDM_LATCH_R } }, 6, Math.PI / 6, Paint.POLISHED, Paint.MID);
            }
        }
        if (!any) {
            return;
        }
        // The support plate: a ring round the grid, its inside left open between the housings.
        float outer = reach + CRDM_LATCH_R + 2.0F;
        uprightTube(cx, cu, new float[][] { { plateY - 1.5F, outer }, { plateY, outer } }, 16, Math.PI / 16, Paint.BANDED, null);
        ring(plateY, outer, outer - 2.5F, 16, true);
        ring(plateY - 1.5F, outer, outer - 2.5F, 16, false);
        uprights.add(new float[] { cx, cu, outer, crown, top });
    }

    // A viewport in the shell facing the glass block's middle, at its height (kept to the straight shell): a bolted
    // boss with the core's dark glass in it. One per height and facing, however many blocks of glass share it.
    private void viewport(Viewport v) {
        float bx = (v.x() + 0.5F) * PX - cx, bu = (v.u() + 0.5F) * PX - cu;
        if (Math.abs(bx) < 0.01F && Math.abs(bu) < 0.01F) {
            return;
        }
        float a = (float) Math.atan2(bu, bx);
        float lo = bottomHeadTop + VIEWPORT / 2.0F + 1.0F, hi = flangeY - 2.0F - VIEWPORT / 2.0F - 1.0F;
        if (hi < lo) {
            return;
        }
        float y = Math.max(lo, Math.min(hi, (v.y() + 0.5F) * PX));
        for (float[] w : windows) {
            if (Math.abs(w[1] - y) < VIEWPORT && Math.abs(Math.atan2(Math.sin(w[0] - a), Math.cos(w[0] - a))) < VIEWPORT / radius) {
                return;
            }
        }
        windows.add(new float[] { a, y });
        boss(a, radius, y, VIEWPORT, VIEWPORT, 1.5F, 1.5F, Paint.PLAIN, Paint.BANDED);
        bossFace(a, radius, y, WINDOW, WINDOW, 1.55F, Paint.M_CORE_DARK, false);
    }
}
