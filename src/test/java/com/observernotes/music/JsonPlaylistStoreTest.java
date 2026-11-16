package com.observernotes.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.support.TempFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonPlaylistStoreTest {

    private Path dir;
    private JsonPlaylistStore store;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        store = new JsonPlaylistStore(dir);
    }

    @AfterEach
    void tearDown() throws Exception {
        TempFiles.deleteRecursively(dir);
    }

    private Path file() {
        return dir.resolve(JsonPlaylistStore.FILE_NAME);
    }

    @Test
    void missingFileMeansAnEmptyPlaylistWithoutWarning() {
        var loaded = store.load();
        assertEquals(StoredPlayer.empty(), loaded.data());
        assertEquals("", loaded.warning());
    }

    @Test
    void saveThenLoadRoundTripsReferencesVolumeRepeatAndPosition() throws Exception {
        StoredPlayer data = new StoredPlayer(List.of(Track.of(Path.of("/musicas/a.mp3")),
                Track.of(Path.of("/musicas/b.wav"))), 1, 0.35, true);
        store.save(data);
        assertEquals(data, store.load().data());
    }

    @Test
    void onlyPathsAreStoredNeverTheAudioItself() throws Exception {
        store.save(new StoredPlayer(List.of(Track.of(Path.of("/musicas/a.mp3"))), 0, 0.5, false));
        assertTrue(Files.size(file()) < 1000);
        try (var s = Files.list(dir)) {
            assertEquals(1, s.count());                                  // nenhuma cópia de música
        }
    }

    @Test
    void corruptFileIsKeptAsInvalidAndReplacedByAnEmptyPlaylistWithAWarning() throws Exception {
        Files.writeString(file(), "{ isto não é json", StandardCharsets.UTF_8);
        var loaded = store.load();
        assertEquals(StoredPlayer.empty(), loaded.data());
        assertTrue(loaded.warning().contains("playlist"));
        assertTrue(Files.exists(dir.resolve(JsonPlaylistStore.FILE_NAME + ".invalido")));
        assertFalse(Files.exists(file()));
    }

    @Test
    void unknownFormatVersionIsTreatedAsInvalid() throws Exception {
        Files.writeString(file(), "{\"formatVersion\":99,\"tracks\":[]}", StandardCharsets.UTF_8);
        assertFalse(store.load().warning().isEmpty());
    }

    @Test
    void incompleteOrOutOfRangeFieldsFallBackToSafeValues() throws Exception {
        Files.writeString(file(), "{\"formatVersion\":1,\"volume\":7.5,\"currentIndex\":40,"
                + "\"tracks\":[{\"path\":\"/m/a.mp3\"},{\"title\":\"sem caminho\"},null,{\"path\":\"\"}]}",
                StandardCharsets.UTF_8);
        var d = store.load().data();
        assertEquals(1.0, d.volume(), 1e-9);
        assertEquals(1, d.tracks().size());
        assertFalse(d.repeat());
        assertEquals("a", d.tracks().get(0).title());
    }
}
