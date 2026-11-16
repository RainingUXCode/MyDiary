package com.observernotes.persistence;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/** Guarda o estado em {@code state.json}, na pasta de dados do usuário. */
public final class JsonStateRepository implements StateRepository {

    public static final int FORMAT_VERSION = 1;
    public static final String FILE_NAME = "state.json";

    private final JsonFileStore store;
    private final Clock clock;

    public JsonStateRepository(Path dataDirectory) {
        this(dataDirectory, new AtomicFileWriter(), Clock.systemUTC(), List.of());
    }

    JsonStateRepository(Path dataDirectory, AtomicWriter writer, Clock clock, List<Migration> migrations) {
        this.clock = clock;
        this.store = new JsonFileStore(dataDirectory.resolve(FILE_NAME), FORMAT_VERSION, migrations, writer, clock);
    }

    /** Construtor para testes: permite injetar o gravador (por exemplo, um que falha). */
    public JsonStateRepository(Path dataDirectory, AtomicWriter writer, Clock clock) {
        this(dataDirectory, writer, clock, List.of());
    }

    @Override
    public LoadResult<AppSnapshot> load() {
        JsonFileStore.Read<AppSnapshot> read = store.read(StateJsonMapper::fromJson);
        AppSnapshot value = read.value() != null ? read.value() : AppSnapshot.empty();
        return new LoadResult<>(value, read.status(), read.messages());
    }

    @Override
    public void save(AppSnapshot snapshot) {
        store.write(StateJsonMapper.toJson(snapshot, clock.instant()));
    }

    public Path file() {
        return store.file();
    }

    public Path backupFile() {
        return store.backupFile();
    }
}
