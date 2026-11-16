package com.observernotes.observer;

import java.lang.System.Logger.Level;

/** Decide o que fazer quando um observador lança exceção. */
@FunctionalInterface
public interface ObserverFailureHandler {

    void onFailure(Observer<?> observer, Object event, RuntimeException failure);

    static ObserverFailureHandler logging() {
        System.Logger logger = System.getLogger("observernotes.observer");
        return (observer, event, failure) -> logger.log(Level.WARNING,
                "Observador " + observer.getClass().getSimpleName() + " falhou em " + event, failure);
    }
}
