package com.observernotes.music;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Fachada usada pela interface: junta o {@link PlayerEngine} (áudio e playlist) e o
 * {@link PlaylistStore} (disco). Independente do domínio das tarefas. A playlist é restaurada
 * ao abrir, mas nada começa a tocar sozinho.
 */
public final class MusicPlayer implements AutoCloseable {

    /** Resultado de adicionar arquivos. */
    public record AddResult(int added, int duplicates, int unsupported) { }

    private final PlayerEngine engine;
    private final PlaylistStore store;
    private final Runnable onChange;
    private final String startupWarning;
    private StoredPlayer lastSaved;   // sob saveLock
    private final Object saveLock = new Object();
    private volatile boolean ready;
    private volatile String saveWarning = "";

    public MusicPlayer(AudioBackend backend, PlaylistStore store, Runnable onChange) {
        this.store = store;
        this.onChange = onChange;
        this.engine = new PlayerEngine(backend, this::engineChanged);
        PlaylistStore.Loaded loaded = store.load();
        this.startupWarning = loaded.warning();
        engine.restore(loaded.data());
        this.lastSaved = engine.persisted();
        this.ready = true;
    }

    public String startupWarning() {
        return startupWarning;
    }

    public AddResult addFiles(List<Path> files) {
        List<Path> ok = files.stream().filter(SupportedFormats::isSupported).toList();
        int added = engine.add(ok);
        return new AddResult(added, ok.size() - added, files.size() - ok.size());
    }

    public void toggle() {
        engine.toggle();
    }

    public void play() {
        engine.play();
    }

    public void pause() {
        engine.pause();
    }

    public void stop() {
        engine.stop();
    }

    public void next() {
        engine.next();
    }

    public void previous() {
        engine.previous();
    }

    public void playIndex(int index) {
        engine.playIndex(index);
    }

    public void remove(int index) {
        engine.remove(index);
    }

    public void move(int index, int delta) {
        engine.move(index, delta);
    }

    public void setVolume(double v) {
        engine.setVolume(v);
    }

    public void adjustVolume(double delta) {
        engine.setVolume(Math.round((engine.snapshot().volume() + delta) * 100) / 100.0);
    }

    public void toggleRepeat() {
        engine.setRepeat(!engine.snapshot().repeat());
    }

    public PlayerSnapshot snapshot() {
        PlayerSnapshot s = engine.snapshot();
        if (s.message().isEmpty() && !saveWarning.isEmpty()) {
            return new PlayerSnapshot(s.state(), s.currentIndex(), s.currentTitle(), s.tracks(), s.volume(),
                    s.repeat(), saveWarning);
        }
        return s;
    }

    /** Fecha o áudio e grava uma última vez. Seguro para chamar mais de uma vez. */
    @Override
    public void close() {
        engine.close();
        persistIfChanged();
    }

    private void engineChanged() {
        if (!ready) {
            return;
        }
        persistIfChanged();
        onChange.run();
    }

    /** Nunca segura o lock do engine dentro do lock de gravação (evita impasse com a thread de áudio). */
    private void persistIfChanged() {
        StoredPlayer now = engine.persisted();
        synchronized (saveLock) {
            if (now.equals(lastSaved)) {
                return;
            }
            try {
                store.save(now);
                lastSaved = now;
                saveWarning = "";
            } catch (IOException | RuntimeException e) {
                saveWarning = "Não foi possível salvar a playlist.";
            }
        }
    }
}
