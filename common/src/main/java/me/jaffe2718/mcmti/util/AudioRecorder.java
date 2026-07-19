package me.jaffe2718.mcmti.util;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import me.jaffe2718.mcmti.config.McmtiConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

/**
 * Captures microphone audio and always returns little-endian 16-bit mono PCM at {@link #SAMPLE_RATE} Hz
 * for online speech recognition.
 * <p>
 * The capture line is <strong>not</strong> opened at game startup. The first {@link #record()} /
 * {@link #recordCycle()} / {@link #ensureOpen()} call opens a device-supported format (falling back
 * and resampling when pure 16 kHz is unavailable) and keeps it open until {@link #destroy()}.
 */
public final class AudioRecorder {
    /** Output sample rate expected by ASR backends. */
    public static final int SAMPLE_RATE = 16000;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNELS = 1;

    private static final AudioFormat PREFERRED_FORMAT =
            new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true, false);

    private static final float[] CANDIDATE_RATES = {
            16000f, 44100f, 48000f, 32000f, 22050f, 24000f, 11025f, 8000f
    };

    private static volatile @Nullable AudioRecorder INSTANCE;
    /** True after a failed open attempt until {@link #destroy()} / successful open. */
    private static volatile boolean openFailed;
    private static volatile boolean openInProgress;

    private final TargetDataLine line;
    private final AudioFormat captureFormat;

    public static void destroy() {
        AudioRecorder current = INSTANCE;
        INSTANCE = null;
        openFailed = false;
        openInProgress = false;
        if (current != null) {
            try {
                current.line.stop();
                current.line.flush();
                current.line.close();
            } catch (Exception e) {
                MicrophoneTextInput.LOGGER.debug("Error closing audio line", e);
            }
        }
    }

    /**
     * Reset state without opening the microphone.
     * Device open is deferred to the first recording request ({@link #ensureOpen()}).
     */
    public static void init() {
        destroy();
        MicrophoneTextInput.LOGGER.info(
                "Audio recorder idle (microphone opens on first use, output {} Hz {}-bit mono)",
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS
        );
    }

    public static @Nullable AudioRecorder instance() {
        return INSTANCE;
    }

    /** Whether the capture line is currently open and cached. */
    public static boolean isOpen() {
        return INSTANCE != null;
    }

    /**
     * Whether an open was attempted and failed. Cleared on {@link #destroy()} or successful open.
     * Used for UI so we do not claim failure before the user ever tries to record.
     */
    public static boolean hasOpenFailed() {
        return openFailed && INSTANCE == null;
    }

    /**
     * Open the microphone on first use and keep it open until {@link #destroy()}.
     * Thread-safe; concurrent callers share one open attempt.
     *
     * @return true if a recorder is available
     */
    public static boolean ensureOpen() {
        if (INSTANCE != null) {
            return true;
        }
        synchronized (AudioRecorder.class) {
            if (INSTANCE != null) {
                return true;
            }
            if (openInProgress) {
                return false;
            }
            openInProgress = true;
            try {
                AudioRecorder opened = openBestRecorder();
                if (opened != null) {
                    INSTANCE = opened;
                    openFailed = false;
                    MicrophoneTextInput.LOGGER.info(
                            "Audio recorder ready: capture={} -> output {} Hz {}-bit mono",
                            opened.captureFormat,
                            SAMPLE_RATE,
                            SAMPLE_SIZE_BITS
                    );
                    return true;
                }
                openFailed = true;
                MicrophoneTextInput.LOGGER.error("No usable microphone TargetDataLine found");
                return false;
            } catch (Throwable t) {
                openFailed = true;
                INSTANCE = null;
                MicrophoneTextInput.LOGGER.error("Failed to open audio recorder", t);
                return false;
            } finally {
                openInProgress = false;
            }
        }
    }

    private AudioRecorder(@NotNull TargetDataLine line, @NotNull AudioFormat captureFormat) {
        this.line = line;
        this.captureFormat = captureFormat;
    }

    /**
     * Record a fixed-length cycle for {@link McmtiConfig.Mode#AUTO_SEND}.
     * Opens the microphone on first call if needed.
     *
     * @return little-endian 16-bit mono PCM @ {@link #SAMPLE_RATE}, or empty if nothing was read
     */
    public static byte @NotNull [] recordCycle() {
        if (!ensureOpen()) {
            return new byte[0];
        }
        AudioRecorder rec = INSTANCE;
        if (rec == null) {
            return new byte[0];
        }
        int captureBytes = bytesForDurationMs(rec.captureFormat, McmtiConfig.recordCycleMs);
        byte[] buf = new byte[captureBytes];
        rec.line.start();
        int read = rec.line.read(buf, 0, buf.length);
        rec.line.stop();
        rec.line.flush();
        if (read <= 0) {
            return new byte[0];
        }
        byte[] exact = read == buf.length ? buf : copyOf(buf, read);
        return rec.toOutputPcm(exact);
    }

    /**
     * Record while the recognize key is held.
     * Opens the microphone on first call if needed.
     *
     * @return little-endian 16-bit mono PCM @ {@link #SAMPLE_RATE}
     */
    public static byte @NotNull [] record() {
        assert McmtiConfig.mode != McmtiConfig.Mode.AUTO_SEND;
        if (!ensureOpen()) {
            return new byte[0];
        }
        AudioRecorder rec = INSTANCE;
        if (rec == null) {
            return new byte[0];
        }
        ByteArrayOutputStream dynamicBuffer = new ByteArrayOutputStream();
        int chunkSize = Math.max(McmtiConfig.recordBufferSize, rec.line.getBufferSize() / 8);
        chunkSize = Math.max(chunkSize, rec.captureFormat.getFrameSize() * 64);
        // Align to frame size
        int frameSize = Math.max(1, rec.captureFormat.getFrameSize());
        chunkSize = (chunkSize / frameSize) * frameSize;
        if (chunkSize <= 0) {
            chunkSize = frameSize * 64;
        }
        byte[] chunk = new byte[chunkSize];
        rec.line.start();
        while (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
            int read = rec.line.read(chunk, 0, chunk.length);
            if (read > 0) {
                dynamicBuffer.write(chunk, 0, read);
            }
        }
        rec.line.stop();
        rec.line.flush();
        byte[] captured = dynamicBuffer.toByteArray();
        if (captured.length == 0) {
            return new byte[0];
        }
        return rec.toOutputPcm(captured);
    }

    private byte @NotNull [] toOutputPcm(byte @NotNull [] captureBytes) {
        if (isPreferredFormat(captureFormat)) {
            return captureBytes;
        }
        return convertToPreferredPcm(captureBytes, captureFormat);
    }

    private static @Nullable AudioRecorder openBestRecorder() {
        // 1) Preferred exact format on default mixer
        TargetDataLine line = tryOpen(PREFERRED_FORMAT);
        if (line != null) {
            return new AudioRecorder(line, line.getFormat());
        }

        // 2) Preferred format on any mixer
        line = tryOpenOnAnyMixer(PREFERRED_FORMAT);
        if (line != null) {
            return new AudioRecorder(line, line.getFormat());
        }

        // 3) Common rates / channel layouts, convert later
        for (float rate : CANDIDATE_RATES) {
            for (int channels : new int[]{1, 2}) {
                for (boolean bigEndian : new boolean[]{false, true}) {
                    AudioFormat format = new AudioFormat(rate, SAMPLE_SIZE_BITS, channels, true, bigEndian);
                    line = tryOpen(format);
                    if (line == null) {
                        line = tryOpenOnAnyMixer(format);
                    }
                    if (line != null) {
                        return new AudioRecorder(line, line.getFormat());
                    }
                }
            }
        }

        // 4) Last resort: any TargetDataLine with any supported format
        line = tryOpenAnySupportedLine();
        if (line != null) {
            return new AudioRecorder(line, line.getFormat());
        }
        return null;
    }

    private static @Nullable TargetDataLine tryOpen(@NotNull AudioFormat format) {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                return null;
            }
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            return line;
        } catch (IllegalArgumentException | LineUnavailableException | SecurityException e) {
            MicrophoneTextInput.LOGGER.debug("Cannot open default TargetDataLine for {}: {}", format, e.toString());
            return null;
        } catch (Exception e) {
            MicrophoneTextInput.LOGGER.debug("Unexpected error opening TargetDataLine for {}", format, e);
            return null;
        }
    }

    private static @Nullable TargetDataLine tryOpenOnAnyMixer(@NotNull AudioFormat format) {
        Mixer.Info[] mixers;
        try {
            mixers = AudioSystem.getMixerInfo();
        } catch (Exception e) {
            return null;
        }
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        for (Mixer.Info mixerInfo : mixers) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (!mixer.isLineSupported(info)) {
                    continue;
                }
                TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                line.open(format);
                MicrophoneTextInput.LOGGER.info("Opened microphone on mixer '{}' with {}", mixerInfo.getName(), format);
                return line;
            } catch (Exception e) {
                MicrophoneTextInput.LOGGER.debug(
                        "Mixer '{}' cannot open {}: {}",
                        mixerInfo.getName(),
                        format,
                        e.toString()
                );
            }
        }
        return null;
    }

    private static @Nullable TargetDataLine tryOpenAnySupportedLine() {
        Mixer.Info[] mixers;
        try {
            mixers = AudioSystem.getMixerInfo();
        } catch (Exception e) {
            return null;
        }
        for (Mixer.Info mixerInfo : mixers) {
            Mixer mixer;
            try {
                mixer = AudioSystem.getMixer(mixerInfo);
            } catch (Exception e) {
                continue;
            }
            for (Line.Info lineInfo : mixer.getTargetLineInfo()) {
                if (!TargetDataLine.class.isAssignableFrom(lineInfo.getLineClass())) {
                    continue;
                }
                try {
                    TargetDataLine line = (TargetDataLine) mixer.getLine(lineInfo);
                    if (lineInfo instanceof DataLine.Info dataInfo) {
                        AudioFormat[] formats = dataInfo.getFormats();
                        if (formats != null) {
                            for (AudioFormat fmt : formats) {
                                if (fmt.getSampleRate() == AudioSystem.NOT_SPECIFIED
                                        || fmt.getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED
                                        || fmt.getChannels() == AudioSystem.NOT_SPECIFIED) {
                                    // Fill in concrete values for open()
                                    float rate = fmt.getSampleRate() == AudioSystem.NOT_SPECIFIED ? 44100f : fmt.getSampleRate();
                                    int bits = fmt.getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED ? 16 : fmt.getSampleSizeInBits();
                                    int ch = fmt.getChannels() == AudioSystem.NOT_SPECIFIED ? 1 : fmt.getChannels();
                                    if (bits != 8 && bits != 16) {
                                        bits = 16;
                                    }
                                    AudioFormat concrete = new AudioFormat(
                                            fmt.getEncoding(),
                                            rate,
                                            bits,
                                            ch,
                                            (bits / 8) * ch,
                                            rate,
                                            fmt.isBigEndian()
                                    );
                                    try {
                                        line.open(concrete);
                                        MicrophoneTextInput.LOGGER.info(
                                                "Opened fallback microphone on mixer '{}' with {}",
                                                mixerInfo.getName(),
                                                concrete
                                        );
                                        return line;
                                    } catch (Exception ignored) {
                                        // try next format
                                    }
                                } else {
                                    try {
                                        line.open(fmt);
                                        MicrophoneTextInput.LOGGER.info(
                                                "Opened fallback microphone on mixer '{}' with {}",
                                                mixerInfo.getName(),
                                                fmt
                                        );
                                        return line;
                                    } catch (Exception ignored) {
                                        // try next format
                                    }
                                }
                            }
                        }
                    }
                    // Try open with preferred-ish defaults
                    try {
                        AudioFormat fallback = new AudioFormat(44100f, 16, 1, true, false);
                        line.open(fallback);
                        MicrophoneTextInput.LOGGER.info(
                                "Opened fallback microphone on mixer '{}' with {}",
                                mixerInfo.getName(),
                                fallback
                        );
                        return line;
                    } catch (Exception ignored) {
                        try {
                            line.close();
                        } catch (Exception closeIgnored) {
                            // ignore
                        }
                    }
                } catch (Exception e) {
                    MicrophoneTextInput.LOGGER.debug(
                            "Cannot use target line on mixer '{}': {}",
                            mixerInfo.getName(),
                            e.toString()
                    );
                }
            }
        }
        return null;
    }

    private static boolean isPreferredFormat(@NotNull AudioFormat format) {
        return format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                && Math.abs(format.getSampleRate() - SAMPLE_RATE) < 0.5f
                && format.getSampleSizeInBits() == SAMPLE_SIZE_BITS
                && format.getChannels() == CHANNELS
                && !format.isBigEndian();
    }

    private static int bytesForDurationMs(@NotNull AudioFormat format, int durationMs) {
        float frameRate = format.getFrameRate() > 0 ? format.getFrameRate() : format.getSampleRate();
        int frameSize = Math.max(1, format.getFrameSize());
        int frames = Math.max(1, Math.round(frameRate * durationMs / 1000f));
        return frames * frameSize;
    }

    /**
     * Convert arbitrary PCM capture bytes to little-endian 16-bit mono @ {@link #SAMPLE_RATE}.
     */
    static byte @NotNull [] convertToPreferredPcm(byte @NotNull [] input, @NotNull AudioFormat format) {
        if (input.length == 0) {
            return input;
        }
        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                && format.getEncoding() != AudioFormat.Encoding.PCM_UNSIGNED) {
            MicrophoneTextInput.LOGGER.warn("Unsupported capture encoding {}, returning empty audio", format.getEncoding());
            return new byte[0];
        }

        int srcChannels = Math.max(1, format.getChannels());
        int srcBits = format.getSampleSizeInBits();
        if (srcBits <= 0) {
            srcBits = 16;
        }
        int srcBytesPerSample = Math.max(1, (srcBits + 7) / 8);
        int srcFrameSize = format.getFrameSize() > 0 ? format.getFrameSize() : srcBytesPerSample * srcChannels;
        int frames = input.length / srcFrameSize;
        if (frames <= 0) {
            return new byte[0];
        }

        // Downmix + normalize to float mono [-1, 1]
        float[] mono = new float[frames];
        boolean bigEndian = format.isBigEndian();
        boolean signed = format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED;
        for (int i = 0; i < frames; i++) {
            int frameOffset = i * srcFrameSize;
            float sum = 0f;
            for (int ch = 0; ch < srcChannels; ch++) {
                int sampleOffset = frameOffset + ch * srcBytesPerSample;
                if (sampleOffset + srcBytesPerSample > input.length) {
                    break;
                }
                int sample = readSample(input, sampleOffset, srcBytesPerSample, bigEndian, signed);
                sum += sampleToFloat(sample, srcBytesPerSample, signed);
            }
            mono[i] = sum / srcChannels;
        }

        float srcRate = format.getSampleRate() > 0 ? format.getSampleRate() : SAMPLE_RATE;
        float[] resampled = resampleLinear(mono, srcRate, SAMPLE_RATE);

        byte[] out = new byte[resampled.length * 2];
        for (int i = 0; i < resampled.length; i++) {
            float v = Math.max(-1f, Math.min(1f, resampled[i]));
            int s = Math.round(v * 32767f);
            if (s > 32767) s = 32767;
            if (s < -32768) s = -32768;
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }

    private static int readSample(byte[] data, int offset, int bytesPerSample, boolean bigEndian, boolean signed) {
        int value = 0;
        if (bigEndian) {
            for (int i = 0; i < bytesPerSample; i++) {
                value = (value << 8) | (data[offset + i] & 0xFF);
            }
        } else {
            for (int i = bytesPerSample - 1; i >= 0; i--) {
                value = (value << 8) | (data[offset + i] & 0xFF);
            }
        }
        if (signed) {
            int bits = bytesPerSample * 8;
            int signBit = 1 << (bits - 1);
            if ((value & signBit) != 0) {
                value -= 1 << bits;
            }
        }
        return value;
    }

    private static float sampleToFloat(int sample, int bytesPerSample, boolean signed) {
        if (!signed) {
            int max = (1 << (bytesPerSample * 8)) - 1;
            return (sample / (float) max) * 2f - 1f;
        }
        int max = (1 << (bytesPerSample * 8 - 1)) - 1;
        return sample / (float) max;
    }

    private static float @NotNull [] resampleLinear(float @NotNull [] input, float srcRate, float dstRate) {
        if (input.length == 0) {
            return input;
        }
        if (Math.abs(srcRate - dstRate) < 0.5f) {
            return input;
        }
        int outLen = Math.max(1, Math.round(input.length * (dstRate / srcRate)));
        float[] out = new float[outLen];
        float ratio = srcRate / dstRate;
        for (int i = 0; i < outLen; i++) {
            float srcIndex = i * ratio;
            int i0 = (int) srcIndex;
            int i1 = Math.min(i0 + 1, input.length - 1);
            float t = srcIndex - i0;
            if (i0 >= input.length) {
                out[i] = input[input.length - 1];
            } else {
                out[i] = input[i0] * (1f - t) + input[i1] * t;
            }
        }
        return out;
    }

    private static byte @NotNull [] copyOf(byte @NotNull [] src, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, 0, out, 0, len);
        return out;
    }
}
