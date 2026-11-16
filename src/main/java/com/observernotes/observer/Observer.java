package com.observernotes.observer;

/** Papel do padrão Observer: recebe notificações de eventos. */
@FunctionalInterface
public interface Observer<E> {

    void onEvent(E event);
}
