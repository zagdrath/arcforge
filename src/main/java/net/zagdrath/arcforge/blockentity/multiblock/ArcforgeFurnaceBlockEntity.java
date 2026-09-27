/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.List;

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
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
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
import net.zagdrath.arcforge.recipe.ArcforgeSmeltingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// The Arcforge Furnace, run from its port. Burning fuel (coal coke) heats the furnace towards its
// maximum; with no fuel burning it cools. A smelt (iron + coal coke -> steel + slag) only progresses
// while the furnace is at least as hot as the recipe needs. Coal coke is both the fuel and the reagent.
public class ArcforgeFurnaceBlockEntity extends BlockEntity implements MenuProvider, MultiblockController {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_BYPRODUCT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int AMBIENT_HEAT = 20;

    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.BYPRODUCT);
    private static final int STRUCTURE_CHECK_INTERVAL = 20;
    private static final int REDSTONE_CHECK_INTERVAL = 4;

    private final FilteredItemHandler items;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemByproduct;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final SideConfig sideConfig = new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.BYPRODUCT, SideMode.NONE);
    private final ContainerData data;

    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private boolean formed;
    private boolean checkRequested = true;
    private boolean powered;
    private int heat = AMBIENT_HEAT;
    private int burnTime;
    private int burnTotal;
    private int progress;
    private int progressTotal = ArcforgeSmeltingRecipe.DEFAULT_TIME;
    private int minHeat = ArcforgeSmeltingRecipe.DEFAULT_MIN_HEAT;

    public ArcforgeFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ARCFORGE_FURNACE.get(), pos, state);
        this.items = new FilteredItemHandler(SLOT_COUNT, (slot, resource) -> isItemValid(level, slot, resource), this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT || slot == SLOT_FUEL, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemByproduct = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_BYPRODUCT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT || slot == SLOT_FUEL,
                slot -> slot == SLOT_OUTPUT || slot == SLOT_BYPRODUCT);
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
                    default -> 0;
                };
            }
        };
    }

    // Iron goes in the input slot; coal coke (fuel and reagent) in the fuel slot. The client passes a
    // null level and checks against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        return switch (slot) {
            case SLOT_INPUT -> MachineRecipes.isArcforgeInput(level, stack);
            case SLOT_FUEL -> isFuel(stack) || MachineRecipes.isArcforgeReagent(level, stack);
            default -> false;
        };
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(ModItemTags.ARCFORGE_FURNACE_FUELS);
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

    // --- Ticking ---

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ArcforgeFurnaceBlockEntity furnace) {
        furnace.tick(level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (checkRequested || level.getGameTime() % STRUCTURE_CHECK_INTERVAL == 0) {
            checkRequested = false;
            updateFormed(level);
        }
        if (formed && level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }

        boolean enabled = formed && redstoneMode.canRun(powered);
        ArcforgeSmeltingRecipe recipe = enabled ? currentRecipe(level) : null;
        boolean canSmelt = recipe != null && productsFit(recipe);
        if (recipe != null) {
            progressTotal = recipe.time();
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
            if (heat >= recipe.minHeat() && ++progress >= recipe.time()) {
                smelt(recipe);
                progress = 0;
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

    private boolean isPowered(ServerLevel level) {
        for (BlockPos part : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            if (isPart(part) && level.hasNeighborSignal(part)) {
                return true;
            }
        }
        return false;
    }

    private @Nullable ArcforgeSmeltingRecipe currentRecipe(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        ItemStack reagent = items.getStack(SLOT_FUEL);
        if (input.isEmpty() || reagent.isEmpty()) {
            return null;
        }
        return MachineRecipes.arcforgeSmelting(level, input, reagent).map(RecipeHolder::value).orElse(null);
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

    // Burns one fuel item, keeping the last one back if the smelt still needs it as its reagent.
    private void burnFuel(ArcforgeSmeltingRecipe recipe) {
        ItemStack fuel = items.getStack(SLOT_FUEL);
        if (!isFuel(fuel) || (fuel.getCount() < 2 && recipe.reagent().test(fuel))) {
            return;
        }
        items.setStack(SLOT_FUEL, fuel.copyWithCount(fuel.getCount() - 1));
        burnTime = ArcforgeConfig.FURNACE_FUEL_BURN_TICKS.getAsInt();
        burnTotal = burnTime;
    }

    private void smelt(ArcforgeSmeltingRecipe recipe) {
        ItemStack input = items.getStack(SLOT_INPUT);
        ItemStack reagent = items.getStack(SLOT_FUEL);
        items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
        items.setStack(SLOT_FUEL, reagent.copyWithCount(reagent.getCount() - 1));
        addTo(SLOT_OUTPUT, recipe.result().create());
        recipe.byproduct().ifPresent(byproduct -> addTo(SLOT_BYPRODUCT, byproduct.create()));
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

    // Input faces route iron and coke to their own slots; output gives steel, by-product gives slag.
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

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
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
            if (formed) {
                MultiblockAutomation.refresh(level, getMinCorner(), getMaxCorner());
            }
        }
    }

    // --- Saving and syncing (clients need to know whether the furnace is formed) ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.deserialize(input.childOrEmpty("items"));
        sideConfig.deserialize(input);
        redstoneMode = RedstoneMode.byId(input.getIntOr("redstone_mode", 0));
        formed = input.getBooleanOr("formed", false);
        heat = input.getIntOr("heat", AMBIENT_HEAT);
        burnTime = input.getIntOr("burn_time", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output.child("items"));
        sideConfig.serialize(output);
        output.putInt("redstone_mode", redstoneMode.ordinal());
        output.putBoolean("formed", formed);
        output.putInt("heat", heat);
        output.putInt("burn_time", burnTime);
        output.putInt("burn_total", burnTotal);
        output.putInt("progress", progress);
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
