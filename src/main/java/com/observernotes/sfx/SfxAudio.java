package com.observernotes.sfx;

/** Saída de baixo nível, separada do MusicPlayer e substituível nos testes. */
public interface SfxAudio extends AutoCloseable {

    void play(SfxEffect effect, double volume);

    @Override
    void close();

    SfxAudio SILENT = new SfxAudio() {
        @Override public void play(SfxEffect effect, double volume) { }
        @Override public void close() { }
    };
}
