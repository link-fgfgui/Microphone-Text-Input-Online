package me.jaffe2718.mcmti.util;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import me.jaffe2718.mcmti.asr.AsrException;
import me.jaffe2718.mcmti.config.McmtiConfig;
import me.jaffe2718.mcmti.event.EventType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.function.Function;

public abstract class SpeechRecognizer {

    private static int instanceID = Integer.MAX_VALUE;

    private static boolean initialized;

    private static final Object RECONFIGURE_LOCK = new Object();

    private static final Text GLOBAL_UNAVAILABLE_TOAST = Text.translatable("message.mcmti.noRecognizerAvailable");

    private static final TreeMap<Integer, SpeechRecognizer> recognizerRegistry = new TreeMap<>();

    private static final Map<Identifier, Integer> registeredIds = new HashMap<>();

    public final @NotNull Identifier id;

    private volatile boolean active;

    protected SpeechRecognizer(@NotNull Identifier regId) {
        this.id = regId;
    }

    public abstract boolean enabled();

    protected abstract @NotNull Text availableToast();

    protected abstract @NotNull Text unavailableToast();

    public abstract @NotNull String transcribe(float[] audio);

    protected boolean available() {
        return this.enabled() && this.active;
    }

    protected void activate() throws IOException {
        this.active = true;
        if (MinecraftClient.getInstance() != null
                && MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(this.availableToast(), true);
        }
        triggerEvent(EventType.SPEECH_RECOGNIZER_ACTIVATED, this);
    }

    protected void deactivate() {
        if (this.active) {
            this.active = false;
            triggerEvent(EventType.SPEECH_RECOGNIZER_DEACTIVATED, this);
        }
    }

    @Override
    public String toString() {
        return String.format("%s[hash=0x%X,id=%s,active=%s]",
                this.getClass().getSimpleName(), System.identityHashCode(this), id, active);
    }

    public static void register(int priority, @NotNull Identifier regId,
                                @NotNull Function<Identifier, ? extends SpeechRecognizer> constructor)
            throws IllegalStateException {
        final int defaultPriority = priority;
        SpeechRecognizer recognizer = constructor.apply(regId);
        if (recognizer == null) {
            throw new IllegalStateException("Recognizer constructor returned null");
        }
        int assignedPriority;
        boolean reinitialize;
        synchronized (SpeechRecognizer.class) {
            if (registeredIds.containsKey(regId)) {
                throw new IllegalStateException(
                        String.format("The id of recognizer \"%s\" conflicts with an existing recognizer", recognizer));
            }
            while (recognizerRegistry.containsKey(priority)) {
                if (priority == Integer.MAX_VALUE) {
                    throw new IllegalStateException("No recognizer priority is available");
                }
                priority++;
            }
            assignedPriority = priority;
            recognizerRegistry.put(assignedPriority, recognizer);
            registeredIds.put(regId, assignedPriority);
            reinitialize = initialized;
        }
        MicrophoneTextInput.LOGGER.info("Recognizer {} registered with priority {}", recognizer, assignedPriority);
        triggerEvent(EventType.SPEECH_RECOGNIZER_REGISTERED, defaultPriority, assignedPriority, recognizer);
        if (reinitialize) {
            init();
        }
    }

    public static void deregister() {
        Identifier[] allIds;
        List<SpeechRecognizer> recognizers;
        synchronized (RECONFIGURE_LOCK) {
            synchronized (SpeechRecognizer.class) {
                allIds = registeredIds.keySet().toArray(new Identifier[0]);
                recognizers = new ArrayList<>(recognizerRegistry.values());
                recognizerRegistry.clear();
                registeredIds.clear();
                instanceID = Integer.MAX_VALUE;
                initialized = false;
            }
            for (SpeechRecognizer recognizer : recognizers) {
                synchronized (recognizer) {
                    recognizer.deactivate();
                }
            }
        }
        triggerEvent(EventType.ALL_SPEECH_RECOGNIZERS_DEREGISTERED, (Object[]) allIds);
    }

    public static void init() {
        synchronized (RECONFIGURE_LOCK) {
            List<Map.Entry<Integer, SpeechRecognizer>> entries;
            synchronized (SpeechRecognizer.class) {
                initialized = true;
                entries = new ArrayList<>(recognizerRegistry.entrySet());
            }
            for (Map.Entry<Integer, SpeechRecognizer> entry : entries) {
                synchronized (entry.getValue()) {
                    entry.getValue().deactivate();
                }
            }
            SpeechRecognizer selected = null;
            int selectedPriority = Integer.MAX_VALUE;
            Integer firstEnabledPriority = null;
            for (Map.Entry<Integer, SpeechRecognizer> entry : entries) {
                SpeechRecognizer recognizer = entry.getValue();
                if (recognizer.enabled() && firstEnabledPriority == null) {
                    firstEnabledPriority = entry.getKey();
                }
                if (recognizer.enabled() && selected == null) {
                    try {
                        synchronized (recognizer) {
                            recognizer.activate();
                            if (recognizer.available()) {
                                selected = recognizer;
                                selectedPriority = entry.getKey();
                            }
                        }
                        if (selected != null) {
                            continue;
                        }
                        MicrophoneTextInput.LOGGER.warn("Recognizer {} did not become available after activation", recognizer);
                    } catch (Exception e) {
                        MicrophoneTextInput.LOGGER.error("Failed to activate recognizer {}", recognizer, e);
                    }
                }
                if (recognizer != selected) {
                    synchronized (recognizer) {
                        recognizer.deactivate();
                    }
                }
            }
            synchronized (SpeechRecognizer.class) {
                instanceID = selected != null ? selectedPriority
                        : firstEnabledPriority != null ? firstEnabledPriority : Integer.MAX_VALUE;
            }
        }
    }

