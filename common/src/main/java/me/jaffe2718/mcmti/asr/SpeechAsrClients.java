package me.jaffe2718.mcmti.asr;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import me.jaffe2718.mcmti.asr.mimo.MimoAsrClient;
import me.jaffe2718.mcmti.asr.openai.OpenAiCompatibleAsrClient;
import me.jaffe2718.mcmti.config.McmtiConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Factory for {@link SpeechAsrClient} instances from {@link McmtiConfig}.
 * <p>
 * Supports built-in providers ({@code MIMO}, {@code OPENAI_COMPATIBLE}) and
 * third-party plugins discovered via {@link ServiceLoader} on the
 * {@link SpeechAsrClientPlugin} SPI.
 */
public final class SpeechAsrClients {
    private SpeechAsrClients() {}

    /** Cached plugin list, loaded once on first access. */
    private static volatile @Nullable List<SpeechAsrClientPlugin> cachedPlugins;

    /**
     * @return unmodifiable snapshot of all {@link SpeechAsrClientPlugin}
     *         implementations discovered via {@link ServiceLoader}
     */
    public static @NotNull List<SpeechAsrClientPlugin> loadedPlugins() {
        List<SpeechAsrClientPlugin> snapshot = cachedPlugins;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (SpeechAsrClients.class) {
            if (cachedPlugins == null) {
                List<SpeechAsrClientPlugin> plugins = new ArrayList<>();
                for (SpeechAsrClientPlugin p : ServiceLoader.load(SpeechAsrClientPlugin.class)) {
                    plugins.add(p);
                    MicrophoneTextInput.LOGGER.info(
                            "Discovered ASR plugin: id='{}', name='{}'",
                            p.providerId(),
                            p.displayName()
                    );
                }
                cachedPlugins = List.copyOf(plugins);
            }
            return cachedPlugins;
        }
    }

    /**
     * Look up a plugin by provider id.
     *
     * @param providerId non-blank id to match against
     *                   {@link SpeechAsrClientPlugin#providerId()}
     * @return matching plugin, or {@code null} if none found
     */
    public static @Nullable SpeechAsrClientPlugin findPlugin(@NotNull String providerId) {
        for (SpeechAsrClientPlugin p : loadedPlugins()) {
            if (p.providerId().equals(providerId)) {
                return p;
            }
        }
        return null;
    }

    public static @NotNull SpeechAsrClient createFromConfig() {
        return switch (McmtiConfig.asrProvider) {
            case MIMO -> new MimoAsrClient(
                    McmtiConfig.apiBaseUrl,
                    McmtiConfig.apiKey,
                    McmtiConfig.model,
                    McmtiConfig.requestTimeoutMs,
                    McmtiConfig.httpProxy
            );
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleAsrClient(
                    McmtiConfig.apiBaseUrl,
                    McmtiConfig.apiKey,
                    McmtiConfig.model,
                    McmtiConfig.transcriptionPrompt,
                    McmtiConfig.requestTimeoutMs,
                    McmtiConfig.httpProxy
            );
            case PLUGIN -> {
                String id = McmtiConfig.pluginProviderId;
                if (id == null || id.isBlank()) {
                    throw new IllegalArgumentException(
                            "ASR provider is PLUGIN but pluginProviderId is empty; "
                                    + "set it to a registered plugin id (e.g. 'dashscope')"
                    );
                }
                SpeechAsrClientPlugin plugin = findPlugin(id.trim());
                if (plugin == null) {
                    throw new IllegalArgumentException(
                            "No ASR plugin registered for id '" + id + "'. "
                                    + "Discovered plugins: " + describePlugins()
                    );
                }
                yield plugin.createFromConfig(new McmtiAsrConfig());
            }
        };
    }

    private static @NotNull String describePlugins() {
        List<SpeechAsrClientPlugin> plugins = loadedPlugins();
        if (plugins.isEmpty()) {
            return "(none — install a plugin mod that provides SpeechAsrClientPlugin via ServiceLoader)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < plugins.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('\'').append(plugins.get(i).providerId()).append('\'');
        }
        return sb.toString();
    }

    /**
     * Adapter that exposes the ASR-relevant fields of {@link McmtiConfig}
     * through the pure-Java {@link AsrConfig} interface, so plugins don't need
     * a compile-time dependency on mcmti's config class (which pulls in
     * Minecraft/MidnightLib).
     */
    private static final class McmtiAsrConfig implements AsrConfig {
        @Override
        public @NotNull String apiKey() {
            String k = McmtiConfig.apiKey;
            return k == null ? "" : k;
        }

        @Override
        public @NotNull String model() {
            String m = McmtiConfig.model;
            return m == null ? "" : m;
        }

        @Override
        public @NotNull String language() {
            String l = McmtiConfig.language;
            return l == null ? "" : l;
        }

        @Override
        public int requestTimeoutMs() {
            return McmtiConfig.requestTimeoutMs;
        }

        @Override
        public @NotNull String httpProxy() {
            String p = McmtiConfig.httpProxy;
            return p == null ? "" : p;
        }

        @Override
        public @NotNull String transcriptionPrompt() {
            String p = McmtiConfig.transcriptionPrompt;
            return p == null ? "" : p;
        }
    }
}
