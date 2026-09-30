/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.farming.LoamGrowth;
import net.zagdrath.arcforge.registry.ModItems;

// A Trellis: a treated-wood lattice, up to 2 high, that Hops climb. Stood on farmland (vanilla or Loam), the bottom one
// takes Hop Seeds: AGE 1 is a shoot, 2 a leafy vine, 3 a full vine, 4 a vine bearing Hop Cones. Once the bottom vine is
// full it climbs into the trellis above, which grows the same way. Using a bearing trellis picks the cones and leaves
// the vine at 3 to bear again: hops are perennial, never replanted. Growth needs light, is faster on moist farmland,
// and on Loam Farmland uses its nutrients like any crop (LoamGrowth), for the bottom vine.
public class TrellisBlock extends Block implements BonemealableBlock {
    public static final int BARE = 0, SHOOT = 1, FULL_VINE = 3, BEARING = 4;
    // Named "age" so LoamGrowth and Jade treat it like a crop's age.
    public static final IntegerProperty AGE = IntegerProperty.create("age", BARE, BEARING);
    // Standing on farmland, which is a pixel short of a full block: the model adds feet reaching down into it.
    public static final BooleanProperty ON_FARMLAND = BooleanProperty.create("on_farmland");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    private static final int MIN_LIGHT = 9;

    public TrellisBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, BARE).setValue(ON_FARMLAND, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, ON_FARMLAND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // --- Standing: on farmland or anything sturdy, or on one trellis that isn't itself on a trellis (2 high at most) ---

    public static boolean isTrellis(BlockState state) {
        return state.getBlock() instanceof TrellisBlock;
    }

    // Whether this trellis is the bottom one, on the ground rather than on another trellis.
    public static boolean isBottom(LevelReader level, BlockPos pos) {
        return !isTrellis(level.getBlockState(pos.below()));
    }

    // Whether the ground under a bottom trellis grows hops (any farmland).
    private static boolean onFarmland(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof FarmlandBlock;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (isTrellis(below)) {
            return !isTrellis(level.getBlockState(pos.below(2)));
        }
        return below.getBlock() instanceof FarmlandBlock || below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ON_FARMLAND, onFarmland(context.getLevel(), context.getClickedPos()));
    }

    // A trellis whose support is gone breaks (dropping what it holds); otherwise it follows what it stands on.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction == Direction.DOWN) {
            return state.canSurvive(level, pos) ? state.setValue(ON_FARMLAND, onFarmland(level, pos)) : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    // --- Planting and picking ---

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.HOP_SEEDS.get()) || state.getValue(AGE) != BARE || !isBottom(level, pos) || !onFarmland(level, pos)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(AGE, SHOOT), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) != BEARING) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            harvest(serverLevel, pos, state);
        }
        return InteractionResult.SUCCESS;
    }

    // Picks the cones (conesMin to conesMax) and leaves the full vine to bear again. Returns how many were picked.
    public static int harvest(ServerLevel level, BlockPos pos, BlockState state) {
        int min = ArcforgeConfig.HOP_CONES_MIN.getAsInt();
        int count = min + level.getRandom().nextInt(Math.max(0, ArcforgeConfig.HOP_CONES_MAX.getAsInt() - min) + 1);
        popResource(level, pos, new ItemStack(ModItems.HOP_CONES.get(), count));
        level.setBlock(pos, state.setValue(AGE, FULL_VINE), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
        return count;
    }

    // --- Growing ---

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) != BARE;
    }

    // Whether the vine here can grow: a bottom vine needs farmland under it, a top one a full vine under it.
    public static boolean canGrow(LevelReader level, BlockPos pos, BlockState state) {
        int age = state.getValue(AGE);
        if (age == BARE || age >= BEARING) {
            return false;
        }
        return isBottom(level, pos) ? onFarmland(level, pos) : level.getBlockState(pos.below()).getValue(AGE) >= FULL_VINE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) < MIN_LIGHT) {
            return;
        }
        if (canGrow(level, pos, state) && random.nextDouble() < growthChance(level, pos)) {
            grow(level, pos, state);
            if (isBottom(level, pos)) {
                LoamGrowth.onCropGrew(level, pos, random);
            }
        }
        climb(level, pos, level.getBlockState(pos));
    }

    // Moist farmland under the vine's bottom grows it at the full growthChance, dry at half.
    private static double growthChance(LevelReader level, BlockPos pos) {
        BlockPos ground = pos.below();
        while (isTrellis(level.getBlockState(ground))) {
            ground = ground.below();
        }
        BlockState soil = level.getBlockState(ground);
        boolean moist = soil.getBlock() instanceof FarmlandBlock && soil.getValue(FarmlandBlock.MOISTURE) > 0;
        return ArcforgeConfig.HOPS_GROWTH_CHANCE.getAsDouble() * (moist ? 1.0 : 0.5);
    }

    private static void grow(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(AGE, Math.min(BEARING, state.getValue(AGE) + 1)), Block.UPDATE_CLIENTS);
    }

    // A full bottom vine sends a shoot up into a bare trellis above it.
    private static void climb(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState above = level.getBlockState(pos.above());
        if (isTrellis(state) && state.getValue(AGE) >= FULL_VINE && isTrellis(above) && above.getValue(AGE) == BARE) {
            level.setBlock(pos.above(), above.setValue(AGE, SHOOT), Block.UPDATE_CLIENTS);
        }
    }

    // --- Bone meal: one stage, and the climb ---

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return canGrow(level, pos, state);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        grow(level, pos, state);
        climb(level, pos, level.getBlockState(pos));
    }
}
