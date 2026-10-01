package me.jaffe2718.mcmti;

import com.mojang.blaze3d.platform.InputConstants;
import me.jaffe2718.mcmti.asr.mimo.MimoSpeechRecognizer;
import me.jaffe2718.mcmti.asr.openai.OpenAiCompatibleSpeechRecognizer;
import me.jaffe2718.mcmti.config.McmtiConfig;
import me.jaffe2718.mcmti.util.AudioRecorder;
import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MicrophoneTextInput {
    public static final String MOD_ID = "mcmti";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final KeyMapping RECOGNIZE_KEY = new KeyMapping("key.mcmti.recognize", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.category.minecraft.mcmti");

    public static void init() {
        McmtiConfig.init(MOD_ID, McmtiConfig.class);
        try {
            AudioRecorder.init();
        } catch (Throwable t) {
            LOGGER.error("Audio recorder init failed; speech input will be unavailable", t);
        }
        SpeechRecognizer.register(100, new ResourceLocation(MOD_ID, "mimo"), MimoSpeechRecognizer::new);
        SpeechRecognizer.register(100, new ResourceLocation(MOD_ID, "openai_compatible"), OpenAiCompatibleSpeechRecognizer::new);
    }
}
