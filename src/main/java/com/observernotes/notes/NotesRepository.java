package com.observernotes.notes;

import com.observernotes.persistence.LoadResult;
import java.util.List;

/** Persistência separada do mural Little Notes. */
public interface NotesRepository {
    LoadResult<List<LittleNote>> load();
    void save(List<LittleNote> notes);
}
