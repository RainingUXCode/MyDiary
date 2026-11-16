package com.observernotes.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.observernotes.persistence.AtomicFileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Grava {@code music.json} na pasta de dados do usuário, em arquivo separado do {@code state.json}
 * das tarefas (o player nunca toca nos dados das tarefas). A gravação é atômica.
 */
public final class JsonPlaylistStore implements PlaylistStore {

    static final int FORMAT = 1;
    public static final String FILE_NAME = "music.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Forma do JSON. Campos opcionais para tolerar arquivos incompletos. */
    private static final class Dto {
        Integer formatVersion;
        Double volume;
        Boolean repeat;
        Integer currentIndex;
        List<TrackDto> tracks;
    }

    private static final class TrackDto {
        String path;
        String title;
    }

    private final Path file;
    private final AtomicFileWriter writer = new AtomicFileWriter();

    public JsonPlaylistStore(Path dataDir) {
        this.file = dataDir.resolve(FILE_NAME);
    }

    @Override
    public Loaded load() {
        if (!Files.exists(file)) {
            return new Loaded(StoredPlayer.empty(), "");
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Dto dto = GSON.fromJson(json, Dto.class);
            if (dto == null || dto.formatVersion == null || dto.formatVersion != FORMAT) {
                return invalid("formato desconhecido");
            }
            List<Track> tracks = new ArrayList<>();
            if (dto.tracks != null) {
                for (TrackDto t : dto.tracks) {
                    if (t != null && t.path != null && !t.path.isBlank()) {
                        try {
                            Track base = Track.of(Path.of(t.path));
                            // o título gravado (ex.: nome de exibição da playlist padrão) é preservado
                            tracks.add(t.title == null || t.title.isBlank() ? base : new Track(base.path(), t.title));
                        } catch (RuntimeException ignored) {
                            // caminho inválido: a entrada é descartada
                        }
                    }
                }
            }
            double volume = dto.volume == null || dto.volume.isNaN() ? StoredPlayer.DEFAULT_VOLUME
                    : Math.max(0.0, Math.min(1.0, dto.volume));
            int index = dto.currentIndex == null ? 0 : dto.currentIndex;
            return new Loaded(new StoredPlayer(tracks, index, volume, Boolean.TRUE.equals(dto.repeat)), "");
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return invalid("arquivo ilegível");
        }
    }

    private Loaded invalid(String reason) {
        try {
            Files.move(file, file.resolveSibling(FILE_NAME + ".invalido"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // se não der para guardar a cópia, o arquivo será sobrescrito na próxima gravação
        }
        return new Loaded(StoredPlayer.empty(),
                "A playlist salva não pôde ser lida (" + reason + "). Começando com a playlist vazia.");
    }

    @Override
    public void save(StoredPlayer data) throws IOException {
        Dto dto = new Dto();
        dto.formatVersion = FORMAT;
        dto.volume = data.volume();
        dto.repeat = data.repeat();
        dto.currentIndex = data.currentIndex();
        dto.tracks = new ArrayList<>();
        for (Track t : data.tracks()) {
            TrackDto td = new TrackDto();
            td.path = t.path();
            td.title = t.title();
            dto.tracks.add(td);
        }
        Files.createDirectories(file.getParent());
        writer.write(file, GSON.toJson(dto).getBytes(StandardCharsets.UTF_8));
    }
}
