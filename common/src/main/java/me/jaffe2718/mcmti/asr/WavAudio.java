package me.jaffe2718.mcmti.asr;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Helpers for packaging PCM as WAV for online ASR APIs.
 */
public final class WavAudio {
    public static final String MIME_WAV = "audio/wav";
    /** MiMo limit: Base64-encoded payload must be ≤ 10 MB. */
    public static final int MAX_BASE64_BYTES = 10 * 1024 * 1024;

    private WavAudio() {}

    /**
     * Wrap little-endian 16-bit mono PCM in a standard RIFF/WAVE container.
     */
    public static byte @NotNull [] pcmToWav(byte @NotNull [] pcm, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcm.length;
        int chunkSize = 36 + dataSize;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt(chunkSize);
        header.put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '});
        header.putInt(16);              // PCM fmt chunk size
        header.putShort((short) 1);     // audio format = PCM
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) bitsPerSample);
        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt(dataSize);

        byte[] wav = new byte[44 + dataSize];
        System.arraycopy(header.array(), 0, wav, 0, 44);
        System.arraycopy(pcm, 0, wav, 44, dataSize);
        return wav;
    }

    /**
     * Encode bytes as a Data URL: {@code data:{mime};base64,...}
     */
    public static @NotNull String toDataUrl(byte @NotNull [] data, @NotNull String mimeType) {
        String base64 = Base64.getEncoder().encodeToString(data);
        return "data:" + mimeType + ";base64," + base64;
    }

    /**
     * Rough upper bound of Base64 length for {@code rawLength} input bytes.
     */
    public static int estimateBase64Length(int rawLength) {
        return ((rawLength + 2) / 3) * 4;
    }
}
