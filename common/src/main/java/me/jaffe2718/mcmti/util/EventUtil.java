package me.jaffe2718.mcmti.util;

import dev.architectury.injectables.annotations.ExpectPlatform;
import me.jaffe2718.mcmti.event.EventType;

public abstract class EventUtil {

    @ExpectPlatform
    public static void triggerEvent(EventType event, Object... args) {
        throw new RuntimeException();
    }
}
