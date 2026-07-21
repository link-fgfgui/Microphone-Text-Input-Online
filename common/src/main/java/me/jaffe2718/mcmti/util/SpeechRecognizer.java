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

    /** Max length of error detail shown on the action bar (full message is logged). */
    private static final int ACTION_BAR_ERROR_MAX = 96;

    /**
     * Outcome of a recognition attempt.
     *
     * @param text         recognized text when successful (may be empty for no speech);
     *                     {@code null} when {@link #failed()}
     * @param errorDetail  short error for action bar when failed; {@code null} on success
     */
    public record RecognizeOutcome(@Nullable String text, @Nullable String errorDetail) {
        public static @NotNull RecognizeOutcome success(@NotNull String text) {
            return new RecognizeOutcome(text, null);
        }

        public static @NotNull RecognizeOutcome failure(@NotNull String detail) {
            return new RecognizeOutcome(null, detail);
        }

        public boolean failed() {
            return errorDetail != null;
        }

        public boolean hasText() {
            return text != null && !text.isEmpty();
        }
    }

    /**
     * Transcribe PCM audio via the configured online speech recognition service.
     *
     * @param pcmAudio little-endian 16-bit mono PCM at {@link AudioRecorder#SAMPLE_RATE} Hz
     * @return success with text (possibly empty), or failure with a short error detail for the action bar
     */
    public static @NotNull RecognizeOutcome recognize(byte @NotNull [] pcmAudio) {
        SpeechAsrClient c = client;
        if (c == null || !c.isReady() || pcmAudio.length == 0) {
            return RecognizeOutcome.success("");
        }
        try {
            AsrResult result = c.transcribe(new AsrRequest(
                    pcmAudio,
                    AudioRecorder.SAMPLE_RATE,
                    McmtiConfig.language
            ));
            return RecognizeOutcome.success(result.text().trim());
        } catch (AsrException e) {
            MicrophoneTextInput.LOGGER.error("Speech recognition failed: {}", e.getMessage());
            return RecognizeOutcome.failure(formatActionBarError(e.getMessage()));
        } catch (Exception e) {
            MicrophoneTextInput.LOGGER.error("Unexpected speech recognition error", e);
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return RecognizeOutcome.failure(formatActionBarError(detail));
        }
    }

    /**
     * One-line error for the action bar: drop everything after the first {@code :}
     * (e.g. HTTP response body), then hard-cap length.
     * <p>
     * {@code "OpenAI-compatible ASR HTTP 400: {...}"} → {@code "OpenAI-compatible ASR HTTP 400"}
     */
    static @NotNull String formatActionBarError(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        String oneLine = message.replace('\r', ' ').replace('\n', ' ').replaceAll(" +", " ").trim();
        int colon = oneLine.indexOf(':');
        if (colon >= 0) {
            oneLine = oneLine.substring(0, colon).trim();
        }
        if (oneLine.isEmpty()) {
            return "unknown error";
        }
        if (oneLine.length() <= ACTION_BAR_ERROR_MAX) {
            return oneLine;
        }
        return oneLine.substring(0, ACTION_BAR_ERROR_MAX) + "...";
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
