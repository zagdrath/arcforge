/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.Ownership;

// The Security Terminal only remembers who owns it (so a Private one can't be broken by others); the profiles it
// edits are the server's (SecurityProfiles).
public class SecurityTerminalBlockEntity extends BlockEntity implements Owned {
    private final Ownership ownership = new Ownership(this::setChanged);

    public SecurityTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SECURITY_TERMINAL.get(), pos, state);
    }

    @Override
    public Ownership ownership() {
        return ownership;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ownership.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ownership.save(output);
    }
}
