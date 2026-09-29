/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueOutput;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.Dismantleable;
import net.zagdrath.arcforge.machine.interaction.WrenchableMachine;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.block.machine.ArcQuarryBoundingBlock;
import net.zagdrath.arcforge.block.machine.ArcQuarryBlock;

// The Wrench. Its mode (Shift + mouse wheel, see WrenchMode) decides what right-clicking does:
//   Configure: a conduit side cycles through its settings (sneaking: backward); a multiblock rechecks its
//              structure (sneaking does nothing, so it can't be broken by accident); a Vault's front locks or
//              unlocks it.
//   Rotate:    a machine turns clockwise (sneaking: counter-clockwise).
//   Port:      a multiblock block on the outside of its structure says what port it is, and sneaking
//              cycles it through the modes the structure allows (see MultiblockPorts); a machine face
//              likewise; conduits as in Configure.
//   Dismantle: sneaking picks up a machine (keeping its contents), a conduit, or a block of a multiblock. A
//              conduit side with a Conduit Filter gives up the filter first.
// Runs from onItemUseFirst so it acts before the block's own interaction (such as opening a GUI); where the
// mode does nothing with a block, the click goes through to it.
public class WrenchItem extends Item {
    private enum Target { CONDUIT, MULTIBLOCK, GLASS, MACHINE, OTHER }

    public WrenchItem(Item.Properties properties) {
        super(properties);
    }

