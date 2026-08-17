package me.jaffe2718.mcmti.neoforge;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import me.jaffe2718.mcmti.event.EventSystem;
import me.jaffe2718.mcmti.util.AudioRecorder;
import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod(value = MicrophoneTextInput.MOD_ID, dist = Dist.CLIENT)
public final class MicrophoneTextInputNeoForge {

    private static IEventBus eventBus;
    private static final AtomicBoolean CLIENT_STARTED = new AtomicBoolean();

    public MicrophoneTextInputNeoForge(@NotNull IEventBus modBus) {
        eventBus = modBus;
        MicrophoneTextInput.init();
        NeoForge.EVENT_BUS.addListener(MicrophoneTextInputNeoForge::onClientTick);
        NeoForge.EVENT_BUS.addListener(MicrophoneTextInputNeoForge::onGameShuttingDown);
        Thread.ofVirtual().start(EventSystem::recognizeTask).setName("thread.mcmti.recognizer.loop");
    }

    public static @NotNull IEventBus getEventBus() {
        if (eventBus == null) {
            throw new IllegalStateException("mcmti mod event bus is not initialized");
        }
        return eventBus;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (CLIENT_STARTED.compareAndSet(false, true)) {
            Thread.ofVirtual().start(SpeechRecognizer::init);
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null) {
            EventSystem.showRecognizeStatus(client.level);
        }
    }

    private static void onGameShuttingDown(GameShuttingDownEvent event) {
        SpeechRecognizer.deregister();
        AudioRecorder.destroy();
    }
}
