package me.jaffe2718.mcmti.neoforge.event;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jetbrains.annotations.NotNull;

@EventBusSubscriber(value = Dist.CLIENT, modid = MicrophoneTextInput.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class EventHandler {

    @SubscribeEvent
    public static void registerBindings(@NotNull RegisterKeyMappingsEvent event) {
        event.register(MicrophoneTextInput.RECOGNIZE_KEY);
    }
}
