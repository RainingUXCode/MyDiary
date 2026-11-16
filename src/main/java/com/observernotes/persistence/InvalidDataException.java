package com.observernotes.persistence;

/** Conteúdo de um arquivo que não pôde ser aceito (sintaxe, tipos, versão ou regras do domínio). */
final class InvalidDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidDataException(String message) {
        super(message);
    }

    InvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
