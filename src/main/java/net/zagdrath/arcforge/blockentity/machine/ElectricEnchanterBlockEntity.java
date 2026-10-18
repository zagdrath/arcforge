/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricEnchanterMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Enchants with FE, Lapis Lazuli and Liquid Experience. Its target (set in the GUI, copied by the Settings Card) is a
// set of enchantments and levels; each operation gives the item in its input slot every target enchantment it supports
// (by the enchantment's supported items, as the anvil checks) up to the target level, leaving out any that clash with
// one it has; a Book becomes an Enchanted Book. Each level gained costs Lapis equal to the level and the Liquid
// Experience the vanilla enchanting cost of that level is worth (its minimum cost in XP levels, as points from level 0,
// times the config multiplier, at LiquidExperience.MB_PER_POINT mB a point), so a top-up pays only for what it gains.
// An item with nothing to gain passes straight to the output. Lapis and Liquid Experience are drawn as the operation
// needs them and kept as credit if it changes, so no cost is ever too big for the tank. Treasure enchantments (Mending,
// the curses) are only offered with the config switch on.
public class ElectricEnchanterBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_LAPIS = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int MACHINE_SLOTS = 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);
    public static final String TARGET_KEY = "enchanter_target";
    public static final Codec<Map<ResourceKey<Enchantment>, Integer>> TARGET_CODEC =
            Codec.unboundedMap(ResourceKey.codec(Registries.ENCHANTMENT), Codec.intRange(1, 255));

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;
    // The target: enchantment to level, in the order they were set.
    private final Map<ResourceKey<Enchantment>, Integer> target = new LinkedHashMap<>();

    // The floating book, on the client.
    private final EnchanterBook book = new EnchanterBook();

    private int progress;
    private int total;
    private int usage;
    // Lapis and Liquid Experience (mB) already drawn toward the operation, kept as credit for the next if it changes.
    private int paidLapis;
    private int paidXp;
    // What the item in the input slot costs, for the GUI.
    private int lapisCost;
    private int xpCost;
    // The item the progress is on: another one starts over.
    private ItemStack working = ItemStack.EMPTY;

    public ElectricEnchanterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ELECTRIC_ENCHANTER.get(), pos, state, MACHINE_SLOTS, ElectricEnchanterBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.ENCHANTER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.ENCHANTER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.tank = new FilteredFluidTank(ArcforgeConfig.ENCHANTER_TANK_CAPACITY.getAsInt(), LiquidExperience::is, this::setChanged);
        this.fluidInput = new AutomationResourceHandler<>(tank, index -> true, index -> false);
        this.itemInput = new AutomationResourceHandler<>(items, ElectricEnchanterBlockEntity::isInputSlot, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, ElectricEnchanterBlockEntity::isInputSlot, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(ElectricEnchanterMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ElectricEnchanterMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ElectricEnchanterMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case ElectricEnchanterMenu.DATA_XP -> tank.getAmount();
                    case ElectricEnchanterMenu.DATA_XP_CAPACITY -> tank.getCapacity();
                    case ElectricEnchanterMenu.DATA_PROGRESS -> progress;
                    case ElectricEnchanterMenu.DATA_TOTAL -> total;
                    case ElectricEnchanterMenu.DATA_USAGE -> usage;
                    case ElectricEnchanterMenu.DATA_STATUS -> status.ordinal();
                    case ElectricEnchanterMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ElectricEnchanterMenu.DATA_SIDE_CONFIG -> packedSides();
                    case ElectricEnchanterMenu.DATA_LAPIS_COST -> lapisCost;
                    case ElectricEnchanterMenu.DATA_XP_COST -> xpCost;
                    case ElectricEnchanterMenu.DATA_TREASURE -> ArcforgeConfig.ENCHANTER_ALLOW_TREASURE.getAsBoolean() ? 1 : 0;
                    default -> 0;
                };
            }
        };
    }

    private static boolean isInputSlot(int slot) {
        return slot == SLOT_INPUT || slot == SLOT_LAPIS;
    }

    // The input takes Books, Enchanted Books and anything enchantable or damageable (what an anvil can enchant); the
    // Lapis slot takes Lapis Lazuli.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        return switch (slot) {
            case SLOT_INPUT -> isBook(stack) || stack.has(DataComponents.ENCHANTABLE) || stack.isDamageableItem() || stack.isEnchanted();
            case SLOT_LAPIS -> stack.is(Tags.Items.GEMS_LAPIS);
            default -> false;
        };
    }

    // A Book or an Enchanted Book: takes any enchantment.
    public static boolean isBook(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // --- The target ---

    // Whether the enchanter offers this enchantment at all (treasure ones only with the config switch on).
    public static boolean offered(Holder<Enchantment> enchantment, boolean treasure) {
        return treasure || !enchantment.is(EnchantmentTags.TREASURE);
    }

    // Whether this item can take this enchantment: books take any, other items those that list them as supported.
    public static boolean supports(ItemStack stack, Holder<Enchantment> enchantment) {
        return stack.isEmpty() || isBook(stack) || enchantment.value().canEnchant(stack);
    }

    // The enchantments the GUI lists: every one offered, or those the item in the input supports, sorted by name.
    public static List<Holder<Enchantment>> candidates(HolderLookup.Provider registries, ItemStack stack, boolean treasure) {
        List<Holder<Enchantment>> list = new ArrayList<>();
        registries.lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .filter(holder -> offered(holder, treasure) && supports(stack, holder))
                .forEach(list::add);
        list.sort(Comparator.comparing(holder -> holder.value().description().getString()));
        return list;
    }

    public Map<ResourceKey<Enchantment>, Integer> getTarget() {
        return target;
    }

    // Sets one enchantment's target level (0 clears it), up to its max level; any target it can't go with is dropped.
    // Returns whether it took it.
    public boolean setTarget(ResourceKey<Enchantment> key, int targetLevel) {
        if (level == null) {
            return false;
        }
        Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
        if (holder == null) {
            return false;
        }
        if (targetLevel <= 0) {
            target.remove(key);
        } else {
            if (!offered(holder, ArcforgeConfig.ENCHANTER_ALLOW_TREASURE.getAsBoolean())) {
                return false;
            }
            int clamped = Math.min(targetLevel, holder.value().getMaxLevel());
            target.entrySet().removeIf(entry -> !entry.getKey().equals(key) && resolve(entry.getKey())
                    .map(other -> !Enchantment.areCompatible(holder, other)).orElse(true));
            target.put(key, clamped);
        }
        targetChanged();
        return true;
    }

    private void setTargetMap(Map<ResourceKey<Enchantment>, Integer> map) {
        target.clear();
        target.putAll(map);
        targetChanged();
    }

    private void targetChanged() {
        progress = 0;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private Optional<Holder.Reference<Enchantment>> resolve(ResourceKey<Enchantment> key) {
        return level == null ? Optional.empty() : level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
    }

    // --- Costs ---

    // One enchantment raised to a level.
    public record Gain(Holder<Enchantment> enchantment, int level) {}

    // What an operation does to an item and costs: the gains, Lapis, and Liquid Experience in mB.
    public record Plan(List<Gain> gains, int lapis, int xp) {
        public boolean isEmpty() {
            return gains.isEmpty();
        }
    }

    // Lapis for one level: the level.
    public static int lapisForLevel(int level) {
        return level;
    }

    // Liquid Experience (mB) for one level of an enchantment: its vanilla enchanting cost in XP levels, as points.
    public static int xpForLevel(Holder<Enchantment> enchantment, int level) {
        int points = LiquidExperience.pointsForLevels(Math.max(1, enchantment.value().getMinCost(level)));
        return (int) Math.ceil(points * ArcforgeConfig.ENCHANTER_XP_MULTIPLIER.getAsDouble()) * LiquidExperience.MB_PER_POINT;
    }

    // What the target does to this item: each target enchantment it supports, doesn't clash with what it has, and isn't
    // at the target level yet; each level gained paid for once.
    public static Plan plan(ItemStack stack, List<Map.Entry<Holder<Enchantment>, Integer>> wanted) {
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        List<Gain> gains = new ArrayList<>();
        int lapis = 0;
        int xp = 0;
        for (Map.Entry<Holder<Enchantment>, Integer> entry : wanted) {
            Holder<Enchantment> enchantment = entry.getKey();
            int have = current.getLevel(enchantment);
            int want = Math.min(entry.getValue(), enchantment.value().getMaxLevel());
            if (have >= want || !supports(stack, enchantment)) {
                continue;
            }
            boolean clashes = current.keySet().stream().anyMatch(other -> !other.equals(enchantment) && !Enchantment.areCompatible(enchantment, other))
                    || gains.stream().anyMatch(gain -> !Enchantment.areCompatible(enchantment, gain.enchantment()));
            if (clashes) {
                continue;
            }
            for (int level = have + 1; level <= want; level++) {
                lapis += lapisForLevel(level);
                xp += xpForLevel(enchantment, level);
            }
            gains.add(new Gain(enchantment, want));
        }
        return new Plan(gains, lapis, xp);
    }

    private List<Map.Entry<Holder<Enchantment>, Integer>> resolvedTarget() {
        List<Map.Entry<Holder<Enchantment>, Integer>> list = new ArrayList<>();
        boolean treasure = ArcforgeConfig.ENCHANTER_ALLOW_TREASURE.getAsBoolean();
        target.forEach((key, lvl) -> resolve(key).filter(holder -> offered(holder, treasure)).ifPresent(holder -> list.add(Map.entry(holder, lvl))));
        return list;
    }

    // The enchanted item: a Book becomes an Enchanted Book, carrying the gains as stored enchantments.
    public static ItemStack apply(ItemStack input, Plan plan) {
        ItemStack result = input.is(Items.BOOK) ? input.transmuteCopy(Items.ENCHANTED_BOOK, 1) : input.copyWithCount(1);
        EnchantmentHelper.updateEnchantments(result, enchantments -> plan.gains().forEach(gain -> enchantments.set(gain.enchantment(), gain.level())));
        return result;
    }

    // --- Ticking ---

    // FE/t while enchanting: the config's FE per operation spread over its base time; Speed draws it faster, Energy
    // cuts it.
    public int energyPerTick() {
        double perTick = ArcforgeConfig.ENCHANTER_ENERGY_PER_OPERATION.getAsInt() / (double) ArcforgeConfig.ENCHANTER_TIME.getAsInt();
        return (int) Math.ceil(perTick * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        if (!canRun(level)) {
            status = stoppedStatus();
        } else {
            status = work();
        }
        setLit(status == MachineStatus.ENCHANTING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work() {
        ItemStack input = items.getStack(SLOT_INPUT);
        if (target.isEmpty()) {
            resetCosts();
            return MachineStatus.NO_TARGET;
        }
        if (input.isEmpty()) {
            resetCosts();
            return MachineStatus.IDLE;
        }
        if (!ItemStack.isSameItemSameComponents(input, working)) {
            working = input.copyWithCount(1);
            progress = 0;
        }
        Plan plan = plan(input, resolvedTarget());
        lapisCost = plan.lapis();
        xpCost = plan.xp();
        if (plan.isEmpty()) {
            // Nothing to gain: straight through.
            progress = 0;
            total = 0;
            return passThrough(input) ? MachineStatus.IDLE : MachineStatus.OUTPUT_FULL;
        }
        total = UpgradeType.time(ArcforgeConfig.ENCHANTER_TIME.getAsInt(), upgrades(UpgradeType.SPEED));
        if (!items.getStack(SLOT_OUTPUT).isEmpty()) {
            return MachineStatus.OUTPUT_FULL;
        }
        if (paidLapis < plan.lapis()) {
            ItemStack lapis = items.getStack(SLOT_LAPIS);
            int take = Math.min(plan.lapis() - paidLapis, lapis.getCount());
            if (take > 0) {
                items.setStack(SLOT_LAPIS, lapis.copyWithCount(lapis.getCount() - take));
                paidLapis += take;
            }
            if (paidLapis < plan.lapis()) {
                return MachineStatus.NO_LAPIS;
            }
        }
        if (paidXp < plan.xp()) {
            int take = Math.min(plan.xp() - paidXp, tank.getAmount());
            if (take > 0) {
                try (Transaction tx = Transaction.openRoot()) {
                    paidXp += tank.extract(0, LiquidExperience.resource(), take, tx);
                    tx.commit();
                }
            }
            if (paidXp < plan.xp()) {
                return MachineStatus.NO_EXPERIENCE;
            }
        }
        int fe = energyPerTick();
        if (!energy.consume(fe)) {
            return MachineStatus.NO_POWER;
        }
        usage = fe;
        progress++;
        if (progress >= total) {
            ItemStack result = apply(input, plan);
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
            items.setStack(SLOT_OUTPUT, result);
            paidLapis -= plan.lapis();
            paidXp -= plan.xp();
            controlState.completed(List.of(result.copy()), List.of(), 1 + plan.lapis(), plan.xp());
            progress = 0;
        }
        setChanged();
        return MachineStatus.ENCHANTING;
    }

    // Moves the input to the output as it is (as many as fit). Returns whether any moved.
    private boolean passThrough(ItemStack input) {
        ItemStack out = items.getStack(SLOT_OUTPUT);
        if (!out.isEmpty() && !ItemStack.isSameItemSameComponents(out, input)) {
            return false;
        }
        int room = out.isEmpty() ? input.getMaxStackSize() : out.getMaxStackSize() - out.getCount();
        int moved = Math.min(room, input.getCount());
        if (moved <= 0) {
            return false;
        }
        items.setStack(SLOT_OUTPUT, input.copyWithCount(out.getCount() + moved));
        items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - moved));
        setChanged();
        return true;
    }

    private void resetCosts() {
        progress = 0;
        total = 0;
        lapisCost = 0;
        xpCost = 0;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public int getUsage() {
        return usage;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    // Whether it is enchanting now (the book turns its pages), for the renderer.
    public boolean isWorking() {
        return getBlockState().getValue(MachineBlock.LIT);
    }

    public EnchanterBook book() {
        return book;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Input faces take items to enchant and Lapis; output faces give what's done.
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

    // Input faces take Liquid Experience; nothing drains it back out.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? fluidInput : null;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInput;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL, GAS -> ConnectionMode.NONE;
        };
    }

    // --- Settings Card: the target too ---

    @Override
    public void writeSettings(ValueOutput output) {
        super.writeSettings(output);
        output.store(TARGET_KEY, TARGET_CODEC, new LinkedHashMap<>(target));
    }

    @Override
    public int readSettings(ValueInput input) {
        input.read(TARGET_KEY, TARGET_CODEC).ifPresent(map -> {
            Map<ResourceKey<Enchantment>, Integer> kept = new LinkedHashMap<>();
            boolean treasure = ArcforgeConfig.ENCHANTER_ALLOW_TREASURE.getAsBoolean();
            map.forEach((key, lvl) -> resolve(key).filter(holder -> offered(holder, treasure))
                    .ifPresent(holder -> kept.put(key, Math.min(lvl, holder.value().getMaxLevel()))));
            setTargetMap(kept);
        });
        return super.readSettings(input);
    }

    @Override
    public List<Component> describe(ValueInput input) {
        List<Component> lines = new ArrayList<>(super.describe(input));
        input.read(TARGET_KEY, TARGET_CODEC).ifPresent(map -> lines.add(Component.translatable("settings.arcforge.enchanter_target", map.size())));
        return lines;
    }

    // --- Saving and the client copy (the target, for the GUI) ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        tank.deserialize(input.childOrEmpty("tank"));
        progress = input.getIntOr("progress", 0);
        paidLapis = input.getIntOr("paid_lapis", 0);
        paidXp = input.getIntOr("paid_xp", 0);
        target.clear();
        input.read(TARGET_KEY, TARGET_CODEC).ifPresent(target::putAll);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        tank.serialize(output.child("tank"));
        output.putInt("progress", progress);
        output.putInt("paid_lapis", paidLapis);
        output.putInt("paid_xp", paidXp);
        output.store(TARGET_KEY, TARGET_CODEC, new LinkedHashMap<>(target));
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
        return Component.translatable("container.arcforge.electric_enchanter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ElectricEnchanterMenu(containerId, inventory, worldPosition, items, data);
    }
}
