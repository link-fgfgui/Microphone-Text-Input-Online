package me.jaffe2718.mcmti.util;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import me.jaffe2718.mcmti.asr.AsrException;
import me.jaffe2718.mcmti.asr.AsrRequest;
import me.jaffe2718.mcmti.asr.AsrResult;
import me.jaffe2718.mcmti.asr.SpeechAsrClient;
import me.jaffe2718.mcmti.asr.SpeechAsrClients;
import me.jaffe2718.mcmti.config.McmtiConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Facade over the configured online {@link SpeechAsrClient}.
 */
public final class SpeechRecognizer {
    private static volatile @Nullable SpeechAsrClient client;

    private SpeechRecognizer() {}

    public static boolean isReady() {
        SpeechAsrClient c = client;
        return c != null && c.isReady();
    }

    public static synchronized void init() {
        destroy();
        try {
            SpeechAsrClient created = SpeechAsrClients.createFromConfig();
            client = created;
            if (created.isReady()) {
                MicrophoneTextInput.LOGGER.info(
                        "Speech recognizer ready (provider={}, model={}, baseUrl={}, proxy={})",
                        created.providerId(),
                        McmtiConfig.model,
                        McmtiConfig.apiBaseUrl,
                        McmtiConfig.httpProxy == null || McmtiConfig.httpProxy.isBlank()
                                ? "none"
                                : "configured"
                );
            } else {
                MicrophoneTextInput.LOGGER.warn(
                        "Speech recognizer not ready: configure API base URL / key in mod config (provider={})",
                        created.providerId()
                );
            }
        } catch (IllegalArgumentException e) {
            client = null;
            MicrophoneTextInput.LOGGER.error("Failed to init speech recognizer: {}", e.getMessage());
        }
    }

    public static synchronized void destroy() {
        SpeechAsrClient old = client;
        client = null;
        if (old != null) {
            try {
                old.close();
            } catch (Exception e) {
                MicrophoneTextInput.LOGGER.debug("Error closing ASR client", e);
            }
        }
    }

    /**
     * Transcribe PCM audio via the configured online speech recognition service.
     *
     * @param pcmAudio little-endian 16-bit mono PCM at {@link AudioRecorder#SAMPLE_RATE} Hz
     * @return recognized text, or empty string on failure / no speech
     */
    public static @NotNull String recognize(byte @NotNull [] pcmAudio) {
        SpeechAsrClient c = client;
        if (c == null || !c.isReady() || pcmAudio.length == 0) {
            return "";
        }
        try {
            AsrResult result = c.transcribe(new AsrRequest(
                    pcmAudio,
                    AudioRecorder.SAMPLE_RATE,
                    McmtiConfig.language
            ));
            return result.text().trim();
        } catch (AsrException e) {
            MicrophoneTextInput.LOGGER.error("Speech recognition failed: {}", e.getMessage());
            return "";
        } catch (Exception e) {
            MicrophoneTextInput.LOGGER.error("Unexpected speech recognition error", e);
            return "";
        }
    }

    /**
     * Send a message to the chat, split it into multiple messages if necessary.
     * Due to the limitation of the chat message length in Minecraft,
     * the message will be split into multiple parts with prefix and not longer than 256 characters.
     *
     * @param player  The player to send the message.
     * @param message The message to send.
     */
    public static void sendChatMessage(@NotNull ClientPlayerEntity player, @NotNull String message) {
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
