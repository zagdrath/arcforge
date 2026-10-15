/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.OilDerrickMenu;
import net.zagdrath.arcforge.multiblock.DerrickLayout;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.multiblock.ShapedPlant;
import net.zagdrath.arcforge.oil.OilCavern;
import net.zagdrath.arcforge.oil.OilCaverns;
import net.zagdrath.arcforge.oil.OilDeposits;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Oil Derrick (see OilfieldStructures.Rig), run by its controller. Built over an oil deposit (see OilDeposits), it
// drills down from its floor to the deposit (the top of its cavern under the bore, where the chunk was generated with
// caverns; else the depth the deposit lies at): a block every oilDerrick.ticksPerBlock ticks, using energyPerTick FE
// and waterPerTick mB of Water (drilling mud) a tick, and a Steel Pipe for every blocksPerPipe blocks of hole (taken as
// each length starts). With no deposit under it, it won't drill. When it reaches the oil it strikes it: the rig comes
// down, its blocks (and whatever it held) are handed back at the hole, and a Well Head caps it, ready for a Pumpjack;
// drilled into a gas cap, the Well Head is a gas well, for a Gas Wellhead. It does IO through its ports: Water and
// Steel Pipes in (Input), FE in (Energy).
public class OilDerrickBlockEntity extends MachineBlockEntity implements ShapedPlant {
    public static final int SLOT_PIPES = 0;
    public static final int MACHINE_SLOTS = 1;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.ENERGY);
    private static final int REDSTONE_CHECK_INTERVAL = 10;

    private OilfieldStructures.@Nullable Rig rig;
    private boolean checkRequested = true;
    private boolean powered;

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank water;
    private final ResourceHandler<FluidResource> waterInput;
    private final ResourceHandler<ItemResource> pipeInput;
    private final ContainerData data;

    // How deep the hole is (blocks), how deep it has to go, the ticks into the next block, and how many more blocks the
    // last pipe lines.
    private int depth;
    private int target;
    private int progress;
    private int lined;
    // The hole that depth is for (the bore of the rig last formed here).
    private @Nullable BlockPos bore;
    private boolean running;
    private int usage;
    // Gives the structure its first ports (see MultiblockPorts.Defaults).
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    public OilDerrickBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.OIL_DERRICK.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> isItemValid(slot, resource), UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE), SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.DERRICK_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.DERRICK_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.water = new FilteredFluidTank(ArcforgeConfig.DERRICK_WATER_CAPACITY.getAsInt(),
                resource -> resource.getFluid().defaultFluidState().is(FluidTags.WATER), this::setChanged);
        this.waterInput = new AutomationResourceHandler<>(water, index -> true, index -> false);
        this.pipeInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_PIPES, slot -> false);
        this.data = new WideIntContainerData(OilDerrickMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case OilDerrickMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case OilDerrickMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case OilDerrickMenu.DATA_USAGE -> usage;
                    case OilDerrickMenu.DATA_WATER -> water.getAmount();
                    case OilDerrickMenu.DATA_WATER_CAPACITY -> water.getCapacity();
                    case OilDerrickMenu.DATA_DEPTH -> depth;
                    case OilDerrickMenu.DATA_TARGET -> target;
                    case OilDerrickMenu.DATA_PROGRESS -> progress;
                    case OilDerrickMenu.DATA_TICKS_PER_BLOCK -> ArcforgeConfig.DERRICK_TICKS_PER_BLOCK.getAsInt();
                    case OilDerrickMenu.DATA_PIPES_NEEDED -> pipesNeeded();
                    case OilDerrickMenu.DATA_STATUS -> status.ordinal();
                    case OilDerrickMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case OilDerrickMenu.DATA_SIDE_CONFIG -> packedSides();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_PIPES && resource.is(ModItems.STEEL_PIPE.get());
    }

    // Steel Pipes still to come to line the rest of the hole (0 before it knows how deep it goes).
    public int pipesNeeded() {
        int perPipe = ArcforgeConfig.DERRICK_BLOCKS_PER_PIPE.getAsInt();
        int left = Math.max(0, target - depth - lined);
        return (left + perPipe - 1) / perPipe;
    }

    // --- Structure ---

    @Override
    public boolean isFormed() {
        return rig != null;
    }

    @Override
    public void requestCheck() {
        checkRequested = true;
    }

    @Override
    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            checkRequested = false;
            updateFormed(serverLevel);
        }
    }

    // Breaking the controller un-forms the rest of the rig (see BiogasDigesterBlockEntity).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && rig != null) {
            OilfieldStructures.unformRig(serverLevel, rig, pos);
            rig = null;
        }
    }

    private void updateFormed(ServerLevel level) {
        OilfieldStructures.Rig found = OilfieldStructures.findRig(level, worldPosition, rig);
        if (Objects.equals(found, rig)) {
            return;
        }
        if (rig != null) {
            OilfieldStructures.unformRig(level, rig, null);
        }
        // A rig built over another hole starts that hole from the top; one broken and rebuilt over the same hole
        // carries on.
        if (found != null && !found.bore().equals(bore)) {
            depth = 0;
            progress = 0;
            lined = 0;
            bore = found.bore();
        }
        rig = found;
        if (found != null) {
            OilfieldStructures.form(level, found.parts(), worldPosition, found.front(), found.min(), found.max());
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        running = false;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public OilfieldStructures.@Nullable Rig getRig() {
        return rig;
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return rig != null ? rig.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return rig != null ? rig.max() : worldPosition;
    }

    @Override
    public boolean isPart(BlockPos pos) {
        return rig != null && rig.contains(pos);
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        if (checkRequested) {
            checkNow();
        }
        usage = 0;
        if (rig == null) {
            status = MachineStatus.NOT_FORMED;
            setRunning(false);
            return;
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }
        status = drill(level);
        if (isRemoved()) {
            // It struck oil and came down.
            return;
        }
        setRunning(status == MachineStatus.DRILLING);
    }

    private MachineStatus drill(ServerLevel level) {
        OilDeposits.Deposit deposit = OilDeposits.at(level, rig.bore());
        if (deposit == null) {
            target = 0;
            return MachineStatus.NO_OIL;
        }
        // Over a cavern (in chunks generated with them), down to the top of the cavern under the bore: into the gas cap's
        // dome when it reaches the bore and still holds gas, which makes a gas well. Elsewhere, down to the deposit's
        // depth, as it always was.
        BlockPos bore = rig.bore();
        OilCavern cavern = OilCaverns.cavernAt(level, deposit, bore);
        int top = cavern != null ? cavern.topAt(bore.getX(), bore.getZ()) : Integer.MIN_VALUE;
        boolean gas = false;
        if (top != Integer.MIN_VALUE && top < bore.getY()) {
            target = Math.max(1, bore.getY() - top);
            gas = deposit.gasCap() && !cavern.holdsOil(top) && OilDeposits.get(level.getServer()).gasRemaining(deposit, level.getSeed()) > 0;
        } else {
            target = Math.max(1, bore.getY() - deposit.y());
        }
        if (depth >= target) {
            strike(level, gas);
            return MachineStatus.FINISHED;
        }
        if (!controlState.isEnabled() || !redstoneMode.canRun(powered)) {
            return stoppedStatus();
        }
        if (lined <= 0) {
            ItemStack pipes = items.getStack(SLOT_PIPES);
            if (pipes.isEmpty()) {
                return MachineStatus.NO_PIPE;
            }
        }
        int perTick = ArcforgeConfig.DERRICK_ENERGY_PER_TICK.getAsInt();
        if (energy.getAmountAsInt() < perTick) {
            return MachineStatus.NO_POWER;
        }
        int mud = ArcforgeConfig.DERRICK_WATER_PER_TICK.getAsInt();
        if (water.getAmount() < mud) {
            return MachineStatus.NO_WATER;
        }
        if (lined <= 0) {
            ItemStack pipes = items.getStack(SLOT_PIPES);
            items.setStack(SLOT_PIPES, pipes.copyWithCount(pipes.getCount() - 1));
            lined = ArcforgeConfig.DERRICK_BLOCKS_PER_PIPE.getAsInt();
        }
        energy.consume(perTick);
        usage = perTick;
        if (mud > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                water.extract(0, water.getResource(0), mud, tx);
                tx.commit();
            }
        }
        if (++progress >= ArcforgeConfig.DERRICK_TICKS_PER_BLOCK.getAsInt()) {
            progress = 0;
            depth++;
            lined--;
        }
        setChanged();
        return MachineStatus.DRILLING;
    }

    // The hole reaches the oil: the rig comes down and hands its blocks back at the hole, a Well Head caps it, and a
    // spurt of oil shows it. (The controller's slots drop as it goes, like any machine's.)
    private void strike(ServerLevel level, boolean gas) {
        OilfieldStructures.Rig struck = rig;
        if (struck == null) {
            return;
        }
        BlockPos bore = struck.bore();
        ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.CRUDE_OIL.get(), "struck_oil");
        if (gas) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.NATURAL_GAS.get(), "struck_gas");
        }
        for (ServerPlayer player : ArcforgeAdvancements.nearby(level, bore)) {
            player.sendSystemMessage(Component.translatable(gas ? "message.arcforge.oil_derrick.struck_gas" : "message.arcforge.oil_derrick.struck"));
        }
        rig = null;
        List<ItemStack> handedBack = new ArrayList<>();
        int casings = 0, frames = 0;
        for (BlockPos pos : struck.parts()) {
            if (pos.equals(worldPosition)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.DERRICK_FRAME.get())) {
                frames++;
            } else if (state.is(ModBlocks.DERRICK_CASING.get())) {
                casings++;
            }
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        handedBack.add(new ItemStack(ModItems.DERRICK_CASING.get(), casings));
        handedBack.add(new ItemStack(ModItems.DERRICK_FRAME.get(), frames));
        handedBack.add(new ItemStack(ModItems.OIL_DERRICK_CONTROLLER.get()));
        level.setBlock(bore, ModBlocks.WELL_HEAD.get().defaultBlockState().setValue(net.zagdrath.arcforge.block.multiblock.WellHeadBlock.GAS, gas),
                Block.UPDATE_ALL);
        for (ItemStack stack : handedBack) {
            if (!stack.isEmpty()) {
                Block.popResource(level, bore.above(), stack);
            }
        }
        level.playSound(null, bore, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 0.6F);
        level.sendParticles(gas ? ParticleTypes.CLOUD : ParticleTypes.SQUID_INK, bore.getX() + 0.5, bore.getY() + 1.2, bore.getZ() + 0.5, 40, 0.2, 1.2, 0.2, 0.15);
        // Last, the controller itself: its slots drop where it stood.
        level.setBlock(worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : rig.parts()) {
            if (level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    private void setRunning(boolean running) {
        if (this.running != running) {
            this.running = running;
            setLit(running);
        }
    }

    @Override
    protected void setLit(boolean lit) {
        if (rig != null || !lit) {
            super.setLit(lit);
        }
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public int getDepth() {
        return depth;
    }

    public int getTarget() {
        return target;
    }

    // FE it used last tick.
    public int getUsage() {
        return usage;
    }

    // Ticks into the block it's boring through.
    public int getProgress() {
        return progress;
    }

    public boolean isRunning() {
        return running;
    }

    // For the GameTests: the hole this deep already.
    public void setDepth(int depth) {
        this.depth = depth;
        setChanged();
    }

    // The rig and a little round it (the crown's gin pole and railing, the drill string in the hole), for its renderer.
    public AABB getRenderBox() {
        return rig != null ? AABB.encapsulatingFullBlocks(rig.min().below(), rig.max()).inflate(0.5) : new AABB(worldPosition);
    }

    // --- The model's frame and shapes (see DerrickLayout and PlantHitbox) ---

    public static ModelFrame frame(OilfieldStructures.Rig rig) {
        return ModelFrame.toward(rig.front().getOpposite(), OilfieldStructures.RIG_WIDTH, OilfieldStructures.RIG_HEIGHT, OilfieldStructures.RIG_WIDTH);
    }

    @Override
    public @Nullable ModelFrame plantFrame() {
        return rig != null ? frame(rig) : null;
    }

    // The blocks' shapes, by the way the rig faces.
    private static final java.util.Map<Direction, VoxelShape[]> SHAPES = new java.util.EnumMap<>(Direction.class);

    @Override
    public VoxelShape[] plantShapes() {
        OilfieldStructures.Rig shown = rig;
        if (shown == null) {
            return new VoxelShape[0];
        }
        synchronized (SHAPES) {
            return SHAPES.computeIfAbsent(shown.front(), front -> frame(shown).shapes(DerrickLayout.solids()));
        }
    }

    // The frame blocks the ladder runs through (see DerrickLayout.ladderBlocks), packed as x | y << 8 | u << 16.
    private static final java.util.Set<Integer> LADDER = new java.util.HashSet<>();

    static {
        for (int[] block : DerrickLayout.ladderBlocks()) {
            LADDER.add(block[0] | block[1] << 8 | block[2] << 16);
        }
    }

    // Whether the formed rig's ladder runs through the block at pos (so it can be climbed).
    public boolean isLadderAt(BlockPos pos) {
        OilfieldStructures.Rig shown = rig;
        if (shown == null || !shown.contains(pos)) {
            return false;
        }
        BlockPos min = shown.min();
        int[] block = frame(shown).frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
        return LADDER.contains(block[0] | block[1] << 8 | block[2] << 16);
    }

    // Where a player climbs the ladder (DerrickLayout.climbBoxes), in world blocks from the rig's minimum corner, by the
    // way the rig faces.
    private static final java.util.Map<Direction, java.util.List<AABB>> CLIMB = new java.util.EnumMap<>(Direction.class);

    // Whether box (a player's) is on the formed rig's ladder.
    public boolean onLadder(AABB box) {
        OilfieldStructures.Rig shown = rig;
        if (shown == null) {
            return false;
        }
        java.util.List<AABB> zones;
        synchronized (CLIMB) {
            zones = CLIMB.computeIfAbsent(shown.front(), front -> {
                ModelFrame frame = frame(shown);
                java.util.List<AABB> out = new java.util.ArrayList<>();
                for (float[] b : DerrickLayout.climbBoxes()) {
                    float[] p = frame.worldPoint(b[0], b[1], b[2]), q = frame.worldPoint(b[3], b[4], b[5]);
                    out.add(new AABB(p[0] / 16.0, p[1] / 16.0, p[2] / 16.0, q[0] / 16.0, q[1] / 16.0, q[2] / 16.0));
                }
                return out;
            });
        }
        AABB local = box.move(-shown.min().getX(), -shown.min().getY(), -shown.min().getZ());
        for (AABB zone : zones) {
            if (zone.intersects(local)) {
                return true;
            }
        }
        return false;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.INPUT ? pipeInput : null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.INPUT ? waterInput : null;
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case ITEM, FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        if (level != null && rig != null) {
            MultiblockAutomation.refresh(level, rig.min(), rig.max());
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        rig = input.getLong("rig_min").map(min -> new OilfieldStructures.Rig(BlockPos.of(min),
                Direction.from2DDataValue(input.getIntOr("rig_front", 0)))).orElse(null);
        energy.deserialize(input.childOrEmpty("energy"));
        water.deserialize(input.childOrEmpty("water"));
        depth = input.getIntOr("depth", 0);
        target = input.getIntOr("target", 0);
        progress = input.getIntOr("progress", 0);
        lined = input.getIntOr("lined", 0);
        bore = input.getLong("bore").map(BlockPos::of).orElse(rig != null ? rig.bore() : null);
        running = input.getBooleanOr("running", false);
        portDefaults.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (rig != null) {
            output.putLong("rig_min", rig.min().asLong());
            output.putInt("rig_front", rig.front().get2DDataValue());
        }
        energy.serialize(output.child("energy"));
        water.serialize(output.child("water"));
        output.putInt("depth", depth);
        output.putInt("target", target);
        output.putInt("progress", progress);
        output.putInt("lined", lined);
        if (bore != null) {
            output.putLong("bore", bore.asLong());
        }
        output.putBoolean("running", running);
        portDefaults.save(output);
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
        return Component.translatable("container.arcforge.oil_derrick");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new OilDerrickMenu(containerId, inventory, worldPosition, items, data);
    }
}
