package me.jaffe2718.mcmti.test.fabric;

import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public final class TestRecognizerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SpeechRecognizer.register(0, ResourceLocation.fromNamespaceAndPath("mcmti_test", "fabric"), TestRecognizer::new);
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
            return Component.literal("Fabric test recognizer ready");
        }

        @Override
        protected @NotNull Component unavailableToast() {
            return Component.literal("Fabric test recognizer unavailable");
        }

        @Override
        public @NotNull String transcribe(float[] audio) {
            return "fabric-test";
        }
    }
}
