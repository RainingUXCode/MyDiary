package com.observernotes.music;

import java.util.List;

/** O que é gravado no disco: referências aos arquivos (sem cópias), posição, volume e repetição. */
public record StoredPlayer(List<Track> tracks, int currentIndex, double volume, boolean repeat) {

    public static final double DEFAULT_VOLUME = 0.7;

    public StoredPlayer {
        tracks = List.copyOf(tracks);
    }

    public static StoredPlayer empty() {
        return new StoredPlayer(List.of(), -1, DEFAULT_VOLUME, false);
    }
}