    public static @NotNull String recognize(float @NotNull [] audio) {
        RecognizeOutcome outcome = recognizeOutcome(audio);
        return outcome.text() == null ? "" : outcome.text();
    }

    public static @NotNull RecognizeOutcome recognizeOutcome(float @NotNull [] audio) {
        if (audio.length == 0) {
            return RecognizeOutcome.success("");
        }
        SpeechRecognizer recognizer;
        synchronized (SpeechRecognizer.class) {
            recognizer = recognizerRegistry.get(instanceID);
        }
        if (recognizer != null && recognizer.enabled()) {
            try {
                String transcription;
                synchronized (recognizer) {
                    if (!recognizer.available()) {
                        recognizer.activate();
                    }
                    if (!recognizer.available()) {
                        return RecognizeOutcome.success("");
                    }
                    transcription = recognizer.transcribe(audio);
                }
                triggerEvent(EventType.SPEECH_RECOGNIZER_TRANSCRIBED, recognizer, audio, transcription);
                if (McmtiConfig.encodingRepair) {
                    transcription = repairEncoding(transcription, McmtiConfig.srcEncoding, McmtiConfig.dstEncoding);
                }
                return RecognizeOutcome.success(transcription);
            } catch (AsrException e) {
                MicrophoneTextInput.LOGGER.error("Speech recognition failed: {}", e.getMessage());
                return RecognizeOutcome.failure(formatActionBarError(e.getMessage()));
            } catch (Exception e) {
                MicrophoneTextInput.LOGGER.error("Unexpected speech recognition error", e);
                String detail = e.getMessage() == null || e.getMessage().isBlank()
                        ? e.getClass().getSimpleName()
                        : e.getMessage();
                return RecognizeOutcome.failure(formatActionBarError(detail));
            }
        }
        MicrophoneTextInput.LOGGER.warn("No enabled SpeechRecognizer found");
        return RecognizeOutcome.success("");
    }

    public static boolean instanceAvailable() {
        SpeechRecognizer recognizer;
        synchronized (SpeechRecognizer.class) {
            recognizer = recognizerRegistry.get(instanceID);
        }
        return recognizer != null && recognizer.available();
    }

    public static @NotNull Text instanceUnavailableToast() {
        SpeechRecognizer recognizer;
        synchronized (SpeechRecognizer.class) {
            recognizer = recognizerRegistry.get(instanceID);
        }
        return recognizer != null ? recognizer.unavailableToast() : GLOBAL_UNAVAILABLE_TOAST;
    }

    public static synchronized @Nullable Identifier getInstanceID() {
        if (recognizerRegistry.containsKey(instanceID)) {
            return recognizerRegistry.get(instanceID).id;
        }
        return null;
    }

    @SuppressWarnings("unused")
    public static synchronized int queryPriority(Identifier id) throws NoSuchElementException {
        if (registeredIds.containsKey(id)) {
            return registeredIds.get(id);
        }
        throw new NoSuchElementException(String.format("Recognizer \"%s\" not registered", id));
    }

    // --- RecognizeOutcome ---

    private static final int ACTION_BAR_ERROR_MAX = 96;

    public record RecognizeOutcome(@Nullable String text, @Nullable String errorDetail) {
        public static @NotNull RecognizeOutcome success(@NotNull String text) {
            return new RecognizeOutcome(text, null);
        }

        public static @NotNull RecognizeOutcome failure(@NotNull String detail) {
            return new RecognizeOutcome(null, detail);
        }

        public boolean failed() {
            return errorDetail != null;
        }

        public boolean hasText() {
            return text != null && !text.isEmpty();
        }
    }

    static @NotNull String formatActionBarError(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        String oneLine = message.replace('\r', ' ').replace('\n', ' ').replaceAll(" +", " ").trim();
        int colon = oneLine.indexOf(':');
        if (colon >= 0) {
            oneLine = oneLine.substring(0, colon).trim();
        }
        if (oneLine.isEmpty()) {
            return "unknown error";
        }
        if (oneLine.length() <= ACTION_BAR_ERROR_MAX) {
            return oneLine;
        }
        return oneLine.substring(0, ACTION_BAR_ERROR_MAX) + "...";
    }

    private static @NotNull String repairEncoding(@NotNull String str, String srcEncoding, String dstEncoding) {
        try {
            return new String(str.getBytes(srcEncoding), dstEncoding);
        } catch (UnsupportedEncodingException uee) {
            MicrophoneTextInput.LOGGER.error("Couldn't repair encoding, using default", uee);
            return str;
        }
    }

    private static void triggerEvent(EventType event, Object... args) {
        try {
            EventUtil.triggerEvent(event, args);
        } catch (RuntimeException e) {
            MicrophoneTextInput.LOGGER.error("Listener failed while handling {}", event, e);
        }
    }
}
