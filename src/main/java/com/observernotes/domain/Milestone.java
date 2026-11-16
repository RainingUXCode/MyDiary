package com.observernotes.domain;

import java.util.List;
import java.util.Optional;

/**
 * Os 16 marcos, na ordem exata em que são conquistados:
 * 3 estrelas, bronze, 3 estrelas, prata, 3 estrelas, ouro, 3 estrelas, conquista máxima.
 * A posição na enumeração define o número do marco (1 a 16).
 */
public enum Milestone {
    STAR_1(RewardType.STAR), STAR_2(RewardType.STAR), STAR_3(RewardType.STAR),
    BRONZE_MEDAL(RewardType.BRONZE_MEDAL),
    STAR_4(RewardType.STAR), STAR_5(RewardType.STAR), STAR_6(RewardType.STAR),
    SILVER_MEDAL(RewardType.SILVER_MEDAL),
    STAR_7(RewardType.STAR), STAR_8(RewardType.STAR), STAR_9(RewardType.STAR),
    GOLD_MEDAL(RewardType.GOLD_MEDAL),
    STAR_10(RewardType.STAR), STAR_11(RewardType.STAR), STAR_12(RewardType.STAR),
    MAX_ACHIEVEMENT(RewardType.MAX_ACHIEVEMENT);

    private static final List<Milestone> ALL = List.of(values());
    public static final int COUNT = ALL.size();

    private final RewardType reward;

    Milestone(RewardType reward) {
        this.reward = reward;
    }

    public RewardType reward() {
        return reward;
    }

    /** Número do marco, de 1 a {@value #COUNT}. */
    public int number() {
        return ordinal() + 1;
    }

    public static Optional<Milestone> forNumber(int number) {
        return number >= 1 && number <= COUNT ? Optional.of(ALL.get(number - 1)) : Optional.empty();
    }
}
