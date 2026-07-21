package me.jaffe2718.mcmti.asr;

import me.jaffe2718.mcmti.asr.mimo.MimoAsrClient;
import me.jaffe2718.mcmti.asr.openai.OpenAiCompatibleAsrClient;
import me.jaffe2718.mcmti.config.McmtiConfig;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for {@link SpeechAsrClient} instances from {@link McmtiConfig}.
 */
public final class SpeechAsrClients {
    private SpeechAsrClients() {}

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
        };
    }
}
