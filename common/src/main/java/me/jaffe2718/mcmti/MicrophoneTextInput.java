package me.jaffe2718.mcmti;


import me.jaffe2718.mcmti.config.McmtiConfig;
import me.jaffe2718.mcmti.util.AudioRecorder;
import me.jaffe2718.mcmti.util.EventUtil;
import me.jaffe2718.mcmti.util.SpeechRecognizer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MicrophoneTextInput {
    public static final String MOD_ID = "mcmti";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final KeyBinding RECOGNIZE_KEY = new KeyBinding("key.mcmti.recognize", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.category.minecraft.mcmti");


    public static void init() {
        McmtiConfig.init(MOD_ID, McmtiConfig.class);
        try {
            AudioRecorder.init();
        } catch (Throwable t) {
            LOGGER.error("Audio recorder init failed; speech input will be unavailable", t);
        }
        try {
            SpeechRecognizer.init();
        } catch (Throwable t) {
            LOGGER.error("Speech recognizer init failed", t);
        }
        EventUtil.register();
    }
}
