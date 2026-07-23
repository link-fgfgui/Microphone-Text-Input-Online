package me.jaffe2718.mcmti.asr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of the ASR-relevant subset of mcmti's configuration.
 * <p>
 * Passed to {@link SpeechAsrClientPlugin#createFromConfig(AsrConfig)} so that
 * third-party plugins can read shared config fields (API key, model, language,
 * timeout, proxy) without taking a compile-time dependency on mcmti's
 * {@code McmtiConfig} class (which pulls in Minecraft/MidnightLib).
 *
 * <p>Implementations are provided by mcmti itself; plugins should not
 * implement this interface.
 */
public interface AsrConfig {

    /** API key for the ASR provider. May be empty if not configured. */
    @NotNull String apiKey();

    /** Model id (e.g. {@code paraformer-realtime-v2}). */
    @NotNull String model();

    /**
     * Language hint ({@code auto}, {@code zh}, {@code en}, or a
     * provider-specific value). May be empty.
     */
    @NotNull String language();

    /** Request timeout in milliseconds. */
    int requestTimeoutMs();

    /**
     * Optional HTTP proxy string (e.g. {@code 127.0.0.1:7890}). Empty = direct.
     */
    @NotNull String httpProxy();

    /**
     * Optional transcription prompt (vocabulary/domain hint). Empty = omit.
     * Mainly used by OpenAI-compatible providers; plugins may ignore.
     */
    default @NotNull String transcriptionPrompt() {
        return "";
    }
}
