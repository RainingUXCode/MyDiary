package com.observernotes.domain;

import java.util.List;
import java.util.Optional;

/**
 * Valor imutável derivado do total acumulado de conclusões inéditas (0 a 16).
 * Não guarda estado próprio: tudo é calculado a partir do total, então não há
 * como ficar inconsistente com o {@link AchievementLedger}.
 */
public record AchievementProgress(int totalScored) {

    public AchievementProgress {
        if (totalScored < 0 || totalScored > Milestone.COUNT) {
            throw new IllegalArgumentException("Total fora de 0.." + Milestone.COUNT + ": " + totalScored);
        }
    }

    public static AchievementProgress of(int totalScored) {
        return new AchievementProgress(totalScored);
    }

    public List<Milestone> unlocked() {
        return List.of(Milestone.values()).subList(0, totalScored);
    }

    public Optional<Milestone> lastMilestone() {
        return totalScored == 0 ? Optional.empty() : Milestone.forNumber(totalScored);
    }

    public Optional<Milestone> nextMilestone() {
        return Milestone.forNumber(totalScored + 1);
    }

    public boolean isMax() {
        return totalScored == Milestone.COUNT;
    }

    /** Estrelas já conquistadas no total (0 a 12). */
    public int starsEarned() {
        return (int) unlocked().stream().filter(m -> m.reward() == RewardType.STAR).count();
    }

    /** Maior medalha conquistada, se houver. */
    public Optional<RewardType> highestMedal() {
        RewardType best = null;
        for (Milestone m : unlocked()) {
            if (m.reward().isMedal()) {
                best = m.reward();
            }
        }
        return Optional.ofNullable(best);
    }

    /** Estrelas acesas no ciclo atual, desde o último marco que não é estrela (0 a 3). */
    public int starsInCurrentTier() {
        int n = 0;
        for (Milestone m : unlocked()) {
            n = m.reward() == RewardType.STAR ? n + 1 : 0;
        }
        return n;
    }
}
