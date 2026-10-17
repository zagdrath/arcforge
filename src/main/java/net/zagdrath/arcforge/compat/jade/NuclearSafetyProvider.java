/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.nuclear.CoriumBlock;
import net.zagdrath.arcforge.blockentity.nuclear.DecontaminationShowerBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.DrainTankBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.DryCaskBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.EccsAccumulatorBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.EccsPumpBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.FreezePlugBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.GeologicalRepositoryBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.SaltProcessingUnitBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.SpentFuelRackBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.safety.Containment;
import net.zagdrath.arcforge.nuclear.safety.Containments;
import net.zagdrath.arcforge.nuclear.safety.Fallout;
import net.zagdrath.arcforge.registry.ModBlocks;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The nuclear tier's safety, spent fuel and waste blocks in Jade: a Spent Fuel Rack's rods, decay heat and temperature
// (and whether its pool is deep enough); a Dry Cask's rods; a Decontamination Shower's water and filter; an ECCS
// Accumulator's water and a Pump's FE; a Freeze Plug frozen or melted; a Drain Tank's salt; a Salt Processing Unit's
// waste; a Geological Repository's store; Corium's crust and how far it has melted down; Fallout dust's chunk; and on a
// containment's own blocks, the containment they bound. The server builds the lines; Client draws them.
public enum NuclearSafetyProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "nuclear_safety");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static String number(String format, double value) {
        return String.format(Locale.ROOT, format, value);
    }

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        List<Component> lines = new ArrayList<>();
        Level level = accessor.getLevel();
        long now = level.getGameTime();
        switch (accessor.getBlockEntity()) {
            case SpentFuelRackBlockEntity rack -> {
                lines.add(Component.translatable("jade.arcforge.spent_fuel_rack.rods", rack.count(), SpentFuelRackBlockEntity.SLOTS,
                        number("%.1f", rack.decayHeat(now)), number("%.0f", rack.getTemperature())).withStyle(ChatFormatting.GRAY));
                int depth = SpentFuelRackBlockEntity.waterDepth(level, accessor.getPosition(), accessor.getBlockState());
                String pool = !rack.isUnderwater() ? "dry" : depth < ArcforgeConfig.POOL_DEPTH.getAsInt() ? "shallow" : "deep";
                lines.add(Component.translatable("jade.arcforge.spent_fuel_rack." + pool).withStyle(pool.equals("deep") ? ChatFormatting.AQUA
                        : ChatFormatting.GOLD));
            }
            case DryCaskBlockEntity cask -> lines.add(Component.translatable("jade.arcforge.dry_cask.rods", cask.count(), DryCaskBlockEntity.SLOTS)
                    .withStyle(ChatFormatting.GRAY));
            case DecontaminationShowerBlockEntity shower -> lines.add(Component.translatable(shower.hasGoodFilter() ? "jade.arcforge.shower.filter"
                    : "jade.arcforge.shower.no_filter", String.format(Locale.ROOT, "%,d", shower.getTank().getAmount()),
                    ArcforgeConfig.FILTER_USES.getAsInt() - shower.getUses()).withStyle(shower.hasGoodFilter() ? ChatFormatting.GRAY : ChatFormatting.GOLD));
            case EccsAccumulatorBlockEntity accumulator -> lines.add(Component.translatable("jade.arcforge.eccs_accumulator.water",
                    String.format(Locale.ROOT, "%,d", accumulator.getTank().getAmount())).withStyle(ChatFormatting.GRAY));
            case EccsPumpBlockEntity pump -> lines.add(Component.translatable(pump.isRunning() ? "jade.arcforge.eccs_pump.running" : "jade.arcforge.eccs_pump.ready",
                    String.format(Locale.ROOT, "%,d", pump.getEnergy().getAmountAsInt())).withStyle(ChatFormatting.GRAY));
            case FreezePlugBlockEntity plug -> lines.add(Component.translatable(plug.isMelted() ? "jade.arcforge.freeze_plug.melted"
                    : "jade.arcforge.freeze_plug.frozen", String.format(Locale.ROOT, "%,d", plug.getEnergy().getAmountAsInt()))
                    .withStyle(plug.isMelted() ? ChatFormatting.RED : ChatFormatting.AQUA));
            case DrainTankBlockEntity tank -> lines.add(Component.translatable("jade.arcforge.drain_tank.salt",
                    String.format(Locale.ROOT, "%,d", tank.getTank().getAmount())).withStyle(ChatFormatting.GRAY));
            case SaltProcessingUnitBlockEntity unit -> lines.add(Component.translatable("jade.arcforge.salt_processing_unit.waste",
                    String.format(Locale.ROOT, "%,d", unit.getWaste().getAmount())).withStyle(unit.isRunning() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            case GeologicalRepositoryBlockEntity vault -> {
                lines.add(Component.translatable("jade.arcforge.geological_repository.holds", vault.logs(), vault.casks()).withStyle(ChatFormatting.GRAY));
                if (!GeologicalRepositoryBlockEntity.isValidSite(level, accessor.getPosition())) {
                    lines.add(Component.translatable("gui.arcforge.geological_repository.invalid").withStyle(ChatFormatting.GOLD));
                }
            }
            case null, default -> blockLines(lines, accessor);
        }
        return lines.isEmpty() ? null : lines;
    }

    private static void blockLines(List<Component> lines, BlockAccessor accessor) {
        BlockState state = accessor.getBlockState();
        Level level = accessor.getLevel();
        BlockPos pos = accessor.getPosition();
        if (state.getBlock() instanceof CoriumBlock) {
            lines.add(Component.translatable("jade.arcforge.corium", state.getValue(CoriumBlock.COOLING), state.getValue(CoriumBlock.DEPTH))
                    .withStyle(ChatFormatting.GOLD));
        } else if (state.is(ModBlocks.FALLOUT.get()) && level instanceof ServerLevel server) {
            lines.add(Component.translatable("jade.arcforge.fallout", number("%.2f", Fallout.ground(server, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).total()))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            // A containment block: the containment on any side of it.
            for (Containment containment : Containments.all(level)) {
                for (Direction direction : Direction.values()) {
                    if (containment.sealed() && containment.contains(pos.relative(direction))) {
                        lines.add(Component.translatable("jade.arcforge.containment", String.format(Locale.ROOT, "%,d", containment.airVolume()),
                                number("%.2f", containment.hydrogenPercent()), number("%.1f", containment.activity)).withStyle(ChatFormatting.DARK_AQUA));
                        return;
                    }
                }
            }
        }
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, List<Component>> streamCodec() {
        return CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            NuclearSafetyProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
