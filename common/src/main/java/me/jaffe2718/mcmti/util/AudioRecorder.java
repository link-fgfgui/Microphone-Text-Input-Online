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
 * Lifecycle:
 * <ul>
 *   <li>Game start: idle (no device open, no permission prompt)</li>
 *   <li>First record: open device, start line, keep it warm</li>
 *   <li>Idle while open: background drain discards ring-buffer samples (no {@code flush()} on each press)</li>
 *   <li>Recording session: {@link #isRecordingSession()} is true only while samples are collected</li>
 *   <li>Game stop: close device</li>
 * </ul>
 * Action-bar "Recording" must key off {@link #isRecordingSession()}, not key-down or line open.
 */
public final class AudioRecorder {
    /** Output sample rate expected by ASR backends. */
    public static final int SAMPLE_RATE = 16000;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNELS = 1;

    private static final int READ_CHUNK_MS = 20;
    /** After first open+start, discard this much audio so the first session is past device warmup. */
    private static final int INITIAL_WARMUP_MS = 300;

    private static final AudioFormat PREFERRED_FORMAT =
            new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true, false);

    private static final float[] CANDIDATE_RATES = {
            16000f, 44100f, 48000f, 32000f, 22050f, 24000f, 11025f, 8000f
    };

    private static volatile @Nullable AudioRecorder INSTANCE;
    private static volatile boolean openFailed;
    private static volatile boolean openInProgress;
    /** True only while a record()/recordCycle() call is collecting samples for ASR. */
    private static volatile boolean sessionActive;

    private final TargetDataLine line;
    private final AudioFormat captureFormat;
    private final Object captureLock = new Object();
    private final Thread drainThread;
    private volatile boolean closed;
    private volatile boolean drainPaused;

    public static void destroy() {
        AudioRecorder current = INSTANCE;
        INSTANCE = null;
        openFailed = false;
        openInProgress = false;
        sessionActive = false;
        if (current != null) {
            current.closed = true;
            current.drainPaused = true;
            current.drainThread.interrupt();
            synchronized (current.captureLock) {
                try {
                    current.line.stop();
                    current.line.flush();
                    current.line.close();
                } catch (Exception e) {
                    MicrophoneTextInput.LOGGER.debug("Error closing audio line", e);
                }
            }
            try {
                current.drainThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Reset state without opening the microphone.
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

    public static boolean isOpen() {
        return INSTANCE != null;
    }

    /**
     * True only while this mod is collecting PCM for recognition.
     * Use this for the action-bar "Recording" message — not key-down alone.
     */
    public static boolean isRecordingSession() {
        return sessionActive && INSTANCE != null;
    }

    public static boolean hasOpenFailed() {
        return openFailed && INSTANCE == null;
    }

    /**
     * Open mic on first use, warm it up, keep it running until {@link #destroy()}.
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
                    opened.line.start();
                    // Discard device warmup so the first real session does not start on silence/garbage.
                    opened.discardMs(INITIAL_WARMUP_MS);
                    opened.startDrainThread();
                    INSTANCE = opened;
                    openFailed = false;
                    MicrophoneTextInput.LOGGER.info(
                            "Audio recorder ready (warm): capture={} -> output {} Hz {}-bit mono",
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
        this.drainThread = new Thread(this::drainLoop, "thread.mcmti.audio.drain");
        this.drainThread.setDaemon(true);
    }

    private void startDrainThread() {
        drainThread.start();
    }

    /**
     * Continuously discard samples while idle so the ring buffer never holds seconds of old audio.
     * Paused during an active recording session so {@link #record()} owns the reads.
     */
    private void drainLoop() {
        int frameSize = Math.max(1, captureFormat.getFrameSize());
        byte[] junk = new byte[Math.max(frameSize * 64, bytesForDurationMs(captureFormat, READ_CHUNK_MS))];
        while (!closed) {
            try {
                if (drainPaused || sessionActive) {
                    LockSupportPark(5_000_000L);
                    continue;
                }
                if (!line.isOpen()) {
                    LockSupportPark(20_000_000L);
                    continue;
                }
                if (!line.isActive()) {
                    try {
                        line.start();
                    } catch (Exception e) {
                        MicrophoneTextInput.LOGGER.debug("Failed to restart capture line", e);
                        LockSupportPark(50_000_000L);
                        continue;
                    }
                }
                int available = line.available();
                if (available >= frameSize) {
                    int toRead = Math.min(junk.length, (available / frameSize) * frameSize);
                    // Non-session reads: discard
                    line.read(junk, 0, toRead);
                } else {
                    LockSupportPark(5_000_000L);
                }
            } catch (Exception e) {
                if (!closed) {
                    MicrophoneTextInput.LOGGER.debug("Audio drain loop error", e);
                    LockSupportPark(50_000_000L);
                }
            }
        }
    }

    private static void LockSupportPark(long nanos) {
        java.util.concurrent.locks.LockSupport.parkNanos(nanos);
    }

    /**
     * Record a fixed-length cycle for {@link McmtiConfig.Mode#AUTO_SEND}.
     */
    public static byte @NotNull [] recordCycle() {
        if (!ensureOpen()) {
            return new byte[0];
        }
        AudioRecorder rec = INSTANCE;
        if (rec == null) {
            return new byte[0];
        }
        synchronized (rec.captureLock) {
            rec.beginSession();
            try {
                int captureBytes = bytesForDurationMs(rec.captureFormat, McmtiConfig.recordCycleMs);
                byte[] buf = new byte[captureBytes];
                int read = rec.readFully(buf, 0, buf.length);
                if (read <= 0) {
                    return new byte[0];
                }
                byte[] exact = read == buf.length ? buf : copyOf(buf, read);
                return rec.toOutputPcm(exact);
            } finally {
                rec.endSession();
            }
        }
    }

    /**
     * Record while the recognize key is held.
     * {@link #isRecordingSession()} is true for the entire sample-collection window.
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
        synchronized (rec.captureLock) {
            rec.beginSession();
            try {
                ByteArrayOutputStream dynamicBuffer = new ByteArrayOutputStream();
                int frameSize = Math.max(1, rec.captureFormat.getFrameSize());
                int chunkSize = bytesForDurationMs(rec.captureFormat, READ_CHUNK_MS);
                chunkSize = Math.max(frameSize, (chunkSize / frameSize) * frameSize);
                byte[] chunk = new byte[chunkSize];

                while (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
                    int available = rec.line.available();
                    int toRead;
                    if (available >= frameSize) {
                        toRead = Math.min(chunk.length, (available / frameSize) * frameSize);
                    } else {
                        // Block for up to one short chunk of new audio
                        toRead = chunk.length;
                    }
                    int read = rec.line.read(chunk, 0, toRead);
                    if (read > 0) {
                        dynamicBuffer.write(chunk, 0, read);
                    }
                }

                // Drain residual samples that arrived as the key was released.
                int leftover = rec.line.available();
                while (leftover >= frameSize) {
                    int toRead = Math.min(chunk.length, (leftover / frameSize) * frameSize);
                    int read = rec.line.read(chunk, 0, toRead);
                    if (read <= 0) {
                        break;
                    }
                    dynamicBuffer.write(chunk, 0, read);
                    leftover = rec.line.available();
                }

                byte[] captured = dynamicBuffer.toByteArray();
                if (captured.length == 0) {
                    return new byte[0];
                }
                return rec.toOutputPcm(captured);
            } finally {
                rec.endSession();
            }
        }
    }

    /**
     * Mark UI session active and take ownership of the TargetDataLine from the drain thread.
     * Does <strong>not</strong> flush — that would drop the first speech samples after key-down.
     */
    private void beginSession() {
        drainPaused = true;
        // Wait briefly for the drain thread to finish any in-flight read.
        // Drain uses short reads; a few ms is enough.
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!line.isOpen()) {
            throw new IllegalStateException("TargetDataLine is not open");
        }
        if (!line.isActive()) {
            line.start();
        }
        // Publish session only after we own the line — action bar must not say Recording earlier.
        sessionActive = true;
    }

    private void endSession() {
        sessionActive = false;
        drainPaused = false;
    }

    /** Blocking discard of approximately {@code ms} of capture audio (warmup). */
    private void discardMs(int ms) {
        int total = bytesForDurationMs(captureFormat, ms);
        byte[] buf = new byte[Math.min(total, bytesForDurationMs(captureFormat, 50))];
        int got = 0;
        while (got < total) {
            int n = line.read(buf, 0, Math.min(buf.length, total - got));
            if (n <= 0) {
                break;
            }
            got += n;
        }
    }

    private int readFully(byte[] buf, int off, int len) {
        int total = 0;
        while (total < len) {
            int read = line.read(buf, off + total, len - total);
            if (read <= 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private byte @NotNull [] toOutputPcm(byte @NotNull [] captureBytes) {
        if (isPreferredFormat(captureFormat)) {
            return captureBytes;
        }
        return convertToPreferredPcm(captureBytes, captureFormat);
    }

    private static @Nullable AudioRecorder openBestRecorder() {
        TargetDataLine line = tryOpen(PREFERRED_FORMAT);
        if (line != null) {
            return new AudioRecorder(line, line.getFormat());
        }

        line = tryOpenOnAnyMixer(PREFERRED_FORMAT);
        if (line != null) {
            return new AudioRecorder(line, line.getFormat());
        }

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
                                    }
                                }
                            }
                        }
                    }
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
