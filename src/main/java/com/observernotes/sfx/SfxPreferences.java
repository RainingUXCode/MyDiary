package com.observernotes.sfx;

/** Preferências independentes do volume e do estado do player musical. */
public record SfxPreferences(boolean enabled, double volume) {

    public static final double DEFAULT_VOLUME = 0.30;

    public static SfxPreferences defaults() {
        return new SfxPreferences(true, DEFAULT_VOLUME);
    }

    public SfxPreferences {
        if (!Double.isFinite(volume) || volume < 0 || volume > 1) {
            throw new IllegalArgumentException("volume de SFX deve estar entre 0 e 1");
        }
    }

    public SfxPreferences withEnabled(boolean value) {
        return new SfxPreferences(value, volume);
    }

    public SfxPreferences withVolume(double value) {
        return new SfxPreferences(enabled, Math.max(0, Math.min(1, value)));
    }
}
