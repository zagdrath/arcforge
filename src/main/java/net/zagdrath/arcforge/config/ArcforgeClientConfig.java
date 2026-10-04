/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

// Settings for each player's own game (arcforge-client.toml): what it shows them. Server rules live in ArcforgeConfig.
public final class ArcforgeClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private ArcforgeClientConfig() {}

    static {
        BUILDER.comment("Toasts that tell you about your machines.").push("notifications");
    }

    public static final ModConfigSpec.BooleanValue ARC_QUARRY_FINISHED_TOAST = BUILDER
            .comment("Show a toast when one of your Arc Quarries finishes (the player who placed it gets it).")
            .define("arcQuarryFinished", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
