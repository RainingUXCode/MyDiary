package com.observernotes.persistence;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Pasta de dados do usuário, fora do projeto e fora do JAR:
 * Windows {@code %APPDATA%\ObserverNotes}, macOS {@code ~/Library/Application Support/ObserverNotes},
 * Linux {@code $XDG_DATA_HOME/observer-notes} (ou {@code ~/.local/share/observer-notes}).
 * A propriedade {@code observernotes.data.dir} substitui o padrão (útil em testes).
 */
public final class UserDataDirectory {

    public static final String OVERRIDE_PROPERTY = "observernotes.data.dir";

    private UserDataDirectory() { }

    public static Path resolve() {
        return resolve(System::getProperty, System::getenv);
    }

    static Path resolve(Function<String, String> property, Function<String, String> env) {
        String override = property.apply(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String home = property.apply("user.home");
        String os = String.valueOf(property.apply("os.name")).toLowerCase();
        if (os.contains("win")) {
            String appData = env.apply("APPDATA");
            Path base = appData != null && !appData.isBlank() ? Path.of(appData) : Path.of(home, "AppData", "Roaming");
            return base.resolve("ObserverNotes");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "ObserverNotes");
        }
        String xdg = env.apply("XDG_DATA_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".local", "share");
        return base.resolve("observer-notes");
    }
}
