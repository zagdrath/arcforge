/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;

// What VaultRenderer draws on a Vault's front: the item, dimmed when the vault is empty but locked to it, and the
// count (none when empty), lit by the light in front of the vault.
public class VaultRenderState extends BlockEntityRenderState {
    public final ItemStackRenderState item = new ItemStackRenderState();
    public boolean hasItem;
    public boolean ghost;
    public @Nullable FormattedCharSequence count;
    public int countWidth;
    public Direction facing = Direction.NORTH;
    public int frontLight;
}
