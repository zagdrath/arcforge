/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.interaction;

import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

// Implemented by machine block entities that players can fill (or empty) by clicking with a bucket
// or any other fluid container. Works with a plain right-click and with a sneak right-click.
public interface FluidInteractable {
    // The handler a held fluid container interacts with, independent of the side configuration.
    @Nullable ResourceHandler<FluidResource> getInteractionFluidHandler();
}
