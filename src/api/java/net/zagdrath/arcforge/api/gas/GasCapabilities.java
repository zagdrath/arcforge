/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.gas;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.zagdrath.arcforge.api.ArcforgeApi;

/**
 * The capabilities through which other mods store and move Arcforge gases.
 *
 * <pre>{@code
 * GasHandler tanks = level.getCapability(GasCapabilities.BLOCK, pos, null);
 * GasHandler cartridge = ItemAccess.forPlayerSlot(player, slot).getCapability(GasCapabilities.ITEM);
 * }</pre>
 */
public final class GasCapabilities {
    /** The id of both capabilities: {@code arcforge:gas_handler}. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ArcforgeApi.MOD_ID, "gas_handler");

    /**
     * A block's gas tanks, as a whole: a machine's gas tanks with their input and output roles, or a Pressurized
     * Cylinder's tank. Every block of a formed multiblock gives its controller's tanks, as
     * {@link net.zagdrath.arcforge.api.machine.MachineCapabilities#MACHINE_CONTROL} does. Provided on the server only,
     * and only by blocks with at least one tank that can hold a gas. The capability has no context: pass
     * {@code null}. It ignores the block's side configuration; for what one face offers a pipe, use NeoForge's
     * {@code Capabilities.Fluid.BLOCK} on that face, which carries gases as fluids.
     */
    public static final BlockCapability<GasHandler, @Nullable Void> BLOCK = BlockCapability.createVoid(ID, GasHandler.class);

    /**
     * An item's gas tank: Gas Cartridges and Jetpacks. Query it through an {@link ItemAccess}, which the handler
     * writes the item's new contents back to; change the item on the server and let the client sync.
     */
    public static final ItemCapability<GasHandler, ItemAccess> ITEM = ItemCapability.create(ID, GasHandler.class, ItemAccess.class);

    private GasCapabilities() {}
}
