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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.AssemblerMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

import com.mojang.serialization.Codec;

// An automatic crafting table. A 3x3 pattern of ghost items (set in the GUI or with JEI's +) picks a crafting
// recipe; each craft takes one of each pattern item from its 18-slot buffer (which only takes pattern items) and
// puts the result in the output slot and any leftovers (empty buckets) in the two remainder slots, for
// energyPerCraft FE over craftTicks ticks. A craft whose result or leftovers wouldn't fit waits, and nothing is
// used until it all fits.
public class AssemblerBlockEntity extends MachineBlockEntity {
    public static final int FIRST_BUFFER = 0;
    public static final int BUFFER_SLOTS = 18;
    public static final int SLOT_OUTPUT = 18;
    public static final int FIRST_REMAINDER = 19;
    public static final int REMAINDER_SLOTS = 2;
    public static final int MACHINE_SLOTS = 21;
    public static final int PATTERN_SIZE = 9;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    // The ghost pattern (never real items) and what it crafts, shown in the GUI.
    private final Pattern pattern;
    private final SimpleContainer preview = new SimpleContainer(1);
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private @Nullable RecipeHolder<CraftingRecipe> recipe;
    private boolean recipeDirty = true;
    private int progress;
    private int usage;
    private int crafts;

    // The pattern's nine ghost cells; tells the machine when they change.
    private static final class Pattern extends SimpleContainer {
        private Runnable onChanged = () -> {};

