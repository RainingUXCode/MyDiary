package com.observernotes.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.observernotes.domain.TaskView;
import com.observernotes.domain.TaskBoard;
import com.observernotes.observer.impl.HistoryObserver;
import com.observernotes.support.TempFiles;
import com.observernotes.support.Snapshots;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonStateRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T21:40:00Z"), ZoneOffset.UTC);

    private Path dir;
    private JsonStateRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        dir = TempFiles.create();
        repo = new JsonStateRepository(dir, new AtomicFileWriter(), CLOCK);
    }

    @AfterEach
    void tearDown() throws IOException {
        TempFiles.deleteRecursively(dir);
    }

    private JsonStateRepository fresh() {
        return new JsonStateRepository(dir, new AtomicFileWriter(), CLOCK);
    }

    // ---- salvar e restaurar ------------------------------------------------------

    @Test
    @DisplayName("salva e restaura tarefas, UUIDs, estados, ledger e histórico sem perdas")
    void roundTrip() {
        AppSnapshot original = Snapshots.sample();
        repo.save(original);
        LoadResult<AppSnapshot> loaded = fresh().load();
        assertEquals(LoadResult.Status.LOADED, loaded.status());
        assertFalse(loaded.hasWarnings());
        assertEquals(original, loaded.value());
        assertEquals(3, loaded.value().domain().tasks().size());
        assertEquals(3, loaded.value().domain().scoredTaskIds().size());
    }

    @Test
    @DisplayName("o ledger guarda os IDs na ordem em que pontuaram (a posição é o número do marco)")
    void ledgerOrderIsPreserved() {
        AppSnapshot original = Snapshots.sample();
        repo.save(original);
        assertEquals(original.domain().scoredTaskIds(), fresh().load().value().domain().scoredTaskIds());
    }

    @Test
    @DisplayName("o arquivo tem versão do formato e não guarda marcos derivados")
    void fileFormat() throws IOException {
        repo.save(Snapshots.sample());
        String text = TempFiles.read(repo.file());
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        assertEquals(1, root.get("formatVersion").getAsInt());
        assertTrue(root.has("tasks"));
        assertTrue(root.getAsJsonObject("achievements").has("scoredTaskIds"));
        assertEquals(1, root.getAsJsonObject("achievements").size());
        assertFalse(text.contains("milestone"));
        assertFalse(text.contains("stars"));
    }

    // ---- arquivo ausente ---------------------------------------------------------

    @Test
    @DisplayName("arquivo inexistente começa do zero, sem avisos e sem criar nada")
    void missingFile() throws IOException {
        LoadResult<AppSnapshot> r = repo.load();
        assertEquals(LoadResult.Status.FRESH, r.status());
        assertEquals(AppSnapshot.empty(), r.value());
        assertFalse(r.hasWarnings());
        assertFalse(Files.exists(repo.file()));
    }

    // ---- arquivo inválido / corrompido -------------------------------------------

    @Test
    @DisplayName("JSON inválido ou corrompido nunca derruba o app e é preservado, não apagado")
    void invalidContentsAreDiscardedButPreserved() throws IOException {
        repo.save(Snapshots.sample());
        String valid = TempFiles.read(repo.file());
        List<String> bad = List.of(
                "",
                "   ",
                "{",
                "não é json",
                "[]",
                "null",
                "{\"formatVersion\": 1}",
                valid + " lixo no final",
                valid.substring(0, valid.length() / 2),
                valid.replace("\"completed\": true", "\"completed\": \"sim\""),
                valid.replace("\"formatVersion\": 1", "\"formatVersion\": 99"),
                valid.replace("\"formatVersion\": 1", "\"formatVersion\": 0"),
                valid.replace("\"formatVersion\": 1", "\"formatVersion\": \"1\""),
                valid.replace("\"tasks\"", "\"tarefas\""));
        int i = 0;
        for (String content : bad) {
            Path caseDir = dir.resolve("caso" + i++);
            JsonStateRepository r = new JsonStateRepository(caseDir, new AtomicFileWriter(), CLOCK);
            TempFiles.write(r.file(), content);

            LoadResult<AppSnapshot> loaded = r.load();

            assertEquals(LoadResult.Status.DISCARDED, loaded.status(), "caso " + (i - 1) + ": " + content);
            assertEquals(AppSnapshot.empty(), loaded.value());
            assertTrue(loaded.hasWarnings());
            assertFalse(Files.exists(r.file()));
            assertEquals(1, TempFiles.countPreserved(r.file()));
            assertEquals(content, TempFiles.read(TempFiles.firstPreserved(r.file())));
        }
    }

    @Test
    @DisplayName("arquivo que não é UTF-8 válido é tratado como corrompido")
    void invalidUtf8() throws IOException {
        Files.write(repo.file(), new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'});
        assertEquals(LoadResult.Status.DISCARDED, repo.load().status());
        assertEquals(1, TempFiles.countPreserved(repo.file()));
    }

    // ---- dados duplicados ou inconsistentes --------------------------------------

    private void assertRejected(Consumer<JsonObject> corruption) throws IOException {
        TempFiles.deleteRecursively(dir); // cada caso começa em uma pasta vazia
        Files.createDirectories(dir);
        repo = fresh();
        repo.save(Snapshots.sample());
        TempFiles.mutateJson(repo.file(), corruption);
        LoadResult<AppSnapshot> r = fresh().load();
        assertEquals(LoadResult.Status.DISCARDED, r.status());
        assertEquals(AppSnapshot.empty(), r.value());
        assertEquals(1, TempFiles.countPreserved(repo.file()));
    }

    @Test
    @DisplayName("rejeita UUIDs de tarefa duplicados")
    void duplicateTaskIds() throws IOException {
        assertRejected(root -> {
            JsonArray tasks = root.getAsJsonArray("tasks");
            String first = tasks.get(0).getAsJsonObject().get("id").getAsString();
            tasks.get(1).getAsJsonObject().addProperty("id", first);
        });
    }

    @Test
    @DisplayName("rejeita IDs repetidos no ledger (pontuação em dobro)")
    void duplicateLedgerIds() throws IOException {
        assertRejected(root -> {
            JsonArray ids = root.getAsJsonObject("achievements").getAsJsonArray("scoredTaskIds");
            ids.add(ids.get(0));
        });
    }

    @Test
    @DisplayName("rejeita mais de 16 pontuações")
    void tooManyScores() throws IOException {
        assertRejected(root -> {
            JsonArray ids = root.getAsJsonObject("achievements").getAsJsonArray("scoredTaskIds");
            for (int i = 0; i < 14; i++) {
                ids.add(UUID.randomUUID().toString());
            }
        });
    }

    @Test
    @DisplayName("rejeita mais tarefas que o limite de segurança")
    void tooManyTasks() throws IOException {
        assertRejected(root -> {
            JsonArray tasks = root.getAsJsonArray("tasks");
            int missing = TaskBoard.MAX_TASKS + 1 - tasks.size();
            for (int i = 0; i < missing; i++) {
                JsonObject t = tasks.get(0).getAsJsonObject().deepCopy();
                t.addProperty("id", UUID.randomUUID().toString());
                t.addProperty("completed", false);
                t.addProperty("scored", false);
                tasks.add(t);
            }
        });
    }

    @Test
    @DisplayName("rejeita tarefa concluída que não consta como pontuada")
    void completedButUnscored() throws IOException {
        assertRejected(root -> root.getAsJsonArray("tasks").get(0).getAsJsonObject().addProperty("scored", false));
    }

    @Test
    @DisplayName("rejeita ponto perdido: tarefa pontuada que sumiu do ledger")
    void lostPoint() throws IOException {
        assertRejected(root ->
                root.getAsJsonObject("achievements").getAsJsonArray("scoredTaskIds").remove(0));
    }

    @Test
    @DisplayName("rejeita campo ausente, UUID inválido, título em branco e histórico malformado")
    void malformedFields() throws IOException {
        assertRejected(root -> root.getAsJsonArray("tasks").get(0).getAsJsonObject().remove("title"));
        assertRejected(root -> root.getAsJsonArray("tasks").get(0).getAsJsonObject().addProperty("id", "123"));
        assertRejected(root -> root.getAsJsonArray("tasks").get(0).getAsJsonObject().addProperty("title", "  "));
        assertRejected(root -> root.getAsJsonArray("history").get(0).getAsJsonObject().addProperty("kind", "VOOU"));
        assertRejected(root -> root.getAsJsonArray("history").get(0).getAsJsonObject().addProperty("at", "ontem"));
        assertRejected(root -> root.remove("achievements"));
    }

    @Test
    @DisplayName("histórico acima do limite é aparado para os 200 mais recentes, sem rejeitar o estado")
    void historyIsTrimmed() throws IOException {
        repo.save(Snapshots.sample());
        TempFiles.mutateJson(repo.file(), root -> {
            JsonArray history = root.getAsJsonArray("history");
            JsonObject sample = history.get(0).getAsJsonObject();
            for (int i = 0; i < 250; i++) {
                history.add(sample.deepCopy());
            }
        });
        LoadResult<AppSnapshot> r = fresh().load();
        assertEquals(LoadResult.Status.LOADED, r.status());
        assertEquals(HistoryObserver.MAX_ENTRIES, r.value().history().size());
        assertEquals(3, r.value().domain().scoredTaskIds().size());
    }

    // ---- backup e recuperação ----------------------------------------------------

    @Test
    @DisplayName("com o principal corrompido, recupera do backup e preserva o arquivo ruim")
    void recoversFromBackup() throws IOException {
        AppSnapshot first = Snapshots.sample();
        repo.save(first);
        repo.save(Snapshots.sample()); // a segunda gravação guarda a primeira como backup
        TempFiles.write(repo.file(), "{ corrompido");

        LoadResult<AppSnapshot> r = fresh().load();
        assertEquals(LoadResult.Status.RECOVERED_FROM_BACKUP, r.status());
        assertEquals(first.domain().scoredTaskIds().size(), r.value().domain().scoredTaskIds().size());
        assertTrue(r.hasWarnings());
        assertEquals(1, TempFiles.countPreserved(repo.file()));
        assertEquals("{ corrompido", TempFiles.read(TempFiles.firstPreserved(repo.file())));
    }

    @Test
    @DisplayName("sem o arquivo principal mas com backup válido, usa o backup")
    void mainMissingBackupPresent() throws IOException {
        repo.save(Snapshots.sample());
        repo.save(Snapshots.sample());
        Files.delete(repo.file());
        LoadResult<AppSnapshot> r = fresh().load();
        assertEquals(LoadResult.Status.RECOVERED_FROM_BACKUP, r.status());
        assertEquals(3, r.value().domain().scoredTaskIds().size());
    }

    @Test
    @DisplayName("principal e backup corrompidos: começa do zero e preserva os dois")
    void bothCorrupt() throws IOException {
        repo.save(Snapshots.sample());
        repo.save(Snapshots.sample());
        TempFiles.write(repo.file(), "ruim 1");
        TempFiles.write(repo.backupFile(), "ruim 2");
        LoadResult<AppSnapshot> r = fresh().load();
        assertEquals(LoadResult.Status.DISCARDED, r.status());
        assertEquals(AppSnapshot.empty(), r.value());
        assertEquals(1, TempFiles.countPreserved(repo.file()));
        long preservedBackups;
        try (var s = Files.list(dir)) {
            preservedBackups = s.filter(p -> p.getFileName().toString().startsWith("state.json.bak.corrupt-")).count();
        }
        assertEquals(1, preservedBackups);
    }

    @Test
    @DisplayName("gravar depois de descartar um arquivo ruim não destrói a cópia preservada")
    void savingAfterDiscardKeepsPreservedCopy() throws IOException {
        TempFiles.write(repo.file(), "dados antigos ilegíveis");
        JsonStateRepository r = fresh();
        assertEquals(LoadResult.Status.DISCARDED, r.load().status());
        r.save(Snapshots.sample());

        assertEquals(LoadResult.Status.LOADED, fresh().load().status());
        assertEquals("dados antigos ilegíveis", TempFiles.read(TempFiles.firstPreserved(repo.file())));
    }

    @Test
    @DisplayName("duas corrupções no mesmo instante geram duas cópias preservadas distintas")
    void preservedNamesAreUnique() throws IOException {
        TempFiles.write(repo.file(), "ruim A");
        fresh().load();
        TempFiles.write(repo.file(), "ruim B");
        fresh().load();
        assertEquals(2, TempFiles.countPreserved(repo.file()));
    }

    @Test
    @DisplayName("arquivo ilegível (não é possível nem ler) bloqueia a gravação em vez de sobrescrevê-lo")
    void unreadableFileBlocksWrites() throws IOException {
        Files.createDirectories(repo.file()); // um diretório no lugar do arquivo: leitura falha
        JsonStateRepository r = fresh();
        LoadResult<AppSnapshot> loaded = r.load();
        assertEquals(LoadResult.Status.DISCARDED, loaded.status());
        assertTrue(loaded.hasWarnings());
        assertThrows(PersistenceException.class, () -> r.save(Snapshots.sample()));
        assertTrue(Files.isDirectory(repo.file()));
    }

    // ---- falhas de escrita -------------------------------------------------------

    /** Grava normalmente até ser mandado falhar. */
    private static final class SwitchableWriter implements AtomicWriter {
        private final AtomicFileWriter real = new AtomicFileWriter();
        volatile boolean failing;

        @Override
        public void write(Path target, byte[] content) throws IOException {
            if (failing) {
                throw new IOException("disco cheio (simulado)");
            }
            real.write(target, content);
        }
    }

    @Test
    @DisplayName("falha de escrita lança PersistenceException e o arquivo anterior fica idêntico")
    void writeFailureKeepsPreviousFile() throws IOException {
        SwitchableWriter writer = new SwitchableWriter();
        JsonStateRepository r = new JsonStateRepository(dir, writer, CLOCK);
        r.save(Snapshots.sample());
        String before = TempFiles.read(r.file());

        writer.failing = true;
        assertThrows(PersistenceException.class, () -> r.save(Snapshots.sample()));
        assertEquals(before, TempFiles.read(r.file()));
        assertEquals(LoadResult.Status.LOADED, fresh().load().status());

        writer.failing = false;
        assertDoesNotThrow(() -> r.save(Snapshots.sample()));
    }

    @Test
    @DisplayName("falha de escrita com o gravador real (temporário bloqueado) também preserva o original")
    void realWriterFailureKeepsPreviousFile() throws IOException {
        repo.save(Snapshots.sample());
        String before = TempFiles.read(repo.file());
        Path blocker = dir.resolve("state.json.tmp");
        Files.createDirectories(blocker);
        Files.writeString(blocker.resolve("x"), "x");

        assertThrows(PersistenceException.class, () -> repo.save(Snapshots.sample()));
        assertEquals(before, TempFiles.read(repo.file()));
    }

    // ---- versionamento -----------------------------------------------------------

    private static JsonFileStore.Decoder<String> titleDecoder() {
        return root -> JsonFields.string(root, "title");
    }

    private static Migration rename(int from, int to, String oldName, String newName) {
        return new Migration() {
            @Override public int from() { return from; }
            @Override public int to() { return to; }
            @Override public JsonObject apply(JsonObject root) {
                JsonObject copy = root.deepCopy();
                copy.add(newName, copy.remove(oldName));
                return copy;
            }
        };
    }

    @Test
    @DisplayName("formato antigo é migrado em cadeia antes de ser lido")
    void migratesOldVersions() throws IOException {
        Path file = dir.resolve("old.json");
        TempFiles.write(file, "{\"formatVersion\": 1, \"name\": \"antigo\"}");
        JsonFileStore store = new JsonFileStore(file, 3,
                List.of(rename(1, 2, "name", "label"), rename(2, 3, "label", "title")),
                new AtomicFileWriter(), CLOCK);
        JsonFileStore.Read<String> r = store.read(titleDecoder());
        assertEquals(LoadResult.Status.LOADED, r.status());
        assertEquals("antigo", r.value());
    }

    @Test
    @DisplayName("formato antigo sem migração disponível é rejeitado e preservado")
    void missingMigration() throws IOException {
        Path file = dir.resolve("old.json");
        TempFiles.write(file, "{\"formatVersion\": 1, \"name\": \"antigo\"}");
        JsonFileStore store = new JsonFileStore(file, 3, List.of(rename(2, 3, "a", "b")),
                new AtomicFileWriter(), CLOCK);
        assertEquals(LoadResult.Status.DISCARDED, store.read(titleDecoder()).status());
        assertEquals(1, TempFiles.countPreserved(file));
    }

    @Test
    @DisplayName("ao gravar, o formato atual é registrado no arquivo")
    void writesCurrentVersion() throws IOException {
        Path file = dir.resolve("v.json");
        JsonFileStore store = new JsonFileStore(file, 3, List.of(), new AtomicFileWriter(), CLOCK);
        JsonObject body = new JsonObject();
        body.addProperty("title", "x");
        store.write(body);
        assertEquals(3, JsonParser.parseString(TempFiles.read(file)).getAsJsonObject()
                .get("formatVersion").getAsInt());
    }

    @Test
    @DisplayName("o arquivo é gravado em UTF-8 e mantém acentos")
    void utf8() throws IOException {
        repo.save(Snapshots.sample());
        assertTrue(new String(Files.readAllBytes(repo.file()), StandardCharsets.UTF_8).contains("Estudar o Observer"));
        List<TaskView> tasks = fresh().load().value().domain().tasks();
        assertEquals("Estudar o Observer", tasks.get(0).title());
    }
}
