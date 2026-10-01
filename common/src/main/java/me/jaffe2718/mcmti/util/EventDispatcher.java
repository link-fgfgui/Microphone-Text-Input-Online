package me.jaffe2718.mcmti.util;

import me.jaffe2718.mcmti.event.EventType;

/**
 * Loader-agnostic bridge for firing MCMti events. Each loader module provides a
 * {@link java.util.ServiceLoader} implementation that posts the event on its own
 * event system.
 */
public interface EventDispatcher {

    void triggerEvent(EventType event, Object... args) throws IllegalArgumentException;
}
