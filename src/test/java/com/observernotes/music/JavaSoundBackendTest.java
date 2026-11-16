package com.observernotes.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.support.TempFiles;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Decodificação e reprodução reais de WAV gerados na hora, com uma saída de áudio falsa no lugar da
 * placa de som (o ambiente de desenvolvimento não tem dispositivo de áudio: isto NÃO prova o som).
 */
class JavaSoundBackendTest {

    /** Saída falsa: guarda o que "tocaria" e permite bloquear a escrita para simular uma placa parada. */
    private static final class RecordingSink implements AudioSink {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        volatile boolean started;
        volatile boolean stopped;
        volatile boolean closed;
        volatile boolean drained;
        AudioFormat format;

        @Override
        public void start() {
            started = true;
            stopped = false;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public synchronized int write(byte[] data, int offset, int length) {
            bytes.write(data, offset, length);
            return length;
        }

        @Override
        public void drain() {
            drained = true;
        }

        @Override
        public void flush() { }

        @Override
        public void close() {
            closed = true;
        }

        synchronized int size() {
            return bytes.size();
        }
    }

    private Path dir;
    private RecordingSink sink;
    private JavaSoundBackend backend;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        sink = new RecordingSink();
        backend = new JavaSoundBackend(f -> {
            sink.format = f;
            return sink;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        TempFiles.deleteRecursively(dir);
    }

    private static boolean await(CountDownLatch l) throws InterruptedException {
        return l.await(5, TimeUnit.SECONDS);
    }

    @Test
    void aWavIsDecodedToPcm16AndFullyDeliveredToTheOutput() throws Exception {
        Path wav = WavFiles.tone(dir, "tom.wav", 0.5);                   // 4000 quadros de 16 bits
        CountDownLatch done = new CountDownLatch(1);
        var p = backend.open(wav, 1.0, done::countDown);
        p.start();
        assertTrue(await(done));
        assertEquals(8000, sink.size());
        assertEquals(16, sink.format.getSampleSizeInBits());
        assertFalse(sink.format.isBigEndian());
        assertTrue(sink.drained);
        assertTrue(sink.closed);                                         // terminou: recursos liberados
    }

    @Test
    void eightBitWavIsConvertedToSixteenBit() throws Exception {
        Path wav = WavFiles.tone(dir, "tom8.wav", 0.25, 440, 8);
        CountDownLatch done = new CountDownLatch(1);
        backend.open(wav, 1.0, done::countDown).start();
        assertTrue(await(done));
        assertEquals(16, sink.format.getSampleSizeInBits());
        assertEquals(4000, sink.size());                                 // 2000 quadros x 2 bytes
    }

    @Test
    void volumeScalesWhatReachesTheOutput() throws Exception {
        Path wav = WavFiles.tone(dir, "tom.wav", 0.25);
        CountDownLatch loud = new CountDownLatch(1);
        backend.open(wav, 1.0, loud::countDown).start();
        assertTrue(await(loud));
        byte[] full = sink.bytes.toByteArray();
        sink = new RecordingSink();
        CountDownLatch soft = new CountDownLatch(1);
        backend.open(wav, 0.5, soft::countDown).start();
        assertTrue(await(soft));
        byte[] half = sink.bytes.toByteArray();
        assertEquals(full.length, half.length);
        int maxFull = 0;
        int maxHalf = 0;
        for (int i = 0; i + 1 < full.length; i += 2) {
            maxFull = Math.max(maxFull, Math.abs((short) ((full[i] & 0xFF) | (full[i + 1] << 8))));
            maxHalf = Math.max(maxHalf, Math.abs((short) ((half[i] & 0xFF) | (half[i + 1] << 8))));
        }
        assertTrue(maxFull > 10000);
        assertTrue(Math.abs(maxHalf - maxFull / 4.0) <= 2.0);
    }

    @Test
    void pausedPlaybackWritesNothingUntilResumed() throws Exception {
        Path wav = WavFiles.tone(dir, "longo.wav", 2.0);                 // 32 KB: cabe em vários blocos
        CountDownLatch done = new CountDownLatch(1);
        var p = backend.open(wav, 1.0, done::countDown);
        p.pause();                                                       // pausa antes de começar
        p.start();
        Thread.sleep(150);
        assertEquals(0, sink.size());
        assertFalse(sink.drained);
        p.resume();
        assertTrue(await(done));
        assertEquals(32000, sink.size());
    }

    @Test
    void closingStopsThePlaybackReleasesResourcesAndNeverReportsFinished() throws Exception {
        Path wav = WavFiles.tone(dir, "longo.wav", 2.0);
        AtomicInteger finished = new AtomicInteger();
        var p = backend.open(wav, 1.0, finished::incrementAndGet);
        p.pause();
        p.start();
        Thread.sleep(50);
        p.close();
        assertTrue(sink.closed);
        Thread.sleep(150);
        assertEquals(0, finished.get());
        p.close();                                                       // idempotente
        p.start();                                                       // depois de fechada: ignorado
        assertEquals(0, sink.size());
    }

    @Test
    void closingBeforeStartingStillReleasesTheOutput() throws Exception {
        var p = backend.open(WavFiles.tone(dir, "a.wav", 0.1), 1.0, () -> { });
        p.close();
        assertTrue(sink.closed);
    }

    @Test
    void missingFileGivesAClearMessageAndOpensNoOutput() {
        var e = assertThrows(AudioException.class,
                () -> backend.open(dir.resolve("sumiu.wav"), 1.0, () -> { }));
        assertTrue(e.getMessage().contains("não encontrado"));
        assertEquals(null, sink.format);
    }

    @Test
    void invalidAudioFilesAreRejectedWithoutCrashing() throws Exception {
        Path text = WavFiles.garbage(dir, "falso.wav");
        var e1 = assertThrows(AudioException.class, () -> backend.open(text, 1.0, () -> { }));
        assertNotNull(e1.getMessage());
        Path mp3 = WavFiles.garbage(dir, "falso.mp3");
        var e2 = assertThrows(AudioException.class, () -> backend.open(mp3, 1.0, () -> { }));
        assertTrue(e2.getMessage().contains("MP3"));
        Path empty = dir.resolve("vazio.wav");
        Files.createFile(empty);
        assertThrows(AudioException.class, () -> backend.open(empty, 1.0, () -> { }));
        assertThrows(AudioException.class, () -> backend.open(dir, 1.0, () -> { }));   // pasta, não arquivo
        assertEquals(null, sink.format);
    }

    @Test
    void noAudioDeviceBecomesAFriendlyErrorAndTheFileIsReleased() throws Exception {
        JavaSoundBackend noDevice = new JavaSoundBackend(f -> {
            throw new AudioException("nenhum dispositivo de áudio disponível");
        });
        Path wav = WavFiles.tone(dir, "a.wav", 0.1);
        var e = assertThrows(AudioException.class, () -> noDevice.open(wav, 1.0, () -> { }));
        assertTrue(e.getMessage().contains("dispositivo"));
        Files.delete(wav);                                               // o arquivo não ficou preso
        assertFalse(Files.exists(wav));
    }

    @Test
    void realEngineWithRealDecodingAdvancesThroughTwoWavFiles() throws Exception {
        Path a = WavFiles.tone(dir, "a.wav", 0.1);
        Path b = WavFiles.tone(dir, "b.wav", 0.1);
        CountDownLatch end = new CountDownLatch(1);
        PlayerEngine engine = new PlayerEngine(backend, () -> {
            if (engine0(end)) {
                end.countDown();
            }
        });
        engineRef = engine;
        engine.add(java.util.List.of(a, b));
        engine.play();
        assertTrue(await(end));
        assertEquals(PlaybackState.STOPPED, engine.snapshot().state());
        assertEquals(3200, sink.size());                                 // duas faixas de 1600 bytes
        engine.close();
    }

    private PlayerEngine engineRef;

    private boolean engine0(CountDownLatch l) {
        PlayerEngine e = engineRef;
        return e != null && e.snapshot().state() == PlaybackState.STOPPED && sink.size() > 0;
    }
}
