package com.observernotes.persistence;

import com.observernotes.settings.ObserverPreferences;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/** Guarda as preferências em {@code settings.json}, separado do estado. */
public final class JsonSettingsRepository implements SettingsRepository {

    public static final int FORMAT_VERSION = 1;
    public static final String FILE_NAME = "settings.json";

    private final JsonFileStore store;

    public JsonSettingsRepository(Path dataDirectory) {
        this(dataDirectory, new AtomicFileWriter(), Clock.systemUTC());
    }

    public JsonSettingsRepository(Path dataDirectory, AtomicWriter writer, Clock clock) {
        this.store = new JsonFileStore(dataDirectory.resolve(FILE_NAME), FORMAT_VERSION, List.of(), writer, clock);
    }

    @Override
    public LoadResult<ObserverPreferences> load() {
        JsonFileStore.Read<ObserverPreferences> read = store.read(SettingsJsonMapper::fromJson);
        ObserverPreferences value = read.value() != null ? read.value() : ObserverPreferences.allEnabled();
        return new LoadResult<>(value, read.status(), read.messages());
    }

    @Override
    public void save(ObserverPreferences preferences) {
        store.write(SettingsJsonMapper.toJson(preferences));
    }

    public Path file() {
        return store.file();
    }
}
