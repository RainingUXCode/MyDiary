package com.observernotes.ui.model;

import java.util.Objects;

/** Uma linha do histórico: hora já formatada e o texto do evento. */
public record HistoryRowModel(String time, String text) {
    public HistoryRowModel {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(text, "text");
    }
}
