/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.logistics;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.logistics.LinkConduitBlock;
import net.zagdrath.arcforge.block.logistics.WiredMachineLinkBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.link.LinkableMachine;
import net.zagdrath.arcforge.machine.link.MachineLink;
import net.zagdrath.arcforge.machine.link.MachineLinks;
import net.zagdrath.arcforge.menu.logistics.WiredMachineLinkMenu;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.PortStore;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.SecurityRules;
import net.zagdrath.arcforge.security.Ownership;

// A Wired Machine Link. Every second (and whenever its run changes) it traces the Link Conduit plugged into it, block by
// block, to the machine or multiblock at the far end (at most logistics.machineLink.maxCableLength blocks), and claims it: a
// machine takes one Wired Machine Link at a time. While it's linked:
// - each of its other faces can be set to any port mode the machine has (NONE first), and a face in a mode hands out the
//   handler that port would have on the machine (MachineLinks.handler): items, fluids, gases, FE and heat go straight to
//   and from the machine's own inventories, tanks and buffers, with no limit of its own;
// - conduits touching it connect as they would to that port (ConduitConnectable);
// - it pushes out of its faces as the port would: items, fluids and gases from output faces while the machine's
//   auto-eject is on (every multiblock.pushInterval ticks, 16 items and 1,000 mB a face), FE from Energy faces the
//   machine gives FE out of, and heat from Heat faces while the machine is hotter (heat.contactRate a face);
// - the run's core glows (LinkConduitBlock.LIT) and so does its screen (WiredMachineLinkBlock.LIT).
// The faces keep their modes when the link goes. Its faces are drawn from PortStore (the cable's face as MACHINE_LINK).
public class WiredMachineLinkBlockEntity extends BlockEntity implements MenuProvider, Owned, ConduitConnectable {
    private static final int TRACE_INTERVAL = 20;
    private static final int ITEMS_PER_FACE = 16;
    private static final int FLUID_PER_FACE = 1_000;

    // Why it is, or isn't, linked.
    public enum Status implements StringRepresentable {
        LINKED("linked"),
        // No Link Conduit is plugged into it.
        NO_CABLE("no_cable"),
        // The cable stops before it reaches anything.
        BROKEN("broken"),
        TOO_LONG("too_long"),
        // The cable ends at something that isn't a machine or a formed multiblock (or at another Wired Machine Link).
        NO_MACHINE("no_machine"),
        // The machine already has another Wired Machine Link.
        TAKEN("taken"),
        // The machine belongs to someone this link's owner may not use.
        NO_ACCESS("no_access");

        public static final Codec<Status> CODEC = StringRepresentable.fromEnum(Status::values);
        private final String name;

        Status(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Component description() {
            return Component.translatable("gui.arcforge.wired_machine_link.status." + name);
        }

        public static Status byId(int id) {
            Status[] values = values();
            return id >= 0 && id < values.length ? values[id] : NO_CABLE;
        }
    }

    private final Ownership ownership = new Ownership(this::setChanged);
    // Its faces' modes, by world direction (kept when the link goes; the cable's face is never set).
    private PortFaces faces = PortFaces.NONE;

    // The last trace (saved, and synced for the screen and Jade).
    private Status status = Status.NO_CABLE;
    private @Nullable Direction cableFace;
    private @Nullable BlockPos machinePos;
    private @Nullable MachineLink link;
    private Component machineName = Component.empty();
    private @Nullable RelativeSide machineSide;
    private int distance;

    // Server side: the run of cable last lit, and when to trace again.
    private final Set<BlockPos> run = new HashSet<>();
    private boolean traceRequested = true;
    private int traceWait;
    private final Map<Direction, BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>> itemTargets = new EnumMap<>(Direction.class);
    private final Map<Direction, BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>> fluidTargets = new EnumMap<>(Direction.class);
    private final Map<Direction, BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new EnumMap<>(Direction.class);
    private final Map<Direction, BlockCapabilityCache<HeatHandler, @Nullable Direction>> heatTargets = new EnumMap<>(Direction.class);

