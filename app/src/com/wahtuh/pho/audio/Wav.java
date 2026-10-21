package com.wahtuh.pho.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Wav {
    private Wav() {}

    public static byte[] fromFloat(float[] pcm, int sampleRate) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length * 2);
        int dataBytes = pcm.length * 2;
        try {
            out.write("RIFF".getBytes("US-ASCII"));
            out.write(le32(36 + dataBytes));
            out.write("WAVE".getBytes("US-ASCII"));
            out.write("fmt ".getBytes("US-ASCII"));
            out.write(le32(16));
            out.write(le16(1));
            out.write(le16(1));
            out.write(le32(sampleRate));
            out.write(le32(sampleRate * 2));
            out.write(le16(2));
            out.write(le16(16));
            out.write("data".getBytes("US-ASCII"));
            out.write(le32(dataBytes));
            for (float f : pcm) {
                int v = Math.max(-32768, Math.min(32767, Math.round(f * 32767f)));
                out.write(v & 0xFF);
                out.write((v >> 8) & 0xFF);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /** Minimal 16-bit PCM WAV reader used by diagnostics and tests. */
    public static final class Decoded {
        public final float[] samples;
        public final int sampleRate;
        Decoded(float[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    public static Decoded read(InputStream in) throws IOException {
        byte[] all = readAll(in);
        if (all.length < 44 || all[0] != 'R' || all[1] != 'I' || all[2] != 'F' || all[3] != 'F') {
            throw new IOException("Not a RIFF WAV file");
        }
        ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        int sampleRate = 16000;
        int channels = 1;
        int bits = 16;
        int dataAt = -1;
        int dataLen = 0;
        while (pos + 8 <= all.length) {
            int id = b.getInt(pos);
            int size = b.getInt(pos + 4);
            pos += 8;
            if (size < 0) break;
            if (id == 0x20746d66) { // "fmt "
                if (pos + 16 > all.length) break;
                channels = b.getShort(pos + 2) & 0xFFFF;
                sampleRate = b.getInt(pos + 4);
                bits = b.getShort(pos + 14) & 0xFFFF;
            } else if (id == 0x61746164) { // "data"
                dataAt = pos;
                dataLen = Math.min(size, all.length - pos);
                break;
            }
            pos += size + (size & 1);
        }
        if (dataAt < 0 || bits != 16) throw new IOException("Only 16-bit PCM WAV is supported");
        int frameBytes = 2 * Math.max(1, channels);
        int frames = dataLen / frameBytes;
        float[] out = new float[frames];
        for (int i = 0; i < frames; i++) {
            short s = b.getShort(dataAt + i * frameBytes);
            out[i] = s / 32768f;
        }
        return new Decoded(out, sampleRate);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static byte[] le32(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    private static byte[] le16(int v) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) v).array();
    }
}
