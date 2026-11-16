package com.observernotes.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.observernotes.support.TempFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AtomicFileWriterTest {

    private Path dir;
    private final AtomicFileWriter writer = new AtomicFileWriter();

    @BeforeEach
    void setUp() throws IOException {
        dir = TempFiles.create();
    }

    @AfterEach
    void tearDown() throws IOException {
        TempFiles.deleteRecursively(dir);
    }

    @Test
    @DisplayName("cria o arquivo e as pastas que faltam, sem deixar temporário")
    void createsFileAndParents() throws IOException {
        Path target = dir.resolve("a/b/state.json");
        writer.write(target, "olá".getBytes(StandardCharsets.UTF_8));
        assertEquals("olá", TempFiles.read(target));
        assertFalse(Files.exists(dir.resolve("a/b/state.json.tmp")));
    }

    @Test
    @DisplayName("substitui o conteúdo anterior por inteiro")
    void replacesExisting() throws IOException {
        Path target = dir.resolve("state.json");
        writer.write(target, "um conteúdo bem longo e antigo".getBytes(StandardCharsets.UTF_8));
        writer.write(target, "novo".getBytes(StandardCharsets.UTF_8));
        assertEquals("novo", TempFiles.read(target));
        assertFalse(Files.exists(dir.resolve("state.json.tmp")));
    }

    @Test
    @DisplayName("se a gravação falha, o arquivo original permanece intacto")
    void failureKeepsOriginal() throws IOException {
        Path target = dir.resolve("state.json");
        writer.write(target, "original".getBytes(StandardCharsets.UTF_8));
        // Um diretório não vazio no lugar do temporário impede a escrita.
        Path blocker = dir.resolve("state.json.tmp");
        Files.createDirectories(blocker);
        Files.writeString(blocker.resolve("x"), "x");

        assertThrows(IOException.class, () -> writer.write(target, "novo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("original", TempFiles.read(target));
    }
}
