package com.observernotes.sfx;

import com.observernotes.persistence.LoadResult;

/** Porta de persistência das preferências de efeitos. */
public interface SfxSettingsStore {

    LoadResult<SfxPreferences> load();

    void save(SfxPreferences preferences);
}
