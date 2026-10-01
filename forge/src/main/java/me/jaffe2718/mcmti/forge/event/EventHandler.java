package me.jaffe2718.mcmti.forge.event;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = MicrophoneTextInput.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class EventHandler {

    @SubscribeEvent
    public static void registerBindings(@NotNull RegisterKeyMappingsEvent event) {
        event.register(MicrophoneTextInput.RECOGNIZE_KEY);
    }
}
