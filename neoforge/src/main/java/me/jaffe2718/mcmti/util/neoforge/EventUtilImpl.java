package me.jaffe2718.mcmti.util.neoforge;

import me.jaffe2718.mcmti.event.EventType;
import me.jaffe2718.mcmti.neoforge.MicrophoneTextInputNeoForge;
import me.jaffe2718.mcmti.neoforge.event.SpeechRecognizerEvent;
import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@SuppressWarnings("unused")
public abstract class EventUtilImpl {

    public static void triggerEvent(@NotNull EventType event, Object... args) throws IllegalArgumentException {
        switch (event) {
            case SPEECH_RECOGNIZER_REGISTERED -> {
                if (args.length == 3 &&
                        args[0] instanceof Integer defaultPriority &&
                        args[1] instanceof Integer priority &&
                        args[2] instanceof SpeechRecognizer recognizer) {
                    MicrophoneTextInputNeoForge.getEventBus().post(new SpeechRecognizerEvent.Registered(defaultPriority, priority, recognizer));
                } else {
                    throw new IllegalArgumentException(String.format("Invalid arguments %s for %s event", Arrays.toString(args), event));
                }
            }
            case SPEECH_RECOGNIZER_ACTIVATED -> {
                if (args.length == 1 && args[0] instanceof SpeechRecognizer recognizer) {
                    MicrophoneTextInputNeoForge.getEventBus().post(new SpeechRecognizerEvent.Activated(recognizer));
                } else {
                    throw new IllegalArgumentException(String.format("Invalid arguments %s for %s event", Arrays.toString(args), event));
                }
            }
            case SPEECH_RECOGNIZER_DEACTIVATED -> {
                if (args.length == 1 && args[0] instanceof SpeechRecognizer recognizer) {
                    MicrophoneTextInputNeoForge.getEventBus().post(new SpeechRecognizerEvent.Deactivated(recognizer));
                } else {
                    throw new IllegalArgumentException(String.format("Invalid arguments %s for %s event", Arrays.toString(args), event));
                }
            }
            case SPEECH_RECOGNIZER_TRANSCRIBED -> {
                if (args.length == 3 &&
                        args[0] instanceof SpeechRecognizer recognizer &&
                        args[1] instanceof float[] audio &&
                        args[2] instanceof String transcription) {
                    MicrophoneTextInputNeoForge.getEventBus().post(new SpeechRecognizerEvent.Transcribed(recognizer, audio, transcription));
                } else {
                    throw new IllegalArgumentException(String.format("Invalid arguments %s for %s event", Arrays.toString(args), event));
                }
            }
            case ALL_SPEECH_RECOGNIZERS_DEREGISTERED -> {
                if (args instanceof Identifier[] ids) {
                    MicrophoneTextInputNeoForge.getEventBus().post(new SpeechRecognizerEvent.Deregistered(ids));
                } else {
                    throw new IllegalArgumentException(String.format("Invalid arguments %s for %s event", Arrays.toString(args), event));
                }
            }
        }
    }
}
