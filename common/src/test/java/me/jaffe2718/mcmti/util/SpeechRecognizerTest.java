package me.jaffe2718.mcmti.util;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
    void lowerPriorityRegistrationAfterStartupDoesNotDisturbActiveInstance() {
        SpeechRecognizer.register(10, id("initial"), WorkingRecognizer::new);
        SpeechRecognizer.init();
        assertEquals(id("initial"), SpeechRecognizer.getInstanceID());

        SpeechRecognizer.register(20, id("late"), LateRecognizer::new);

        assertEquals(id("initial"), SpeechRecognizer.getInstanceID());
        assertEquals("working", SpeechRecognizer.recognize(new float[]{0.25f}));
    }

    @Test
    void disabledHigherPriorityRegistrationDoesNotDisturbActiveInstance() {
        SpeechRecognizer.register(10, id("initial"), WorkingRecognizer::new);
        SpeechRecognizer.init();
        assertEquals(id("initial"), SpeechRecognizer.getInstanceID());

        SpeechRecognizer.register(0, id("disabled"), DisabledRecognizer::new);

        assertEquals(id("initial"), SpeechRecognizer.getInstanceID());
        assertEquals("working", SpeechRecognizer.recognize(new float[]{0.25f}));
    }

    @Test
    void registrationAfterStartupActivatesOnlyWhenRecognized() {
        CountingRecognizer.calls = 0;
        CountingRecognizer.activations = 0;
        SpeechRecognizer.register(10, id("initial"), WorkingRecognizer::new);
        SpeechRecognizer.init();
        assertEquals(id("initial"), SpeechRecognizer.getInstanceID());

        // Registering a higher-priority recognizer takes over the instance id
        // immediately, but activation stays lazy until the next recognize() call.
        SpeechRecognizer.register(0, id("counting"), CountingRecognizer::new);

        assertEquals(id("counting"), SpeechRecognizer.getInstanceID());
        assertEquals(0, CountingRecognizer.activations);
        assertEquals("counting", SpeechRecognizer.recognize(new float[]{0.25f}));
        assertEquals(1, CountingRecognizer.activations);
        assertEquals(1, CountingRecognizer.calls);
    }

    @Test
    void emptyAudioDoesNotInvokeRecognizer() {
        CountingRecognizer.calls = 0;
        SpeechRecognizer.register(0, id("counting"), CountingRecognizer::new);
        SpeechRecognizer.init();

        assertEquals("", SpeechRecognizer.recognize(new float[0]));
        assertEquals(0, CountingRecognizer.calls);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mcmti_test", path);
    }

    private abstract static class TestRecognizer extends SpeechRecognizer {
        private TestRecognizer(ResourceLocation id) {
            super(id);
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        protected @NotNull Component availableToast() {
            return Component.literal("ready");
        }

        @Override
        protected @NotNull Component unavailableToast() {
            return Component.literal("unavailable");
        }
    }

    private static final class FailingRecognizer extends TestRecognizer {
        private FailingRecognizer(ResourceLocation id) {
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

    private static final class DisabledRecognizer extends TestRecognizer {
        private DisabledRecognizer(ResourceLocation id) {
            super(id);
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "disabled";
        }
    }

    private static class WorkingRecognizer extends TestRecognizer {
        private WorkingRecognizer(ResourceLocation id) {
            super(id);
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "working";
        }
    }

    private static final class LateRecognizer extends TestRecognizer {
        private LateRecognizer(ResourceLocation id) {
            super(id);
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "late";
        }
    }

    private static final class CountingRecognizer extends TestRecognizer {
        private static int calls;
        private static int activations;

        private CountingRecognizer(ResourceLocation id) {
            super(id);
        }

        @Override
        protected void activate() throws IOException {
            activations++;
            super.activate();
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            calls++;
            return "counting";
        }
    }
}
