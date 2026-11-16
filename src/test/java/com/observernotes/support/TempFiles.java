package com.observernotes.support;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Utilitários de arquivo para os testes de persistência. */
public final class TempFiles {

    private TempFiles() { }

    public static Path create() throws IOException {
        return Files.createTempDirectory("observernotes-test");
    }

    public static void deleteRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    public static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** Lê um JSON do disco, aplica a alteração e grava de volta (simula edição ou dano manual). */
    public static void mutateJson(Path file, Consumer<JsonObject> mutation) throws IOException {
        JsonObject root = JsonParser.parseString(read(file)).getAsJsonObject();
        mutation.accept(root);
        write(file, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    /** Arquivos {@code nome.corrupt-*} preservados ao lado de {@code file}. */
    public static long countPreserved(Path file) throws IOException {
        try (Stream<Path> s = Files.list(file.getParent())) {
            return s.filter(p -> p.getFileName().toString().startsWith(file.getFileName() + ".corrupt-")).count();
        }
    }

    public static Path firstPreserved(Path file) throws IOException {
        try (Stream<Path> s = Files.list(file.getParent())) {
            return s.filter(p -> p.getFileName().toString().startsWith(file.getFileName() + ".corrupt-"))
                    .sorted().findFirst().orElseThrow();
        }
    }
}
