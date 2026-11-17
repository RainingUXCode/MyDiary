package com.observernotes.sfx;

import com.observernotes.persistence.LoadResult;
import com.observernotes.persistence.PersistenceException;
import java.util.List;
import java.util.Objects;

/** Mantém preferências, salva cada alteração e aplica o bloqueio global dos efeitos. */
public final class SfxService implements SoundEffects {

    private final SfxAudio audio;
    private final SfxSettingsStore store;
    private final List<String> startupMessages;
    private volatile SfxPreferences preferences;

    public SfxService(SfxAudio audio, SfxSettingsStore store) {
        this.audio = Objects.requireNonNull(audio, "audio");
        this.store = Objects.requireNonNull(store, "store");
        LoadResult<SfxPreferences> loaded = store.load();
        preferences = loaded.value();
        startupMessages = loaded.messages();
    }

    @Override
    public void play(SfxEffect effect) {
        SfxPreferences p = preferences;
        if (p.enabled() && p.volume() > 0) {
            audio.play(Objects.requireNonNull(effect, "effect"), p.volume());
        }
    }

    @Override
    public SfxPreferences preferences() {
        return preferences;
    }

    @Override
    public void setEnabled(boolean enabled) {
        update(preferences.withEnabled(enabled));
    }

    @Override
    public void setVolume(double volume) {
        update(preferences.withVolume(volume));
    }

    private void update(SfxPreferences next) {
        if (next.equals(preferences)) {
            return;
        }
        store.save(next);
        preferences = next;
    }

    @Override
    public List<String> startupMessages() {
        return startupMessages;
    }

    @Override
    public void close() {
        audio.close();
    }
}
