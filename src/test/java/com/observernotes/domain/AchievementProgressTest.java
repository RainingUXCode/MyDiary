package com.observernotes.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AchievementProgressTest {

    @Test
    @DisplayName("sem pontos: nada conquistado")
    void empty() {
        AchievementProgress p = AchievementProgress.of(0);
        assertEquals(0, p.starsEarned());
        assertEquals(0, p.starsInCurrentTier());
        assertTrue(p.lastMilestone().isEmpty());
        assertTrue(p.highestMedal().isEmpty());
        assertEquals(Optional.of(Milestone.STAR_1), p.nextMilestone());
    }

    @Test
    @DisplayName("três estrelas e depois bronze zera o ciclo de estrelas")
    void bronzeResetsTier() {
        assertEquals(3, AchievementProgress.of(3).starsInCurrentTier());
        AchievementProgress bronze = AchievementProgress.of(4);
        assertEquals(0, bronze.starsInCurrentTier());
        assertEquals(3, bronze.starsEarned());
        assertEquals(Optional.of(RewardType.BRONZE_MEDAL), bronze.highestMedal());
    }

    @Test
    @DisplayName("valores intermediários de cada ciclo")
    void tiers() {
        assertEquals(1, AchievementProgress.of(5).starsInCurrentTier());
        assertEquals(3, AchievementProgress.of(7).starsInCurrentTier());
        assertEquals(Optional.of(RewardType.SILVER_MEDAL), AchievementProgress.of(8).highestMedal());
        assertEquals(Optional.of(RewardType.SILVER_MEDAL), AchievementProgress.of(11).highestMedal());
        assertEquals(Optional.of(RewardType.GOLD_MEDAL), AchievementProgress.of(12).highestMedal());
        assertEquals(12, AchievementProgress.of(15).starsEarned());
        assertEquals(3, AchievementProgress.of(15).starsInCurrentTier());
    }

    @Test
    @DisplayName("16 é a conquista máxima e não há próximo marco")
    void max() {
        AchievementProgress p = AchievementProgress.of(16);
        assertTrue(p.isMax());
        assertEquals(Optional.of(Milestone.MAX_ACHIEVEMENT), p.lastMilestone());
        assertTrue(p.nextMilestone().isEmpty());
        assertEquals(16, p.unlocked().size());
    }

    @Test
    @DisplayName("rejeita total fora de 0 a 16")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> AchievementProgress.of(-1));
        assertThrows(IllegalArgumentException.class, () -> AchievementProgress.of(17));
    }
}
