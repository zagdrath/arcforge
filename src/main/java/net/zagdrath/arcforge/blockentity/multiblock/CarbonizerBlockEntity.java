/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.CarbonizerMenu;
import net.zagdrath.arcforge.multiblock.CarbonizerStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.recipe.CarbonizingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Every Carbonizer block has one of these. In a formed structure each points at the master (the left,
// bottom, front block), which runs one chamber per slice in parallel, holds the shared slots and the
// creosote buffer tank, and serves the GUI and capabilities for every block.
//
// The master's contents stay with that block when the structure breaks and come back when it re-forms.
// If a different block becomes master, the old one hands everything over (see absorb).
public class CarbonizerBlockEntity extends BlockEntity implements MenuProvider, FluidInteractable, MultiblockController {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_BUCKET_IN = 2;
    public static final int SLOT_BUCKET_OUT = 3;
    public static final int SLOT_COUNT = 4;
    public static final int MAX_CHAMBERS = 8;

    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.BYPRODUCT);
    private static final int REDSTONE_CHECK_INTERVAL = 4;

    private final FilteredItemHandler items;
    private final FilteredFluidTank tank;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ResourceHandler<FluidResource> fluidOutput;
    private final SideConfig sideConfig = new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.BYPRODUCT, SideMode.NONE);
    private final ContainerData data;

    // Each chamber holds the one input item it is baking.
    private final ItemStack[] chamberInput = new ItemStack[MAX_CHAMBERS];
    private final int[] chamberProgress = new int[MAX_CHAMBERS];
    private final int[] chamberTime = new int[MAX_CHAMBERS];

    private @Nullable BlockPos masterPos;
    private CarbonizerStructure.@Nullable Formation formation;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private boolean powered;
    // Whether this block holds a structure's contents (it is, or was, a master).
    private boolean holdsContents;

    // Client only: how far the slice door on this block is open (0 = shut, 1 = open), animated
    // towards the LIT state by clientTick and drawn by CarbonizerDoorRenderer.
    private float doorOpen;
    private float doorOpenPrevious;
    private boolean doorOpening;
    private boolean doorInitialized;

    public CarbonizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CARBONIZER.get(), pos, state);
        Arrays.fill(chamberInput, ItemStack.EMPTY);
        this.items = new FilteredItemHandler(SLOT_COUNT, (slot, resource) -> isItemValid(level, slot, resource), this::setChanged);
        this.tank = new FilteredFluidTank(minCapacity(), resource -> true, this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.fluidOutput = new AutomationResourceHandler<>(tank, index -> false, index -> true);
        this.data = new WideIntContainerData(CarbonizerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= CarbonizerMenu.DATA_CHAMBER_FIRST && index < CarbonizerMenu.DATA_CHAMBER_FIRST + MAX_CHAMBERS) {
                    return chamberDisplay(index - CarbonizerMenu.DATA_CHAMBER_FIRST);
                }
                return switch (index) {
                    case CarbonizerMenu.DATA_CHAMBERS -> formation != null ? formation.slices() : 0;
                    case CarbonizerMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case CarbonizerMenu.DATA_FLUID_AMOUNT -> tank.getAmount();
                    case CarbonizerMenu.DATA_FLUID_CAPACITY -> tank.getCapacity();
                    case CarbonizerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case CarbonizerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // Whether players and automation may put this item in a slot. The client passes a null level and
    // checks against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return switch (slot) {
            case SLOT_INPUT -> MachineRecipes.isCarbonizerInput(level, resource.toStack(1));
            case SLOT_BUCKET_IN -> ItemAccess.forStack(resource.toStack(1)).getCapability(Capabilities.Fluid.ITEM) != null;
            default -> false;
        };
    }

    private static int minCapacity() {
        return ArcforgeConfig.CARBONIZER_MIN_CREOSOTE_CAPACITY.getAsInt();
    }

    private static int capacityFor(int slices) {
        return Math.max(minCapacity(), slices * ArcforgeConfig.CARBONIZER_CREOSOTE_PER_SLICE.getAsInt());
    }

    // Permille progress of a working chamber, or -1 while it is idle or stalled.
    private int chamberDisplay(int chamber) {
        if (!isWorking(chamber)) {
            return -1;
        }
        return chamberProgress[chamber] * 1000 / Math.max(1, chamberTime[chamber]);
    }

    private boolean isWorking(int chamber) {
        return formation != null && chamber < formation.slices() && !chamberInput[chamber].isEmpty()
                && chamberProgress[chamber] < chamberTime[chamber] && redstoneMode.canRun(powered);
    }

    // --- Structure ---

    public void setMaster(BlockPos master) {
        masterPos = master.immutable();
        formation = null;
        setChanged();
    }

    public void clearMaster() {
        if (formation != null) {
            releaseChambers(0);
        }
        masterPos = null;
        formation = null;
        setChanged();
    }

    public void becomeMaster(CarbonizerStructure.Formation newFormation) {
        masterPos = worldPosition;
        formation = newFormation;
        holdsContents = true;
        tank.setCapacity(capacityFor(newFormation.slices()));
        releaseChambers(newFormation.slices());
        setChanged();
    }

    // Takes over the contents of a block that used to be a master.
    public void absorb(CarbonizerBlockEntity other) {
        if (!other.holdsContents || level == null) {
            return;
        }
        if (!holdsContents) {
            sideConfig.load(other.sideConfig.pack());
            redstoneMode = other.redstoneMode;
        }
        other.releaseChambers(0);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack leftover = insertOrMerge(slot, other.items.getStack(slot));
            other.items.setStack(slot, ItemStack.EMPTY);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), leftover);
        }
        FluidStack fluid = other.tank.getResource(0).toStack(other.tank.getAmount());
        if (!fluid.isEmpty() && (tank.getAmount() == 0 || tank.contains(fluid.getFluid()))) {
            tank.set(0, FluidResource.of(fluid), tank.getAmount() + fluid.getAmount());
            other.tank.set(0, FluidResource.EMPTY, 0);
        }
        other.holdsContents = false;
        other.setChanged();
        holdsContents = true;
        setChanged();
    }

    private ItemStack insertOrMerge(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = items.getStack(slot);
        if (current.isEmpty()) {
            items.setStack(slot, stack);
            return ItemStack.EMPTY;
        }
        if (!ItemStack.isSameItemSameComponents(current, stack)) {
            return stack;
        }
        int moved = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
        items.setStack(slot, current.copyWithCount(current.getCount() + moved));
        return stack.copyWithCount(stack.getCount() - moved);
    }

    // Returns the items of chambers from `from` onwards to the input slot (or drops them), e.g. when the
    // structure breaks or re-forms with fewer slices.
    private void releaseChambers(int from) {
        for (int chamber = from; chamber < MAX_CHAMBERS; chamber++) {
            releaseChamber(chamber);
        }
    }

    private void releaseChamber(int chamber) {
        if (!chamberInput[chamber].isEmpty()) {
            ItemStack leftover = insertOrMerge(SLOT_INPUT, chamberInput[chamber]);
            if (!leftover.isEmpty() && level != null) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), leftover);
            }
        }
        chamberInput[chamber] = ItemStack.EMPTY;
        chamberProgress[chamber] = 0;
        chamberTime[chamber] = 0;
    }

    // The master of the formed structure this block belongs to, or null.
    public @Nullable CarbonizerBlockEntity getFormedMaster() {
        if (masterPos == null || level == null) {
            return null;
        }
        if (masterPos.equals(worldPosition)) {
            return isFormed() ? this : null;
        }
        return level.isLoaded(masterPos) && level.getBlockEntity(masterPos) instanceof CarbonizerBlockEntity master && master.isFormed() ? master : null;
    }

    @Override
    public boolean isFormed() {
        return formation != null && CarbonizerBlock.isFormed(getBlockState());
    }

    public int getSlices() {
        return formation != null ? formation.slices() : 0;
    }

    @Override
    public Direction getStructureFacing() {
        return formation != null ? formation.facing() : getBlockState().getValue(CarbonizerBlock.FACING);
    }

    @Override
    public BlockPos getMinCorner() {
        return formation != null ? formation.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return formation != null ? formation.max() : worldPosition;
    }

    @Override
    public boolean isPart(BlockPos pos) {
        return isInside(pos);
    }

    // --- Door animation (client) ---

    public static final int DOOR_SWING_TICKS = 4;

    public static boolean hasDoor(BlockState state) {
        return CarbonizerBlock.isFormed(state) && state.getValue(CarbonizerBlock.DEPTH) == CarbonizerBlock.Depth.FRONT;
    }

    public static void clientTick(CarbonizerBlockEntity carbonizer, BlockState state) {
        boolean open = hasDoor(state) && state.getValue(CarbonizerBlock.LIT);
        float target = open ? 1.0F : 0.0F;
        // A door already open when the chunk loads doesn't swing.
        if (!carbonizer.doorInitialized) {
            carbonizer.doorInitialized = true;
            carbonizer.doorOpen = target;
        }
        carbonizer.doorOpenPrevious = carbonizer.doorOpen;
        carbonizer.doorOpening = open;
        float step = 1.0F / DOOR_SWING_TICKS;
        carbonizer.doorOpen = open ? Math.min(1.0F, carbonizer.doorOpen + step) : Math.max(0.0F, carbonizer.doorOpen - step);
    }

    // How far open the door is this frame, eased so it starts quickly and settles into place either way.
    public float getDoorOpen(float partialTick) {
        float linear = Mth.lerp(partialTick, doorOpenPrevious, doorOpen);
        return doorOpening ? easeOut(linear) : 1.0F - easeOut(1.0F - linear);
    }

    private static float easeOut(float t) {
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    // --- Ticking (the master of a formed structure only) ---

    public static void serverTick(ServerLevel level, CarbonizerBlockEntity carbonizer) {
        if (carbonizer.isFormed()) {
            carbonizer.tick(level);
        }
    }

    private void tick(ServerLevel level) {
        CarbonizerStructure.Formation formed = formation;
        if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level, formed);
        }
        tank.setCapacity(capacityFor(formed.slices()));
        fillContainer();

        boolean enabled = redstoneMode.canRun(powered);
        for (int chamber = 0; chamber < formed.slices(); chamber++) {
            if (enabled && chamberInput[chamber].isEmpty()) {
                startChamber(level, chamber);
            }
            boolean working = false;
            if (!chamberInput[chamber].isEmpty()) {
                if (enabled && chamberProgress[chamber] < chamberTime[chamber]) {
                    chamberProgress[chamber]++;
                    working = true;
                    setChanged();
                }
                if (chamberProgress[chamber] >= chamberTime[chamber]) {
                    // Stalls, door closed, until the output and the tank have room.
                    working &= finishChamber(level, chamber);
                }
            }
            setSliceLit(level, formed, chamber, working);
        }

        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this);
        }
    }

    private static boolean isPowered(ServerLevel level, CarbonizerStructure.Formation formed) {
        for (BlockPos pos : BlockPos.betweenClosed(formed.min(), formed.max())) {
            if (level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    // Loads one input item into an idle chamber, if its products would currently fit.
    private void startChamber(ServerLevel level, int chamber) {
        ItemStack input = items.getStack(SLOT_INPUT);
        if (input.isEmpty()) {
            return;
        }
        RecipeHolder<CarbonizingRecipe> recipe = MachineRecipes.carbonizing(level, input).orElse(null);
        if (recipe == null || !productsFit(recipe.value())) {
            return;
        }
        chamberInput[chamber] = input.copyWithCount(1);
        chamberProgress[chamber] = 0;
        chamberTime[chamber] = recipe.value().time();
        items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
    }

    // Moves a finished chamber's products out. Returns false if they don't fit yet (the chamber stalls).
    private boolean finishChamber(ServerLevel level, int chamber) {
        RecipeHolder<CarbonizingRecipe> recipe = MachineRecipes.carbonizing(level, chamberInput[chamber]).orElse(null);
        if (recipe == null) {
            // The recipe was removed by a datapack reload: give the item back.
            releaseChamber(chamber);
            return false;
        }
        CarbonizingRecipe value = recipe.value();
        if (!productsFit(value)) {
            return false;
        }
        ItemStack result = value.result().create();
        ItemStack output = items.getStack(SLOT_OUTPUT);
        items.setStack(SLOT_OUTPUT, output.isEmpty() ? result : output.copyWithCount(output.getCount() + result.getCount()));
        value.byproduct().ifPresent(fluid -> {
            try (Transaction tx = Transaction.openRoot()) {
                tank.insert(0, FluidResource.of(fluid.create()), fluid.amount(), tx);
                tx.commit();
            }
        });
        chamberInput[chamber] = ItemStack.EMPTY;
        chamberProgress[chamber] = 0;
        chamberTime[chamber] = 0;
        setChanged();
        return true;
    }

    private boolean productsFit(CarbonizingRecipe recipe) {
        ItemStack result = recipe.result().create();
        ItemStack output = items.getStack(SLOT_OUTPUT);
        boolean itemFits = output.isEmpty()
                || (ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize());
        if (!itemFits) {
            return false;
        }
        if (recipe.byproduct().isEmpty()) {
            return true;
        }
        FluidStack fluid = recipe.byproduct().get().create();
        try (Transaction tx = Transaction.openRoot()) {
            return tank.insert(0, FluidResource.of(fluid), fluid.getAmount(), tx) == fluid.getAmount();
        }
    }

    // Only the two front blocks look different while lit (their doors swing open), but the whole slice
    // carries the state. The slice's door clanks once as it opens or shuts.
    private static void setSliceLit(ServerLevel level, CarbonizerStructure.Formation formed, int chamber, boolean lit) {
        BlockPos[] slice = formed.slice(chamber);
        for (BlockPos pos : slice) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CarbonizerBlock && state.getValue(CarbonizerBlock.LIT) != lit) {
                level.setBlock(pos, state.setValue(CarbonizerBlock.LIT, lit), Block.UPDATE_CLIENTS);
                if (pos == slice[0]) {
                    level.playSound(null, pos.above(), lit ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS,
                            0.5F, 1.1F + level.getRandom().nextFloat() * 0.2F);
                }
            }
        }
    }

    // Fills the container in the bucket slot from the creosote tank and moves it to the slot below.
    private void fillContainer() {
        ItemStack input = items.getStack(SLOT_BUCKET_IN);
        if (input.isEmpty() || tank.getAmount() <= 0) {
            return;
        }
        ItemStacksResourceHandler scratch = new ItemStacksResourceHandler(1);
        scratch.set(0, ItemResource.of(input), 1);
        ResourceHandler<FluidResource> container = ItemAccess.forHandlerIndexStrict(scratch, 0).getCapability(Capabilities.Fluid.ITEM);
        if (container == null) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            if (ResourceHandlerUtil.move(tank, container, resource -> true, Integer.MAX_VALUE, tx) == 0) {
                return;
            }
            ItemStack result = scratch.getResource(0).toStack(scratch.getAmountAsInt(0));
            ItemStack output = items.getStack(SLOT_BUCKET_OUT);
            boolean fits = result.isEmpty() || output.isEmpty()
                    || (ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize());
            if (!fits) {
                return;
            }
            tx.commit();
            items.setStack(SLOT_BUCKET_IN, input.copyWithCount(input.getCount() - 1));
            if (!result.isEmpty()) {
                items.setStack(SLOT_BUCKET_OUT, output.isEmpty() ? result : output.copyWithCount(output.getCount() + result.getCount()));
            }
        }
    }

    // --- Configuration, changed by players through the menu ---

    @Override
    public SideMode getSideMode(RelativeSide side) {
        return sideConfig.get(side);
    }

    @Override
    public void setSideMode(RelativeSide side, SideMode mode) {
        if (sideConfig.get(side) != mode) {
            sideConfig.set(side, mode);
            onSideConfigChanged();
        }
    }

    @Override
    public void clearSideModes() {
        sideConfig.clear();
        onSideConfigChanged();
    }

    @Override
    public List<SideMode> getAllowedSideModes() {
        return SIDE_MODES;
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        redstoneMode = mode;
        setChanged();
    }

    private void onSideConfigChanged() {
        setChanged();
        if (level != null) {
            MultiblockAutomation.refresh(level, getMinCorner(), getMaxCorner());
        }
    }

    // --- Capabilities, served by the master for every block (see ModCapabilities) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    // Creosote leaves through by-product faces; nothing goes in.
    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.BYPRODUCT ? fluidOutput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.BYPRODUCT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    public @Nullable ResourceHandler<ItemResource> getItemCapability(@Nullable Direction side) {
        CarbonizerBlockEntity master = getFormedMaster();
        return master != null ? master.itemHandlerAt(worldPosition, side) : null;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidCapability(@Nullable Direction side) {
        CarbonizerBlockEntity master = getFormedMaster();
        return master != null ? master.fluidHandlerAt(worldPosition, side) : null;
    }

    // Held buckets take creosote from any block of the structure.
    @Override
    public @Nullable ResourceHandler<FluidResource> getInteractionFluidHandler() {
        CarbonizerBlockEntity master = getFormedMaster();
        return (master != null ? master : this).fluidOutput;
    }

    // --- Removal: the contents drop only when the block holding them is broken ---

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            releaseChambers(0);
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
            }
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        masterPos = input.read("master", BlockPos.CODEC).orElse(null);
        formation = input.read("formation", CarbonizerStructure.Formation.CODEC).orElse(null);
        holdsContents = input.getBooleanOr("holds_contents", false);
        items.deserialize(input.childOrEmpty("items"));
        tank.deserialize(input.childOrEmpty("creosote"));
        sideConfig.deserialize(input);
        redstoneMode = RedstoneMode.byId(input.getIntOr("redstone_mode", 0));
        Arrays.fill(chamberInput, ItemStack.EMPTY);
        int chamber = 0;
        for (ValueInput entry : input.childrenListOrEmpty("chambers")) {
            if (chamber >= MAX_CHAMBERS) {
                break;
            }
            chamberInput[chamber] = entry.read("item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            chamberProgress[chamber] = entry.getIntOr("progress", 0);
            chamberTime[chamber] = entry.getIntOr("time", 0);
            chamber++;
        }
        if (formation != null) {
            tank.setCapacity(capacityFor(formation.slices()));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("master", BlockPos.CODEC, masterPos);
        output.storeNullable("formation", CarbonizerStructure.Formation.CODEC, formation);
        output.putBoolean("holds_contents", holdsContents);
        items.serialize(output.child("items"));
        tank.serialize(output.child("creosote"));
        sideConfig.serialize(output);
        output.putInt("redstone_mode", redstoneMode.ordinal());
        ValueOutput.ValueOutputList chambers = output.childrenList("chambers");
        for (int chamber = 0; chamber < MAX_CHAMBERS; chamber++) {
            ValueOutput entry = chambers.addChild();
            entry.store("item", ItemStack.OPTIONAL_CODEC, chamberInput[chamber]);
            entry.putInt("progress", chamberProgress[chamber]);
            entry.putInt("time", chamberTime[chamber]);
        }
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.carbonizer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CarbonizerMenu(containerId, inventory, worldPosition, items, data);
    }
}
