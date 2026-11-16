package com.observernotes.music;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Abre um arquivo (WAV/AIFF/AU; MP3 se o MP3SPI estiver no classpath) como PCM de 16 bits little-endian. */
final class PcmSource {

    private PcmSource() { }

    static AudioInputStream open(Path file) throws AudioException {
        if (!Files.isRegularFile(file)) {
            throw new AudioException("arquivo não encontrado (foi movido ou apagado?)");
        }
        if (!Files.isReadable(file)) {
            throw new AudioException("sem permissão para ler o arquivo");
        }
        AudioInputStream source = null;
        try {
            source = AudioSystem.getAudioInputStream(file.toFile());
            AudioFormat base = source.getFormat();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16,
                    base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
            if (!AudioFormat.Encoding.PCM_SIGNED.equals(base.getEncoding()) || base.getSampleSizeInBits() != 16
                    || base.isBigEndian()) {
                if (!AudioSystem.isConversionSupported(target, base)) {
                    throw new AudioException("formato de áudio não suportado");
                }
                return AudioSystem.getAudioInputStream(target, source);
            }
            return source;
        } catch (UnsupportedAudioFileException e) {
            closeQuietly(source);
            String name = file.getFileName().toString().toLowerCase();
            throw new AudioException(name.endsWith(".mp3")
                    ? "não foi possível ler o MP3 (a biblioteca MP3SPI está no classpath?)"
                    : "arquivo de áudio inválido ou formato não suportado", e);
        } catch (IOException | RuntimeException e) {
            closeQuietly(source);
            throw new AudioException("não foi possível ler o arquivo", e);
        } catch (AudioException e) {
            closeQuietly(source);
            throw e;
        }
    }

    private static void closeQuietly(AudioInputStream s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // nada a fazer
            }
        }
    }
}
