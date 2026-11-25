package com.observernotes.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.notes.LittleNote;
import com.observernotes.support.TempFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonNotesRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC);

    private Path dir;
    private JsonNotesRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        dir = TempFiles.create();
        repo = new JsonNotesRepository(dir, new AtomicFileWriter(), CLOCK);
    }

    @AfterEach
    void tearDown() throws IOException {
        TempFiles.deleteRecursively(dir);
    }

    private JsonNotesRepository fresh() {
        return new JsonNotesRepository(dir, new AtomicFileWriter(), CLOCK);
    }

    @Test
    void missingFileStartsEmptyWithoutCreatingAnything() {
        LoadResult<List<LittleNote>> loaded = repo.load();
        assertEquals(LoadResult.Status.FRESH, loaded.status());
        assertTrue(loaded.value().isEmpty());
        assertFalse(Files.exists(repo.file()));
    }

    @Test
    void savesAndLoadsTheThreeNotesInOrder() throws IOException {
        List<LittleNote> notes = List.of(
                new LittleNote("a", "Beber água"),
                new LittleNote("b", "Ligar pra vovó"),
                new LittleNote("c", "Você consegue!"));
        repo.save(notes);

        LoadResult<List<LittleNote>> loaded = fresh().load();
        assertEquals(LoadResult.Status.LOADED, loaded.status());
        assertEquals(notes, loaded.value());
        assertTrue(TempFiles.read(repo.file()).contains("\"formatVersion\": 1"));
    }

    @Test
    void corruptFileStartsEmptyAndIsPreserved() throws IOException {
        TempFiles.write(repo.file(), "{{{");
        LoadResult<List<LittleNote>> loaded = repo.load();
        assertEquals(LoadResult.Status.DISCARDED, loaded.status());
        assertTrue(loaded.value().isEmpty());
        assertFalse(Files.exists(repo.file()));
        assertEquals(1, TempFiles.countPreserved(repo.file()));
    }

    @Test
    void corruptMainFileRecoversTheLastGoodBackup() throws IOException {
        List<LittleNote> first = List.of(new LittleNote("a", "Primeiro"));
        repo.save(first);
        repo.save(List.of(new LittleNote("b", "Segundo")));
        TempFiles.write(repo.file(), "{corrompido");

        LoadResult<List<LittleNote>> loaded = fresh().load();
        assertEquals(LoadResult.Status.RECOVERED_FROM_BACKUP, loaded.status());
        assertEquals(first, loaded.value());
        assertEquals(1, TempFiles.countPreserved(repo.file()));
    }
}
