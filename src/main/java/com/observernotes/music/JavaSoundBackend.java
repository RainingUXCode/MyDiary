package com.observernotes.music;

import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

/**
 * Reprodução com {@code javax.sound.sampled}. Cada faixa tem a própria thread (daemon), então
 * a interface Swing nunca espera pelo áudio. Fechar a faixa libera a linha e o arquivo na hora.
 */
public final class JavaSoundBackend implements AudioBackend {

    private final AudioSink.Factory sinks;

    public JavaSoundBackend() {
        this(LineSink::open);
    }

    JavaSoundBackend(AudioSink.Factory sinks) {
        this.sinks = sinks;
    }

    @Override
    public Playback open(Path file, double volume, Runnable onFinished) throws AudioException {
        AudioInputStream pcm = PcmSource.open(file);
        AudioSink sink;
        try {
            sink = sinks.open(pcm.getFormat());
        } catch (AudioException | RuntimeException e) {
            try {
                pcm.close();
            } catch (IOException ignored) {
                // nada a fazer
            }
            if (e instanceof AudioException ae) {
                throw ae;
            }
            throw new AudioException("não foi possível abrir a saída de áudio", e);
        }
        return new StreamPlayback(pcm, sink, volume, onFinished);
    }

    private static final class StreamPlayback implements Playback {

        private final AudioInputStream pcm;
        private final AudioSink sink;
        private final Runnable onFinished;
        private final Object lock = new Object();
        private final Thread thread;
        private volatile double volume;
        private volatile boolean paused;
        private volatile boolean closed;
        private boolean started;

        StreamPlayback(AudioInputStream pcm, AudioSink sink, double volume, Runnable onFinished) {
            this.pcm = pcm;
            this.sink = sink;
            this.volume = volume;
            this.onFinished = onFinished;
            this.thread = new Thread(this::run, "observer-notes-audio");
            this.thread.setDaemon(true);
        }

        @Override
        public void start() {
            synchronized (lock) {
                if (started || closed) {
                    return;
                }
                started = true;
            }
            sink.start();
            thread.start();
        }

        @Override
        public void pause() {
            paused = true;
            sink.stop();
        }

        @Override
        public void resume() {
            paused = false;
            sink.start();
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        @Override
        public void setVolume(double volume) {
            this.volume = volume;
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                lock.notifyAll();
            }
            release();
        }

        private void release() {
            try {
                sink.stop();
                sink.flush();
            } finally {
                sink.close();
            }
            try {
                pcm.close();
            } catch (IOException ignored) {
                // nada a fazer
            }
        }

        private void run() {
            AudioFormat f = pcm.getFormat();
            byte[] buffer = new byte[Math.max(2, f.getFrameSize()) * 2048];
            try {
                while (!closed) {
                    synchronized (lock) {
                        while (paused && !closed) {
                            lock.wait();
                        }
                    }
                    if (closed) {
                        break;
                    }
                    int n = pcm.read(buffer, 0, buffer.length);
                    if (n < 0) {
                        break;
                    }
                    VolumeScaler.apply(buffer, n, volume);
                    int offset = 0;
                    while (offset < n && !closed) {
                        int w = sink.write(buffer, offset, n - offset);
                        if (w <= 0) {
                            break;
                        }
                        offset += w;
                    }
                }
                if (!closed) {
                    sink.drain();
                }
            } catch (IOException | RuntimeException e) {
                // arquivo corrompido no meio: trata como fim da faixa e segue para a próxima
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                if (!closed) {
                    close();
                    onFinished.run();
                }
            }
        }
    }
}
