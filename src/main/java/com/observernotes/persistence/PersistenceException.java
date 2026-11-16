package com.observernotes.persistence;

/** Falha ao gravar dados. Os dados anteriores em disco continuam intactos. */
public final class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
