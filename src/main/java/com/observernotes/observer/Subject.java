package com.observernotes.observer;

/** Papel Subject do padrão Observer: quem quer ouvir se inscreve aqui. */
public interface Subject<E> {

    Subscription subscribe(Observer<? super E> observer);
}
