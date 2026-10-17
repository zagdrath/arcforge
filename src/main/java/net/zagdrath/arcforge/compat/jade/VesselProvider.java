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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.VesselPart;
import net.zagdrath.arcforge.block.nuclear.ControlRodChannelBlock;
import net.zagdrath.arcforge.block.nuclear.VesselInteriorBlock;
import net.zagdrath.arcforge.blockentity.multiblock.FissionReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.HeavyWaterTowerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamGeneratorBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.VesselControllerBlockEntity;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.nuclear.reactor.ReactorSim;
import net.zagdrath.arcforge.registry.ModItems;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The walled vessels in Jade, from any block of one (its walls, its controller, what's inside). The Fission Reactor:
// thermal power, k-effective, the fuel, cladding and coolant temperatures and pressure, its rods loaded, its mode, and
// a trip if one has scrammed it; looking at a Fuel Channel, the rod in it, and at a Control Rod Channel, its bank and
// how far that bank is in. The Steam Generator: its tubes and the steam it made last tick. The Heavy Water Tower: its
// trays and the Heavy Water it holds. The server builds the lines; Client draws them.
public enum VesselProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "vessel");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static @Nullable VesselControllerBlockEntity controller(BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof VesselControllerBlockEntity own) {
            return own;
        }
        Block block = accessor.getBlock();
        if (block instanceof VesselPart part) {
            return part.structure().findController(accessor.getLevel(), accessor.getPosition());
        }
        if (block instanceof VesselInteriorBlock inside) {
            return inside.structure().findController(accessor.getLevel(), accessor.getPosition());
        }
        return null;
    }

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        VesselControllerBlockEntity vessel = controller(accessor);
        if (vessel == null) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        switch (vessel) {
            case FissionReactorBlockEntity reactor -> reactor(lines, reactor, accessor);
            case SteamGeneratorBlockEntity generator -> lines.add(Component.translatable("jade.arcforge.steam_generator.steam", generator.getTubes(),
                    String.format(Locale.ROOT, "%,d", generator.getLastSteam())).withStyle(ChatFormatting.GRAY));
            case HeavyWaterTowerBlockEntity tower -> lines.add(Component.translatable("jade.arcforge.heavy_water_tower.trays", tower.getTrays(),
                    String.format(Locale.ROOT, "%,d", tower.getOutput().getAmount())).withStyle(ChatFormatting.GRAY));
            default -> {}
        }
        lines.add(vessel.getStatus().getDescription());
        return lines;
    }

    private static void reactor(List<Component> lines, FissionReactorBlockEntity reactor, BlockAccessor accessor) {
        ReactorSim core = reactor.sim();
        if (core == null || !reactor.isFormed()) {
            return;
        }
        lines.add(Component.translatable("jade.arcforge.fission_reactor.power", String.format(Locale.ROOT, "%,.0f", core.thermalPower),
                ReactorSim.percent(core.power), String.format(Locale.ROOT, "%.4f", core.k))
                .withStyle(core.power > 0.01 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        lines.add(Component.translatable("jade.arcforge.fission_reactor.temperatures", String.format(Locale.ROOT, "%.0f", core.hottestFuel()),
                String.format(Locale.ROOT, "%.0f", core.hottestCladding()), String.format(Locale.ROOT, "%.0f", core.coolantTemp),
                String.format(Locale.ROOT, "%.1f", core.pressure)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("jade.arcforge.fission_reactor.rods", reactor.loadedFuel(), reactor.fuelChannelBlocks(),
                core.controlCells().length, reactor.controlChannelBlocks(),
                Component.translatable("jade.arcforge.fission_reactor.mode." + core.mode.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
        if (core.scrammed && core.trip != ReactorSim.Trip.NONE) {
            lines.add(Component.translatable("gui.arcforge.fission_reactor.trip." + core.trip.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.RED));
        }
        // The cell looked at.
        var state = accessor.getBlockState();
        if (state.getBlock() instanceof ControlRodChannelBlock) {
            int bank = state.getValue(ControlRodChannelBlock.BANK);
            lines.add(Component.translatable("jade.arcforge.fission_reactor.bank", ControlRodChannelBlock.bankName(bank), reactor.getBankInsertion(bank))
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            var rod = reactor.rods().get(accessor.getPosition());
            if (rod != null && rod.is(ModItems.FUEL_ROD.get())) {
                FuelRod fuel = FuelRod.of(rod);
                lines.add(Component.translatable("jade.arcforge.fission_reactor.channel", Enrichment.percent(fuel.enrichment()), fuel.burnupText())
                        .withStyle(ChatFormatting.GREEN));
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

    // Jade needs the tooltip half as its own provider, under the same uid.
    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            VesselProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
