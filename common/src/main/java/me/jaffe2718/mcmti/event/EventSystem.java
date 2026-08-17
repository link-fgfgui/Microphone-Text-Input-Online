package me.jaffe2718.mcmti.event;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import me.jaffe2718.mcmti.config.McmtiConfig;
import me.jaffe2718.mcmti.util.AudioRecorder;
import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@SuppressWarnings("unused")
public interface EventSystem {

    ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();

    static void showRecognizeStatus(ClientLevel world) {
        if (Minecraft.getInstance().player instanceof LocalPlayer player
                && Minecraft.getInstance().screen == null) {
            if (AudioRecorder.hasOpenFailed()) {
                player.displayClientMessage(Component.translatable("message.mcmti.audioInputDeviceLoadFailed"), true);
            } else if (!SpeechRecognizer.instanceAvailable()) {
                player.displayClientMessage(SpeechRecognizer.instanceUnavailableToast(), true);
            } else if (McmtiConfig.mode != McmtiConfig.Mode.AUTO_SEND
                    && MicrophoneTextInput.RECOGNIZE_KEY.isDown()) {
                if (AudioRecorder.isRecordingSession()) {
                    player.displayClientMessage(Component.translatable("message.mcmti.recordingAudio"), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.mcmti.openingMicrophone"), true);
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
                if (Minecraft.getInstance() != null &&
                        Minecraft.getInstance().player instanceof LocalPlayer player
                        && Minecraft.getInstance().screen == null
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
                                    player.displayClientMessage(Component.translatable(
                                            "message.mcmti.recognitionError", outcome.errorDetail()), true);
                                } else if (outcome.hasText()) {
                                    player.displayClientMessage(Component.translatable("message.mcmti.messageSent"), true);
                                    sendChatMessage(player, outcome.text());
                                }
                            });
                        }
                        case RELEASE_KEY_TO_SEND -> {
                            if (MicrophoneTextInput.RECOGNIZE_KEY.isDown()) {
                                float[] audio = AudioRecorder.record();
                                vthread = Thread.ofVirtual().start(() -> {
                                    var outcome = SpeechRecognizer.recognizeOutcome(audio);
                                    if (outcome.failed()) {
                                        player.displayClientMessage(Component.translatable(
                                                "message.mcmti.recognitionError", outcome.errorDetail()), true);
                                    } else if (outcome.hasText()) {
                                        SCHEDULED_EXECUTOR_SERVICE.schedule(
                                                () -> player.displayClientMessage(Component.translatable("message.mcmti.messageSent"), true), 100, TimeUnit.MILLISECONDS);
                                        sendChatMessage(player, outcome.text());
                                    }
                                });
                            } else if (vthread != null && vthread.isAlive()) {
                                player.displayClientMessage(Component.translatable("message.mcmti.recognizing"), true);
                            }
                            LockSupport.parkNanos(1000000L);
                        }
                        case RELEASE_KEY_TO_INPUT -> {
                            if (MicrophoneTextInput.RECOGNIZE_KEY.isDown()) {
                                float[] audio = AudioRecorder.record();
                                vthread = Thread.ofVirtual().start(() -> {
                                    var outcome = SpeechRecognizer.recognizeOutcome(audio);
                                    if (outcome.failed()) {
                                        player.displayClientMessage(Component.translatable(
                                                "message.mcmti.recognitionError", outcome.errorDetail()), true);
                                    } else if (outcome.hasText()) {
                                        String text = outcome.text();
                                        Minecraft.getInstance().submit(() ->
                                                Minecraft.getInstance().setScreen(new ChatScreen(McmtiConfig.prefix + text))).join();
                                    }
                                });
                            } else if (vthread != null && vthread.isAlive()) {
                                player.displayClientMessage(Component.translatable("message.mcmti.recognizing"), true);
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

    static void sendChatMessage(@NotNull LocalPlayer player, @NotNull String message) {
        final int maxLength = 256 - McmtiConfig.prefix.length();
        while (message.length() > maxLength) {
            player.connection.sendChat(McmtiConfig.prefix + message.substring(0, maxLength));
            message = message.substring(maxLength);
        }
        if (!message.isEmpty()) {
            player.connection.sendChat(McmtiConfig.prefix + message);
        }
    }
}
