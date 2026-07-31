package me.jaffe2718.mcmti.test.neoforge;

import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

@Mod(value = "mcmti_test", dist = Dist.CLIENT)
public final class TestRecognizerMod {

    public TestRecognizerMod() {
        SpeechRecognizer.register(0, Identifier.of("mcmti_test", "neoforge"), TestRecognizer::new);
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
            return Text.literal("NeoForge test recognizer ready");
        }

        @Override
        protected @NotNull Text unavailableToast() {
            return Text.literal("NeoForge test recognizer unavailable");
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "neoforge-test";
        }
    }
}
