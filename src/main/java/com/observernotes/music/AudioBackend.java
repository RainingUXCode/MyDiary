package com.observernotes.music;

import java.nio.file.Path;

/** Porta para o áudio de verdade. Em testes, é substituída por um dublê determinístico. */
public interface AudioBackend {

    /**
     * Abre o arquivo e deixa a reprodução pronta (ainda sem tocar).
     *
     * @param onFinished chamado (em outra thread) só quando a faixa termina sozinha, nunca após {@code close()}
     * @throws AudioException arquivo ausente, formato inválido ou sem dispositivo de áudio
     */
    Playback open(Path file, double volume, Runnable onFinished) throws AudioException;

    /** Uma faixa aberta. Todos os métodos são seguros para chamar de qualquer thread. */
    interface Playback extends AutoCloseable {

        void start();

        void pause();

        void resume();

        /** 0.0 (mudo) a 1.0 (máximo). */
        void setVolume(double volume);

        /** Libera linha de áudio e arquivo. Pode ser chamado mais de uma vez. */
        @Override
        void close();
    }
}
