package me.jaffe2718.mcmti.test.fabric;

import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

public final class TestRecognizerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SpeechRecognizer.register(0, Identifier.of("mcmti_test", "fabric"), TestRecognizer::new);
    }

    private static final class TestRecognizer extends SpeechRecognizer {
        private TestRecognizer(@NotNull Identifier id) {
            super(id);
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        protected @NotNull Text availableToast() {
            return Text.literal("Fabric test recognizer ready");
        }

        @Override
        protected @NotNull Text unavailableToast() {
            return Text.literal("Fabric test recognizer unavailable");
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "fabric-test";
        }
    }
}
