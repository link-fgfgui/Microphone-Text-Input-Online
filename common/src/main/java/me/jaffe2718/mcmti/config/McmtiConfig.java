package me.jaffe2718.mcmti.config;

import eu.midnightdust.lib.config.MidnightConfig;
import me.jaffe2718.mcmti.asr.mimo.MimoAsrClient;
import me.jaffe2718.mcmti.asr.openai.OpenAiCompatibleAsrClient;
import me.jaffe2718.mcmti.util.SpeechRecognizer;

public class McmtiConfig extends MidnightConfig {

    public enum Mode {
        AUTO_SEND,
        RELEASE_KEY_TO_SEND,
        RELEASE_KEY_TO_INPUT,
    }

    /**
     * Online ASR provider. Add new enum values when more backends are implemented.
     */
    public enum AsrProvider {
        /** Xiaomi MiMo-V2.5-ASR ({@code input_audio} + {@code asr_options}). */
        MIMO,
        /**
         * OpenAI-compatible ASR (Qwen3-ASR / vLLM / DashScope, etc.).
         * Uses {@code audio_url} chat completions or {@code /audio/transcriptions}.
         */
        OPENAI_COMPATIBLE,
    }

    @Override
    public void writeChanges() {
        super.writeChanges();
        Thread.ofVirtual().start(SpeechRecognizer::init);
    }

    @Entry(category = "general")
    public static AsrProvider asrProvider = AsrProvider.MIMO;

    @Entry(category = "general", width = 128)
    public static String apiBaseUrl = MimoAsrClient.DEFAULT_BASE_URL;

    /**
     * API key. Required for MiMo / cloud providers; optional for local OpenAI-compatible servers.
     */
    @Entry(category = "general", width = 128)
    public static String apiKey = "";

    @Entry(category = "general", width = 64)
    public static String model = MimoAsrClient.DEFAULT_MODEL;

    /**
     * Language hint for ASR.
     * MiMo: {@code auto}/{@code zh}/{@code en}. OpenAI-compatible: provider-dependent ({@code auto} omits language).
     */
    @Entry(category = "general", width = 15)
    public static String language = "auto";

    @Entry(category = "general", min = 1000, max = 300_000)
    public static int requestTimeoutMs = 60_000;

    /**
     * OpenAI-compatible endpoint style (ignored by MiMo).
     */
    @Entry(category = "general")
    @Condition(requiredOption = "asrProvider", requiredValue = "OPENAI_COMPATIBLE")
    public static OpenAiCompatibleAsrClient.ApiStyle openaiApiStyle = OpenAiCompatibleAsrClient.ApiStyle.CHAT_COMPLETIONS;

    /**
     * System / context prompt for OpenAI-compatible ASR (hotwords, domain terms).
     * Chat completions: system message. Transcriptions: {@code prompt} field.
     */
    @Entry(category = "general", width = 4096)
    @Condition(requiredOption = "asrProvider", requiredValue = "OPENAI_COMPATIBLE")
    public static String systemPrompt = "You are a speech-to-text engine for Minecraft in-game chat. Transcribe the speaker's words only into one plain chat line ready to send. Prefer correct Minecraft terms when the sound matches: creeper, zombie, skeleton, enderman, nether, end, villager, diamond, netherite, redstone, elytra, totem, shulker, portal, raid, village, minecart, crafting table, enchanting table, respawn, PvP, AFK, TPS, FPS, lag, server, lobby, spawn, home, warp, tpa, tpahere, msg, whisper, team, party, guild. Keep original language (Chinese or English or mixed). Output a single line of plain text only: no markdown, no quotes, no labels, no brackets, no timestamps, no speaker tags, no translation unless spoken, no explanations, no filler such as um or uh. Prefer Arabic digits for numbers. If nothing intelligible was said, output nothing.";

    @Entry(category = "general")
    public static Mode mode = Mode.RELEASE_KEY_TO_SEND;

    @Entry(category = "general", min = 1024, max = 65536)
    @Condition(requiredOption = "mode", requiredValue = "AUTO_SEND")
    public static int recordCycleMs = 5000;    // unit: ms, (sampleRate = 16000Hz), default: 5s

    @Entry(category = "general", min = 64, max = 4096)
    @Condition(requiredOption = "mode", requiredValue = {"RELEASE_KEY_TO_SEND", "RELEASE_KEY_TO_INPUT"})
    public static int recordBufferSize = 1024;    // unit: byte, default: 1024 bytes

    @Entry(category = "general", width = 64)
    public static String prefix = "🎤";

    /**
     * Suggested defaults when switching to OpenAI-compatible (for docs / manual use).
     */
    public static void applyOpenAiCompatibleSuggestedDefaults() {
        apiBaseUrl = OpenAiCompatibleAsrClient.DEFAULT_BASE_URL;
        model = OpenAiCompatibleAsrClient.DEFAULT_MODEL;
        openaiApiStyle = OpenAiCompatibleAsrClient.ApiStyle.CHAT_COMPLETIONS;
    }
}
