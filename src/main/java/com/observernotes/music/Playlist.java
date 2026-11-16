package com.observernotes.music;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lista de faixas e posição atual. Estrutura pura (sem áudio, sem arquivos, sem threads):
 * quem a usa ({@link PlayerEngine}) é quem sincroniza o acesso.
 */
public final class Playlist {

    private final List<Track> tracks = new ArrayList<>();
    private int current = -1;

    public int size() {
        return tracks.size();
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    public List<Track> tracks() {
        return List.copyOf(tracks);
    }

    /** Índice da faixa atual, ou -1 se a lista está vazia. */
    public int currentIndex() {
        return current;
    }

    public Track currentTrack() {
        return current < 0 ? null : tracks.get(current);
    }

    /** Acrescenta ao fim, ignorando repetidas. @return quantas entraram */
    public int add(Collection<Path> files) {
        Set<String> known = new HashSet<>();
        tracks.forEach(t -> known.add(t.key()));
        int added = 0;
        for (Path f : files) {
            Track t = Track.of(f);
            if (known.add(t.key())) {
                tracks.add(t);
                added++;
            }
        }
        if (current < 0 && !tracks.isEmpty()) {
            current = 0;
        }
        return added;
    }

    /** Substitui tudo (restauração do disco). O índice inválido vira 0. */
    public void restore(List<Track> restored, int index) {
        tracks.clear();
        Set<String> known = new HashSet<>();
        for (Track t : restored) {
            if (known.add(t.key())) {
                tracks.add(t);
            }
        }
        current = tracks.isEmpty() ? -1 : (index >= 0 && index < tracks.size() ? index : 0);
    }

    /** @return {@code true} se a faixa removida era a atual */
    public boolean remove(int index) {
        if (index < 0 || index >= tracks.size()) {
            return false;
        }
        tracks.remove(index);
        boolean wasCurrent = index == current;
        if (tracks.isEmpty()) {
            current = -1;
        } else if (index < current) {
            current--;
        } else if (wasCurrent && current >= tracks.size()) {
            current = tracks.size() - 1;
        }
        return wasCurrent;
    }

    /** Move a faixa uma posição ({@code delta} = -1 sobe, +1 desce). A faixa atual continua sendo a mesma música. */
    public boolean move(int index, int delta) {
        int to = index + delta;
        if (index < 0 || index >= tracks.size() || to < 0 || to >= tracks.size() || delta == 0) {
            return false;
        }
        Track moved = tracks.remove(index);
        tracks.add(to, moved);
        if (current == index) {
            current = to;
        } else if (index < current && to >= current) {
            current--;
        } else if (index > current && to <= current) {
            current++;
        }
        return true;
    }

    public boolean select(int index) {
        if (index < 0 || index >= tracks.size()) {
            return false;
        }
        current = index;
        return true;
    }

    public boolean hasNext() {
        return current >= 0 && current < tracks.size() - 1;
    }

    /** Vai à seguinte (ou anterior) dando a volta nas pontas. */
    public void advance(boolean forward) {
        if (tracks.isEmpty()) {
            return;
        }
        current = (current + (forward ? 1 : -1) + tracks.size()) % tracks.size();
    }
}
