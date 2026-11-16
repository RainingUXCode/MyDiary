package com.observernotes.observer;

/**
 * Lado de publicação, separado do lado de inscrição (princípio da segregação
 * de interfaces): o serviço só publica; a interface só se inscreve.
 */
public interface EventPublisher<E> {

    void publish(E event);
}
