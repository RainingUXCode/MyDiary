package com.observernotes.domain;

/** Dados restaurados que violam as regras do domínio. Nada é aplicado quando ela ocorre. */
public final class InvalidStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidStateException(String message) {
        super(message);
    }
}
