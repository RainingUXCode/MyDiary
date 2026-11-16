package com.observernotes.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaylistTest {

    private static List<Path> files(String... names) {
        return java.util.Arrays.stream(names).map(n -> Path.of("/musicas", n)).toList();
    }

    private static Playlist three() {
        Playlist p = new Playlist();
        p.add(files("a.mp3", "b.wav", "c.mp3"));
        return p;
    }

    private static String titles(Playlist p) {
        return p.tracks().stream().map(Track::title).reduce("", String::concat);
    }

    @Test
    void emptyPlaylistHasNoCurrentTrack() {
        Playlist p = new Playlist();
        assertTrue(p.isEmpty());
        assertEquals(-1, p.currentIndex());
        assertNull(p.currentTrack());
        assertFalse(p.hasNext());
        p.advance(true);                                                 // não quebra
        assertFalse(p.remove(0));
        assertFalse(p.move(0, 1));
        assertFalse(p.select(0));
    }

    @Test
    void titleIsTheFileNameWithoutExtensionAndDuplicatesAreIgnored() {
        Playlist p = new Playlist();
        assertEquals(2, p.add(files("Minha Musica.mp3", "outra.v2.wav")));
        assertEquals("Minha Musica", p.tracks().get(0).title());
        assertEquals("outra.v2", p.tracks().get(1).title());
        assertEquals(0, p.add(files("Minha Musica.mp3")));
        assertEquals(2, p.size());
        assertEquals(0, p.currentIndex());
    }

    @Test
    void advanceWrapsBothWays() {
        Playlist p = three();
        p.advance(false);
        assertEquals(2, p.currentIndex());
        p.advance(true);
        assertEquals(0, p.currentIndex());
        assertTrue(p.hasNext());
        p.select(2);
        assertFalse(p.hasNext());
    }

    @Test
    void removeKeepsTheCurrentTrackConsistent() {
        Playlist p = three();
        p.select(1);
        p.remove(0);                                                     // antes da atual
        assertEquals(0, p.currentIndex());
        assertEquals("b", p.currentTrack().title());
        assertTrue(p.remove(0));                                         // a própria atual
        assertEquals(0, p.currentIndex());
        assertEquals("c", p.currentTrack().title());
        p.remove(0);
        assertTrue(p.isEmpty());
        assertEquals(-1, p.currentIndex());
    }

    @Test
    void removingTheLastCurrentTrackMovesToTheNewLast() {
        Playlist p = three();
        p.select(2);
        p.remove(2);
        assertEquals(1, p.currentIndex());
    }

    @Test
    void moveKeepsPlayingTheSameSong() {
        Playlist p = three();
        p.select(1);
        assertTrue(p.move(1, -1));
        assertEquals("bac", titles(p));
        assertEquals(0, p.currentIndex());
        assertEquals("b", p.currentTrack().title());
        p.select(2);
        p.move(0, 1);                                                    // b passa por trás de a; c é a atual
        assertEquals("abc", titles(p));
        assertEquals("c", p.currentTrack().title());
        p.select(1);
        p.move(0, 2);                                                    // a passa por cima da atual (b)
        assertEquals("bca", titles(p));
        assertEquals("b", p.currentTrack().title());
        assertFalse(p.move(0, -1));
        assertFalse(p.move(2, 1));
    }

    @Test
    void restoreNormalizesAnInvalidIndexAndDuplicates() {
        Playlist p = new Playlist();
        Track a = Track.of(Path.of("/m/a.mp3"));
        p.restore(List.of(a, a, Track.of(Path.of("/m/b.mp3"))), 99);
        assertEquals(2, p.size());
        assertEquals(0, p.currentIndex());
        p.restore(List.of(), 3);
        assertEquals(-1, p.currentIndex());
    }

    @Test
    void supportedFormatsAreCaseInsensitiveAndRejectOthers() {
        assertTrue(SupportedFormats.isSupported(Path.of("x/Som.MP3")));
        assertTrue(SupportedFormats.isSupported(Path.of("x.wav")));
        assertFalse(SupportedFormats.isSupported(Path.of("x.txt")));
        assertFalse(SupportedFormats.isSupported(Path.of("semextensao")));
        assertFalse(SupportedFormats.isSupported(null));
    }
}
