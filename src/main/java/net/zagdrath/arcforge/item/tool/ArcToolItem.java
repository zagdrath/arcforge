/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.menu.tool.ArcToolMenu;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.tag.ModBlockTags;

// The Arc Drill (pickaxe and shovel) and Arc Saw (axe), in three tiers. They never wear out but run on FE
// (ENERGY): every block costs FE (see cost), and with too little they don't mine. Up to four modules
// (TOOL_MODULES, installed in the tool's GUI, switched with sneak + scroll) add area and vein mining, Silk
// Touch, Fortune and speed; the Saw also fells whole trunks (FELLING). ArcToolEvents does the breaking.
public class ArcToolItem extends Item {
    public enum Kind {
        DRILL, SAW
    }

    public static final int BAR_COLOR = 0x5FD4C4;
    // FE for stripping a log or making a path.
    public static final int USE_COST = 50;
    // The speed each tier's tool component mines at (config arcToolSpeed rescales it; see ArcToolEvents).
    private static final float[] BASE_SPEED = { 8.0F, 10.0F, 14.0F };
    private static final float[] DRILL_ATTACK = { 4.0F, 5.0F, 6.0F };
    private static final float[] SAW_ATTACK = { 7.0F, 8.0F, 9.0F };

    // Set while felled logs after the first break, so they cost fellingFeMultiplier times as much.
    public static final ThreadLocal<Double> COST_MULTIPLIER = ThreadLocal.withInitial(() -> 1.0);

    private final ConduitTier tier;
    private final Kind kind;

    public ArcToolItem(ConduitTier tier, Kind kind, Item.Properties properties) {
        super(properties);
        this.tier = tier;
        this.kind = kind;
    }

