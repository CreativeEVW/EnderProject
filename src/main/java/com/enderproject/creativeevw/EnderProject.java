package com.enderproject.creativeevw;

import com.enderproject.creativeevw.block.ModBlockEntities;
import com.enderproject.creativeevw.block.ModBlocks;
import com.enderproject.creativeevw.item.EndObserverHandler;
import com.enderproject.creativeevw.item.EndObserverItem;
import com.enderproject.creativeevw.item.EndlessSwordItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(EnderProject.MODID)
public class EnderProject {
    public static final String MODID = "enderproject";
    public static final Logger LOGGER = LogUtils.getLogger();

    // === 物品注册 ===
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<Item> ENDLESS_SWORD = ITEMS.register("endless_sword",
            () -> new EndlessSwordItem(Tiers.NETHERITE, new Item.Properties()
                    .stacksTo(1)
                    .fireResistant()
                    .durability(1)
                    .component(DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false))));
    public static final DeferredItem<Item> END_OBSERVER = ITEMS.register("end_observer",
            () -> new EndObserverItem(new Item.Properties()
                    .stacksTo(1)
                    .fireResistant()));
    public static final DeferredItem<BlockItem> STEM_CELL = ITEMS.register("stem_cell",
            () -> new BlockItem(ModBlocks.STEM_CELL.get(), new Item.Properties()));

    // === 创造标签 ===
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENDERPROJECT_TAB =
            CREATIVE_MODE_TABS.register("enderproject_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.enderproject"))
                    .icon(() -> ENDLESS_SWORD.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ENDLESS_SWORD.get());
                        output.accept(END_OBSERVER.get());
                        output.accept(STEM_CELL.get());
                    }).build());

    public EnderProject(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(EndObserverHandler::registerPayload);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("EnderProject common setup complete");
    }

    // === 终末轮回穿透无尽套：LOWEST 优先级覆写死亡取消（观察者佩戴者除外） ===
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().is(ModDamageTypes.ENDLESS)
                && !(event.getEntity() instanceof Player p && EndObserverHandler.isWearingObserver(p))) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("EnderProject server starting");
    }
}
