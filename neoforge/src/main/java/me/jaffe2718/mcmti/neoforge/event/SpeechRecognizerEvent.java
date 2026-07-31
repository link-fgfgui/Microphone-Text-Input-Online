package me.jaffe2718.mcmti.neoforge.event;

import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.util.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SpeechRecognizerEvent extends Event implements IModBusEvent {

    @Nullable
    protected final SpeechRecognizer recognizer;

    public SpeechRecognizerEvent(@Nullable SpeechRecognizer recognizer) {
        this.recognizer = recognizer;
    }

    @Nullable
    public SpeechRecognizer getRecognizer() {
        return this.recognizer;
    }

    public static class Registered extends SpeechRecognizerEvent {

        private final int defaultPriority;
        private final int priority;

        public Registered(int defaultPriority, int priority, SpeechRecognizer recognizer) {
            super(recognizer);
            this.defaultPriority = defaultPriority;
            this.priority = priority;
        }

        public int getDefaultPriority() {
            return this.defaultPriority;
        }

        public int getPriority() {
            return this.priority;
        }
    }

    public static class Activated extends SpeechRecognizerEvent {

        public Activated(@NotNull SpeechRecognizer recognizer) {
            super(recognizer);
        }
    }

    public static class Deactivated extends SpeechRecognizerEvent {

        public Deactivated(@NotNull SpeechRecognizer recognizer) {
            super(recognizer);
        }
    }

    public static class Transcribed extends SpeechRecognizerEvent {

        private final float @NotNull [] audio;
        @NotNull
        private final String transcription;

        public Transcribed(@NotNull SpeechRecognizer recognizer, float @NotNull [] audio, @NotNull String transcription) {
            super(recognizer);
            this.audio = audio.clone();
            this.transcription = transcription;
        }

        public float[] getAudio() {
            return this.audio.clone();
        }

        @NotNull
        public String getTranscription() {
            return this.transcription;
        }
    }

    public static class Deregistered extends SpeechRecognizerEvent {

        private final Identifier[] ids;

        public Deregistered(Identifier[] ids) {
            super(null);
            this.ids = ids.clone();
        }

        public Identifier[] getIds() {
            return ids.clone();
        }
    }
}
