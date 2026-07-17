package com.enderproject.creativeevw;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModDamageTypes {

    public static final ResourceKey<DamageType> ENDLESS = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EnderProject.MODID, "endless")
    );

    public static DamageSource causeRandomDamage(LivingEntity levelAccess, @Nullable Entity source, @Nullable Entity attacker) {
        return new DamageSourceRandomMessages(
                levelAccess.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ENDLESS),
                source,
                attacker
        );
    }

    public static class DamageSourceRandomMessages extends DamageSource {
        public DamageSourceRandomMessages(Holder<DamageType> holder, @Nullable Entity source, @Nullable Entity attacker) {
            super(holder, source, attacker);
        }

        @Override
        public @NotNull Component getLocalizedDeathMessage(LivingEntity attacked) {
            int type = attacked.getRandom().nextInt(5);
            LivingEntity killer = attacked.getKillCredit();
            String base = "death.attack." + this.getMsgId();
            if (killer != null) {
                return Component.translatable(base + ".player." + type, attacked.getDisplayName(), killer.getDisplayName());
            }
            return Component.translatable(base + "." + type, attacked.getDisplayName());
        }
    }
}
