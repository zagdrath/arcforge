/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.RefiningMachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.refining.RefiningCore;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.RefiningMenu;
import net.zagdrath.arcforge.multiblock.SteamCrackerLayout;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.PlantKind;
import net.zagdrath.arcforge.recipe.RefiningProcess;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Steam Cracker (see OilfieldStructures.PlantBox, 3x3x3), run by its controller: a furnace box whose radiant tubes
// crack Naphtha or Refinery Gas with steam at 800°C or hotter into Ethylene, Propylene, a little Hydrogen and a
// trickle of Heavy Oil (arcforge:steam_cracking recipes; see RefiningCore for how it runs). Through its ports: the
// feed (and steam) in (Input), steam in (Steam), heat in (Heat), Heavy Oil out of Output ports, the gases out of Gas
// Output ports or each out of its own (Ethylene, Propylene, Hydrogen), pushed every few ticks. Speed upgrades make it
// faster. Its peep holes glow as hot as the furnace is (see SteamCrackerRenderer).
public class SteamCrackerBlockEntity extends PlantControllerBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.STEAM, SideMode.HEAT, SideMode.OUTPUT,
            SideMode.GAS_OUTPUT, SideMode.ETHYLENE, SideMode.PROPYLENE, SideMode.HYDROGEN);
    // The glow is synced to clients when it moves by this much; it starts at GLOW_FROM °C and is full GLOW_SPAN hotter.
    // The gas ports, pushed as the output ports are (they aren't outputs on every machine).
    private static final Set<SideMode> PUSHED_GASES = EnumSet.of(SideMode.GAS_OUTPUT, SideMode.HYDROGEN);
    private static final float GLOW_STEP = 0.05F, GLOW_FROM = 300.0F, GLOW_SPAN = 600.0F;

    private final RefiningCore core;
    private final ResourceHandler<FluidResource> inputs;
    private final ContainerData data;
    private float glow;
    private float syncedGlow = -1.0F;

    public SteamCrackerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.STEAM_CRACKER.get(), PlantKind.STEAM_CRACKER, pos, state, 0, (level, slot, resource) -> false, UPGRADES,
                SIDE_MODES);
        this.core = new RefiningCore(RefiningProcess.STEAM_CRACKING, new RefiningCore.Layout(-1, -1, 0, 0,
                ArcforgeConfig.STEAM_CRACKER_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.STEAM_CRACKER_MAX_TEMPERATURE.getAsInt(),
                ArcforgeConfig.STEAM_CRACKER_FEED_CAPACITY.getAsInt(), ArcforgeConfig.STEAM_CRACKER_PRODUCT_CAPACITY.getAsInt(),
                ArcforgeConfig.STEAM_CRACKER_SECOND_FEED_CAPACITY.getAsInt()), items, () -> level, this::setChanged);
        core.registerVents(gasVents);
        ResourceHandler<FluidResource> steam = core.secondFeedInput();
        this.inputs = steam != null ? new CombinedResourceHandler<>(core.feedInput(), steam) : core.feedInput();
        this.data = RefiningMachineBlockEntity.menuData(core, new RefiningMachineBlockEntity.MachineBlockEntityAccess() {
            @Override
            public int statusOrdinal() {
                return status.ordinal();
            }

            @Override
            public int redstoneOrdinal() {
                return redstoneMode.ordinal();
            }

            @Override
            public int sides() {
                return packedSides();
            }
        });
    }

    // A client-side copy of the slots (none but the upgrade slots), for the menu.
    public static MachineItemHandler clientItems() {
        return clientItems(0, UPGRADES);
    }

    @Override
    protected List<float[]> solids(int length) {
        return SteamCrackerLayout.solids();
    }

    public RefiningCore getCore() {
        return core;
    }

    public void serverTick(ServerLevel level) {
        if (!tickStructure(level)) {
            return;
        }
        status = core.tick(level, redstoneMode.canRun(powered), upgrades(UpgradeType.SPEED), 0);
        boolean working = status == RefiningProcess.STEAM_CRACKING.runningStatus();
        if (working && core.progress() == 0) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, core.mainProduct(), RefiningProcess.STEAM_CRACKING.getSerializedName());
        }
        setRunning(working);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this, true);
            MultiblockAutomation.pushFluids(level, this, PUSHED_GASES, FluidType.BUCKET_VOLUME);
        }
        int temperature = core.heat() != null ? core.heat().getTemperature() : 0;
        glow = Math.max(0.0F, Math.min(1.0F, (temperature - GLOW_FROM) / GLOW_SPAN));
        if (Math.abs(glow - syncedGlow) >= GLOW_STEP || glow == 0.0F && syncedGlow != 0.0F) {
            syncedGlow = glow;
            sync();
        }
    }

    // Client side: how brightly the peep holes glow (0 to 1).
    public float getGlow() {
        return Math.max(0.0F, syncedGlow);
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return core.all();
        }
        return switch (mode) {
            case INPUT -> inputs;
            case STEAM -> core.secondFeedInput();
            case OUTPUT -> core.liquidOutputs();
            case GAS_OUTPUT -> core.gasOutputs();
            case ETHYLENE -> core.outputFor(ModFluids.ETHYLENE.get());
            case PROPYLENE -> core.outputFor(ModFluids.PROPYLENE.get());
            case HYDROGEN -> core.outputFor(ModFluids.HYDROGEN.get());
            default -> null;
        };
    }

    @Override
    public @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return core.heatInput();
        }
        return faceMode(pos, side) == SideMode.HEAT ? core.heatInput() : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            // Steam is a gas: it comes in through Steam (and Input) ports on gas conduits.
            case GAS -> mode == SideMode.INPUT || mode == SideMode.STEAM ? ConnectionMode.INPUT
                    : mode == SideMode.GAS_OUTPUT || mode == SideMode.ETHYLENE || mode == SideMode.PROPYLENE || mode == SideMode.HYDROGEN
                            ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        core.load(input);
        syncedGlow = input.getFloatOr("glow", 0.0F);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        core.save(output);
        output.putFloat("glow", Math.max(0.0F, syncedGlow));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.steam_cracker");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RefiningMenu(RefiningProcess.STEAM_CRACKING, containerId, inventory, worldPosition, items, data);
    }
}
