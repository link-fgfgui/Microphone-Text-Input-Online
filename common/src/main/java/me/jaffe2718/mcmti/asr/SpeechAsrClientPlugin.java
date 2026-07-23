package me.jaffe2718.mcmti.asr;

import org.jetbrains.annotations.NotNull;

/**
 * SPI for third-party ASR provider plugins.
 * <p>
 * A plugin mod can implement this interface and register it via
 * {@code META-INF/services/me.jaffe2718.mcmti.asr.SpeechAsrClientPlugin}
 * (standard Java {@link java.util.ServiceLoader} mechanism). mcmti will discover
 * all implementations on the classpath and dispatch to the one whose
 * {@link #providerId()} matches the configured {@code pluginProviderId} when
 * {@code PLUGIN} ASR provider is selected.
 *
 * <p>Implementation requirements:
 * <ul>
 *   <li>Stateless factory — {@link #createFromConfig(AsrConfig)} is called on
 *       every config save and must return a fresh, ready-to-use client.</li>
 *   <li>Thread-safe — the returned {@link SpeechAsrClient} must handle
 *       concurrent {@code transcribe()} calls.</li>
 *   <li>Provider id must be unique and stable; matched verbatim against the
 *       user-configured {@code pluginProviderId}.</li>
 * </ul>
 */
public interface SpeechAsrClientPlugin {

    /**
     * Unique, stable provider identifier (e.g. {@code "dashscope"}).
     * <p>
     * Matched against the configured {@code pluginProviderId}. Lower-case ASCII
     * letters, digits, and {@code -} are recommended.
     *
     * @return non-blank provider id
     */
    @NotNull String providerId();

    /**
     * Human-readable display name shown in logs.
     *
     * @return non-blank display name
     */
    @NotNull String displayName();

    /**
     * Build a fresh {@link SpeechAsrClient} from the given config.
     * <p>
     * Called whenever the user saves the mcmti config. Implementations read
     * shared fields (API key, model, language, timeout, proxy) from
     * {@code config} and may use their own plugin-specific config for extras.
     *
     * @param config read-only view of ASR-relevant mcmti config fields
     * @return a new, ready-to-use client; never {@code null}
     */
    @NotNull SpeechAsrClient createFromConfig(@NotNull AsrConfig config);
}
