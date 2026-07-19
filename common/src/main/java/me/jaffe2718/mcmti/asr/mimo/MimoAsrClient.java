package me.jaffe2718.mcmti.asr.mimo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.jaffe2718.mcmti.MicrophoneTextInput;
import me.jaffe2718.mcmti.asr.AsrException;
import me.jaffe2718.mcmti.asr.AsrRequest;
import me.jaffe2718.mcmti.asr.AsrResult;
import me.jaffe2718.mcmti.asr.SpeechAsrClient;
import me.jaffe2718.mcmti.asr.WavAudio;
import me.jaffe2718.mcmti.util.AudioRecorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Xiaomi MiMo speech recognition client (model {@code mimo-v2.5-asr}).
 * <p>
 * Uses the OpenAI-compatible Chat Completions API:
 * {@code POST {baseUrl}/chat/completions} with {@code input_audio} content
 * and top-level {@code asr_options}.
 *
 * @see <a href="https://mimo.mi.com/docs/en-US/quick-start/usage-guide/audio/Speech-Recognition">MiMo Speech Recognition</a>
 */
public final class MimoAsrClient implements SpeechAsrClient {
    public static final String PROVIDER_ID = "mimo";
    public static final String DEFAULT_BASE_URL = "https://api.xiaomimimo.com/v1";
    public static final String DEFAULT_MODEL = "mimo-v2.5-asr";

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("auto", "zh", "en");

    private final @NotNull String baseUrl;
    private final @NotNull String apiKey;
    private final @NotNull String model;
    private final int timeoutMs;
    private final HttpClient httpClient;

    public MimoAsrClient(
            @NotNull String baseUrl,
            @NotNull String apiKey,
            @NotNull String model,
            int timeoutMs
    ) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey.trim();
        this.model = model.isBlank() ? DEFAULT_MODEL : model.trim();
        this.timeoutMs = Math.max(1_000, timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public @NotNull String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isReady() {
        return !apiKey.isEmpty() && !baseUrl.isEmpty();
    }

    @Override
    public @NotNull AsrResult transcribe(@NotNull AsrRequest request) throws AsrException {
        if (!isReady()) {
            throw new AsrException("MiMo ASR is not configured (missing API key or base URL)");
        }
        if (request.pcmAudio().length == 0) {
            return AsrResult.EMPTY;
        }

        byte[] wav = WavAudio.pcmToWav(
                request.pcmAudio(),
                request.sampleRate() > 0 ? request.sampleRate() : AudioRecorder.SAMPLE_RATE,
                AudioRecorder.CHANNELS,
                AudioRecorder.SAMPLE_SIZE_BITS
        );
        int estimatedBase64 = WavAudio.estimateBase64Length(wav.length);
        if (estimatedBase64 > WavAudio.MAX_BASE64_BYTES) {
            throw new AsrException("Audio payload too large for MiMo ASR (Base64 limit 10 MB, estimated "
                    + estimatedBase64 + " bytes)");
        }

        String dataUrl = WavAudio.toDataUrl(wav, WavAudio.MIME_WAV);
        String language = normalizeLanguage(request.language());
        String body = buildRequestBody(dataUrl, language);

        URI uri = URI.create(baseUrl + "/chat/completions");
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AsrException("MiMo ASR network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AsrException("MiMo ASR request interrupted", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AsrException(
                    "MiMo ASR HTTP " + response.statusCode() + ": " + truncate(response.body(), 500),
                    response.statusCode()
            );
        }

        return parseResponse(response.body());
    }

    private @NotNull String buildRequestBody(@NotNull String dataUrl, @NotNull String language) {
        JsonObject inputAudio = new JsonObject();
        inputAudio.addProperty("data", dataUrl);

        JsonObject contentItem = new JsonObject();
        contentItem.addProperty("type", "input_audio");
        contentItem.add("input_audio", inputAudio);

        JsonArray content = new JsonArray();
        content.add(contentItem);

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.add("content", content);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject asrOptions = new JsonObject();
        asrOptions.addProperty("language", language);

        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.add("messages", messages);
        root.add("asr_options", asrOptions);
        root.addProperty("stream", false);
        return root.toString();
    }

    private static @NotNull AsrResult parseResponse(@Nullable String body) throws AsrException {
        if (body == null || body.isBlank()) {
            throw new AsrException("MiMo ASR returned an empty response body");
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error") && root.get("error").isJsonObject()) {
                JsonObject error = root.getAsJsonObject("error");
                String msg = error.has("message") ? error.get("message").getAsString() : error.toString();
                throw new AsrException("MiMo ASR error: " + msg);
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return AsrResult.EMPTY;
            }
            JsonObject choice0 = choices.get(0).getAsJsonObject();
            JsonObject message = choice0.getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                return AsrResult.EMPTY;
            }
            JsonElement contentEl = message.get("content");
            String text = extractTextContent(contentEl);
            return new AsrResult(text == null ? "" : text.trim(), null);
        } catch (AsrException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AsrException("Failed to parse MiMo ASR response: " + truncate(body, 300), e);
        }
    }

    /**
     * Content may be a plain string or a multimodal array; ASR typically returns a string.
     */
    private static @Nullable String extractTextContent(@Nullable JsonElement contentEl) {
        if (contentEl == null || contentEl.isJsonNull()) {
            return null;
        }
        if (contentEl.isJsonPrimitive()) {
            return contentEl.getAsString();
        }
        if (contentEl.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement part : contentEl.getAsJsonArray()) {
                if (part.isJsonPrimitive()) {
                    sb.append(part.getAsString());
                } else if (part.isJsonObject()) {
                    JsonObject obj = part.getAsJsonObject();
                    if (obj.has("text")) {
                        sb.append(obj.get("text").getAsString());
                    } else if (obj.has("content")) {
                        sb.append(obj.get("content").getAsString());
                    }
                }
            }
            return sb.toString();
        }
        return contentEl.toString();
    }

    /**
     * Map free-form config language to MiMo's {@code auto}/{@code zh}/{@code en}.
     */
    static @NotNull String normalizeLanguage(@Nullable String language) {
        if (language == null || language.isBlank()) {
            return "auto";
        }
        String lang = language.trim().toLowerCase(Locale.ROOT);
        if (SUPPORTED_LANGUAGES.contains(lang)) {
            return lang;
        }
        // common aliases
        if (lang.startsWith("zh") || lang.equals("cn") || lang.equals("chinese")) {
            return "zh";
        }
        if (lang.startsWith("en") || lang.equals("english")) {
            return "en";
        }
        MicrophoneTextInput.LOGGER.debug("Unsupported MiMo language '{}', falling back to auto", language);
        return "auto";
    }

    private static @NotNull String normalizeBaseUrl(@NotNull String baseUrl) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? DEFAULT_BASE_URL : url;
    }

    private static @NotNull String truncate(@Nullable String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
