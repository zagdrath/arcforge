/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.item.tool.SettingsCardData;
import net.zagdrath.arcforge.item.tool.SettingsCopyable;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.Ownership;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.ArcforgeFurnaceMenu;
import net.zagdrath.arcforge.multiblock.ArcforgeFurnaceStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.recipe.ArcforgeSmeltingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

import com.mojang.serialization.Codec;

// The Arcforge Furnace, run from its port. Burning fuel (coal coke) heats the furnace towards its
// maximum; with no fuel burning it cools. A smelt (metal, up to two additives and coal coke -> a result
// and slag, e.g. iron + coke -> steel, iron + gold + coke -> Wrought Alloy, steel + amethyst + nickel plate
// + coke -> Hardened Alloy; the two additives go in either additive slot) only progresses while the
// furnace is at least as hot as the recipe needs. Coal coke is both the fuel and the reagent: the furnace
// keeps back what the smelt needs. A coal coke block in the coke slot is broken open into nine loose coal
// coke when needed, which are burned and used before anything else in the slot. Oxygen piped into an Oxygen port
// speeds smelts up: one that starts with oxygenPerSmelt in the tank uses it and runs oxygenSpeedMultiplier times as
// fast.
public class ArcforgeFurnaceBlockEntity extends BlockEntity implements MenuProvider, MultiblockController, Owned, SettingsCopyable {
    // Who placed it and its security override (see SecurityRules).
    protected final Ownership ownership = new Ownership(this::setChanged);

    public static final int SLOT_METAL = 0;
    public static final int SLOT_ADDITIVE = 1;
    public static final int SLOT_ADDITIVE_2 = 2;
    public static final int SLOT_COKE = 3;
    public static final int SLOT_OUTPUT = 4;
    public static final int SLOT_BYPRODUCT = 5;
    public static final int SLOT_COUNT = 6;
    // Older saves had fewer slots: layout 1 (before the additive slot) iron, coke, output and by-product in
    // slots 0-3; layout 2 (one additive) metal, additive, coke, output and by-product in slots 0-4.
    private static final int SLOT_LAYOUT = 3;
    private static final int[][] OLD_LAYOUTS = {
            {},
            { SLOT_METAL, SLOT_COKE, SLOT_OUTPUT, SLOT_BYPRODUCT },
            { SLOT_METAL, SLOT_ADDITIVE, SLOT_COKE, SLOT_OUTPUT, SLOT_BYPRODUCT } };

