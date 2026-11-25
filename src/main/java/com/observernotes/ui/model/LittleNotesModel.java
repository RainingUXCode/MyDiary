package com.observernotes.ui.model;

import java.util.List;
import java.util.Objects;

/** Dados prontos para desenhar e interagir com o mural Little Notes. */
public record LittleNotesModel(List<Item> items, String editingId) {

    public static final String NEW_ID = "__new_note__";

    public record Item(String id, String text) {
        public Item {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(text, "text");
        }
    }

    public LittleNotesModel {
        items = List.copyOf(items);
        editingId = editingId == null ? "" : editingId;
        if (items.size() > 3) {
            throw new IllegalArgumentException("máximo de 3 bilhetes");
        }
    }

    public static LittleNotesModel empty() {
        return new LittleNotesModel(List.of(), "");
    }

    public boolean editing() {
        return !editingId.isEmpty();
    }

    public boolean adding() {
        return NEW_ID.equals(editingId);
    }

    public boolean canAdd() {
        return items.size() < 3 && !adding();
    }
}
