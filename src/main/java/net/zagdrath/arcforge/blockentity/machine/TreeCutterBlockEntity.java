/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ArcforgeFakePlayer;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.TreeCutterMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.security.SecurityRules;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Plants saplings and fells grown trees in an area in front of it, with FE.
//  - The area is a square on its own level, starting the block in front of it: radius treeCutter.radius (2, a 5x5) plus
//    rangePerUpgrade (1) for each Range upgrade, so eight make a 21x21.
//  - Every interval (40) ticks (faster with Speed upgrades) it does one thing: fells the first fully grown tree in the
//    area, or else plants one sapling from its sapling slots on an empty spot where it can grow. It plants in 2x2 patches
//    with a gap between them, so 2x2 spruce, jungle and dark oak saplings stand together and grow into big trees.
//  - A tree is a column of logs standing on its area with natural (not player-placed) leaves: it takes every log joined
//    to it (diagonals too, so branches and 2x2 trunks), up to maxLogsPerTree (a bigger one is left standing), and every
//    natural leaf round them. Logs drop as an axe would; leaves drop their saplings, sticks and apples, and themselves too
//    with collectLeaves. Saplings go back into its sapling slots first (so it replants), everything else into its 9
//    output slots. A tree whose drops won't all fit waits.
//  - Felling costs energyPerLog FE a log, planting energyPerPlant, both cut by Energy upgrades.
//  - Bone Meal in its fertilizer slot is used on a sapling in the area every fertilizeInterval ticks (faster with Speed).
// It works as its owner (it won't fell a tree its owner couldn't break) and as a fake player, so protection mods can
// refuse.
public class TreeCutterBlockEntity extends MachineBlockEntity {
    public static final int SAPLING_SLOTS = 3;
    public static final int SLOT_FERTILIZER = 3;
    public static final int FIRST_OUTPUT = 4;
    public static final int OUTPUT_SLOTS = 9;
    public static final int MACHINE_SLOTS = FIRST_OUTPUT + OUTPUT_SLOTS;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY, UpgradeType.RANGE);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);
    private static final ItemStack AXE = new ItemStack(Items.NETHERITE_AXE);
    // How many leaves a tree may have for each of its logs (a cap on the search, well above any vanilla tree).
    private static final int LEAVES_PER_LOG = 12;

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int cooldown;
    private int fertilizeCooldown;
    // Where the next sapling to fertilize is looked for from, so it works through the area.
    private int fertilizeCursor;
    private int usage;
    // The last tree felled (logs) and how many it has felled, for the GUI, Jade and tests.
    private int lastLogs;
    private int felled;

    public TreeCutterBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: top input (saplings and bone meal), bottom output, back energy.
        super(ModBlockEntityTypes.TREE_CUTTER.get(), pos, state, MACHINE_SLOTS, TreeCutterBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.TREE_CUTTER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.TREE_CUTTER_MAX_INPUT.getAsInt(), this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot <= SLOT_FERTILIZER, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot >= FIRST_OUTPUT && slot < MACHINE_SLOTS);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot <= SLOT_FERTILIZER,
                slot -> slot >= FIRST_OUTPUT && slot < MACHINE_SLOTS);
        this.data = new WideIntContainerData(TreeCutterMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case TreeCutterMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case TreeCutterMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case TreeCutterMenu.DATA_USAGE -> usage;
                    case TreeCutterMenu.DATA_SIZE -> radius() * 2 + 1;
                    case TreeCutterMenu.DATA_LAST_LOGS -> lastLogs;
                    case TreeCutterMenu.DATA_FELLED -> felled;
                    case TreeCutterMenu.DATA_STATUS -> status.ordinal();
                    case TreeCutterMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case TreeCutterMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // Saplings in the sapling slots, Bone Meal in the fertilizer slot; nothing goes into the outputs by hand.
    public static boolean isItemValid(int slot, ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        if (slot < SAPLING_SLOTS) {
            return isSapling(stack);
        }
        return slot == SLOT_FERTILIZER && stack.is(Items.BONE_MEAL);
    }

    public static boolean isSapling(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem;
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, TreeCutterBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    // --- The area ---

    public int radius() {
        return ArcforgeConfig.TREE_CUTTER_RADIUS.getAsInt() + ArcforgeConfig.TREE_CUTTER_RANGE_PER_UPGRADE.getAsInt() * upgrades(UpgradeType.RANGE);
    }

    // The middle of the area: radius + 1 blocks in front of it, on its own level.
    public BlockPos areaCentre() {
        return worldPosition.relative(getFacing(), radius() + 1);
    }

    // The area's ground-level positions, row by row.
    public List<BlockPos> areaPositions() {
        int r = radius();
        BlockPos centre = areaCentre();
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, 0, -r), centre.offset(r, 0, r))) {
            positions.add(pos.immutable());
        }
        return positions;
    }

    // Where it plants: 2x2 patches with a one-block gap between them (from the area's corner, every third row and column is
    // left empty), so 2x2 spruce, jungle and dark oak saplings stand together and grow into big trees, and neighbouring
    // trees don't grow into each other.
    public boolean isPlantingSpot(BlockPos pos) {
        int r = radius();
        BlockPos centre = areaCentre();
        int dx = pos.getX() - (centre.getX() - r);
        int dz = pos.getZ() - (centre.getZ() - r);
        return dx >= 0 && dz >= 0 && dx <= 2 * r && dz <= 2 * r && dx % 3 != 2 && dz % 3 != 2;
    }

    // --- Costs and timing ---

    private double energyMultiplier() {
        return UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY));
    }

    public int energyForLogs(int logs) {
        return (int) Math.ceil((long) logs * ArcforgeConfig.TREE_CUTTER_ENERGY_PER_LOG.getAsInt() * energyMultiplier());
    }

    public int energyPerPlant() {
        return (int) Math.ceil(ArcforgeConfig.TREE_CUTTER_ENERGY_PER_PLANT.getAsInt() * energyMultiplier());
    }

    private int interval(int base) {
        return UpgradeType.time(base, upgrades(UpgradeType.SPEED));
    }

    // --- Ticking ---

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
            setLit(false);
            autoEject(level, itemOutput);
            return;
        }
        if (--cooldown <= 0) {
            cooldown = interval(ArcforgeConfig.TREE_CUTTER_INTERVAL.getAsInt());
            status = act(level);
            setLit(status == MachineStatus.FELLING || status == MachineStatus.PLANTING);
        }
        if (--fertilizeCooldown <= 0) {
            fertilizeCooldown = interval(ArcforgeConfig.TREE_CUTTER_FERTILIZE_INTERVAL.getAsInt());
            fertilize(level);
        }
        autoEject(level, itemOutput);
    }

    // One action: fell the first grown tree, or else plant one sapling.
    private MachineStatus act(ServerLevel level) {
        List<BlockPos> area = areaPositions();
        int r = radius();
        BlockPos centre = areaCentre();
        boolean waitingOnPower = false;
        boolean waitingOnRoom = false;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos base : area) {
            if (seen.contains(base) || !isTreeBase(level, base)) {
                continue;
            }
            Tree tree = findTree(level, base, r, centre);
            if (tree == null) {
                continue;
            }
            seen.addAll(tree.logs());
            if (!ownerMayBreak(level, base)) {
                continue;
            }
            MachineStatus result = fell(level, base, tree);
            if (result == MachineStatus.FELLING) {
                return result;
            }
            waitingOnPower |= result == MachineStatus.NO_POWER;
            waitingOnRoom |= result == MachineStatus.OUTPUT_FULL;
        }
        if (waitingOnRoom) {
            return MachineStatus.OUTPUT_FULL;
        }
        if (waitingOnPower) {
            return MachineStatus.NO_POWER;
        }
        return plant(level, area);
    }

    // The bottom log of a trunk standing on the area: a log with no log under it.
    private static boolean isTreeBase(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.LOGS) && !level.getBlockState(pos.below()).is(BlockTags.LOGS);
    }

    public static boolean isNaturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT));
    }

    // A tree's blocks: its logs (bottom first) and its natural leaves.
    public record Tree(List<BlockPos> logs, List<BlockPos> leaves) {}

    // The tree whose trunk stands at base, or null if it isn't a grown tree (no natural leaves: a log post or a house) or
    // has more than maxLogsPerTree logs. Logs are joined on any side or diagonal, above the area's level, within the tree's
    // reach of the area; leaves are the natural leaves joined (on a side) to them or to each other, up to 7 steps out,
    // that are nearest to this tree: a leaf s steps out is only taken if its vanilla leaf distance is s, so leaves closer
    // to a neighbouring trunk stay on the neighbour (which would otherwise be left a bare post that never counts as a tree).
    public static @Nullable Tree findTree(Level level, BlockPos base, int radius, BlockPos centre) {
        int maxLogs = ArcforgeConfig.TREE_CUTTER_MAX_LOGS.getAsInt();
        int top = base.getY() + ArcforgeConfig.TREE_CUTTER_MAX_HEIGHT.getAsInt();
        // Branches and crowns may reach this far past the area.
        int reach = radius + 8;
        Set<BlockPos> logs = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        logs.add(base);
        queue.add(base);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
                if (next.getY() < base.getY() || next.getY() > top || Math.abs(next.getX() - centre.getX()) > reach
                        || Math.abs(next.getZ() - centre.getZ()) > reach || logs.contains(next)) {
                    continue;
                }
                if (!level.isLoaded(next) || !level.getBlockState(next).is(BlockTags.LOGS)) {
                    continue;
                }
                // A trunk standing on the ground beside it is another tree.
                if (next.getY() == base.getY() && !level.getBlockState(next.below()).is(BlockTags.LOGS) && !next.equals(base)
                        && !isTwoByTwo(base, next)) {
                    continue;
                }
                BlockPos found = next.immutable();
                logs.add(found);
                if (logs.size() > maxLogs) {
                    return null;
                }
                queue.add(found);
            }
        }
        // Natural leaves round the logs, by distance.
        Set<BlockPos> leaves = new LinkedHashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>(logs);
        int maxLeaves = logs.size() * LEAVES_PER_LOG;
        search:
        for (int step = 0; step < 7 && !frontier.isEmpty(); step++) {
            ArrayDeque<BlockPos> next = new ArrayDeque<>();
            for (BlockPos pos : frontier) {
                for (Direction direction : Direction.values()) {
                    BlockPos at = pos.relative(direction);
                    if (at.getY() < base.getY() || at.getY() > top + 8 || leaves.contains(at) || logs.contains(at) || !level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    if (isNaturalLeaves(state) && (!state.hasProperty(LeavesBlock.DISTANCE) || state.getValue(LeavesBlock.DISTANCE) == step + 1)) {
                        leaves.add(at);
                        next.add(at);
                        if (leaves.size() >= maxLeaves) {
                            break search;
                        }
                    }
                }
            }
            frontier = next;
        }
        return leaves.isEmpty() ? null : new Tree(new ArrayList<>(logs), new ArrayList<>(leaves));
    }

    // Whether two ground-level logs side by side or corner to corner belong to one 2x2 trunk.
    private static boolean isTwoByTwo(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getZ() - b.getZ()) <= 1;
    }

    // Fells the tree if it can pay for it and hold everything it drops.
    private MachineStatus fell(ServerLevel level, BlockPos base, Tree tree) {
        // Never more than a full buffer, so a tree bigger than the buffer pays (see maxLogsPerTree) still falls once it's full.
        int cost = Math.min(energyForLogs(tree.logs().size()), energy.getCapacityAsInt());
        if (energy.getAmountAsInt() < cost) {
            return MachineStatus.NO_POWER;
        }
        FakePlayer player = ArcforgeFakePlayer.at(level, worldPosition, getFacing(), AXE.copy());
        List<ItemStack> drops = new ArrayList<>();
        for (BlockPos pos : tree.leaves()) {
            BlockState state = level.getBlockState(pos);
            drops.addAll(Block.getDrops(state, level, pos, null));
            if (ArcforgeConfig.TREE_CUTTER_COLLECT_LEAVES.getAsBoolean()) {
                drops.add(new ItemStack(state.getBlock().asItem()));
            }
        }
        for (BlockPos pos : tree.logs()) {
            BlockState state = level.getBlockState(pos);
            drops.addAll(Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, AXE));
        }
        drops.removeIf(ItemStack::isEmpty);
        ItemStack[] slots = simulate(drops);
        if (slots == null) {
            return MachineStatus.OUTPUT_FULL;
        }
        BlockState baseState = level.getBlockState(base);
        if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, base, baseState, player)).isCanceled()) {
            return MachineStatus.CANNOT_BREAK;
        }
        if (!energy.consume(cost)) {
            return MachineStatus.NO_POWER;
        }
        usage = cost;
        level.levelEvent(2001, base, Block.getId(baseState));
        for (BlockPos pos : tree.leaves()) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        // Top down, so nothing above is left hanging for a tick.
        List<BlockPos> logs = new ArrayList<>(tree.logs());
        logs.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        for (BlockPos pos : logs) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (int i = 0; i < MACHINE_SLOTS; i++) {
            if (i != SLOT_FERTILIZER) {
                items.setStack(i, slots[i]);
            }
        }
        lastLogs = tree.logs().size();
        felled++;
        ArcforgeAdvancements.produced(this, new ItemStack(baseState.getBlock().asItem()), null, "tree_cutting");
        setChanged();
        return MachineStatus.FELLING;
    }

    // Where the drops would go: saplings into the sapling slots first (to replant), then everything into the outputs.
    // The slots after it, or null if they won't all fit. The fertilizer slot isn't touched.
    private ItemStack @Nullable [] simulate(List<ItemStack> drops) {
        ItemStack[] slots = new ItemStack[MACHINE_SLOTS];
        for (int i = 0; i < MACHINE_SLOTS; i++) {
            slots[i] = items.getStack(i).copy();
        }
        for (ItemStack drop : drops) {
            int left = drop.getCount();
            if (isSapling(drop)) {
                left = put(slots, 0, SAPLING_SLOTS, drop, left);
            }
            left = put(slots, FIRST_OUTPUT, MACHINE_SLOTS, drop, left);
            if (left > 0) {
                return null;
            }
        }
        return slots;
    }

    // Adds count of the drop to slots [from, to): onto matching stacks first, then into empty ones. Returns what's left.
    private static int put(ItemStack[] slots, int from, int to, ItemStack drop, int count) {
        for (int i = from; i < to && count > 0; i++) {
            if (!slots[i].isEmpty() && ItemStack.isSameItemSameComponents(slots[i], drop)) {
                int moved = Math.min(count, slots[i].getMaxStackSize() - slots[i].getCount());
                slots[i].grow(moved);
                count -= moved;
            }
        }
        for (int i = from; i < to && count > 0; i++) {
            if (slots[i].isEmpty()) {
                int moved = Math.min(count, drop.getMaxStackSize());
                slots[i] = drop.copyWithCount(moved);
                count -= moved;
            }
        }
        return count;
    }

    // Plants one sapling on the first empty spot of the area where it can grow.
    private MachineStatus plant(ServerLevel level, List<BlockPos> area) {
        int slot = -1;
        for (int i = 0; i < SAPLING_SLOTS; i++) {
            if (isSapling(items.getStack(i))) {
                slot = i;
                break;
            }
        }
        boolean room = false;
        for (BlockPos pos : area) {
            if (!isPlantingSpot(pos) || !level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                continue;
            }
            if (slot < 0) {
                // Nothing to plant: is there anywhere a sapling could go?
                room = true;
                break;
            }
            ItemStack stack = items.getStack(slot);
            BlockState sapling = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
            if (!sapling.canSurvive(level, pos)) {
                continue;
            }
            int cost = energyPerPlant();
            if (!energy.consume(cost)) {
                return MachineStatus.NO_POWER;
            }
            usage = cost;
            level.setBlock(pos, sapling, Block.UPDATE_ALL);
            level.playSound(null, pos, net.minecraft.world.level.block.SoundType.GRASS.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
            items.setStack(slot, stack.copyWithCount(stack.getCount() - 1));
            setChanged();
            return MachineStatus.PLANTING;
        }
        return room ? MachineStatus.NO_SAPLINGS : MachineStatus.GROWING_TREES;
    }

    // Uses one Bone Meal on the next sapling in the area that can take it, once its whole 2x2 patch is planted (so a
    // spruce, jungle or dark oak patch grows into one big tree rather than a small one, or nothing).
    private void fertilize(ServerLevel level) {
        ItemStack meal = items.getStack(SLOT_FERTILIZER);
        if (meal.isEmpty()) {
            return;
        }
        List<BlockPos> area = areaPositions();
        for (int i = 0; i < area.size(); i++) {
            int index = (fertilizeCursor + i) % area.size();
            BlockPos pos = area.get(index);
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.SAPLINGS) && state.getBlock() instanceof BonemealableBlock grower && isPatchPlanted(level, pos)
                    && grower.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION)) {
                if (grower.isBonemealSuccess(level, level.getRandom(), pos, state, BonemealSource.INTERACTION)) {
                    grower.performBonemeal(level, level.getRandom(), pos, state, BonemealSource.INTERACTION);
                }
                level.levelEvent(1505, pos, 15);
                items.setStack(SLOT_FERTILIZER, meal.copyWithCount(meal.getCount() - 1));
                fertilizeCursor = index + 1;
                setChanged();
                return;
            }
        }
    }

    // Whether every planting spot of pos's 2x2 patch (those inside the area) holds a sapling.
    private boolean isPatchPlanted(Level level, BlockPos pos) {
        int r = radius();
        BlockPos corner = areaCentre().offset(-r, 0, -r);
        int px = corner.getX() + (pos.getX() - corner.getX()) / 3 * 3;
        int pz = corner.getZ() + (pos.getZ() - corner.getZ()) / 3 * 3;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos spot = new BlockPos(px + dx, pos.getY(), pz + dz);
                if (isPlantingSpot(spot) && !level.getBlockState(spot).is(BlockTags.SAPLINGS)) {
                    return false;
                }
            }
        }
        return true;
    }

    // It acts as its owner: it won't fell a tree its owner couldn't break (see SecurityRules).
    private boolean ownerMayBreak(ServerLevel level, BlockPos target) {
        return SecurityRules.of(level, target).map(owned -> SecurityRules.canAccess(owner(), owned)).orElse(true);
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public int getLastLogs() {
        return lastLogs;
    }

    // Trees felled since it was loaded.
    public int getFelled() {
        return felled;
    }

    public int getUsage() {
        return usage;
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
        lastLogs = input.getIntOr("last_logs", 0);
        fertilizeCursor = input.getIntOr("fertilize_cursor", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("last_logs", lastLogs);
        output.putInt("fertilize_cursor", fertilizeCursor);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.tree_cutter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TreeCutterMenu(containerId, inventory, worldPosition, items, data);
    }
}
