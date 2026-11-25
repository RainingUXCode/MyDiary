package com.observernotes.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.observernotes.settings.ObserverKind;
import com.observernotes.settings.ObserverPreferences;
import com.observernotes.support.TempFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonSettingsRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T21:40:00Z"), ZoneOffset.UTC);

    private Path dir;
    private JsonSettingsRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        dir = TempFiles.create();
        repo = new JsonSettingsRepository(dir, new AtomicFileWriter(), CLOCK);
    }

    @AfterEach
    void tearDown() throws IOException {
        TempFiles.deleteRecursively(dir);
    }

    @Test
    @DisplayName("sem arquivo, todos os observadores começam ativos")
    void defaults() {
        LoadResult<ObserverPreferences> r = repo.load();
        assertEquals(LoadResult.Status.FRESH, r.status());
        for (ObserverKind k : ObserverKind.values()) {
            assertTrue(r.value().isEnabled(k));
        }
    }

    @Test
    @DisplayName("salva e restaura a ativação de cada observador")
    void roundTrip() {
        ObserverPreferences prefs = ObserverPreferences.allEnabled()
                .with(ObserverKind.COUNTER, false).with(ObserverKind.ACHIEVEMENTS, false);
        repo.save(prefs);
        LoadResult<ObserverPreferences> r = new JsonSettingsRepository(dir, new AtomicFileWriter(), CLOCK).load();
        assertEquals(LoadResult.Status.LOADED, r.status());
        assertEquals(prefs, r.value());
        assertFalse(r.value().isEnabled(ObserverKind.COUNTER));
        assertTrue(r.value().isEnabled(ObserverKind.HISTORY));
    }

    @Test
    @DisplayName("observador ausente no arquivo fica ativo; chave desconhecida é ignorada")
    void missingAndUnknownKeys() throws IOException {
        TempFiles.write(repo.file(), "{\"formatVersion\":1,\"observers\":{\"HISTORY\":false,\"FUTURO\":true}}");
        ObserverPreferences p = repo.load().value();
        assertFalse(p.isEnabled(ObserverKind.HISTORY));
        assertTrue(p.isEnabled(ObserverKind.COUNTER));
    }

    @Test
    @DisplayName("settings antigos com CONSTELLATION continuam abrindo e ignoram a chave removida")
    void legacyConstellationKeyIsIgnored() throws IOException {
        TempFiles.write(repo.file(), "{\"formatVersion\":1,\"observers\":{"
                + "\"HISTORY\":false,\"CONSTELLATION\":false,\"COUNTER\":true,\"ACHIEVEMENTS\":true}}");
        LoadResult<ObserverPreferences> loaded = repo.load();
        assertEquals(LoadResult.Status.LOADED, loaded.status());
        assertFalse(loaded.value().isEnabled(ObserverKind.HISTORY));
        assertTrue(loaded.value().isEnabled(ObserverKind.COUNTER));
        assertTrue(loaded.value().isEnabled(ObserverKind.ACHIEVEMENTS));
    }

    @Test
    @DisplayName("arquivo corrompido volta aos padrões e é preservado")
    void corruptFile() throws IOException {
        TempFiles.write(repo.file(), "{{{");
        LoadResult<ObserverPreferences> r = repo.load();
        assertEquals(LoadResult.Status.DISCARDED, r.status());
        assertEquals(ObserverPreferences.allEnabled(), r.value());
        assertEquals(1, TempFiles.countPreserved(repo.file()));
        assertFalse(Files.exists(repo.file()));
    }

    @Test
    @DisplayName("valor que não é booleano ou objeto ausente invalida o arquivo")
    void invalidValues() throws IOException {
        TempFiles.write(repo.file(), "{\"formatVersion\":1,\"observers\":{\"HISTORY\":\"nao\"}}");
        assertEquals(LoadResult.Status.DISCARDED, repo.load().status());
        TempFiles.write(repo.file(), "{\"formatVersion\":1}");
        assertEquals(LoadResult.Status.DISCARDED, repo.load().status());
    }

    @Test
    @DisplayName("falha de escrita nas preferências lança PersistenceException e mantém o arquivo")
    void writeFailure() throws IOException {
        repo.save(ObserverPreferences.allEnabled());
        String before = TempFiles.read(repo.file());
        JsonSettingsRepository failing = new JsonSettingsRepository(dir,
                (target, content) -> { throw new IOException("simulado"); }, CLOCK);
        failing.load();
        assertThrows(PersistenceException.class,
                () -> failing.save(ObserverPreferences.allEnabled().with(ObserverKind.HISTORY, false)));
        assertEquals(before, TempFiles.read(repo.file()));
    }
}
