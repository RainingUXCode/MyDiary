package com.observernotes.sfx;

import java.util.List;

/** API usada pela interface; não expõe decodificação nem detalhes do player musical. */
public interface SoundEffects extends AutoCloseable {

    void play(SfxEffect effect);

    SfxPreferences preferences();

    void setEnabled(boolean enabled);

    void setVolume(double volume);

    default List<String> startupMessages() {
        return List.of();
    }

    @Override
    void close();

    SoundEffects NONE = new SoundEffects() {
        private final SfxPreferences preferences = SfxPreferences.defaults();
        @Override public void play(SfxEffect effect) { }
        @Override public SfxPreferences preferences() { return preferences; }
        @Override public void setEnabled(boolean enabled) { }
        @Override public void setVolume(double volume) { }
        @Override public void close() { }
    };
}
