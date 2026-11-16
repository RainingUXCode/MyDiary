package com.observernotes.music;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Referência a um arquivo de música no computador do usuário (o arquivo nunca é copiado). */
public record Track(String path, String title) {

    public Track {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(title, "title");
    }

    /** Título = nome do arquivo sem a extensão. */
    public static Track of(Path file) {
        Path abs = file.toAbsolutePath().normalize();
        String name = abs.getFileName() == null ? abs.toString() : abs.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        return new Track(abs.toString(), title.isBlank() ? name : title);
    }

    public Path file() {
        return Path.of(path);
    }

    /** Chave de comparação: o mesmo arquivo nunca entra duas vezes na playlist. */
    String key() {
        return path.toLowerCase(Locale.ROOT);
    }
}