    public static WrenchMode mode(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.WRENCH_MODE.get(), WrenchMode.CONFIGURE);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        // An Arc Quarry's invisible parts act as its main block.
        if (state.getBlock() instanceof ArcQuarryBoundingBlock) {
            BlockPos main = ArcQuarryBoundingBlock.mainOf(level, pos);
            if (main != null && level.getBlockState(main).getBlock() instanceof ArcQuarryBlock) {
                pos = main;
                state = level.getBlockState(main);
            }
        }
        Player player = context.getPlayer();
        boolean sneaking = player != null && player.isSecondaryUseActive();
        Target target = targetOf(state);
        WrenchMode mode = mode(stack);
        // Configure on a Vault's front locks or unlocks it.
        if (mode == WrenchMode.CONFIGURE && VaultBlock.isFront(state, context.getClickedFace())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
                vault.setLocked(!vault.isLocked());
                tell(player, vault.isLocked()
                        ? Component.translatable("message.arcforge.vault.locked", vault.getTemplate().isEmpty()
                                ? Component.translatable("gui.arcforge.vault.next_item") : vault.getTemplate().getHoverName())
                        : Component.translatable("message.arcforge.vault.unlocked"));
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, 1.4F);
            }
            return InteractionResult.SUCCESS;
        }
        if (!acts(mode, target, sneaking, level.getBlockEntity(pos))) {
            return InteractionResult.PASS;
        }
        // The server does the work; the client only swings.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        InteractionResult result = switch (mode) {
            case CONFIGURE -> target == Target.CONDUIT ? ((ConduitBlock) state.getBlock()).useWrench(context)
                    : ((MultiblockPart) state.getBlock()).useWrench(context);
            case ROTATE -> rotate(level, pos, state, sneaking);
            case PORT -> switch (target) {
                case CONDUIT -> ((ConduitBlock) state.getBlock()).useWrench(context);
                case MACHINE -> machineSide(context, pos, state, sneaking);
                default -> port(context, state, sneaking);
            };
            case DISMANTLE -> switch (target) {
                case MACHINE -> state.getBlock() instanceof ArcQuarryBlock ? dismantleQuarry(level, pos, player) : dismantle(level, pos, state, player);
                case CONDUIT -> removeFilterOrBreak(context);
                default -> breakBlock(level, pos, player);
            };
        };
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, 1.4F);
        return result;
    }

    private static Target targetOf(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ConduitBlock) {
            return Target.CONDUIT;
        }
        if (block instanceof MultiblockPart) {
            return Target.MULTIBLOCK;
        }
        if (block instanceof PressureGlassBlock) {
            return Target.GLASS;
        }
        return block instanceof WrenchableMachine ? Target.MACHINE : Target.OTHER;
    }

    // Whether this mode does something with the block (otherwise the click passes to it).
    private static boolean acts(WrenchMode mode, Target target, boolean sneaking, @Nullable BlockEntity blockEntity) {
        return switch (mode) {
            case CONFIGURE -> target == Target.CONDUIT || target == Target.MULTIBLOCK && !sneaking;
            case ROTATE -> target == Target.MACHINE;
            case PORT -> target == Target.CONDUIT || target == Target.MULTIBLOCK || target == Target.GLASS
                    || target == Target.MACHINE && blockEntity instanceof ConfigurableMachine;
            case DISMANTLE -> sneaking && target != Target.OTHER;
        };
    }

    // --- Rotate ---

    private static InteractionResult rotate(Level level, BlockPos pos, BlockState state, boolean backward) {
        // Six-way machines (the Block Breaker and Placer) cycle through every direction.
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction[] all = Direction.values();
            Direction facing = state.getValue(BlockStateProperties.FACING);
            Direction next = all[(facing.ordinal() + (backward ? all.length - 1 : 1)) % all.length];
            level.setBlock(pos, state.setValue(BlockStateProperties.FACING, next), Block.UPDATE_ALL);
            level.invalidateCapabilities(pos);
            ConduitBlock.refreshAround(level, pos);
            return InteractionResult.SUCCESS;
        }
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return InteractionResult.PASS;
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        level.setBlock(pos, state.setValue(HorizontalDirectionalBlock.FACING, backward ? facing.getCounterClockWise() : facing.getClockWise()), Block.UPDATE_ALL);
        // Side configuration is relative to the front, so every face's capabilities just changed.
        level.invalidateCapabilities(pos);
        ConduitBlock.refreshAround(level, pos);
        return InteractionResult.SUCCESS;
    }

    // --- Port ---

    // A multiblock's port on the clicked face: says what it is, or (sneaking) sets it to the next mode.
    // Each face of a block is its own port.
    private static InteractionResult port(UseOnContext context, BlockState state, boolean change) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        MultiblockController controller = state.getBlock() instanceof MultiblockPart part ? part.findController(level, pos) : null;
        if (controller == null || !controller.isFormed() || !MultiblockPorts.canHold(level, controller, pos)
                || controller.isInside(pos.relative(face))) {
            tell(context.getPlayer(), Component.translatable("message.arcforge.wrench.not_port"));
            return InteractionResult.SUCCESS;
        }
        SideMode current = MultiblockPorts.get(level, pos, face);
        if (change) {
            SideMode next = MultiblockPorts.next(controller, current, false);
            MultiblockPorts.set(level, controller, pos, next, face);
            tell(context.getPlayer(), Component.translatable("message.arcforge.wrench.port_set", portName(controller, next)));
        } else {
            tell(context.getPlayer(), Component.translatable("message.arcforge.wrench.port", portName(controller, current)));
        }
        return InteractionResult.SUCCESS;
    }

    // A machine face's mode. An Energy face says which way the energy goes: consumers take it in, generators give it out.
    private static Component faceName(BlockEntity blockEntity, Direction face, SideMode mode) {
        if (mode == SideMode.ENERGY && blockEntity instanceof net.zagdrath.arcforge.conduit.ConduitConnectable connectable) {
            ConnectionMode connection = connectable.getConduitConnection(face, ConduitType.ENERGY);
            if (connection != ConnectionMode.NONE) {
                return energyName(connection == ConnectionMode.INPUT);
            }
        }
        return mode.getDescription();
    }

    private static Component energyName(boolean input) {
        return Component.translatable(input ? "gui.arcforge.side_mode.energy_input" : "gui.arcforge.side_mode.energy_output");
    }

    // A port's mode, with which way things go through it on this structure, e.g. "Naphtha (output)".
    private static Component portName(MultiblockController controller, SideMode mode) {
        boolean in = false, out = false;
        for (ConduitType type : ConduitType.values()) {
            ConnectionMode connection = controller.getConduitConnection(mode, type);
            in |= connection == ConnectionMode.INPUT;
            out |= connection == ConnectionMode.OUTPUT;
        }
        if (mode == SideMode.ENERGY && in != out) {
            return energyName(in);
        }
        if (mode == SideMode.NONE || in == out) {
            return mode.getDescription();
        }
        return Component.translatable(out ? "message.arcforge.wrench.port_out" : "message.arcforge.wrench.port_in", mode.getDescription());
    }

    // A machine face (the clicked one): says its side mode, or (sneaking) sets the next allowed one.
    private static InteractionResult machineSide(UseOnContext context, BlockPos pos, BlockState state, boolean change) {
        Level level = context.getLevel();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ConfigurableMachine machine)) {
            return InteractionResult.PASS;
        }
        RelativeSide side = RelativeSide.fromDirection(facingOf(blockEntity, state), context.getClickedFace());
        SideMode mode = machine.getSideMode(side);
        if (change) {
            List<SideMode> allowed = machine.getAllowedSideModes();
            int index = allowed.indexOf(mode);
            mode = allowed.get(index < 0 ? 0 : (index + 1) % allowed.size());
            machine.setSideMode(side, mode);
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            tell(context.getPlayer(), Component.translatable("message.arcforge.wrench.side_set", side.getDescription(),
                    faceName(blockEntity, context.getClickedFace(), mode)));
        } else {
            tell(context.getPlayer(), Component.translatable("message.arcforge.wrench.side", side.getDescription(),
                    faceName(blockEntity, context.getClickedFace(), mode)));
        }
        return InteractionResult.SUCCESS;
    }

    private static Direction facingOf(BlockEntity blockEntity, BlockState state) {
        if (blockEntity instanceof MachineBlockEntity machine) {
            return machine.getFacing();
        }
        if (blockEntity instanceof StorageBlockEntity storage) {
            return storage.getFacing();
        }
        return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    private static void tell(@Nullable Player player, Component message) {
        if (player != null) {
            player.sendOverlayMessage(message);
        }
    }

    // --- Dismantle ---

    // A conduit, or a block of a multiblock (which breaks the structure): broken with its drops.
    private static InteractionResult breakBlock(Level level, BlockPos pos, @Nullable Player player) {
        level.destroyBlock(pos, true, player);
        return InteractionResult.SUCCESS;
    }

    // A covered conduit gives up its cover first, then a filtered side its filter (settings kept on the item); the next
    // click breaks the conduit.
    private static InteractionResult removeFilterOrBreak(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof ConduitBlockEntity covered && covered.hasCover()) {
            covered.setCover(null);
            Block.popResource(level, pos, new ItemStack(ModItems.CONDUIT_COVER.get()));
            return InteractionResult.SUCCESS;
        }
        Direction side = ConduitBlock.sideAt(pos, context.getClickLocation());
        if (level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit && conduit.hasFilter(side)) {
            Block.popResource(level, pos, conduit.clearFilter(side));
            tell(context.getPlayer(), Component.translatable("message.arcforge.conduit_filter.removed"));
            return InteractionResult.SUCCESS;
        }
        return breakBlock(level, pos, context.getPlayer());
    }

    // Drops the machine as an item carrying its block entity data (fluid, energy, settings), so it can be
    // placed back unchanged. The items in its slots drop separately.
    // The Arc Quarry comes up with its settings (and name) but not its energy or progress; its buffer spills.
    private static InteractionResult dismantleQuarry(Level level, BlockPos pos, @Nullable Player player) {
        ItemStack drop = new ItemStack(ModItems.ARC_QUARRY.get());
        if (level.getBlockEntity(pos) instanceof ArcQuarryBlockEntity quarry) {
            drop.applyComponents(quarry.collectComponents());
        }
        level.removeBlock(pos, false);
        Block.popResource(level, pos, drop);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult dismantle(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
        ItemStack drop = new ItemStack(state.getBlock());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LogUtils.getLogger())) {
                TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
                blockEntity.saveCustomOnly(output);
                blockEntity.removeComponentsFromTag(output);
                // Slot items drop into the world when the block is removed (see Dismantleable).
                output.discard("items");
                BlockItem.setBlockEntityData(drop, blockEntity.getType(), output);
            }
            drop.applyComponents(blockEntity.collectComponents());
            if (blockEntity instanceof Dismantleable dismantleable) {
                dismantleable.markDismantled();
            }
        }
        level.removeBlock(pos, false);
        Block.popResource(level, pos, drop);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        WrenchMode mode = mode(stack);
        builder.accept(Component.translatable("tooltip.arcforge.wrench.mode", mode.displayName()));
        builder.accept(Component.translatable("tooltip.arcforge.wrench." + mode.getSerializedName()).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arcforge.wrench.scroll").withStyle(ChatFormatting.DARK_GRAY));
    }
}
