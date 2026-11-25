package com.observernotes.application;

import com.observernotes.notes.LittleNote;
import com.observernotes.notes.NotesRepository;
import com.observernotes.persistence.LoadResult;
import com.observernotes.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Casos de uso do mural. Não publica eventos e não conhece tarefas, histórico ou pontuação. */
public final class LittleNotesService {

    public static final int MAX_NOTES = 3;

    private final NotesRepository repository;
    private final List<String> startupMessages;
    private List<LittleNote> notes;
    private PersistenceException lastSaveError;

    public LittleNotesService(NotesRepository repository) {
        this.repository = repository;
        LoadResult<List<LittleNote>> loaded = repository.load();
        this.notes = validated(loaded.value());
        this.startupMessages = List.copyOf(loaded.messages());
        if (loaded.status() == LoadResult.Status.RECOVERED_FROM_BACKUP) {
            saveRecoveredCopy();
        }
    }

    /** Serviço vazio em memória para prévias e construtores legados de teste. */
    public static LittleNotesService empty() {
        return new LittleNotesService(new NotesRepository() {
            @Override public LoadResult<List<LittleNote>> load() {
                return new LoadResult<>(List.of(), LoadResult.Status.FRESH, List.of());
            }
            @Override public void save(List<LittleNote> ignored) { }
        });
    }

    public List<LittleNote> notes() {
        return List.copyOf(notes);
    }

    public List<String> startupMessages() {
        return startupMessages;
    }

    public Optional<PersistenceException> lastSaveError() {
        return Optional.ofNullable(lastSaveError);
    }

    public LittleNote add(String text) {
        if (notes.size() >= MAX_NOTES) {
            throw new IllegalStateException("máximo de " + MAX_NOTES + " bilhetes");
        }
        LittleNote note = new LittleNote(UUID.randomUUID().toString(), text);
        List<LittleNote> next = new ArrayList<>(notes);
        next.add(note);
        persist(next);
        return note;
    }

    public void update(String id, String text) {
        List<LittleNote> next = new ArrayList<>(notes);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(id)) {
                next.set(i, new LittleNote(id, text));
                persist(next);
                return;
            }
        }
    }

    public boolean remove(String id) {
        List<LittleNote> next = new ArrayList<>(notes);
        boolean removed = next.removeIf(n -> n.id().equals(id));
        if (removed) {
            persist(next);
        }
        return removed;
    }

    private void persist(List<LittleNote> next) {
        try {
            repository.save(List.copyOf(next));
            notes = List.copyOf(next);
            lastSaveError = null;
        } catch (PersistenceException e) {
            lastSaveError = e;
            throw e;
        }
    }

    private void saveRecoveredCopy() {
        try {
            repository.save(notes);
        } catch (PersistenceException e) {
            lastSaveError = e;
        }
    }

    private static List<LittleNote> validated(List<LittleNote> loaded) {
        List<LittleNote> source = loaded == null ? List.of() : loaded;
        if (source.size() > MAX_NOTES) {
            throw new IllegalArgumentException("arquivo contém mais de " + MAX_NOTES + " bilhetes");
        }
        HashSet<String> ids = new HashSet<>();
        for (LittleNote note : source) {
            if (!ids.add(note.id())) {
                throw new IllegalArgumentException("identificador de bilhete duplicado: " + note.id());
            }
        }
        return List.copyOf(source);
    }
}