    public WiredMachineLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WIRED_MACHINE_LINK.get(), pos, state);
    }

    @Override
    public Ownership ownership() {
        return ownership;
    }

    public Direction getFacing() {
        return getBlockState().getValue(WiredMachineLinkBlock.FACING);
    }

    // --- What it shows ---

    public Status getStatus() {
        return status;
    }

    public boolean isLinked() {
        return status == Status.LINKED;
    }

    public Component getMachineName() {
        return machineName;
    }

    // The face of the machine (relative to its front) the cable is on, while linked.
    public @Nullable RelativeSide getMachineSide() {
        return machineSide;
    }

    // Blocks of Link Conduit between it and the machine.
    public int getDistance() {
        return distance;
    }

    public @Nullable Direction getCableFace() {
        return cableFace;
    }

    // A face's mode as it shows: the cable's face is the Wired Machine Link.
    public SideMode getFaceMode(Direction face) {
        return face == cableFace ? SideMode.MACHINE_LINK : faces.get(face);
    }

    public SideMode getFaceMode(RelativeSide side) {
        return getFaceMode(side.toDirection(getFacing()));
    }

    // Whether it's linked to the machine (or structure controller) machine (see LinkableMachine.getMachineLink).
    public boolean isLinkedTo(LinkableMachine machine) {
        return status == Status.LINKED && machine.getBlockPos().equals(machinePos);
    }

    public @Nullable LinkableMachine linkedMachine() {
        if (status != Status.LINKED || machinePos == null || level == null || !level.isLoaded(machinePos)) {
            return null;
        }
        return level.getBlockEntity(machinePos) instanceof LinkableMachine machine ? machine : null;
    }

    // --- Setting its faces (from its screen) ---

    // Cycles a face (relative to its front) through the linked machine's port modes: SideConfig.ACTION_*. The cable's face
    // is locked, and nothing changes while it isn't linked.
    public void cycleFace(RelativeSide side, int action) {
        Direction face = side.toDirection(getFacing());
        LinkableMachine machine = linkedMachine();
        if (face == cableFace || machine == null) {
            return;
        }
        List<SideMode> modes = MachineLinks.modes(machine);
        int index = Math.max(0, modes.indexOf(faces.get(face)));
        SideMode next = switch (action) {
            case SideConfig.ACTION_NEXT -> modes.get((index + 1) % modes.size());
            case SideConfig.ACTION_PREVIOUS -> modes.get((index + modes.size() - 1) % modes.size());
            default -> SideMode.NONE;
        };
        setFace(face, next);
    }

    public void setFace(Direction face, SideMode mode) {
        if (face == cableFace || mode == SideMode.MACHINE_LINK || faces.get(face) == mode) {
            return;
        }
        faces = faces.with(face, mode);
        changed();
    }

    // Whether a face's mode is one the linked machine has.
    private boolean available(LinkableMachine machine, SideMode mode) {
        return mode != SideMode.NONE && mode != SideMode.MACHINE_LINK && machine.getAllowedSideModes().contains(mode);
    }

    // --- Capabilities ---

    // The handler on side: the linked machine's handler for that face's mode. Null on the cable's face, on faces set to
    // nothing (or to a mode the machine doesn't have), and while it isn't linked.
    public <T> @Nullable T handler(BlockCapability<T, @Nullable Direction> capability, @Nullable Direction side) {
        if (side == null || side == cableFace || link == null || level == null || level.isClientSide()) {
            return null;
        }
        LinkableMachine machine = linkedMachine();
        SideMode mode = faces.get(side);
        return machine != null && available(machine, mode) ? MachineLinks.handler(level, machine, link, capability, mode) : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        LinkableMachine machine = linkedMachine();
        SideMode mode = faces.get(side);
        if (side == cableFace || link == null || machine == null || !available(machine, mode)) {
            return ConnectionMode.NONE;
        }
        return MachineLinks.connection(machine, link, mode, type);
    }

    // --- Ticking ---

    public void requestTrace() {
        traceRequested = true;
    }

    // The linked machine is being removed: offer nothing until the next trace (which comes next tick).
    public void machineRemoved(LinkableMachine machine) {
        if (isLinkedTo(machine)) {
            link = null;
            traceRequested = true;
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
            }
        }
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (traceRequested || --traceWait <= 0) {
            traceRequested = false;
            traceWait = TRACE_INTERVAL;
            trace(level, pos);
        }
        LinkableMachine machine = linkedMachine();
        if (machine != null && link != null) {
            push(level, pos, machine, link);
        }
    }

    // Follows the cable from its face to the far end and links the machine there (or says why not).
    private void trace(ServerLevel level, BlockPos pos) {
        Direction cable = LinkConduitBlock.cableFace(level, pos);
        Set<BlockPos> newRun = new HashSet<>();
        Status newStatus;
        MachineLink newLink = null;
        LinkableMachine machine = null;
        if (cable == null) {
            newStatus = Status.NO_CABLE;
        } else {
            int max = ArcforgeConfig.LINK_CONDUIT_MAX_LENGTH.getAsInt();
            BlockPos at = pos.relative(cable);
            Direction from = cable.getOpposite();
            newStatus = Status.BROKEN;
            while (true) {
                if (!level.isLoaded(at)) {
                    // Part of the run is in an unloaded chunk: leave things as they are until it's back.
                    return;
                }
                BlockState state = level.getBlockState(at);
                if (!LinkConduitBlock.isCable(state)) {
                    break;
                }
                if (!newRun.add(at.immutable()) || newRun.size() > max) {
                    newStatus = newRun.size() > max ? Status.TOO_LONG : Status.BROKEN;
                    break;
                }
                Direction next = null;
                for (Direction side : Direction.values()) {
                    if (side != from && state.getValue(LinkConduitBlock.joint(side)) != LinkConduitBlock.Joint.NONE) {
                        next = side;
                        break;
                    }
                }
                if (next == null) {
                    break;
                }
                BlockPos across = at.relative(next);
                if (state.getValue(LinkConduitBlock.joint(next)) == LinkConduitBlock.Joint.PLUG) {
                    if (!level.isLoaded(across)) {
                        return;
                    }
                    machine = MachineLinks.machineAt(level, across, next.getOpposite());
                    if (machine == null) {
                        newStatus = Status.NO_MACHINE;
                    } else if (!ownerMayLink(level, across)) {
                        // It acts as its owner: it won't link a machine its owner couldn't use (see SecurityRules).
                        newStatus = Status.NO_ACCESS;
                    } else {
                        // A candidate: apply() claims it.
                        newLink = new MachineLink(across.immutable(), next.getOpposite(), pos.immutable());
                        newStatus = Status.LINKED;
                    }
                    break;
                }
                from = next.getOpposite();
                at = across;
            }
        }
        apply(level, pos, newStatus, cable, machine, newLink, newRun);
    }

    private boolean ownerMayLink(ServerLevel level, BlockPos target) {
        return SecurityRules.of(level, target).map(owned -> SecurityRules.canAccess(owner(), owned)).orElse(true);
    }

    // Takes on the trace's result. A LINKED result is a candidate: it claims the machine here, with its own state already
    // showing the link (so the machine sees it as linked while it refreshes), or ends up TAKEN.
    private void apply(ServerLevel level, BlockPos pos, Status newStatus, @Nullable Direction cable, @Nullable LinkableMachine machine,
            @Nullable MachineLink newLink, Set<BlockPos> newRun) {
        BlockPos newMachinePos = machine != null ? machine.getBlockPos().immutable() : null;
        // Let go of a machine it no longer links.
        if (machinePos != null && !machinePos.equals(newMachinePos)) {
            release(level);
        }
        Status oldStatus = status;
        BlockPos oldMachinePos = machinePos;
        MachineLink oldLink = link;
        if (newStatus == Status.LINKED && machine != null && newLink != null) {
            status = Status.LINKED;
            machinePos = newMachinePos;
            link = newLink;
            if (!machine.claimMachineLink(newLink)) {
                newStatus = Status.TAKEN;
            }
            status = oldStatus;
            machinePos = oldMachinePos;
            link = oldLink;
        }
        if (newStatus != Status.LINKED) {
            machine = null;
            newLink = null;
            newMachinePos = null;
        }
        Component name = machine != null ? machine.getDisplayName() : Component.empty();
        RelativeSide side = null;
        if (machine != null && newLink != null) {
            side = RelativeSide.fromDirection(MachineLinks.facing(machine), newLink.face());
        }
        int newDistance = newStatus == Status.LINKED ? newRun.size() : 0;
        boolean linkedNow = newStatus == Status.LINKED && (status != Status.LINKED || !Objects.equals(newMachinePos, machinePos));
        boolean changed = newStatus != status || cable != cableFace || !Objects.equals(newMachinePos, machinePos)
                || !Objects.equals(newLink, link) || !name.equals(machineName) || side != machineSide || newDistance != distance;
        status = newStatus;
        cableFace = cable;
        machinePos = newMachinePos;
        link = newLink;
        machineName = name;
        machineSide = side;
        distance = newDistance;
        light(level, newStatus == Status.LINKED ? newRun : Set.of());
        if (newStatus != Status.LINKED) {
            // A run it doesn't light (it may have been lit before a reload, or by a link that's gone) is dark.
            for (BlockPos at : newRun) {
                setCableLit(level, at, false);
            }
        }
        BlockState state = getBlockState();
        if (state.getValue(WiredMachineLinkBlock.LIT) != (newStatus == Status.LINKED)) {
            level.setBlock(pos, state.setValue(WiredMachineLinkBlock.LIT, newStatus == Status.LINKED), Block.UPDATE_CLIENTS);
        }
        if (changed) {
            changed();
        }
        if (linkedNow) {
            ArcforgeAdvancements.machineLinked(this, machine instanceof MultiblockController, newDistance);
        }
    }

    // Lights the run that's live and puts out what was lit before and isn't any more.
    private void light(ServerLevel level, Set<BlockPos> live) {
        for (BlockPos at : run) {
            if (!live.contains(at)) {
                setCableLit(level, at, false);
            }
        }
        for (BlockPos at : live) {
            setCableLit(level, at, true);
        }
        run.clear();
        run.addAll(live);
    }

    private static void setCableLit(Level level, BlockPos at, boolean lit) {
        if (!level.isLoaded(at)) {
            return;
        }
        BlockState state = level.getBlockState(at);
        if (LinkConduitBlock.isCable(state) && state.getValue(LinkConduitBlock.LIT) != lit) {
            level.setBlock(at, state.setValue(LinkConduitBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void release(Level level) {
        if (machinePos != null && level.isLoaded(machinePos) && level.getBlockEntity(machinePos) instanceof LinkableMachine machine) {
            machine.releaseMachineLink(worldPosition);
        }
    }

    // Its faces or link changed: redraw its port plates, reconnect conduits, and update the screen.
    private void changed() {
        setChanged();
        if (level == null || level.isClientSide()) {
            return;
        }
        PortStore.set(level, worldPosition, renderedFaces());
        level.invalidateCapabilities(worldPosition);
        ConduitBlock.refreshAround(level, worldPosition);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private PortFaces renderedFaces() {
        PortFaces shown = faces;
        if (cableFace != null) {
            shown = shown.with(cableFace, SideMode.MACHINE_LINK);
        }
        return shown;
    }

    // --- Pushing out, as the machine's ports would ---

    private void push(ServerLevel level, BlockPos pos, LinkableMachine machine, MachineLink link) {
        boolean eject = machine.isAutoEject() && level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0;
        for (Direction face : Direction.values()) {
            SideMode mode = faces.get(face);
            if (face == cableFace || !available(machine, mode) || isMachineBlock(machine, pos.relative(face))) {
                continue;
            }
            if (mode == SideMode.ENERGY || mode == SideMode.ENERGY_OUTPUT) {
                EnergyHandler source = MachineLinks.handler(level, machine, link, Capabilities.Energy.BLOCK, mode);
                EnergyHandler target = target(energyTargets, Capabilities.Energy.BLOCK, level, pos, face);
                if (source != null && target != null) {
                    EnergyHandlerUtil.move(source, target, Integer.MAX_VALUE, null);
                }
            } else if (mode == SideMode.HEAT) {
                HeatHandler source = MachineLinks.handler(level, machine, link, ModCapabilities.HEAT, mode);
                HeatHandler target = target(heatTargets, ModCapabilities.HEAT, level, pos, face);
                if (source != null && target != null && source.getTemperature() > target.getTemperature()) {
                    int offered = source.extractHeat(ArcforgeConfig.HEAT_CONTACT_RATE.getAsInt(), true);
                    int taken = offered > 0 ? target.receiveHeat(offered, true) : 0;
                    if (taken > 0) {
                        target.receiveHeat(source.extractHeat(taken, false), false);
                    }
                }
            }
            if (!eject) {
                continue;
            }
            if (mode.isOutput()) {
                ResourceHandler<ItemResource> items = MachineLinks.handler(level, machine, link, Capabilities.Item.BLOCK, mode);
                ResourceHandler<ItemResource> itemTarget = target(itemTargets, Capabilities.Item.BLOCK, level, pos, face);
                if (items != null && itemTarget != null) {
                    ResourceHandlerUtil.move(items, itemTarget, resource -> true, ITEMS_PER_FACE, null);
                }
            }
            if (mode.isOutput() || mode == SideMode.GAS_OUTPUT || mode == SideMode.HYDROGEN || mode == SideMode.OXYGEN) {
                ResourceHandler<FluidResource> fluid = MachineLinks.handler(level, machine, link, Capabilities.Fluid.BLOCK, mode);
                ResourceHandler<FluidResource> fluidTarget = target(fluidTargets, Capabilities.Fluid.BLOCK, level, pos, face);
                if (fluid != null && fluidTarget != null) {
                    ResourceHandlerUtil.move(fluid, fluidTarget, resource -> true, FLUID_PER_FACE, null);
                }
            }
        }
    }

    // Whether pos is the linked machine itself (or inside its structure): it never pushes back into the machine.
    private static boolean isMachineBlock(LinkableMachine machine, BlockPos pos) {
        return machine instanceof MultiblockController controller ? controller.isInside(pos) : machine.getBlockPos().equals(pos);
    }

    private static <H> @Nullable H target(Map<Direction, BlockCapabilityCache<H, @Nullable Direction>> caches,
            BlockCapability<H, @Nullable Direction> capability, ServerLevel level, BlockPos pos, Direction direction) {
        return caches.computeIfAbsent(direction, dir -> BlockCapabilityCache.create(capability, level, pos.relative(dir), dir.getOpposite()))
                .getCapability();
    }

    // --- Removal ---

    // Broken: the machine's face goes back to its own mode, the run goes dark, and its port plates go.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            release(server);
            for (BlockPos at : run) {
                setCableLit(server, at, false);
            }
            run.clear();
            status = Status.NO_CABLE;
            PortStore.set(server, pos, PortFaces.NONE);
        }
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WiredMachineLinkMenu(containerId, inventory, worldPosition);
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ownership.load(input);
        faces = input.read("faces", PortFaces.CODEC).orElse(PortFaces.NONE);
        status = input.read("status", Status.CODEC).orElse(Status.NO_CABLE);
        cableFace = input.read("cable_face", Direction.CODEC).orElse(null);
        machinePos = input.read("machine", BlockPos.CODEC).orElse(null);
        link = input.read("link", MachineLink.CODEC).orElse(null);
        machineName = input.read("machine_name", ComponentSerialization.CODEC).orElse(Component.empty());
        machineSide = input.getInt("machine_side").map(RelativeSide::byId).orElse(null);
        distance = input.getIntOr("distance", 0);
        traceRequested = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ownership.save(output);
        output.store("faces", PortFaces.CODEC, faces);
        output.store("status", Status.CODEC, status);
        if (cableFace != null) {
            output.store("cable_face", Direction.CODEC, cableFace);
        }
        if (machinePos != null) {
            output.store("machine", BlockPos.CODEC, machinePos);
        }
        if (link != null) {
            output.store("link", MachineLink.CODEC, link);
        }
        output.store("machine_name", ComponentSerialization.CODEC, machineName);
        if (machineSide != null) {
            output.putInt("machine_side", machineSide.ordinal());
        }
        output.putInt("distance", distance);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // For Jade: the faces set to something, relative to its front.
    public List<Map.Entry<RelativeSide, SideMode>> setFaces() {
        List<Map.Entry<RelativeSide, SideMode>> list = new ArrayList<>();
        for (RelativeSide side : RelativeSide.values()) {
            SideMode mode = getFaceMode(side);
            if (mode != SideMode.NONE) {
                list.add(Map.entry(side, mode));
            }
        }
        return list;
    }
}
