package com.enderproject.creativeevw.item;

import com.enderproject.creativeevw.EnderProject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(value = Dist.CLIENT, modid = EnderProject.MODID)
public final class EndObserverKeyHandler {

    private static final KeyMapping TOGGLE_SPECTATOR = new KeyMapping(
            "key.enderproject.toggle_spectator",
            GLFW.GLFW_KEY_H,
            "key.categories.enderproject"
    );

    private EndObserverKeyHandler() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_SPECTATOR);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (TOGGLE_SPECTATOR.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.getConnection() != null) {
                mc.getConnection().send(new EndObserverHandler.SpectatorTogglePayload());
            }
        }
    }
}
