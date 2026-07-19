package me.jaffe2718.mcmti.event;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import me.jaffe2718.mcmti.config.McmtiConfig;
import me.jaffe2718.mcmti.util.AudioRecorder;
import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@SuppressWarnings("unused")
public interface EventSystem {

    ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();

    static void showRecognizeStatus(ClientWorld world) {
        if (MinecraftClient.getInstance().player instanceof ClientPlayerEntity player
                && MinecraftClient.getInstance().currentScreen == null) {
            // Do not complain about mic until the user has actually tried to record
            if (AudioRecorder.hasOpenFailed()) {
                player.sendMessage(Text.translatable("message.mcmti.audioInputDeviceLoadFailed"), true);
            } else if (!SpeechRecognizer.isReady()) {
                player.sendMessage(Text.translatable("message.mcmti.speechRecognizerNotReady"), true);
            } else if (McmtiConfig.mode != McmtiConfig.Mode.AUTO_SEND
                    && MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
                if (AudioRecorder.isOpen()) {
                    player.sendMessage(Text.translatable("message.mcmti.recordingAudio"), true);
                } else {
                    player.sendMessage(Text.translatable("message.mcmti.openingMicrophone"), true);
                }
            }
        }
    }


    @SuppressWarnings("InfiniteLoopStatement")
    static void recognizeTask() {
        MicrophoneTextInput.LOGGER.info("Recognize thread started");
        @Nullable Thread vthread = null;
        while (true) {
            try {
                if (MinecraftClient.getInstance() != null &&
                        MinecraftClient.getInstance().player instanceof ClientPlayerEntity player
                        && MinecraftClient.getInstance().currentScreen == null
                        && SpeechRecognizer.isReady()) {
                    switch (McmtiConfig.mode) {
                        case AUTO_SEND -> {
                            // Opens mic on first cycle (lazy); empty audio if open fails
                            byte[] audio = AudioRecorder.recordCycle();
                            if (audio.length == 0) {
                                LockSupport.parkNanos(50_000_000L);
                                break;
                            }
                            Thread.ofVirtual().start(() -> {
                                String result = SpeechRecognizer.recognize(audio);
                                if (!result.isEmpty()) {
                                    player.sendMessage(Text.translatable("message.mcmti.messageSent"), true);
                                    SpeechRecognizer.sendChatMessage(player, result);
                                }
                            });
                        }
                        case RELEASE_KEY_TO_SEND -> {
                            if (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
                                // ensureOpen runs inside record(); first press may take tens of ms
                                byte[] audio = AudioRecorder.record();
                                vthread = Thread.ofVirtual().start(() -> {
                                    String result = SpeechRecognizer.recognize(audio);
                                    if (!result.isEmpty()) {
                                        SCHEDULED_EXECUTOR_SERVICE.schedule(
                                                () -> player.sendMessage(Text.translatable("message.mcmti.messageSent"), true), 100, TimeUnit.MILLISECONDS);
                                        SpeechRecognizer.sendChatMessage(player, result);
                                    }
                                });
                            } else if (vthread != null && vthread.isAlive()) {
                                player.sendMessage(Text.translatable("message.mcmti.recognizing"), true);
                            }
                            LockSupport.parkNanos(1000000L);
                        }
                        case RELEASE_KEY_TO_INPUT -> {
                            if (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
                                byte[] audio = AudioRecorder.record();
                                vthread = Thread.ofVirtual().start(() -> {
                                    String result = SpeechRecognizer.recognize(audio);
                                    if (!result.isEmpty()) {
                                        MinecraftClient.getInstance().submit(() -> MinecraftClient.getInstance().setScreen(new ChatScreen(McmtiConfig.prefix + result))).join();
                                    }
                                });
                            } else if (vthread != null && vthread.isAlive()) {
                                player.sendMessage(Text.translatable("message.mcmti.recognizing"), true);
                            }
                            LockSupport.parkNanos(1000000L);
                        }
                    }
                } else {
                    LockSupport.parkNanos(10000000L);
                }
            } catch (Throwable t) {
                MicrophoneTextInput.LOGGER.error("Error in recognize task", t);
            }
        }
    }
}
