package com.enderproject.creativeevw.util;

import com.enderproject.creativeevw.EnderProject;
import com.enderproject.creativeevw.ModDamageTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

/**
 * 寰宇支配之剑的核心击杀逻辑，可供任意物品复用。
 * <p>
 * 核心能力：
 * <ul>
 *   <li>{@link #endlessKill(Player, LivingEntity)} — 单目标强制击杀，绕过护甲</li>
 *   <li>{@link #aoeAttack(Player, float, float, boolean, boolean, boolean, boolean)} — 范围攻击</li>
 *   <li>{@link #sweepAttack(Level, Player, Entity, ItemStack)} — 横扫击退</li>
 * </ul>
 */
public final class EndlessKillHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EndlessKillHelper() {
    }

    // ==================== 核心单目标击杀 ====================

    /**
     * 对单个实体执行无尽击杀。
     * 使用无尽伤害类型，造成 Float.MAX_VALUE 伤害并强制死亡。
     *
     * @param player 攻击者
     * @param victim 被攻击的实体
     */
    public static void endlessKill(Player player, LivingEntity victim) {
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        var damageSource = ModDamageTypes.causeRandomDamage(player, victim, player);
        hurt(victim, damageSource, Float.MAX_VALUE);

        if (victim.isDeadOrDying()) {
            victim.setHealth(0);
            die(victim, damageSource);
            player.killedEntity(serverLevel, victim);
        }
    }

    /**
     * 对单个实体执行无尽击杀，使用自定义伤害量。
     *
     * @param player 攻击者
     * @param victim 被攻击的实体
     * @param damage 伤害量
     */
    public static void endlessKill(Player player, LivingEntity victim, float damage) {
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        var damageSource = ModDamageTypes.causeRandomDamage(player, victim, player);
        hurt(victim, damageSource, damage);

        if (victim.isDeadOrDying()) {
            victim.setHealth(0);
            die(victim, damageSource);
            player.killedEntity(serverLevel, victim);
        }
    }

    /**
     * 解析实体的 LivingEntity 本体，支持 PartEntity（如末影龙部件）。
     */
    @Nullable
    public static LivingEntity resolveVictim(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity;
        }
        if (entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity livingEntity) {
            return livingEntity;
        }
        return null;
    }

    // ==================== 自定义伤害（绕过护甲） ====================

    /**
     * 自定义伤害方法，绕过原版护甲/吸收/保护附魔系统。
     * 直接操作生命值，同时处理击退、声音、战斗追踪等副作用。
     */
    public static boolean hurt(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide || victim.isDeadOrDying()) {
            return false;
        }

        // 处理多部件实体
        if (victim.isMultipartEntity()) {
            for (Entity part : victim.getParts()) {
                if (part instanceof PartEntity<?> partEntity && partEntity.getParent() == victim) {
                    part.hurt(source, amount);
                }
            }
        }

        if (victim.isSleeping() && !victim.level().isClientSide) {
            victim.stopSleeping();
        }

        boolean flag = false;

        victim.setNoActionTime(0);
        victim.walkAnimation.setSpeed(1.5F);
        victim.invulnerableTime = 20;
        victim.getCombatTracker().recordDamage(source, amount);
        victim.setHealth(victim.getHealth() - amount);
        victim.gameEvent(GameEvent.ENTITY_DAMAGE);
        victim.hurtDuration = 10;
        victim.hurtTime = victim.hurtDuration;

        Entity sourceEntity = source.getEntity();
        if (sourceEntity != null) {
            if (sourceEntity instanceof LivingEntity livingSource) {
                if (!source.is(DamageTypeTags.NO_ANGER)) {
                    victim.setLastHurtByMob(livingSource);
                }
            }

            if (sourceEntity instanceof Player player) {
                victim.setLastHurtByPlayer(player);
            } else if (sourceEntity instanceof TamableAnimal tamable) {
                if (tamable.isTame()) {
                    LivingEntity owner = tamable.getOwner();
                    if (owner instanceof Player ownerPlayer) {
                        victim.setLastHurtByPlayer(ownerPlayer);
                    } else {
                        victim.setLastHurtByPlayer(null);
                    }
                }
            }
        }

        victim.level().broadcastDamageEvent(victim, source);

        if (!source.is(DamageTypeTags.NO_IMPACT)) {
            victim.hurtMarked = true;
        }

        if (sourceEntity != null && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            double d0 = sourceEntity.getX() - victim.getX();
            double d1;
            for (d1 = sourceEntity.getZ() - victim.getZ(); d0 * d0 + d1 * d1 < 1.0E-4D; d1 = (Math.random() - Math.random()) * 0.01D) {
                d0 = (Math.random() - Math.random()) * 0.01D;
            }
            victim.knockback(0.4F, d0, d1);
            if (!flag) {
                victim.indicateDamage(d0, d1);
            }
        }

        if (!victim.isDeadOrDying()) {
            victim.playSound(SoundEvents.GENERIC_HURT, 2F, victim.getVoicePitch());
        }

        if (victim instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(serverPlayer, source, amount, amount, false);
        }

        if (sourceEntity instanceof ServerPlayer serverAttacker) {
            CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(serverAttacker, victim, source, amount, amount, false);
        }

        return true;
    }

    // ==================== 强制死亡 ====================

    /**
     * 强制实体死亡，绕过所有保护机制。
     * 利用 1.21.1 中公开的 {@link LivingEntity#die(DamageSource)} 触发原版死亡处理。
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean die(LivingEntity victim, DamageSource damageSource) {
        if (victim.isRemoved()) return false;

        if (victim.isSleeping()) {
            victim.stopSleeping();
        }

        if (!victim.level().isClientSide && victim.hasCustomName()) {
            LOGGER.info("Named entity {} died: {}", victim, victim.getCombatTracker().getDeathMessage().getString());
        }

        // 调用原版 die() — MC 1.21.1 中为 public，无需 AT
        victim.die(damageSource);
        return true;
    }

    // ==================== 横扫攻击 ====================

    /**
     * 横扫攻击 — 对目标周围的实体造成击退并播放粒子效果。
     *
     * @param level  世界
     * @param player 攻击者
     * @param victim 主要目标
     * @param weapon 使用的武器（决定横扫范围）
     */
    public static void sweepAttack(Level level, Player player, Entity victim, ItemStack weapon) {
        if (level.isClientSide) return;

        AABB sweepBox = weapon.getSweepHitBox(player, victim);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, sweepBox)) {
            double reachSq = 9.0; // 3 blocks, standard survival attack reach
            if (!player.isAlliedTo(nearby)
                    && (!(nearby instanceof ArmorStand stand) || !stand.isMarker())
                    && player.distanceToSqr(nearby) < reachSq) {
                nearby.knockback(0.6F,
                        Mth.sin(player.getYRot() * ((float) Math.PI / 180F)),
                        -Mth.cos(player.getYRot() * ((float) Math.PI / 180F)));
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 1.0F, 1.0F);

        double d0 = -Mth.sin(player.getYRot() * ((float) Math.PI / 180F));
        double d1 = Mth.cos(player.getYRot() * ((float) Math.PI / 180F));
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    player.getX() + d0, player.getY(0.5D), player.getZ() + d1,
                    0, d0, 0.0D, d1, 0.0D);
        }
    }

    // ==================== 范围攻击（AOE） ====================

    /**
     * 范围攻击 — 对指定范围内所有符合条件的实体造成无尽伤害。
     *
     * @param player          攻击者
     * @param range           范围（半径，格）
     * @param damage          伤害量
     * @param hurtAnimals     是否攻击被动生物（true 则攻击一切生物）
     * @param hurtItems       是否攻击掉落物
     * @param hurtProjectiles 是否攻击弹射物
     * @param lightning       是否在目标位置召唤闪电
     */
    public static void aoeAttack(Player player, float range, float damage,
                                  boolean hurtAnimals, boolean hurtItems,
                                  boolean hurtProjectiles, boolean lightning) {
        if (player.level().isClientSide) return;

        AABB aabb = player.getBoundingBox().inflate(range, range, range);
        List<Entity> targets = player.level().getEntities(player, aabb);
        DamageSource src = ModDamageTypes.causeRandomDamage(player, player, player);

        targets.stream()
                .filter(entity -> hurtItems || !(entity instanceof ItemEntity))
                .filter(entity -> hurtProjectiles || !(entity instanceof Projectile))
                .filter(entity -> {
                    if (hurtAnimals) return true;
                    return entity instanceof Enemy;
                })
                .forEach(entity -> {
                    if (entity instanceof LivingEntity living) {
                        if (living instanceof EnderDragon dragon) {
                            dragon.setHealth(0);
                        } else if (living instanceof WitherBoss wither) {
                            wither.setInvulnerableTicks(0);
                            living.hurt(src, damage);
                        } else {
                            living.hurt(src, damage);
                        }
                    } else if (entity instanceof ExperienceOrb || entity instanceof AbstractArrow) {
                        entity.discard();
                    } else if (entity instanceof Projectile) {
                        entity.discard();
                    } else {
                        entity.hurt(src, damage);
                    }

                    if (lightning) {
                        trySummonLightning(player.level(), 1, entity.blockPosition(),
                                player instanceof ServerPlayer sp ? sp : null);
                    }
                });
    }

    // ==================== 闪电召唤 ====================

    public static boolean trySummonLightning(Level level, int bolts, BlockPos pos, @Nullable ServerPlayer thrower) {
        if (level instanceof ServerLevel serverLevel) {
            boolean acted = false;
            for (int i = 0; i < bolts; i++) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (bolt != null) {
                    bolt.moveTo(Vec3.atBottomCenterOf(pos));
                    bolt.setCause(thrower);
                    serverLevel.addFreshEntity(bolt);
                }
                acted = true;
            }
            return acted;
        }
        return false;
    }
}
