package com.observernotes.notes;

import java.util.Objects;

/** Um bilhete curto do mural, independente de tarefas, eventos e conquistas. */
public record LittleNote(String id, String text) {

    public static final int MAX_LENGTH = 60;

    public LittleNote {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        if (id.isBlank()) {
            throw new IllegalArgumentException("identificador de bilhete vazio");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("texto de bilhete vazio");
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("bilhete excede " + MAX_LENGTH + " caracteres");
        }
    }
}
