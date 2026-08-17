package me.jaffe2718.mcmti.config;

import eu.midnightdust.lib.config.EntryInfo;
import eu.midnightdust.lib.config.MidnightConfig;
import eu.midnightdust.lib.config.MidnightConfigListWidget;
import eu.midnightdust.lib.config.MidnightConfigScreen;
import me.jaffe2718.mcmti.asr.mimo.MimoAsrClient;
import me.jaffe2718.mcmti.util.AudioRecorder;
import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import java.util.List;

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
         * Uses multipart {@code /audio/transcriptions}.
         */
        OPENAI_COMPATIBLE,
    }

    @Override
    public void writeChanges() {
        super.writeChanges();
        Thread.ofVirtual().start(SpeechRecognizer::init);
    }

    /**
     * Manual microphone open/close controls on the config screen.
     * Mic still auto-opens on first record; these buttons are for permission / device recovery.
     */
    @Override
    public void onTabInit(String tabName, MidnightConfigListWidget list, MidnightConfigScreen screen) {
        if (!"general".equals(tabName)) {
            return;
        }

        int x = Math.max(0, screen.width - 185);
        int btnW = 72;
        int gap = 6;

        final Button[] pair = new Button[2];

        pair[0] = Button.builder(
                Component.translatable("mcmti.midnightconfig.button.loadMicrophone"),
                button -> {
                    boolean ok = AudioRecorder.ensureOpen();
                    notifyPlayer(ok
                            ? Component.translatable("message.mcmti.microphoneLoaded")
                            : Component.translatable("message.mcmti.audioInputDeviceLoadFailed"));
                    refreshMicButtons(pair[0], pair[1]);
                }
        ).bounds(x, 0, btnW, 20).build();

        pair[1] = Button.builder(
                Component.translatable("mcmti.midnightconfig.button.unloadMicrophone"),
                button -> {
                    AudioRecorder.destroy();
                    notifyPlayer(Component.translatable("message.mcmti.microphoneUnloaded"));
                    refreshMicButtons(pair[0], pair[1]);
                }
        ).bounds(x + btnW + gap, 0, btnW, 20).build();

        refreshMicButtons(pair[0], pair[1]);

        list.addButton(
                List.of(pair[0], pair[1]),
                Component.translatable("mcmti.midnightconfig.microphone"),
                new EntryInfo(null, modid)
        );
    }

    private static void refreshMicButtons(Button loadBtn, Button unloadBtn) {
        boolean open = AudioRecorder.isOpen();
        if (loadBtn != null) {
            loadBtn.active = !open;
        }
        if (unloadBtn != null) {
            unloadBtn.active = open;
        }
    }

    private static void notifyPlayer(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, true);
        }
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
     * Optional HTTP proxy for online ASR requests.
     * Empty = direct. {@code http://} is optional.
     * Examples: {@code 127.0.0.1:7890}, {@code user:pass@127.0.0.1:7890}.
     */
    @Entry(category = "general", width = 128)
    public static String httpProxy = "";

    /**
     * OpenAI-compatible {@code /audio/transcriptions} multipart {@code prompt} field.
     * <p>
     * This is <strong>not</strong> a chat system prompt. Providers treat it as a short
     * vocabulary / domain / continuation hint (proper nouns, topic, previous segment),
     * not as instructions like "summarize" or "output markdown".
     * Empty = omit the field.
     */
    @Entry(category = "general", width = 4096)
    @Condition(requiredOption = "asrProvider", requiredValue = "OPENAI_COMPATIBLE")
    public static String transcriptionPrompt = "Minecraft in-game chat. Expected vocabulary: creeper, zombie, skeleton, enderman, nether, end, villager, diamond, netherite, redstone, elytra, totem, shulker, portal, raid, village, minecart, crafting table, enchanting table, respawn, PvP, AFK, TPS, FPS, lag, server, lobby, spawn, home, warp, tpa, tpahere, msg, whisper, team, party, guild.";

    @Entry(category = "general")
    public static Mode mode = Mode.RELEASE_KEY_TO_SEND;

    @Entry(category = "general", min = 1024, max = 65536)
    @Condition(requiredOption = "mode", requiredValue = "AUTO_SEND")
    public static int recordCycleMs = 5000;    // unit: ms, (sampleRate = 16000Hz), default: 5s

    @Entry(category = "general", min = 64, max = 4096)
    @Condition(requiredOption = "mode", requiredValue = {"RELEASE_KEY_TO_SEND", "RELEASE_KEY_TO_INPUT"})
    public static int recordBufferSize = 1024;    // unit: byte, default: 1024 bytes

    @Entry(category = "general", width = 64)
    public static String prefix = "[🎙]";

    @Entry(category = "general")
    public static boolean encodingRepair = false;

    @Entry(category = "general", width = 16)
    @Condition(requiredOption = "encodingRepair")
    public static String srcEncoding = java.nio.charset.Charset.defaultCharset().displayName();

    @Entry(category = "general", width = 16)
    @Condition(requiredOption = "encodingRepair")
    public static String dstEncoding = java.nio.charset.Charset.defaultCharset().displayName();
}
