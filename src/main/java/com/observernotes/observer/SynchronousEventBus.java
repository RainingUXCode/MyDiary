package com.observernotes.observer;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publicador central de eventos. Entrega cada evento a todos os inscritos, na
 * ordem de inscrição, na thread de quem publica.
 *
 * <p>Falha de um observador é isolada: o erro vai para o {@link ObserverFailureHandler}
 * e os demais continuam sendo notificados. {@link #publish} nunca lança por
 * causa de observador, então o estado do domínio não depende da interface.
 */
public final class SynchronousEventBus<E> implements Subject<E>, EventPublisher<E> {

    private final CopyOnWriteArrayList<Observer<? super E>> observers = new CopyOnWriteArrayList<>();
    private final ObserverFailureHandler failureHandler;

    public SynchronousEventBus() {
        this(ObserverFailureHandler.logging());
    }

    public SynchronousEventBus(ObserverFailureHandler failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    @Override
    public Subscription subscribe(Observer<? super E> observer) {
        Objects.requireNonNull(observer, "observer");
        observers.addIfAbsent(observer);
        return () -> observers.remove(observer);
    }

    @Override
    public void publish(E event) {
        Objects.requireNonNull(event, "event");
        for (Observer<? super E> observer : observers) {
            try {
                observer.onEvent(event);
            } catch (RuntimeException failure) {
                report(observer, event, failure);
            }
        }
    }

    public int observerCount() {
        return observers.size();
    }

    private void report(Observer<?> observer, Object event, RuntimeException failure) {
        try {
            failureHandler.onFailure(observer, event, failure);
        } catch (RuntimeException ignored) {
            // O tratador de falhas também não pode derrubar a publicação.
        }
    }
}
