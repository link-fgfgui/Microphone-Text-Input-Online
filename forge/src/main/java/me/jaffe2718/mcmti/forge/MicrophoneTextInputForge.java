package me.jaffe2718.mcmti.forge;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import me.jaffe2718.mcmti.event.EventSystem;
import me.jaffe2718.mcmti.util.AudioRecorder;
import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod(MicrophoneTextInput.MOD_ID)
public final class MicrophoneTextInputForge {

    private static IEventBus eventBus;
    private static final AtomicBoolean CLIENT_STARTED = new AtomicBoolean();

    public MicrophoneTextInputForge(FMLJavaModLoadingContext context) {
        this(context.getModEventBus());
    }

    public MicrophoneTextInputForge() {
        this(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public MicrophoneTextInputForge(@NotNull IEventBus modBus) {
        eventBus = modBus;
        // MCMti is a client-only mod; no-op on a dedicated server.
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(MicrophoneTextInputForge::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(MicrophoneTextInputForge::onGameShuttingDown);
        MicrophoneTextInput.init();
        new Thread(EventSystem::recognizeTask, "thread.mcmti.recognizer.loop").start();
    }

    public static @NotNull IEventBus getEventBus() {
        if (eventBus == null) {
            throw new IllegalStateException("mcmti mod event bus is not initialized");
        }
        return eventBus;
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (CLIENT_STARTED.compareAndSet(false, true)) {
            new Thread(SpeechRecognizer::init, "thread.mcmti.recognizer.init").start();
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
