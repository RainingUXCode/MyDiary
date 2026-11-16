package com.observernotes.music;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Extensões aceitas. WAV, AIFF e AU são lidos pelo próprio Java; MP3 depende da biblioteca
 * MP3SPI/JLayer (declarada no pom.xml), que se registra no {@code javax.sound}.
 */
public final class SupportedFormats {

    public static final List<String> EXTENSIONS = List.of("mp3", "wav", "wave", "aif", "aiff", "au");

    private SupportedFormats() { }

    public static boolean isSupported(Path file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }
}
