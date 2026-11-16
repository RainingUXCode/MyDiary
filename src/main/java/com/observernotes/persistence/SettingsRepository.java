package com.observernotes.persistence;

import com.observernotes.settings.ObserverPreferences;

/** Porta de persistência das preferências. Fica separada do estado: uma falha não afeta a outra. */
public interface SettingsRepository {

    LoadResult<ObserverPreferences> load();

    /** @throws PersistenceException se não foi possível gravar */
    void save(ObserverPreferences preferences);
}
