package me.jaffe2718.mcmti.test.neoforge;

import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

@Mod(value = "mcmti_test", dist = Dist.CLIENT)
public final class TestRecognizerMod {

    public TestRecognizerMod() {
        SpeechRecognizer.register(0, ResourceLocation.fromNamespaceAndPath("mcmti_test", "neoforge"), TestRecognizer::new);
    }

    private static final class TestRecognizer extends SpeechRecognizer {
        private TestRecognizer(@NotNull ResourceLocation id) {
            super(id);
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        protected @NotNull Component availableToast() {
            return Component.literal("NeoForge test recognizer ready");
        }

        @Override
        protected @NotNull Component unavailableToast() {
            return Component.literal("NeoForge test recognizer unavailable");
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "neoforge-test";
        }
    }
}
