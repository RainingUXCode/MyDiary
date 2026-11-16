package com.observernotes.ui.controller;

import com.observernotes.persistence.PersistenceException;
import java.util.function.Consumer;

/**
 * Recebe os avisos de falha de gravação (estado e preferências) vindos do {@code AppCore}.
 * Existe antes do núcleo porque o núcleo precisa do callback para ser criado.
 */
public final class SaveFailureTracker implements Consumer<PersistenceException> {

    private volatile int count;
    private volatile String lastMessage = "";

    @Override
    public void accept(PersistenceException e) {
        lastMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        count++;
    }

    /** Quantas falhas já foram informadas (cresce sempre; compare antes e depois de um comando). */
    public int count() {
        return count;
    }

    public String lastMessage() {
        return lastMessage;
    }
}
