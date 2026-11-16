package com.observernotes.observer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SynchronousEventBusTest {

    private final List<Throwable> failures = new ArrayList<>();
    private final SynchronousEventBus<String> bus =
            new SynchronousEventBus<>((o, e, f) -> failures.add(f));

    @Test
    @DisplayName("entrega o evento a todos os inscritos, na ordem de inscrição")
    void deliversInOrder() {
        List<String> log = new ArrayList<>();
        bus.subscribe(e -> log.add("A:" + e));
        bus.subscribe(e -> log.add("B:" + e));
        bus.publish("x");
        assertEquals(List.of("A:x", "B:x"), log);
    }

    @Test
    @DisplayName("cancelar a inscrição interrompe as notificações e é idempotente")
    void unsubscribe() {
        List<String> log = new ArrayList<>();
        Subscription s = bus.subscribe(log::add);
        bus.publish("1");
        s.cancel();
        s.cancel();
        bus.publish("2");
        assertEquals(List.of("1"), log);
        assertEquals(0, bus.observerCount());
    }

    @Test
    @DisplayName("o mesmo observador inscrito duas vezes recebe uma só cópia")
    void duplicateSubscription() {
        List<String> log = new ArrayList<>();
        Observer<String> o = log::add;
        bus.subscribe(o);
        bus.subscribe(o);
        bus.publish("x");
        assertEquals(List.of("x"), log);
    }

    @Test
    @DisplayName("falha de um observador é isolada: os outros continuam e publish não lança")
    void failureIsolation() {
        List<String> log = new ArrayList<>();
        bus.subscribe(e -> { throw new IllegalStateException("boom"); });
        bus.subscribe(log::add);
        assertDoesNotThrow(() -> bus.publish("x"));
        assertEquals(List.of("x"), log);
        assertEquals(1, failures.size());
    }

    @Test
    @DisplayName("um tratador de falhas que também falha não derruba a publicação")
    void failingFailureHandler() {
        List<String> log = new ArrayList<>();
        SynchronousEventBus<String> b = new SynchronousEventBus<>((o, e, f) -> { throw new RuntimeException("x"); });
        b.subscribe(e -> { throw new RuntimeException("y"); });
        b.subscribe(log::add);
        assertDoesNotThrow(() -> b.publish("ok"));
        assertEquals(List.of("ok"), log);
    }

    @Test
    @DisplayName("publicar sem inscritos não faz nada")
    void noObservers() {
        assertDoesNotThrow(() -> bus.publish("x"));
    }
}
