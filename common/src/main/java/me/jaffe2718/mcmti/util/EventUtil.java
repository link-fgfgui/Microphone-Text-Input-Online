package me.jaffe2718.mcmti.util;

import me.jaffe2718.mcmti.event.EventType;

import java.util.ServiceLoader;

public final class EventUtil {

    private static volatile EventDispatcher dispatcher;
    private static boolean resolved;

    private EventUtil() {
    }

    public static void triggerEvent(EventType event, Object... args) throws IllegalArgumentException {
        EventDispatcher target = dispatcher();
        if (target != null) {
            target.triggerEvent(event, args);
        }
    }

    private static EventDispatcher dispatcher() {
        if (!resolved) {
            synchronized (EventUtil.class) {
                if (!resolved) {
                    dispatcher = ServiceLoader.load(EventDispatcher.class).findFirst().orElse(null);
                    resolved = true;
                }
            }
        }
        return dispatcher;
    }
}
