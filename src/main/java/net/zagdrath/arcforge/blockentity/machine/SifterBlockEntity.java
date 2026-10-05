/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.tool.SifterMeshItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.SifterMenu;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.SiftingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Sieves gravel, sand, red sand and Deepslate Gravel with FE (arcforge:sifting recipes): each block sifted rolls every
// output of its recipe on its own, at the output's chance times the mesh's. It needs a Sifter Mesh in its mesh slot; the
// mesh also sets the speed, and wears by a point a block (meshWearChance) until it breaks. A block only starts when all of
// its possible outputs would fit. Input faces take the blocks and meshes (each into its own slot), output faces give the
// finds. Speed upgrades make it faster (drawing FE just as much faster), Energy upgrades cut the FE per block.
public class SifterBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_MESH = 1;
    public static final int FIRST_OUTPUT = 2;
    public static final int OUTPUT_SLOTS = 6;
    public static final int MACHINE_SLOTS = FIRST_OUTPUT + OUTPUT_SLOTS;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;

    public SifterBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: blocks and meshes in the top, finds out of the bottom, FE into the back.
        super(ModBlockEntityTypes.SIFTER.get(), pos, state, MACHINE_SLOTS, SifterBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.SIFTER_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.SIFTER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT || slot == SLOT_MESH, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, SifterBlockEntity::isOutputSlot);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT || slot == SLOT_MESH, SifterBlockEntity::isOutputSlot);
        this.data = new WideIntContainerData(SifterMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case SifterMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case SifterMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case SifterMenu.DATA_USAGE -> usage;
                    case SifterMenu.DATA_PROGRESS -> progress;
                    case SifterMenu.DATA_TOTAL -> total;
                    case SifterMenu.DATA_STATUS -> status.ordinal();
                    case SifterMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case SifterMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static boolean isOutputSlot(int slot) {
        return slot >= FIRST_OUTPUT && slot < MACHINE_SLOTS;
    }

    // The input slot takes what some sifting recipe takes; the mesh slot takes Sifter Meshes. The client passes a null
    // level and checks against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        if (slot == SLOT_MESH) {
            return SifterMeshItem.tierOf(resource.toStack(1)) != null;
        }
        return slot == SLOT_INPUT && MachineRecipes.isSifterInput(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t for this recipe: Speed draws it faster, Energy cuts it.
    public int energyPerTick(SiftingRecipe recipe) {
        return (int) Math.ceil(recipe.baseEnergyPerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // Ticks per block with this mesh: the mesh's speed, then Speed upgrades.
    public int ticksFor(SiftingRecipe recipe, SifterMeshItem.Tier mesh) {
        int base = Math.max(1, (int) Math.round(recipe.ticks() / mesh.speedMultiplier()));
        return UpgradeType.time(base, upgrades(UpgradeType.SPEED));
    }

    // An output's chance with this mesh, at most 1.
    public static double chance(SiftingRecipe.Output output, SifterMeshItem.Tier mesh) {
        return Math.min(1.0, output.chance() * mesh.chanceMultiplier());
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        if (!canRun(level)) {
            status = stoppedStatus();
        } else {
            status = work(level, pos);
        }
        setLit(status == MachineStatus.SIFTING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level, BlockPos pos) {
        ItemStack input = items.getStack(SLOT_INPUT);
        RecipeHolder<SiftingRecipe> holder = input.isEmpty() ? null : MachineRecipes.sifting(level, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            return MachineStatus.IDLE;
        }
        SiftingRecipe recipe = holder.value();
        SifterMeshItem.Tier mesh = SifterMeshItem.tierOf(items.getStack(SLOT_MESH));
        if (mesh == null) {
            return MachineStatus.NO_MESH;
        }
        total = ticksFor(recipe, mesh);
        if (!fitsAll(recipe)) {
            return MachineStatus.OUTPUT_FULL;
        }
        int draw = energyPerTick(recipe);
        if (draw > 0 && !energy.consume(draw)) {
            return MachineStatus.NO_POWER;
        }
        usage = draw;
        if (++progress >= total) {
            progress = 0;
            finish(level, pos, recipe, mesh);
        }
        setChanged();
        return MachineStatus.SIFTING;
    }

    // Whether every output the recipe can give would fit at once.
    private boolean fitsAll(SiftingRecipe recipe) {
        ItemStack[] slots = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            slots[i] = items.getStack(FIRST_OUTPUT + i).copy();
        }
        for (SiftingRecipe.Output output : recipe.outputs()) {
            if (!put(slots, output.item().create())) {
                return false;
            }
        }
        return true;
    }

    // Adds the stack to the slots (onto matching stacks first, then empty ones); false if it doesn't all fit.
    private static boolean put(ItemStack[] slots, ItemStack stack) {
        int left = stack.getCount();
        for (int i = 0; i < slots.length && left > 0; i++) {
            if (!slots[i].isEmpty() && ItemStack.isSameItemSameComponents(slots[i], stack)) {
                int moved = Math.min(left, slots[i].getMaxStackSize() - slots[i].getCount());
                slots[i].grow(moved);
                left -= moved;
            }
        }
        for (int i = 0; i < slots.length && left > 0; i++) {
            if (slots[i].isEmpty()) {
                int moved = Math.min(left, stack.getMaxStackSize());
                slots[i] = stack.copyWithCount(moved);
                left -= moved;
            }
        }
        return left <= 0;
    }

    private void finish(ServerLevel level, BlockPos pos, SiftingRecipe recipe, SifterMeshItem.Tier mesh) {
        ItemStack input = items.getStack(SLOT_INPUT);
        items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
        ItemStack[] slots = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            slots[i] = items.getStack(FIRST_OUTPUT + i).copy();
        }
        List<ItemStack> finds = new ArrayList<>();
        for (SiftingRecipe.Output output : recipe.outputs()) {
            if (level.getRandom().nextDouble() < chance(output, mesh)) {
                ItemStack found = output.item().create();
                put(slots, found);
                ArcforgeAdvancements.produced(this, found, null, "sifting");
                finds.add(found.copy());
            }
        }
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            items.setStack(FIRST_OUTPUT + i, slots[i]);
        }
        controlState.completed(finds, List.of(), 1, 0);
        // The mesh wears.
        ItemStack meshStack = items.getStack(SLOT_MESH);
        if (level.getRandom().nextDouble() < ArcforgeConfig.SIFTER_MESH_WEAR_CHANCE.getAsDouble()) {
            ItemStack worn = meshStack.copy();
            worn.setDamageValue(worn.getDamageValue() + 1);
            if (worn.getDamageValue() >= worn.getMaxDamage()) {
                items.setStack(SLOT_MESH, ItemStack.EMPTY);
                level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
            } else {
                items.setStack(SLOT_MESH, worn);
            }
        }
    }

    public int getUsage() {
        return usage;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

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
            case FLUID, GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.sifter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SifterMenu(containerId, inventory, worldPosition, items, data);
    }
}
