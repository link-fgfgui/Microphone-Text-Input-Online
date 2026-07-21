package me.jaffe2718.mcmti.asr.openai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.jaffe2718.mcmti.asr.AsrException;
import me.jaffe2718.mcmti.asr.AsrHttpClients;
import me.jaffe2718.mcmti.asr.AsrRequest;
import me.jaffe2718.mcmti.asr.AsrResult;
import me.jaffe2718.mcmti.asr.SpeechAsrClient;
import me.jaffe2718.mcmti.asr.WavAudio;
import me.jaffe2718.mcmti.util.AudioRecorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * OpenAI-compatible ASR client for Qwen3-ASR (vLLM / DashScope) and similar backends.
 * <p>
 * Always uses {@code POST /audio/transcriptions} (multipart form).
 *
 * <p>Typical endpoints:
 * <ul>
 *   <li>Local vLLM: {@code http://host:port/v1}</li>
 *   <li>DashScope Beijing: {@code https://dashscope.aliyuncs.com/compatible-mode/v1}</li>
 *   <li>DashScope Singapore: {@code https://dashscope-intl.aliyuncs.com/compatible-mode/v1}</li>
 * </ul>
 */
public final class OpenAiCompatibleAsrClient implements SpeechAsrClient {
    public static final String PROVIDER_ID = "openai_compatible";
    public static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public static final String DEFAULT_MODEL = "qwen3-asr-flash";

    private final @NotNull String baseUrl;
    private final @NotNull String apiKey;
    private final @NotNull String model;
    /** Multipart {@code prompt}: vocabulary / topic hint, not a chat system message. */
    private final @NotNull String transcriptionPrompt;
    private final int timeoutMs;
    private final HttpClient httpClient;

    public OpenAiCompatibleAsrClient(
            @NotNull String baseUrl,
            @NotNull String apiKey,
            @NotNull String model,
            @Nullable String transcriptionPrompt,
            int timeoutMs
    ) {
        this(baseUrl, apiKey, model, transcriptionPrompt, timeoutMs, null);
    }

    public OpenAiCompatibleAsrClient(
            @NotNull String baseUrl,
            @NotNull String apiKey,
            @NotNull String model,
            @Nullable String transcriptionPrompt,
            int timeoutMs,
            @Nullable String httpProxy
    ) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model.isBlank() ? DEFAULT_MODEL : model.trim();
        this.transcriptionPrompt = transcriptionPrompt == null ? "" : transcriptionPrompt.trim();
        this.timeoutMs = Math.max(1_000, timeoutMs);
        try {
            this.httpClient = AsrHttpClients.create(this.timeoutMs, httpProxy);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid HTTP proxy for OpenAI-compatible ASR: " + e.getMessage(), e);
        }
    }

    @Override
    public @NotNull String providerId() {
        return PROVIDER_ID;
    }

    /**
     * Local vLLM often has no API key; only base URL is required.
     */
    @Override
    public boolean isReady() {
        return !baseUrl.isEmpty();
    }

    @Override
    public @NotNull AsrResult transcribe(@NotNull AsrRequest request) throws AsrException {
        if (!isReady()) {
            throw new AsrException("OpenAI-compatible ASR is not configured (missing API base URL)");
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

        return transcribeViaTranscriptions(wav, request.language());
    }

    private @NotNull AsrResult transcribeViaTranscriptions(byte @NotNull [] wav, @Nullable String language)
            throws AsrException {
        String boundary = "----mcmti" + UUID.randomUUID().toString().replace("-", "");
        byte[] multipart = buildTranscriptionsMultipart(boundary, wav, language);

        URI uri = URI.create(baseUrl + "/audio/transcriptions");
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart));
        applyAuth(builder);

        HttpResponse<String> response = send(builder.build());
        ensure2xx(response);
        return parseTranscriptionResponse(response.body());
    }

    private byte @NotNull [] buildTranscriptionsMultipart(
            @NotNull String boundary,
            byte @NotNull [] wav,
            @Nullable String language
    ) throws AsrException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeFormFile(out, boundary, "file", "audio.wav", WavAudio.MIME_WAV, wav);
            writeFormField(out, boundary, "model", model);
            writeFormField(out, boundary, "response_format", "json");
            String lang = normalizeLanguageHint(language);
            if (lang != null) {
                writeFormField(out, boundary, "language", lang);
            }
            if (!transcriptionPrompt.isBlank()) {
                // OpenAI transcriptions "prompt": domain vocabulary / topic / prior segment —
                // not chat system instructions (no "summarize", roleplay, output format, etc.)
                writeFormField(out, boundary, "prompt", transcriptionPrompt);
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new AsrException("Failed to build multipart body", e);
        }
    }

    private static void writeFormField(
            @NotNull ByteArrayOutputStream out,
            @NotNull String boundary,
            @NotNull String name,
            @NotNull String value
    ) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFormFile(
            @NotNull ByteArrayOutputStream out,
            @NotNull String boundary,
            @NotNull String name,
            @NotNull String filename,
            @NotNull String contentType,
            byte @NotNull [] data
    ) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name
                + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void applyAuth(HttpRequest.@NotNull Builder builder) {
        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
    }

    private @NotNull HttpResponse<String> send(@NotNull HttpRequest request) throws AsrException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AsrException("OpenAI-compatible ASR network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AsrException("OpenAI-compatible ASR request interrupted", e);
        }
    }

    private static void ensure2xx(@NotNull HttpResponse<String> response) throws AsrException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AsrException(
                    "OpenAI-compatible ASR HTTP " + response.statusCode() + ": " + truncate(response.body(), 500),
                    response.statusCode()
            );
        }
    }

    private static @NotNull AsrResult parseTranscriptionResponse(@Nullable String body) throws AsrException {
        if (body == null || body.isBlank()) {
            throw new AsrException("OpenAI-compatible transcriptions returned an empty response body");
        }
        // Some servers return plain text when response_format is omitted
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            return new AsrResult(trimmed, null);
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            throwIfErrorObject(root);
            String text = root.has("text") && !root.get("text").isJsonNull()
                    ? root.get("text").getAsString()
                    : "";
            String language = root.has("language") && !root.get("language").isJsonNull()
                    ? root.get("language").getAsString()
                    : null;
            return new AsrResult(text == null ? "" : text.trim(), language);
        } catch (AsrException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AsrException("Failed to parse transcriptions response: " + truncate(body, 300), e);
        }
    }

    private static void throwIfErrorObject(@NotNull JsonObject root) throws AsrException {
        if (root.has("error") && root.get("error").isJsonObject()) {
            JsonObject error = root.getAsJsonObject("error");
            String msg = error.has("message") ? error.get("message").getAsString() : error.toString();
            throw new AsrException("OpenAI-compatible ASR error: " + msg);
        }
    }

    /**
     * @return BCP-47-ish short code for transcriptions, or null to omit
     */
    static @Nullable String normalizeLanguageHint(@Nullable String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String lang = language.trim().toLowerCase(Locale.ROOT);
        if (lang.equals("auto") || lang.equals("detect") || lang.equals("none")) {
            return null;
        }
        if (lang.startsWith("zh") || lang.equals("cn") || lang.equals("chinese")) {
            return "zh";
        }
        if (lang.startsWith("en") || lang.equals("english")) {
            return "en";
        }
        // pass through short codes like ja, ko, yue
        if (lang.length() <= 16 && lang.matches("[a-z]{2,3}(-[a-z0-9]+)?")) {
            return lang;
        }
        return lang;
    }

    private static @NotNull String normalizeBaseUrl(@NotNull String baseUrl) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static @NotNull String truncate(@Nullable String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
