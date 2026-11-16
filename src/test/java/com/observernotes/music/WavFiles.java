package com.observernotes.music;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Gera WAVs de teste (tom senoidal) na hora: nenhum áudio de terceiros é distribuído. */
final class WavFiles {

    private WavFiles() { }

    static Path tone(Path dir, String name, double seconds, double hz, int bits) throws IOException {
        float rate = 8000f;
        int frames = (int) (rate * seconds);
        int bytesPer = bits / 8;
        byte[] data = new byte[frames * bytesPer];
        for (int i = 0; i < frames; i++) {
            double v = Math.sin(2 * Math.PI * hz * i / rate) * 0.5;
            if (bits == 16) {
                int s = (int) (v * 32767);
                data[2 * i] = (byte) s;
                data[2 * i + 1] = (byte) (s >> 8);
            } else {
                data[i] = (byte) (128 + (int) (v * 127));           // WAV de 8 bits é sem sinal
            }
        }
        AudioFormat fmt = bits == 16
                ? new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, 1, 2, rate, false)
                : new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, rate, 8, 1, 1, rate, false);
        Path out = dir.resolve(name);
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(data), fmt, frames)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, out.toFile());
        }
        return out;
    }

    static Path tone(Path dir, String name, double seconds) throws IOException {
        return tone(dir, name, seconds, 440, 16);
    }

    static Path garbage(Path dir, String name) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, "isto nao e um arquivo de audio".getBytes());
        return p;
    }
}
