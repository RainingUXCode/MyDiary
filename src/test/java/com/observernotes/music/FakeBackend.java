package com.observernotes.music;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dublê determinístico do áudio: não toca nada, mas registra tudo e deixa o teste "terminar" a faixa. */
public final class FakeBackend implements AudioBackend {

    public final List<FakePlayback> opened = new ArrayList<>();
    public final Set<String> failing = new HashSet<>();

    @Override
    public synchronized Playback open(Path file, double volume, Runnable onFinished) throws AudioException {
        if (failing.contains(file.getFileName().toString())) {
            throw new AudioException("arquivo não encontrado");
        }
        FakePlayback p = new FakePlayback(file, volume, onFinished);
        opened.add(p);
        return p;
    }

    public synchronized FakePlayback last() {
        return opened.get(opened.size() - 1);
    }

    public synchronized long openCount() {
        return opened.size();
    }

    public synchronized long openNow() {
        return opened.stream().filter(p -> !p.closed).count();
    }

    public static final class FakePlayback implements Playback {
        public final Path file;
        public final Runnable onFinished;
        public volatile double volume;
        public volatile boolean started;
        public volatile boolean paused;
        public volatile boolean closed;

        FakePlayback(Path file, double volume, Runnable onFinished) {
            this.file = file;
            this.volume = volume;
            this.onFinished = onFinished;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void pause() {
            paused = true;
        }

        @Override
        public void resume() {
            paused = false;
        }

        @Override
        public void setVolume(double v) {
            volume = v;
        }

        @Override
        public void close() {
            closed = true;
        }

        /** Simula o fim natural da faixa (como a thread de áudio faria). */
        public void finish() {
            if (!closed) {
                closed = true;
                onFinished.run();
            }
        }
    }
}
