/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit;

import net.minecraft.core.Direction;

// Implemented by Arcforge block entities so a conduit side left on "auto" knows which way to move
// resources, based on the machine's own side configuration.
public interface ConduitConnectable {
    // How an auto conduit touching `side` of this block should connect: INPUT (conduit pushes into
    // the machine), OUTPUT (conduit pulls from it) or NONE.
    ConnectionMode getConduitConnection(Direction side, ConduitType type);
}
