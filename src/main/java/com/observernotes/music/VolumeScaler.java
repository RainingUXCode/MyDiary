package com.observernotes.music;

/** Volume por software em PCM de 16 bits little-endian com sinal (funciona em qualquer placa/mixer). */
public final class VolumeScaler {

    private VolumeScaler() { }

    /** Ganho perceptual: o quadrado do controle (0,5 no controle = 25% da amplitude). */
    public static double gain(double volume) {
        double v = Math.max(0.0, Math.min(1.0, volume));
        return v * v;
    }

    public static void apply(byte[] pcm, int length, double volume) {
        double g = gain(volume);
        if (g >= 0.9999) {
            return;
        }
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int scaled = (int) Math.round(sample * g);
            pcm[i] = (byte) scaled;
            pcm[i + 1] = (byte) (scaled >> 8);
        }
    }
}
