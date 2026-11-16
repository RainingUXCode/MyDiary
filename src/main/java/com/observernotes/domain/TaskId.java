package com.observernotes.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador imutável de uma tarefa. */
public record TaskId(UUID value) {

    public TaskId {
        Objects.requireNonNull(value, "value");
    }

    public static TaskId random() {
        return new TaskId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
