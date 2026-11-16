package com.observernotes.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Armazém de um arquivo JSON versionado. Garantias:
 * <ul>
 *   <li>escrita atômica (temporário + troca) e backup da última versão boa;</li>
 *   <li>leitura estrita e validada, com migração de versões antigas;</li>
 *   <li>um arquivo inválido nunca é apagado nem sobrescrito: é movido para
 *       {@code nome.corrupt-<data>} antes de qualquer nova gravação;</li>
 *   <li>se não for possível preservar um arquivo problemático, as gravações são
 *       bloqueadas em vez de arriscar sobrescrevê-lo.</li>
 * </ul>
 */
final class JsonFileStore {

    static final String VERSION_FIELD = "formatVersion";

    private static final System.Logger LOG = System.getLogger("observernotes.persistence");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS").withZone(ZoneOffset.UTC);

    /** Converte o JSON (já na versão atual) no objeto desejado, ou lança se for inválido. */
    @FunctionalInterface
    interface Decoder<T> {
        T decode(JsonObject root);
    }

    record Read<T>(T value, LoadResult.Status status, List<String> messages) { }

    private record Attempt<T>(T value, String error, boolean unreadable) { }

    private final Path file;
    private final Path backup;
    private final int currentVersion;
    private final List<Migration> migrations;
    private final AtomicWriter writer;
    private final Clock clock;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private boolean knownGood;
    private boolean writesBlocked;

