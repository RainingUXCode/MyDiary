package com.observernotes.music;

import java.util.List;

/** Foto imutável do player para a interface desenhar. */
public record PlayerSnapshot(PlaybackState state, int currentIndex, String currentTitle, List<TrackInfo> tracks,
                             double volume, boolean repeat, String message) {

    public record TrackInfo(String title, String path, boolean available, boolean current) { }

    public PlayerSnapshot {
        tracks = List.copyOf(tracks);
    }

    public boolean playing() {
        return state == PlaybackState.PLAYING;
    }

    public boolean hasTracks() {
        return !tracks.isEmpty();
    }
}
