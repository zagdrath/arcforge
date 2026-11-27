/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import net.minecraft.world.phys.Vec3;

// A block entity that gives off radiation of its own, beyond what it holds (a running Fission Reactor's core): its dose
// rate (mSv/h at a block) and where it comes from. RadiationField counts it as one source there, shielded by the blocks
// between, its vessel's walls among them.
public interface RadiationSource {
    double radiationRate();

    // Neutrons (mSv/h at a block): shielded by the blocks' neutron transmission (see RadiationShielding) rather than
    // their gamma one, and counted apart by the Geiger Counter. A fusion plasma's; none for most sources.
    default double neutronRate() {
        return 0.0;
    }

    Vec3 radiationCentre();
}
