package me.jaffe2718.mcmti.asr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider-agnostic speech recognition result.
 *
 * @param text     recognized transcript (never null; empty if no speech)
 * @param language detected or requested language, if available
 */
public record AsrResult(
        @NotNull String text,
        @Nullable String language
) {
    public static final AsrResult EMPTY = new AsrResult("", null);

    public AsrResult {
        if (text == null) {
            text = "";
        }
    }

    public boolean isEmpty() {
        return text.isBlank();
    }
}
