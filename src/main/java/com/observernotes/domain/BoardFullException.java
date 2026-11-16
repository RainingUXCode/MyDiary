package com.observernotes.domain;

/** Lançada ao atingir o limite de segurança de tarefas persistidas. */
public final class BoardFullException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BoardFullException(int max) {
        super("Limite de " + max + " tarefas simultâneas atingido");
    }
}
