package me.jaffe2718.mcmti.asr.openai;

import me.jaffe2718.mcmti.asr.AsrException;
import me.jaffe2718.mcmti.asr.AsrRequest;
import me.jaffe2718.mcmti.asr.AsrResult;
import me.jaffe2718.mcmti.asr.WavAudio;
import me.jaffe2718.mcmti.config.McmtiConfig;
import me.jaffe2718.mcmti.util.AudioRecorder;
import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class OpenAiCompatibleSpeechRecognizer extends SpeechRecognizer {

    private volatile @Nullable OpenAiCompatibleAsrClient client;

    public OpenAiCompatibleSpeechRecognizer(@NotNull ResourceLocation regId) {
        super(regId);
    }

    @Override
    public boolean enabled() {
        return McmtiConfig.asrProvider == McmtiConfig.AsrProvider.OPENAI_COMPATIBLE;
    }

    @Override
    protected @NotNull Component availableToast() {
        return Component.translatable("message.mcmti.openAiReady");
    }

    @Override
    protected @NotNull Component unavailableToast() {
        return Component.translatable("message.mcmti.speechRecognizerNotReady");
    }

    @Override
    protected boolean available() {
        return super.available() && this.client != null && this.client.isReady();
    }

    @Override
    protected void activate() throws IOException {
        OpenAiCompatibleAsrClient created = new OpenAiCompatibleAsrClient(
                McmtiConfig.apiBaseUrl,
                McmtiConfig.apiKey,
                McmtiConfig.model,
                McmtiConfig.transcriptionPrompt,
                McmtiConfig.requestTimeoutMs,
                McmtiConfig.httpProxy
        );
        if (!created.isReady()) {
            this.client = null;
            throw new IOException("OpenAI-compatible ASR is not configured");
        }
        this.client = created;
        super.activate();
    }

    @Override
    protected void deactivate() {
        this.client = null;
        super.deactivate();
    }

    @Override
    public @NotNull String transcribe(float[] audio) {
        if (this.client == null || !this.client.isReady()) {
            return "";
        }
        byte[] pcm = WavAudio.floatToPcm16(audio);
        AsrResult result = this.client.transcribe(new AsrRequest(pcm, AudioRecorder.SAMPLE_RATE, McmtiConfig.language));
        return result.text().trim();
    }
}
