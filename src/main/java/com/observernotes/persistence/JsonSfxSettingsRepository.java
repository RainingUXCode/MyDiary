package com.observernotes.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.observernotes.sfx.SfxPreferences;
import com.observernotes.sfx.SfxSettingsStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/** Preferências independentes de efeitos, guardadas em {@code sfx-settings.json}. */
public final class JsonSfxSettingsRepository implements SfxSettingsStore {

    public static final String FILE_NAME = "sfx-settings.json";
    private final JsonFileStore store;

    public JsonSfxSettingsRepository(Path dataDirectory) {
        this(dataDirectory, new AtomicFileWriter(), Clock.systemUTC());
    }

    JsonSfxSettingsRepository(Path dataDirectory, AtomicWriter writer, Clock clock) {
        store = new JsonFileStore(dataDirectory.resolve(FILE_NAME), 1, List.of(), writer, clock);
    }

    @Override
    public LoadResult<SfxPreferences> load() {
        JsonFileStore.Read<SfxPreferences> read = store.read(JsonSfxSettingsRepository::decode);
        return new LoadResult<>(read.value() == null ? SfxPreferences.defaults() : read.value(),
                read.status(), read.messages());
    }

    @Override
    public void save(SfxPreferences p) {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", p.enabled());
        root.addProperty("volume", p.volume());
        store.write(root);
    }

    private static SfxPreferences decode(JsonObject root) {
        JsonElement enabled = root.get("enabled");
        JsonElement volume = root.get("volume");
        if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
            throw new InvalidDataException("enabled deveria ser verdadeiro ou falso");
        }
        if (volume == null || !volume.isJsonPrimitive() || !volume.getAsJsonPrimitive().isNumber()) {
            throw new InvalidDataException("volume deveria ser um número entre 0 e 1");
        }
        try {
            return new SfxPreferences(enabled.getAsBoolean(), volume.getAsDouble());
        } catch (IllegalArgumentException e) {
            throw new InvalidDataException(e.getMessage(), e);
        }
    }

    Path file() {
        return store.file();
    }
}
