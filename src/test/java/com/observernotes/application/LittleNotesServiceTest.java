package com.observernotes.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.observernotes.notes.LittleNote;
import com.observernotes.notes.NotesRepository;
import com.observernotes.persistence.LoadResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class LittleNotesServiceTest {

    private static LittleNotesService service() {
        return new LittleNotesService(new NotesRepository() {
            @Override public LoadResult<List<LittleNote>> load() {
                return new LoadResult<>(List.of(), LoadResult.Status.FRESH, List.of());
            }
            @Override public void save(List<LittleNote> notes) { }
        });
    }

    @Test
    void acceptsAtMostThreeNotes() {
        LittleNotesService notes = service();
        notes.add("um");
        notes.add("dois");
        notes.add("três");
        assertEquals(3, notes.notes().size());
        assertThrows(IllegalStateException.class, () -> notes.add("quatro"));
    }

    @Test
    void acceptsSixtyCharactersButRejectsSixtyOne() {
        LittleNotesService notes = service();
        notes.add("x".repeat(60));
        assertEquals(60, notes.notes().get(0).text().length());
        assertThrows(IllegalArgumentException.class, () -> notes.update(notes.notes().get(0).id(), "x".repeat(61)));
    }
}
