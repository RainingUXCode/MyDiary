package com.observernotes.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.observernotes.notes.LittleNote;
import com.observernotes.notes.NotesRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Guarda o mural em {@code notes.json}, separado do estado das tarefas. */
public final class JsonNotesRepository implements NotesRepository {

    public static final int FORMAT_VERSION = 1;
    public static final String FILE_NAME = "notes.json";

    private final JsonFileStore store;

    public JsonNotesRepository(Path dataDirectory) {
        this(dataDirectory, new AtomicFileWriter(), Clock.systemUTC());
    }

    public JsonNotesRepository(Path dataDirectory, AtomicWriter writer, Clock clock) {
        this.store = new JsonFileStore(dataDirectory.resolve(FILE_NAME), FORMAT_VERSION, List.of(), writer, clock);
    }

    @Override
    public LoadResult<List<LittleNote>> load() {
        JsonFileStore.Read<List<LittleNote>> read = store.read(JsonNotesRepository::fromJson);
        List<LittleNote> value = read.value() == null ? List.of() : read.value();
        return new LoadResult<>(value, read.status(), read.messages());
    }

    @Override
    public void save(List<LittleNote> notes) {
        store.write(toJson(notes));
    }

    public Path file() {
        return store.file();
    }

    public Path backupFile() {
        return store.backupFile();
    }

    private static JsonObject toJson(List<LittleNote> notes) {
        if (notes.size() > LittleNotesServiceLimit.MAX) {
            throw new IllegalArgumentException("máximo de " + LittleNotesServiceLimit.MAX + " bilhetes");
        }
        JsonArray array = new JsonArray();
        for (LittleNote note : notes) {
            JsonObject item = new JsonObject();
            item.addProperty("id", note.id());
            item.addProperty("text", note.text());
            array.add(item);
        }
        JsonObject root = new JsonObject();
        root.add("notes", array);
        return root;
    }

    private static List<LittleNote> fromJson(JsonObject root) {
        JsonArray array = JsonFields.array(root, "notes");
        if (array.size() > LittleNotesServiceLimit.MAX) {
            throw new InvalidDataException("o mural contém mais de " + LittleNotesServiceLimit.MAX + " bilhetes");
        }
        List<LittleNote> notes = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                throw new InvalidDataException("notes[" + i + "] deveria ser um objeto");
            }
            JsonObject item = element.getAsJsonObject();
            String id = JsonFields.string(item, "id");
            String text = JsonFields.string(item, "text");
            try {
                LittleNote note = new LittleNote(id, text);
                if (!ids.add(note.id())) {
                    throw new InvalidDataException("identificador de bilhete duplicado: " + note.id());
                }
                notes.add(note);
            } catch (IllegalArgumentException e) {
                throw new InvalidDataException("bilhete inválido em notes[" + i + "]: " + e.getMessage(), e);
            }
        }
        return List.copyOf(notes);
    }

    /** Evita acoplar o repositório à aplicação apenas por causa do limite persistido. */
    private static final class LittleNotesServiceLimit {
        static final int MAX = 3;
    }
}