    JsonFileStore(Path file, int currentVersion, List<Migration> migrations, AtomicWriter writer, Clock clock) {
        this.file = Objects.requireNonNull(file, "file");
        this.backup = file.resolveSibling(file.getFileName() + ".bak");
        this.currentVersion = currentVersion;
        this.migrations = List.copyOf(migrations);
        this.writer = Objects.requireNonNull(writer, "writer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Path file() {
        return file;
    }

    Path backupFile() {
        return backup;
    }

    // ---- leitura ---------------------------------------------------------------

    <T> Read<T> read(Decoder<T> decoder) {
        List<String> msgs = new ArrayList<>();
        boolean mainExists = Files.exists(file);
        boolean backupExists = Files.exists(backup);
        if (!mainExists && !backupExists) {
            return new Read<>(null, LoadResult.Status.FRESH, msgs);
        }

        if (mainExists) {
            Attempt<T> main = attempt(file, decoder);
            if (main.value() != null) {
                knownGood = true;
                return new Read<>(main.value(), LoadResult.Status.LOADED, msgs);
            }
            msgs.add("O arquivo " + file.getFileName() + " não pôde ser usado: " + main.error());
            if (main.unreadable()) {
                block(msgs, file);
            } else {
                preserve(file, msgs);
            }
        } else {
            msgs.add("O arquivo " + file.getFileName() + " não foi encontrado, mas existe um backup.");
        }

        if (backupExists) {
            Attempt<T> bak = attempt(backup, decoder);
            if (bak.value() != null) {
                msgs.add("Dados recuperados do backup " + backup.getFileName() + ".");
                return new Read<>(bak.value(), LoadResult.Status.RECOVERED_FROM_BACKUP, msgs);
            }
            msgs.add("O backup " + backup.getFileName() + " também não pôde ser usado: " + bak.error());
            if (bak.unreadable()) {
                block(msgs, backup);
            } else {
                preserve(backup, msgs);
            }
        }
        return new Read<>(null, LoadResult.Status.DISCARDED, msgs);
    }

    private <T> Attempt<T> attempt(Path path, Decoder<T> decoder) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            return new Attempt<>(null, "não foi possível ler (" + e.getMessage() + ")", true);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = migrate(parseObject(text));
            T value = decoder.decode(root);
            return new Attempt<>(Objects.requireNonNull(value), null, false);
        } catch (CharacterCodingException e) {
            return new Attempt<>(null, "o arquivo não é um texto UTF-8 válido", false);
        } catch (InvalidDataException e) {
            return new Attempt<>(null, e.getMessage(), false);
        } catch (RuntimeException e) {
            return new Attempt<>(null, "dados inconsistentes (" + e.getMessage() + ")", false);
        }
    }

    private JsonObject parseObject(String text) {
        try {
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(false);
            JsonElement element = gson.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new InvalidDataException("há conteúdo depois do fim do JSON");
            }
            if (element == null || !element.isJsonObject()) {
                throw new InvalidDataException("a raiz do JSON deveria ser um objeto");
            }
            return element.getAsJsonObject();
        } catch (IOException | JsonParseException e) {
            throw new InvalidDataException("JSON inválido ou incompleto", e);
        }
    }

    private JsonObject migrate(JsonObject root) {
        int version = JsonFields.integer(root, VERSION_FIELD);
        if (version < 1) {
            throw new InvalidDataException("versão do formato inválida: " + version);
        }
        if (version > currentVersion) {
            throw new InvalidDataException("o arquivo foi criado por uma versão mais nova do aplicativo (formato "
                    + version + "; este suporta até " + currentVersion + ")");
        }
        JsonObject current = root;
        while (version < currentVersion) {
            final int from = version;
            Migration m = migrations.stream().filter(x -> x.from() == from).findFirst()
                    .orElseThrow(() -> new InvalidDataException("não há migração a partir do formato " + from));
            current = m.apply(current);
            version = m.to();
            current.addProperty(VERSION_FIELD, version);
        }
        return current;
    }

    private void preserve(Path path, List<String> msgs) {
        Path dest = uniqueSibling(path, ".corrupt-" + STAMP.format(clock.instant()));
        try {
            Files.move(path, dest);
            msgs.add("O arquivo foi preservado como " + dest.getFileName() + " (nada foi apagado).");
            return;
        } catch (IOException moveFailure) {
            LOG.log(Level.WARNING, "Não foi possível mover " + path, moveFailure);
        }
        try {
            Files.copy(path, dest);
            msgs.add("O arquivo foi copiado para " + dest.getFileName() + " (nada foi apagado).");
        } catch (IOException copyFailure) {
            block(msgs, path);
        }
    }

    private void block(List<String> msgs, Path path) {
        writesBlocked = true;
        msgs.add("Não foi possível preservar " + path.getFileName()
                + ": as gravações foram bloqueadas para não sobrescrevê-lo.");
    }

    private static Path uniqueSibling(Path path, String suffix) {
        Path candidate = path.resolveSibling(path.getFileName() + suffix);
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = path.resolveSibling(path.getFileName() + suffix + "-" + n++);
        }
        return candidate;
    }

    // ---- escrita ---------------------------------------------------------------

    /** Grava {@code body} com o campo de versão. Em caso de falha o arquivo anterior fica intacto. */
    void write(JsonObject body) {
        if (writesBlocked) {
            throw new PersistenceException("Gravação bloqueada: há um arquivo anterior que não foi possível "
                    + "ler nem preservar, e ele não será sobrescrito (" + file.getFileName() + ")");
        }
        JsonObject root = new JsonObject();
        root.addProperty(VERSION_FIELD, currentVersion);
        body.entrySet().stream().filter(e -> !e.getKey().equals(VERSION_FIELD))
                .forEach(e -> root.add(e.getKey(), e.getValue()));
        byte[] data = gson.toJson(root).getBytes(StandardCharsets.UTF_8);

        // Guarda a última versão boa antes de substituí-la. Só copia o que sabemos que é válido.
        if (knownGood && Files.isRegularFile(file)) {
            try {
                writer.write(backup, Files.readAllBytes(file));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Não foi possível atualizar o backup de " + file.getFileName(), e);
            }
        }
        try {
            writer.write(file, data);
        } catch (IOException | RuntimeException e) {
            throw new PersistenceException("Não foi possível gravar " + file.getFileName()
                    + "; o arquivo anterior foi mantido", e);
        }
        knownGood = true;
    }
}