    public static final int AMBIENT_HEAT = 20;
    // Coal coke in a coal coke block.
    private static final int BLOCK_COKE = 9;

    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.BYPRODUCT, SideMode.OXYGEN);
    private static final int STRUCTURE_CHECK_INTERVAL = 20;
    private static final int REDSTONE_CHECK_INTERVAL = 4;

    private final FilteredItemHandler items;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemByproduct;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final FilteredFluidTank oxygen;
    private final ResourceHandler<FluidResource> oxygenInput;
    private final SideConfig sideConfig = new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.BYPRODUCT, SideMode.NONE);
    private final ContainerData data;

    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private boolean formed;
    private boolean checkRequested = true;
    private boolean powered;
    private int heat = AMBIENT_HEAT;
    // Coal coke left from a coke block broken open in the fuel slot (0-8).
    private int looseCoke;
    private int burnTime;
    private int burnTotal;
    private int progress;
    private int progressTotal = ArcforgeSmeltingRecipe.DEFAULT_TIME;
    private int minHeat = ArcforgeSmeltingRecipe.DEFAULT_MIN_HEAT;
    // Whether the current (or next) smelt has paid for its oxygen boost. It stays paid for if the smelt is
    // interrupted, until one finishes.
    private boolean boosted;

    public ArcforgeFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ARCFORGE_FURNACE.get(), pos, state);
        this.items = new FilteredItemHandler(SLOT_COUNT, (slot, resource) -> isItemValid(level, slot, resource), this::setChanged);
        this.itemInput = new RoutedInput(items, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemByproduct = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_BYPRODUCT);
        this.itemAutomation = new RoutedInput(items, slot -> slot == SLOT_OUTPUT || slot == SLOT_BYPRODUCT);
        this.oxygen = new FilteredFluidTank(ArcforgeConfig.FURNACE_OXYGEN_CAPACITY.getAsInt(),
                resource -> resource.getFluid() == ModFluids.OXYGEN.get(), this::setChanged);
        this.oxygenInput = new AutomationResourceHandler<>(oxygen, index -> true, index -> false);
        this.data = new WideIntContainerData(ArcforgeFurnaceMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ArcforgeFurnaceMenu.DATA_FORMED -> formed ? 1 : 0;
                    case ArcforgeFurnaceMenu.DATA_HEAT -> heat;
                    case ArcforgeFurnaceMenu.DATA_MAX_HEAT -> ArcforgeConfig.FURNACE_MAX_HEAT.getAsInt();
                    case ArcforgeFurnaceMenu.DATA_MIN_HEAT -> minHeat;
                    case ArcforgeFurnaceMenu.DATA_PROGRESS -> progress;
                    case ArcforgeFurnaceMenu.DATA_PROGRESS_TOTAL -> progressTotal;
                    case ArcforgeFurnaceMenu.DATA_BURN_TIME -> burnTime;
                    case ArcforgeFurnaceMenu.DATA_BURN_TOTAL -> burnTotal;
                    case ArcforgeFurnaceMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ArcforgeFurnaceMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case ArcforgeFurnaceMenu.DATA_OXYGEN -> oxygen.getAmount();
                    case ArcforgeFurnaceMenu.DATA_BOOST -> boosted ? (int) Math.round(oxygenSpeed() * 100.0) : 0;
                    default -> 0;
                };
            }
        };
    }

    // Each slot takes its part of some recipe: a metal, an additive, or coal coke (fuel and reagent), so
    // input faces route each item to its own slot. The client passes a null level and checks against
    // the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        return switch (slot) {
            case SLOT_METAL -> MachineRecipes.isArcforgeMetal(level, stack);
            case SLOT_ADDITIVE, SLOT_ADDITIVE_2 -> MachineRecipes.isArcforgeAdditive(level, stack);
            case SLOT_COKE -> isFuel(stack);
            default -> false;
        };
    }

    private static boolean isInputSlot(int slot) {
        return slot == SLOT_METAL || slot == SLOT_ADDITIVE || slot == SLOT_ADDITIVE_2 || slot == SLOT_COKE;
    }

    // Where an item put in through an input port goes: coal coke to the coke slot, a recipe metal to the metal slot, and
    // an additive to the additive slot already holding it, else the first empty one. -1: nowhere. Coal Coke is a metal
    // too (it bakes into Graphite): it fills the coke slot first, and only what doesn't fit there goes on to the metal
    // slot, and only while that already holds Coal Coke (see RoutedInput). So steel lines keep feeding their fuel, and a
    // Graphite line, started by putting one coke in the metal slot by hand, keeps its fuel topped up too.
    private int routeSlot(ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        if (isFuel(stack)) {
            return SLOT_COKE;
        }
        if (MachineRecipes.isArcforgeMetal(level, stack)) {
            return SLOT_METAL;
        }
        if (!MachineRecipes.isArcforgeAdditive(level, stack)) {
            return -1;
        }
        for (int slot : new int[] { SLOT_ADDITIVE, SLOT_ADDITIVE_2 }) {
            if (ItemStack.isSameItemSameComponents(items.getStack(slot), stack)) {
                return slot;
            }
        }
        for (int slot : new int[] { SLOT_ADDITIVE, SLOT_ADDITIVE_2 }) {
            if (items.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    // Input ports: an insert without a slot goes where routeSlot says; inserts into a slot as the slot allows.
    private final class RoutedInput extends AutomationResourceHandler<ItemResource> {
        RoutedInput(ResourceHandler<ItemResource> delegate, java.util.function.IntPredicate canExtract) {
            super(delegate, ArcforgeFurnaceBlockEntity::isInputSlot, canExtract);
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            int slot = routeSlot(resource);
            if (slot < 0) {
                return 0;
            }
            int inserted = insert(slot, resource, amount, transaction);
            // Coal Coke that doesn't fit the coke slot tops up a metal slot already baking it into Graphite.
            if (slot == SLOT_COKE && inserted < amount && metalSlotTakes(resource)) {
                inserted += insert(SLOT_METAL, resource, amount - inserted, transaction);
            }
            return inserted;
        }
    }

    // Whether fuel that doesn't fit the coke slot may go on to the metal slot: it's a recipe metal (Coal Coke) and the
    // metal slot already holds it.
    public boolean metalSlotTakes(ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        return MachineRecipes.isArcforgeMetal(level, stack) && ItemStack.isSameItemSameComponents(items.getStack(SLOT_METAL), stack);
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(ModItemTags.ARCFORGE_FURNACE_FUELS);
    }

    private static boolean isCokeBlock(ItemStack stack) {
        return stack.is(ModItemTags.COAL_COKE_BLOCKS);
    }

    // What the coke slot offers as fuel and reagent: loose coke first, and a coke block counts as coal coke.
    private ItemStack fuelAndReagent() {
        ItemStack slot = items.getStack(SLOT_COKE);
        return looseCoke > 0 || isCokeBlock(slot) ? new ItemStack(ModItems.COAL_COKE.get()) : slot;
    }

    // How many fuel or reagent items are available, counting nine per coke block.
    private int fuelAndReagentCount() {
        ItemStack slot = items.getStack(SLOT_COKE);
        return looseCoke + (isCokeBlock(slot) ? slot.getCount() * BLOCK_COKE : slot.getCount());
    }

    // Uses up one fuel or reagent item: loose coke first, breaking open a coke block if that's what the slot holds.
    private void takeFuelOrReagent() {
        ItemStack slot = items.getStack(SLOT_COKE);
        if (looseCoke == 0 && isCokeBlock(slot)) {
            items.setStack(SLOT_COKE, slot.copyWithCount(slot.getCount() - 1));
            looseCoke = BLOCK_COKE;
        }
        if (looseCoke > 0) {
            looseCoke--;
            setChanged();
        } else {
            items.setStack(SLOT_COKE, slot.copyWithCount(slot.getCount() - 1));
        }
    }

    public FilteredItemHandler getItems() {
        return items;
    }

    public Direction getFacing() {
        return getBlockState().getValue(ArcforgeFurnacePortBlock.FACING);
    }

    // --- Structure ---

    @Override
    public boolean isFormed() {
        return formed;
    }

    // Recheck on the next tick (a nearby part was placed or removed).
    public void requestCheck() {
        checkRequested = true;
    }

    public void checkNow() {
        if (level != null && !level.isClientSide()) {
            checkRequested = false;
            updateFormed(level);
        }
    }

    private void updateFormed(Level level) {
        boolean valid = ArcforgeFurnaceStructure.isValid(level, worldPosition, getFacing());
        if (valid == formed) {
            return;
        }
        formed = valid;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        MultiblockAutomation.refresh(level, getMinCorner(), getMaxCorner());
        if (formed && level instanceof ServerLevel serverLevel) {
            MultiblockEffects.formed(serverLevel, getMinCorner(), getMaxCorner());
            ArcforgeAdvancements.formed(serverLevel, this, null);
        }
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return ArcforgeFurnaceStructure.minCorner(worldPosition, getFacing());
    }

    @Override
    public BlockPos getMaxCorner() {
        return ArcforgeFurnaceStructure.maxCorner(worldPosition, getFacing());
    }

    @Override
    public boolean isPart(BlockPos pos) {
        return ArcforgeFurnaceStructure.isPart(worldPosition, getFacing(), pos);
    }

    // Gives the structure its first ports (see MultiblockPorts.Defaults).
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    // --- Ticking ---

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ArcforgeFurnaceBlockEntity furnace) {
        furnace.tick(level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (checkRequested || level.getGameTime() % STRUCTURE_CHECK_INTERVAL == 0) {
            checkRequested = false;
            updateFormed(level);
        }
        portDefaults.tick(level, this);
        if (formed && level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }

        boolean enabled = formed && redstoneMode.canRun(powered);
        ArcforgeSmeltingRecipe recipe = enabled ? currentRecipe(level) : null;
        boolean canSmelt = recipe != null && productsFit(recipe) && fuelAndReagentCount() >= recipe.coke();
        if (recipe != null) {
            progressTotal = timeFor(recipe);
            minHeat = recipe.minHeat();
        }

        if (burnTime <= 0 && canSmelt) {
            burnFuel(recipe);
        }
        int previousHeat = heat;
        if (burnTime > 0 && formed) {
            burnTime--;
            heat = Math.min(ArcforgeConfig.FURNACE_MAX_HEAT.getAsInt(), heat + ArcforgeConfig.FURNACE_HEAT_GAIN.getAsInt());
        } else {
            heat = Math.max(AMBIENT_HEAT, heat - ArcforgeConfig.FURNACE_HEAT_LOSS.getAsInt());
        }

        if (canSmelt) {
            if (heat >= recipe.minHeat()) {
                if (progress == 0 && !boosted) {
                    boostWithOxygen();
                    progressTotal = timeFor(recipe);
                }
                if (++progress >= progressTotal) {
                    smelt(recipe);
                    progress = 0;
                    boosted = false;
                }
            }
        } else {
            progress = 0;
        }

        boolean lit = formed && burnTime > 0;
        if (state.getValue(ArcforgeFurnacePortBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(ArcforgeFurnacePortBlock.LIT, lit), Block.UPDATE_ALL);
        }

        if (formed && level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this);
        }
        if (heat != previousHeat || burnTime > 0 || progress > 0) {
            setChanged();
        }
    }

    // Pays for the starting smelt's boost, if there's enough oxygen.
    private void boostWithOxygen() {
        int needed = ArcforgeConfig.FURNACE_OXYGEN_PER_SMELT.getAsInt();
        if (oxygen.getAmount() < needed) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            oxygen.extract(0, oxygen.getResource(0), needed, tx);
            tx.commit();
        }
        boosted = true;
    }

    private static double oxygenSpeed() {
        return ArcforgeConfig.FURNACE_OXYGEN_SPEED.getAsDouble();
    }

    // Ticks the smelt takes: the recipe's time, divided by the oxygen speed-up if it's boosted.
    private int timeFor(ArcforgeSmeltingRecipe recipe) {
        return boosted ? Math.max(1, (int) Math.ceil(recipe.time() / oxygenSpeed())) : recipe.time();
    }

    public FilteredFluidTank getOxygen() {
        return oxygen;
    }

    public boolean isBoosted() {
        return boosted;
    }

    public int getProgress() {
        return progress;
    }

    public int getProgressTotal() {
        return progressTotal;
    }

    public int getHeat() {
        return heat;
    }

    // For tests: skip the warm-up.
    public void setHeat(int celsius) {
        heat = Math.max(AMBIENT_HEAT, Math.min(ArcforgeConfig.FURNACE_MAX_HEAT.getAsInt(), celsius));
        setChanged();
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos part : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            if (isPart(part) && level.hasNeighborSignal(part)) {
                return true;
            }
        }
        return false;
    }

    private @Nullable ArcforgeSmeltingRecipe currentRecipe(ServerLevel level) {
        ItemStack metal = items.getStack(SLOT_METAL);
        if (metal.isEmpty()) {
            return null;
        }
        return MachineRecipes.arcforgeSmelting(level, metal, items.getStack(SLOT_ADDITIVE), items.getStack(SLOT_ADDITIVE_2))
                .map(RecipeHolder::value).orElse(null);
    }

    private boolean productsFit(ArcforgeSmeltingRecipe recipe) {
        return fits(SLOT_OUTPUT, recipe.result().create())
                && recipe.byproduct().map(byproduct -> fits(SLOT_BYPRODUCT, byproduct.create())).orElse(true);
    }

    private boolean fits(int slot, ItemStack stack) {
        ItemStack current = items.getStack(slot);
        return current.isEmpty()
                || (ItemStack.isSameItemSameComponents(current, stack) && current.getCount() + stack.getCount() <= current.getMaxStackSize());
    }

    // Burns one coke, keeping back what the smelt still needs as its reagent.
    private void burnFuel(ArcforgeSmeltingRecipe recipe) {
        ItemStack fuel = fuelAndReagent();
        if (!isFuel(fuel) || fuelAndReagentCount() <= recipe.coke()) {
            return;
        }
        takeFuelOrReagent();
        burnTime = ArcforgeConfig.FURNACE_FUEL_BURN_TICKS.getAsInt();
        burnTotal = burnTime;
    }

    private void smelt(ArcforgeSmeltingRecipe recipe) {
        ItemStack metal = items.getStack(SLOT_METAL);
        items.setStack(SLOT_METAL, metal.copyWithCount(metal.getCount() - recipe.metal().count()));
        // Each additive comes out of the slot it matched.
        boolean swapped = recipe.swapped(new ArcforgeSmeltingRecipe.Input(metal, items.getStack(SLOT_ADDITIVE), items.getStack(SLOT_ADDITIVE_2)));
        recipe.additive().ifPresent(additive -> take(swapped ? SLOT_ADDITIVE_2 : SLOT_ADDITIVE, additive.count()));
        recipe.additive2().ifPresent(additive -> take(swapped ? SLOT_ADDITIVE : SLOT_ADDITIVE_2, additive.count()));
        for (int i = 0; i < recipe.coke(); i++) {
            takeFuelOrReagent();
        }
        addTo(SLOT_OUTPUT, recipe.result().create());
        recipe.byproduct().ifPresent(byproduct -> addTo(SLOT_BYPRODUCT, byproduct.create()));
    }

    private void take(int slot, int count) {
        ItemStack stack = items.getStack(slot);
        items.setStack(slot, stack.copyWithCount(stack.getCount() - count));
    }

    private void addTo(int slot, ItemStack stack) {
        ItemStack current = items.getStack(slot);
        items.setStack(slot, current.isEmpty() ? stack : current.copyWithCount(current.getCount() + stack.getCount()));
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
    public boolean isAutoEject() {
        return sideConfig.isAutoEject();
    }

    @Override
    public void setAutoEject(boolean autoEject) {
        sideConfig.setAutoEject(autoEject);
        setChanged();
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

    // --- Capabilities, served for every brick, wall and the port (see ModCapabilities) ---

    // Input faces route metal, additive and coke to their own slots; output gives the result, by-product gives slag.
    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            case BYPRODUCT -> itemByproduct;
            default -> null;
        };
    }

    // Oxygen ports fill the oxygen tank (and so does an unsided query); nothing comes back out.
    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.OXYGEN ? oxygenInput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        if (type == ConduitType.GAS) {
            return mode == SideMode.OXYGEN ? ConnectionMode.INPUT : ConnectionMode.NONE;
        }
        if (type != ConduitType.ITEM) {
            return ConnectionMode.NONE;
        }
        return switch (mode) {
            case INPUT -> ConnectionMode.INPUT;
            case OUTPUT, BYPRODUCT -> ConnectionMode.OUTPUT;
            default -> ConnectionMode.NONE;
        };
    }

    // --- Removal ---

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
            }
            if (looseCoke > 0) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(ModItems.COAL_COKE.get(), looseCoke));
            }
            if (formed) {
                MultiblockAutomation.refresh(level, getMinCorner(), getMaxCorner());
            }
        }
    }

    // --- Saving and syncing (clients need to know whether the furnace is formed) ---

    // --- Settings Card ---

    @Override
    public Identifier settingsKind() {
        return MachineSettings.kind(this);
    }

    @Override
    public Optional<SettingsCardData.StructureSize> settingsSize() {
        return isFormed() ? Optional.of(MachineSettings.Frame.of(this).size()) : Optional.empty();
    }

    @Override
    public void writeSettings(ValueOutput output) {
        MachineSettings.writeRedstone(output, redstoneMode);
        if (level != null && isFormed()) {
            MachineSettings.writePorts(output, level, this);
        }
        output.putBoolean("auto_eject", isAutoEject());
    }

    @Override
    public int readSettings(ValueInput input) {
        int skipped = MachineSettings.readRedstone(input, this);
        if (level != null && isFormed()) {
            skipped += MachineSettings.readPorts(input, level, this);
        }
        input.read("auto_eject", Codec.BOOL).ifPresent(this::setAutoEject);
        setChanged();
        return skipped;
    }

    @Override
    public List<Component> describe(ValueInput input) {
        List<Component> lines = new ArrayList<>();
        MachineSettings.describePorts(input, lines);
        MachineSettings.describeRedstone(input, lines);
        MachineSettings.describeAutoEject(input, lines);
        return lines;
    }

    @Override
    public Ownership ownership() {
        return ownership;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ownership.load(input);
        portDefaults.load(input);
        int layout = input.getIntOr("slot_layout", 1);
        if (layout >= SLOT_LAYOUT) {
            items.deserialize(input.childOrEmpty("items"));
        } else {
            // Older saves have fewer slots (see OLD_LAYOUTS): read them aside (loading replaces the whole
            // list) and move each to its slot now.
            int[] moveTo = OLD_LAYOUTS[Math.max(1, layout)];
            ItemStacksResourceHandler old = new ItemStacksResourceHandler(moveTo.length);
            old.deserialize(input.childOrEmpty("items"));
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                items.setStack(slot, ItemStack.EMPTY);
            }
            for (int i = 0; i < Math.min(old.size(), moveTo.length); i++) {
                items.setStack(moveTo[i], old.getResource(i).toStack(old.getAmountAsInt(i)));
            }
        }
        sideConfig.deserialize(input);
        redstoneMode = RedstoneMode.byId(input.getIntOr("redstone_mode", 0));
        formed = input.getBooleanOr("formed", false);
        heat = input.getIntOr("heat", AMBIENT_HEAT);
        burnTime = input.getIntOr("burn_time", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        progress = input.getIntOr("progress", 0);
        looseCoke = input.getIntOr("loose_coke", 0);
        oxygen.deserialize(input.childOrEmpty("oxygen"));
        boosted = input.getBooleanOr("boosted", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ownership.save(output);
        portDefaults.save(output);
        items.serialize(output.child("items"));
        output.putInt("slot_layout", SLOT_LAYOUT);
        sideConfig.serialize(output);
        output.putInt("redstone_mode", redstoneMode.ordinal());
        output.putBoolean("formed", formed);
        output.putInt("heat", heat);
        output.putInt("burn_time", burnTime);
        output.putInt("burn_total", burnTotal);
        output.putInt("progress", progress);
        output.putInt("loose_coke", looseCoke);
        oxygen.serialize(output.child("oxygen"));
        output.putBoolean("boosted", boosted);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.arcforge_furnace");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ArcforgeFurnaceMenu(containerId, inventory, worldPosition, items, data);
    }
}
