/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.farming.CompostBinBlock;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.block.farming.TrellisBlock;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

// Farming blocks in Jade, read from their block states (no server data needed): Loam Farmland's nutrients and whether
// it's moist ("Nutrients: 7 / 15", "Moist"), a Compost Bin's fill ("Compost: 3 / 7", or "Compost ready") and a
// Trellis's hop vine.
public final class FarmingProviders {
    private FarmingProviders() {}

    public enum LoamFarmland implements IBlockComponentProvider {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "loam_farmland");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (!(state.getBlock() instanceof LoamFarmlandBlock)) {
                return;
            }
            tooltip.add(Component.translatable("jade.arcforge.loam_farmland.nutrients", state.getValue(LoamFarmlandBlock.NUTRIENTS),
                    LoamFarmlandBlock.MAX_NUTRIENTS));
            tooltip.add(Component.translatable(state.getValue(FarmlandBlock.MOISTURE) > 0 ? "jade.arcforge.loam_farmland.moist" : "jade.arcforge.loam_farmland.dry"));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    public enum CompostBin implements IBlockComponentProvider {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "compost_bin");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (!(state.getBlock() instanceof CompostBinBlock)) {
                return;
            }
            int level = state.getValue(CompostBinBlock.LEVEL);
            tooltip.add(level == CompostBinBlock.READY
                    ? Component.translatable("jade.arcforge.compost_bin.ready")
                    : Component.translatable("jade.arcforge.compost_bin.level", level, CompostBinBlock.FULL));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    // A Trellis: bare, or its hop vine's stage ("Hops: growing (2 / 4)", "Hops: cones ready"). Crops show their growth
    // through Jade's own crop view.
    public enum Trellis implements IBlockComponentProvider {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "trellis");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (!(state.getBlock() instanceof TrellisBlock)) {
                return;
            }
            int age = state.getValue(TrellisBlock.AGE);
            tooltip.add(switch (age) {
                case TrellisBlock.BARE -> Component.translatable("jade.arcforge.trellis.bare");
                case TrellisBlock.BEARING -> Component.translatable("jade.arcforge.trellis.bearing");
                default -> Component.translatable("jade.arcforge.trellis.growing", age, TrellisBlock.BEARING);
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