    // The tool component, attack attributes and path making or stripping for a tier and kind.
    public static Item.Properties properties(Item.Properties properties, ConduitTier tier, Kind kind) {
        int index = JetpackItem.tierIndex(tier);
        HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        TagKey<Block> incorrect = tier == ConduitTier.TEMPERED ? BlockTags.INCORRECT_FOR_DIAMOND_TOOL : BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        TagKey<Block> mineable = kind == Kind.DRILL ? ModBlockTags.ARC_DRILL_MINEABLE : ModBlockTags.ARC_SAW_MINEABLE;
        float attack = (kind == Kind.DRILL ? DRILL_ATTACK : SAW_ATTACK)[index];
        float attackSpeed = kind == Kind.DRILL ? -2.8F : -3.0F;
        properties.stacksTo(1)
                .component(DataComponents.TOOL, new Tool(List.of(Tool.Rule.deniesDrops(blocks.getOrThrow(incorrect)),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(mineable), BASE_SPEED[index])), 1.0F, 0, true))
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attack - 1.0F, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build());
        return kind == Kind.DRILL
                ? properties.delayedComponent(DataComponents.BLOCK_TRANSFORMER, context -> context.getOrThrow(BlockTransformers.SHOVEL))
                : properties.delayedComponent(DataComponents.BLOCK_TRANSFORMER, context -> context.getOrThrow(BlockTransformers.AXE));
    }

    public ConduitTier tier() {
        return tier;
    }

    public Kind kind() {
        return kind;
    }

    public float baseSpeed() {
        return BASE_SPEED[JetpackItem.tierIndex(tier)];
    }

    public float configuredSpeed() {
        return (float) ArcforgeConfig.perTierDouble(ArcforgeConfig.ARC_TOOL_SPEED, JetpackItem.tierIndex(tier));
    }

    public int capacity() {
        return ArcforgeConfig.perTier(ArcforgeConfig.ARC_TOOL_CAPACITY, JetpackItem.tierIndex(tier));
    }

    public int receiveRate() {
        return ArcforgeConfig.perTier(ArcforgeConfig.ARC_TOOL_RECEIVE, JetpackItem.tierIndex(tier));
    }

    // Module slots the tier opens: 2, 3 or 4.
    public int moduleSlots() {
        return 2 + JetpackItem.tierIndex(tier);
    }

    // The Area module's reach: 1 (3x3), or 2 (5x5) on an Arcforged tool.
    public int areaRadius() {
        return tier == ConduitTier.ARCFORGED ? 2 : 1;
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ENERGY.get(), 0);
    }

    public static void setEnergy(ItemStack stack, int energy) {
        if (energy > 0) {
            stack.set(ModDataComponents.ENERGY.get(), energy);
        } else {
            stack.remove(ModDataComponents.ENERGY.get());
        }
    }

    // Takes up to `amount` FE, never going below 0.
    public static void spend(ItemStack stack, int amount) {
        setEnergy(stack, Math.max(0, energy(stack) - amount));
    }

    public static ToolModules modules(ItemInstance stack) {
        return stack.getOrDefault(ModDataComponents.TOOL_MODULES.get(), ToolModules.EMPTY);
    }

    public static boolean isFelling(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FELLING.get(), true);
    }

    // A full one, for the creative tab.
    public ItemStack charged() {
        ItemStack stack = new ItemStack(this);
        setEnergy(stack, capacity());
        return stack;
    }

    // FE to break a block: base x (1 + hardnessFactor x hardness), then x (1 + the modules' factors), each rounded
    // up; felled logs after the first are multiplied again.
    public static int cost(ItemStack stack, Level level, BlockPos pos, BlockState state) {
        double hardness = Math.max(0.0F, state.getDestroySpeed(level, pos));
        int base = Mth.ceil(ArcforgeConfig.ARC_TOOL_BASE_FE.getAsInt() * (1.0 + ArcforgeConfig.ARC_TOOL_HARDNESS_FACTOR.getAsDouble() * hardness));
        double factor = 1.0 + modules(stack).enabledTypes().stream().mapToDouble(ModuleType::feFactor).sum();
        return Mth.ceil(Mth.ceil(base * factor) * COST_MULTIPLIER.get());
    }

    // --- Mining ---

    // Empty, it mines nothing.
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return energy(stack) > 0 && super.isCorrectToolForDrops(stack, state);
    }

    // Each block breaks for its FE (free in creative); the tool itself never wears.
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (!level.isClientSide() && !(owner instanceof Player player && player.hasInfiniteMaterials())) {
            spend(stack, cost(stack, level, pos, state));
        }
        return true;
    }

    // Stripping (Saw) or path making (Drill) costs USE_COST and doesn't work without it. Sneaking with the Saw
    // toggles Felling instead.
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (kind == Kind.SAW && player != null && player.isSecondaryUseActive()) {
            return toggleFelling(context.getItemInHand(), player);
        }
        ItemStack stack = context.getItemInHand();
        boolean free = player != null && player.hasInfiniteMaterials();
        if (!free && energy(stack) < USE_COST) {
            return InteractionResult.PASS;
        }
        InteractionResult result = super.useOn(context);
        if (result.consumesAction() && !context.getLevel().isClientSide() && !free) {
            spend(stack, USE_COST);
        }
        return result;
    }

    // In the air: opens the module GUI (sneaking with the Saw toggles Felling).
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (kind == Kind.SAW && player.isSecondaryUseActive()) {
            return toggleFelling(stack, player);
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = serverPlayer.getInventory().getSelectedSlot();
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ArcToolMenu(id, inventory, slot), stack.getHoverName()),
                    buf -> buf.writeVarInt(slot));
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult toggleFelling(ItemStack stack, Player player) {
        if (!player.level().isClientSide()) {
            boolean felling = !isFelling(stack);
            stack.set(ModDataComponents.FELLING.get(), felling);
            player.sendOverlayMessage(Component.translatable(felling ? "message.arcforge.arc_tool.felling.on" : "message.arcforge.arc_tool.felling.off"));
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    // Like a shovel, the Drill puts out campfires. (Stripping and path making are the BLOCK_TRANSFORMER component.)
    @Override
    public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
        return (kind == Kind.DRILL && ability == ItemAbilities.SHOVEL_DOUSE) || super.canPerformAction(stack, ability);
    }

    // --- Silk Touch and Fortune modules: enchantments the tool reports while they're on ---

    @Override
    public int getEnchantmentLevel(ItemInstance stack, Holder<Enchantment> enchantment) {
        ToolModules modules = modules(stack);
        if (enchantment.is(Enchantments.SILK_TOUCH) && modules.isOn(ModuleType.SILK_TOUCH)) {
            return 1;
        }
        if (enchantment.is(Enchantments.FORTUNE) && modules.fortuneLevel() > 0) {
            return modules.fortuneLevel();
        }
        return super.getEnchantmentLevel(stack, enchantment);
    }

    @Override
    public ItemEnchantments getAllEnchantments(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> lookup) {
        ItemEnchantments enchantments = super.getAllEnchantments(stack, lookup);
        ToolModules modules = modules(stack);
        if (!modules.isOn(ModuleType.SILK_TOUCH) && modules.fortuneLevel() == 0) {
            return enchantments;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        if (modules.isOn(ModuleType.SILK_TOUCH)) {
            lookup.get(Enchantments.SILK_TOUCH).ifPresent(silk -> mutable.upgrade(silk, 1));
        }
        if (modules.fortuneLevel() > 0) {
            lookup.get(Enchantments.FORTUNE).ifPresent(fortune -> mutable.upgrade(fortune, modules.fortuneLevel()));
        }
        return mutable.toImmutable();
    }

    // --- Display ---

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(Mth.clamp((float) energy(stack) / capacity(), 0.0F, 1.0F) * 13.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.energy", String.format("%,d", energy(stack)), String.format("%,d", capacity()))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arcforge.arc_tool.module_slots", moduleSlots()).withStyle(ChatFormatting.GRAY));
        Component installed = moduleList(modules(stack), moduleSlots());
        builder.accept(installed == null ? Component.translatable("tooltip.arcforge.arc_tool.no_modules").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("tooltip.arcforge.arc_tool.modules", installed).withStyle(ChatFormatting.GRAY));
        if (kind == Kind.SAW) {
            builder.accept(Component.translatable("tooltip.arcforge.arc_tool.felling",
                    Component.translatable(isFelling(stack) ? "options.on" : "options.off")).withStyle(ChatFormatting.GRAY));
        }
    }

    // "Area on · Fortune II · Silk off": each installed module, green when on and grey when off. Null with none.
    public static MutableComponent moduleList(ToolModules modules, int usable) {
        MutableComponent list = null;
        for (int index = 0; index < usable; index++) {
            if (modules.slot(index).isEmpty()) {
                continue;
            }
            MutableComponent entry = modules.slot(index).get().displayName().copy()
                    .withStyle(modules.isOn(index) ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
            list = list == null ? Component.empty().append(entry) : list.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY)).append(entry);
        }
        return list;
    }
}