        Pattern() {
            super(PATTERN_SIZE);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            onChanged.run();
        }
    }

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, new Pattern());
    }

    private AssemblerBlockEntity(BlockPos pos, BlockState state, Pattern pattern) {
        super(ModBlockEntityTypes.ASSEMBLER.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> isItemValid(pattern, slot, resource), UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.pattern = pattern;
        pattern.onChanged = () -> {
            recipeDirty = true;
            setChanged();
        };
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.ASSEMBLER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.ASSEMBLER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, AssemblerBlockEntity::isBuffer, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, AssemblerBlockEntity::isProduct);
        this.itemAutomation = new AutomationResourceHandler<>(items, AssemblerBlockEntity::isBuffer, AssemblerBlockEntity::isProduct);
        this.data = new WideIntContainerData(AssemblerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case AssemblerMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case AssemblerMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case AssemblerMenu.DATA_USAGE -> usage;
                    case AssemblerMenu.DATA_PROGRESS -> progress;
                    case AssemblerMenu.DATA_TOTAL -> craftTicks();
                    case AssemblerMenu.DATA_STATUS -> status.ordinal();
                    case AssemblerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case AssemblerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static boolean isBuffer(int slot) {
        return slot >= FIRST_BUFFER && slot < FIRST_BUFFER + BUFFER_SLOTS;
    }

    private static boolean isProduct(int slot) {
        return slot == SLOT_OUTPUT || slot >= FIRST_REMAINDER && slot < FIRST_REMAINDER + REMAINDER_SLOTS;
    }

    // The buffer takes only items the pattern uses; nothing else takes anything by hand.
    public static boolean isItemValid(SimpleContainer pattern, int slot, ItemResource resource) {
        if (!isBuffer(slot)) {
            return false;
        }
        for (int i = 0; i < pattern.getContainerSize(); i++) {
            ItemStack cell = pattern.getItem(i);
            if (!cell.isEmpty() && cell.is(resource.getItem())) {
                return true;
            }
        }
        return false;
    }

    // A client-side copy of the slots, for the menu; its buffer checks the pattern the menu shows.
    public static MachineItemHandler clientItems(SimpleContainer pattern) {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(pattern, slot, resource), UPGRADES, () -> {});
    }

    public SimpleContainer getPattern() {
        return pattern;
    }

    public SimpleContainer getPreview() {
        return preview;
    }

    // Sets one pattern cell to a copy of the stack (count 1), or clears it with EMPTY.
    public void setPatternCell(int cell, ItemStack stack) {
        if (cell >= 0 && cell < PATTERN_SIZE) {
            pattern.setItem(cell, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
    }

    // Sets the whole pattern (JEI), row by row; missing cells are cleared.
    public void setPattern(List<ItemStack> cells) {
        for (int i = 0; i < PATTERN_SIZE; i++) {
            setPatternCell(i, i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
        }
    }

    // The recipe the pattern makes, or null.
    public @Nullable RecipeHolder<CraftingRecipe> getRecipe() {
        return recipe;
    }

    public int craftTicks() {
        return UpgradeType.time(ArcforgeConfig.ASSEMBLER_CRAFT_TICKS.getAsInt(), upgrades(UpgradeType.SPEED));
    }

    // FE/t while crafting: Speed draws it faster, Energy cuts it, so a craft costs energyPerCraft x 0.8^n.
    public int energyPerTick() {
        return (int) Math.ceil((double) ArcforgeConfig.ASSEMBLER_ENERGY_PER_CRAFT.getAsInt() / ArcforgeConfig.ASSEMBLER_CRAFT_TICKS.getAsInt()
                * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    private List<ItemStack> patternCells() {
        List<ItemStack> cells = new ArrayList<>(PATTERN_SIZE);
        for (int i = 0; i < PATTERN_SIZE; i++) {
            cells.add(pattern.getItem(i).copy());
        }
        return cells;
    }

    private boolean patternEmpty() {
        return pattern.isEmpty();
    }

    private void resolveRecipe(ServerLevel level) {
        recipeDirty = false;
        if (patternEmpty()) {
            recipe = null;
        } else {
            CraftingInput input = CraftingInput.of(3, 3, patternCells());
            recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        }
        ItemStack result = recipe != null ? recipe.value().assemble(CraftingInput.of(3, 3, patternCells())) : ItemStack.EMPTY;
        if (!ItemStack.matches(result, preview.getItem(0))) {
            preview.setItem(0, result);
        }
        progress = 0;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        if (recipeDirty) {
            resolveRecipe(level);
        }
        if (!redstoneAllows(level)) {
            status = stoppedStatus();
        } else if (recipe == null) {
            status = MachineStatus.NO_PATTERN;
            progress = 0;
        } else if (craft(level, true) != MachineStatus.CRAFTING) {
            // Missing items, or the result or leftovers wouldn't fit: wait without using FE.
            status = craft(level, true);
            progress = 0;
        } else if (!energy.consume(energyPerTick())) {
            status = MachineStatus.NO_POWER;
        } else {
            usage = energyPerTick();
            status = MachineStatus.CRAFTING;
            if (++progress >= craftTicks()) {
                progress = 0;
                status = craft(level, false);
            }
            setChanged();
        }
        setLit(status == MachineStatus.CRAFTING);
        autoEject(level, itemOutput);
    }

    // The buffer after taking one of each pattern item, and the taken stacks in grid order; null if something is
    // missing. With simulate the buffer is left as it is.
    private @Nullable Taken take(boolean simulate) {
        List<ItemStack> buffer = new ArrayList<>(BUFFER_SLOTS);
        for (int i = 0; i < BUFFER_SLOTS; i++) {
            buffer.add(items.getStack(FIRST_BUFFER + i).copy());
        }
        List<ItemStack> taken = new ArrayList<>(PATTERN_SIZE);
        for (int cell = 0; cell < PATTERN_SIZE; cell++) {
            ItemStack wanted = pattern.getItem(cell);
            if (wanted.isEmpty()) {
                taken.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack found = ItemStack.EMPTY;
            for (ItemStack held : buffer) {
                if (!held.isEmpty() && matches(wanted, held)) {
                    found = held.copyWithCount(1);
                    held.shrink(1);
                    break;
                }
            }
            if (found.isEmpty()) {
                return null;
            }
            taken.add(found);
        }
        return new Taken(buffer, taken);
    }

    private record Taken(List<ItemStack> buffer, List<ItemStack> taken) {}

    // The same item; components only count if the pattern item has its own.
    private static boolean matches(ItemStack wanted, ItemStack held) {
        return wanted.getComponentsPatch().isEmpty() ? held.is(wanted.getItem()) : ItemStack.isSameItemSameComponents(wanted, held);
    }

    // One craft: takes the items, and only commits if the recipe still matches and everything fits. With simulate,
    // only says whether it would (CRAFTING) or why not.
    private MachineStatus craft(ServerLevel level, boolean simulate) {
        Taken taken = take(false);
        if (taken == null || recipe == null) {
            return MachineStatus.MISSING_ITEMS;
        }
        CraftingInput input = CraftingInput.of(3, 3, taken.taken());
        if (!recipe.value().matches(input, level)) {
            return MachineStatus.MISSING_ITEMS;
        }
        ItemStack result = recipe.value().assemble(input);
        List<ItemStack> leftovers = recipe.value().getRemainingItems(input).stream().filter(stack -> !stack.isEmpty()).toList();
        ItemStack[] output = { items.getStack(SLOT_OUTPUT).copy() };
        if (!merge(output, result)) {
            return MachineStatus.OUTPUT_FULL;
        }
        ItemStack[] remainders = { items.getStack(FIRST_REMAINDER).copy(), items.getStack(FIRST_REMAINDER + 1).copy() };
        for (ItemStack leftover : leftovers) {
            if (!merge(remainders, leftover)) {
                return MachineStatus.OUTPUT_FULL;
            }
        }
        if (simulate) {
            return MachineStatus.CRAFTING;
        }
        for (int i = 0; i < BUFFER_SLOTS; i++) {
            items.setStack(FIRST_BUFFER + i, taken.buffer().get(i));
        }
        items.setStack(SLOT_OUTPUT, output[0]);
        items.setStack(FIRST_REMAINDER, remainders[0]);
        items.setStack(FIRST_REMAINDER + 1, remainders[1]);
        crafts++;
        List<ItemStack> produced = new ArrayList<>(leftovers);
        produced.addFirst(result);
        controlState.completed(produced, List.of(), taken.taken().stream().filter(stack -> !stack.isEmpty()).count(), 0);
        return MachineStatus.CRAFTING;
    }

    // Adds `add` onto the non-empty `into` if it all fits (changing `into` in place, or returning false).
    private static boolean merge(ItemStack into, ItemStack add) {
        if (!ItemStack.isSameItemSameComponents(into, add) || into.getCount() + add.getCount() > into.getMaxStackSize()) {
            return false;
        }
        into.grow(add.getCount());
        return true;
    }

    // Into the first of the slots it fits in (merging first, then an empty one).
    private static boolean merge(ItemStack[] slots, ItemStack add) {
        if (add.isEmpty()) {
            return true;
        }
        for (ItemStack slot : slots) {
            if (!slot.isEmpty() && merge(slot, add)) {
                return true;
            }
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].isEmpty()) {
                slots[i] = add.copy();
                return true;
            }
        }
        return false;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public int getProgress() {
        return progress;
    }

    public int getUsage() {
        return usage;
    }

    // Crafts since it was loaded, for tests.
    public int getCrafts() {
        return crafts;
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

    // The Settings Card also copies the pattern (ghosts only, never items).
    @Override
    public void writeSettings(ValueOutput output) {
        super.writeSettings(output);
        output.store("pattern", ItemStack.OPTIONAL_CODEC.listOf(), patternCells());
    }

    @Override
    public int readSettings(ValueInput input) {
        input.read("pattern", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(cells -> {
            for (int i = 0; i < PATTERN_SIZE; i++) {
                setPatternCell(i, i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
            }
        });
        return super.readSettings(input);
    }

    @Override
    public List<Component> describe(ValueInput input) {
        List<Component> lines = new java.util.ArrayList<>(super.describe(input));
        input.read("pattern", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(cells -> lines.add(Component.translatable("settings.arcforge.pattern",
                cells.stream().filter(cell -> !cell.isEmpty()).count())));
        return lines;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        List<ItemStack> cells = input.read("pattern", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < PATTERN_SIZE; i++) {
            pattern.setItem(i, i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
        }
        progress = input.getIntOr("progress", 0);
        recipeDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.store("pattern", ItemStack.OPTIONAL_CODEC.listOf(), patternCells());
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.assembler");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AssemblerMenu(containerId, inventory, worldPosition, items, data, pattern, preview);
    }
}
