package com.observernotes.sfx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.observernotes.persistence.LoadResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SfxServiceTest {

    @Test
    void enableVolumeAndPlaybackAreIndependentAndPersisted() {
        List<String> played = new ArrayList<>();
        SfxAudio audio = new SfxAudio() {
            @Override public void play(SfxEffect effect, double volume) { played.add(effect + "@" + volume); }
            @Override public void close() { }
        };
        class MemoryStore implements SfxSettingsStore {
            SfxPreferences saved = SfxPreferences.defaults();
            @Override public LoadResult<SfxPreferences> load() {
                return new LoadResult<>(saved, LoadResult.Status.LOADED, List.of());
            }
            @Override public void save(SfxPreferences p) { saved = p; }
        }
        MemoryStore store = new MemoryStore();
        SfxService service = new SfxService(audio, store);
        service.play(SfxEffect.CLICK);
        service.setVolume(0.6);
        service.play(SfxEffect.PAGE);
        service.setEnabled(false);
        service.play(SfxEffect.MEDAL);
        assertEquals(List.of("CLICK@0.3", "PAGE@0.6"), played);
        assertEquals(new SfxPreferences(false, 0.6), store.saved);
    }
}
