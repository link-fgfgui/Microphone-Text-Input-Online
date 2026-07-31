package me.jaffe2718.mcmti.fabric.event;

import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.util.Identifier;

public final class McmtiSpeechRecognizerEvents {

    public static final Event<RegisterCallback> SPEECH_RECOGNIZER_REGISTERED = EventFactory.createArrayBacked(RegisterCallback.class,
            callbacks -> (defaultPriority, priority, recognizer) -> {
                for (RegisterCallback callback : callbacks) {
                    callback.onTriggered(defaultPriority, priority, recognizer);
                }
            });

    public static final Event<ActivateCallback> SPEECH_RECOGNIZER_ACTIVATED = EventFactory.createArrayBacked(ActivateCallback.class,
            callbacks -> recognizer -> {
                for (ActivateCallback callback : callbacks) {
                    callback.onTriggered(recognizer);
                }
            });

    public static final Event<DeactivateCallback> SPEECH_RECOGNIZER_DEACTIVATED = EventFactory.createArrayBacked(DeactivateCallback.class,
            callbacks -> recognizer -> {
                for (DeactivateCallback callback : callbacks) {
                    callback.onTriggered(recognizer);
                }
            });

    public static final Event<TranscribeFinishedCallback> SPEECH_RECOGNIZER_TRANSCRIBED = EventFactory.createArrayBacked(TranscribeFinishedCallback.class,
            callbacks -> (recognizer, audio, transcription) -> {
                for (TranscribeFinishedCallback callback : callbacks) {
                    callback.onTriggered(recognizer, audio.clone(), transcription);
                }
            });

    public static final Event<DeregisterCallback> ALL_SPEECH_RECOGNIZERS_DEREGISTERED = EventFactory.createArrayBacked(DeregisterCallback.class,
            callbacks -> ids -> {
                for (DeregisterCallback callback : callbacks) {
                    callback.onTriggered(ids.clone());
                }
            });

    @FunctionalInterface
    public interface RegisterCallback {
        void onTriggered(int defaultPriority, int priority, SpeechRecognizer recognizer);
    }

    @FunctionalInterface
    public interface ActivateCallback {
        void onTriggered(SpeechRecognizer recognizer);
    }

    @FunctionalInterface
    public interface DeactivateCallback {
        void onTriggered(SpeechRecognizer recognizer);
    }

    @FunctionalInterface
    public interface TranscribeFinishedCallback {
        void onTriggered(SpeechRecognizer recognizer, float[] audio, String transcription);
    }

    @FunctionalInterface
    public interface DeregisterCallback {
        void onTriggered(Identifier[] ids);
    }
}
