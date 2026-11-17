package com.observernotes.sfx;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * Pré-decodifica os cinco MP3 uma única vez em uma thread própria e mantém Clips prontos.
 * Há no máximo um efeito tocando e um pedido pendente, evitando filas e sobreposição desagradável.
 */
public final class ClasspathSfxAudio implements SfxAudio {

    private static final System.Logger LOG = System.getLogger("observernotes.sfx");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "observer-notes-sfx");
        t.setDaemon(true);
        return t;
    });
    private final Map<SfxEffect, Clip> clips = new EnumMap<>(SfxEffect.class);
    private final AtomicReference<Request> pending = new AtomicReference<>();
    private final AtomicBoolean dispatching = new AtomicBoolean();
    private volatile boolean closed;
    private Clip current;

    private record Request(SfxEffect effect, double volume) { }

    public ClasspathSfxAudio() {
        worker.execute(this::preload);
    }

    private void preload() {
        for (SfxEffect effect : SfxEffect.values()) {
            if (closed) {
                return;
            }
            try {
                clips.put(effect, openClip(effect));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Não foi possível carregar " + effect.resource(), e);
            }
        }
    }

    private static Clip openClip(SfxEffect effect) throws Exception {
        InputStream resource = ClasspathSfxAudio.class.getResourceAsStream(effect.resource());
        if (resource == null) {
            throw new IllegalStateException("recurso ausente: " + effect.resource());
        }
        try (InputStream buffered = new BufferedInputStream(resource);
             AudioInputStream encoded = AudioSystem.getAudioInputStream(buffered)) {
            AudioFormat source = encoded.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getSampleRate(), 16,
                    source.getChannels(), source.getChannels() * 2, source.getSampleRate(), false);
            byte[] bytes;
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, encoded);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                decoded.transferTo(out);
                bytes = out.toByteArray();
            }
            Clip clip = AudioSystem.getClip();
            try (AudioInputStream memory = new AudioInputStream(new ByteArrayInputStream(bytes), pcm,
                    bytes.length / pcm.getFrameSize())) {
                clip.open(memory);
            }
            return clip;
        }
    }

    @Override
    public void play(SfxEffect effect, double volume) {
        if (closed) {
            return;
        }
        pending.set(new Request(effect, Math.max(0, Math.min(1, volume))));
        if (dispatching.compareAndSet(false, true)) {
            worker.execute(this::drainLatest);
        }
    }

    private void drainLatest() {
        try {
            Request request;
            while (!closed && (request = pending.getAndSet(null)) != null) {
                Clip clip = clips.get(request.effect());
                if (clip == null) {
                    continue; // ainda carregando ou arquivo indisponível: a ação da aplicação continua normalmente
                }
                if (current != null && current.isRunning()) {
                    current.stop();
                }
                current = clip;
                clip.stop();
                clip.setFramePosition(0);
                applyVolume(clip, request.volume());
                clip.start();
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao reproduzir efeito sonoro", e);
        } finally {
            dispatching.set(false);
            if (!closed && pending.get() != null && dispatching.compareAndSet(false, true)) {
                worker.execute(this::drainLatest);
            }
        }
    }

    private static void applyVolume(Clip clip, double volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = volume <= 0 ? gain.getMinimum() : (float) (20.0 * Math.log10(volume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending.set(null);
        worker.execute(() -> {
            for (Clip clip : clips.values()) {
                try {
                    clip.stop();
                    clip.close();
                } catch (RuntimeException ignored) {
                    // melhor esforço ao encerrar
                }
            }
            clips.clear();
        });
        worker.shutdown();
    }
}
