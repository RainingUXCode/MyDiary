package com.observernotes.music;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/** {@link AudioSink} sobre uma {@link SourceDataLine} do Java Sound. */
final class LineSink implements AudioSink {

    private final SourceDataLine line;

    private LineSink(SourceDataLine line) {
        this.line = line;
    }

    static AudioSink open(AudioFormat format) throws AudioException {
        try {
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            return new LineSink(line);
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            throw new AudioException("nenhum dispositivo de áudio disponível", e);
        }
    }

    @Override
    public void start() {
        line.start();
    }

    @Override
    public void stop() {
        line.stop();
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        return line.write(data, offset, length);
    }

    @Override
    public void drain() {
        line.drain();
    }

    @Override
    public void flush() {
        line.flush();
    }

    @Override
    public void close() {
        line.close();
    }
}
