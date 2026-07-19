package me.jaffe2718.mcmti.asr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when an online speech recognition request fails.
 */
public class AsrException extends Exception {
    private final int statusCode;

    public AsrException(@NotNull String message) {
        this(message, null, -1);
    }

    public AsrException(@NotNull String message, @Nullable Throwable cause) {
        this(message, cause, -1);
    }

    public AsrException(@NotNull String message, int statusCode) {
        this(message, null, statusCode);
    }

    public AsrException(@NotNull String message, @Nullable Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
