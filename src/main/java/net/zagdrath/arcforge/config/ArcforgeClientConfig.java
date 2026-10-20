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

    static {
        BUILDER.comment("How the large multiblock models are drawn. Their still parts (casings, decks, pipes, frames) are in the world's",
                "own mesh at any distance; these set when their moving parts (rotors, fans, beams, drill strings, cranks) are simplified.")
                .push("largeModels");
    }

    public static final ModConfigSpec.IntValue LOD_DISTANCE = BUILDER
            .comment("Blocks away past which moving parts are drawn as plain solid shapes and stop turning.")
            .defineInRange("simpleMovingPartsFrom", 48, 8, 1_024);

    public static final ModConfigSpec.IntValue STATIC_ONLY_DISTANCE = BUILDER
            .comment("Blocks away past which only the still parts are drawn (no moving parts at all). At least simpleMovingPartsFrom.")
            .defineInRange("stillPartsOnlyFrom", 96, 8, 1_024);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
