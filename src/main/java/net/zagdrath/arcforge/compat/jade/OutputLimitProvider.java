/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.machine.output.OutputLimit;
import net.zagdrath.arcforge.machine.output.OutputLimited;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// "Limited by connections: 3,200 / 50,400 HU/t" on a heat, FE or steam producer whose output is held back by what's
// connected (see OutputLimit), the same yellow line as its GUI. Looking at any block of a multiblock shows its
// controller's. Nothing is sent while it isn't limited.
public enum OutputLimitProvider implements StreamServerDataProvider<BlockAccessor, OutputLimitProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "output_limit");

    public record Data(int given, int could, int unit) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::given, ByteBufCodecs.VAR_INT, Data::could, ByteBufCodecs.VAR_INT, Data::unit, Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        OutputLimited limited = accessor.getBlockEntity() instanceof OutputLimited own ? own : null;
        if (limited == null) {
            MultiblockController controller = MultiblockNameProvider.controllerAt(accessor.getLevel(), accessor.getPosition(), accessor.getBlock());
            limited = controller instanceof OutputLimited own ? own : null;
        }
        OutputLimit.Shown shown = limited != null ? limited.getOutputLimit().shown(accessor.getLevel().getGameTime()) : null;
        return shown != null ? new Data(shown.given(), shown.could(), shown.unit().ordinal()) : null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            OutputLimitProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                OutputLimit.Unit[] units = OutputLimit.Unit.values();
                OutputLimit.Unit unit = units[Math.clamp(data.unit(), 0, units.length - 1)];
                tooltip.add(new OutputLimit.Shown(data.given(), data.could(), unit).line().copy().withColor(ArcforgeGui.WARNING));
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
