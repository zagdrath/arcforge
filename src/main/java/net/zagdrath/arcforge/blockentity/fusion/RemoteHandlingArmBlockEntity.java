/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.fusion;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.fusion.TokamakLayout;
import net.zagdrath.arcforge.fusion.TokamakStructure;
import net.zagdrath.arcforge.item.fusion.ActivatedComponentItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.fusion.RemoteHandlingArmMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Remote Handling Arm: set against a Fusion Reactor's Vessel Port (facing it), it services the reactor through the
// port while it's shut down (no plasma, the field off): with the replacement blocks in its nine input slots, one at a
// time, every SERVICE_TICKS, it reseals a vacuum leak (a First Wall block), repairs damaged coils (any coil block), then
// replaces the most worn of the Divertor, First Wall and Breeding Blanket (one block's worth of wear each). What it
// takes out comes out of its nine output slots as Activated Components, radioactive by the neutrons they took. It runs
// on a little FE. Its renderer draws the arm reaching into the port while it works, folded back while it idles.
public class RemoteHandlingArmBlockEntity extends MachineBlockEntity {
    public static final int INPUT_SLOTS = 9, OUTPUT_SLOTS = 9, MACHINE_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;
    public static final int SERVICE_TICKS = 40, ENERGY_PER_TICK = 80;
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;
    private int progress;
    // Synced: whether it's at work (the renderer reaches the arm into the port), and the client's arm angle (0 folded,
    // 1 reaching in).
    private boolean working;
    public float reach, reachBefore;
    private int replaced;

