package com.observernotes.music;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Máquina de estados do player: decide o que toca, pausa, avança e para, usando um
 * {@link AudioBackend}. Todos os métodos são sincronizados (a interface e a thread de áudio
 * chamam daqui). O {@code listener} é avisado a cada mudança e deve ser rápido e não bloquear
 * (a interface apenas agenda um repaint).
 */
public final class PlayerEngine implements AutoCloseable {

    private final AudioBackend backend;
    private final Runnable listener;
    private final Playlist playlist = new Playlist();
    private final Set<String> failed = new HashSet<>();

    private AudioBackend.Playback playback;
    private long generation;
    private PlaybackState state = PlaybackState.STOPPED;
    private double volume = StoredPlayer.DEFAULT_VOLUME;
    private boolean repeat;
    private String message = "";
    private boolean closed;

    public PlayerEngine(AudioBackend backend, Runnable listener) {
        this.backend = backend;
        this.listener = listener;
    }

    // ---------------------------------------------------------------- reprodução

    public synchronized void play() {
        if (closed) {
            return;
        }
        if (playlist.isEmpty()) {
            message = "Playlist vazia: use NOVA! para adicionar músicas";
            changed();
            return;
        }
        if (state == PlaybackState.PAUSED && playback != null) {
            playback.resume();
            state = PlaybackState.PLAYING;
            message = "";
            changed();
        } else if (state != PlaybackState.PLAYING) {
            startCurrent();
        }
    }

    public synchronized void pause() {
        if (state == PlaybackState.PLAYING && playback != null) {
            playback.pause();
            state = PlaybackState.PAUSED;
            changed();
        }
    }

    public synchronized void toggle() {
        if (state == PlaybackState.PLAYING) {
            pause();
        } else {
            play();
        }
    }

    public synchronized void stop() {
        closePlayback();
        if (state != PlaybackState.STOPPED) {
            state = PlaybackState.STOPPED;
            changed();
        }
    }

    public synchronized void next() {
        skip(true);
    }

    public synchronized void previous() {
        skip(false);
    }

    private void skip(boolean forward) {
        if (closed) {
            return;
        }
        if (playlist.isEmpty()) {
            message = "Playlist vazia: use NOVA! para adicionar músicas";
            changed();
            return;
        }
        playlist.advance(forward);
        startCurrent();
    }

    /** Toca a faixa escolhida. Uma faixa que falhou antes ganha nova chance (o arquivo pode ter voltado). */
    public synchronized void playIndex(int index) {
        if (closed || !playlist.select(index)) {
            return;
        }
        failed.remove(playlist.currentTrack().key());
        startCurrent();
    }

    public synchronized void setVolume(double value) {
        volume = Math.max(0.0, Math.min(1.0, value));
        if (playback != null) {
            playback.setVolume(volume);
        }
        changed();
    }

    public synchronized void setRepeat(boolean value) {
        repeat = value;
        changed();
    }

    // ---------------------------------------------------------------- playlist

    /** @return quantas faixas realmente entraram (repetidas são ignoradas) */
    public synchronized int add(Collection<java.nio.file.Path> files) {
        int added = playlist.add(files);
        if (added > 0) {
            changed();
        }
        return added;
    }

    public synchronized void remove(int index) {
        boolean wasPlaying = state != PlaybackState.STOPPED;
        if (index < 0 || index >= playlist.size()) {
            return;
        }
        boolean removedCurrent = playlist.remove(index);
        if (removedCurrent) {
            closePlayback();
            state = PlaybackState.STOPPED;
            if (wasPlaying && !playlist.isEmpty()) {
                startCurrent();
                return;
            }
        }
        changed();
    }

    public synchronized void move(int index, int delta) {
        if (playlist.move(index, delta)) {
            changed();
        }
    }

    /** Restaura a playlist salva sem tocar nada. */
    public synchronized void restore(StoredPlayer data) {
        closePlayback();
        state = PlaybackState.STOPPED;
        playlist.restore(data.tracks(), data.currentIndex());
        volume = data.volume();
        repeat = data.repeat();
        failed.clear();
        message = "";
        changed();
    }

    // ---------------------------------------------------------------- consulta

    public synchronized StoredPlayer persisted() {
        return new StoredPlayer(playlist.tracks(), playlist.currentIndex(), volume, repeat);
    }

    public synchronized PlayerSnapshot snapshot() {
        List<PlayerSnapshot.TrackInfo> infos = new ArrayList<>();
        int cur = playlist.currentIndex();
        List<Track> tracks = playlist.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            boolean ok = !failed.contains(t.key()) && Files.isReadable(t.file());
            infos.add(new PlayerSnapshot.TrackInfo(t.title(), t.path(), ok, i == cur));
        }
        Track current = playlist.currentTrack();
        return new PlayerSnapshot(state, cur, current == null ? "" : current.title(), infos, volume, repeat, message);
    }

    @Override
    public synchronized void close() {
        closed = true;
        closePlayback();
        state = PlaybackState.STOPPED;
    }

    // ---------------------------------------------------------------- interno

    /** Começa a tocar a faixa atual; se não abrir, marca como indisponível e tenta as seguintes (no máximo uma volta). */
    private void startCurrent() {
        closePlayback();
        int attempts = playlist.size();
        String lastReason = "";
        for (int i = 0; i < attempts; i++) {
            Track t = playlist.currentTrack();
            long gen = ++generation;
            AudioBackend.Playback p = null;
            try {
                p = backend.open(t.file(), volume, () -> finished(gen));
                p.start();
                playback = p;
                failed.remove(t.key());
                state = PlaybackState.PLAYING;
                message = "";
                changed();
                return;
            } catch (AudioException | RuntimeException e) {
                if (p != null) {
                    p.close();
                }
                failed.add(t.key());
                lastReason = e.getMessage() == null ? "erro desconhecido" : e.getMessage();
                message = "Não foi possível tocar \"" + t.title() + "\": " + lastReason;
                if (attempts > 1) {
                    playlist.advance(true);
                }
            }
        }
        generation++;
        state = PlaybackState.STOPPED;
        if (attempts > 1) {
            message = "Nenhuma música pôde ser tocada (" + lastReason + ")";
        }
        changed();
    }

    /** Chamado pela thread de áudio quando a faixa acaba sozinha. */
    private synchronized void finished(long gen) {
        if (closed || gen != generation) {
            return;
        }
        closePlayback();
        if (playlist.hasNext() || (repeat && !playlist.isEmpty())) {
            playlist.advance(true);
            startCurrent();
        } else {
            playlist.select(0);
            state = PlaybackState.STOPPED;
            message = "";
            changed();
        }
    }

    private void closePlayback() {
        generation++;
        AudioBackend.Playback p = playback;
        playback = null;
        if (p != null) {
            try {
                p.close();
            } catch (RuntimeException ignored) {
                // liberar recursos nunca deve derrubar o app
            }
        }
    }

    private void changed() {
        try {
            listener.run();
        } catch (RuntimeException ignored) {
            // um ouvinte com defeito não pode quebrar o player
        }
    }
}
