package com.observernotes.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PlayerEngineTest {

    private final FakeBackend backend = new FakeBackend();
    private final AtomicInteger changes = new AtomicInteger();
    private final PlayerEngine engine = new PlayerEngine(backend, changes::incrementAndGet);

    private void load(String... names) {
        engine.add(java.util.Arrays.stream(names).map(n -> Path.of("/m", n)).toList());
    }

    private String playing() {
        return backend.last().file.getFileName().toString();
    }

    @Test
    void emptyPlaylistNeverStartsAndExplainsWhy() {
        engine.play();
        engine.next();
        engine.previous();
        engine.toggle();
        assertEquals(0, backend.openCount());
        assertEquals(PlaybackState.STOPPED, engine.snapshot().state());
        assertTrue(engine.snapshot().message().contains("Playlist vazia"));
        assertFalse(engine.snapshot().hasTracks());
    }

    @Test
    void playPauseResumeUseTheSamePlaybackWithoutReopening() {
        load("a.wav", "b.wav");
        engine.play();
        assertTrue(backend.last().started);
        assertEquals(PlaybackState.PLAYING, engine.snapshot().state());
        engine.pause();
        assertTrue(backend.last().paused);
        assertEquals(PlaybackState.PAUSED, engine.snapshot().state());
        engine.toggle();                                                 // retoma
        assertFalse(backend.last().paused);
        assertEquals(PlaybackState.PLAYING, engine.snapshot().state());
        assertEquals(1, backend.openCount());
        engine.toggle();
        assertEquals(PlaybackState.PAUSED, engine.snapshot().state());
    }

    @Test
    void nextAndPreviousSwitchTracksAndReleaseThePreviousOne() {
        load("a.wav", "b.wav", "c.wav");
        engine.play();
        var first = backend.last();
        engine.next();
        assertTrue(first.closed);
        assertEquals("b.wav", playing());
        engine.next();
        engine.next();                                                   // dá a volta
        assertEquals("a.wav", playing());
        engine.previous();
        assertEquals("c.wav", playing());
        assertEquals(1, backend.openNow());                              // só uma faixa aberta por vez
    }

    @Test
    void playIndexChoosesTheTrackEvenWhilePausedOrStopped() {
        load("a.wav", "b.wav", "c.wav");
        engine.playIndex(2);
        assertEquals("c.wav", playing());
        engine.pause();
        engine.playIndex(1);
        assertEquals("b.wav", playing());
        assertEquals(PlaybackState.PLAYING, engine.snapshot().state());
        engine.playIndex(9);                                             // inválido: ignora
        assertEquals("b.wav", playing());
    }

    @Test
    void finishedTrackAdvancesAutomaticallyAndStopsAtTheEnd() {
        load("a.wav", "b.wav");
        engine.play();
        backend.last().finish();
        assertEquals("b.wav", playing());
        assertEquals(PlaybackState.PLAYING, engine.snapshot().state());
        backend.last().finish();                                         // fim da lista, sem repetição
        assertEquals(PlaybackState.STOPPED, engine.snapshot().state());
        assertEquals(0, engine.snapshot().currentIndex());               // pronta para tocar de novo
        assertEquals(0, backend.openNow());
        assertEquals(2, backend.openCount());
    }

    @Test
    void repeatLoopsTheWholePlaylistAndASingleTrack() {
        load("a.wav", "b.wav");
        engine.setRepeat(true);
        engine.play();
        backend.last().finish();
        backend.last().finish();
        assertEquals("a.wav", playing());
        assertEquals(PlaybackState.PLAYING, engine.snapshot().state());
        PlayerEngine solo = new PlayerEngine(backend, () -> { });
        solo.add(List.of(Path.of("/m/solo.wav")));
        solo.setRepeat(true);
        solo.play();
        long before = backend.openCount();
        backend.last().finish();
        assertEquals(before + 1, backend.openCount());
        assertEquals(PlaybackState.PLAYING, solo.snapshot().state());
    }

    @Test
    void staleFinishCallbackFromAReplacedTrackIsIgnored() {
        load("a.wav", "b.wav", "c.wav");
        engine.play();
        var old = backend.last();
        engine.next();                                                   // a foi fechada e substituída por b
        old.onFinished.run();                                            // thread de áudio atrasada
        assertEquals("b.wav", playing());
        assertEquals(2, backend.openCount());
    }

    @Test
    void volumeIsClampedAppliedToTheCurrentAndKeptForTheNextTrack() {
        load("a.wav", "b.wav");
        engine.play();
        engine.setVolume(0.25);
        assertEquals(0.25, backend.last().volume, 1e-9);
        engine.setVolume(5);
        assertEquals(1.0, engine.snapshot().volume(), 1e-9);
        engine.setVolume(-1);
        assertEquals(0.0, engine.snapshot().volume(), 1e-9);
        engine.setVolume(0.4);
        engine.next();
        assertEquals(0.4, backend.last().volume, 1e-9);
    }

    @Test
    void missingTrackIsSkippedMarkedUnavailableAndReported() {
        load("a.wav", "b.wav", "c.wav");
        backend.failing.add("a.wav");
        engine.play();
        assertEquals("b.wav", playing());
        assertEquals(PlaybackState.PLAYING, engine.snapshot().state());
        assertFalse(engine.snapshot().tracks().get(0).available());
        backend.failing.clear();                                         // arquivo voltou
        engine.playIndex(0);
        assertEquals("a.wav", playing());
    }

    @Test
    void whenNothingCanBePlayedItStopsWithoutLoopingForever() {
        load("a.wav", "b.wav");
        backend.failing.addAll(List.of("a.wav", "b.wav"));
        engine.play();
        assertEquals(PlaybackState.STOPPED, engine.snapshot().state());
        assertTrue(engine.snapshot().message().contains("Nenhuma música"));
        assertEquals(0, backend.openCount());
        engine.setRepeat(true);
        engine.next();
        assertEquals(PlaybackState.STOPPED, engine.snapshot().state());
    }

    @Test
    void autoAdvanceSkipsABrokenTrackInTheMiddle() {
        load("a.wav", "b.wav", "c.wav");
        backend.failing.add("b.wav");
        engine.play();
        backend.last().finish();
        assertEquals("c.wav", playing());
    }

    @Test
    void removingTheCurrentTrackWhilePlayingMovesOnAndReleasesAudio() {
        load("a.wav", "b.wav");
        engine.play();
        var first = backend.last();
        engine.remove(0);
        assertTrue(first.closed);
        assertEquals("b.wav", playing());
        assertEquals(PlaybackState.PLAYING, engine.snapshot().state());
        engine.remove(0);
        assertEquals(PlaybackState.STOPPED, engine.snapshot().state());
        assertEquals(0, backend.openNow());
        assertFalse(engine.snapshot().hasTracks());
    }

    @Test
    void removingAnotherTrackDoesNotInterruptPlayback() {
        load("a.wav", "b.wav", "c.wav");
        engine.playIndex(1);
        var p = backend.last();
        engine.remove(0);
        assertFalse(p.closed);
        assertEquals(0, engine.snapshot().currentIndex());
        assertEquals("b", engine.snapshot().currentTitle());
    }

    @Test
    void reorderingNeverRestartsTheMusic() {
        load("a.wav", "b.wav", "c.wav");
        engine.playIndex(0);
        long opened = backend.openCount();
        engine.move(0, 1);
        engine.move(1, 1);
        assertEquals(opened, backend.openCount());
        assertEquals("a", engine.snapshot().currentTitle());
        assertEquals(2, engine.snapshot().currentIndex());
    }

    @Test
    void closeReleasesEverythingAndLaterCommandsDoNothing() {
        load("a.wav", "b.wav");
        engine.play();
        var p = backend.last();
        engine.close();
        assertTrue(p.closed);
        assertEquals(0, backend.openNow());
        engine.play();
        engine.next();
        p.onFinished.run();                                              // fim atrasado depois de fechar
        assertEquals(1, backend.openCount());
        engine.close();                                                  // idempotente
    }

    @Test
    void restoreDoesNotPlayAnythingAndStopsWhatWasPlaying() {
        load("a.wav");
        engine.play();
        var p = backend.last();
        engine.restore(new StoredPlayer(List.of(Track.of(Path.of("/m/x.mp3")), Track.of(Path.of("/m/y.mp3"))), 1, 0.3, true));
        assertTrue(p.closed);
        assertEquals(PlaybackState.STOPPED, engine.snapshot().state());
        assertEquals(1, backend.openCount());
        assertEquals("y", engine.snapshot().currentTitle());
        assertTrue(engine.snapshot().repeat());
        assertEquals(0.3, engine.snapshot().volume(), 1e-9);
    }

    @Test
    void aBrokenListenerCannotBreakThePlayer() {
        PlayerEngine e = new PlayerEngine(backend, () -> {
            throw new IllegalStateException("ouvinte com defeito");
        });
        e.add(List.of(Path.of("/m/a.wav")));
        e.play();
        assertEquals(PlaybackState.PLAYING, e.snapshot().state());
    }

    @Test
    void listenerIsNotifiedOfChanges() {
        load("a.wav");
        int before = changes.get();
        engine.play();
        engine.pause();
        assertTrue(changes.get() >= before + 2);
    }
}
