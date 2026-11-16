package com.observernotes.domain;

import static com.observernotes.domain.RewardType.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MilestoneTest {

    @Test
    @DisplayName("são exatamente 16 marcos na sequência: 3 estrelas, bronze, 3, prata, 3, ouro, 3, máxima")
    void exactSequenceOfSixteen() {
        List<RewardType> expected = List.of(
                STAR, STAR, STAR, BRONZE_MEDAL,
                STAR, STAR, STAR, SILVER_MEDAL,
                STAR, STAR, STAR, GOLD_MEDAL,
                STAR, STAR, STAR, MAX_ACHIEVEMENT);
        List<RewardType> actual = new ArrayList<>();
        for (int n = 1; n <= 16; n++) {
            Milestone m = Milestone.forNumber(n).orElseThrow();
            assertEquals(n, m.number());
            actual.add(m.reward());
        }
        assertEquals(expected, actual);
        assertEquals(16, Milestone.COUNT);
    }

    @Test
    @DisplayName("números fora de 1 a 16 não têm marco")
    void outOfRange() {
        assertTrue(Milestone.forNumber(0).isEmpty());
        assertTrue(Milestone.forNumber(17).isEmpty());
        assertTrue(Milestone.forNumber(-1).isEmpty());
    }

    @Test
    @DisplayName("doze estrelas, três medalhas e uma conquista máxima")
    void rewardCounts() {
        int stars = 0;
        int medals = 0;
        int max = 0;
        for (Milestone m : Milestone.values()) {
            if (m.reward() == STAR) {
                stars++;
            } else if (m.reward().isMedal()) {
                medals++;
            } else {
                max++;
            }
        }
        assertEquals(12, stars);
        assertEquals(3, medals);
        assertEquals(1, max);
    }
}
