package me.jaffe2718.mcmti.asr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider-agnostic speech recognition request.
 *
 * @param pcmAudio   little-endian 16-bit mono PCM samples
 * @param sampleRate sample rate in Hz (e.g. 16000)
 * @param language   language hint ({@code auto}, {@code zh}, {@code en}, or provider-specific); may be null
 */
public record AsrRequest(
        byte @NotNull [] pcmAudio,
        int sampleRate,
        @Nullable String language
) {
    public AsrRequest {
        if (pcmAudio == null) {
            throw new IllegalArgumentException("pcmAudio must not be null");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
    }
}
