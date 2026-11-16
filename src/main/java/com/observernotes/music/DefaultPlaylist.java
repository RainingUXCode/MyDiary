package com.observernotes.music;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Playlist padrão: as músicas que acompanham o aplicativo (em {@code assets/music/}, listadas em
 * {@code playlist.txt} como {@code arquivo|título de exibição}). Na primeira execução elas são extraídas para
 * {@code <pasta de dados>/musicas-padrao/} (o player toca arquivos de verdade, nunca de dentro do JAR) e entram na
 * playlist <b>depois</b> das músicas que o usuário já tenha, sem duplicar e sem começar a tocar sozinhas.
 * <p>
 * Um arquivo marcador impede que as músicas voltem se o usuário as remover da playlist. Os títulos são apenas nomes
 * de exibição: os MP3 mantêm os metadados originais. Músicas adicionadas pelo usuário não são tocadas por aqui.
 */
public final class DefaultPlaylist {

    static final String RESOURCE_DIR = "/assets/music/";
    static final String INDEX = "playlist.txt";
    static final String MARKER = "musicas-padrao.ok";
    static final String FOLDER = "musicas-padrao";

    /** Fonte dos bytes das músicas (o JAR, em produção). */
    interface Resources {
        InputStream open(String name);
    }

    private DefaultPlaylist() { }

    public static void seedIfNeeded(Path dataDir, PlaylistStore store) {
        seedIfNeeded(dataDir, store, name -> DefaultPlaylist.class.getResourceAsStream(RESOURCE_DIR + name));
    }

    /** @return quantas músicas padrão foram adicionadas (0 se já havia sido feito ou se não há o que adicionar) */
    static int seedIfNeeded(Path dataDir, PlaylistStore store, Resources res) {
        Path marker = dataDir.resolve(MARKER);
        if (Files.exists(marker)) {
            return 0;
        }
        try {
            List<String[]> entries = readIndex(res);
            if (entries.isEmpty()) {
                return 0;
            }
            Path folder = dataDir.resolve(FOLDER);
            Files.createDirectories(folder);
            List<Track> defaults = new ArrayList<>();
            for (String[] e : entries) {
                Path target = folder.resolve(e[0]).toAbsolutePath().normalize();
                if (!Files.isRegularFile(target)) {
                    try (InputStream in = res.open(e[0])) {
                        if (in == null) {
                            continue;                      // arquivo ausente do pacote: ignora só esta faixa
                        }
                        Files.copy(in, target);
                    }
                }
                defaults.add(new Track(target.toString(), e[1]));
            }
            StoredPlayer cur = store.load().data();
            List<Track> all = new ArrayList<>(cur.tracks());
            Set<String> seen = new HashSet<>();
            all.forEach(t -> seen.add(t.key()));
            int added = 0;
            for (Track t : defaults) {
                if (seen.add(t.key())) {
                    all.add(t);
                    added++;
                }
            }
            if (added > 0) {
                int index = cur.tracks().isEmpty() ? 0 : cur.currentIndex();
                store.save(new StoredPlayer(all, index, cur.volume(), cur.repeat()));
            }
            Files.writeString(marker, "ok\n", StandardCharsets.UTF_8);
            return added;
        } catch (IOException | RuntimeException e) {
            System.getLogger("observernotes.music").log(System.Logger.Level.WARNING,
                    "Não foi possível preparar a playlist padrão; tentará de novo na próxima abertura", e);
            return 0;
        }
    }

    private static List<String[]> readIndex(Resources res) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = res.open(INDEX)) {
            if (in == null) {
                return out;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                line = line.strip();
                int bar = line.indexOf('|');
                if (line.isEmpty() || line.startsWith("#") || bar <= 0) {
                    continue;
                }
                String file = line.substring(0, bar).strip();
                String title = line.substring(bar + 1).strip();
                if (!file.isEmpty() && !title.isEmpty() && !file.contains("/") && !file.contains("\\")
                        && file.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                    out.add(new String[] {file, title});
                }
            }
        }
        return out;
    }
}
