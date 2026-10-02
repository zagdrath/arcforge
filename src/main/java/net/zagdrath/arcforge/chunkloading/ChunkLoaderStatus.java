/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.chunkloading;

import net.minecraft.network.chat.Component;

// What a Chunk Loader is doing; only ACTIVE loads chunks.
public enum ChunkLoaderStatus {
    ACTIVE("active"),
    DISABLED("disabled"),
    NO_POWER("no_power"),
    LIMIT("limit"),
    OWNER_OFFLINE("owner_offline"),
    NO_OWNER("no_owner");

    private final String name;

    ChunkLoaderStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Component description() {
        return Component.translatable("gui.arcforge.chunk_loader.status." + name);
    }

    public static ChunkLoaderStatus byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : NO_OWNER;
    }
}
