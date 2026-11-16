package com.observernotes.music;

import javax.sound.sampled.AudioFormat;

/** Saída de áudio (a placa de som). Separada para que a lógica de reprodução seja testável sem hardware. */
interface AudioSink {

    void start();

    void stop();

    /** Pode bloquear enquanto o buffer da placa estiver cheio ou a saída parada. @return bytes aceitos */
    int write(byte[] data, int offset, int length);

    /** Espera o que já foi escrito terminar de tocar. */
    void drain();

    void flush();

    void close();

    interface Factory {
        AudioSink open(AudioFormat format) throws AudioException;
    }
}
