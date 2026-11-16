package com.observernotes.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Playlist padrão: extração na primeira execução, títulos de exibição, sem duplicar e sem mexer nas músicas do usuário. */
class DefaultPlaylistTest {

    private static final String INDEX = "# comentário\nsweet.mp3|Sweet Morning\npink.mp3|Pink Café\n../x.mp3|Ruim\n";

    private static DefaultPlaylist.Resources res() {
        return name -> {
            if (name.equals("playlist.txt")) {
                return new ByteArrayInputStream(INDEX.getBytes(StandardCharsets.UTF_8));
            }
            return name.endsWith(".mp3") && !name.contains("..") ? new ByteArrayInputStream(new byte[] {1, 2, 3}) : null;
        };
    }

    @Test
    void firstRunExtractsFilesAndAddsThemWithDisplayTitles() throws IOException {
        Path dir = Files.createTempDirectory("on-default");
        JsonPlaylistStore store = new JsonPlaylistStore(dir);
        assertEquals(2, DefaultPlaylist.seedIfNeeded(dir, store, res()));
        var loaded = store.load().data();
        assertEquals(List.of("Sweet Morning", "Pink Café"), loaded.tracks().stream().map(Track::title).toList());
        assertTrue(Files.isRegularFile(Path.of(loaded.tracks().get(0).path())));
        assertEquals(0, loaded.currentIndex());
        assertEquals(0, DefaultPlaylist.seedIfNeeded(dir, store, res()), "só na primeira vez");
    }

    @Test
    void userTracksAreKeptFirstAndRemovedDefaultsDoNotComeBack() throws IOException {
        Path dir = Files.createTempDirectory("on-default2");
        JsonPlaylistStore store = new JsonPlaylistStore(dir);
        Path mine = Files.createFile(dir.resolve("minha.mp3"));
        store.save(new StoredPlayer(List.of(Track.of(mine)), 0, 0.4, true));
        DefaultPlaylist.seedIfNeeded(dir, store, res());
        var data = store.load().data();
        assertEquals(List.of("minha", "Sweet Morning", "Pink Café"), data.tracks().stream().map(Track::title).toList());
        assertEquals(0.4, data.volume(), 0.0001);
        assertTrue(data.repeat());
        store.save(new StoredPlayer(List.of(data.tracks().get(0)), 0, 0.4, true));    // usuário remove as padrão
        DefaultPlaylist.seedIfNeeded(dir, store, res());
        assertEquals(1, store.load().data().tracks().size());
    }

    @Test
    void storedDisplayTitlesSurviveReloading() throws IOException {
        Path dir = Files.createTempDirectory("on-default3");
        JsonPlaylistStore store = new JsonPlaylistStore(dir);
        store.save(new StoredPlayer(List.of(new Track(dir.resolve("a-b.mp3").toAbsolutePath().toString(), "Título Bonito")), 0, 0.7, false));
        assertEquals("Título Bonito", store.load().data().tracks().get(0).title());
    }

    @Test
    void bundledPackageListsSevenUniqueMp3sThatReallyExist() throws IOException {
        var seen = new java.util.HashSet<String>();
        try (var in = DefaultPlaylist.class.getResourceAsStream("/assets/music/playlist.txt")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|");
                assertTrue(seen.add(p[0]));
                try (var mp3 = DefaultPlaylist.class.getResourceAsStream("/assets/music/" + p[0])) {
                    assertFalse(mp3 == null, p[0]);
                }
            }
        }
        assertEquals(7, seen.size());
    }
}
