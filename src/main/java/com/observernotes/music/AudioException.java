package com.observernotes.music;

/** Falha ao abrir ou tocar um arquivo de áudio. A mensagem já vem em português e pode ser mostrada ao usuário. */
public class AudioException extends Exception {

    public AudioException(String message) {
        super(message);
    }

    public AudioException(String message, Throwable cause) {
        super(message, cause);
    }
}
