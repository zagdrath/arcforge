/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Steam Generator's model (see SteamGeneratorRenderer), on the refinery tower's sheet: a vertical heat exchanger
// standing in the casing frame on its plinth. From the bottom: a skirt, the hemispherical channel head (where the hot
// and cold legs come and go) with its divider flange, the tall plated tube-bundle shell, a conical transition out to
// the wider steam drum, the drum with a manway, and the elliptical top head with the steam outlet standing on its
// crown. The port nozzles run from the walls to the shell. Sized to the structure.
public final class SteamGeneratorMesh extends VesselMesh {
    static final float OUTLET_R = 3.0F, MANWAY = 8.0F;

    final float lowerRadius, drumRadius, channelTop, transition, drumBottom, headY, headHeight, crown;
    final int panelX, panelY;

    public SteamGeneratorMesh(ModelFrame frame, int panelX, int panelY) {
        super(frame);
        this.panelX = panelX;
        this.panelY = panelY;
        float half = Math.min(width, length) / 2.0F;
        this.drumRadius = half - 6.0F;
        this.lowerRadius = Math.max(9.0F, drumRadius - Math.max(6.0F, half * 0.28F));
        this.channelTop = PLINTH + 4.0F + Math.max(5.0F, lowerRadius * 0.4F);
        this.headHeight = drumRadius * 0.4F;
        this.crown = height - 3.0F - 6.0F;
        this.headY = crown - headHeight;
        float span = headY - (channelTop + 2.0F);
        float drum = Math.max(8.0F, span * 0.3F), cone = Math.max(4.0F, span * 0.08F);
        this.drumBottom = headY - drum;
        this.transition = drumBottom - cone;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        plinth();
        casingFrame();
        float rl = lowerRadius, rd = drumRadius, skirt = PLINTH + 4.0F;
        // The skirt and the channel head.
        shell(new float[][] { { PLINTH, rl - 3.0F }, { skirt, rl - 3.0F } }, SHELL_SIDES);
        ring(skirt, rl - 3.0F, 0.0F, SHELL_SIDES, false);
        shell(new float[][] { { skirt, rl - 3.0F }, { skirt + (channelTop - skirt) * 0.6F, rl - 0.75F }, { channelTop, rl } }, SHELL_SIDES);
        // The tubesheet flange between the channel head and the shell.
        flange(channelTop, channelTop + 2.0F, rl + 2.0F, rl, SHELL_SIDES);
        uprights.add(new float[] { cx, cu, rl + 2.0F, channelTop, channelTop + 2.0F });
        // The tube-bundle shell, the cone and the drum.
        shell(new float[][] { { channelTop + 2.0F, rl }, { transition, rl } }, SHELL_SIDES);
        shell(new float[][] { { transition, rl }, { drumBottom, rd } }, SHELL_SIDES);
        shell(new float[][] { { drumBottom, rd }, { headY, rd } }, SHELL_SIDES);
        // A girth band where the drum meets its head.
        flange(headY - 1.5F, headY, rd + 1.0F, rd, SHELL_SIDES);
        uprightTube(cx, cu, head(headY, rd, headHeight, true), SHELL_SIDES, Math.PI / SHELL_SIDES, Paint.CASING, Paint.PLAIN);
        uprights.add(new float[] { cx, cu, rd * 0.75F, headY, crown });
        // The steam outlet on the crown, flanged at its top.
        float top = height - 3.0F;
        uprightTube(cx, cu, new float[][] { { crown - 1.0F, OUTLET_R }, { top - 1.5F, OUTLET_R } }, 8, Math.PI / 8, Paint.BANDED, null);
        uprightTube(cx, cu, new float[][] { { top - 1.5F, OUTLET_R + 1.5F }, { top, OUTLET_R + 1.5F } }, 8, Math.PI / 8, Paint.BANDED, Paint.PLAIN);
        uprights.add(new float[] { cx, cu, OUTLET_R + 1.5F, crown, top });
        // The drum's manway, facing out of the front.
        if (headY - drumBottom >= MANWAY + 2.0F) {
            boss((float) (-Math.PI / 2.0), rd, (drumBottom + headY) / 2.0F, MANWAY, MANWAY, 1.0F, 2.0F, Paint.PLAIN, Paint.BANDED);
        }
        panel(panelX, panelY);
        return finish(ports);
    }
}
