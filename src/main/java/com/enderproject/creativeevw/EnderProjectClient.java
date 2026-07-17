package com.enderproject.creativeevw;

import com.enderproject.creativeevw.item.EndObserverRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@Mod(value = EnderProject.MODID, dist = Dist.CLIENT)
public class EnderProjectClient {
    public EnderProjectClient(ModContainer container, IEventBus modEventBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(FMLClientSetupEvent.class,
                event -> event.enqueueWork(() -> CuriosRendererRegistry.register(
                        EnderProject.END_OBSERVER.get(), EndObserverRenderer::new)));
    }
}
