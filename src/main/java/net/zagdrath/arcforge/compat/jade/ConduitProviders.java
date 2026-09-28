/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.conduit.item.ItemPacket;
import net.zagdrath.arcforge.conduit.network.ActiveConduitNetwork;
import net.zagdrath.arcforge.conduit.network.ConduitNetwork;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.conduit.network.FluidConduitNetwork;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

// What's in a conduit, through Jade's own energy, fluid and item displays: an energy conduit shows the FE
// its whole network holds, fluid and pressurized conduits the fluid or gas in theirs, and an item
// conduit the items travelling through it or waiting in it. (Thermodynamic conduits are in HeatProvider.)
public final class ConduitProviders {
    private ConduitProviders() {}

    private static @Nullable ConduitNetwork<?> network(Accessor<?> accessor) {
        return accessor instanceof BlockAccessor block && block.getLevel() instanceof ServerLevel level
                ? ConduitNetworkManager.get(level).getNetwork(block.getPosition())
                : null;
    }

    private static @Nullable ConduitBlockEntity conduit(Accessor<?> accessor) {
        return accessor instanceof BlockAccessor block && block.getBlockEntity() instanceof ConduitBlockEntity conduit ? conduit : null;
    }

    public enum Energy implements IServerExtensionProvider<EnergyView.Data>, IClientExtensionProvider<EnergyView.Data, EnergyView> {
        INSTANCE;

        @Override
        public @Nullable List<ViewGroup<EnergyView.Data>> getGroups(Accessor<?> accessor) {
            ConduitBlockEntity conduit = conduit(accessor);
            if (conduit == null || conduit.getConduitType() != ConduitType.ENERGY || !(network(accessor) instanceof ActiveConduitNetwork<?> network)) {
                return null;
            }
            return List.of(new ViewGroup<>(List.of(new EnergyView.Data(network.getStored(), network.getCapacity()))));
        }

        @Override
        public List<ClientViewGroup<EnergyView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<EnergyView.Data>> groups) {
            return ClientViewGroup.map(groups, data -> EnergyView.read(data, "FE"), null);
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_energy");
        }
    }

    public enum Fluid implements IServerExtensionProvider<FluidView.Data>, IClientExtensionProvider<FluidView.Data, FluidView> {
        INSTANCE;

        @Override
        public @Nullable List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
            ConduitBlockEntity conduit = conduit(accessor);
            if (conduit == null || (conduit.getConduitType() != ConduitType.FLUID && conduit.getConduitType() != ConduitType.GAS)
                    || !(network(accessor) instanceof FluidConduitNetwork network)) {
                return null;
            }
            JadeFluidObject fluid = network.getFluid().isEmpty() || network.getAmount() <= 0
                    ? JadeFluidObject.empty()
                    : JadeFluidObject.of(network.getFluid().getFluid(), network.getAmount());
            return List.of(new ViewGroup<>(List.of(new FluidView.Data(fluid, network.getCapacity()))));
        }

        @Override
        public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<FluidView.Data>> groups) {
            return ClientViewGroup.map(groups, FluidView::readDefault, null);
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_fluid");
        }
    }

    public enum Items implements IServerExtensionProvider<ItemStack>, IClientExtensionProvider<ItemStack, ItemView> {
        INSTANCE;

        @Override
        public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            ConduitBlockEntity conduit = conduit(accessor);
            if (conduit == null || conduit.getConduitType() != ConduitType.ITEM) {
                return null;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemPacket packet : conduit.getPackets()) {
                stacks.add(packet.stack.copy());
            }
            conduit.getStoredItems().forEach(stack -> stacks.add(stack.copy()));
            return stacks.isEmpty() ? null : List.of(new ViewGroup<>(stacks));
        }

        @Override
        public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
            return ClientViewGroup.map(groups, ItemView::new, null);
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_items");
        }
    }

    // The conduit's tier and what it moves, e.g. "Wrought · up to 256 FE/t".
    public enum Info implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlock() instanceof ConduitBlock conduit) {
                tooltip.add(Component.translatable("jade.arcforge.conduit_rate", conduit.getTier().getDisplayName(),
                        conduit.getTier().describeThroughput(conduit.getConduitType())));
            }
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_info");
        }
    }

    // One line per Conduit Filter, e.g. "Filter North: Allowlist · Insert (3)", the side looked at first. Filters
    // are synced with the conduit (they're drawn on it), so this reads the client's copy.
    public enum Filters implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof ConduitBlockEntity conduit)) {
                return;
            }
            Direction looked = ConduitBlock.sideAt(accessor.getPosition(), accessor.getHitResult().getLocation());
            List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
            sides.remove(looked);
            sides.addFirst(looked);
            for (Direction side : sides) {
                if (conduit.hasFilter(side)) {
                    FilterSettings settings = FilterSettings.of(conduit.getFilter(side));
                    tooltip.add(Component.translatable("jade.arcforge.conduit_filter",
                            Component.translatable("conduit_side.arcforge." + side.getSerializedName()),
                            settings.listName(), settings.flow().displayName(), settings.count()));
                }
            }
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_filters");
        }
    }
}
