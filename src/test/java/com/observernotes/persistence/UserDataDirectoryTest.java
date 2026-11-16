package com.observernotes.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserDataDirectoryTest {

    private static Path resolve(Map<String, String> props, Map<String, String> env) {
        return UserDataDirectory.resolve(props::get, env::get);
    }

    @Test
    @DisplayName("a propriedade observernotes.data.dir substitui o padrão")
    void overrideWins() {
        Path p = resolve(Map.of("observernotes.data.dir", "/dados", "os.name", "Linux", "user.home", "/home/u"), Map.of());
        assertEquals(Path.of("/dados"), p);
    }

    @Test
    @DisplayName("Windows usa APPDATA")
    void windows() {
        Path p = resolve(Map.of("os.name", "Windows 11", "user.home", "C:/Users/u"), Map.of("APPDATA", "C:/Roaming"));
        assertEquals(Path.of("C:/Roaming", "ObserverNotes"), p);
    }

    @Test
    @DisplayName("macOS usa Application Support")
    void mac() {
        Path p = resolve(Map.of("os.name", "Mac OS X", "user.home", "/Users/u"), Map.of());
        assertEquals(Path.of("/Users/u", "Library", "Application Support", "ObserverNotes"), p);
    }

    @Test
    @DisplayName("Linux usa XDG_DATA_HOME ou ~/.local/share")
    void linux() {
        assertEquals(Path.of("/xdg", "observer-notes"),
                resolve(Map.of("os.name", "Linux", "user.home", "/home/u"), Map.of("XDG_DATA_HOME", "/xdg")));
        assertEquals(Path.of("/home/u", ".local", "share", "observer-notes"),
                resolve(Map.of("os.name", "Linux", "user.home", "/home/u"), Map.of()));
    }
}
