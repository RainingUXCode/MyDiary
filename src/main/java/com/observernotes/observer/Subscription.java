package com.observernotes.observer;

/** Recibo de uma inscrição; cancelar é idempotente. */
@FunctionalInterface
public interface Subscription {

    void cancel();
}
