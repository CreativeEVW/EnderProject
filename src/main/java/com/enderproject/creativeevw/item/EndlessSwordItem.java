package com.enderproject.creativeevw.item;

import com.enderproject.creativeevw.ModDamageTypes;
import com.enderproject.creativeevw.util.EndlessKillHelper;
import com.enderproject.creativeevw.item.EndObserverHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EndlessSwordItem extends SwordItem {

    private static final float AOE_RANGE = 16.0F;

    public EndlessSwordItem(Tier tier, Properties properties) {
        super(tier, properties.component(DataComponents.ATTRIBUTE_MODIFIERS, createModifiers()));
    }

    private static ItemAttributeModifiers createModifiers() {
        var entries = List.of(
                new ItemAttributeModifiers.Entry(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, 1024.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND),
                new ItemAttributeModifiers.Entry(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath("enderproject", "endless_sword_reach"),
                                3.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND));
        return new ItemAttributeModifiers(entries, false);
    }

    // === 无耐久度 ===

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    // ==================== 左键：命中后玩家周围 2 格 AOE ====================

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!player.level().isClientSide) {
            AABB aabb = entity.getBoundingBox().inflate(2.0);
            for (Entity nearby : player.level().getEntities(player, aabb)) {
                if (nearby instanceof LivingEntity living) {
                    executeAttack(player, living);
                }
            }
        }
        return true;
    }

    // ==================== 右键：16 格范围 endlessKill ====================

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                            @NotNull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide) {
            aoeEndlessKill(player);
            player.getCooldowns().addCooldown(held.getItem(), 20);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResultHolder.success(held);
    }

    private static void aoeEndlessKill(Player player) {
        AABB aabb = player.getBoundingBox().inflate(AOE_RANGE);
        int totalXp = 0;

        // 第一轮：击杀实体、清除弹射物
        for (Entity entity : player.level().getEntities(player, aabb)) {
            if (entity instanceof Projectile) {
                entity.discard();
                continue;
            }
            if (entity instanceof LivingEntity living) {
                // 在击杀前捕获经验值（击杀后 wasExperienceConsumed 会返回 true 导致值为 0）
                int xp = living instanceof Player ? 0 : living.getExperienceReward((ServerLevel) player.level(), player);

                if (living instanceof EnderDragon dragon) {
                    dragon.setHealth(0);
                    EndlessKillHelper.die(dragon, ModDamageTypes.causeRandomDamage(player, dragon, player));
                } else if (living instanceof WitherBoss wither) {
                    wither.setInvulnerableTicks(0);
                    EndlessKillHelper.endlessKill(player, living);
                } else if (living instanceof Player targetPlayer) {
                    attackPlayer(player, targetPlayer);
                } else {
                    EndlessKillHelper.endlessKill(player, living);
                }

                if (xp > 0 && living.isDeadOrDying()) {
                    totalXp += xp;
                }
            }
        }

        // 第二轮：掉落物传到玩家脚下，已有经验颗粒吸收
        for (Entity entity : player.level().getEntities(player, aabb)) {
            if (entity instanceof ItemEntity item) {
                item.teleportTo(player.getX(), player.getY(), player.getZ());
            } else if (entity instanceof ExperienceOrb orb) {
                orb.teleportTo(player.getX(), player.getY(), player.getZ());
            }
        }

        // 因 wasExperienceConsumed 被触发，原版不生成经验颗粒，手动生成在玩家位置
        if (totalXp > 0) {
            ExperienceOrb orb = EntityType.EXPERIENCE_ORB.create(player.level());
            if (orb != null) {
                orb.setPos(player.getX(), player.getY() + 0.5, player.getZ());
                orb.value = totalXp;
                player.level().addFreshEntity(orb);
            }
        }
    }

    // ==================== 核心攻击逻辑 ====================

    private static void executeAttack(Player attacker, Entity target) {
        LivingEntity victim = EndlessKillHelper.resolveVictim(target);
        if (victim == null) return;

        if (victim instanceof Player targetPlayer) {
            attackPlayer(attacker, targetPlayer);
        } else {
            attackEntity(attacker, victim);
        }
    }

    // ---- 非玩家目标 ----

    private static void attackEntity(Player attacker, LivingEntity victim) {
        EndlessKillHelper.endlessKill(attacker, victim);
    }

    // ---- 玩家目标 ----

    private static void attackPlayer(Player attacker, Player target) {
        // 佩戴观察者时不掉落物品
        if (!EndObserverHandler.isWearingObserver(target)) {
            dropAllItems(target);
        }

        // 2. 使重生方块（床、重生锚）失效
        invalidateRespawn(target);

        // 3. 执行 endlessKill
        EndlessKillHelper.endlessKill(attacker, target);
    }

    private static void dropAllItems(Player player) {
        player.getInventory().dropAll();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }

    private static void invalidateRespawn(Player player) {
        if (player instanceof ServerPlayer sp) {
            ServerLevel overworld = sp.getServer().overworld();
            BlockPos worldSpawn = overworld.getSharedSpawnPos();
            sp.setRespawnPosition(
                    Level.OVERWORLD,
                    worldSpawn,
                    0.0F,
                    true,
                    false
            );
        }
    }

    // ==================== 显示 NULL ====================

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                 @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.enderproject.endless_sword.damage"));
    }
}
