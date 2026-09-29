/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.security;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// A block's owner and its security override, as an Owned block entity keeps them. Saved as "owner" (UUID),
// "owner_name" and "security" (the override's ordinal, -1 for none: follow the owner's profile).
public final class Ownership {
    private @Nullable UUID owner;
    private String ownerName = "";
    private Optional<SecurityMode> override = Optional.empty();
    private final Runnable onChange;

    public Ownership(Runnable onChange) {
        this.onChange = onChange;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public Optional<SecurityMode> override() {
        return override;
    }

    public void setOwner(@Nullable UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name == null ? "" : name;
        onChange.run();
    }

    public void setOverride(Optional<SecurityMode> override) {
        this.override = override;
        onChange.run();
    }

    public void load(ValueInput input) {
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        int security = input.getIntOr("security", -1);
        override = security < 0 ? Optional.empty() : Optional.of(SecurityMode.byId(security));
    }

    public void save(ValueOutput output) {
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
            output.putString("owner_name", ownerName);
        }
        output.putInt("security", override.map(Enum::ordinal).orElse(-1));
    }
}
