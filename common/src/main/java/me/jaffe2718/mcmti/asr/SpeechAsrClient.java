package me.jaffe2718.mcmti.asr;

import org.jetbrains.annotations.NotNull;

/**
 * Online speech recognition client.
 * <p>
 * Implementations talk to a remote ASR service. The client must be thread-safe
 * for concurrent {@link #transcribe(AsrRequest)} calls.
 */
public interface SpeechAsrClient extends AutoCloseable {

    /**
     * @return short provider id, e.g. {@code mimo}
     */
    @NotNull String providerId();

    /**
     * Whether this client is configured well enough to accept requests.
     */
    boolean isReady();

    /**
     * Transcribe audio to text.
     *
     * @throws AsrException if the remote call fails
     */
    @NotNull AsrResult transcribe(@NotNull AsrRequest request) throws AsrException;

    @Override
    default void close() {}
}
