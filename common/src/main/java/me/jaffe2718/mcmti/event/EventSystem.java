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
import org.jetbrains.annotations.NotNull;
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
            if (AudioRecorder.hasOpenFailed()) {
                player.sendMessage(Text.translatable("message.mcmti.audioInputDeviceLoadFailed"), true);
            } else if (!SpeechRecognizer.instanceAvailable()) {
                player.sendMessage(SpeechRecognizer.instanceUnavailableToast(), true);
            } else if (McmtiConfig.mode != McmtiConfig.Mode.AUTO_SEND
                    && MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
                if (AudioRecorder.isRecordingSession()) {
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
                        && SpeechRecognizer.instanceAvailable()) {
                    switch (McmtiConfig.mode) {
                        case AUTO_SEND -> {
                            float[] audio = AudioRecorder.recordCycle();
                            if (audio.length == 0) {
                                LockSupport.parkNanos(50_000_000L);
                                break;
                            }
                            Thread.ofVirtual().start(() -> {
                                var outcome = SpeechRecognizer.recognizeOutcome(audio);
                                if (outcome.failed()) {
                                    player.sendMessage(Text.translatable(
                                            "message.mcmti.recognitionError", outcome.errorDetail()), true);
                                } else if (outcome.hasText()) {
                                    player.sendMessage(Text.translatable("message.mcmti.messageSent"), true);
                                    sendChatMessage(player, outcome.text());
                                }
                            });
                        }
                        case RELEASE_KEY_TO_SEND -> {
                            if (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
                                float[] audio = AudioRecorder.record();
                                vthread = Thread.ofVirtual().start(() -> {
                                    var outcome = SpeechRecognizer.recognizeOutcome(audio);
                                    if (outcome.failed()) {
                                        player.sendMessage(Text.translatable(
                                                "message.mcmti.recognitionError", outcome.errorDetail()), true);
                                    } else if (outcome.hasText()) {
                                        SCHEDULED_EXECUTOR_SERVICE.schedule(
                                                () -> player.sendMessage(Text.translatable("message.mcmti.messageSent"), true), 100, TimeUnit.MILLISECONDS);
                                        sendChatMessage(player, outcome.text());
                                    }
                                });
                            } else if (vthread != null && vthread.isAlive()) {
                                player.sendMessage(Text.translatable("message.mcmti.recognizing"), true);
                            }
                            LockSupport.parkNanos(1000000L);
                        }
                        case RELEASE_KEY_TO_INPUT -> {
                            if (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
                                float[] audio = AudioRecorder.record();
                                vthread = Thread.ofVirtual().start(() -> {
                                    var outcome = SpeechRecognizer.recognizeOutcome(audio);
                                    if (outcome.failed()) {
                                        player.sendMessage(Text.translatable(
                                                "message.mcmti.recognitionError", outcome.errorDetail()), true);
                                    } else if (outcome.hasText()) {
                                        String text = outcome.text();
                                        MinecraftClient.getInstance().submit(() ->
                                                MinecraftClient.getInstance().setScreen(new ChatScreen(McmtiConfig.prefix + text))).join();
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

    static void sendChatMessage(@NotNull ClientPlayerEntity player, @NotNull String message) {
        final int maxLength = 256 - McmtiConfig.prefix.length();
        while (message.length() > maxLength) {
            player.networkHandler.sendChatMessage(McmtiConfig.prefix + message.substring(0, maxLength));
            message = message.substring(maxLength);
        }
        if (!message.isEmpty()) {
            player.networkHandler.sendChatMessage(McmtiConfig.prefix + message);
        }
    }
}
