package com.enderproject.creativeevw.item;

import com.enderproject.creativeevw.EnderProject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = EnderProject.MODID)
public final class EndObserverHandler {

    private static final Map<UUID, GameType> PREVIOUS_MODE = Collections.synchronizedMap(new HashMap<>());

    private EndObserverHandler() {}

    // === 每 tick：飞行 + 免伤 + 净化 + 清除仇恨 ===

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        boolean wearing = isWearingObserver(player);

        if (wearing) {
            // 绝对免疫：先回满血
            player.setHealth(player.getMaxHealth());
            // 再解除假死（寰宇支配之剑等绕过事件直接设 dead=true）
            if (player.dead) {
                player.dead = false;
                player.setPose(Pose.STANDING);
            }
            player.fallDistance = 0;
            player.clearFire();

            // 净化：清除负面药水效果
            List<MobEffectInstance> toRemove = player.getActiveEffects().stream()
                    .filter(e -> !e.getEffect().value().isBeneficial())
                    .toList();
            toRemove.forEach(e -> player.removeEffect(e.getEffect()));

            // 持续 buff
            if (!player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 1, false, false));
            }
            if (!player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 4, false, false));
            }

            // 创造飞行 + 无敌
            player.getAbilities().mayfly = true;
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();

            // 清除生物仇恨
            clearMobTargets(player);
        } else {
            // 脱下时恢复
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.getAbilities().invulnerable = false;
                player.onUpdateAbilities();
            }
            PREVIOUS_MODE.remove(player.getUUID());
        }
    }

    // === 攻击拦截：佩戴观察者时完全不可被攻击（连寰宇支配之剑也无法出手） ===

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof Player target && isWearingObserver(target)) {
            event.setCanceled(true);
        }
    }

    // === 免死：取消死亡事件并回满血 ===

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && isWearingObserver(player)) {
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            player.clearFire();
            player.removeAllEffects();
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 1, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 2, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 4, false, false));
        }
    }

    // === 辅助 ===

    public static boolean isWearingObserver(Player player) {
        var opt = CuriosApi.getCuriosInventory(player);
        return opt.flatMap(inv ->
                inv.findFirstCurio(stack -> stack.getItem() instanceof EndObserverItem)
        ).isPresent();
    }

    private static void clearMobTargets(Player player) {
        var nearby = player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(32.0));
        for (Mob mob : nearby) {
            LivingEntity target = mob.getTarget();
            if (target == player) {
                mob.setTarget(null);
            }
        }
    }

    // === H 键切换旁观模式 ===

    public static void toggleSpectator(ServerPlayer player) {
        UUID id = player.getUUID();
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            // 退出旁观，恢复原模式
            GameType prev = PREVIOUS_MODE.remove(id);
            if (prev == null) prev = GameType.SURVIVAL;
            player.setGameMode(prev);
        } else {
            // 保存当前模式并切换到旁观
            PREVIOUS_MODE.put(id, player.gameMode.getGameModeForPlayer());
            player.setGameMode(GameType.SPECTATOR);
        }
    }

    // === 网络包 ===

    public record SpectatorTogglePayload() implements CustomPacketPayload {
        public static final Type<SpectatorTogglePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(EnderProject.MODID, "spectator_toggle"));

        public static final StreamCodec<FriendlyByteBuf, SpectatorTogglePayload> STREAM_CODEC =
                StreamCodec.unit(new SpectatorTogglePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SpectatorTogglePayload payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    toggleSpectator(sp);
                }
            });
        }
    }

    public static void registerPayload(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EnderProject.MODID).versioned("1");
        registrar.playToServer(SpectatorTogglePayload.TYPE, SpectatorTogglePayload.STREAM_CODEC, SpectatorTogglePayload::handle);
    }
}
