package me.jaffe2718.mcmti.util;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeechRecognizerTest {

    @AfterEach
    void cleanRegistry() {
        SpeechRecognizer.deregister();
    }

    @Test
    void fallsBackWhenHigherPriorityActivationFails() {
        SpeechRecognizer.register(0, id("failing"), FailingRecognizer::new);
        SpeechRecognizer.register(1, id("working"), WorkingRecognizer::new);

        SpeechRecognizer.init();

        assertEquals(id("working"), SpeechRecognizer.getInstanceID());
        assertEquals("working", SpeechRecognizer.recognize(new float[]{0.25f}));
    }

    @Test
    void activatesHigherPriorityRecognizerRegisteredAfterStartup() {
        SpeechRecognizer.register(10, id("initial"), WorkingRecognizer::new);
        SpeechRecognizer.init();

        SpeechRecognizer.register(0, id("late"), LateRecognizer::new);

        assertEquals(id("late"), SpeechRecognizer.getInstanceID());
        assertEquals("late", SpeechRecognizer.recognize(new float[]{0.25f}));
    }

    @Test
    void emptyAudioDoesNotInvokeRecognizer() {
        CountingRecognizer.calls = 0;
        SpeechRecognizer.register(0, id("counting"), CountingRecognizer::new);
        SpeechRecognizer.init();

        assertEquals("", SpeechRecognizer.recognize(new float[0]));
        assertEquals(0, CountingRecognizer.calls);
    }

    private static Identifier id(String path) {
        return Identifier.of("mcmti_test", path);
    }

    private abstract static class TestRecognizer extends SpeechRecognizer {
        private TestRecognizer(Identifier id) {
            super(id);
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        protected @NotNull Text availableToast() {
            return Text.literal("ready");
        }

        @Override
        protected @NotNull Text unavailableToast() {
            return Text.literal("unavailable");
        }
    }

    private static final class FailingRecognizer extends TestRecognizer {
        private FailingRecognizer(Identifier id) {
            super(id);
        }

        @Override
        protected void activate() throws IOException {
            throw new IOException("expected failure");
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "failing";
        }
    }

    private static class WorkingRecognizer extends TestRecognizer {
        private WorkingRecognizer(Identifier id) {
            super(id);
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "working";
        }
    }

    private static final class LateRecognizer extends TestRecognizer {
        private LateRecognizer(Identifier id) {
            super(id);
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "late";
        }
    }

    private static final class CountingRecognizer extends TestRecognizer {
        private static int calls;

        private CountingRecognizer(Identifier id) {
            super(id);
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            calls++;
            return "counting";
        }
    }
}
