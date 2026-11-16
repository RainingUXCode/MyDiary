package com.observernotes.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.support.TempFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MusicPlayerTest {

    private Path dir;
    private FakeBackend backend;
    private AtomicInteger changes;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        backend = new FakeBackend();
        changes = new AtomicInteger();
    }

    @AfterEach
    void tearDown() throws Exception {
        TempFiles.deleteRecursively(dir);
    }

    private MusicPlayer open() {
        return new MusicPlayer(backend, new JsonPlaylistStore(dir), changes::incrementAndGet);
    }

    private Path song(String name) throws IOException {
        return Files.write(dir.resolve(name), new byte[] {1, 2, 3});
    }

    @Test
    void addFilesFiltersByFormatAndCountsDuplicates() throws Exception {
        MusicPlayer m = open();
        var r = m.addFiles(List.of(song("a.mp3"), song("b.wav"), song("capa.jpg"), song("a.mp3")));
        assertEquals(2, r.added());
        assertEquals(1, r.unsupported());
        assertEquals(1, r.duplicates());
        assertEquals(2, m.snapshot().tracks().size());
        assertEquals(0, backend.openCount());                            // adicionar nunca toca
    }

    @Test
    void playlistVolumeRepeatAndPositionComeBackAfterRestartWithoutPlaying() throws Exception {
        MusicPlayer m = open();
        m.addFiles(List.of(song("a.mp3"), song("b.wav"), song("c.wav")));
        m.playIndex(1);
        m.setVolume(0.35);
        m.toggleRepeat();
        m.move(2, -1);
        m.close();
        long opened = backend.openCount();
        MusicPlayer again = open();
        var s = again.snapshot();
        assertEquals(3, s.tracks().size());
        assertEquals("a,c,b", String.join(",", s.tracks().stream().map(PlayerSnapshot.TrackInfo::title).toList()));
        assertEquals("b", s.currentTitle());
        assertEquals(0.35, s.volume(), 1e-9);
        assertTrue(s.repeat());
        assertEquals(PlaybackState.STOPPED, s.state());                  // não toca sozinho
        assertEquals(opened, backend.openCount());
    }

    @Test
    void movedOrDeletedFilesAreShownAsUnavailableAndDoNotBreakStartup() throws Exception {
        Path a = song("a.wav");
        Path b = song("b.wav");
        MusicPlayer m = open();
        m.addFiles(List.of(a, b));
        m.close();
        Files.delete(a);
        MusicPlayer again = open();
        var s = again.snapshot();
        assertEquals(2, s.tracks().size());
        assertFalse(s.tracks().get(0).available());
        assertTrue(s.tracks().get(1).available());
        backend.failing.add("a.wav");
        again.play();                                                    // pula a ausente
        assertEquals(PlaybackState.PLAYING, again.snapshot().state());
        assertEquals("b", again.snapshot().currentTitle());
        again.close();
    }

    @Test
    void removalIsPersistedAndTheMusicFileIsNeverTouched() throws Exception {
        Path a = song("a.wav");
        MusicPlayer m = open();
        m.addFiles(List.of(a, song("b.wav")));
        m.remove(0);
        m.close();
        assertTrue(Files.exists(a));
        assertEquals(1, open().snapshot().tracks().size());
    }

    @Test
    void corruptSavedPlaylistStartsEmptyWithAWarning() throws Exception {
        Files.writeString(dir.resolve(JsonPlaylistStore.FILE_NAME), "lixo");
        MusicPlayer m = open();
        assertFalse(m.startupWarning().isEmpty());
        assertFalse(m.snapshot().hasTracks());
        m.addFiles(List.of(song("a.wav")));                              // e volta a funcionar
        assertEquals(1, open().snapshot().tracks().size());
    }

    @Test
    void failureToSaveIsReportedButPlaybackKeepsWorking() throws Exception {
        PlaylistStore broken = new PlaylistStore() {
            @Override
            public Loaded load() {
                return new Loaded(StoredPlayer.empty(), "");
            }

            @Override
            public void save(StoredPlayer data) throws IOException {
                throw new IOException("disco cheio");
            }
        };
        MusicPlayer m = new MusicPlayer(backend, broken, changes::incrementAndGet);
        m.addFiles(List.of(song("a.wav")));
        assertTrue(m.snapshot().message().contains("salvar"));
        m.play();
        assertEquals(PlaybackState.PLAYING, m.snapshot().state());
    }

    @Test
    void volumeStepsAndRepeatToggleAreSavedWithoutWritingOnEveryTick() throws Exception {
        MusicPlayer m = open();
        m.adjustVolume(-0.1);
        m.adjustVolume(-0.1);
        assertEquals(0.5, m.snapshot().volume(), 1e-9);
        m.toggleRepeat();
        m.toggleRepeat();
        m.close();
        assertEquals(0.5, open().snapshot().volume(), 1e-9);
        assertFalse(open().snapshot().repeat());
    }

    @Test
    void autoAdvancePersistsTheNewPosition() throws Exception {
        MusicPlayer m = open();
        m.addFiles(List.of(song("a.wav"), song("b.wav")));
        m.play();
        backend.last().finish();                                         // a terminou, b começa
        assertEquals("b", m.snapshot().currentTitle());
        assertEquals("b", open().snapshot().currentTitle());
        m.close();
    }

    @Test
    void closeReleasesAudioAndStaysSafeToRepeat() throws Exception {
        MusicPlayer m = open();
        m.addFiles(List.of(song("a.wav")));
        m.play();
        m.close();
        assertEquals(0, backend.openNow());
        m.close();
    }
}