    public RemoteHandlingArmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.REMOTE_HANDLING_ARM.get(), pos, state, MACHINE_SLOTS, (level, slot, resource) -> slot < INPUT_SLOTS && isPart(resource.toStack(1)),
                Set.<UpgradeType>of(), new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.INPUT, SideMode.ENERGY, SideMode.NONE), SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ENERGY_PER_TICK * 400, ENERGY_PER_TICK * 4, this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot < INPUT_SLOTS, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot >= INPUT_SLOTS && slot < MACHINE_SLOTS);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot < INPUT_SLOTS, slot -> slot >= INPUT_SLOTS && slot < MACHINE_SLOTS);
        this.data = new WideIntContainerData(RemoteHandlingArmMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case RemoteHandlingArmMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case RemoteHandlingArmMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case RemoteHandlingArmMenu.DATA_PROGRESS -> progress;
                    case RemoteHandlingArmMenu.DATA_STATUS -> status.ordinal();
                    case RemoteHandlingArmMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case RemoteHandlingArmMenu.DATA_SIDE_CONFIG -> packedSides();
                    case RemoteHandlingArmMenu.DATA_REPLACED -> replaced;
                    default -> 0;
                };
            }
        };
    }

    // What it can fit: First Wall, Divertor Cassette and Breeding Blanket blocks, and the coils.
    public static boolean isPart(ItemStack stack) {
        return partOf(stack) != null;
    }

    private static TokamakLayout.@Nullable Part partOf(ItemStack stack) {
        if (stack.is(ModItems.FIRST_WALL.get())) {
            return TokamakLayout.Part.FIRST_WALL;
        }
        if (stack.is(ModItems.DIVERTOR_CASSETTE.get())) {
            return TokamakLayout.Part.DIVERTOR;
        }
        if (stack.is(ModItems.BREEDING_BLANKET.get())) {
            return TokamakLayout.Part.BLANKET;
        }
        if (stack.is(ModItems.TOROIDAL_FIELD_COIL.get()) || stack.is(ModItems.POLOIDAL_FIELD_COIL.get()) || stack.is(ModItems.CENTRAL_SOLENOID.get())
                || stack.is(ModItems.STELLARATOR_COIL.get())) {
            return TokamakLayout.Part.TOROIDAL_COIL;
        }
        return null;
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> slot < INPUT_SLOTS && isPart(resource.toStack(1)), Set.of(), () -> {});
    }

    // The reactor whose Vessel Port it faces, or null.
    public @Nullable FusionReactorBlockEntity reactor(Level level) {
        BlockPos port = worldPosition.relative(getFacing());
        if (!level.getBlockState(port).is(ModBlocks.VESSEL_PORT.get())) {
            return null;
        }
        FusionReactorBlockEntity reactor = TokamakStructure.findController(level, port);
        return reactor != null && reactor.isFormed() ? reactor : null;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean wasWorking = working;
        status = canRun(level) ? work(level) : stoppedStatus();
        working = status == MachineStatus.RUNNING;
        setLit(working);
        autoEject(level, itemOutput);
        if (working != wasWorking) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private MachineStatus work(ServerLevel level) {
        FusionReactorBlockEntity reactor = reactor(level);
        if (reactor == null) {
            progress = 0;
            return MachineStatus.NOT_FORMED;
        }
        if (!reactor.isShutDown()) {
            progress = 0;
            return MachineStatus.WAITING_SHUTDOWN;
        }
        int slot = nextSlot(reactor);
        if (slot < 0) {
            progress = 0;
            return MachineStatus.IDLE;
        }
        if (!hasRoom()) {
            return MachineStatus.OUTPUT_FULL;
        }
        if (!energy.consume(ENERGY_PER_TICK)) {
            return MachineStatus.NO_POWER;
        }
        if (++progress < SERVICE_TICKS) {
            setChanged();
            return MachineStatus.RUNNING;
        }
        progress = 0;
        ItemStack part = items.getStack(slot);
        TokamakLayout.Part kind = partOf(part);
        if (kind == null) {
            return MachineStatus.IDLE;
        }
        boolean done;
        double activity = 0.0;
        if (kind == TokamakLayout.Part.FIRST_WALL && reactor.leak() > 0.0) {
            done = reactor.reseal();
        } else {
            activity = reactor.replace(kind);
            done = activity >= 0.0;
        }
        if (done) {
            items.setStack(slot, part.copyWithCount(part.getCount() - 1));
            replaced++;
            if (activity > 0.0) {
                insertOutput(ActivatedComponentItem.of(activity, level.getGameTime()));
            }
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 0.8F);
            ArcforgeAdvancements.produced(this, part, null, "remote_handling");
        }
        setChanged();
        return MachineStatus.RUNNING;
    }

    // The input slot holding what's most needed: a First Wall block for a leak, a coil for damaged coils, then the most
    // worn of the divertor, first wall and blanket; -1 if nothing it holds is needed.
    private int nextSlot(FusionReactorBlockEntity reactor) {
        if (reactor.leak() > 0.0) {
            int slot = find(TokamakLayout.Part.FIRST_WALL);
            if (slot >= 0) {
                return slot;
            }
        }
        if (reactor.wear(TokamakLayout.Part.TOROIDAL_COIL) > 0.0) {
            int slot = find(TokamakLayout.Part.TOROIDAL_COIL);
            if (slot >= 0) {
                return slot;
            }
        }
        TokamakLayout.Part[] order = { TokamakLayout.Part.DIVERTOR, TokamakLayout.Part.FIRST_WALL, TokamakLayout.Part.BLANKET };
        java.util.Arrays.sort(order, (a, b) -> Double.compare(reactor.wear(b), reactor.wear(a)));
        for (TokamakLayout.Part part : order) {
            if (reactor.wear(part) > 0.0) {
                int slot = find(part);
                if (slot >= 0) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private int find(TokamakLayout.Part part) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (partOf(items.getStack(slot)) == part) {
                return slot;
            }
        }
        return -1;
    }

    private boolean hasRoom() {
        for (int slot = INPUT_SLOTS; slot < MACHINE_SLOTS; slot++) {
            if (items.getStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void insertOutput(ItemStack stack) {
        for (int slot = INPUT_SLOTS; slot < MACHINE_SLOTS; slot++) {
            if (items.getStack(slot).isEmpty()) {
                items.setStack(slot, stack);
                return;
            }
        }
    }

    public void clientTick() {
        reachBefore = reach;
        reach = Math.max(0.0F, Math.min(1.0F, reach + (working ? 0.05F : -0.05F)));
    }

    public boolean isWorking() {
        return working;
    }

    // Ticks into the part it's fitting now (of SERVICE_TICKS).
    public int getProgress() {
        return progress;
    }

    // Parts it has fitted since it was placed.
    public int getReplaced() {
        return replaced;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // --- Capabilities ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL, GAS, FLUID -> ConnectionMode.NONE;
        };
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        progress = input.getIntOr("progress", 0);
        replaced = input.getIntOr("replaced", 0);
        working = input.getBooleanOr("working", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("progress", progress);
        output.putInt("replaced", replaced);
        output.putBoolean("working", working);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.remote_handling_arm");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RemoteHandlingArmMenu(containerId, inventory, worldPosition, items, data);
    }
}
